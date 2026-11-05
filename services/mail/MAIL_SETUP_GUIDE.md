# Faizal.uk Mail Server — Complete Operational & Credentials Guide

This document contains all details, server credentials, network configurations, DNS settings, and management commands for the Docker Mailserver deployed on the production VM (`my-mac`).

---

## 1. Active Credentials & Account Details

| Field | Value |
| :--- | :--- |
| **Email Address** | `me@faizal.uk` |
| **Username** | `me@faizal.uk` |
| **Password** | `FaizalMail2026!` |
| **Mail Domain** | `faizal.uk` |
| **Server Hostname** | `mail.faizal.uk` |
| **Server IP Address** | `68.233.115.138` |
| **SSL Certificate** | Valid Let's Encrypt CA (`mail.faizal.uk`) |

---

## 2. Mail Client Connection Settings

### For Native Apps (Outlook, Windows Mail, Apple Mail, Thunderbird, Android)

#### Incoming Mail (IMAP4)
* **Server**: `mail.faizal.uk`
* **Port**: `993`
* **Encryption / Security**: `SSL/TLS`
* **Username**: `me@faizal.uk`
* **Password**: `FaizalMail2026!`

#### Outgoing Mail (SMTP)
* **Server**: `mail.faizal.uk`
* **Port**: `465` (SSL/TLS) or `587` (STARTTLS)
* **Encryption / Security**: `SSL/TLS` (for 465) or `STARTTLS` (for 587)
* **Username**: `me@faizal.uk`
* **Password**: `FaizalMail2026!`
* **Authentication Required**: `Yes` (same as incoming)

---

### For Gmail Web Import ("Check mail from other accounts")
* **POP Server**: `mail.faizal.uk`
* **Port**: `995` *(Note: Must be 995, not 993)*
* **Username**: `me@faizal.uk`
* **Password**: `FaizalMail2026!`
* **Always use a secure connection (SSL)**: `Checked (Yes)`
* **Leave a copy of retrieved message on the server**: `Checked (Yes)`

---

## 3. Server Ports Reference

| Port | Protocol | Purpose | Status |
| :--- | :--- | :--- | :--- |
| **25** | TCP | SMTP (Incoming mail delivery from external servers) | Active |
| **465** | TCP | SMTPS (Secure mail submission over TLS) | Active |
| **587** | TCP | SMTP Submission (STARTTLS) | Active |
| **993** | TCP | IMAPS (Secure mail access for phone/desktop clients) | Active |
| **995** | TCP | POP3S (Secure POP3 for Gmail / Outlook Import) | Active |

> [!IMPORTANT]
> Ensure ports `25, 465, 587, 993, 995` are all included in your **Oracle Cloud Security List Ingress Rules**.

---

## 4. Cloudflare DNS Configuration

Configure these 5 records inside your **Cloudflare Dashboard** under `faizal.uk` -> **DNS**:

| Type | Name | Target / Content | Proxy Status | Priority | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **A** | `mail` | `68.233.115.138` | **DNS Only** (Grey Cloud) | - | Do **not** proxy mail traffic |
| **MX** | `@` | `mail.faizal.uk` | DNS Only | `10` | Inbound delivery |
| **TXT** | `@` | `v=spf1 mx a:mail.faizal.uk ~all` | DNS Only | - | SPF Authentication |
| **TXT** | `_dmarc` | `v=DMARC1; p=none; rua=mailto:postmaster@faizal.uk` | DNS Only | - | DMARC Policy |
| **TXT** | `mail._domainkey` | *(See DKIM value below)* | DNS Only | - | DKIM Signature |

### DKIM TXT Record Content:
```text
v=DKIM1; h=sha256; k=rsa; p=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1v+JsDUKAWvO+K1Ab7okSi37lbcgfRimJpjREJNh4JDIj/p4WyEs9XRf3kOSWXB2+YHvtxR2MjNxmhQSuNFdmb2wKZ53jSJDnX0Py3bMEXldJOlzEQnRvJWjVdsugcMDkjwL399NGMQOILmwfU4vgU28BXxTxLx9ukJX7EMvRvULKaWKHTumWIfQiOEFvkXMWYzmTwLuglArEDK63vQ3IKyeC12V8+ktjsSitdbotVh0q6KrxzGzXUOJA7zGW2W4lF6Xm6TW8zbo/728sTHoXrLnNQYsyeovBM7ZOHIhHX0koXgFIe/DTCwK8V3trZ3Dr6D4DruGtwL6OWIRLB+y0wIDAQAB
```

---

## 5. Administration & Management Commands

All commands can be executed remotely from PowerShell or terminal:

### Add a New Email Account
```bash
ssh my-mac "cd /home/ubuntu/faizal.uk/mail && ./setup.sh email add name@faizal.uk 'DesiredPassword'"
```

### Change an Account Password
```bash
ssh my-mac "cd /home/ubuntu/faizal.uk/mail && ./setup.sh email update me@faizal.uk 'NewPassword'"
```

### List All Active Email Accounts
```bash
ssh my-mac "cd /home/ubuntu/faizal.uk/mail && ./setup.sh email list"
```

### Delete an Email Account
```bash
ssh my-mac "cd /home/ubuntu/faizal.uk/mail && ./setup.sh email del user@faizal.uk"
```

### Check Server Logs (Real-time)
```bash
ssh my-mac "docker compose -f /home/ubuntu/faizal.uk/mail/docker-compose.yml logs -f mailserver"
```

### Check Container Status & Resource Usage
```bash
ssh my-mac "docker compose -f /home/ubuntu/faizal.uk/mail/docker-compose.yml ps"
ssh my-mac "docker stats --no-stream mailserver"
```

### Restart Mail Server
```bash
ssh my-mac "cd /home/ubuntu/faizal.uk/mail && docker compose restart"
```

---

## 6. Directory Layout on Production VM (`my-mac`)

Location: `/home/ubuntu/faizal.uk/mail/`

* `docker-compose.yml`: Container and port definitions.
* `mailserver.env`: Configuration options (Memory limits, Let's Encrypt SSL, POP3, Spam/Fail2ban settings).
* `setup.sh`: Management CLI script.
* `config/`: Persistent server configuration and DKIM keys (`config/opendkim/`).
* `mail-data/`: Actual stored mailboxes and messages.
* `mail-state/`: State files (account database, sieve scripts).
* `mail-logs/`: Server log files (`mail.log`, `fail2ban.log`).
* `admin/`: Ultra-lightweight Web Control Center daemon (`server.py`, `index.html`).

---

## 7. Mail Control Center (Web Admin & Webmail Dashboard)

An ultra-lightweight (~16 MB RAM), zero-dependency web control dashboard is running 24/7 on the VM under PM2.

### Access URLs & Master Key
* **Primary URL**: [https://mailadmin.faizal.uk](https://mailadmin.faizal.uk)
* **Secondary URL**: [https://faizal.uk/mailadmin](https://faizal.uk/mailadmin)
* **Master Admin Key**: `FaizalAdmin2026!`

### Features Included
1. **Live Dashboard Telemetry**:
   - Real-time container health, CPU utilization, and exact RAM usage.
   - Status checks for all mail ports (25, 465, 587, 993, 995).
   - 1-Click Container Restart and Postfix Queue Flush.
2. **Mailbox Administration**:
   - Create mailboxes with custom storage quotas (MB/GB/unlimited).
   - Reset passwords on demand with built-in cryptographic password generator.
   - Live quota usage indicator and percentage meters.
   - Delete mailboxes.
3. **Aliases & Forwarding**:
   - Create forwarding addresses (e.g. `support@faizal.uk -> me@faizal.uk`).
   - Create catch-all routing (`@faizal.uk -> destination`).
   - Remove aliases.
4. **Security & Fail2ban**:
   - Real-time status for all jails (`postfix`, `dovecot`, `custom`).
   - List active banned IP addresses with 1-click Unban.
   - Manual IP ban and unban tool.
5. **Built-in Webmail Client**:
   - Read inbox messages instantly without needing to enter a mailbox password (uses secure Maildir direct disk access).
   - Compose & deliver outbound emails (uses local Postfix submission or SMTPS).
   - HTML & plain text message viewer with attachment display.
6. **DNS & DKIM Assistant**:
   - Reads your active DKIM key directly from `/config/opendkim/keys/faizal.uk/mail.txt`.
   - Displays all required DNS records with 1-click "Copy" buttons.
7. **Logs & Postfix Queue Manager**:
   - Live stream of `mail.log` with instant search filter.
   - Inspect deferred / active outbound queue messages with delay reasons.
   - Delete individual messages or purge the entire queue.
