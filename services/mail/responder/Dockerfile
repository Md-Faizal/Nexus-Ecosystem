# Nexus Fleet Mail Responder Container
FROM python:3.12-alpine

# Set unbuffered logging so container logs stream in real-time
ENV PYTHONUNBUFFERED=1

WORKDIR /app

# The daemon uses only Python standard library (sqlite3, imaplib, smtplib, email, json, etc.)
# No external pip dependencies required

# Copy service files
COPY config.json exclusions.json main.py ./
COPY templates/ ./templates/
COPY assets/ ./assets/

# Prepare persistent state directory
RUN mkdir -p /app/state

VOLUME ["/app/state"]

CMD ["python", "main.py"]
