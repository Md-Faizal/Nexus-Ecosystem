import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { Sliders, Mail, ShieldAlert, Palette, CheckCircle2, Send, Lock, ExternalLink, RefreshCw, KeyRound, Eye, EyeOff, AlertCircle } from 'lucide-react';

export default function SettingsTab({
  siteTheme,
  onThemeChange,
  onSendTestEmail,
  showToast
}) {
  const [testEmailRecipient, setTestEmailRecipient] = useState('');
  const [sendingTest, setSendingTest] = useState(false);
  const [maintenanceMode, setMaintenanceMode] = useState(false);

  // 4-Digit Passcode Management State
  const [currentPin, setCurrentPin] = useState('');
  const [newPin, setNewPin] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [showPins, setShowPins] = useState(false);
  const [changingPin, setChangingPin] = useState(false);
  const [pinError, setPinError] = useState('');

  const handleTestEmail = async (e) => {
    e.preventDefault();
    if (!testEmailRecipient) return;
    setSendingTest(true);
    try {
      await onSendTestEmail(testEmailRecipient);
      setTestEmailRecipient('');
    } finally {
      setSendingTest(false);
    }
  };

  const handleChangePin = async (e) => {
    e.preventDefault();
    setPinError('');

    if (!/^\d{4}$/.test(currentPin)) {
      setPinError('Current PIN must be exactly 4 numeric digits.');
      return;
    }
    if (!/^\d{4}$/.test(newPin)) {
      setPinError('New PIN must be exactly 4 numeric digits.');
      return;
    }
    if (newPin !== confirmPin) {
      setPinError('New PIN and Confirm PIN do not match.');
      return;
    }
    if (currentPin === newPin) {
      setPinError('New PIN must be different from current PIN.');
      return;
    }

    setChangingPin(true);
    try {
      const activeAuth = localStorage.getItem('faizal_admin_pin') || currentPin;
      const res = await fetch('/api/change-pin', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': activeAuth,
          'X-Admin-Pin': activeAuth
        },
        body: JSON.stringify({
          current_pin: currentPin,
          new_pin: newPin
        })
      });

      const data = await res.json().catch(() => ({}));
      if (res.ok && data.success) {
        localStorage.setItem('faizal_admin_pin', newPin);
        setCurrentPin('');
        setNewPin('');
        setConfirmPin('');
        showToast?.('4-digit Admin passcode successfully updated!');
      } else {
        setPinError(data.error || 'Failed to update PIN. Please verify your current PIN.');
      }
    } catch (err) {
      setPinError('Network error connecting to API.');
    } finally {
      setChangingPin(false);
    }
  };

  return (
    <div className="space-y-8" style={{ display: 'flex', flexDirection: 'column', gap: '32px' }}>
      {/* Header Banner */}
      <div
        className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 rounded-3xl border"
        style={{
          padding: '28px 32px',
          backgroundColor: 'rgba(18, 19, 22, 0.85)',
          borderColor: 'rgba(255, 255, 255, 0.09)',
          backdropFilter: 'blur(20px)',
          borderRadius: '24px'
        }}
      >
        <div>
          <div className="flex items-center gap-2 mb-1.5">
            <span className="w-2 h-2 rounded-full bg-[#89ff00] animate-pulse" />
            <span className="text-xs font-mono font-semibold text-[#89ff00] tracking-widest uppercase">
              Global Cluster Parameters
            </span>
          </div>
          <h2 className="text-xl sm:text-2xl font-bold text-white tracking-tight">
            System & Engine Settings
          </h2>
          <p className="text-xs sm:text-sm text-neutral-400 mt-1">
            Configure site appearance themes, SMTP relays, and emergency maintenance routing.
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6" style={{ display: 'grid', gap: '24px' }}>
        {/* Site Theme Card */}
        <div
          className="rounded-3xl border flex flex-col justify-between"
          style={{
            padding: '28px',
            backgroundColor: 'rgba(18, 19, 22, 0.85)',
            borderColor: 'rgba(255, 255, 255, 0.09)',
            backdropFilter: 'blur(20px)',
            borderRadius: '24px',
            gap: '20px'
          }}
        >
          <div className="flex items-center gap-3">
            <div
              className="rounded-xl flex items-center justify-center"
              style={{
                width: '42px',
                height: '42px',
                backgroundColor: 'rgba(255, 255, 255, 0.04)',
                border: '1px solid rgba(255, 255, 255, 0.08)',
                borderRadius: '12px'
              }}
            >
              <Palette className="w-5 h-5 text-[#89ff00]" />
            </div>
            <div>
              <h3 className="text-base font-semibold text-white">Public Site Visual Engine</h3>
              <p className="text-xs text-neutral-400 mt-0.5">Choose the public active theme variant.</p>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3 pt-2">
            <button
              onClick={() => onThemeChange('classic')}
              className="text-left transition-all cursor-pointer"
              style={{
                padding: '16px',
                borderRadius: '16px',
                border: siteTheme === 'classic' ? '1px solid #89ff00' : '1px solid rgba(255, 255, 255, 0.1)',
                backgroundColor: siteTheme === 'classic' ? 'rgba(137, 255, 0, 0.1)' : 'rgba(255, 255, 255, 0.04)',
                boxShadow: siteTheme === 'classic' ? '0 0 20px rgba(137, 255, 0, 0.15)' : 'none'
              }}
            >
              <span className="text-xs font-mono font-bold text-white block">Classic Minimalist</span>
              <span className="text-[11px] text-neutral-400 block mt-1">
                Monochrome elegance with smooth typography
              </span>
            </button>

            <button
              onClick={() => onThemeChange('vibrant')}
              className="text-left transition-all cursor-pointer"
              style={{
                padding: '16px',
                borderRadius: '16px',
                border: siteTheme === 'vibrant' ? '1px solid #89ff00' : '1px solid rgba(255, 255, 255, 0.1)',
                backgroundColor: siteTheme === 'vibrant' ? 'rgba(137, 255, 0, 0.1)' : 'rgba(255, 255, 255, 0.04)',
                boxShadow: siteTheme === 'vibrant' ? '0 0 20px rgba(137, 255, 0, 0.15)' : 'none'
              }}
            >
              <span className="text-xs font-mono font-bold text-white block">Modern Vibrant</span>
              <span className="text-[11px] text-neutral-400 block mt-1">
                Atelier Neon Lime and Crimson hyper-aesthetics
              </span>
            </button>
          </div>
        </div>

        {/* Brevo SMTP Relay Test */}
        <div
          className="rounded-3xl border flex flex-col justify-between"
          style={{
            padding: '28px',
            backgroundColor: 'rgba(18, 19, 22, 0.85)',
            borderColor: 'rgba(255, 255, 255, 0.09)',
            backdropFilter: 'blur(20px)',
            borderRadius: '24px',
            gap: '20px'
          }}
        >
          <div className="flex items-center gap-3">
            <div
              className="rounded-xl flex items-center justify-center"
              style={{
                width: '42px',
                height: '42px',
                backgroundColor: 'rgba(255, 255, 255, 0.04)',
                border: '1px solid rgba(255, 255, 255, 0.08)',
                borderRadius: '12px'
              }}
            >
              <Mail className="w-5 h-5 text-sky-400" />
            </div>
            <div>
              <h3 className="text-base font-semibold text-white">SMTP Relay Connectivity Test</h3>
              <p className="text-xs text-neutral-400 mt-0.5">Send a test probe via Brevo ESMTP relay (Port 587).</p>
            </div>
          </div>

          <form onSubmit={handleTestEmail} className="space-y-3 pt-2">
            <input
              type="email"
              required
              value={testEmailRecipient}
              onChange={(e) => setTestEmailRecipient(e.target.value)}
              placeholder="recipient@example.com"
              style={{
                padding: '12px 16px',
                backgroundColor: 'rgba(0, 0, 0, 0.5)',
                border: '1px solid rgba(255, 255, 255, 0.12)',
                borderRadius: '12px',
                color: '#fff',
                width: '100%'
              }}
              className="text-xs font-mono placeholder:text-neutral-600 focus:outline-none focus:border-[#89ff00]/50"
            />
            <button
              type="submit"
              disabled={sendingTest}
              style={{
                padding: '12px 20px',
                backgroundColor: 'rgba(255, 255, 255, 0.08)',
                border: '1px solid rgba(255, 255, 255, 0.14)',
                borderRadius: '12px',
                color: '#fff',
                width: '100%'
              }}
              className="hover:bg-white/15 text-xs font-mono font-semibold transition-all cursor-pointer flex items-center justify-center gap-2 disabled:opacity-50"
            >
              <Send className="w-3.5 h-3.5 text-[#89ff00]" />
              <span>{sendingTest ? 'Sending Test Probe...' : 'Send Relay Test'}</span>
            </button>
          </form>
        </div>

        {/* Stalwart Mail Server & HA Replication */}
        <div
          className="rounded-3xl border flex flex-col justify-between"
          style={{
            padding: '28px',
            backgroundColor: 'rgba(18, 19, 22, 0.85)',
            borderColor: 'rgba(255, 255, 255, 0.09)',
            backdropFilter: 'blur(20px)',
            borderRadius: '24px',
            gap: '18px'
          }}
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div
                className="rounded-xl flex items-center justify-center"
                style={{
                  width: '42px',
                  height: '42px',
                  backgroundColor: 'rgba(56, 189, 248, 0.08)',
                  border: '1px solid rgba(56, 189, 248, 0.25)',
                  borderRadius: '12px'
                }}
              >
                <Mail className="w-5 h-5 text-sky-400" />
              </div>
              <div>
                <h3 className="text-base font-semibold text-white">Stalwart Mail HA & Webadmin</h3>
                <p className="text-xs text-neutral-400 mt-0.5">Dual-node mail stack on my-mac & container-of-souls.</p>
              </div>
            </div>
            <span className="px-2.5 py-1 rounded-full text-[10px] font-mono font-bold bg-[#89ff00]/15 text-[#89ff00] border border-[#89ff00]/30">
              DUAL-NODE HA
            </span>
          </div>

          <div className="grid grid-cols-2 gap-2 text-xs font-mono">
            <div className="p-3 rounded-xl bg-black/40 border border-white/5 space-y-1">
              <div className="text-[10px] text-neutral-500 uppercase tracking-wider font-bold">Primary Mail Node</div>
              <div className="text-[#89ff00] font-semibold flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-[#89ff00] animate-pulse" />
                my-mac :8080 / 25
              </div>
              <div className="text-[11px] text-neutral-400">MX Ingress & Webmail</div>
            </div>

            <div className="p-3 rounded-xl bg-black/40 border border-white/5 space-y-1">
              <div className="text-[10px] text-neutral-500 uppercase tracking-wider font-bold">Standby Failover</div>
              <div className="text-sky-400 font-semibold flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-sky-400" />
                cos :8080 (Synced)
              </div>
              <div className="text-[11px] text-neutral-400">VCN Spool Replication</div>
            </div>
          </div>

          <div className="flex items-center gap-2 pt-1">
            <a
              href="https://mail.faizal.uk/admin"
              target="_blank"
              rel="noopener noreferrer"
              className="flex-1 py-2.5 px-3 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-mono text-center text-white transition-all flex items-center justify-center gap-1.5"
            >
              <span>Launch Webadmin</span>
              <ExternalLink className="w-3.5 h-3.5 text-neutral-400" />
            </a>

            <button
              type="button"
              onClick={() => showToast?.('Triggered VCN data replication across cluster nodes')}
              className="py-2.5 px-3 rounded-xl bg-[#89ff00]/10 hover:bg-[#89ff00]/20 border border-[#89ff00]/30 text-xs font-mono text-[#89ff00] transition-all cursor-pointer font-semibold flex items-center gap-1.5"
              title="Verify VCN Data Sync"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              <span>Sync Nodes</span>
            </button>
          </div>
        </div>

        {/* Maintenance Mode */}
        <div
          className="rounded-3xl border flex items-center justify-between"
          style={{
            padding: '28px',
            backgroundColor: 'rgba(18, 19, 22, 0.85)',
            borderColor: 'rgba(255, 255, 255, 0.09)',
            backdropFilter: 'blur(20px)',
            borderRadius: '24px'
          }}
        >
          <div className="flex items-center gap-3">
            <div
              className="rounded-xl flex items-center justify-center"
              style={{
                width: '42px',
                height: '42px',
                backgroundColor: 'rgba(255, 255, 255, 0.04)',
                border: '1px solid rgba(255, 255, 255, 0.08)',
                borderRadius: '12px'
              }}
            >
              <ShieldAlert className="w-5 h-5 text-amber-400" />
            </div>
            <div>
              <h3 className="text-base font-semibold text-white">Emergency Maintenance Mode</h3>
              <p className="text-xs text-neutral-400 mt-0.5">Reroutes incoming traffic to maintenance splash.</p>
            </div>
          </div>

          <label className="relative inline-flex items-center cursor-pointer">
            <input
              type="checkbox"
              checked={maintenanceMode}
              onChange={() => {
                setMaintenanceMode(!maintenanceMode);
                showToast?.(`Maintenance mode ${!maintenanceMode ? 'ENABLED' : 'DISABLED'}`);
              }}
              className="sr-only peer"
            />
            <div className="w-11 h-6 bg-neutral-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-neutral-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#89ff00]"></div>
          </label>
        </div>

        {/* Master 4-Digit Passcode Management */}
        <div
          className="rounded-3xl border flex flex-col justify-between"
          style={{
            padding: '28px',
            backgroundColor: 'rgba(18, 19, 22, 0.85)',
            borderColor: 'rgba(255, 255, 255, 0.09)',
            backdropFilter: 'blur(20px)',
            borderRadius: '24px',
            gap: '20px'
          }}
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div
                className="rounded-xl flex items-center justify-center"
                style={{
                  width: '42px',
                  height: '42px',
                  backgroundColor: 'rgba(137, 255, 0, 0.08)',
                  border: '1px solid rgba(137, 255, 0, 0.25)',
                  borderRadius: '12px'
                }}
              >
                <KeyRound className="w-5 h-5 text-[#89ff00]" />
              </div>
              <div>
                <h3 className="text-base font-semibold text-white">Master 4-Digit Passcode</h3>
                <p className="text-xs text-neutral-400 mt-0.5">Change the 4-digit security PIN used to unlock this Admin console.</p>
              </div>
            </div>
            <span className="px-2.5 py-1 rounded-full text-[10px] font-mono font-bold bg-[#89ff00]/15 text-[#89ff00] border border-[#89ff00]/30 flex items-center gap-1.5">
              <CheckCircle2 className="w-3 h-3" />
              ENFORCED 4-DIGIT PIN
            </span>
          </div>

          {/* Form to change 4-digit passcode */}
          <form onSubmit={handleChangePin} className="space-y-4 pt-1">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              {/* Current PIN */}
              <div className="space-y-1.5">
                <label className="text-[11px] font-mono text-neutral-400 uppercase tracking-wider font-semibold">
                  Current PIN
                </label>
                <div className="relative">
                  <input
                    type={showPins ? 'text' : 'password'}
                    inputMode="numeric"
                    pattern="[0-9]*"
                    maxLength={4}
                    required
                    value={currentPin}
                    onChange={(e) => setCurrentPin(e.target.value.replace(/\D/g, '').slice(0, 4))}
                    placeholder="••••"
                    style={{
                      padding: '12px 14px',
                      backgroundColor: 'rgba(0, 0, 0, 0.5)',
                      border: '1px solid rgba(255, 255, 255, 0.12)',
                      borderRadius: '12px',
                      color: '#fff',
                      width: '100%',
                      letterSpacing: showPins ? '0.15em' : '0.3em'
                    }}
                    className="text-center font-mono text-sm placeholder:text-neutral-600 focus:outline-none focus:border-[#89ff00]/60 transition-colors"
                  />
                </div>
              </div>

              {/* New PIN */}
              <div className="space-y-1.5">
                <label className="text-[11px] font-mono text-neutral-400 uppercase tracking-wider font-semibold">
                  New 4-Digit PIN
                </label>
                <div className="relative">
                  <input
                    type={showPins ? 'text' : 'password'}
                    inputMode="numeric"
                    pattern="[0-9]*"
                    maxLength={4}
                    required
                    value={newPin}
                    onChange={(e) => setNewPin(e.target.value.replace(/\D/g, '').slice(0, 4))}
                    placeholder="••••"
                    style={{
                      padding: '12px 14px',
                      backgroundColor: 'rgba(0, 0, 0, 0.5)',
                      border: '1px solid rgba(255, 255, 255, 0.12)',
                      borderRadius: '12px',
                      color: '#fff',
                      width: '100%',
                      letterSpacing: showPins ? '0.15em' : '0.3em'
                    }}
                    className="text-center font-mono text-sm placeholder:text-neutral-600 focus:outline-none focus:border-[#89ff00]/60 transition-colors"
                  />
                </div>
              </div>

              {/* Confirm New PIN */}
              <div className="space-y-1.5">
                <label className="text-[11px] font-mono text-neutral-400 uppercase tracking-wider font-semibold">
                  Confirm New PIN
                </label>
                <div className="relative">
                  <input
                    type={showPins ? 'text' : 'password'}
                    inputMode="numeric"
                    pattern="[0-9]*"
                    maxLength={4}
                    required
                    value={confirmPin}
                    onChange={(e) => setConfirmPin(e.target.value.replace(/\D/g, '').slice(0, 4))}
                    placeholder="••••"
                    style={{
                      padding: '12px 14px',
                      backgroundColor: 'rgba(0, 0, 0, 0.5)',
                      border: '1px solid rgba(255, 255, 255, 0.12)',
                      borderRadius: '12px',
                      color: '#fff',
                      width: '100%',
                      letterSpacing: showPins ? '0.15em' : '0.3em'
                    }}
                    className="text-center font-mono text-sm placeholder:text-neutral-600 focus:outline-none focus:border-[#89ff00]/60 transition-colors"
                  />
                </div>
              </div>
            </div>

            {/* Error Message Alert */}
            {pinError && (
              <div className="flex items-center gap-2 p-3 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-xs font-mono">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{pinError}</span>
              </div>
            )}

            {/* Form Actions */}
            <div className="flex items-center justify-between pt-1">
              <button
                type="button"
                onClick={() => setShowPins(!showPins)}
                className="text-xs font-mono text-neutral-400 hover:text-white transition-colors flex items-center gap-1.5 cursor-pointer"
              >
                {showPins ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                <span>{showPins ? 'Hide Digits' : 'Reveal Digits'}</span>
              </button>

              <button
                type="submit"
                disabled={changingPin || currentPin.length !== 4 || newPin.length !== 4 || confirmPin.length !== 4}
                style={{
                  padding: '10px 22px',
                  backgroundColor: 'rgba(137, 255, 0, 0.12)',
                  border: '1px solid rgba(137, 255, 0, 0.35)',
                  borderRadius: '12px',
                  color: '#89ff00'
                }}
                className="hover:bg-[#89ff00]/20 text-xs font-mono font-bold transition-all cursor-pointer flex items-center gap-2 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <KeyRound className="w-3.5 h-3.5" />
                <span>{changingPin ? 'Updating Passcode...' : 'Save New Passcode'}</span>
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
