import React, { useState, useEffect, useCallback, useRef } from 'react';

const S = {
  section: {
    marginBottom: 32,
    background: 'rgba(18,19,22,0.85)',
    border: '1px solid rgba(137,255,0,0.12)',
    borderRadius: 12,
    overflow: 'hidden',
  },
  sectionHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: 10,
    padding: '14px 20px',
    borderBottom: '1px solid rgba(137,255,0,0.08)',
    background: 'rgba(137,255,0,0.04)',
  },
  sectionTitle: {
    fontFamily: "'JetBrains Mono', monospace",
    fontSize: 12,
    fontWeight: 700,
    color: '#89ff00',
    letterSpacing: '0.08em',
    textTransform: 'uppercase',
    margin: 0,
  },
  sectionBody: { padding: '18px 20px' },
  row: { display: 'flex', gap: 16, marginBottom: 16, flexWrap: 'wrap' },
  field: { display: 'flex', flexDirection: 'column', gap: 6, flex: 1, minWidth: 200 },
  label: {
    fontFamily: "'JetBrains Mono', monospace",
    fontSize: 10,
    fontWeight: 600,
    color: 'rgba(137,255,0,0.65)',
    letterSpacing: '0.1em',
    textTransform: 'uppercase',
  },
  input: {
    background: 'rgba(255,255,255,0.04)',
    border: '1px solid rgba(255,255,255,0.1)',
    borderRadius: 8,
    padding: '9px 12px',
    color: '#fff',
    fontFamily: "'Inter', sans-serif",
    fontSize: 13,
    outline: 'none',
    transition: 'border-color 0.2s',
  },
  textarea: {
    background: 'rgba(255,255,255,0.04)',
    border: '1px solid rgba(255,255,255,0.1)',
    borderRadius: 8,
    padding: '9px 12px',
    color: '#fff',
    fontFamily: "'Inter', sans-serif",
    fontSize: 13,
    outline: 'none',
    resize: 'vertical',
    minHeight: 80,
    width: '100%',
    boxSizing: 'border-box',
  },
  btnPrimary: {
    background: '#89ff00',
    color: '#0a0b0d',
    border: 'none',
    borderRadius: 8,
    padding: '9px 18px',
    fontFamily: "'JetBrains Mono', monospace",
    fontSize: 12,
    fontWeight: 700,
    cursor: 'pointer',
    letterSpacing: '0.05em',
    transition: 'opacity 0.2s',
  },
  btnSecondary: {
    background: 'rgba(137,255,0,0.1)',
    color: '#89ff00',
    border: '1px solid rgba(137,255,0,0.3)',
    borderRadius: 8,
    padding: '9px 18px',
    fontFamily: "'JetBrains Mono', monospace",
    fontSize: 12,
    fontWeight: 700,
    cursor: 'pointer',
    letterSpacing: '0.05em',
    transition: 'background 0.2s',
  },
  btnDanger: {
    background: 'rgba(255,80,80,0.08)',
    color: '#ff5050',
    border: '1px solid rgba(255,80,80,0.25)',
    borderRadius: 6,
    padding: '5px 12px',
    fontFamily: "'JetBrains Mono', monospace",
    fontSize: 10,
    fontWeight: 700,
    cursor: 'pointer',
    letterSpacing: '0.05em',
  },
  statusBadge: (on) => ({
    display: 'inline-flex',
    alignItems: 'center',
    gap: 6,
    padding: '4px 12px',
    borderRadius: 20,
    background: on ? 'rgba(137,255,0,0.12)' : 'rgba(255,80,80,0.1)',
    border: `1px solid ${on ? 'rgba(137,255,0,0.3)' : 'rgba(255,80,80,0.3)'}`,
    fontFamily: "'JetBrains Mono', monospace",
    fontSize: 11,
    fontWeight: 700,
    color: on ? '#89ff00' : '#ff5050',
    letterSpacing: '0.08em',
  }),
  pulseDot: (on) => ({
    width: 7,
    height: 7,
    borderRadius: '50%',
    background: on ? '#89ff00' : '#ff5050',
    boxShadow: on ? '0 0 6px #89ff00' : '0 0 6px #ff5050',
    animation: on ? 'arPulse 1.6s ease-in-out infinite' : 'none',
  }),
  toggleTrack: (on) => ({
    width: 44,
    height: 24,
    borderRadius: 12,
    background: on ? '#89ff00' : 'rgba(255,255,255,0.1)',
    border: on ? '1px solid #89ff00' : '1px solid rgba(255,255,255,0.15)',
    position: 'relative',
    cursor: 'pointer',
    transition: 'all 0.25s',
    flexShrink: 0,
  }),
  toggleThumb: (on) => ({
    position: 'absolute',
    top: 3,
    left: on ? 22 : 3,
    width: 16,
    height: 16,
    borderRadius: '50%',
    background: on ? '#0a0b0d' : 'rgba(255,255,255,0.4)',
    transition: 'left 0.25s',
  }),
  logTable: {
    width: '100%',
    borderCollapse: 'collapse',
    fontFamily: "'JetBrains Mono', monospace",
    fontSize: 11,
  },
  logTh: {
    padding: '8px 10px',
    textAlign: 'left',
    color: 'rgba(137,255,0,0.6)',
    fontWeight: 700,
    borderBottom: '1px solid rgba(137,255,0,0.1)',
    letterSpacing: '0.08em',
    fontSize: 10,
    textTransform: 'uppercase',
  },
  logTd: {
    padding: '7px 10px',
    color: 'rgba(255,255,255,0.75)',
    borderBottom: '1px solid rgba(255,255,255,0.04)',
    fontSize: 11,
    verticalAlign: 'middle',
  },
  previewWrap: {
    borderRadius: 8,
    overflow: 'hidden',
    border: '1px solid rgba(255,255,255,0.08)',
    background: '#fff',
  },
  chip: (active) => ({
    padding: '5px 12px',
    borderRadius: 6,
    background: active ? 'rgba(137,255,0,0.15)' : 'rgba(255,255,255,0.05)',
    border: `1px solid ${active ? 'rgba(137,255,0,0.4)' : 'rgba(255,255,255,0.1)'}`,
    color: active ? '#89ff00' : 'rgba(255,255,255,0.5)',
    fontFamily: "'JetBrains Mono', monospace",
    fontSize: 11,
    fontWeight: 700,
    cursor: 'pointer',
    letterSpacing: '0.05em',
  }),
};

const DEFAULTS = {
  enabled: 'true',
  cooldown_days: '7',
  suppress_self: 'true',
  suppress_bulk: 'true',
  whitelist: '',
  blacklist: '',
  from_name: 'Mohammad Faizal (Auto-Reply)',
  from_email: 'me@faizal.uk',
  reply_to: 'me@faizal.uk',
  hero_banner_url: 'https://faizal.uk/images/mail/hero_aurora_v2.jpg?v=2',
  headline: 'Got your message.',
  salutation: 'Hi there,',
  body_paragraph:
    'Thanks so much for reaching out and taking the time to check out my work. This is a quick automated note to let you know your email landed safely in my inbox.',
  response_window: '1–2 DAYS',
  callout_message: 'I personally read every message and typically reply within 1 to 2 business days.',
  closing_paragraph: 'In the meantime, feel free to browse some of my latest projects and engineering work below.',
  cta_primary_text: 'Explore Portfolio →',
  cta_primary_url: 'https://faizal.uk',
  cta_secondary_text: 'Selected Works',
  cta_secondary_url: 'https://faizal.uk/work',
  footer_note: 'Made with love & curiosity 🤍',
};

function Toggle({ on, onChange, label }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
      <div style={S.toggleTrack(on)} onClick={() => onChange(!on)}>
        <div style={S.toggleThumb(on)} />
      </div>
      {label && <span style={{ color: 'rgba(255,255,255,0.65)', fontSize: 13, fontFamily: "'Inter', sans-serif" }}>{label}</span>}
    </div>
  );
}

export default function AutoResponderStudio({ fetchWithAuth, showToast }) {
  const [config, setConfig] = useState(DEFAULTS);
  const [initialLoading, setInitialLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [refreshingConfig, setRefreshingConfig] = useState(false);
  const [previewHtml, setPreviewHtml] = useState('');
  const [previewMode, setPreviewMode] = useState('desktop');
  const [previewEmail, setPreviewEmail] = useState('visitor@example.com');
  const [testEmail, setTestEmail] = useState('');
  const [testSending, setTestSending] = useState(false);
  const [logs, setLogs] = useState([]);
  const [logsLoading, setLogsLoading] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [sigGuideTab, setSigGuideTab] = useState('iphone');
  const [sigCopied, setSigCopied] = useState(false);
  const [templateCopied, setTemplateCopied] = useState(false);
  const [blankCopied, setBlankCopied] = useState(false);
  const [blankSending, setBlankSending] = useState(false);
  const [logoCopied, setLogoCopied] = useState(false);
  const previewDebounce = useRef(null);
  const isFirstMount = useRef(true);
  const showToastRef = useRef(showToast);
  showToastRef.current = showToast;

  const FZ_BASE64 = "data:image/png;base64,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";

  const SIGNATURE_HTML = `<table cellpadding="0" cellspacing="0" border="0" style="font-family: 'Inter', -apple-system, BlinkMacSystemFont, Arial, sans-serif; font-size: 13px; color: #111111; line-height: 1.4; border-top: 2px solid #89ff00; padding-top: 14px; margin-top: 18px; max-width: 480px;">
  <tr>
    <td style="vertical-align: middle; padding-right: 14px; border-right: 1px solid #e0e0e0;">
      <a href="https://faizal.uk" target="_blank" style="text-decoration: none;">
        <img src="https://faizal.uk/images/mail/fz_logo.png" height="28" width="53" alt="FZ" style="display: block; height: 28px; width: 53px; border: 0;" />
      </a>
    </td>
    <td style="vertical-align: middle; padding-left: 14px;">
      <div style="font-size: 14px; font-weight: 800; color: #111111; letter-spacing: -0.2px; text-transform: uppercase;">MOHAMMAD FAIZAL</div>
      <div style="font-size: 12px; color: #555555; font-family: 'JetBrains Mono', monospace; margin-top: 2px;">Full-Stack Engineer &amp; Systems Architect</div>
      <div style="font-size: 11px; margin-top: 6px; font-weight: 600;">
        <a href="https://faizal.uk" target="_blank" style="color: #1A1AE8; text-decoration: none;">faizal.uk</a>
        <span style="color: #cccccc; margin: 0 6px;">|</span>
        <a href="https://faizal.uk/work" target="_blank" style="color: #1A1AE8; text-decoration: none;">Selected Works</a>
        <span style="color: #cccccc; margin: 0 6px;">|</span>
        <a href="mailto:me@faizal.uk" style="color: #1A1AE8; text-decoration: none;">me@faizal.uk</a>
      </div>
    </td>
  </tr>
</table>`;

  const BLANK_COMPOSER_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Mohammad Faizal</title>
<style>
  body { margin: 0; padding: 0; background-color: #f2f1f5; -webkit-text-size-adjust: none; }
  @media only screen and (max-width: 620px) {
    .main-tbl { width: 100% !important; }
    .hdr-pad { padding: 14px 14px 12px 14px !important; }
    .body-pad { padding: 24px 18px !important; }
  }
</style>
</head>
<body style="margin: 0; padding: 0; background-color: #f2f1f5;">
  <center style="width: 100%; background-color: #f2f1f5; padding: 24px 0 32px 0;">
    <table class="main-tbl" border="0" cellpadding="0" cellspacing="0" width="600" style="max-width: 600px; width: 100%; border-collapse: collapse; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 24px rgba(0,0,0,0.06);">
      <!-- Top Pixelated Bar -->
      <tr>
        <td style="padding: 0; line-height: 0; font-size: 0;">
          <img src="https://faizal.uk/images/mail/pixelated-bar.png" width="600" alt="" style="display: block; width: 100%; max-width: 600px; height: auto; border: 0;" />
        </td>
      </tr>
      <!-- Brand Header -->
      <tr>
        <td class="hdr-pad" style="background-color: #ffffff; padding: 18px 24px 14px 24px; border-bottom: 1px solid #f0edf5;">
          <table border="0" cellpadding="0" cellspacing="0" width="100%">
            <tr>
              <td align="left" style="vertical-align: middle; white-space: nowrap;">
                <img src="https://faizal.uk/images/mail/fz_logo.png" height="28" width="53" alt="FZ" style="display: inline-block; vertical-align: middle; height: 28px; width: 53px; border: 0;" />
                <span style="font-family: 'Inter', -apple-system, BlinkMacSystemFont, Arial, sans-serif; font-size: 16px; font-weight: 800; letter-spacing: -0.2px; color: #111111; vertical-align: middle; margin-left: 10px; text-transform: uppercase;">
                  MOHAMMAD FAIZAL
                </span>
              </td>
              <td align="right" style="vertical-align: middle; white-space: nowrap;">
                <span style="display: inline-block; padding: 4px 12px; border-radius: 50px; background-color: #111111; font-family: 'JetBrains Mono', monospace; font-size: 11px; font-weight: 700; color: #89ff00; letter-spacing: 0.5px; text-transform: uppercase;">
                  DIRECT
                </span>
              </td>
            </tr>
          </table>
        </td>
      </tr>
      <!-- Clean Content Container -->
      <tr>
        <td class="body-pad" style="background-color: #ffffff; padding: 32px 28px 24px 28px;">
          <p style="margin: 0 0 16px 0; font-family: 'Inter', -apple-system, BlinkMacSystemFont, Arial, sans-serif; font-size: 15px; color: #222222; line-height: 1.6;">Hi [Name],</p>
          <p style="margin: 0 0 16px 0; font-family: 'Inter', -apple-system, BlinkMacSystemFont, Arial, sans-serif; font-size: 15px; color: #222222; line-height: 1.6;">[Write your message here...]</p>
          <p style="margin: 0 0 24px 0; font-family: 'Inter', -apple-system, BlinkMacSystemFont, Arial, sans-serif; font-size: 15px; color: #222222; line-height: 1.6;">Best regards,<br /><strong style="color: #111111;">Mohammad Faizal</strong></p>
        </td>
      </tr>
      <!-- Footer Navigation & Personal Signature -->
      <tr>
        <td style="background-color: #faf9fc; padding: 24px 24px 20px 24px; border-top: 1px solid #f0edf5; text-align: center;">
          <div style="margin-bottom: 12px;">
            <img src="https://faizal.uk/images/mail/fz_logo.png" height="24" width="45" alt="FZ" style="display: inline-block; height: 24px; width: 45px; border: 0;" />
          </div>
          <div style="margin-bottom: 12px; font-family: 'Inter', Arial, sans-serif; font-size: 12px; font-weight: 600;">
            <a href="https://faizal.uk" style="color: #111111; text-decoration: none; margin: 0 8px;">Portfolio</a>
            <span style="color: #d0cbd8;">•</span>
            <a href="https://faizal.uk/work" style="color: #111111; text-decoration: none; margin: 0 8px;">Selected Works</a>
            <span style="color: #d0cbd8;">•</span>
            <a href="mailto:me@faizal.uk" style="color: #111111; text-decoration: none; margin: 0 8px;">Direct Contact</a>
          </div>
          <p style="margin: 0; font-family: 'Inter', Arial, sans-serif; font-size: 12px; color: #716e7e; line-height: 1.6;">
            Mohammad Faizal · <a href="mailto:me@faizal.uk" style="color: #716e7e; text-decoration: none;">me@faizal.uk</a><br />
            © 2026 Mohammad Faizal. All rights reserved.
          </p>
        </td>
      </tr>
      <!-- Bottom Pixelated Bar -->
      <tr>
        <td style="padding: 0; line-height: 0; font-size: 0;">
          <img src="https://faizal.uk/images/mail/pixelated-bar.png" width="600" alt="" style="display: block; width: 100%; max-width: 600px; height: auto; border: 0;" />
        </td>
      </tr>
    </table>
  </center>
</body>
</html>`;

  const copyRichSignature = async () => {
    try {
      const text = "Mohammad Faizal — Full-Stack Engineer & Systems Architect | https://faizal.uk";
      const htmlPayload = `<html><body><!--StartFragment-->${SIGNATURE_HTML}<!--EndFragment--></body></html>`;
      const blobHtml = new Blob([htmlPayload], { type: 'text/html' });
      const blobText = new Blob([text], { type: 'text/plain' });
      await navigator.clipboard.write([
        new ClipboardItem({
          'text/html': blobHtml,
          'text/plain': blobText,
        })
      ]);
      setSigCopied(true);
      setTimeout(() => setSigCopied(false), 3000);
      showToastRef.current?.('Rich signature copied! Ready to paste into Gmail / Apple Mail settings', 'success');
    } catch (e) {
      await navigator.clipboard.writeText(SIGNATURE_HTML);
      setSigCopied(true);
      setTimeout(() => setSigCopied(false), 3000);
      showToastRef.current?.('HTML signature code copied to clipboard!', 'success');
    }
  };

  const copyLogoUrl = async () => {
    try {
      await navigator.clipboard.writeText('https://faizal.uk/images/mail/fz_logo.png');
      setLogoCopied(true);
      setTimeout(() => setLogoCopied(false), 3000);
      showToastRef.current?.('Logo URL copied! In Gmail Signature settings, click the 🖼️ Image icon and paste this URL', 'success');
    } catch (e) {
      showToastRef.current?.('Failed to copy logo URL', 'error');
    }
  };

  const copyBlankComposer = async () => {
    try {
      await navigator.clipboard.writeText(BLANK_COMPOSER_HTML);
      setBlankCopied(true);
      setTimeout(() => setBlankCopied(false), 3000);
      showToastRef.current?.('Blank Composer HTML copied to clipboard!', 'success');
    } catch (e) {
      showToastRef.current?.('Failed to copy blank composer HTML', 'error');
    }
  };

  const sendBlankStarter = async () => {
    setBlankSending(true);
    try {
      showToastRef.current?.('Sending blank composer starter to me@faizal.uk…', 'info');
      const res = await fetchWithAuth('/api/admin/auto-responder/test', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          to: 'me@faizal.uk',
          custom_html: BLANK_COMPOSER_HTML,
          custom_subject: 'Mohammad Faizal — Email Template Starter',
        }),
      });
      const data = await res.json();
      if (data.success) {
        showToastRef.current?.('✓ Sent to me@faizal.uk! Star it or save as template in Gmail to reply/compose anytime!', 'success');
      } else {
        showToastRef.current?.(`Send failed: ${data.error}`, 'error');
      }
    } catch (e) {
      showToastRef.current?.('Network error sending starter email', 'error');
    } finally {
      setBlankSending(false);
    }
  };

  const copyFullTemplate = async () => {
    try {
      await navigator.clipboard.writeText(previewHtml || '');
      setTemplateCopied(true);
      setTimeout(() => setTemplateCopied(false), 3000);
      showToastRef.current?.('Full email template HTML copied to clipboard!', 'success');
    } catch (e) {
      showToastRef.current?.('Failed to copy template code', 'error');
    }
  };

  // ── Load config ─────────────────────────────────────────────────────────────
  const loadConfig = useCallback(async (isInitial = false) => {
    if (!isInitial) setRefreshingConfig(true);
    try {
      const res = await fetchWithAuth('/api/auto-responder/config');
      const data = await res.json();
      if (data.success && data.config) {
        setConfig((prev) => ({ ...prev, ...data.config }));
      }
    } catch (e) {
      showToastRef.current?.('Failed to load auto-responder config', 'error');
    } finally {
      if (isInitial) setInitialLoading(false);
      else setRefreshingConfig(false);
    }
  }, [fetchWithAuth]);

  // ── Load logs ────────────────────────────────────────────────────────────────
  const loadLogs = useCallback(async () => {
    setLogsLoading(true);
    try {
      const res = await fetchWithAuth('/api/auto-responder/logs');
      const data = await res.json();
      if (data.success) setLogs(data.logs || []);
    } catch (e) {
      // silent
    } finally {
      setLogsLoading(false);
    }
  }, [fetchWithAuth]);

  // ── Live preview ─────────────────────────────────────────────────────────────
  const refreshPreview = useCallback(async (targetEmail) => {
    const emailToUse = targetEmail || previewEmail;
    setPreviewLoading(true);
    try {
      const res = await fetchWithAuth(
        `/api/auto-responder/preview-html?email=${encodeURIComponent(emailToUse)}`
      );
      const html = await res.text();
      setPreviewHtml(html);
    } catch (e) {
      // silent
    } finally {
      setPreviewLoading(false);
    }
  }, [fetchWithAuth, previewEmail]);

  // Initial load on mount only (guarantees zero flicker loops)
  useEffect(() => {
    loadConfig(true);
    loadLogs();
    refreshPreview('visitor@example.com');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Debounced live preview when user actively edits config or preview email
  useEffect(() => {
    if (isFirstMount.current) {
      isFirstMount.current = false;
      return;
    }
    if (previewDebounce.current) clearTimeout(previewDebounce.current);
    previewDebounce.current = setTimeout(() => {
      refreshPreview();
    }, 800);
    return () => clearTimeout(previewDebounce.current);
  }, [config, previewEmail, refreshPreview]);

  // ── Save config ──────────────────────────────────────────────────────────────
  const saveConfig = async () => {
    setSaving(true);
    const settings = {};
    for (const [k, v] of Object.entries(config)) {
      settings[`auto_responder_${k}`] = String(v);
    }
    try {
      const res = await fetchWithAuth('/api/auto-responder/config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ settings }),
      });
      const data = await res.json();
      if (data.success) {
        showToast?.('Auto-responder config saved ✓', 'success');
      } else {
        showToast?.(`Save failed: ${data.error}`, 'error');
      }
    } catch (e) {
      showToast?.('Network error saving config', 'error');
    } finally {
      setSaving(false);
    }
  };

  // ── Field updater ────────────────────────────────────────────────────────────
  const set = (key) => (e) => {
    const val = typeof e === 'boolean' ? (e ? 'true' : 'false') : e.target.value;
    setConfig((prev) => ({ ...prev, [key]: val }));
  };

  // ── Send test ────────────────────────────────────────────────────────────────
  const sendTest = async () => {
    if (!testEmail.trim()) {
      showToast?.('Enter a test email address first', 'error');
      return;
    }
    setTestSending(true);
    try {
      const res = await fetchWithAuth('/api/auto-responder/test', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ to: testEmail.trim() }),
      });
      const data = await res.json();
      if (data.success) {
        showToast?.(data.message || 'Test sent ✓', 'success');
      } else {
        showToast?.(`Test failed: ${data.error}`, 'error');
      }
    } catch (e) {
      showToast?.('Network error sending test', 'error');
    } finally {
      setTestSending(false);
    }
  };

  // ── Clear cooldown ───────────────────────────────────────────────────────────
  const clearCooldown = async (email = null) => {
    try {
      const res = await fetchWithAuth('/api/auto-responder/clear-cooldown', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email }),
      });
      const data = await res.json();
      if (data.success) {
        showToast?.(email ? `Cooldown cleared for ${email}` : `All cooldowns cleared (${data.cleared} rows)`, 'success');
        loadLogs();
      } else {
        showToast?.(`Clear failed: ${data.error}`, 'error');
      }
    } catch (e) {
      showToast?.('Network error', 'error');
    }
  };

  const isOn = config.enabled === 'true';

  if (initialLoading) {
    return (
      <div style={{ padding: 40, textAlign: 'center', color: 'rgba(255,255,255,0.3)', fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>
        Loading auto-responder config…
      </div>
    );
  }

  return (
    <div>
      <style>{`
        @keyframes arPulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.4; }
        }
        .ar-input:focus { border-color: rgba(137,255,0,0.4) !important; }
        .ar-btn:hover { opacity: 0.85; }
        .ar-btn-sec:hover { background: rgba(137,255,0,0.18) !important; }
        .ar-log-row:hover td { background: rgba(137,255,0,0.03) !important; }
      `}</style>

      {/* ── Studio Header ─────────────────────────────────────────────────── */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 24, flexWrap: 'wrap', gap: 12 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 18, fontWeight: 700, color: '#fff', fontFamily: "'Inter', sans-serif" }}>
            Email Auto-Responder Studio
          </h2>
          <p style={{ margin: '4px 0 0', fontSize: 13, color: 'rgba(255,255,255,0.4)', fontFamily: "'Inter', sans-serif" }}>
            Runtime control, copy editing & live preview
          </p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <div style={S.statusBadge(isOn)}>
            <div style={S.pulseDot(isOn)} />
            {isOn ? 'ENGINE ACTIVE' : 'ENGINE PAUSED'}
          </div>
          <Toggle
            on={isOn}
            onChange={(v) => setConfig((prev) => ({ ...prev, enabled: v ? 'true' : 'false' }))}
          />
        </div>
      </div>

      {/* ── Engine Rules ──────────────────────────────────────────────────── */}
      <div style={S.section}>
        <div style={S.sectionHeader}>
          <span style={{ fontSize: 16 }}>⚙️</span>
          <h3 style={S.sectionTitle}>Engine Rules</h3>
        </div>
        <div style={S.sectionBody}>
          <div style={S.row}>
            <div style={S.field}>
              <label style={S.label}>Cooldown Period</label>
              <select
                value={config.cooldown_days}
                onChange={set('cooldown_days')}
                style={{ ...S.input, cursor: 'pointer' }}
                className="ar-input"
              >
                {[1, 2, 3, 5, 7, 14, 21, 30].map((d) => (
                  <option key={d} value={String(d)}>
                    {d} day{d !== 1 ? 's' : ''}
                  </option>
                ))}
              </select>
            </div>
            <div style={{ ...S.field, minWidth: 150 }}>
              <label style={S.label}>Suppress Self</label>
              <Toggle
                on={config.suppress_self === 'true'}
                onChange={(v) => set('suppress_self')({ target: { value: v ? 'true' : 'false' } })}
                label="Skip own domain"
              />
            </div>
            <div style={{ ...S.field, minWidth: 150 }}>
              <label style={S.label}>Suppress Bulk</label>
              <Toggle
                on={config.suppress_bulk === 'true'}
                onChange={(v) => set('suppress_bulk')({ target: { value: v ? 'true' : 'false' } })}
                label="Skip newsletters"
              />
            </div>
          </div>
        </div>
      </div>

      {/* ── Sender Identity & Deliverability ───────────────────────────────── */}
      <div style={S.section}>
        <div style={S.sectionHeader}>
          <span style={{ fontSize: 16 }}>🛡️</span>
          <h3 style={S.sectionTitle}>Sender Identity & Deliverability</h3>
        </div>
        <div style={S.sectionBody}>
          <div style={S.row}>
            <div style={{ ...S.field, flex: 1.5 }}>
              <label style={S.label}>Sender Display Name</label>
              <input
                type="text"
                value={config.from_name}
                onChange={set('from_name')}
                placeholder="Mohammad Faizal (Auto-Reply)"
                style={S.input}
                className="ar-input"
              />
            </div>
            <div style={S.field}>
              <label style={S.label}>Sending Address (From)</label>
              <input
                type="email"
                value={config.from_email}
                onChange={set('from_email')}
                placeholder="me@faizal.uk"
                style={S.input}
                className="ar-input"
              />
            </div>
            <div style={S.field}>
              <label style={S.label}>Reply-To Address</label>
              <input
                type="email"
                value={config.reply_to}
                onChange={set('reply_to')}
                placeholder="me@faizal.uk"
                style={S.input}
                className="ar-input"
              />
            </div>
          </div>
          <div style={{
            padding: '8px 12px',
            borderRadius: 6,
            background: 'rgba(137,255,0,0.06)',
            border: '1px solid rgba(137,255,0,0.18)',
            color: 'rgba(255,255,255,0.7)',
            fontSize: 11,
            fontFamily: "'JetBrains Mono', monospace",
            lineHeight: 1.5
          }}>
            ✓ <span style={{ color: '#89ff00' }}>Anti-Impersonation & Spam Score Optimized</span>: Identifies as an automated confirmation while directing incoming human replies straight to your primary inbox. Image assets and rainbow bars are self-hosted directly on faizal.uk.
          </div>
        </div>
      </div>

      {/* ── Never Auto-Reply List (Exclusions) ────────────────────────── */}
      <div style={S.section}>
        <div style={S.sectionHeader}>
          <span style={{ fontSize: 16 }}>🚫</span>
          <h3 style={S.sectionTitle}>Never Auto-Reply List (Exclusions)</h3>
          <span style={{ marginLeft: 'auto', fontSize: 11, color: '#ff7070', fontFamily: "'JetBrains Mono', monospace", fontWeight: 600 }}>
            Automated Replies Suppressed
          </span>
        </div>
        <div style={S.sectionBody}>
          {/* Safe Inbox Guarantee Banner */}
          <div style={{
            background: 'rgba(137,255,0,0.06)',
            border: '1px solid rgba(137,255,0,0.22)',
            borderRadius: 8,
            padding: '10px 14px',
            marginBottom: 16,
            display: 'flex',
            alignItems: 'flex-start',
            gap: 10
          }}>
            <span style={{ fontSize: 18, lineHeight: 1 }}>🛡️</span>
            <div>
              <div style={{ color: '#89ff00', fontWeight: 600, fontSize: 12, fontFamily: "'JetBrains Mono', monospace" }}>
                SAFE INBOX GUARANTEE
              </div>
              <div style={{ color: 'rgba(255,255,255,0.7)', fontSize: 12, marginTop: 2, lineHeight: 1.5 }}>
                Adding addresses or domains here <strong>ONLY</strong> silences the automated acknowledgement note. Their incoming emails will still land safely in your inbox 100% of the time — nothing in your mailbox is blocked.
              </div>
            </div>
          </div>

          <div style={S.field}>
            <label style={S.label}>Excluded Addresses &amp; Domains</label>
            <textarea
              value={config.blacklist || ''}
              onChange={set('blacklist')}
              placeholder="friend@gmail.com, @colleagues.com, recruiter@company.com, alerts@bank.com"
              style={{
                ...S.textarea,
                minHeight: 85,
                marginTop: 4,
                borderColor: config.blacklist ? 'rgba(255,100,100,0.3)' : undefined,
                fontFamily: "'JetBrains Mono', monospace",
                fontSize: 12
              }}
              className="ar-input"
            />
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 4, flexWrap: 'wrap', gap: 6 }}>
              <span style={{ fontSize: 11, color: 'rgba(255,255,255,0.4)', fontFamily: "'Inter', sans-serif" }}>
                Comma, newline, or semicolon-separated. Use exact emails or <code>@domain.com</code> wildcards to exclude an entire company.
              </span>
              {config.blacklist && (
                <span style={{ fontSize: 11, color: '#89ff00', fontFamily: "'JetBrains Mono', monospace" }}>
                  ✓ {config.blacklist.split(/[,;\n]/).filter(s => s.trim()).length} exclusion rule(s) active
                </span>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* ── Hero Banner ───────────────────────────────────────────────────── */}
      <div style={S.section}>
        <div style={S.sectionHeader}>
          <span style={{ fontSize: 16 }}>🖼️</span>
          <h3 style={S.sectionTitle}>Hero Banner</h3>
        </div>
        <div style={S.sectionBody}>
          <div style={S.row}>
            <div style={{ ...S.field, flex: 2 }}>
              <label style={S.label}>Banner Image URL</label>
              <input
                type="url"
                value={config.hero_banner_url}
                onChange={set('hero_banner_url')}
                placeholder="https://…/email-banner.jpg"
                style={S.input}
                className="ar-input"
              />
            </div>
          </div>
          {config.hero_banner_url && (
            <div style={{ marginTop: 10, borderRadius: 8, overflow: 'hidden', maxWidth: 600, border: '1px solid rgba(255,255,255,0.08)' }}>
              <img
                src={config.hero_banner_url}
                alt="Banner preview"
                style={{ width: '100%', display: 'block', maxHeight: 180, objectFit: 'cover' }}
                onError={(e) => { e.target.style.display = 'none'; }}
              />
            </div>
          )}
        </div>
      </div>

      {/* ── Editorial Content ─────────────────────────────────────────────── */}
      <div style={S.section}>
        <div style={S.sectionHeader}>
          <span style={{ fontSize: 16 }}>✍️</span>
          <h3 style={S.sectionTitle}>Email Copy</h3>
        </div>
        <div style={S.sectionBody}>
          <div style={S.row}>
            <div style={S.field}>
              <label style={S.label}>Headline</label>
              <input type="text" value={config.headline} onChange={set('headline')} style={S.input} className="ar-input" />
            </div>
            <div style={S.field}>
              <label style={S.label}>Salutation</label>
              <input type="text" value={config.salutation} onChange={set('salutation')} style={S.input} className="ar-input" />
            </div>
          </div>

          <div style={{ marginBottom: 16 }}>
            <label style={S.label}>Body Paragraph</label>
            <textarea value={config.body_paragraph} onChange={set('body_paragraph')} style={{ ...S.textarea, marginTop: 6 }} className="ar-input" />
          </div>

          <div style={S.row}>
            <div style={S.field}>
              <label style={S.label}>Response Window Badge</label>
              <input type="text" value={config.response_window} onChange={set('response_window')} placeholder="within a few days" style={S.input} className="ar-input" />
            </div>
            <div style={S.field}>
              <label style={S.label}>Callout Message</label>
              <input type="text" value={config.callout_message} onChange={set('callout_message')} style={S.input} className="ar-input" />
            </div>
          </div>

          <div style={{ marginBottom: 16 }}>
            <label style={S.label}>Closing Paragraph</label>
            <textarea value={config.closing_paragraph} onChange={set('closing_paragraph')} style={{ ...S.textarea, marginTop: 6, minHeight: 56 }} className="ar-input" />
          </div>

          <div style={S.row}>
            <div style={S.field}>
              <label style={S.label}>Primary CTA Text</label>
              <input type="text" value={config.cta_primary_text} onChange={set('cta_primary_text')} style={S.input} className="ar-input" />
            </div>
            <div style={S.field}>
              <label style={S.label}>Primary CTA URL</label>
              <input type="url" value={config.cta_primary_url} onChange={set('cta_primary_url')} style={S.input} className="ar-input" />
            </div>
          </div>

          <div style={S.row}>
            <div style={S.field}>
              <label style={S.label}>Secondary CTA Text</label>
              <input type="text" value={config.cta_secondary_text} onChange={set('cta_secondary_text')} style={S.input} className="ar-input" />
            </div>
            <div style={S.field}>
              <label style={S.label}>Secondary CTA URL</label>
              <input type="url" value={config.cta_secondary_url} onChange={set('cta_secondary_url')} style={S.input} className="ar-input" />
            </div>
          </div>

          <div>
            <label style={S.label}>Footer Note</label>
            <input type="text" value={config.footer_note} onChange={set('footer_note')} style={{ ...S.input, marginTop: 6, width: '100%', boxSizing: 'border-box' }} className="ar-input" placeholder="Made with love & curiosity 🤍" />
          </div>
        </div>
      </div>

      {/* ── Save Button ───────────────────────────────────────────────────── */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 32 }}>
        <button onClick={saveConfig} disabled={saving} style={S.btnPrimary} className="ar-btn">
          {saving ? 'Saving…' : '💾 Save Configuration'}
        </button>
        <button onClick={() => loadConfig(false)} disabled={refreshingConfig} style={S.btnSecondary} className="ar-btn-sec">
          {refreshingConfig ? 'Refreshing…' : '↺ Reset to Saved'}
        </button>
      </div>

      {/* ── Live Preview ──────────────────────────────────────────────────── */}
      <div style={S.section}>
        <div style={S.sectionHeader}>
          <span style={{ fontSize: 16 }}>👁</span>
          <h3 style={S.sectionTitle}>Live Preview</h3>
          <div style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
            {['desktop', 'mobile'].map((m) => (
              <span key={m} style={S.chip(previewMode === m)} onClick={() => setPreviewMode(m)}>
                {m === 'desktop' ? '🖥 Desktop' : '📱 Mobile'}
              </span>
            ))}
          </div>
        </div>
        <div style={S.sectionBody}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 14 }}>
            <label style={{ ...S.label, whiteSpace: 'nowrap' }}>Recipient Email Sim</label>
            <input
              type="email"
              value={previewEmail}
              onChange={(e) => setPreviewEmail(e.target.value)}
              placeholder="visitor@example.com"
              style={{ ...S.input, flex: 1, maxWidth: 300 }}
              className="ar-input"
            />
            <button onClick={refreshPreview} style={S.btnSecondary} className="ar-btn-sec">
              {previewLoading ? 'Loading…' : '↺ Refresh'}
            </button>
          </div>
          <div style={{
            display: 'flex',
            justifyContent: 'center',
            background: '#1a1b1f',
            borderRadius: 10,
            padding: previewMode === 'desktop' ? '20px 0' : '20px',
            border: '1px solid rgba(255,255,255,0.06)',
          }}>
            {previewHtml ? (
              <iframe
                title="email-preview"
                srcDoc={previewHtml}
                style={{
                  width: previewMode === 'desktop' ? '600px' : '375px',
                  height: 700,
                  border: 'none',
                  borderRadius: 6,
                  display: 'block',
                  background: '#fff',
                  transition: 'width 0.3s',
                }}
              />
            ) : (
              <div style={{ color: 'rgba(255,255,255,0.3)', fontFamily: "'JetBrains Mono', monospace", fontSize: 12, padding: 40 }}>
                {previewLoading ? 'Rendering…' : 'Preview will load shortly…'}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* ── Test Dispatcher ───────────────────────────────────────────────── */}
      <div style={S.section}>
        <div style={S.sectionHeader}>
          <span style={{ fontSize: 16 }}>🚀</span>
          <h3 style={S.sectionTitle}>Test Dispatcher</h3>
        </div>
        <div style={{ ...S.sectionBody, display: 'flex', alignItems: 'flex-end', gap: 12, flexWrap: 'wrap' }}>
          <div style={{ ...S.field, minWidth: 280, marginBottom: 0 }}>
            <label style={S.label}>Send Test Auto-Reply To</label>
            <input
              type="email"
              value={testEmail}
              onChange={(e) => setTestEmail(e.target.value)}
              placeholder="me@example.com"
              style={S.input}
              className="ar-input"
              onKeyDown={(e) => e.key === 'Enter' && sendTest()}
            />
          </div>
          <button onClick={sendTest} disabled={testSending} style={{ ...S.btnPrimary, flexShrink: 0 }} className="ar-btn">
            {testSending ? '⏳ Sending…' : '📧 Send Test'}
          </button>
        </div>
      </div>

      {/* ── Reusable Signature & Templates ─────────────────────────────────── */}
      <div style={S.section}>
        <div style={S.sectionHeader}>
          <span style={{ fontSize: 16 }}>📬</span>
          <h3 style={S.sectionTitle}>Reusable Signature &amp; Templates (Mobile &amp; Desktop)</h3>
          <span style={{ marginLeft: 'auto', fontSize: 11, color: '#89ff00', fontFamily: "'JetBrains Mono', monospace", fontWeight: 600 }}>
            Atelier Brand Assets
          </span>
        </div>
        <div style={S.sectionBody}>
          <p style={{ margin: '0 0 16px', fontSize: 13, color: 'rgba(255,255,255,0.7)', lineHeight: 1.5 }}>
            Use your branded Atelier signature and full responsive email layout when replying manually from your phone, laptop, or webmail.
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 20, marginBottom: 20 }}>
            {/* Signature Preview Card */}
            <div style={{
              background: '#ffffff',
              borderRadius: 10,
              padding: 20,
              boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
              border: '1px solid #e5e5e5',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'space-between'
            }}>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                    <span style={{ fontSize: 11, fontWeight: 700, color: '#111111', fontFamily: "'JetBrains Mono', monospace", textTransform: 'uppercase' }}>
                      Atelier Email Signature
                    </span>
                    <span style={{ fontSize: 10, background: '#1A1AE8', color: '#FFF0E3', padding: '2px 8px', borderRadius: 20, fontWeight: 700, fontFamily: "'JetBrains Mono', monospace" }}>
                      FOOTER LOCKUP
                    </span>
                  </div>
                  <div dangerouslySetInnerHTML={{ __html: SIGNATURE_HTML.replace('https://faizal.uk/images/mail/fz_logo.png', FZ_BASE64) }} />
                </div>
                <div style={{ marginTop: 16, display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                  <button
                    onClick={copyRichSignature}
                    style={{
                      background: sigCopied ? '#89ff00' : '#111113',
                      color: sigCopied ? '#000000' : '#ffffff',
                      border: '1px solid #222225',
                      borderRadius: 6,
                      padding: '8px 14px',
                      fontSize: 12,
                      fontWeight: 700,
                      fontFamily: "'JetBrains Mono', monospace",
                      cursor: 'pointer',
                      transition: 'all 0.2s',
                    }}
                    className="ar-btn"
                  >
                    {sigCopied ? '✓ Copied to Clipboard!' : '📋 Copy Rich Signature'}
                  </button>
                  <button
                    onClick={copyLogoUrl}
                    style={{
                      ...S.btnSecondary,
                      padding: '8px 12px',
                      fontSize: 11,
                    }}
                    className="ar-btn-sec"
                    title="Copy direct PNG logo URL for Gmail's Insert Image button"
                  >
                    {logoCopied ? '✓ URL Copied!' : '🔗 Copy Logo URL'}
                  </button>
                </div>
              </div>

              {/* Blank Composer Template Card */}
              <div style={{
                background: 'rgba(137,255,0,0.03)',
                borderRadius: 10,
                padding: 20,
                border: '1px solid rgba(137,255,0,0.25)',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between'
              }}>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                    <span style={{ fontSize: 11, fontWeight: 700, color: '#89ff00', fontFamily: "'JetBrains Mono', monospace", textTransform: 'uppercase' }}>
                      Blank Composer Template
                    </span>
                    <span style={{ fontSize: 10, background: 'rgba(137,255,0,0.2)', color: '#89ff00', padding: '2px 8px', borderRadius: 20, fontWeight: 700, fontFamily: "'JetBrains Mono', monospace" }}>
                      BESPOKE EMAIL
                    </span>
                  </div>
                  <p style={{ fontSize: 12, color: 'rgba(255,255,255,0.7)', lineHeight: 1.5, margin: '0 0 12px' }}>
                    Clean branded template with rainbow bars, FZ header, editable body container, and clean personal signature. No canned text, no orange callouts, and no recipient disclaimers.
                  </p>
                </div>
                <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                  <button
                    onClick={copyBlankComposer}
                    style={{
                      ...S.btnPrimary,
                      padding: '8px 14px',
                      fontSize: 12,
                    }}
                    className="ar-btn"
                  >
                    {blankCopied ? '✓ Blank HTML Copied!' : '📋 Copy Blank Template HTML'}
                  </button>
                  <button
                    onClick={sendBlankStarter}
                    disabled={blankSending}
                    style={{
                      ...S.btnSecondary,
                      padding: '8px 14px',
                      fontSize: 12,
                    }}
                    className="ar-btn-sec"
                  >
                    {blankSending ? '⏳ Sending…' : '📥 Send Starter to me@faizal.uk'}
                  </button>
                </div>
              </div>

              {/* Auto-Responder Layout Card */}
              <div style={{
                background: 'rgba(255,255,255,0.03)',
                borderRadius: 10,
                padding: 20,
                border: '1px solid rgba(255,255,255,0.08)',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between'
              }}>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                      <span style={{ fontSize: 11, fontWeight: 700, color: 'rgba(255,255,255,0.8)', fontFamily: "'JetBrains Mono', monospace", textTransform: 'uppercase' }}>
                        Auto-Responder Full HTML
                      </span>
                        <span style={{ fontSize: 10, background: 'rgba(255,255,255,0.1)', color: 'rgba(255,255,255,0.8)', padding: '2px 8px', borderRadius: 20, fontWeight: 700, fontFamily: "'JetBrains Mono', monospace" }}>
                          LIVE RECEIPT
                        </span>
                      </div>
                      <p style={{ fontSize: 12, color: 'rgba(255,255,255,0.6)', lineHeight: 1.5, margin: '0 0 12px' }}>
                        The automated acknowledgment email layout including artwork hero banner, response window box, and configured copy.
                      </p>
                  </div>
                  <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                    <button
                      onClick={copyFullTemplate}
                      style={{
                        ...S.btnSecondary,
                        background: templateCopied ? 'rgba(137,255,0,0.2)' : undefined,
                        color: templateCopied ? '#89ff00' : undefined,
                      }}
                      className="ar-btn-sec"
                    >
                      {templateCopied ? '✓ Full HTML Copied!' : '📄 Copy Auto-Reply HTML'}
                    </button>
                  </div>
                </div>
              </div>

              {/* Setup Guide Tabs */}
              <div style={{
                background: 'rgba(0,0,0,0.25)',
                borderRadius: 8,
                padding: 16,
                border: '1px solid rgba(255,255,255,0.06)'
              }}>
                <div style={{ display: 'flex', gap: 8, marginBottom: 14, borderBottom: '1px solid rgba(255,255,255,0.08)', paddingBottom: 10, flexWrap: 'wrap' }}>
                  <span style={{ fontSize: 11, fontWeight: 700, color: 'rgba(255,255,255,0.5)', fontFamily: "'JetBrains Mono', monospace", textTransform: 'uppercase', marginRight: 4, alignSelf: 'center' }}>
                    Setup Guide:
                  </span>
                  {[
                    { id: 'iphone', label: '📱 iPhone (Apple Mail)' },
                    { id: 'android', label: '🤖 Android (Gmail / Spark)' },
                    { id: 'templates', label: '📨 Gmail Canned Templates' },
                  ].map((t) => (
                    <button
                      key={t.id}
                      onClick={() => setSigGuideTab(t.id)}
                      style={{
                        background: sigGuideTab === t.id ? 'rgba(137,255,0,0.15)' : 'rgba(255,255,255,0.04)',
                        color: sigGuideTab === t.id ? '#89ff00' : 'rgba(255,255,255,0.7)',
                        border: `1px solid ${sigGuideTab === t.id ? 'rgba(137,255,0,0.3)' : 'rgba(255,255,255,0.08)'}`,
                        borderRadius: 6,
                        padding: '5px 12px',
                        fontSize: 11,
                        fontFamily: "'JetBrains Mono', monospace",
                        cursor: 'pointer',
                      }}
                    >
                      {t.label}
                    </button>
                  ))}
                </div>

                {sigGuideTab === 'iphone' && (
                  <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.8)', lineHeight: 1.6 }}>
                    <ol style={{ margin: 0, paddingLeft: 20 }}>
                      <li><strong>For Everyday Signature:</strong> Click <strong>📋 Copy Rich Signature</strong> above on this device. Open <strong>Settings → Mail → Signature</strong> on your iPhone, tap and hold to paste.</li>
                      <li style={{ color: '#89ff00', marginTop: 4 }}>
                        <strong>⭐ Crucial iPhone Step:</strong> Immediately <strong>shake your iPhone</strong> until the &ldquo;Undo Change Attributes&rdquo; popup appears, then tap <strong>Undo</strong> (prevents iOS from stripping formatting).
                      </li>
                      <li style={{ marginTop: 6 }}>
                        <strong>For Full Graphical Emails on iPhone:</strong> Click <strong>📥 Send Starter to me@faizal.uk</strong> above. In Apple Mail on your iPhone, open that email and tap <strong>Reply</strong> or <strong>Forward</strong>. The entire graphics, rainbow bars, header, and footer stay intact — simply type your text in the body and send!
                      </li>
                    </ol>
                  </div>
                )}

                {sigGuideTab === 'android' && (
                  <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.8)', lineHeight: 1.6 }}>
                    <ol style={{ margin: 0, paddingLeft: 20 }}>
                      <li>
                        <strong>⭐ Instant Phone Method (Gmail Mobile App):</strong> Click <strong>📥 Send Starter to me@faizal.uk</strong> above. In your Gmail mobile app, open the message and tap the <strong>Star (⭐)</strong> icon. Whenever you want to reply or write a branded email, tap Reply or Forward on that email, edit your message, and hit send!
                      </li>
                      <li style={{ marginTop: 6 }}>
                        <strong>Gmail Web Signature:</strong> On laptop/desktop, open <strong>Gmail Settings ⚙️ → See all settings → General → Signature</strong>. Click <strong>📋 Copy Rich Signature</strong> and paste. If Gmail strips the logo, click Gmail&rsquo;s <strong>🖼️ Insert Image</strong> icon in the signature toolbar and paste the URL from <strong>🔗 Copy Logo URL</strong> above.
                      </li>
                      <li style={{ marginTop: 6 }}>
                        <strong>Third-Party Apps (Spark / Outlook / FairEmail):</strong> Open Settings → Accounts → Signature, click <strong>📋 Copy Rich Signature</strong>, and paste directly.
                      </li>
                    </ol>
                  </div>
                )}

                {sigGuideTab === 'templates' && (
                  <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.8)', lineHeight: 1.6 }}>
                    <ol style={{ margin: 0, paddingLeft: 20 }}>
                      <li>In Gmail Web, open <strong>Settings (⚙️) → See all settings → Advanced → Templates</strong> and select <strong>Enable</strong>.</li>
                      <li>Click <strong>Compose</strong> a new email. Click <strong> Copy Blank Template HTML</strong> above and paste (or click <strong>📥 Send Starter</strong> and forward/copy it).</li>
                      <li>Click the 3 dots <strong>More options (⋮)</strong> at the bottom right of the compose window → <strong>Templates → Save draft as template → Save as new template</strong> (name it: &ldquo;Mohammad Faizal Atelier Template&rdquo;).</li>
                      <li style={{ color: '#89ff00', marginTop: 4 }}>
                        <strong>⭐ 1-Click Reuse Forever:</strong> Whenever composing or replying in Gmail, click <strong>⋮ → Templates → Mohammad Faizal Atelier Template</strong>. The entire branded card loads with an empty message body ready for typing!
                      </li>
                    </ol>
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* ── Audit Log ─────────────────────────────────────────────────────── */}
          <div style={S.section}>
            <div style={S.sectionHeader}>
              <span style={{ fontSize: 16 }}>📋</span>
              <h3 style={S.sectionTitle}>Audit Log</h3>
              <div style={{ marginLeft: 'auto', display: 'flex', gap: 10, alignItems: 'center' }}>
                <button onClick={() => clearCooldown(null)} style={S.btnDanger}>
                  Clear All Cooldowns
                </button>
                <button onClick={loadLogs} style={S.btnSecondary} className="ar-btn-sec">
                  ↺ Refresh
                </button>
              </div>
            </div>
            <div style={{ padding: '0 4px', overflowX: 'auto' }}>
              {logsLoading ? (
                <div style={{ padding: 24, textAlign: 'center', color: 'rgba(255,255,255,0.3)', fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>
                  Loading logs…
                </div>
              ) : logs.length === 0 ? (
                <div style={{ padding: 24, textAlign: 'center', color: 'rgba(255,255,255,0.25)', fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>
                  No log entries yet
                </div>
              ) : (
                <table style={S.logTable}>
                  <thead>
                    <tr>
                      <th style={S.logTh}>#</th>
                      <th style={S.logTh}>Sender</th>
                      <th style={S.logTh}>Subject</th>
                      <th style={S.logTh}>Action</th>
                      <th style={S.logTh}>Time</th>
                      <th style={S.logTh}>Cooldown</th>
                      <th style={S.logTh}></th>
                    </tr>
                  </thead>
                  <tbody>
                    {logs.map((log) => (
                      <tr key={log.id} className="ar-log-row">
                        <td style={{ ...S.logTd, color: 'rgba(255,255,255,0.3)' }}>{log.id}</td>
                        <td style={S.logTd}>
                          <span style={{ color: '#89ff00' }}>{log.sender_email}</span>
                        </td>
                        <td style={{ ...S.logTd, maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {log.subject || '—'}
                        </td>
                        <td style={S.logTd}>
                          <span style={{
                            padding: '2px 8px',
                            borderRadius: 4,
                            fontSize: 10,
                            fontWeight: 700,
                            background: log.action === 'REPLIED' ? 'rgba(137,255,0,0.1)' : 'rgba(255,160,0,0.1)',
                            color: log.action === 'REPLIED' ? '#89ff00' : '#ffa040',
                            border: `1px solid ${log.action === 'REPLIED' ? 'rgba(137,255,0,0.2)' : 'rgba(255,160,0,0.2)'}`,
                          }}>
                            {log.action}
                          </span>
                        </td>
                        <td style={{ ...S.logTd, color: 'rgba(255,255,255,0.4)', fontSize: 10 }}>
                          {log.created_at ? new Date(log.created_at + 'Z').toLocaleString() : '—'}
                        </td>
                        <td style={{ ...S.logTd, color: 'rgba(255,255,255,0.4)', fontSize: 10 }}>
                          {log.last_replied_at ? `${log.reply_count}x` : '—'}
                        </td>
                        <td style={S.logTd}>
                          {log.last_replied_at && (
                            <button
                              onClick={() => clearCooldown(log.sender_email)}
                              style={S.btnDanger}
                            >
                              Clear
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </div>
        );
}
