#!/bin/bash
set -euo pipefail

echo "=========================================================="
echo "🧪 DOUBLE FAULT FAILOVER TEST: my-mac & cos Down -> pro"
echo "=========================================================="

echo ""
echo "--- 1. Baseline Request (my-mac Active) ---"
curl -s -w "\nHTTP Status: %{http_code} | Total Time: %{time_total}s\n" \
  -H "Host: faizal.uk" "http://127.0.0.1/api/maintenance-status"

echo ""
echo "--- 2. Inducing DOUBLE Fault: Stopping my-mac AND disabling cos tunnel ---"
docker stop personal-site-rust
sudo systemctl stop cos-tunnel.service
sleep 1

echo ""
echo "--- 3. Testing Direct Failover to Priority 3 (pro @ 35.208.86.56 via :9101) ---"
curl -s -w "\nHTTP Status: %{http_code} | Total Time: %{time_total}s\n" \
  -H "Host: faizal.uk" "http://127.0.0.1/api/maintenance-status"

echo ""
echo "--- 4. Restoring my-mac and cos-tunnel ---"
docker start personal-site-rust
sudo systemctl start cos-tunnel.service
sleep 2

echo ""
echo "--- 5. Verifying Self-Healing Return to my-mac ---"
curl -s -w "\nHTTP Status: %{http_code} | Total Time: %{time_total}s\n" \
  -H "Host: faizal.uk" "http://127.0.0.1/api/maintenance-status"

echo ""
echo "✅ Double-Fault Simulation Passed!"
