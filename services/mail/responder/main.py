#!/usr/bin/env python3
"""
Nexus Fleet Mail Responder Daemon
Autonomous, intelligent auto-reply acknowledgement engine for faizal.uk.
Implements RFC 3834 loop prevention, 7-day sliding cooldown, and multi-tier exclusions.
"""

import os
import sys
import json
import time
import email
import logging
import sqlite3
import imaplib
import smtplib
from datetime import datetime, timedelta, timezone
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.header import decode_header
from email.utils import parseaddr, formataddr

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger("responder")

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

def load_json(rel_path):
    full_path = os.path.join(BASE_DIR, rel_path)
    with open(full_path, "r", encoding="utf-8") as f:
        return json.load(f)

def init_db(db_path):
    os.makedirs(os.path.dirname(db_path), exist_ok=True)
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()
    cur.execute("""
        CREATE TABLE IF NOT EXISTS replied_senders (
            sender_email TEXT PRIMARY KEY,
            last_replied_at TIMESTAMP,
            reply_count INTEGER DEFAULT 1
        )
    """)
    cur.execute("""
        CREATE TABLE IF NOT EXISTS processed_messages (
            message_id TEXT PRIMARY KEY,
            sender_email TEXT,
            subject TEXT,
            processed_at TIMESTAMP,
            action TEXT
        )
    """)
    conn.commit()
    return conn

def decode_mime_words(raw_header):
    if not raw_header:
        return ""
    decoded_fragments = decode_header(raw_header)
    result = []
    for fragment, charset in decoded_fragments:
        if isinstance(fragment, bytes):
            charset = charset or "utf-8"
            try:
                result.append(fragment.decode(charset, errors="replace"))
            except LookupError:
                result.append(fragment.decode("utf-8", errors="replace"))
        else:
            result.append(str(fragment))
    return "".join(result)

class AutoResponder:
    def __init__(self, config, exclusions):
        self.config = config
        self.exclusions = exclusions
        
        mail_cfg = config["mail"]
        self.imap_server = mail_cfg["imap_server"]
        self.imap_port = mail_cfg.get("imap_port", 993)
        self.smtp_server = mail_cfg["smtp_server"]
        self.smtp_port = mail_cfg.get("smtp_port", 465)
        self.username = mail_cfg["username"]
        
        # Password from env or fallback to config
        pw_env = mail_cfg.get("password_env", "MAIL_PASSWORD")
        self.password = os.environ.get(pw_env, os.environ.get("STALWART_PASSWORD", "FaizalMail2026!"))
        
        self.sender_name = mail_cfg.get("sender_name", "Mohammad Faizal")
        self.sender_email = mail_cfg.get("sender_email", "me@faizal.uk")
        
        rules = config.get("rules", {})
        self.cooldown_days = rules.get("cooldown_days", 7)
        self.check_interval = rules.get("check_interval_seconds", 15)
        self.ignore_threads = rules.get("ignore_threads_with_in_reply_to", True)

        paths = config.get("paths", {})
        db_path = os.path.join(BASE_DIR, paths.get("db_file", "state/responder.db"))
        self.conn = init_db(db_path)

        # Load templates
        html_path = os.path.join(BASE_DIR, paths.get("html_template", "templates/acknowledgement.html"))
        txt_path = os.path.join(BASE_DIR, paths.get("txt_template", "templates/acknowledgement.txt"))
        
        with open(html_path, "r", encoding="utf-8") as f:
            self.html_template = f.read()
        with open(txt_path, "r", encoding="utf-8") as f:
            self.txt_template = f.read()

    def is_suppressed(self, msg, sender_addr):
        sender_lower = sender_addr.lower()
        domain = sender_lower.split("@")[-1] if "@" in sender_lower else ""

        # 1. Never reply to self
        if sender_lower == self.sender_email.lower():
            return True, "Self sender"

        # 2. Check Whitelist (Friends/Family - silent pass-through)
        for whitelisted in self.exclusions.get("whitelist_senders", []):
            if sender_lower == whitelisted.lower():
                return True, "Sender is whitelisted contact (bypassed)"

        # 3. Check Blacklist senders & domains
        if sender_lower in [s.lower() for s in self.exclusions.get("blacklist_senders", [])]:
            return True, "Sender is blacklisted"
        if domain in [d.lower() for d in self.exclusions.get("blacklist_domains", [])]:
            return True, f"Domain {domain} is blacklisted"

        # 4. Check Bot / Automated sender patterns
        for pattern in self.exclusions.get("drop_sender_patterns", []):
            if pattern.lower() in sender_lower:
                return True, f"Sender matches automated pattern: {pattern}"

        # 5. Check Loop Prevention Headers (RFC 3834 & Standard suppresses)
        auto_submitted = msg.get("Auto-Submitted", "").lower()
        if auto_submitted and auto_submitted != "no":
            return True, f"Auto-Submitted header present: {auto_submitted}"

        precedence = msg.get("Precedence", "").lower()
        if precedence in ["bulk", "list", "junk", "auto_reply"]:
            return True, f"Precedence header is {precedence}"

        if msg.get("List-Unsubscribe") or msg.get("List-Id"):
            return True, "Mailing list header detected"

        if msg.get("X-Autoreply", "").lower() == "yes":
            return True, "X-Autoreply detected"

        # 6. Check Calendar invites
        content_type = msg.get_content_type().lower()
        if "calendar" in content_type:
            return True, "Calendar invitation detected"

        # 7. Check ongoing conversation thread (In-Reply-To)
        if self.ignore_threads and msg.get("In-Reply-To"):
            return True, "Message is an existing reply thread (In-Reply-To exists)"

        # 8. Check 7-Day Cooldown from Database
        cur = self.conn.cursor()
        cur.execute("SELECT last_replied_at FROM replied_senders WHERE sender_email = ?", (sender_lower,))
        row = cur.fetchone()
        if row:
            last_replied_str = row[0]
            try:
                last_replied = datetime.fromisoformat(last_replied_str)
                if last_replied.tzinfo is None:
                    last_replied = last_replied.replace(tzinfo=timezone.utc)
                if datetime.now(timezone.utc) - last_replied < timedelta(days=self.cooldown_days):
                    return True, f"Within {self.cooldown_days}-day cooldown (last replied: {last_replied_str})"
            except Exception as e:
                logger.warning(f"Failed to parse timestamp {last_replied_str}: {e}")

        return False, "Approved"

    def record_processed(self, msg_id, sender_addr, subject, action):
        cur = self.conn.cursor()
        now = datetime.now(timezone.utc).isoformat()
        cur.execute("""
            INSERT OR REPLACE INTO processed_messages (message_id, sender_email, subject, processed_at, action)
            VALUES (?, ?, ?, ?, ?)
        """, (msg_id, sender_addr, subject, now, action))
        self.conn.commit()

    def update_cooldown(self, sender_addr):
        cur = self.conn.cursor()
        sender_lower = sender_addr.lower()
        now = datetime.now(timezone.utc).isoformat()
        cur.execute("""
            INSERT INTO replied_senders (sender_email, last_replied_at, reply_count)
            VALUES (?, ?, 1)
            ON CONFLICT(sender_email) DO UPDATE SET
                last_replied_at = excluded.last_replied_at,
                reply_count = reply_count + 1
        """, (sender_lower, now))
        self.conn.commit()

    def send_acknowledgement(self, to_addr, to_name, orig_msg_id, orig_subject):
        msg = MIMEMultipart("alternative")
        
        reply_subject = f"Re: {orig_subject}" if orig_subject else "Got your message! — Mohammad Faizal"
        msg["Subject"] = reply_subject
        msg["From"] = formataddr((self.sender_name, self.sender_email))
        msg["To"] = formataddr((to_name, to_addr))
        
        # RFC 3834 Anti-Loop Headers
        msg["Auto-Submitted"] = "auto-replied"
        msg["Precedence"] = "auto_reply"
        msg["X-Auto-Response-Suppress"] = "All"
        
        if orig_msg_id:
            msg["In-Reply-To"] = orig_msg_id
            msg["References"] = orig_msg_id

        # Attach text & HTML bodies
        part_txt = MIMEText(self.txt_template, "plain", "utf-8")
        part_html = MIMEText(self.html_template, "html", "utf-8")
        msg.attach(part_txt)
        msg.attach(part_html)

        # Dispatch via SMTP SSL
        with smtplib.SMTP_SSL(self.smtp_server, self.smtp_port) as smtp:
            smtp.login(self.username, self.password)
            smtp.sendmail(self.sender_email, [to_addr], msg.as_string())

        logger.info(f"✅ Dispatched acknowledgement to {to_addr} (Ref: {orig_msg_id})")
        self.update_cooldown(to_addr)

    def process_mailbox(self):
        try:
            with imaplib.IMAP4_SSL(self.imap_server, self.imap_port) as imap:
                imap.login(self.username, self.password)
                imap.select("INBOX")

                # Search for unread messages
                status, messages = imap.search(None, "UNSEEN")
                if status != "OK" or not messages[0]:
                    return

                msg_ids = messages[0].split()
                logger.info(f"Checking {len(msg_ids)} unseen message(s)...")

                for m_id in msg_ids:
                    status, data = imap.fetch(m_id, "(RFC822)")
                    if status != "OK":
                        continue

                    raw_email = data[0][1]
                    parsed = email.message_from_bytes(raw_email)
                    
                    msg_id_header = parsed.get("Message-ID", f"<auto-{time.time()}@faizal.uk>")
                    subject = decode_mime_words(parsed.get("Subject", ""))
                    from_header = parsed.get("From", "")
                    from_name, from_addr = parseaddr(from_header)
                    from_name = decode_mime_words(from_name)

                    # Check already processed
                    cur = self.conn.cursor()
                    cur.execute("SELECT action FROM processed_messages WHERE message_id = ?", (msg_id_header,))
                    if cur.fetchone():
                        continue

                    suppressed, reason = self.is_suppressed(parsed, from_addr)
                    if suppressed:
                        logger.info(f"⏩ Dropped auto-reply for '{from_addr}': {reason}")
                        self.record_processed(msg_id_header, from_addr, subject, f"DROPPED: {reason}")
                    else:
                        logger.info(f"⚡ Processing auto-reply for '{from_addr}' (Subject: {subject})")
                        self.send_acknowledgement(from_addr, from_name, msg_id_header, subject)
                        self.record_processed(msg_id_header, from_addr, subject, "REPLIED")

        except Exception as e:
            logger.error(f"Error checking mailbox: {e}")

    def run(self):
        logger.info(f"Starting Nexus Fleet Mail Responder for {self.username}...")
        logger.info(f"IMAP: {self.imap_server}:{self.imap_port} | SMTP: {self.smtp_server}:{self.smtp_port}")
        logger.info(f"Cooldown: {self.cooldown_days} days | Poll interval: {self.check_interval}s")
        
        while True:
            self.process_mailbox()
            time.sleep(self.check_interval)

if __name__ == "__main__":
    cfg = load_json("config.json")
    excl = load_json("exclusions.json")
    responder = AutoResponder(cfg, excl)
    responder.run()
