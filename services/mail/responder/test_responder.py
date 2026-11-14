#!/usr/bin/env python3
"""
Unit test suite for Nexus Fleet Mail Auto-Responder.
Tests RFC 3834 anti-loop detection, multi-tier exclusions, and cooldown logic.
"""

import unittest
import sqlite3
import os
import json
import email
from datetime import datetime, timedelta
from email.message import EmailMessage

from main import AutoResponder, init_db

class TestAutoResponderLogic(unittest.TestCase):
    def setUp(self):
        self.test_dir = os.path.dirname(os.path.abspath(__file__))
        self.config = {
            "mail": {
                "imap_server": "localhost",
                "imap_port": 993,
                "smtp_server": "localhost",
                "smtp_port": 465,
                "username": "me@faizal.uk",
                "sender_name": "Mohammad Faizal",
                "sender_email": "me@faizal.uk"
            },
            "rules": {
                "cooldown_days": 7,
                "check_interval_seconds": 15,
                "ignore_threads_with_in_reply_to": True
            },
            "paths": {
                "db_file": "state/test_responder.db",
                "html_template": "templates/acknowledgement.html",
                "txt_template": "templates/acknowledgement.txt"
            }
        }
        self.exclusions = {
            "blacklist_domains": ["stripe.com", "google.com", "amazon.com"],
            "blacklist_senders": ["mailer-daemon@faizal.uk", "badactor@spam.org"],
            "whitelist_senders": ["vip-friend@gmail.com"],
            "drop_sender_patterns": ["noreply", "no-reply", "newsletter", "alert", "billing"]
        }
        
        # In-memory or clean test DB
        self.responder = AutoResponder(self.config, self.exclusions)
        self.responder.conn = sqlite3.connect(":memory:")
        cur = self.responder.conn.cursor()
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
        self.responder.conn.commit()

    def create_msg(self, headers=None, sender="client@company.com", content_type="text/plain"):
        msg = EmailMessage()
        msg["From"] = sender
        msg["To"] = "me@faizal.uk"
        msg["Subject"] = "Inquiry regarding collaboration"
        msg["Message-ID"] = "<test-123@company.com>"
        msg.set_content("Hello Mohammad, I would like to discuss a project.")
        
        if headers:
            for k, v in headers.items():
                msg[k] = v
        return msg

    def test_normal_legitimate_sender_approved(self):
        msg = self.create_msg(sender="recruiter@innovate.co.uk")
        suppressed, reason = self.responder.is_suppressed(msg, "recruiter@innovate.co.uk")
        self.assertFalse(suppressed)
        self.assertEqual(reason, "Approved")

    def test_self_email_suppressed(self):
        msg = self.create_msg(sender="me@faizal.uk")
        suppressed, reason = self.responder.is_suppressed(msg, "me@faizal.uk")
        self.assertTrue(suppressed)
        self.assertIn("Self sender", reason)

    def test_whitelist_bypassed(self):
        msg = self.create_msg(sender="vip-friend@gmail.com")
        suppressed, reason = self.responder.is_suppressed(msg, "vip-friend@gmail.com")
        self.assertTrue(suppressed)
        self.assertIn("whitelisted", reason)

    def test_blacklist_domain_suppressed(self):
        msg = self.create_msg(sender="notifications@stripe.com")
        suppressed, reason = self.responder.is_suppressed(msg, "notifications@stripe.com")
        self.assertTrue(suppressed)

    def test_drop_pattern_suppressed(self):
        msg = self.create_msg(sender="noreply@anywhere.io")
        suppressed, reason = self.responder.is_suppressed(msg, "noreply@anywhere.io")
        self.assertTrue(suppressed)
        self.assertIn("automated pattern", reason)

    def test_rfc3834_auto_submitted_suppressed(self):
        msg = self.create_msg(headers={"Auto-Submitted": "auto-replied"}, sender="bot@remote.org")
        suppressed, reason = self.responder.is_suppressed(msg, "bot@remote.org")
        self.assertTrue(suppressed)
        self.assertIn("Auto-Submitted", reason)

    def test_bulk_precedence_suppressed(self):
        msg = self.create_msg(headers={"Precedence": "bulk"}, sender="sales@store.com")
        suppressed, reason = self.responder.is_suppressed(msg, "sales@store.com")
        self.assertTrue(suppressed)
        self.assertIn("Precedence header", reason)

    def test_mailing_list_unsubscribe_suppressed(self):
        msg = self.create_msg(headers={"List-Unsubscribe": "<https://list.org/unsub>"}, sender="digest@daily.org")
        suppressed, reason = self.responder.is_suppressed(msg, "digest@daily.org")
        self.assertTrue(suppressed)
        self.assertIn("Mailing list header", reason)

    def test_in_reply_to_ongoing_thread_suppressed(self):
        msg = self.create_msg(headers={"In-Reply-To": "<prev-msg-999@faizal.uk>"}, sender="colleague@work.org")
        suppressed, reason = self.responder.is_suppressed(msg, "colleague@work.org")
        self.assertTrue(suppressed)
        self.assertIn("In-Reply-To exists", reason)

    def test_cooldown_7_days(self):
        sender = "client@newpartner.com"
        msg = self.create_msg(sender=sender)
        
        # 1. First email: Approved
        suppressed, _ = self.responder.is_suppressed(msg, sender)
        self.assertFalse(suppressed)
        
        # Record reply now
        self.responder.update_cooldown(sender)
        
        # 2. Second email immediately after: Suppressed due to cooldown
        suppressed, reason = self.responder.is_suppressed(msg, sender)
        self.assertTrue(suppressed)
        self.assertIn("cooldown", reason)
        
        # 3. Simulate passage of 8 days
        cur = self.responder.conn.cursor()
        eight_days_ago = (datetime.utcnow() - timedelta(days=8)).isoformat()
        cur.execute("UPDATE replied_senders SET last_replied_at = ? WHERE sender_email = ?", (eight_days_ago, sender))
        self.responder.conn.commit()
        
        # 4. Third email after cooldown expired: Approved
        suppressed, reason = self.responder.is_suppressed(msg, sender)
        self.assertFalse(suppressed)
        self.assertEqual(reason, "Approved")

if __name__ == "__main__":
    unittest.main()
