import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { Shield, ShieldAlert, Ban, Unlock, UserX, AlertOctagon } from 'lucide-react';
import ConfirmModal from '../components/ConfirmModal';

export default function SecurityTab({
  accessLogs = [],
  bannedIps = [],
  onBanIp,
  onUnbanIp
}) {
  const [newBan, setNewBan] = useState({ ip: '', reason: '' });
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [selectedUnbanIp, setSelectedUnbanIp] = useState(null);

  const handleBanSubmit = async (e) => {
    e.preventDefault();
    if (!newBan.ip.trim()) return;
    setIsSubmitting(true);
    try {
      await onBanIp(newBan);
      setNewBan({ ip: '', reason: '' });
    } finally {
      setIsSubmitting(false);
    }
  };

  const confirmUnban = async () => {
    if (!selectedUnbanIp) return;
    await onUnbanIp(selectedUnbanIp);
    setSelectedUnbanIp(null);
  };

  return (
    <div className="space-y-8" style={{ display: 'flex', flexDirection: 'column', gap: '32px' }}>
      {/* Header Banner */}
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
              Firewall & Ingress Security
            </span>
          </div>
          <h2 className="text-xl sm:text-2xl font-bold text-white tracking-tight">
            Access Auditing & IP Blacklist
          </h2>
          <p className="text-xs sm:text-sm text-neutral-400 mt-1">
            Enforce IP-level firewall blocking and inspect incoming authenticated ingress logs.
          </p>
        </div>

        <span className="px-3 py-1.5 rounded-xl bg-white/5 border border-white/10 text-xs font-mono text-neutral-300">
          Blacklisted IPs: <strong className="text-[#ff3366]">{bannedIps.length}</strong>
        </span>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8" style={{ gap: '28px' }}>
        {/* Add Ban Form */}
        <div
          className="rounded-3xl backdrop-blur-xl h-fit"
          style={{
            padding: '28px',
            backgroundColor: 'rgba(18, 19, 22, 0.85)',
            border: '1px solid rgba(255, 255, 255, 0.09)',
            boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.45)',
            boxSizing: 'border-box'
          }}
        >
          <div className="flex items-center gap-2.5 mb-4">
            <div className="p-2 rounded-xl bg-[#ff3366]/10 border border-[#ff3366]/20 text-[#ff3366]">
              <Ban className="w-4 h-4" />
            </div>
            <h3 className="text-base font-semibold text-white">Blacklist IP Address</h3>
          </div>

          <form onSubmit={handleBanSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-mono text-neutral-400 uppercase tracking-wider mb-1.5">
                Target IP Address
              </label>
              <input
                type="text"
                required
                value={newBan.ip}
                onChange={(e) => setNewBan({ ...newBan, ip: e.target.value })}
                placeholder="192.168.1.1 or 203.0.113.4"
                className="w-full px-3.5 py-2.5 rounded-xl bg-black/40 border border-white/10 text-white text-xs font-mono placeholder:text-neutral-600 focus:outline-none focus:border-[#ff3366]/50"
              />
            </div>

            <div>
              <label className="block text-xs font-mono text-neutral-400 uppercase tracking-wider mb-1.5">
                Reason / Note (Optional)
              </label>
              <input
                type="text"
                value={newBan.reason}
                onChange={(e) => setNewBan({ ...newBan, reason: e.target.value })}
                placeholder="e.g. Malicious scanning, brute-force"
                className="w-full px-3.5 py-2.5 rounded-xl bg-black/40 border border-white/10 text-white text-xs font-mono placeholder:text-neutral-600 focus:outline-none focus:border-[#ff3366]/50"
              />
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full py-2.5 rounded-xl bg-[#ff3366] hover:bg-[#ff4d7d] text-white font-semibold text-xs font-mono shadow-[0_0_20px_rgba(255,51,102,0.25)] transition-all cursor-pointer disabled:opacity-50"
            >
              {isSubmitting ? 'Blacklisting...' : 'Block IP Address'}
            </button>
          </form>
        </div>

        {/* Banned IPs List */}
        <div className="lg:col-span-2 space-y-4">
          <div className="p-6 rounded-3xl bg-neutral-900/60 border border-white/10 backdrop-blur-xl">
            <h3 className="text-base font-semibold text-white mb-4">
              Active Blacklisted Nodes ({bannedIps.length})
            </h3>

            {bannedIps.length === 0 ? (
              <div className="p-8 text-center text-xs font-mono text-neutral-500">
                No IP addresses are currently blacklisted.
              </div>
            ) : (
              <div className="divide-y divide-white/5">
                {bannedIps.map((b, i) => (
                  <div key={i} className="py-3 flex items-center justify-between gap-3 text-xs font-mono">
                    <div>
                      <span className="font-semibold text-white">{b.ip || b}</span>
                      {b.reason && (
                        <p className="text-neutral-400 text-[11px] mt-0.5">{b.reason}</p>
                      )}
                    </div>
                    <button
                      onClick={() => setSelectedUnbanIp(b.ip || b)}
                      className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-[#89ff00] hover:text-white transition-all cursor-pointer"
                    >
                      <Unlock className="w-3.5 h-3.5" />
                      <span>Unban</span>
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Access Logs Audit */}
          <div className="p-6 rounded-3xl bg-neutral-900/60 border border-white/10 backdrop-blur-xl">
            <h3 className="text-base font-semibold text-white mb-4">
              Recent Admin Access Audit ({accessLogs.length})
            </h3>

            {accessLogs.length === 0 ? (
              <div className="p-8 text-center text-xs font-mono text-neutral-500">
                No administrative authentication logs found.
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs font-mono">
                  <thead>
                    <tr className="border-b border-white/10 text-neutral-400">
                      <th className="pb-3 pr-4 font-semibold uppercase">IP</th>
                      <th className="pb-3 px-4 font-semibold uppercase">Action / Route</th>
                      <th className="pb-3 pl-4 font-semibold uppercase text-right">Time</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-white/5">
                    {accessLogs.slice(0, 15).map((log, i) => (
                      <tr key={i} className="hover:bg-white/[0.02]">
                        <td className="py-3 pr-4 text-white font-medium">{log.ip || '—'}</td>
                        <td className="py-3 px-4 text-neutral-300">{log.action || log.route || 'Access'}</td>
                        <td className="py-3 pl-4 text-neutral-500 text-right">{log.timestamp || 'Recently'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      </div>

      <ConfirmModal
        isOpen={Boolean(selectedUnbanIp)}
        title="Unban IP Address?"
        message={`Are you sure you want to lift the blacklist block for ${selectedUnbanIp}?`}
        confirmLabel="Unban IP"
        isDestructive={false}
        onConfirm={confirmUnban}
        onCancel={() => setSelectedUnbanIp(null)}
      />
    </div>
  );
}
