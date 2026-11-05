#!/usr/bin/env node
/**
 * Unified Health & 24h Supervision Report CLI Bridge
 * All health reporting, telemetry, and email engines are centralized in the Rust Axum backend.
 * This script forwards CLI/Systemd triggers directly to the Rust backend API.
 */
const http = require('http');

const PORT = process.env.PORT || 3002;
const args = process.argv.slice(2);

function request(path, method = 'GET', postData = null) {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: '127.0.0.1',
      port: PORT,
      path: path,
      method: method,
      headers: postData ? {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(postData)
      } : {}
    };

    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => resolve({ statusCode: res.statusCode, data }));
    });

    req.on('error', err => reject(err));
    if (postData) req.write(postData);
    req.end();
  });
}

async function main() {
  if (args.includes('--send')) {
    const toIdx = args.indexOf('--to');
    const customTo = toIdx !== -1 && args[toIdx + 1] ? args[toIdx + 1] : null;
    const body = JSON.stringify({ recipient: customTo });

    try {
      const res = await request('/api/health-report/send', 'POST', body);
      console.log(`[${new Date().toISOString()}] Response (${res.statusCode}): ${res.data}`);
      process.exit(res.statusCode === 200 ? 0 : 1);
    } catch (err) {
      console.error(`[${new Date().toISOString()}] Failed to contact Rust health daemon on port ${PORT}: ${err.message}`);
      process.exit(1);
    }
  } else if (args.includes('--preview')) {
    try {
      const res = await request('/api/health-report/preview');
      console.log(res.data);
      process.exit(0);
    } catch (err) {
      console.error(`Failed to fetch preview from Rust backend: ${err.message}`);
      process.exit(1);
    }
  } else if (args.includes('--json')) {
    try {
      const res = await request('/api/health-report/data');
      console.log(res.data);
      process.exit(0);
    } catch (err) {
      console.error(`Failed to fetch health data from Rust backend: ${err.message}`);
      process.exit(1);
    }
  } else {
    console.log(`
Atelier Cluster 24h Supervision Report Bridge (Rust Engine)
Usage:
  node health_report.js --send [--to email@example.com]   Compile and send email report via Rust
  node health_report.js --preview                         Output HTML email
  node health_report.js --json                            Output raw JSON metrics
    `);
    process.exit(0);
  }
}

main();
