# Stalwart All-in-One Mail & JMAP Server

Modern, high-performance, single-binary mail server written in Rust with native JMAP, IMAP4, SMTP, and Web Admin dashboard.

## Services & Ports
- **JMAP & Web Admin UI:** `https://mailadmin.faizal.uk/admin` or `http://127.0.0.1:8080/admin`
- **Inbound SMTP:** Port `25`
- **SMTPS (TLS):** Port `465`
- **Submission (STARTTLS):** Port `587`
- **IMAPS:** Port `993`
- **POP3S:** Port `995`

## Default Admin Credentials
- **Username:** `admin`
- **Password:** `FaizalAdmin2026!` (Configured via `STALWART_RECOVERY_ADMIN`)

## Local Automated Emails (From other apps in the same VM)
- **Host:** `127.0.0.1`
- **Port:** `25` (Direct internal relay without SSL) or `587` (Submission)
- **Authentication:** Use any provisioned mailbox or configure trusted network `127.0.0.1/32` inside Stalwart settings.
