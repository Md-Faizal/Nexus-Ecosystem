#!/usr/bin/env bash
# Helper CLI for Stalwart Mail Server
cd "$(dirname "$0")"

if ! command -v docker &> /dev/null; then
    echo "Docker is not installed or not in PATH."
    exit 1
fi

docker compose exec -T -u stalwart stalwart-mail stalwart-cli "$@"
