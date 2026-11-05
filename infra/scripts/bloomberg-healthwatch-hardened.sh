#!/bin/bash
set -euo pipefail

################################################################################
# Autonomous Health Watchdog for Bloomberg Terminal (Hardened)
#
# - Monitors active slot health every 45s via systemd timer
# - Self-healing failover: if active slot fails, automatically validates
#   and fails over to the standby slot with zero downtime
# - Container self-healing with 5-minute circuit breaker to prevent CPU thrashing
# - Heartbeat & deadman telemetry support
################################################################################

NGINX_UPSTREAM_CONF="${NGINX_UPSTREAM_CONF:-/etc/nginx/conf.d/bloomberg-terminal-upstream.conf}"
BLUE_PORT="${BLUE_PORT:-18081}"
GREEN_PORT="${GREEN_PORT:-18082}"
CHECK_TIMEOUT="${CHECK_TIMEOUT:-4}"
FAILURE_THRESHOLD="${FAILURE_THRESHOLD:-3}"
SUCCESS_THRESHOLD="${SUCCESS_THRESHOLD:-2}"
APP_DIR="${APP_DIR:-/home/ubuntu/stack/apps/bloomberg-terminal}"
COMPOSE_FILE="${COMPOSE_FILE:-$APP_DIR/docker-compose.yml}"
STATE_DIR="${STATE_DIR:-/tmp/bloomberg-healthwatch}"
HEARTBEAT_FILE="${HEALTHWATCH_HEARTBEAT_FILE:-/var/run/bloomberg-healthwatch.heartbeat}"
DEADMAN_ALERT_URL="${HEALTHWATCH_ALERT_URL:-}"
DEADMAN_ALERT_COMMAND="${HEALTHWATCH_ALERT_COMMAND:-}"
RECREATE_COOLDOWN_SECONDS=300

mkdir -p "$STATE_DIR"
mkdir -p "$(dirname "$HEARTBEAT_FILE")"
ACTIVE_FAILURES_FILE="$STATE_DIR/active_failures"
STANDBY_SUCCESS_FILE="$STATE_DIR/standby_successes"
LAST_RECREATE_FILE="$STATE_DIR/last_recreate_epoch"

log() {
    echo "[$(date -u +'%Y-%m-%dT%H:%M:%SZ')] [Bloomberg-Watchdog] $*"
}

write_heartbeat() {
    echo "$(date -u +%s)" > "$HEARTBEAT_FILE"
    if [ -d "$APP_DIR/logs" ]; then
        echo "$(date -u +%s)" > "$APP_DIR/logs/healthwatch.heartbeat" 2>/dev/null || true
    fi
}

trigger_deadman_alert() {
    local payload
    payload=$(printf '{"service":"bloomberg-healthwatch","status":"deadman","timestamp":"%s","heartbeat_file":"%s"}' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" "$HEARTBEAT_FILE")

    if [ -n "$DEADMAN_ALERT_URL" ]; then
        curl -fsS --max-time 10 -H 'Content-Type: application/json' -d "$payload" "$DEADMAN_ALERT_URL" >/dev/null 2>&1 || true
    fi

    if [ -n "$DEADMAN_ALERT_COMMAND" ]; then
        bash -lc "$DEADMAN_ALERT_COMMAND" >/dev/null 2>&1 || true
    fi
}

read_count() {
    local file="$1"
    if [ -f "$file" ]; then
        cat "$file" 2>/dev/null || echo 0
    else
        echo 0
    fi
}

write_count() {
    local file="$1"
    local value="$2"
    printf '%s' "$value" > "$file"
}

get_active_slot() {
    if [ -f "$NGINX_UPSTREAM_CONF" ] && grep -q "127.0.0.1:$GREEN_PORT" "$NGINX_UPSTREAM_CONF"; then
        echo "green"
    else
        echo "blue"
    fi
}

get_standby_slot() {
    local active
    active="$(get_active_slot)"
    if [ "$active" = "green" ]; then
        echo "blue"
    else
        echo "green"
    fi
}

get_port_for_slot() {
    local slot="$1"
    if [ "$slot" = "green" ]; then
        echo "$GREEN_PORT"
    else
        echo "$BLUE_PORT"
    fi
}

check_health_endpoint() {
    local url="$1"
    local tmp_file
    tmp_file="/tmp/bloomberg-healthwatch-response.$$"
    local code
    code=$(curl -sS --max-time "$CHECK_TIMEOUT" -o "$tmp_file" -w '%{http_code}' "$url" 2>/dev/null || true)
    if [ "$code" = "200" ]; then
        rm -f "$tmp_file"
        return 0
    fi
    rm -f "$tmp_file"
    return 1
}

check_slot() {
    local slot="$1"
    local port
    port="$(get_port_for_slot "$slot")"
    local health_url="http://127.0.0.1:${port}/health/ready"

    if check_health_endpoint "$health_url"; then
        return 0
    fi

    # Fallback probe to root index
    local index_url="http://127.0.0.1:${port}/"
    if check_health_endpoint "$index_url"; then
        return 0
    fi

    return 1
}

switch_nginx_to_slot() {
    local target_slot="$1"
    local target_port
    target_port="$(get_port_for_slot "$target_slot")"

    log "Switching upstream to $target_slot on port $target_port"

    cat <<EOF > /tmp/upstream_bloomberg.conf
# Managed by bloomberg-healthwatch.sh
upstream bloomberg_terminal_active {
    server 127.0.0.1:${target_port} max_fails=2 fail_timeout=5s;
    keepalive 32;
}
EOF

    sudo cp /tmp/upstream_bloomberg.conf "$NGINX_UPSTREAM_CONF"
    sudo chmod 644 "$NGINX_UPSTREAM_CONF"
    if [ -d "$APP_DIR/logs" ]; then
        cp /tmp/upstream_bloomberg.conf "$APP_DIR/logs/upstream_bloomberg.conf" 2>/dev/null || true
    fi
    rm -f /tmp/upstream_bloomberg.conf
    sudo nginx -t
    sudo systemctl reload nginx || sudo nginx -s reload
}

can_recreate_container() {
    local now last_recreate elapsed
    now=$(date +%s)
    last_recreate=$(read_count "$LAST_RECREATE_FILE")
    elapsed=$(( now - last_recreate ))

    if [ "$elapsed" -lt "$RECREATE_COOLDOWN_SECONDS" ]; then
        log "Circuit breaker active: Last container recreate was ${elapsed}s ago (cooldown ${RECREATE_COOLDOWN_SECONDS}s). Skipping recreate to prevent CPU thrashing."
        return 1
    fi
    return 0
}

attempt_recreate_slot() {
    local slot="$1"
    if ! can_recreate_container; then
        return 0
    fi
    log "Attempting container start/recreation for slot $slot"
    write_count "$LAST_RECREATE_FILE" "$(date +%s)"
    if [ -f "$COMPOSE_FILE" ]; then
        docker compose -f "$COMPOSE_FILE" up -d --no-deps "bloomberg-api-$slot" "bloomberg-web-$slot" >/dev/null 2>&1 || true
    fi
}

main() {
    local active_slot standby_slot active_port standby_port
    local active_failures standby_successes
    local heartbeat_age

    write_heartbeat

    active_slot="$(get_active_slot)"
    standby_slot="$(get_standby_slot)"
    active_port="$(get_port_for_slot "$active_slot")"
    standby_port="$(get_port_for_slot "$standby_slot")"

    # 0. Check if active container is intentionally stopped (NOT running)
    local active_container_status
    active_container_status=$(docker inspect -f '{{.State.Status}}' "bloomberg-web-$active_slot" 2>/dev/null || echo "not_found")
    if [ "$active_container_status" = "exited" ] || [ "$active_container_status" = "created" ]; then
        log "Active slot $active_slot container is in '$active_container_status' state (stopped by operator). Respecting stopped state, skipping auto-recovery."
        exit 0
    fi

    # 1. Check active slot
    if check_slot "$active_slot"; then
        write_count "$ACTIVE_FAILURES_FILE" 0
        log "Active slot $active_slot on $active_port is healthy. All systems nominal."
        exit 0
    fi

    active_failures=$(($(read_count "$ACTIVE_FAILURES_FILE") + 1))
    write_count "$ACTIVE_FAILURES_FILE" "$active_failures"
    log "Active slot $active_slot on $active_port failed health check #${active_failures}/${FAILURE_THRESHOLD}."

    if [ "$active_failures" -lt "$FAILURE_THRESHOLD" ]; then
        exit 0
    fi

    # 2. Active slot failed threshold times. Check if standby slot is healthy
    if check_slot "$standby_slot"; then
        standby_successes=$(($(read_count "$STANDBY_SUCCESS_FILE") + 1))
        write_count "$STANDBY_SUCCESS_FILE" "$standby_successes"
        log "Standby slot $standby_slot on $standby_port is healthy and stable ${standby_successes}/${SUCCESS_THRESHOLD}."

        if [ "$standby_successes" -ge "$SUCCESS_THRESHOLD" ]; then
            write_count "$ACTIVE_FAILURES_FILE" 0
            write_count "$STANDBY_SUCCESS_FILE" 0
            switch_nginx_to_slot "$standby_slot"
            exit 0
        fi

        exit 0
    fi

    # 3. Both slots unhealthy or standby stopped. Attempt auto-recovery
    write_count "$STANDBY_SUCCESS_FILE" 0
    log "Attempting recovery of active slot $active_slot..."
    attempt_recreate_slot "$active_slot"
    sleep 3

    if check_slot "$active_slot"; then
        write_count "$ACTIVE_FAILURES_FILE" 0
        log "Recovered active slot $active_slot on $active_port successfully."
        exit 0
    fi

    log "Active slot recovery failed. Attempting recovery of standby slot $standby_slot..."
    attempt_recreate_slot "$standby_slot"
    sleep 3

    if check_slot "$standby_slot"; then
        write_count "$ACTIVE_FAILURES_FILE" 0
        write_count "$STANDBY_SUCCESS_FILE" 0
        log "Standby slot $standby_slot on $standby_port is healthy after recovery; switching traffic."
        switch_nginx_to_slot "$standby_slot"
        exit 0
    fi

    log "ERROR: Neither active nor standby slot could be recovered after ${FAILURE_THRESHOLD} consecutive failures."
    exit 1
}

main "$@"
