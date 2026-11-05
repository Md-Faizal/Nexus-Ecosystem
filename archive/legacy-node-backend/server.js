const express = require('express');
const cors = require('cors');
const sqlite3 = require('sqlite3').verbose();
const path = require('path');
const multer = require('multer');
const fs = require('fs');
const os = require('os');
const { exec } = require('child_process');
const nodemailer = require('nodemailer');
const { collectSystemHealth, generateReportHtml, sendReportEmail } = require('./healthReporter');

const app = express();
const PORT = process.env.PORT || 3002;

// Middleware
app.use(cors());
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ limit: '50mb', extended: true }));

// 1. IP Blacklist Check
app.use((req, res, next) => {
    const ip = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
    db.get(`SELECT ip FROM banned_ips WHERE ip = ?`, [ip], (err, row) => {
        if (row) return res.status(403).send('Your IP is banned.');
        next();
    });
});

// 2. Visitor Tracking
app.use((req, res, next) => {
    // Only track page loads, not internal API or static assets
    if (req.method === 'GET' && !req.path.startsWith('/api') && !req.path.includes('.')) {
        const ip = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
        db.run(`INSERT INTO visitors (ip, user_agent, path) VALUES (?, ?, ?)`, 
            [ip, req.headers['user-agent'], req.path], (err) => {
                if (err) console.error('Visitor tracking database error:', err.message);
            });
    }
    next();
});

// 3. Maintenance Mode Check (Non-blocking, for overlay)
app.get('/api/maintenance-status', (req, res) => {
    db.get(`SELECT value FROM settings WHERE key = ?`, ['maintenance_mode'], (err, row) => {
        res.json({ maintenance: row ? row.value === 'true' : false });
    });
});

// 4. Theme Configuration Check
app.get('/api/theme-config', (req, res) => {
    db.get(`SELECT value FROM settings WHERE key = ?`, ['site_theme'], (err, row) => {
        res.json({ theme: row ? row.value : 'classic' });
    });
});

// Set up multer for file uploads (store in memory for DB storage)
const storage = multer.memoryStorage();
const upload = multer({ storage });

// Admin Authentication Middleware
const requireAdmin = (req, res, next) => {
    const pin = req.headers['authorization'] || req.query.pin;
    if (pin === '2004') {
        next();
    } else {
        res.status(403).json({ success: false, error: 'Unauthorized. Invalid PIN.' });
    }
};

// Initialize SQLite database
const dbPath = path.join(__dirname, 'messages.db');
const db = new sqlite3.Database(dbPath, (err) => {
    if (err) {
        console.error('Error opening database', err.message);
    } else {
        console.log('Connected to the SQLite database.');
        
        db.serialize(() => {
            // Create table if not exists
            db.run(`CREATE TABLE IF NOT EXISTS messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                email TEXT NOT NULL,
                subject TEXT NOT NULL,
                message TEXT NOT NULL,
                ip_address TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )`);
            
            // Create settings table for profile picture reference
            db.run(`CREATE TABLE IF NOT EXISTS settings (
                key TEXT PRIMARY KEY,
                value TEXT
            )`);

            // Create profile_pictures table for history
            db.run(`CREATE TABLE IF NOT EXISTS profile_pictures (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                value BLOB,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )`);

            // Create visitors table for analytics
            db.run(`CREATE TABLE IF NOT EXISTS visitors (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ip TEXT,
                user_agent TEXT,
                path TEXT,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
            )`);

            // Create projects table for portfolio management
            db.run(`CREATE TABLE IF NOT EXISTS projects (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT,
                description TEXT,
                image_blob BLOB,
                link TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )`);

            // Create access_logs table
            db.run(`CREATE TABLE IF NOT EXISTS access_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ip TEXT,
                status TEXT,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
            )`);

            // Create banned_ips table
            db.run(`CREATE TABLE IF NOT EXISTS banned_ips (
                ip TEXT PRIMARY KEY,
                reason TEXT,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
            )`);

            // Initialize default settings if not present
            const defaultSettings = [
                ['maintenance_mode', 'false'],
                ['notification_email', ''],
                ['site_theme', 'classic']
            ];
            defaultSettings.forEach(([key, val]) => {
                db.run(`INSERT OR IGNORE INTO settings (key, value) VALUES (?, ?)`, [key, val], (err) => {
                    if (err) console.error('Error seeding settings:', err.message);
                });
            });

            // Seed initial projects if empty
            db.get(`SELECT COUNT(*) as count FROM projects`, (err, row) => {
                if (row && row.count === 0) {
                    const initialProjects = [
                        ['Heritage Manufacturing Operations', 'Overseeing daily production at UCA — maintaining traditional ayurvedic methods while ensuring modern quality standards.', 'https://faizal.uk'],
                        ['Digital Modernization', 'Introduced inventory management systems, production tracking, and data analytics to streamline business operations.', 'https://faizal.uk'],
                        ['Market Expansion & Branding', 'Developed new packaging and branding strategies to expand UCA’s reach while staying true to its legacy.', 'https://faizal.uk']
                    ];
                    initialProjects.forEach(([title, desc, link]) => {
                        db.run(`INSERT INTO projects (title, description, link) VALUES (?, ?, ?)`, [title, desc, link], (err) => {
                            if (err) console.error('Error seeding project:', err.message);
                        });
                    });
                }
            });
        });
    }
});

// Helper to get SMTP Transporter
async function getTransporter() {
    return new Promise((resolve) => {
        db.all(`SELECT key, value FROM settings WHERE key IN ('notification_email', 'smtp_password')`, (err, rows) => {
            const email = rows ? rows.find(r => r.key === 'notification_email')?.value : null;
            const pass = rows ? rows.find(r => r.key === 'smtp_password')?.value : null;
            
            if (email && pass && email.includes('@')) {
                resolve(nodemailer.createTransport({
                    service: 'gmail',
                    auth: { user: email, pass: pass }
                }));
            } else {
                // Primary: Brevo SMTP Relay on port 587 (bypasses Oracle port 25 block)
                // Fallback: Local Stalwart on 127.0.0.1:465
                resolve(nodemailer.createTransport({
                    host: 'smtp-relay.brevo.com',
                    port: 587,
                    secure: false,
                    auth: {
                        user: 'bb6408001@smtp-brevo.com',
                        pass: 'xsmtpsib-73f7e08afbd853d9e9d2e8c2049f93badd7e7f2b34e3e9de937eee1a307cace3-cmEy8jEk90T1HM6Z'
                    }
                }));
            }
        });
    });
}


// Route to handle form submissions
app.post('/save-message', (req, res) => {
    const { name, email, subject, message } = req.body;
    
    if (!name || !email || !subject || !message) {
        return res.status(400).json({ success: false, error: 'All fields are required.' });
    }

    // Extract IP address
    let ip = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
    if (Array.isArray(ip)) ip = ip[0];
    if (ip && ip.startsWith('::ffff:')) ip = ip.substring(7);

    const sql = `INSERT INTO messages (name, email, subject, message, ip_address) VALUES (?, ?, ?, ?, ?)`;
    const params = [name, email, subject, message, ip];

    db.run(sql, params, function(err) {
        if (err) {
            console.error('Error inserting message', err.message);
            return res.status(500).json({ success: false, error: 'Database error' });
        }
        
        // Send Email Notification (Background)
        getTransporter().then(transporter => {
            if (transporter) {
                const mailOptions = {
                    from: `"Personal Site" <${email}>`,
                    to: transporter.options.auth.user,
                    subject: `New Message: ${subject}`,
                    text: `From: ${name} (${email})\n\nSubject: ${subject}\n\nMessage:\n${message}\n\nIP: ${ip}`
                };
                transporter.sendMail(mailOptions, (error) => {
                    if (error) console.error('Email notify fail:', error.message);
                });
            }
        });

        res.status(200).json({ success: true });
    });
});

// Route to get all messages (Admin only)
app.get('/api/messages', requireAdmin, (req, res) => {
    db.all(`SELECT * FROM messages ORDER BY created_at DESC`, [], (err, rows) => {
        if (err) {
            console.error('Error fetching messages', err.message);
            return res.status(500).json({ success: false, error: 'Database error' });
        }
        res.status(200).json({ success: true, messages: rows });
    });
});

// Route to upload a new profile picture (Admin only)
app.post('/api/upload-picture', requireAdmin, upload.single('profilePic'), (req, res) => {
    if (!req.file) {
        return res.status(400).json({ success: false, error: 'No file uploaded.' });
    }
    
    db.serialize(() => {
        // 1. Insert into history
        db.run(`INSERT INTO profile_pictures (value) VALUES (?)`, [req.file.buffer], function(err) {
            if (err) {
                console.error('Error saving picture to history', err.message);
                return res.status(500).json({ success: false, error: 'Database error' });
            }
            
            const newId = this.lastID;
            
            // 2. Update current setting
            db.run(`INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)`, ['current_profile_id', newId.toString()], (err) => {
                if (err) {
                    return res.status(500).json({ success: false, error: 'Database error' });
                }
                res.status(200).json({ success: true, message: 'Profile picture updated and saved to history.' });
            });
        });
    });
});

// Route to fetch the current profile picture
app.get('/api/profile-pic', (req, res) => {
    db.get(`SELECT value FROM profile_pictures WHERE id = (SELECT value FROM settings WHERE key = ?)`, ['current_profile_id'], (err, row) => {
        if (err || !row) {
            return res.redirect('https://images.unsplash.com/photo-1560250097-0b93528c311a?q=80&w=240&auto=format&fit=crop');
        }
        res.set('Content-Type', 'image/jpeg');
        res.send(row.value);
    });
});

// Route to fetch a specific profile picture from history
app.get('/api/profile-pic/:id', (req, res) => {
    db.get(`SELECT value FROM profile_pictures WHERE id = ?`, [req.params.id], (err, row) => {
        if (err || !row) return res.status(404).send('Not found');
        res.set('Content-Type', 'image/jpeg');
        res.send(row.value);
    });
});

// Route to fetch profile picture history (IDs only)
app.get('/api/profile-history', requireAdmin, (req, res) => {
    db.all(`SELECT id, created_at FROM profile_pictures ORDER BY created_at DESC`, (err, rows) => {
        if (err) return res.status(500).json({ success: false });
        res.json({ success: true, history: rows });
    });
});

// Route to set a historical picture as current
app.post('/api/select-profile-pic/:id', requireAdmin, (req, res) => {
    db.run(`INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)`, ['current_profile_id', req.params.id], (err) => {
        if (err) return res.status(500).json({ success: false });
        res.json({ success: true });
    });
});

// Route to delete a picture from history
app.delete('/api/profile-pic/:id', requireAdmin, (req, res) => {
    db.run(`DELETE FROM profile_pictures WHERE id = ?`, [req.params.id], function(err) {
        if (err) return res.status(500).json({ success: false });
        res.json({ success: true });
    });
});

// Route to switch to a historical profile picture
app.post('/api/use-history-pic/:id', requireAdmin, (req, res) => {
    const { id } = req.params;
    db.run(`INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)`, ['profile_picture_id', id], (err) => {
        if (err) return res.status(500).json({ success: false, error: err.message });
        res.json({ success: true });
    });
});

// Route to delete a historical profile picture
app.delete('/api/profile-history/:id', requireAdmin, (req, res) => {
    const { id } = req.params;
    db.run(`DELETE FROM profile_pictures WHERE id = ?`, [id], (err) => {
        if (err) return res.status(500).json({ success: false, error: err.message });
        res.json({ success: true });
    });
});

// Route to track a visit (called from frontend)
app.post('/api/track-visit', (req, res) => {
    const ip = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
    const { path } = req.body;
    db.run(`INSERT INTO visitors (ip, user_agent, path) VALUES (?, ?, ?)`, 
        [ip, req.headers['user-agent'], path || '/'], (err) => {
            res.json({ success: !err });
        });
});

// Route to fetch system stats, PM2, and Analytics
app.get('/api/system-stats', requireAdmin, (req, res) => {
    const stats = {
        os: {
            platform: os.platform(),
            release: os.release(),
            uptime: os.uptime(),
            totalMem: os.totalmem(),
            freeMem: os.freemem(),
            cpus: os.cpus().length,
            loadAvg: os.loadavg()
        },
        pm2: [],
        analytics: {
            totalViews: 0,
            uniqueVisitors: 0,
            recentVisitors: []
        }
    };

    // Parallel fetch for PM2 and DB stats
    db.get(`SELECT COUNT(*) as views, COUNT(DISTINCT ip) as unique_v FROM visitors`, (err, row) => {
        if (row) {
            stats.analytics.totalViews = row.views;
            stats.analytics.uniqueVisitors = row.unique_v;
        }
        
        db.all(`SELECT * FROM visitors ORDER BY timestamp DESC LIMIT 10`, (err, rows) => {
            if (rows) stats.analytics.recentVisitors = rows;

            exec('pm2 jlist', (error, stdout, stderr) => {
                if (!error && stdout) {
                    try {
                        const pm2Data = JSON.parse(stdout);
                        stats.pm2 = pm2Data.map(p => ({
                            name: p.name,
                            status: p.pm2_env.status,
                            cpu: p.monit.cpu,
                            memory: p.monit.memory,
                            uptime: Math.floor((Date.now() - p.pm2_env.pm_uptime) / 1000),
                            restarts: p.pm2_env.restart_time
                        }));
                    } catch (e) {}
                }
                res.json({ success: true, stats });
            });
        });
    });
});

// --- NEW FEATURES API ---

// Portfolio Management
app.get('/api/projects', (req, res) => {
    db.all(`SELECT id, title, description, link, created_at FROM projects ORDER BY created_at DESC`, (err, rows) => {
        res.json({ success: true, projects: rows || [] });
    });
});

app.get('/api/project-image/:id', (req, res) => {
    db.get(`SELECT image_blob FROM projects WHERE id = ?`, [req.params.id], (err, row) => {
        if (row && row.image_blob) {
            res.set('Content-Type', 'image/jpeg');
            return res.send(row.image_blob);
        }
        res.status(404).send('Not found');
    });
});

app.post('/api/projects', requireAdmin, upload.single('image'), (req, res) => {
    const { title, description, link } = req.body;
    const imageBlob = req.file ? req.file.buffer : null;
    db.run(`INSERT INTO projects (title, description, link, image_blob) VALUES (?, ?, ?, ?)`,
        [title, description, link, imageBlob], function(err) {
            if (err) return res.status(500).json({ success: false });
            res.json({ success: true, id: this.lastID });
        });
});

app.delete('/api/projects/:id', requireAdmin, (req, res) => {
    db.run(`DELETE FROM projects WHERE id = ?`, [req.params.id], (err) => {
        res.json({ success: !err });
    });
});

// Security & Logs
app.get('/api/access-logs', requireAdmin, (req, res) => {
    db.all(`SELECT * FROM access_logs ORDER BY timestamp DESC LIMIT 50`, (err, rows) => {
        res.json({ success: true, logs: rows || [] });
    });
});

app.post('/api/ban-ip', requireAdmin, (req, res) => {
    const { ip, reason } = req.body;
    db.run(`INSERT OR REPLACE INTO banned_ips (ip, reason) VALUES (?, ?)`, [ip, reason], (err) => {
        res.json({ success: !err });
    });
});

app.get('/api/banned-ips', requireAdmin, (req, res) => {
    db.all(`SELECT * FROM banned_ips`, (err, rows) => {
        res.json({ success: true, ips: rows || [] });
    });
});

app.delete('/api/ban-ip/:ip', requireAdmin, (req, res) => {
    db.run(`DELETE FROM banned_ips WHERE ip = ?`, [req.params.ip], (err) => {
        res.json({ success: !err });
    });
});

// Settings Management
app.get('/api/settings', requireAdmin, (req, res) => {
    db.all(`SELECT * FROM settings`, (err, rows) => {
        if (err) console.error('Settings fetch error:', err.message);
        res.json({ success: true, settings: rows || [] });
    });
});

app.post('/api/settings', requireAdmin, (req, res) => {
    const { key, value } = req.body;
    console.log(`Updating setting: ${key} = ${key === 'smtp_password' ? '********' : value}`);
    db.run(`INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)`, [key, value], (err) => {
        if (err) console.error('Settings update error:', err.message);
        res.json({ success: !err });
    });
});

app.post('/api/test-email', requireAdmin, async (req, res) => {
    const transporter = await getTransporter();
    if (!transporter) return res.json({ success: false, error: 'SMTP credentials not configured. Please save your Gmail and App Password first.' });
    
    const mailOptions = {
        from: '"Personal Site Test" <noreply@faizal.uk>',
        to: transporter.options.auth.user,
        subject: 'Test Email from Personal Site',
        text: 'If you are reading this, your SMTP configuration is working perfectly!'
    };
    
    transporter.sendMail(mailOptions, (error) => {
        if (error) return res.json({ success: false, error: error.message });
        res.json({ success: true });
    });
});

// Update PIN validation to log attempts
app.post('/api/verify-pin', (req, res) => {
    const { pin } = req.body;
    const ip = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
    db.get("SELECT value FROM settings WHERE key = 'admin_pin' AND length(value) = 4", [], (err, row) => {
        const effectivePin = (row && row.value) ? row.value : '2004';
        const isValid = pin === effectivePin;
        const status = isValid ? 'SUCCESS' : 'FAILURE';
        db.run(`INSERT INTO access_logs (ip, status) VALUES (?, ?)`, [ip, status], (logErr) => {
            if (logErr) console.error('Access logs log error:', logErr.message);
        });
        res.json({ success: isValid });
    });
});

app.post('/api/change-pin', (req, res) => {
    const { current_pin, new_pin } = req.body;
    const ip = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
    db.get("SELECT value FROM settings WHERE key = 'admin_pin' AND length(value) = 4", [], (err, row) => {
        const effectivePin = (row && row.value) ? row.value : '2004';
        if (current_pin !== effectivePin) {
            return res.status(403).json({ success: false, error: 'Current 4-digit PIN is incorrect.' });
        }
        if (!new_pin || new_pin.length !== 4 || !/^\d{4}$/.test(new_pin)) {
            return res.status(400).json({ success: false, error: 'New PIN must be exactly 4 numeric digits.' });
        }
        db.run("INSERT OR REPLACE INTO settings (key, value) VALUES ('admin_pin', ?)", [new_pin], (saveErr) => {
            if (saveErr) {
                return res.status(500).json({ success: false, error: saveErr.message });
            }
            db.run(`INSERT INTO access_logs (ip, status) VALUES (?, 'PIN_CHANGED')`, [ip]);
            res.json({ success: true, message: 'Admin PIN updated successfully.' });
        });
    });
});

// Route to delete a message (Admin only)
app.delete('/api/messages/:id', requireAdmin, (req, res) => {
    const { id } = req.params;
    db.run(`DELETE FROM messages WHERE id = ?`, [id], function(err) {
        if (err) {
            console.error('Error deleting message', err.message);
            return res.status(500).json({ success: false, error: 'Database error' });
        }
        res.status(200).json({ success: true });
    });
});

// ==========================================
// VM HEALTH & 24-HOUR REPORTING API ENDPOINTS
// ==========================================

// Route to fetch real-time full VM Health and Performance data
app.get('/api/health-report/data', requireAdmin, async (req, res) => {
    try {
        const data = await collectSystemHealth();
        res.json({ success: true, data });
    } catch (err) {
        console.error('Health data error:', err);
        res.status(500).json({ success: false, error: err.message });
    }
});

// Route to fetch rendered HTML report preview
app.get('/api/health-report/preview', requireAdmin, async (req, res) => {
    try {
        const data = await collectSystemHealth();
        const html = generateReportHtml(data);
        res.setHeader('Content-Type', 'text/html');
        res.send(html);
    } catch (err) {
        res.status(500).send(`Error generating preview: ${err.message}`);
    }
});

// Route to trigger sending health report email immediately
app.post('/api/health-report/send', requireAdmin, async (req, res) => {
    try {
        const { recipient } = req.body || {};
        const result = await sendReportEmail({ recipient, trigger: 'admin_dashboard' });
        res.json(result);
    } catch (err) {
        res.status(500).json({ success: false, error: err.message });
    }
});

// Route to fetch health report configuration, status, and history
app.get('/api/health-report/status', requireAdmin, (req, res) => {
    db.all(`SELECT key, value FROM settings WHERE key IN ('report_recipient_email', 'report_enabled', 'report_time', 'last_report_timestamp', 'last_report_status')`, (err, rows) => {
        const settings = {};
        if (rows) {
            rows.forEach(r => settings[r.key] = r.value);
        }
        db.all(`SELECT * FROM health_reports ORDER BY timestamp DESC LIMIT 15`, (err2, reports) => {
            res.json({
                success: true,
                settings: {
                    recipient: settings.report_recipient_email || 'me@faizal.uk',
                    enabled: settings.report_enabled !== 'false',
                    time: settings.report_time || '08:00',
                    lastSent: settings.last_report_timestamp || null,
                    lastStatus: settings.last_report_status || null
                },
                history: reports || []
            });
        });
    });
});

// Route to update report configuration
app.post('/api/health-report/config', requireAdmin, (req, res) => {
    const { recipient, enabled, time } = req.body || {};
    db.serialize(() => {
        if (recipient !== undefined) db.run(`INSERT OR REPLACE INTO settings (key, value) VALUES ('report_recipient_email', ?)`, [recipient]);
        if (enabled !== undefined) db.run(`INSERT OR REPLACE INTO settings (key, value) VALUES ('report_enabled', ?)`, [String(enabled)]);
        if (time !== undefined) db.run(`INSERT OR REPLACE INTO settings (key, value) VALUES ('report_time', ?)`, [time]);
        res.json({ success: true, message: 'Health report settings updated.' });
    });
});

// Route to fetch 24-hour error log summary
app.get('/api/health-report/errors', requireAdmin, async (req, res) => {
    try {
        const data = await collectSystemHealth();
        res.json({ success: true, errors: data.errors24h, healthIssues: data.healthIssues });
    } catch (err) {
        res.status(500).json({ success: false, error: err.message });
    }
});

// Start the server
app.listen(PORT, () => {
    console.log(`Server running on http://localhost:${PORT}`);
});

// Handle graceful shutdown
process.on('SIGTERM', () => {
    db.close();
    process.exit(0);
});
process.on('SIGINT', () => {
    db.close();
    process.exit(0);
});
