/**
 * =============================================================================
 * DEPRECATED - ARCHIVE REFERENCE ONLY
 * =============================================================================
 * Automated 24h Health Reporting, Anomaly Audits, and Email Telemetry have been
 * officially migrated and centralized into the compiled Rust Axum backend:
 *   --> backend-rust/src/health.rs
 *   --> backend-rust/src/mailer.rs
 *
 * This JavaScript file is retained purely as historical reference and is no
 * longer executed in production.
 * =============================================================================
 */

const os = require('os');
const { exec, execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const net = require('net');
const http = require('http');
const nodemailer = require('nodemailer');
const sqlite3 = require('sqlite3').verbose();

// Path to SQLite database
const DB_PATH = path.join(__dirname, 'messages.db');

/**
 * Open SQLite database instance
 */
function getDb() {
    return new sqlite3.Database(DB_PATH);
}

/**
 * Execute a shell command and return a Promise resolving stdout or stderr
 */
function runShell(cmd, timeoutMs = 7000) {
    return new Promise((resolve) => {
        exec(cmd, { timeout: timeoutMs, maxBuffer: 1024 * 1024 * 5 }, (error, stdout, stderr) => {
            if (error) {
                resolve({ success: false, stdout: (stdout || '').trim(), stderr: (stderr || error.message).trim() });
            } else {
                resolve({ success: true, stdout: (stdout || '').trim(), stderr: '' });
            }
        });
    });
}

/**
 * Test a local TCP port connectivity
 */
function checkPort(port, host = '127.0.0.1', timeout = 1500) {
    return new Promise((resolve) => {
        const socket = new net.Socket();
        let status = 'closed';
        socket.setTimeout(timeout);

        socket.on('connect', () => {
            status = 'open';
            socket.destroy();
            resolve({ port, host, status });
        });

        socket.on('timeout', () => {
            socket.destroy();
            resolve({ port, host, status: 'timeout' });
        });

        socket.on('error', () => {
            resolve({ port, host, status: 'closed' });
        });

        socket.connect(port, host);
    });
}

/**
 * Helper to escape HTML characters
 */
function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

/**
 * Format bytes into human readable format
 */
function formatBytes(bytes, decimals = 1) {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
}

/**
 * Format uptime seconds into human readable duration
 */
function formatUptime(seconds) {
    const d = Math.floor(seconds / (3600 * 24));
    const h = Math.floor((seconds % (3600 * 24)) / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const parts = [];
    if (d > 0) parts.push(`${d}d`);
    if (h > 0 || d > 0) parts.push(`${h}h`);
    parts.push(`${m}m`);
    return parts.join(' ');
}

/**
 * Initialize health_reports table in DB
 */
function initDb() {
    return new Promise((resolve) => {
        const db = getDb();
        db.serialize(() => {
            db.run(`CREATE TABLE IF NOT EXISTS health_reports (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                recipient TEXT,
                overall_health TEXT,
                error_count INTEGER,
                trigger_source TEXT,
                delivery_status TEXT,
                details TEXT
            )`);
            
            db.run(`INSERT OR IGNORE INTO settings (key, value) VALUES ('report_recipient_email', 'me@faizal.uk')`);
            db.run(`INSERT OR IGNORE INTO settings (key, value) VALUES ('report_enabled', 'true')`);
            db.run(`INSERT OR IGNORE INTO settings (key, value) VALUES ('report_time', '08:00')`);
            db.close(() => resolve());
        });
    });
}

/**
 * Collect complete system health, performance metrics, PM2, Docker, logs and analytics
 */
async function collectSystemHealth() {
    await initDb();
    const data = {
        collectedAt: new Date().toISOString(),
        hostname: os.hostname(),
        platform: os.platform(),
        release: os.release(),
        arch: os.arch(),
        uptimeSeconds: os.uptime(),
        uptimeFormatted: formatUptime(os.uptime()),
        cpu: {
            cores: os.cpus().length,
            model: os.cpus()[0]?.model || 'Generic CPU',
            loadAvg1m: os.loadavg()[0].toFixed(2),
            loadAvg5m: os.loadavg()[1].toFixed(2),
            loadAvg15m: os.loadavg()[2].toFixed(2),
            loadPercent: Math.min(100, Math.round((os.loadavg()[0] / os.cpus().length) * 100))
        },
        memory: {
            totalBytes: os.totalmem(),
            freeBytes: os.freemem(),
            usedBytes: os.totalmem() - os.freemem(),
            usedPercent: Math.round(((os.totalmem() - os.freemem()) / os.totalmem()) * 100),
            swapTotalBytes: 0,
            swapUsedBytes: 0,
            swapUsedPercent: 0
        },
        disk: {
            filesystem: '/',
            totalBytes: 0,
            usedBytes: 0,
            availBytes: 0,
            usedPercent: 0
        },
        pm2: [],
        pm2Summary: { total: 0, online: 0, errored: 0, stopped: 0, totalMemoryBytes: 0 },
        docker: [],
        dockerSummary: { total: 0, running: 0, healthy: 0, unhealthy: 0, stopped: 0 },
        services: [],
        errors24h: {
            systemd: [],
            nginx: [],
            pm2: [],
            kernel: [],
            sshAuthFailures: 0,
            totalErrorCount: 0
        },
        analytics24h: {
            views: 0,
            uniqueVisitors: 0,
            messagesReceived: 0,
            adminLoginsSuccess: 0,
            adminLoginsFailed: 0,
            bannedIpsCount: 0
        },
        overallStatus: 'HEALTHY', // HEALTHY, WARNING, CRITICAL
        healthIssues: []
    };

    // 1. Detailed Memory and Swap from `free -b`
    const freeRes = await runShell('free -b');
    if (freeRes.success && freeRes.stdout) {
        const lines = freeRes.stdout.split('\n');
        for (const line of lines) {
            const parts = line.trim().split(/\s+/);
            if (parts[0] === 'Mem:' && parts.length >= 7) {
                const total = parseInt(parts[1], 10);
                const available = parseInt(parts[6], 10);
                const used = total - available;
                data.memory.totalBytes = total;
                data.memory.usedBytes = used;
                data.memory.freeBytes = available;
                data.memory.usedPercent = Math.round((used / total) * 100);
            } else if (parts[0] === 'Swap:' && parts.length >= 4) {
                const totalSwap = parseInt(parts[1], 10);
                const usedSwap = parseInt(parts[2], 10);
                data.memory.swapTotalBytes = totalSwap;
                data.memory.swapUsedBytes = usedSwap;
                data.memory.swapUsedPercent = totalSwap > 0 ? Math.round((usedSwap / totalSwap) * 100) : 0;
            }
        }
    }

    // 2. Disk Space from `df -B1 -P /`
    const dfRes = await runShell('df -B1 -P /');
    if (dfRes.success && dfRes.stdout) {
        const lines = dfRes.stdout.split('\n');
        if (lines.length >= 2) {
            const parts = lines[1].trim().split(/\s+/);
            if (parts.length >= 6) {
                data.disk.filesystem = parts[0];
                data.disk.totalBytes = parseInt(parts[1], 10);
                data.disk.usedBytes = parseInt(parts[2], 10);
                data.disk.availBytes = parseInt(parts[3], 10);
                data.disk.usedPercent = parseInt(parts[4].replace('%', ''), 10);
            }
        }
    }

    // 3. PM2 Process Inspection (`pm2 jlist`)
    const pm2Res = await runShell('pm2 jlist');
    if (pm2Res.success && pm2Res.stdout) {
        try {
            const list = JSON.parse(pm2Res.stdout);
            data.pm2Summary.total = list.length;
            data.pm2 = list.map(proc => {
                const status = proc.pm2_env?.status || 'unknown';
                const restarts = proc.pm2_env?.restart_time || 0;
                const mem = proc.monit?.memory || 0;
                const cpu = proc.monit?.cpu || 0;
                const uptime = proc.pm2_env?.pm_uptime ? Math.floor((Date.now() - proc.pm2_env.pm_uptime) / 1000) : 0;

                if (status === 'online') data.pm2Summary.online++;
                else if (status === 'errored') data.pm2Summary.errored++;
                else data.pm2Summary.stopped++;

                data.pm2Summary.totalMemoryBytes += mem;

                if (status === 'errored') {
                    data.healthIssues.push(`PM2 process '${proc.name}' is in ERRORED state!`);
                } else if (restarts > 50) {
                    data.healthIssues.push(`PM2 process '${proc.name}' has excessive restarts (${restarts})`);
                }

                return {
                    name: proc.name,
                    id: proc.pm_id,
                    pid: proc.pid,
                    status,
                    cpu,
                    memoryBytes: mem,
                    memoryFormatted: formatBytes(mem),
                    restarts,
                    uptimeFormatted: formatUptime(uptime)
                };
            });
        } catch (e) {
            data.healthIssues.push(`Failed to parse PM2 jlist output: ${e.message}`);
        }
    }

    // 4. Docker Containers (`docker ps -a` & `docker stats`)
    const dockerPsRes = await runShell("docker ps -a --format '{{json .}}'");
    if (dockerPsRes.success && dockerPsRes.stdout) {
        const lines = dockerPsRes.stdout.split('\n').filter(Boolean);
        data.dockerSummary.total = lines.length;

        // Fetch stats in parallel
        const dockerStatsRes = await runShell("docker stats --no-stream --format '{{json .}}'");
        const statsMap = new Map();
        if (dockerStatsRes.success && dockerStatsRes.stdout) {
            dockerStatsRes.stdout.split('\n').filter(Boolean).forEach(line => {
                try {
                    const parsed = JSON.parse(line);
                    statsMap.set(parsed.Name || parsed.Container, parsed);
                } catch (e) {}
            });
        }

        for (const line of lines) {
            try {
                const c = JSON.parse(line);
                const stats = statsMap.get(c.Names) || {};
                const isRunning = c.State === 'running';
                const health = c.HealthStatus || (c.Status.includes('(healthy)') ? 'healthy' : (c.Status.includes('(unhealthy)') ? 'unhealthy' : 'none'));

                if (isRunning) data.dockerSummary.running++;
                else data.dockerSummary.stopped++;

                if (health === 'healthy') data.dockerSummary.healthy++;
                if (health === 'unhealthy') {
                    data.dockerSummary.unhealthy++;
                    data.healthIssues.push(`Docker container '${c.Names}' is UNHEALTHY!`);
                }

                if (!isRunning && c.State !== 'exited') {
                    data.healthIssues.push(`Docker container '${c.Names}' is in state: ${c.State}`);
                }

                data.docker.push({
                    id: (c.ID || '').substring(0, 12),
                    name: c.Names,
                    image: c.Image,
                    state: c.State,
                    status: c.Status,
                    health,
                    cpu: stats.CPUPerc || '0.0%',
                    memory: stats.MemUsage || '0B',
                    netIo: stats.NetIO || '0B / 0B',
                    ports: c.Ports || ''
                });
            } catch (e) {}
        }
    }

    // 5. Port / Microservices Health Probes
    const servicesToCheck = [
        { name: 'Nginx Web (HTTP)', port: 80, host: '127.0.0.1' },
        { name: 'Personal Site (Node.js)', port: 3002, host: '127.0.0.1' },
        { name: 'Stalwart Mail Admin/API', port: 8080, host: '127.0.0.1' },
        { name: 'Stalwart SMTPS (465)', port: 465, host: '127.0.0.1' },
        { name: 'Stalwart Submission (587)', port: 587, host: '127.0.0.1' },
        { name: 'Stalwart IMAPS (993)', port: 993, host: '127.0.0.1' },
        { name: 'MacOS Server (Blue)', port: 3001, host: '127.0.0.1' },
        { name: 'CS Panel Web', port: 3005, host: '127.0.0.1' },
        { name: 'Nexus Backend', port: 3010, host: '127.0.0.1' }
    ];

    const probeResults = await Promise.all(servicesToCheck.map(s => checkPort(s.port, s.host)));
    data.services = servicesToCheck.map((s, idx) => {
        const probe = probeResults[idx];
        const isOpen = probe.status === 'open';
        if (!isOpen) {
            // Some optional services might be down, note them
            if (['Nginx Web (HTTP)', 'Personal Site (Node.js)', 'Stalwart SMTPS (465)'].includes(s.name)) {
                data.healthIssues.push(`Core service '${s.name}' on port ${s.port} is NOT reachable!`);
            }
        }
        return {
            name: s.name,
            port: s.port,
            status: isOpen ? 'ONLINE' : 'OFFLINE',
            badgeClass: isOpen ? 'status-online' : 'status-fail'
        };
    });

    // 6. 24-Hour System Errors & Log Insights
    // a. Journalctl priority 3 (errors & critical) in last 24h
    const journalRes = await runShell('journalctl --since "24 hours ago" -p 3 --no-pager -n 40');
    if (journalRes.success && journalRes.stdout) {
        const rawLines = journalRes.stdout.split('\n').filter(Boolean);
        // Categorize lines
        let sshFails = 0;
        const otherErrors = [];
        for (const line of rawLines) {
            if (line.includes('sshd') && (line.includes('Failed') || line.includes('authentication attempts exceeded') || line.includes('invalid user'))) {
                sshFails++;
            } else {
                otherErrors.push(line);
            }
        }
        data.errors24h.sshAuthFailures = sshFails;
        data.errors24h.systemd = otherErrors.slice(-20);
    }

    // b. Kernel dmesg errors / OOM check
    const dmesgRes = await runShell('dmesg -l err,crit,alert,emerg --color=never 2>/dev/null | tail -n 15');
    if (dmesgRes.success && dmesgRes.stdout) {
        data.errors24h.kernel = dmesgRes.stdout.split('\n').filter(Boolean);
    }
    const oomRes = await runShell("dmesg -T 2>/dev/null | grep -i 'out of memory\\|oom_reaper\\|killed process' | tail -n 5");
    if (oomRes.success && oomRes.stdout) {
        const oomLines = oomRes.stdout.split('\n').filter(Boolean);
        if (oomLines.length > 0) {
            data.healthIssues.push(`Kernel OOM (Out Of Memory) killer triggered ${oomLines.length} event(s)!`);
            data.errors24h.kernel.push(...oomLines);
        }
    }

    // c. Nginx error log (last 20 lines)
    const nginxLogRes = await runShell('sudo tail -n 25 /var/log/nginx/error.log 2>/dev/null || tail -n 25 /var/log/nginx/error.log 2>/dev/null');
    if (nginxLogRes.success && nginxLogRes.stdout) {
        data.errors24h.nginx = nginxLogRes.stdout.split('\n').filter(l => l.trim().length > 0).slice(-15);
    }

    // d. PM2 error logs (active non-empty error logs)
    try {
        const pm2LogDir = path.join(os.homedir(), '.pm2', 'logs');
        if (fs.existsSync(pm2LogDir)) {
            const files = fs.readdirSync(pm2LogDir).filter(f => f.endsWith('-error.log'));
            for (const f of files) {
                const fullPath = path.join(pm2LogDir, f);
                const stat = fs.statSync(fullPath);
                if (stat.size > 0) {
                    const content = fs.readFileSync(fullPath, 'utf8').trim().split('\n').slice(-10);
                    if (content.length > 0 && content[0]) {
                        data.errors24h.pm2.push({
                            file: f,
                            lines: content
                        });
                    }
                }
            }
        }
    } catch (e) {}

    // Calculate total error count
    data.errors24h.totalErrorCount = 
        data.errors24h.systemd.length + 
        data.errors24h.kernel.length + 
        data.errors24h.nginx.length + 
        data.errors24h.pm2.reduce((acc, p) => acc + p.lines.length, 0);

    // 7. 24-Hour Analytics & Security from SQLite
    try {
        const db = getDb();
        await new Promise((resolve) => {
            db.get(`SELECT COUNT(*) as views, COUNT(DISTINCT ip) as unique_v FROM visitors WHERE timestamp >= datetime('now', '-24 hours')`, (err, row) => {
                if (row) {
                    data.analytics24h.views = row.views || 0;
                    data.analytics24h.uniqueVisitors = row.unique_v || 0;
                }
                db.get(`SELECT COUNT(*) as msg_count FROM messages WHERE created_at >= datetime('now', '-24 hours')`, (err2, row2) => {
                    if (row2) data.analytics24h.messagesReceived = row2.msg_count || 0;
                    
                    db.all(`SELECT status, COUNT(*) as cnt FROM access_logs WHERE timestamp >= datetime('now', '-24 hours') GROUP BY status`, (err3, rows3) => {
                        if (rows3) {
                            rows3.forEach(r => {
                                if (r.status === 'SUCCESS') data.analytics24h.adminLoginsSuccess = r.cnt;
                                if (r.status === 'FAILURE') data.analytics24h.adminLoginsFailed = r.cnt;
                            });
                        }
                        db.get(`SELECT COUNT(*) as ban_cnt FROM banned_ips`, (err4, row4) => {
                            if (row4) data.analytics24h.bannedIpsCount = row4.ban_cnt || 0;
                            db.close(() => resolve());
                        });
                    });
                });
            });
        });
    } catch (e) {}

    // 8. Overall Health Score Calculation
    if (data.disk.usedPercent >= 92) {
        data.overallStatus = 'CRITICAL';
        data.healthIssues.push(`Disk space usage is dangerously high: ${data.disk.usedPercent}%!`);
    } else if (data.disk.usedPercent >= 85) {
        if (data.overallStatus !== 'CRITICAL') data.overallStatus = 'WARNING';
        data.healthIssues.push(`Disk space usage is high: ${data.disk.usedPercent}%`);
    }

    if (data.memory.usedPercent >= 92) {
        if (data.overallStatus !== 'CRITICAL') data.overallStatus = 'WARNING';
        data.healthIssues.push(`RAM usage is high: ${data.memory.usedPercent}%`);
    }

    if (data.pm2Summary.errored > 0) {
        data.overallStatus = 'CRITICAL';
    }

    if (data.dockerSummary.unhealthy > 0) {
        data.overallStatus = 'CRITICAL';
    }

    if (data.healthIssues.length > 0 && data.overallStatus === 'HEALTHY') {
        data.overallStatus = 'WARNING';
    }

    return data;
}

/**
 * Generate a responsive, dark-mode executive HTML email report
 */
function generateReportHtml(data) {
    const isCritical = data.overallStatus === 'CRITICAL';
    const isWarning = data.overallStatus === 'WARNING';
    const isHealthy = data.overallStatus === 'HEALTHY';

    const statusColor = isCritical ? '#ff3366' : (isWarning ? '#f59e0b' : '#89ff00');
    const statusBg = isCritical ? 'rgba(255, 51, 102, 0.15)' : (isWarning ? 'rgba(245, 158, 11, 0.15)' : 'rgba(137, 255, 0, 0.15)');
    const statusText = isCritical ? 'CRITICAL ATTENTION REQUIRED' : (isWarning ? 'SYSTEM WARNINGS DETECTED' : 'ALL SYSTEMS OPERATIONAL');

    const formattedDate = new Date(data.collectedAt).toUTCString();

    // Render PM2 rows
    const pm2Rows = data.pm2.map(p => {
        const isOnline = p.status === 'online';
        const color = isOnline ? '#10b981' : '#ef4444';
        return `
            <tr style="border-bottom: 1px solid rgba(255,255,255,0.06);">
                <td style="padding: 10px 12px; font-weight: 600; color: #ffffff;">${escapeHtml(p.name)}</td>
                <td style="padding: 10px 12px;"><span style="display:inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 700; background: ${isOnline ? 'rgba(16,185,129,0.15)' : 'rgba(239,68,68,0.15)'}; color: ${color};">${escapeHtml(p.status.toUpperCase())}</span></td>
                <td style="padding: 10px 12px; color: #cbd5e1;">${p.cpu}%</td>
                <td style="padding: 10px 12px; color: #cbd5e1;">${p.memoryFormatted}</td>
                <td style="padding: 10px 12px; color: ${p.restarts > 5 ? '#f59e0b' : '#cbd5e1'}; font-weight: ${p.restarts > 5 ? 'bold' : 'normal'};">${p.restarts}</td>
                <td style="padding: 10px 12px; color: #94a3b8; font-size: 12px;">${p.uptimeFormatted}</td>
            </tr>
        `;
    }).join('');

    // Render Docker rows
    const dockerRows = data.docker.map(c => {
        const isHealthyDocker = c.health === 'healthy' || (c.state === 'running' && c.health === 'none');
        const color = isHealthyDocker ? '#10b981' : '#ef4444';
        return `
            <tr style="border-bottom: 1px solid rgba(255,255,255,0.06);">
                <td style="padding: 10px 12px; font-weight: 600; color: #ffffff;">${escapeHtml(c.name)}</td>
                <td style="padding: 10px 12px; color: #94a3b8; font-size: 12px;">${escapeHtml(c.image)}</td>
                <td style="padding: 10px 12px;"><span style="display:inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 700; background: ${color}22; color: ${color};">${escapeHtml(c.health !== 'none' ? c.health.toUpperCase() : c.state.toUpperCase())}</span></td>
                <td style="padding: 10px 12px; color: #cbd5e1;">${escapeHtml(c.cpu)}</td>
                <td style="padding: 10px 12px; color: #cbd5e1;">${escapeHtml(c.memory)}</td>
                <td style="padding: 10px 12px; color: #94a3b8; font-size: 12px;">${escapeHtml(c.status)}</td>
            </tr>
        `;
    }).join('');

    // Render Services pills
    const servicePills = data.services.map(s => {
        const isOnline = s.status === 'ONLINE';
        const color = isOnline ? '#10b981' : '#ef4444';
        const bg = isOnline ? 'rgba(16, 185, 129, 0.1)' : 'rgba(239, 68, 68, 0.1)';
        return `
            <div style="display: inline-block; background: ${bg}; border: 1px solid ${color}40; border-radius: 6px; padding: 6px 12px; margin: 4px; font-size: 12px;">
                <span style="display: inline-block; width: 7px; height: 7px; border-radius: 50%; background: ${color}; margin-right: 6px;"></span>
                <strong style="color: #ffffff;">${escapeHtml(s.name)}</strong>
                <span style="color: ${color}; margin-left: 4px;">${s.status}</span>
            </div>
        `;
    }).join('');

    // Render Issues List
    let issuesHtml = '';
    if (data.healthIssues.length > 0) {
        issuesHtml = `
            <div style="background: rgba(239, 68, 68, 0.1); border-left: 4px solid #ef4444; border-radius: 6px; padding: 14px 16px; margin-bottom: 24px;">
                <div style="font-weight: 700; color: #ef4444; font-size: 14px; margin-bottom: 8px;">Action Items & Warnings (${data.healthIssues.length})</div>
                <ul style="margin: 0; padding-left: 20px; color: #fca5a5; font-size: 13px; line-height: 1.6;">
                    ${data.healthIssues.map(i => `<li>${escapeHtml(i)}</li>`).join('')}
                </ul>
            </div>
        `;
    }

    // Render Errors snippet
    let errorsHtml = '';
    const hasAnyErrors = data.errors24h.systemd.length > 0 || data.errors24h.nginx.length > 0 || data.errors24h.kernel.length > 0 || data.errors24h.pm2.length > 0;
    
    if (hasAnyErrors) {
        errorsHtml = '<div style="margin-top: 15px;">';
        if (data.errors24h.systemd.length > 0) {
            errorsHtml += `
                <div style="margin-bottom: 12px;">
                    <div style="font-size: 12px; font-weight: 700; color: #f59e0b; margin-bottom: 4px;">SYSTEMD CRITICAL LOGS (${data.errors24h.systemd.length})</div>
                    <pre style="background: #000000; color: #ef4444; padding: 10px; border-radius: 6px; font-size: 11px; overflow-x: auto; white-space: pre-wrap; font-family: monospace; border: 1px solid rgba(255,255,255,0.08);">${escapeHtml(data.errors24h.systemd.slice(-6).join('\n'))}</pre>
                </div>
            `;
        }
        if (data.errors24h.nginx.length > 0) {
            errorsHtml += `
                <div style="margin-bottom: 12px;">
                    <div style="font-size: 12px; font-weight: 700; color: #f59e0b; margin-bottom: 4px;">NGINX ERROR LOGS (${data.errors24h.nginx.length})</div>
                    <pre style="background: #000000; color: #fca5a5; padding: 10px; border-radius: 6px; font-size: 11px; overflow-x: auto; white-space: pre-wrap; font-family: monospace; border: 1px solid rgba(255,255,255,0.08);">${escapeHtml(data.errors24h.nginx.slice(-5).join('\n'))}</pre>
                </div>
            `;
        }
        if (data.errors24h.pm2.length > 0) {
            errorsHtml += `
                <div style="margin-bottom: 12px;">
                    <div style="font-size: 12px; font-weight: 700; color: #f59e0b; margin-bottom: 4px;">PM2 PROCESS ERRORS</div>
                    ${data.errors24h.pm2.map(p => `
                        <div style="font-size: 11px; color: #94a3b8; margin-top: 4px;"><strong>${escapeHtml(p.file)}:</strong></div>
                        <pre style="background: #000000; color: #fca5a5; padding: 8px; border-radius: 6px; font-size: 10px; overflow-x: auto; white-space: pre-wrap; font-family: monospace; border: 1px solid rgba(255,255,255,0.08);">${escapeHtml(p.lines.slice(-4).join('\n'))}</pre>
                    `).join('')}
                </div>
            `;
        }
        errorsHtml += '</div>';
    } else {
        errorsHtml = `
            <div style="background: rgba(16, 185, 129, 0.08); border: 1px solid rgba(16, 185, 129, 0.2); border-radius: 6px; padding: 12px; color: #10b981; font-size: 13px;">
                ✓ Excellent: Zero critical errors or unexpected crashes logged across all system services in the past 24 hours.
            </div>
        `;
    }

    return `
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>VM Health & 24h Performance Report</title>
</head>
<body style="margin: 0; padding: 0; background-color: #000000; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #f8fafc; line-height: 1.5;">
    <div style="max-width: 680px; margin: 0 auto; padding: 24px 16px;">
        
        <!-- Header -->
        <table style="width: 100%; margin-bottom: 20px; background: #0c0d10; border: 1px solid rgba(137, 255, 0, 0.25); border-radius: 16px; padding: 20px;">
            <tr>
                <td>
                    <div style="font-family: monospace; font-size: 11px; font-weight: 700; color: #89ff00; letter-spacing: 0.18em; text-transform: uppercase; margin-bottom: 4px;">MOHAMMAD FAIZAL // ATELIER CLUSTER</div>
                    <div style="font-size: 18px; font-weight: 800; color: #ffffff; letter-spacing: -0.5px;">24h System Health & Supervision Report</div>
                    <div style="font-size: 11px; font-family: monospace; color: #94a3b8; margin-top: 4px;">Master: <strong style="color: #cbd5e1;">${escapeHtml(data.hostname)}</strong> (${data.arch} / Linux ${data.release.split('-')[0]}) &bull; ${formattedDate}</div>
                </td>
                <td style="text-align: right; vertical-align: top;">
                    <div style="display: inline-block; background: ${statusBg}; border: 1px solid ${statusColor}; color: ${statusColor}; font-family: monospace; font-weight: 800; font-size: 11px; padding: 6px 12px; border-radius: 8px; text-transform: uppercase; letter-spacing: 0.5px;">
                        ${statusText}
                    </div>
                </td>
            </tr>
        </table>

        <!-- Issues Alert (if any) -->
        ${issuesHtml}

        <!-- Top KPI Grid -->
        <table style="width: 100%; border-collapse: separate; border-spacing: 10px; margin-bottom: 14px;">
            <tr>
                <!-- Uptime -->
                <td style="background: #141416; border: 1px solid rgba(255,255,255,0.06); border-radius: 10px; padding: 14px; width: 25%; vertical-align: top;">
                    <div style="font-size: 11px; text-transform: uppercase; color: #94a3b8; font-weight: 700;">System Uptime</div>
                    <div style="font-size: 22px; font-weight: 800; color: #ffffff; margin-top: 4px;">${data.uptimeFormatted}</div>
                    <div style="font-size: 11px; color: #64748b; margin-top: 2px;">Load: ${data.cpu.loadAvg1m}, ${data.cpu.loadAvg5m}</div>
                </td>
                <!-- CPU Usage -->
                <td style="background: #141416; border: 1px solid rgba(255,255,255,0.06); border-radius: 10px; padding: 14px; width: 25%; vertical-align: top;">
                    <div style="font-size: 11px; text-transform: uppercase; color: #94a3b8; font-weight: 700;">CPU Utilization</div>
                    <div style="font-size: 22px; font-weight: 800; color: #ffffff; margin-top: 4px;">${data.cpu.loadPercent}%</div>
                    <div style="font-size: 11px; color: #64748b; margin-top: 2px;">${data.cpu.cores} vCPUs (${data.cpu.loadAvg15m} 15m)</div>
                </td>
                <!-- RAM Usage -->
                <td style="background: #141416; border: 1px solid rgba(255,255,255,0.06); border-radius: 10px; padding: 14px; width: 25%; vertical-align: top;">
                    <div style="font-size: 11px; text-transform: uppercase; color: #94a3b8; font-weight: 700;">RAM Allocation</div>
                    <div style="font-size: 22px; font-weight: 800; color: ${data.memory.usedPercent > 85 ? '#f59e0b' : '#ffffff'}; margin-top: 4px;">${data.memory.usedPercent}%</div>
                    <div style="font-size: 11px; color: #64748b; margin-top: 2px;">${formatBytes(data.memory.usedBytes, 0)} / ${formatBytes(data.memory.totalBytes, 0)}</div>
                </td>
                <!-- Disk Usage -->
                <td style="background: #141416; border: 1px solid rgba(255,255,255,0.06); border-radius: 10px; padding: 14px; width: 25%; vertical-align: top;">
                    <div style="font-size: 11px; text-transform: uppercase; color: #94a3b8; font-weight: 700;">Disk Storage</div>
                    <div style="font-size: 22px; font-weight: 800; color: ${data.disk.usedPercent > 85 ? '#f59e0b' : '#ffffff'}; margin-top: 4px;">${data.disk.usedPercent}%</div>
                    <div style="font-size: 11px; color: #64748b; margin-top: 2px;">${formatBytes(data.disk.availBytes, 1)} Free of ${formatBytes(data.disk.totalBytes, 0)}</div>
                </td>
            </tr>
        </table>

        <!-- Progress Bars for Memory and Disk -->
        <div style="background: #141416; border: 1px solid rgba(255,255,255,0.06); border-radius: 10px; padding: 14px 18px; margin-bottom: 20px;">
            <div style="margin-bottom: 12px;">
                <div style="display: flex; justify-content: space-between; font-size: 12px; margin-bottom: 4px;">
                    <span style="font-weight: 600; color: #cbd5e1;">Memory & Swap Usage</span>
                    <span style="color: #94a3b8;">RAM: ${formatBytes(data.memory.usedBytes)} (${data.memory.usedPercent}%) &bull; Swap: ${formatBytes(data.memory.swapUsedBytes)} (${data.memory.swapUsedPercent}%)</span>
                </div>
                <div style="background: rgba(255,255,255,0.06); border-radius: 4px; height: 8px; overflow: hidden;">
                    <div style="background: ${data.memory.usedPercent > 85 ? '#f59e0b' : '#3b82f6'}; width: ${data.memory.usedPercent}%; height: 100%;"></div>
                </div>
            </div>
            <div>
                <div style="display: flex; justify-content: space-between; font-size: 12px; margin-bottom: 4px;">
                    <span style="font-weight: 600; color: #cbd5e1;">Root Partition (/dev/sda1)</span>
                    <span style="color: #94a3b8;">Used: ${formatBytes(data.disk.usedBytes)} &bull; Available: ${formatBytes(data.disk.availBytes)} (${data.disk.usedPercent}%)</span>
                </div>
                <div style="background: rgba(255,255,255,0.06); border-radius: 4px; height: 8px; overflow: hidden;">
                    <div style="background: ${data.disk.usedPercent > 85 ? (data.disk.usedPercent > 92 ? '#ef4444' : '#f59e0b') : '#10b981'}; width: ${data.disk.usedPercent}%; height: 100%;"></div>
                </div>
            </div>
        </div>

        <!-- PM2 Applications Report -->
        <div style="background: #141416; border: 1px solid rgba(255,255,255,0.06); border-radius: 10px; padding: 16px; margin-bottom: 20px;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
                <div style="font-size: 14px; font-weight: 700; color: #d4af37; text-transform: uppercase;">PM2 Applications Status</div>
                <div style="font-size: 12px; color: #94a3b8;"><strong style="color: #10b981;">${data.pm2Summary.online} Online</strong> / ${data.pm2Summary.total} Total &bull; Memory: ${formatBytes(data.pm2Summary.totalMemoryBytes)}</div>
            </div>
            <table style="width: 100%; border-collapse: collapse; font-size: 13px;">
                <thead>
                    <tr style="border-bottom: 1px solid rgba(255,255,255,0.1); color: #94a3b8; text-align: left;">
                        <th style="padding: 8px 12px;">Process</th>
                        <th style="padding: 8px 12px;">Status</th>
                        <th style="padding: 8px 12px;">CPU</th>
                        <th style="padding: 8px 12px;">Memory</th>
                        <th style="padding: 8px 12px;">Restarts</th>
                        <th style="padding: 8px 12px;">Uptime</th>
                    </tr>
                </thead>
                <tbody>
                    ${pm2Rows || '<tr><td colspan="6" style="padding: 12px; color: #64748b;">No PM2 processes found</td></tr>'}
                </tbody>
            </table>
        </div>

        <!-- Docker Containers Report -->
        <div style="background: #141416; border: 1px solid rgba(255,255,255,0.06); border-radius: 10px; padding: 16px; margin-bottom: 20px;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
                <div style="font-size: 14px; font-weight: 700; color: #d4af37; text-transform: uppercase;">Docker Containers & Health</div>
                <div style="font-size: 12px; color: #94a3b8;"><strong style="color: #10b981;">${data.dockerSummary.running} Running</strong> (${data.dockerSummary.healthy} Healthy) / ${data.dockerSummary.total} Total</div>
            </div>
            <table style="width: 100%; border-collapse: collapse; font-size: 13px;">
                <thead>
                    <tr style="border-bottom: 1px solid rgba(255,255,255,0.1); color: #94a3b8; text-align: left;">
                        <th style="padding: 8px 12px;">Container</th>
                        <th style="padding: 8px 12px;">Image</th>
                        <th style="padding: 8px 12px;">Health / State</th>
                        <th style="padding: 8px 12px;">CPU</th>
                        <th style="padding: 8px 12px;">Memory</th>
                        <th style="padding: 8px 12px;">Status</th>
                    </tr>
                </thead>
                <tbody>
                    ${dockerRows || '<tr><td colspan="6" style="padding: 12px; color: #64748b;">No Docker containers found</td></tr>'}
                </tbody>
            </table>
        </div>

        <!-- Network & Services Health -->
        <div style="background: #141416; border: 1px solid rgba(255,255,255,0.06); border-radius: 10px; padding: 16px; margin-bottom: 20px;">
            <div style="font-size: 14px; font-weight: 700; color: #d4af37; text-transform: uppercase; margin-bottom: 10px;">Active Network Services & Ports</div>
            <div>
                ${servicePills}
            </div>
        </div>

        <!-- 24-Hour System Errors & Security Incidents -->
        <div style="background: #141416; border: 1px solid rgba(255,255,255,0.06); border-radius: 10px; padding: 16px; margin-bottom: 20px;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px;">
                <div style="font-size: 14px; font-weight: 700; color: #d4af37; text-transform: uppercase;">24-Hour System Errors & Security Log</div>
                <div style="font-size: 12px; color: #94a3b8;">
                    SSH Brute-Force Blocks: <strong style="color: ${data.errors24h.sshAuthFailures > 0 ? '#f59e0b' : '#10b981'};">${data.errors24h.sshAuthFailures}</strong> &bull; Total Incidents: ${data.errors24h.totalErrorCount}
                </div>
            </div>
            ${errorsHtml}
        </div>

        <!-- 24-Hour Web Analytics & Visitors -->
        <div style="background: #141416; border: 1px solid rgba(255,255,255,0.06); border-radius: 10px; padding: 16px; margin-bottom: 24px;">
            <div style="font-size: 14px; font-weight: 700; color: #d4af37; text-transform: uppercase; margin-bottom: 12px;">24-Hour Website Traffic & Admin Security</div>
            <table style="width: 100%; border-collapse: separate; border-spacing: 8px;">
                <tr>
                    <td style="background: rgba(255,255,255,0.02); border: 1px solid rgba(255,255,255,0.04); padding: 10px; border-radius: 6px; text-align: center;">
                        <div style="font-size: 11px; color: #94a3b8;">Page Views</div>
                        <div style="font-size: 18px; font-weight: 700; color: #ffffff; margin-top: 2px;">${data.analytics24h.views}</div>
                    </td>
                    <td style="background: rgba(255,255,255,0.02); border: 1px solid rgba(255,255,255,0.04); padding: 10px; border-radius: 6px; text-align: center;">
                        <div style="font-size: 11px; color: #94a3b8;">Unique Visitors</div>
                        <div style="font-size: 18px; font-weight: 700; color: #ffffff; margin-top: 2px;">${data.analytics24h.uniqueVisitors}</div>
                    </td>
                    <td style="background: rgba(255,255,255,0.02); border: 1px solid rgba(255,255,255,0.04); padding: 10px; border-radius: 6px; text-align: center;">
                        <div style="font-size: 11px; color: #94a3b8;">Messages Inbound</div>
                        <div style="font-size: 18px; font-weight: 700; color: #ffffff; margin-top: 2px;">${data.analytics24h.messagesReceived}</div>
                    </td>
                    <td style="background: rgba(255,255,255,0.02); border: 1px solid rgba(255,255,255,0.04); padding: 10px; border-radius: 6px; text-align: center;">
                        <div style="font-size: 11px; color: #94a3b8;">Admin Logins</div>
                        <div style="font-size: 18px; font-weight: 700; color: #ffffff; margin-top: 2px;">${data.analytics24h.adminLoginsSuccess} <span style="font-size: 12px; color: ${data.analytics24h.adminLoginsFailed > 0 ? '#ef4444' : '#64748b'};">(${data.analytics24h.adminLoginsFailed} fails)</span></div>
                    </td>
                </tr>
            </table>
        </div>

        <!-- Footer -->
        <div style="border-top: 1px solid rgba(255,255,255,0.08); padding-top: 16px; font-size: 11px; color: #64748b; text-align: center;">
            <p style="margin: 0 0 6px 0;">Automated VM Health Monitoring &bull; Dispatched via Stalwart Mail Server (<code style="color: #cbd5e1;">mail.faizal.uk</code>)</p>
            <p style="margin: 0;">
                <a href="https://faizal.uk/admin.html" style="color: #d4af37; text-decoration: none; margin: 0 8px;">Admin Dashboard</a> |
                <a href="https://faizal.uk/mail/" style="color: #d4af37; text-decoration: none; margin: 0 8px;">Mail Control Center</a> |
                <a href="https://mac.faizal.uk" style="color: #d4af37; text-decoration: none; margin: 0 8px;">MacOS Stack</a>
            </p>
        </div>

    </div>
</body>
</html>
    `;
}

/**
 * Parse recipient list string or array into an array of valid trimmed emails
 */
function parseRecipients(input) {
    if (!input) return ['me@faizal.uk'];
    if (Array.isArray(input)) {
        return input.map(e => String(e).trim()).filter(e => e.includes('@'));
    }
    return String(input)
        .split(/[,;\s\n]+/)
        .map(e => e.trim())
        .filter(e => e.includes('@'));
}

/**
 * Send health report via Stalwart mail server using dedicated reports@faizal.uk sender
 */
async function sendReportEmail(options = {}) {
    const db = getDb();
    
    // Retrieve recipient(s) from options or settings
    let targetRecipient = options.recipient;
    if (!targetRecipient) {
        targetRecipient = await new Promise((resolve) => {
            db.get(`SELECT value FROM settings WHERE key = 'report_recipient_email'`, (err, row) => {
                if (row && row.value && row.value.includes('@')) {
                    resolve(row.value);
                } else {
                    db.get(`SELECT value FROM settings WHERE key = 'notification_email'`, (err2, row2) => {
                        resolve(row2 && row2.value && row2.value.includes('@') ? row2.value : 'me@faizal.uk');
                    });
                }
            });
        });
    }

    const recipients = parseRecipients(targetRecipient);
    const recipientStr = recipients.join(', ');

    console.log(`[HealthReporter] Generating health report for: ${recipientStr}...`);
    const data = await collectSystemHealth();
    const html = generateReportHtml(data);

    // Primary: Brevo SMTP relay on port 587 (bypasses Oracle Cloud port 25 block)
    // Fallback: Local Stalwart SMTPS on 127.0.0.1:465
    let transporter;
    try {
        transporter = nodemailer.createTransport({
            host: 'smtp-relay.brevo.com',
            port: 587,
            secure: false,
            auth: {
                user: 'bb6408001@smtp-brevo.com',
                pass: 'xsmtpsib-73f7e08afbd853d9e9d2e8c2049f93badd7e7f2b34e3e9de937eee1a307cace3-cmEy8jEk90T1HM6Z'
            },
            connectionTimeout: 10000,
            greetingTimeout: 10000
        });
    } catch (e) {
        console.warn('[HealthReporter] Falling back to local Stalwart transport:', e.message);
        transporter = nodemailer.createTransport({
            host: '127.0.0.1',
            port: 465,
            secure: true,
            auth: {
                user: 'reports@faizal.uk',
                pass: 'FaizalReports2026!'
            },
            tls: { rejectUnauthorized: false }
        });
    }

    const statusBadge = data.overallStatus === 'CRITICAL' ? '🔴 CRITICAL' : (data.overallStatus === 'WARNING' ? '🟡 WARNING' : '🟢 NORMAL');
    const dateStr = new Date().toISOString().split('T')[0];

    const mailOptions = {
        from: '"Faizal VM Health Monitor" <reports@faizal.uk>',
        replyTo: 'reports@faizal.uk',
        to: recipients,
        envelope: {
            from: 'me@faizal.uk',
            to: recipients
        },
        subject: `[VM Report] ${statusBadge} - Health & 24h Performance Digest (${dateStr})`,
        html: html
    };

    try {
        const info = await transporter.sendMail(mailOptions);
        console.log(`[HealthReporter] Email sent successfully to [${recipientStr}]: ${info.messageId}`);

        // Record in health_reports table
        await new Promise((resolve) => {
            db.run(`INSERT INTO health_reports (recipient, overall_health, error_count, trigger_source, delivery_status, details) VALUES (?, ?, ?, ?, ?, ?)`,
                [recipientStr, data.overallStatus, data.errors24h.totalErrorCount, options.trigger || 'manual', 'SUCCESS', JSON.stringify({ messageId: info.messageId, uptime: data.uptimeFormatted, recipientsCount: recipients.length })],
                (err) => {
                    db.run(`INSERT OR REPLACE INTO settings (key, value) VALUES ('last_report_timestamp', ?)`, [new Date().toISOString()]);
                    db.run(`INSERT OR REPLACE INTO settings (key, value) VALUES ('last_report_status', 'SUCCESS')`);
                    db.close(() => resolve());
                }
            );
        });

        return {
            success: true,
            messageId: info.messageId,
            recipient: recipientStr,
            recipients,
            overallStatus: data.overallStatus,
            data
        };
    } catch (err) {
        console.error(`[HealthReporter] Failed to send email to [${recipientStr}]: ${err.message}`);

        await new Promise((resolve) => {
            db.run(`INSERT INTO health_reports (recipient, overall_health, error_count, trigger_source, delivery_status, details) VALUES (?, ?, ?, ?, ?, ?)`,
                [recipientStr, data.overallStatus, data.errors24h.totalErrorCount, options.trigger || 'manual', 'FAILED', JSON.stringify({ error: err.message })],
                (err2) => {
                    db.run(`INSERT OR REPLACE INTO settings (key, value) VALUES ('last_report_status', 'FAILED: ' || ?)`, [err.message]);
                    db.close(() => resolve());
                }
            );
        });

        return {
            success: false,
            error: err.message,
            recipient: recipientStr,
            recipients,
            overallStatus: data.overallStatus,
            data
        };
    }
}

/**
 * CLI execution handler
 */
if (require.main === module) {
    const args = process.argv.slice(2);
    if (args.includes('--send')) {
        const toIdx = args.indexOf('--to');
        const customTo = toIdx !== -1 && args[toIdx + 1] ? args[toIdx + 1] : null;
        sendReportEmail({ recipient: customTo, trigger: 'cli' }).then(res => {
            console.log('Result:', JSON.stringify(res, null, 2));
            process.exit(res.success ? 0 : 1);
        });
    } else if (args.includes('--json')) {
        collectSystemHealth().then(data => {
            console.log(JSON.stringify(data, null, 2));
            process.exit(0);
        });
    } else if (args.includes('--preview')) {
        collectSystemHealth().then(data => {
            console.log(generateReportHtml(data));
            process.exit(0);
        });
    } else {
        console.log(`
VM Health & 24-Hour Performance Reporter
Usage:
  node healthReporter.js --send [--to email@example.com]   Compile & email report
  node healthReporter.js --preview                        Output compiled HTML report
  node healthReporter.js --json                           Output health metrics as JSON
        `);
        process.exit(0);
    }
}

module.exports = {
    collectSystemHealth,
    generateReportHtml,
    sendReportEmail,
    initDb
};
