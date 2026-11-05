#!/usr/bin/env python3
"""
Ultra-lean OCI Resource Manager Stack Auto-Allocator
Runs as a periodic one-shot job via systemd timer (0 MB idle RAM).
"""

import os
import sys
import json
import time
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from datetime import datetime, timezone

try:
    import oci
except ImportError:
    print("[ERROR] OCI SDK not installed. Please install via: pip3 install --break-system-packages oci")
    sys.exit(1)

# ── Configuration ─────────────────────────────────────────────────────────────
STACK_OCID = os.environ.get(
    "OCI_STACK_OCID",
    "ocid1.ormstack.oc1.ap-hyderabad-1.amaaaaaae3t6lgqavlngst2cjhzru45xfnsa3ul4tb7weubebag5gxrzmqga"
)
REGION = os.environ.get("OCI_REGION", "ap-hyderabad-1")
STATE_FILE = os.environ.get("OCI_STATE_FILE", "/home/ubuntu/stack/ops/oci_state.json")

# SMTP Relay Config (Defaults match production docker-compose Brevo relay)
SMTP_HOST = os.environ.get("SMTP_HOST", "smtp-relay.brevo.com")
SMTP_PORT = int(os.environ.get("SMTP_PORT", "587"))
SMTP_USER = os.environ.get("SMTP_USER", "bb6408001@smtp-brevo.com")
SMTP_PASS = os.environ.get("SMTP_PASS", "xsmtpsib-73f7e08afbd853d9e9d2e8c2049f93badd7e7f2b34e3e9de937eee1a307cace3-cmEy8jEk90T1HM6Z")
SENDER_EMAIL = os.environ.get("SENDER_EMAIL", "reports@faizal.uk")
RECIPIENTS = [
    email.strip() for email in os.environ.get("RECIPIENTS", "me@faizal.uk,mohammad0faizal@gmail.com").split(",") if email.strip()
]

# ── Helper: State Management ──────────────────────────────────────────────────
def load_state():
    if os.path.exists(STATE_FILE):
        try:
            with open(STATE_FILE, "r") as f:
                return json.load(f)
        except Exception as e:
            print(f"[WARN] Failed to read state file: {e}")
    return {
        "stack_ocid": STACK_OCID,
        "region": REGION,
        "status": "INITIALIZING",
        "total_attempts": 0,
        "attempts_today": 0,
        "today_date": datetime.now(timezone.utc).strftime("%Y-%m-%d"),
        "last_attempt_at": None,
        "last_status": "NONE",
        "last_message": "Initialized, waiting for first check.",
        "last_job_ocid": None,
        "last_daily_email_date": None,
        "allocated_at": None,
        "recent_attempts": []
    }

def record_attempt(state, status, message, job_ocid=None):
    if "recent_attempts" not in state or not isinstance(state["recent_attempts"], list):
        state["recent_attempts"] = []
    
    timestamp = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    short_job = job_ocid[-10:] if job_ocid else "N/A"
    
    state["recent_attempts"].insert(0, {
        "time": timestamp,
        "status": status,
        "message": message,
        "job_id": short_job
    })
    # Keep up to the 10 most recent attempts
    state["recent_attempts"] = state["recent_attempts"][:10]

PUBLIC_STATE_FILE = os.environ.get("OCI_PUBLIC_STATE_FILE", "/home/ubuntu/stack/apps/personal-site/public/assets/oci_state.json")

def save_state(state):
    for fpath in [STATE_FILE, PUBLIC_STATE_FILE]:
        try:
            os.makedirs(os.path.dirname(fpath), exist_ok=True)
            tmp_path = f"{fpath}.tmp"
            with open(tmp_path, "w") as f:
                json.dump(state, f, indent=2)
            os.replace(tmp_path, fpath)
        except Exception as e:
            print(f"[WARN] Failed to write state to {fpath}: {e}")

# ── Helper: Email Dispatcher ──────────────────────────────────────────────────
def send_email(subject, html_content):
    try:
        msg = MIMEMultipart("alternative")
        msg["Subject"] = subject
        msg["From"] = f"Faizal OCI Hunter <{SENDER_EMAIL}>"
        msg["To"] = ", ".join(RECIPIENTS)

        part = MIMEText(html_content, "html")
        msg.attach(part)

        with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=15) as server:
            server.starttls()
            if SMTP_USER and SMTP_PASS:
                server.login(SMTP_USER, SMTP_PASS)
            server.sendmail(SENDER_EMAIL, RECIPIENTS, msg.as_string())
        print(f"[MAIL] Sent: '{subject}' to {', '.join(RECIPIENTS)}")
        return True
    except Exception as e:
        print(f"[MAIL ERROR] Failed to send email: {e}")
        return False

# ── Helper: OCI Client Setup ──────────────────────────────────────────────────
def get_rm_client():
    # Priority 1: ~/.oci/config file
    oci_config_path = os.path.expanduser("~/.oci/config")
    if os.path.exists(oci_config_path):
        try:
            cfg = oci.config.from_file(oci_config_path)
            return oci.resource_manager.ResourceManagerClient(cfg)
        except Exception as e:
            print(f"[WARN] Failed loading ~/.oci/config: {e}")

    # Priority 2: Instance Principals
    try:
        signer = oci.auth.signers.InstancePrincipalsSecurityTokenSigner()
        return oci.resource_manager.ResourceManagerClient(config={"region": REGION}, signer=signer)
    except Exception as e:
        print(f"[WARN] Failed loading Instance Principals: {e}")

    raise RuntimeError("No valid OCI credentials found (~/.oci/config or Instance Principal required)")

# ── Main Run Cycle ────────────────────────────────────────────────────────────
def run():
    state = load_state()
    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")

    # Reset today counter if UTC day rolled over
    if state.get("today_date") != today:
        state["today_date"] = today
        state["attempts_today"] = 0

    # Stop if already allocated
    if state.get("status") == "ALLOCATED":
        print("[INFO] Stack already ALLOCATED. Stopping.")
        sys.exit(0)

    # Rate-limit guard: Ensure at least 150s elapsed between create-job API requests
    last_attempt_str = state.get("last_attempt_at")
    if last_attempt_str:
        try:
            # Parse '2026-10-19 08:52:10 UTC'
            last_dt = datetime.strptime(last_attempt_str.replace(" UTC", ""), "%Y-%m-%d %H:%M:%S").replace(tzinfo=timezone.utc)
            elapsed_sec = (datetime.now(timezone.utc) - last_dt).total_seconds()
            if elapsed_sec < 150:
                print(f"[COOLDOWN] Only {int(elapsed_sec)}s elapsed since last job attempt (min 150s). Skipping to protect rate-limit.")
                sys.exit(0)
        except Exception as e:
            print(f"[WARN] Failed to parse last_attempt_at: {e}")

    try:
        rm = get_rm_client()
    except Exception as e:
        print(f"[AUTH ERROR] {e}")
        state["status"] = "AUTH_ERROR"
        state["last_message"] = str(e)
        save_state(state)
        sys.exit(1)

    # 1. Fetch recent jobs
    try:
        jobs_response = rm.list_jobs(stack_id=STACK_OCID, sort_by="TIMECREATED", sort_order="DESC", limit=3)
        jobs = jobs_response.data
    except Exception as e:
        print(f"[OCI ERROR] Failed to query stack jobs: {e}")
        err_str = str(e)
        if "429" in err_str or "TooManyRequests" in err_str:
            clean_msg = "OCI 429 Too Many Requests (waiting for cooldown)"
            state["status"] = "RATE_LIMIT"
        else:
            clean_msg = err_str[:120]
            state["status"] = "API_ERROR"
        state["last_message"] = clean_msg
        save_state(state)
        sys.exit(1)

    # 2. Check for active/running job
    active_job = None
    for j in jobs:
        if j.lifecycle_state in ["ACCEPTED", "IN_PROGRESS", "CANCELING"]:
            active_job = j
            break

    if active_job:
        print(f"[INFO] Job {active_job.id} is currently {active_job.lifecycle_state}. Waiting.")
        state["status"] = "JOB_IN_PROGRESS"
        state["last_job_ocid"] = active_job.id
        state["last_message"] = f"Job {active_job.id[-8:]} is {active_job.lifecycle_state}"
        save_state(state)
        sys.exit(0)

    # 3. Inspect latest completed job
    if jobs:
        latest = jobs[0]
        state["last_job_ocid"] = latest.id

        if latest.lifecycle_state == "SUCCEEDED":
            alloc_time = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
            print(f"[SUCCESS] Stack apply job SUCCEEDED! Allocation confirmed at {alloc_time}")
            state["status"] = "ALLOCATED"
            state["allocated_at"] = alloc_time
            state["last_status"] = "SUCCEEDED"
            state["last_message"] = "Capacity acquired! Stack applied successfully."
            save_state(state)

            # Send FINAL SUCCESS ALERT
            subject = f"🎉 [SUCCESS] OCI Stack Allocated! ({REGION})"
            html = f"""
            <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #0b0c10; color: #f0f0f0; padding: 24px; border-radius: 12px; border: 1px solid #1f2833; max-width: 600px; margin: 0 auto;">
                <div style="border-bottom: 2px solid #89ff00; padding-bottom: 12px; margin-bottom: 20px;">
                    <h2 style="color: #89ff00; margin: 0; font-size: 20px;">🎉 OCI Stack Allocation Succeeded!</h2>
                    <p style="color: #c5c6c7; font-size: 13px; margin: 4px 0 0;">Capacity has been secured in <strong>{REGION}</strong>.</p>
                </div>
                <table style="width: 100%; border-collapse: collapse; font-size: 13px; margin-bottom: 20px;">
                    <tr><td style="color: #888; padding: 6px 0;">Allocation Time:</td><td style="font-weight: bold; color: #fff;">{alloc_time}</td></tr>
                    <tr><td style="color: #888; padding: 6px 0;">Total Retries:</td><td style="font-weight: bold; color: #89ff00;">{state['total_attempts']}</td></tr>
                    <tr><td style="color: #888; padding: 6px 0;">Stack OCID:</td><td style="font-family: monospace; font-size: 11px; word-break: break-all; color: #aaa;">{STACK_OCID}</td></tr>
                    <tr><td style="color: #888; padding: 6px 0;">Job OCID:</td><td style="font-family: monospace; font-size: 11px; word-break: break-all; color: #aaa;">{latest.id}</td></tr>
                </table>
                <div style="background-color: #121418; border-left: 3px solid #89ff00; padding: 12px; font-size: 12px; color: #c5c6c7;">
                    The automated retry runner has safely self-terminated. You can now access your new instance in the Oracle Cloud Console.
                </div>
            </div>
            """
            send_email(subject, html)

            # Self-terminate systemd timer
            os.system("sudo systemctl disable --now oci-allocator.timer 2>/dev/null || true")
            sys.exit(0)

        elif latest.lifecycle_state == "FAILED":
            state["last_status"] = "FAILED"
            msg = getattr(latest, "lifecycle_details", "") or "Out of host capacity (or execution error)"
            state["last_message"] = msg
            print(f"[RETRY] Last job failed ({msg}). Triggering next attempt...")
            # If the latest job hasn't been recorded in recent_attempts yet
            if not state.get("recent_attempts") or state["recent_attempts"][0].get("job_id") != latest.id[-10:]:
                record_attempt(state, "FAILED", msg, latest.id)

    # 4. Trigger New Apply Job
    try:
        from oci.resource_manager.models import CreateJobDetails, CreateApplyJobOperationDetails
        
        job_details = CreateJobDetails(
            stack_id=STACK_OCID,
            display_name=f"auto-alloc-{int(time.time())}",
            operation="APPLY",
            job_operation_details=CreateApplyJobOperationDetails(
                operation="APPLY",
                execution_plan_strategy="AUTO_APPROVED"
            )
        )
        new_job = rm.create_job(job_details).data
        state["total_attempts"] += 1
        state["attempts_today"] += 1
        state["last_attempt_at"] = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
        state["last_job_ocid"] = new_job.id
        state["status"] = "SEARCHING"
        state["last_message"] = f"Created apply job {new_job.id[-8:]} (Waiting for result)"
        record_attempt(state, "TRIGGERED", "Apply job created, waiting for OCI capacity evaluation", new_job.id)
        print(f"[TRIGGER] Created apply job {new_job.id[-8:]} (Attempt #{state['total_attempts']}, #{state['attempts_today']} today)")
    except Exception as e:
        print(f"[TRIGGER ERROR] Failed to create job: {e}")
        err_msg = str(e)
        if "429" in err_msg or "TooManyRequests" in err_msg:
            clean_msg = "OCI 429 Too Many Requests (waiting for cooldown)"
        elif "Out of host capacity" in err_msg:
            clean_msg = "Out of host capacity in ap-hyderabad-1"
        else:
            clean_msg = err_msg[:120]
        state["last_message"] = clean_msg
        record_attempt(state, "RATE_LIMIT" if "429" in err_msg else "ERROR", clean_msg)

    # 5. Daily Email Check (Once per calendar day)
    if state.get("last_daily_email_date") != today:
        now_hour = datetime.now(timezone.utc).hour
        # Send once per day (preferred around 08:00 UTC or if first run of the day)
        if now_hour >= 8 or state.get("last_daily_email_date") is None:
            subject = f"⏳ [OCI Capacity] Daily Status: {state['total_attempts']} attempts ({REGION})"
            html = f"""
            <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #0b0c10; color: #f0f0f0; padding: 24px; border-radius: 12px; border: 1px solid #1f2833; max-width: 600px; margin: 0 auto;">
                <div style="border-bottom: 2px solid #f59e0b; padding-bottom: 12px; margin-bottom: 20px;">
                    <h2 style="color: #f59e0b; margin: 0; font-size: 18px;">⏳ OCI Capacity Hunter Daily Digest</h2>
                    <p style="color: #c5c6c7; font-size: 13px; margin: 4px 0 0;">Still searching for host capacity in <strong>{REGION}</strong>.</p>
                </div>
                <table style="width: 100%; border-collapse: collapse; font-size: 13px; margin-bottom: 20px;">
                    <tr><td style="color: #888; padding: 6px 0;">Date:</td><td style="font-weight: bold; color: #fff;">{today} (UTC)</td></tr>
                    <tr><td style="color: #888; padding: 6px 0;">Attempts Today:</td><td style="font-weight: bold; color: #f59e0b;">{state['attempts_today']}</td></tr>
                    <tr><td style="color: #888; padding: 6px 0;">Total Retries:</td><td style="font-weight: bold; color: #fff;">{state['total_attempts']}</td></tr>
                    <tr><td style="color: #888; padding: 6px 0;">Latest Status:</td><td style="color: #f87171;">{state['last_message']}</td></tr>
                    <tr><td style="color: #888; padding: 6px 0;">Last Attempt:</td><td style="color: #aaa;">{state.get('last_attempt_at', 'N/A')}</td></tr>
                </table>
                <div style="background-color: #121418; border-left: 3px solid #f59e0b; padding: 12px; font-size: 12px; color: #c5c6c7;">
                    The lean runner continues every 2 minutes. You will receive an immediate notification as soon as an instance is successfully provisioned.
                </div>
            </div>
            """
            if send_email(subject, html):
                state["last_daily_email_date"] = today

    save_state(state)

def send_manual_digest():
    state = load_state()
    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    subject = f"⏳ [OCI Capacity] Manual Status Digest: {state['total_attempts']} attempts ({REGION})"
    html = f"""
    <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #0b0c10; color: #f0f0f0; padding: 24px; border-radius: 12px; border: 1px solid #1f2833; max-width: 600px; margin: 0 auto;">
        <div style="border-bottom: 2px solid #89ff00; padding-bottom: 12px; margin-bottom: 20px;">
            <h2 style="color: #89ff00; margin: 0; font-size: 18px;">⚡ OCI Capacity Hunter Manual Digest</h2>
            <p style="color: #c5c6c7; font-size: 13px; margin: 4px 0 0;">Manual report trigger for <strong>{REGION}</strong>.</p>
        </div>
        <table style="width: 100%; border-collapse: collapse; font-size: 13px; margin-bottom: 20px;">
            <tr><td style="color: #888; padding: 6px 0;">Current Status:</td><td style="font-weight: bold; color: #fff;">{state.get('status', 'SEARCHING')}</td></tr>
            <tr><td style="color: #888; padding: 6px 0;">Attempts Today:</td><td style="font-weight: bold; color: #f59e0b;">{state.get('attempts_today', 0)}</td></tr>
            <tr><td style="color: #888; padding: 6px 0;">Total Retries:</td><td style="font-weight: bold; color: #fff;">{state.get('total_attempts', 0)}</td></tr>
            <tr><td style="color: #888; padding: 6px 0;">Latest Output:</td><td style="color: #89ff00;">{state.get('last_message', 'Active')}</td></tr>
            <tr><td style="color: #888; padding: 6px 0;">Last Attempt:</td><td style="color: #aaa;">{state.get('last_attempt_at', 'N/A')}</td></tr>
        </table>
    </div>
    """
    if send_email(subject, html):
        print("[SUCCESS] Manual email sent successfully.")
        return True
    return False

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--send-mail":
        send_manual_digest()
    else:
        run()
