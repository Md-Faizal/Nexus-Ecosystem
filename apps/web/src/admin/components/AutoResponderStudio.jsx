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
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [previewHtml, setPreviewHtml] = useState('');
  const [previewMode, setPreviewMode] = useState('desktop');
  const [previewEmail, setPreviewEmail] = useState('visitor@example.com');
  const [testEmail, setTestEmail] = useState('');
  const [testSending, setTestSending] = useState(false);
  const [logs, setLogs] = useState([]);
  const [logsLoading, setLogsLoading] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const previewDebounce = useRef(null);

  // ── Load config ─────────────────────────────────────────────────────────────
  const loadConfig = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetchWithAuth('/api/auto-responder/config');
      const data = await res.json();
      if (data.success && data.config) {
        setConfig((prev) => ({ ...prev, ...data.config }));
      }
    } catch (e) {
      showToast?.('Failed to load auto-responder config', 'error');
    } finally {
      setLoading(false);
    }
  }, [fetchWithAuth, showToast]);

  // ── Load logs ────────────────────────────────────────────────────────────────
  const loadLogs = useCallback(async () => {
    setLogsLoading(true);
    try {
      const res = await fetchWithAuth('/api/auto-responder/logs');
      const data = await res.json();
      if (data.success) setLogs(data.logs || []);
    } catch (e) {
      showToast?.('Failed to load logs', 'error');
    } finally {
      setLogsLoading(false);
    }
  }, [fetchWithAuth, showToast]);

  useEffect(() => {
    loadConfig();
    loadLogs();
  }, [loadConfig, loadLogs]);

  // ── Live preview (debounced) ─────────────────────────────────────────────────
  const refreshPreview = useCallback(async () => {
    setPreviewLoading(true);
    try {
      const res = await fetchWithAuth(
        `/api/auto-responder/preview-html?email=${encodeURIComponent(previewEmail)}`
      );
      const html = await res.text();
      setPreviewHtml(html);
    } catch (e) {
      // silent
    } finally {
      setPreviewLoading(false);
    }
  }, [fetchWithAuth, previewEmail]);

  useEffect(() => {
    if (previewDebounce.current) clearTimeout(previewDebounce.current);
    previewDebounce.current = setTimeout(refreshPreview, 800);
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

  if (loading) {
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
        <button onClick={loadConfig} style={S.btnSecondary} className="ar-btn-sec">
          ↺ Reset to Saved
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
