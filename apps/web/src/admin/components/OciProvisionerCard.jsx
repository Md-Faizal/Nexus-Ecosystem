import React, { useState, useEffect } from 'react';
import { Radio, RefreshCw, Send, CheckCircle2, AlertTriangle, Clock, Layers, ChevronDown, ChevronUp, History } from 'lucide-react';

export default function OciProvisionerCard() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [sendingMail, setSendingMail] = useState(false);
  const [mailFeedback, setMailFeedback] = useState(null);
  const [showHistory, setShowHistory] = useState(false);

  const fetchStatus = async () => {
    try {
      // Priority 1: Direct public assets state file
      let res = await fetch('/assets/oci_state.json?t=' + Date.now());
      if (res.ok) {
        const json = await res.json();
        setData(json);
        setLoading(false);
        return;
      }
      // Priority 2: API route fallback
      res = await fetch('/api/oci-allocator/status?t=' + Date.now());
      if (res.ok) {
        const json = await res.json();
        setData(json);
      }
    } catch {
      // Ignored if file doesn't exist yet
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStatus();
    const interval = setInterval(fetchStatus, 30000);
    return () => clearInterval(interval);
  }, []);

  const handleSendMail = async () => {
    setSendingMail(true);
    setMailFeedback(null);
    try {
      const pin = localStorage.getItem('faizal_admin_pin') || '2004';
      const res = await fetch('/api/oci-allocator/send-mail', {
        method: 'POST',
        headers: {
          'Authorization': pin,
          'Content-Type': 'application/json'
        }
      });
      const resData = await res.json().catch(() => ({}));
      if (res.ok && resData.success) {
        setMailFeedback({ type: 'success', text: 'Digest sent to me@faizal.uk & mohammad0faizal@gmail.com!' });
      } else {
        setMailFeedback({ type: 'error', text: resData.error || 'Failed to dispatch email.' });
      }
    } catch (err) {
      setMailFeedback({ type: 'error', text: err.message || 'Dispatch error' });
    } finally {
      setSendingMail(false);
      setTimeout(() => setMailFeedback(null), 5000);
    }
  };

  if (loading && !data) return null;
  if (!data || data.status === 'NOT_CONFIGURED') return null;

  const isAllocated = data.status === 'ALLOCATED';
  const isAuthError = data.status === 'AUTH_ERROR';
  const isInProgress = data.status === 'JOB_IN_PROGRESS';

  const badgeColor = isAllocated
    ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30'
    : isAuthError
    ? 'bg-red-500/20 text-red-400 border-red-500/30'
    : isInProgress
    ? 'bg-blue-500/20 text-blue-400 border-blue-500/30'
    : 'bg-amber-500/20 text-amber-400 border-amber-500/30';

  const statusLabel = isAllocated
    ? 'ALLOCATED ✅'
    : isAuthError
    ? 'AUTH REQUIRED ⚠️'
    : isInProgress
    ? 'APPLY IN PROGRESS'
    : 'SEARCHING CAPACITY';

  const recentAttempts = data.recent_attempts || [];

  return (
    <div
      className="rounded-2xl backdrop-blur-xl border transition-all"
      style={{
        padding: '20px 24px',
        backgroundColor: 'rgba(15, 17, 21, 0.85)',
        borderColor: isAllocated ? 'rgba(16, 185, 129, 0.3)' : 'rgba(255, 255, 255, 0.08)',
        boxShadow: '0 4px 20px 0 rgba(0, 0, 0, 0.35)',
      }}
    >
      {/* Header Row */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-white/5">
        <div className="flex items-center gap-2.5">
          <div className="p-1.5 rounded-lg bg-white/5 text-amber-400">
            <Radio className={`w-4 h-4 ${!isAllocated ? 'animate-pulse text-amber-400' : 'text-emerald-400'}`} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-sm font-bold text-white tracking-tight">
                OCI Resource Manager Auto-Provisioner
              </span>
              <span className="text-[11px] font-mono text-neutral-400">
                [{data.region || 'ap-hyderabad-1'}]
              </span>
            </div>
            <p className="text-[11px] font-mono text-neutral-500 mt-0.5 truncate max-w-md">
              Stack: {data.stack_ocid ? `${data.stack_ocid.slice(0, 18)}...${data.stack_ocid.slice(-8)}` : 'Active'}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 shrink-0 flex-wrap">
          <span className={`px-2.5 py-1 rounded-full text-[11px] font-mono font-bold border ${badgeColor}`}>
            {statusLabel}
          </span>
          <button
            onClick={handleSendMail}
            disabled={sendingMail}
            title="Send status email now"
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 border border-amber-500/30 text-xs font-mono font-semibold transition-all cursor-pointer disabled:opacity-50"
          >
            <Send className={`w-3.5 h-3.5 ${sendingMail ? 'animate-spin' : ''}`} />
            <span>{sendingMail ? 'Sending...' : 'Send Digest'}</span>
          </button>
          <button
            onClick={fetchStatus}
            title="Refresh status"
            className="p-1.5 rounded-lg bg-white/5 hover:bg-white/10 text-neutral-400 hover:text-white transition-colors cursor-pointer"
          >
            <RefreshCw className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Feedback banner if mail sent */}
      {mailFeedback && (
        <div className={`mt-3 px-3 py-2 rounded-lg text-xs font-mono border ${
          mailFeedback.type === 'success' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-red-500/10 text-red-400 border-red-500/20'
        }`}>
          {mailFeedback.text}
        </div>
      )}

      {/* Metric Counters */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-3 pt-1">
        <div>
          <span className="text-[10px] font-mono uppercase text-neutral-500">Today Attempts</span>
          <div className="text-sm font-mono font-bold text-amber-400 mt-0.5">
            {data.attempts_today ?? 0}
          </div>
        </div>
        <div>
          <span className="text-[10px] font-mono uppercase text-neutral-500">Total Retries</span>
          <div className="text-sm font-mono font-bold text-white mt-0.5">
            {data.total_attempts ?? 0}
          </div>
        </div>
        <div>
          <span className="text-[10px] font-mono uppercase text-neutral-500">Last Attempt</span>
          <div className="text-[11px] font-mono text-neutral-300 mt-0.5" title={data.last_attempt_at || 'Waiting'}>
            {data.last_attempt_at || 'Pending'}
          </div>
        </div>
        <div>
          <span className="text-[10px] font-mono uppercase text-neutral-500">Recipients</span>
          <div className="text-[11px] font-mono text-neutral-400 mt-0.5 truncate" title="me@faizal.uk, mohammad0faizal@gmail.com">
            2 active emails
          </div>
        </div>
      </div>

      {/* Full Untruncated Status Box */}
      <div className="mt-3.5 p-3 rounded-xl bg-white/[0.03] border border-white/5">
        <div className="text-[10px] font-mono uppercase text-neutral-500 mb-1 flex items-center justify-between">
          <span>Latest OCI Engine Message</span>
          {data.last_job_ocid && (
            <span className="text-[10px] font-mono text-neutral-400">
              Job: {data.last_job_ocid.slice(-10)}
            </span>
          )}
        </div>
        <div className="text-xs font-mono text-neutral-300 leading-relaxed whitespace-pre-wrap break-words">
          {data.last_message || 'Waiting for first check cycle...'}
        </div>
      </div>

      {/* Recent Attempts Accordion */}
      {recentAttempts.length > 0 && (
        <div className="mt-3">
          <button
            onClick={() => setShowHistory(!showHistory)}
            className="flex items-center gap-1.5 text-xs font-mono text-neutral-400 hover:text-white transition-colors cursor-pointer"
          >
            <History className="w-3.5 h-3.5 text-neutral-500" />
            <span>Recent Attempt History ({recentAttempts.length})</span>
            {showHistory ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
          </button>

          {showHistory && (
            <div className="mt-2 space-y-1.5 max-h-48 overflow-y-auto pr-1">
              {recentAttempts.map((att, idx) => (
                <div
                  key={idx}
                  className="flex flex-col sm:flex-row sm:items-center justify-between gap-1 px-3 py-2 rounded-lg bg-black/40 border border-white/5 text-[11px] font-mono"
                >
                  <div className="flex items-center gap-2">
                    <span className={`px-1.5 py-0.5 rounded text-[9px] font-bold ${
                      att.status === 'TRIGGERED'
                        ? 'bg-blue-500/20 text-blue-400'
                        : att.status === 'FAILED'
                        ? 'bg-amber-500/20 text-amber-400'
                        : 'bg-red-500/20 text-red-400'
                    }`}>
                      {att.status}
                    </span>
                    <span className="text-neutral-300 break-all">{att.message}</span>
                  </div>
                  <div className="flex items-center gap-2 text-neutral-500 shrink-0 text-[10px]">
                    {att.job_id && att.job_id !== 'N/A' && <span>Job: {att.job_id}</span>}
                    <span>{att.time ? att.time.split(' ')[1] : ''}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
