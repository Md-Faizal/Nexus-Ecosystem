import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { Zap, Send, Eye, ShieldAlert, CheckCircle2, AlertTriangle, Clock, Mail, Server } from 'lucide-react';

export default function ReportsTab({
  reportConfig,
  onUpdateConfig,
  onOpenPreview,
  onDispatchReport,
  sendingReport,
  errors = {}
}) {
  const [recipient, setRecipient] = useState(reportConfig?.recipient || 'me@faizal.uk, fazztronic@gmail.com');
  const [dispatchTime, setDispatchTime] = useState(reportConfig?.dispatchTime || '08:00 UTC');
  const [isSaving, setIsSaving] = useState(false);

  const handleSave = async (e) => {
    e.preventDefault();
    setIsSaving(true);
    try {
      await onUpdateConfig({ recipient, dispatchTime });
    } finally {
      setIsSaving(false);
    }
  };

  const totalErrors = errors?.total_error_count || 0;
  const sshFailures = errors?.ssh_auth_failures || 0;

  return (
    <div className="space-y-8" style={{ display: 'flex', flexDirection: 'column', gap: '32px' }}>
      {/* Overview Banner */}
      <div
        className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 rounded-3xl backdrop-blur-xl"
        style={{
          padding: '28px',
          backgroundColor: 'rgba(18, 19, 22, 0.85)',
          border: '1px solid rgba(255, 255, 255, 0.09)',
          boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.45)',
          boxSizing: 'border-box'
        }}
      >
        <div>
          <div className="flex items-center gap-2 mb-1.5">
            <span className="w-2 h-2 rounded-full bg-[#89ff00] animate-pulse" />
            <span className="text-xs font-mono font-semibold text-[#89ff00] tracking-widest uppercase">
              Automated Infrastructure Digest
            </span>
          </div>
          <h2 className="text-xl sm:text-2xl font-bold text-white tracking-tight">
            24h Infrastructure Telemetry &amp; Daily Email
          </h2>
          <p className="text-xs sm:text-sm text-neutral-400 mt-1 max-w-2xl leading-relaxed">
            Aggregates 24-hour hardware averages, CPU spike distributions, container health, and security logs into an interactive production digest.
          </p>
        </div>

        <div className="flex items-center gap-3 shrink-0">
          <button
            onClick={onOpenPreview}
            className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-mono font-semibold text-white transition-all cursor-pointer"
          >
            <Eye className="w-4 h-4 text-sky-400" />
            <span>Open Preview</span>
          </button>
          <button
            onClick={onDispatchReport}
            disabled={sendingReport}
            className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-[#89ff00] hover:bg-[#9eff1a] text-black font-semibold text-xs font-mono shadow-[0_0_20px_rgba(137,255,0,0.25)] transition-all cursor-pointer disabled:opacity-50"
          >
            <Send className={`w-3.5 h-3.5 ${sendingReport ? 'animate-bounce' : ''}`} />
            <span>{sendingReport ? 'Dispatching...' : 'Dispatch Now'}</span>
          </button>
        </div>
      </div>

      {/* Grid: Settings & Audit Summary */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6" style={{ gap: '24px' }}>
        {/* Recipient Configuration Card */}
        <div
          className="rounded-3xl backdrop-blur-xl flex flex-col justify-between"
          style={{
            padding: '28px',
            backgroundColor: 'rgba(18, 19, 22, 0.85)',
            border: '1px solid rgba(255, 255, 255, 0.09)',
            boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.45)',
            boxSizing: 'border-box'
          }}
        >
          <div>
            <div className="flex items-center gap-2.5 mb-4">
              <div className="p-2 rounded-xl bg-white/5 border border-white/5 text-neutral-300">
                <Mail className="w-4 h-4 text-[#89ff00]" />
              </div>
              <h3 className="text-base font-semibold text-white">
                Dispatch Schedule & Recipient
              </h3>
            </div>

            <form onSubmit={handleSave} className="space-y-4">
              <div>
                <label className="block text-xs font-mono text-neutral-400 uppercase tracking-wider mb-1.5">
                  Report Recipients
                </label>
                <input
                  type="text"
                  value={recipient}
                  onChange={(e) => setRecipient(e.target.value)}
                  placeholder="email@domain.com, alerts@domain.com"
                  className="w-full px-3.5 py-2.5 rounded-xl bg-black/40 border border-white/10 text-white text-xs font-mono placeholder:text-neutral-600 focus:outline-none focus:border-[#89ff00]/50"
                />
              </div>

              <div>
                <label className="block text-xs font-mono text-neutral-400 uppercase tracking-wider mb-1.5">
                  Scheduled Trigger Time
                </label>
                <div className="flex items-center gap-2">
                  <div className="relative flex-1">
                    <Clock className="w-4 h-4 text-neutral-500 absolute left-3 top-1/2 -translate-y-1/2" />
                    <input
                      type="text"
                      value={dispatchTime}
                      onChange={(e) => setDispatchTime(e.target.value)}
                      placeholder="08:00 UTC"
                      className="w-full pl-9 pr-3.5 py-2.5 rounded-xl bg-black/40 border border-white/10 text-white text-xs font-mono placeholder:text-neutral-600 focus:outline-none focus:border-[#89ff00]/50"
                    />
                  </div>
                </div>
              </div>

              <button
                type="submit"
                disabled={isSaving}
                className="w-full mt-2 py-2.5 rounded-xl bg-white/10 hover:bg-white/15 border border-white/10 text-xs font-mono font-semibold text-white transition-all cursor-pointer disabled:opacity-50"
              >
                {isSaving ? 'Updating...' : 'Save Configuration'}
              </button>
            </form>
          </div>
        </div>

        {/* 24h Error Inspection Card */}
        <div
          className="lg:col-span-2 rounded-3xl backdrop-blur-xl"
          style={{
            padding: '28px',
            backgroundColor: 'rgba(18, 19, 22, 0.85)',
            border: '1px solid rgba(255, 255, 255, 0.09)',
            boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.45)',
            boxSizing: 'border-box'
          }}
        >
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2.5">
              <div className="p-2 rounded-xl bg-white/5 border border-white/5 text-neutral-300">
                <ShieldAlert className="w-4 h-4 text-[#ff3366]" />
              </div>
              <div>
                <h3 className="text-base font-semibold text-white">
                  24h Anomaly & Security Inspection
                </h3>
                <p className="text-xs text-neutral-400 mt-0.5">
                  Automated scan results across systemd, Nginx, and SSH daemon.
                </p>
              </div>
            </div>

            <span
              className={`px-3 py-1 rounded-full text-xs font-mono font-bold border ${
                totalErrors === 0
                  ? 'text-[#89ff00] bg-[#89ff00]/10 border-[#89ff00]/25'
                  : 'text-[#ff3366] bg-[#ff3366]/10 border-[#ff3366]/25'
              }`}
            >
              {totalErrors === 0 ? '✓ ZERO ERRORS' : `⚠ ${totalErrors} ANOMALIES`}
            </span>
          </div>

          <div className="space-y-3 mt-4 text-xs font-mono">
            <div className="p-3.5 rounded-2xl bg-black/40 border border-white/5 flex items-center justify-between">
              <span className="text-neutral-300">SSH Authentication Failures (24h)</span>
              <strong className={sshFailures > 20 ? 'text-[#ff3366]' : 'text-neutral-300'}>
                {sshFailures} attempts blocked
              </strong>
            </div>

            <div className="p-3.5 rounded-2xl bg-black/40 border border-white/5 flex items-center justify-between">
              <span className="text-neutral-300">Nginx 5xx / Bad Gateway Logs</span>
              <strong className="text-neutral-300">
                {errors?.nginx?.length || 0} entries recorded
              </strong>
            </div>

            <div className="p-3.5 rounded-2xl bg-black/40 border border-white/5 flex items-center justify-between">
              <span className="text-neutral-300">Systemd Critical Units (journalctl -p 3)</span>
              <strong className="text-neutral-300">
                {errors?.systemd?.length || 0} alerts
              </strong>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
