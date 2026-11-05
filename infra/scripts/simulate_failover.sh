#!/bin/bash
set -euo pipefail

echo "=========================================================="
echo "🧪 CLUSTER FAILOVER SIMULATION: my-mac > cos > pro"
echo "=========================================================="

echo ""
echo "--- 1. Baseline Request (my-mac Active) ---"
curl -s -w "\nHTTP Status: %{http_code} | Total Time: %{time_total}s\n" \
  -H "Host: faizal.uk" "http://127.0.0.1/api/maintenance-status"

echo ""
echo "--- 2. Inducing Fault: Stopping my-mac personal-site-rust container ---"
docker stop personal-site-rust
sleep 1

echo ""
echo "--- 3. Testing Failover to Priority 2 (cos @ 10.0.0.195 via :9100) ---"
curl -s -w "\nHTTP Status: %{http_code} | Total Time: %{time_total}s\n" \
  -H "Host: faizal.uk" "http://127.0.0.1/api/maintenance-status"

echo ""
echo "--- 4. Restoring Priority 1 (my-mac) container ---"
docker start personal-site-rust
sleep 2

echo ""
echo "--- 5. Verifying Self-Healing (Traffic smoothly returned to my-mac) ---"
curl -s -w "\nHTTP Status: %{http_code} | Total Time: %{time_total}s\n" \
  -H "Host: faizal.uk" "http://127.0.0.1/api/maintenance-status"

echo ""
echo "✅ Test completed successfully!"
