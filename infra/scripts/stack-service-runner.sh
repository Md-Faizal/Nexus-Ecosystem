#!/usr/bin/env bash

set -u

IPC_DIR="${IPC_DIR:-/home/ubuntu/stack/ops/ipc}"
mkdir -p "$IPC_DIR"

ssh_options=(-F /home/ubuntu/.ssh/config
  -o BatchMode=yes \
  -o ConnectTimeout=5 \
  -o StrictHostKeyChecking=yes \
  -o UserKnownHostsFile=/home/ubuntu/.ssh/known_hosts \
  -o IdentitiesOnly=yes)

while true; do
  for req in "$IPC_DIR"/*.req; do
    [[ -f "$req" ]] || continue

    read -r control_type action service extra < "$req"
    extra="${extra:-}"
    response="${req%.req}.res"
    output=''
    exit_code=1

    # ─── Cluster-Wide Orchestration Actions ─────────────────────────────────────
    if [[ "$control_type" == "cluster" ]]; then
      case "$action" in
        sync-db)
          if [[ -f /home/ubuntu/scripts/cluster-db-sync.sh ]]; then
            output=$(su - ubuntu -c "bash /home/ubuntu/scripts/cluster-db-sync.sh" 2>&1)
            exit_code=$?
          else
            output="Error: /home/ubuntu/scripts/cluster-db-sync.sh not found"
            exit_code=1
          fi
          ;;

        restart-swarm)
          out_local=$(docker restart personal-site-rust 2>&1 || true)
          out_cos=$(su - ubuntu -c "ssh -F /home/ubuntu/.ssh/config container-of-souls 'docker restart personal-site-rust'" 2>&1 || true)
          out_pro=$(su - ubuntu -c "ssh -F /home/ubuntu/.ssh/config prometheus 'docker restart personal-site-rust'" 2>&1 || true)
          output="my-mac: $out_local | cos: $out_cos | pro: $out_pro"
          exit_code=0
          ;;

        restart-node)
          target_node="${service:-my-mac}"
          case "$target_node" in
            my-mac)
              output=$(docker restart personal-site-rust 2>&1)
              exit_code=$?
              ;;
            cos|container-of-souls)
              output=$(su - ubuntu -c "ssh -F /home/ubuntu/.ssh/config container-of-souls 'docker restart personal-site-rust'" 2>&1)
              exit_code=$?
              ;;
            pro|prometheus)
              output=$(su - ubuntu -c "ssh -F /home/ubuntu/.ssh/config prometheus 'docker restart personal-site-rust'" 2>&1)
              exit_code=$?
              ;;
            *)
              output="Unknown node: $target_node"
              exit_code=1
              ;;
          esac
          ;;

        flush-dns)
          output=$(resolvectl flush-caches 2>&1 || systemctl restart systemd-resolved 2>&1)
          exit_code=$?
          ;;

        test-routes)
          t_local=$(curl -s -o /dev/null -w "%{time_total}" -m 2 http://127.0.0.1:3002/stats 2>/dev/null || echo "timeout")
          t_cos=$(curl -s -o /dev/null -w "%{time_total}" -m 2 http://127.0.0.1:9100/stats 2>/dev/null || echo "timeout")
          t_pro=$(curl -s -o /dev/null -w "%{time_total}" -m 2 http://127.0.0.1:9101/stats 2>/dev/null || echo "timeout")
          output="Route Latencies: my-mac=${t_local}s, cos=${t_cos}s, pro=${t_pro}s"
          exit_code=0
          ;;

        *)
          output="Unknown cluster action '$action'"
          exit_code=1
          ;;
      esac

    # ─── Remote Docker (container-of-souls or prometheus) ──────────────────────
    elif [[ "$control_type" == remote-docker && "$action" =~ ^(status|start|stop|restart)$ ]]; then
      if [[ "$service" == "bloomberg-terminal" ]]; then
        if [[ "$action" == status ]]; then
          output=$(su - ubuntu -c "ssh -F /home/ubuntu/.ssh/config container-of-souls bash /home/ubuntu/stack/apps/bloomberg-terminal/deploy/cos-blue-green.sh status" 2>&1)
          exit_code=$?
          if [[ "$exit_code" -eq 0 ]]; then
            output="$(tail -n 1 <<< "$output")"
          fi
        else
          output=$(su - ubuntu -c "ssh -F /home/ubuntu/.ssh/config container-of-souls bash /home/ubuntu/stack/apps/bloomberg-terminal/deploy/cos-blue-green.sh $action" 2>&1)
          exit_code=$?
        fi
      elif [[ "$service" =~ ^(bedrock-server|minecraft|mc-manager)$ ]]; then
        target_container="minecraft-bedrock-manager"
        if [[ "$action" == "status" ]]; then
          output=$(su - ubuntu -c "ssh -F /home/ubuntu/.ssh/config prometheus \"if docker inspect -f '{{.State.Running}}' '$target_container' 2>/dev/null | grep -qx true; then printf RUNNING; else printf STOPPED; fi\"" 2>&1)
          exit_code=$?
        else
          output=$(su - ubuntu -c "ssh -F /home/ubuntu/.ssh/config prometheus \"docker $action '$target_container'\"" 2>&1)
          exit_code=$?
        fi
      elif [[ "$service" == "pro-personal-site" || "$service" == "personal-site-pro" ]]; then
        target_container="personal-site-rust"
        output=$(su - ubuntu -c "ssh -F /home/ubuntu/.ssh/config prometheus \"docker $action '$target_container'\"" 2>&1)
        exit_code=$?
      elif [[ "$service" =~ ^(stalwart-mail|stalwart-mail-standby|macos-server-green|personal-site-rust|personal-site-cos)$ ]]; then
        target_container="$service"
        [[ "$service" == "personal-site-cos" ]] && target_container="personal-site-rust"
        output=$(su - ubuntu -c "ssh -F /home/ubuntu/.ssh/config container-of-souls \"docker $action $target_container 2>&1 || docker $action $service 2>&1\"" 2>&1)
        exit_code=$?
      else
        output="Unsupported remote-docker service '$service'"
      fi

    # ─── Local Docker / Systemctl on my-mac ─────────────────────────────────────
    elif [[ "$action" =~ ^(start|stop|restart)$ ]]; then
      if [[ "$control_type" == systemctl && "$service" =~ ^(cs-panel|nginx|nexus-backend|cockpit)$ ]]; then
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
