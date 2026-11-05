#!/usr/bin/env bash

set -u

IPC_DIR="${IPC_DIR:-/home/ubuntu/stack/ops/ipc}"
mkdir -p "$IPC_DIR"

while true; do
  for req in "$IPC_DIR"/*.req; do
    [[ -f "$req" ]] || continue

    read -r control_type action service < "$req"
    response="${req%.req}.res"
    output=''
    exit_code=1

    if [[ "$action" =~ ^(start|stop|restart)$ ]]; then
      if [[ "$control_type" == systemctl && "$service" =~ ^(cs-panel|mc-manager|nginx|nexus-backend)$ ]]; then
        output=$(systemctl "$action" "${service}.service" 2>&1)
        exit_code=$?
      elif [[ "$control_type" == docker && "$service" =~ ^(stalwart-mail|macos-server-blue|personal-site-rust)$ ]]; then
        output=$(docker "$action" "$service" 2>&1)
        exit_code=$?
      else
        output="Invalid control type '$control_type' or service '$service'"
      fi
    else
      output="Invalid action '$action'"
    fi

    temp_response="${response}.tmp.$$"
    printf 'EXIT:%s\nOUTPUT:%s\n' "$exit_code" "$output" > "$temp_response"
    mv "$temp_response" "$response"
    rm -f "$req"
  done
  sleep 0.5
done