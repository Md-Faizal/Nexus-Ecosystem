import React, { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Shield,
  ShieldCheck,
  ShieldAlert,
  Ban,
  Unlock,
  Key,
  Smartphone,
  Laptop,
  Terminal,
  Globe,
  Trash2,
  Plus,
  RefreshCw,
  Power,
  CheckCircle2,
  XCircle,
  ExternalLink,
  Layers,
  Lock,
  Radio
} from 'lucide-react';
import ConfirmModal from '../components/ConfirmModal';

export default function SecurityTab({
  accessLogs = [],
  bannedIps = [],
  onBanIp,
  onUnbanIp,
  fetchWithAuth,
  showToast
}) {
  const [activeSubTab, setActiveSubTab] = useState('ecosystem'); // 'ecosystem' | 'sessions' | 'pats' | 'firewall'

  // Ecosystem Apps state
  const [apps, setApps] = useState([]);
  const [loadingApps, setLoadingApps] = useState(false);
  const [showAddAppModal, setShowAddAppModal] = useState(false);
  const [newApp, setNewApp] = useState({
    name: '',
    slug: '',
    description: '',
    allowed_redirect_uris: '',
    allowed_scopes: 'profile:read'
  });

  // Sessions state
  const [sessions, setSessions] = useState([]);
  const [loadingSessions, setLoadingSessions] = useState(false);

  // PATs state
  const [pats, setPats] = useState([]);
  const [loadingPats, setLoadingPats] = useState(false);
  const [showCreatePatModal, setShowCreatePatModal] = useState(false);
  const [newPatName, setNewPatName] = useState('');
  const [createdPatSecret, setCreatedPatSecret] = useState(null);

  // Firewall state
  const [newBan, setNewBan] = useState({ ip: '', reason: '' });
  const [isSubmittingBan, setIsSubmittingBan] = useState(false);
  const [selectedUnbanIp, setSelectedUnbanIp] = useState(null);

  // ── Fetch Ecosystem Apps ───────────────────────────────────────────────────

  const loadApps = useCallback(async () => {
    if (!fetchWithAuth) return;
    setLoadingApps(true);
    try {
      const res = await fetchWithAuth('/api/auth/apps');
      const data = await res.json();
      if (data.success) {
        setApps(data.apps || []);
      }
    } catch (_) {
    } finally {
      setLoadingApps(false);
    }
  }, [fetchWithAuth]);

  // ── Fetch Active Sessions ──────────────────────────────────────────────────

  const loadSessions = useCallback(async () => {
    if (!fetchWithAuth) return;
    setLoadingSessions(true);
    try {
      const res = await fetchWithAuth('/api/auth/sessions');
      const data = await res.json();
      if (data.success) {
        setSessions(data.sessions || []);
      }
    } catch (_) {
    } finally {
      setLoadingSessions(false);
    }
  }, [fetchWithAuth]);

  // ── Fetch PATs ─────────────────────────────────────────────────────────────

  const loadPats = useCallback(async () => {
    if (!fetchWithAuth) return;
    setLoadingPats(true);
    try {
      const res = await fetchWithAuth('/api/auth/tokens');
      const data = await res.json();
      if (data.success) {
        setPats(data.tokens || []);
      }
    } catch (_) {
    } finally {
      setLoadingPats(false);
    }
  }, [fetchWithAuth]);

  useEffect(() => {
    loadApps();
    loadSessions();
    loadPats();
  }, [loadApps, loadSessions, loadPats]);

  // ── App Handlers ───────────────────────────────────────────────────────────

  const handleToggleApp = async (appId, currentStatus) => {
    try {
      const res = await fetchWithAuth(`/api/auth/apps/${appId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ is_enabled: !currentStatus })
      });
      const data = await res.json();
      if (data.success) {
        showToast && showToast(`Application access ${!currentStatus ? 'ENABLED' : 'DISABLED'}`);
        loadApps();
      }
    } catch (e) {
      showToast && showToast('Failed to toggle application status', 'error');
    }
  };

  const handleCreateApp = async (e) => {
    e.preventDefault();
    if (!newApp.name.trim()) return;

    try {
      const uris = newApp.allowed_redirect_uris
        .split('\n')
        .map((s) => s.trim())
        .filter(Boolean);
      const scopes = newApp.allowed_scopes
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean);

      const res = await fetchWithAuth('/api/auth/apps', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: newApp.name.trim(),
          slug: newApp.slug.trim() || undefined,
          description: newApp.description.trim() || undefined,
          allowed_redirect_uris: uris,
          allowed_scopes: scopes,
          is_first_party: true
        })
      });

      const data = await res.json();
      if (data.success) {
        showToast && showToast(`Application '${newApp.name}' registered`);
        setShowAddAppModal(false);
        setNewApp({
          name: '',
          slug: '',
          description: '',
          allowed_redirect_uris: '',
          allowed_scopes: 'profile:read'
        });
        loadApps();
      } else {
        showToast && showToast(data.error || 'Failed to register app', 'error');
      }
    } catch (e) {
      showToast && showToast(e.message, 'error');
    }
  };

  const handleDeleteApp = async (appId, appName) => {
    if (!confirm(`Permanently revoke and delete '${appName}' from the Nexus ecosystem?`)) return;
    try {
      const res = await fetchWithAuth(`/api/auth/apps/${appId}`, { method: 'DELETE' });
      const data = await res.json();
      if (data.success) {
        showToast && showToast(`Application '${appName}' deleted`);
        loadApps();
      }
    } catch (e) {
      showToast && showToast('Failed to delete application', 'error');
    }
  };

  // ── Session Handlers ───────────────────────────────────────────────────────

  const handleRevokeSession = async (deviceId, deviceName) => {
    try {
      const res = await fetchWithAuth(`/api/auth/sessions/${deviceId}`, { method: 'DELETE' });
      const data = await res.json();
      if (data.success) {
        showToast && showToast(`Session terminated for '${deviceName}'`);
        loadSessions();
      }
    } catch (e) {
      showToast && showToast('Failed to terminate session', 'error');
    }
  };

  const handleRevokeAllOtherSessions = async () => {
    if (!confirm('Sign out of all other devices across the entire Nexus Fleet?')) return;
    try {
      const res = await fetchWithAuth('/api/auth/sessions', { method: 'DELETE' });
      const data = await res.json();
      if (data.success) {
        showToast && showToast('All other sessions terminated successfully');
        loadSessions();
      }
    } catch (e) {
      showToast && showToast('Failed to terminate sessions', 'error');
    }
  };

  // ── PAT Handlers ───────────────────────────────────────────────────────────

  const handleCreatePat = async (e) => {
    e.preventDefault();
    if (!newPatName.trim()) return;

    try {
      const res = await fetchWithAuth('/api/auth/tokens', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: newPatName.trim(), scopes: ['*'] })
      });
      const data = await res.json();
      if (data.success) {
        setCreatedPatSecret(data.token);
        setNewPatName('');
        loadPats();
      }
    } catch (e) {
      showToast && showToast('Failed to create token', 'error');
    }
  };

  const handleRevokePat = async (patId) => {
    try {
      const res = await fetchWithAuth(`/api/auth/tokens/${patId}`, { method: 'DELETE' });
      const data = await res.json();
      if (data.success) {
        showToast && showToast('Personal Access Token revoked');
        loadPats();
      }
    } catch (e) {
      showToast && showToast('Failed to revoke token', 'error');
    }
  };

  // ── Firewall Handlers ──────────────────────────────────────────────────────

  const handleBanSubmit = async (e) => {
    e.preventDefault();
    if (!newBan.ip.trim()) return;
    setIsSubmittingBan(true);
    try {
      await onBanIp(newBan);
      setNewBan({ ip: '', reason: '' });
    } finally {
      setIsSubmittingBan(false);
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
              Nexus Identity & Access Governance
            </span>
          </div>
          <h2 className="text-xl sm:text-2xl font-bold text-white tracking-tight">
            Security, Sessions & Ecosystem Apps
          </h2>
          <p className="text-xs sm:text-sm text-neutral-400 mt-1">
            Govern where your Nexus account can be used, inspect active signed-in devices, and enforce ingress firewall rules.
          </p>
        </div>

        {/* Sub-Nav Pills */}
        <div className="flex flex-wrap gap-2">
          <button
            onClick={() => setActiveSubTab('ecosystem')}
            className={`px-4 py-2 rounded-xl text-xs font-semibold tracking-wider uppercase transition-all flex items-center gap-2 cursor-pointer ${activeSubTab === 'ecosystem'
                ? 'bg-[#89ff00] text-black shadow-[0_0_15px_rgba(137,255,0,0.3)]'
                : 'bg-white/5 text-neutral-400 hover:text-white hover:bg-white/10 border border-white/5'
              }`}
          >
            <Layers className="w-3.5 h-3.5" />
            Connected Apps ({apps.length})
          </button>
          <button
            onClick={() => setActiveSubTab('sessions')}
            className={`px-4 py-2 rounded-xl text-xs font-semibold tracking-wider uppercase transition-all flex items-center gap-2 cursor-pointer ${activeSubTab === 'sessions'
                ? 'bg-[#00e5ff] text-black shadow-[0_0_15px_rgba(0,229,255,0.3)]'
                : 'bg-white/5 text-neutral-400 hover:text-white hover:bg-white/10 border border-white/5'
              }`}
          >
            <Laptop className="w-3.5 h-3.5" />
            Devices ({sessions.length})
          </button>
          <button
            onClick={() => setActiveSubTab('pats')}
            className={`px-4 py-2 rounded-xl text-xs font-semibold tracking-wider uppercase transition-all flex items-center gap-2 cursor-pointer ${activeSubTab === 'pats'
                ? 'bg-purple-500 text-white shadow-[0_0_15px_rgba(168,85,247,0.3)]'
                : 'bg-white/5 text-neutral-400 hover:text-white hover:bg-white/10 border border-white/5'
              }`}
          >
            <Key className="w-3.5 h-3.5" />
            Tokens ({pats.length})
          </button>
          <button
            onClick={() => setActiveSubTab('firewall')}
            className={`px-4 py-2 rounded-xl text-xs font-semibold tracking-wider uppercase transition-all flex items-center gap-2 cursor-pointer ${activeSubTab === 'firewall'
                ? 'bg-[#ff3366] text-white shadow-[0_0_15px_rgba(255,51,102,0.3)]'
                : 'bg-white/5 text-neutral-400 hover:text-white hover:bg-white/10 border border-white/5'
              }`}
          >
            <Shield className="w-3.5 h-3.5" />
            Firewall ({bannedIps.length})
          </button>
        </div>
      </div>

      {/* ─── SUB-TAB 1: ECOSYSTEM APPLICATIONS (Where Nexus Account Can Be Used) ─── */}
      {activeSubTab === 'ecosystem' && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-base font-semibold text-white">Registered Ecosystem Applications</h3>
              <p className="text-xs text-neutral-400 mt-0.5">
                Authorized clients permitted to request OAuth tokens and delegate permissions for your Nexus ID.
              </p>
            </div>
            <button
              onClick={() => setShowAddAppModal(true)}
              className="flex items-center gap-2 px-4 py-2 rounded-xl bg-[#89ff00] text-black font-semibold text-xs tracking-wider uppercase hover:bg-[#7ce600] transition-all cursor-pointer shadow-[0_0_15px_rgba(137,255,0,0.25)]"
            >
              <Plus className="w-4 h-4" />
              Register New App
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {apps.map((app) => (
              <div
                key={app.id}
                className="p-5 rounded-2xl bg-neutral-900/60 border border-white/10 backdrop-blur-xl flex flex-col justify-between transition-all hover:border-white/20"
              >
                <div>
                  <div className="flex items-start justify-between gap-3 mb-3">
                    <div>
                      <h4 className="text-sm font-bold text-white flex items-center gap-2">
                        {app.name}
                        {app.is_first_party && (
                          <span className="px-1.5 py-0.5 rounded text-[9px] font-mono bg-blue-500/20 text-blue-400 border border-blue-500/30">
                            1ST PARTY
                          </span>
                        )}
                      </h4>
                      <span className="text-[10px] font-mono text-neutral-500">ID: {app.slug}</span>
                    </div>

                    <button
                      onClick={() => handleToggleApp(app.id, app.is_enabled)}
                      className={`px-2.5 py-1 rounded-full text-[10px] font-mono font-bold uppercase transition-all flex items-center gap-1.5 cursor-pointer ${app.is_enabled
                          ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 hover:bg-emerald-500/30'
                          : 'bg-red-500/20 text-red-400 border border-red-500/30 hover:bg-red-500/30'
                        }`}
                    >
                      <span className={`w-1.5 h-1.5 rounded-full ${app.is_enabled ? 'bg-emerald-400' : 'bg-red-400'}`} />
                      {app.is_enabled ? 'Active' : 'Disabled'}
                    </button>
                  </div>

                  <p className="text-xs text-neutral-400 leading-relaxed mb-4">
                    {app.description || 'No description provided.'}
                  </p>

                  <div className="space-y-2 mb-4">
                    <span className="block text-[10px] font-mono uppercase tracking-wider text-neutral-500">
                      Permitted Scopes
                    </span>
                    <div className="flex flex-wrap gap-1.5">
                      {app.allowed_scopes.map((scope, idx) => (
                        <span
                          key={idx}
                          className="px-2 py-0.5 rounded-md bg-white/5 border border-white/10 text-[10px] font-mono text-neutral-300"
                        >
                          {scope}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="pt-4 border-t border-white/5 flex items-center justify-between text-xs">
                  <span className="text-[10px] font-mono text-neutral-500 truncate max-w-[180px]">
                    {app.allowed_redirect_uris[0] || 'No redirect URI'}
                  </span>
                  <button
                    onClick={() => handleDeleteApp(app.id, app.name)}
                    className="p-1.5 rounded-lg text-neutral-500 hover:text-red-400 hover:bg-red-500/10 transition-colors cursor-pointer"
                    title="Delete App"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ─── SUB-TAB 2: WHERE YOU'RE SIGNED IN (Sessions & Devices) ───────────── */}
      {activeSubTab === 'sessions' && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-base font-semibold text-white">Your Devices & Active Sessions</h3>
              <p className="text-xs text-neutral-400 mt-0.5">
                Every machine, smartphone, or agent terminal currently authenticated with your Nexus ID.
              </p>
            </div>
            <button
              onClick={handleRevokeAllOtherSessions}
              className="flex items-center gap-2 px-4 py-2 rounded-xl bg-red-500/15 border border-red-500/30 text-red-400 font-semibold text-xs tracking-wider uppercase hover:bg-red-500/25 transition-all cursor-pointer"
            >
              <Power className="w-4 h-4" />
              Sign Out All Other Devices
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {sessions.map((sess) => {
              const isDesktop = sess.device_type === 'desktop';
              const isMobile = sess.device_type === 'mobile';
              const isCli = sess.client_app.includes('cli') || sess.device_type === 'cli';

              return (
                <div
                  key={sess.id}
                  className={`p-5 rounded-2xl bg-neutral-900/60 border backdrop-blur-xl flex flex-col justify-between transition-all ${sess.is_current ? 'border-[#89ff00]/40 shadow-[0_0_20px_rgba(137,255,0,0.15)]' : 'border-white/10'
                    }`}
                >
                  <div>
                    <div className="flex items-start justify-between gap-3 mb-3">
                      <div className="flex items-center gap-3">
                        <div className="p-2.5 rounded-xl bg-white/5 border border-white/10 text-white">
                          {isCli ? <Terminal className="w-5 h-5 text-[#89ff00]" /> : isMobile ? <Smartphone className="w-5 h-5 text-[#00e5ff]" /> : <Laptop className="w-5 h-5 text-white" />}
                        </div>
                        <div>
                          <h4 className="text-sm font-bold text-white flex items-center gap-2">
                            {sess.device_name}
                            {sess.is_current && (
                              <span className="px-1.5 py-0.5 rounded text-[9px] font-mono bg-[#89ff00]/20 text-[#89ff00] border border-[#89ff00]/30">
                                THIS DEVICE
                              </span>
                            )}
                          </h4>
                          <span className="text-[10px] font-mono text-neutral-400 uppercase">
                            App: {sess.client_app}
                          </span>
                        </div>
                      </div>
                    </div>

                    <div className="space-y-1.5 py-2 text-xs font-mono text-neutral-400">
                      <div className="flex justify-between">
                        <span>IP Address:</span>
                        <span className="text-white">{sess.ip_address || '127.0.0.1'}</span>
                      </div>
                      <div className="flex justify-between">
                        <span>Last Active:</span>
                        <span className="text-neutral-300">{sess.last_seen || 'Just now'}</span>
                      </div>
                    </div>
                  </div>

                  <div className="pt-4 mt-2 border-t border-white/5 flex justify-end">
                    {!sess.is_current && (
                      <button
                        onClick={() => handleRevokeSession(sess.id, sess.device_name)}
                        className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-red-500/10 hover:bg-red-500/20 text-red-400 text-xs font-semibold transition-colors cursor-pointer"
                      >
                        <Power className="w-3.5 h-3.5" />
                        Terminate Session
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* ─── SUB-TAB 3: MACHINE PERSONAL ACCESS TOKENS (PATs) ─────────────────── */}
      {activeSubTab === 'pats' && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-base font-semibold text-white">Personal Access Tokens (PATs)</h3>
              <p className="text-xs text-neutral-400 mt-0.5">
                Static secret tokens for authenticating background scripts, Minecraft server hooks, and CI/CD pipelines.
              </p>
            </div>
            <button
              onClick={() => setShowCreatePatModal(true)}
              className="flex items-center gap-2 px-4 py-2 rounded-xl bg-purple-600 text-white font-semibold text-xs tracking-wider uppercase hover:bg-purple-500 transition-all cursor-pointer shadow-[0_0_15px_rgba(168,85,247,0.3)]"
            >
              <Plus className="w-4 h-4" />
              Generate Token
            </button>
          </div>

          {createdPatSecret && (
            <div className="p-5 rounded-2xl bg-purple-950/40 border border-purple-500/40 backdrop-blur-xl">
              <div className="flex items-center gap-2 text-purple-300 font-bold text-xs uppercase mb-2">
                <CheckCircle2 className="w-4 h-4 text-purple-400" />
                Token Generated — Copy it now!
              </div>
              <p className="text-xs text-neutral-300 mb-3">
                This token will never be displayed again. Store it securely in your environment variables.
              </p>
              <div className="flex items-center gap-3">
                <input
                  type="text"
                  readOnly
                  value={createdPatSecret}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-black/60 border border-purple-500/30 text-purple-200 font-mono text-xs select-all"
                />
                <button
                  onClick={() => {
                    navigator.clipboard.writeText(createdPatSecret);
                    showToast && showToast('Token copied to clipboard!');
                  }}
                  className="px-4 py-2.5 rounded-xl bg-purple-600 hover:bg-purple-500 text-white text-xs font-semibold whitespace-nowrap cursor-pointer"
                >
                  Copy
                </button>
                <button
                  onClick={() => setCreatedPatSecret(null)}
                  className="px-3 py-2.5 rounded-xl bg-white/10 text-neutral-400 hover:text-white text-xs cursor-pointer"
                >
                  Dismiss
                </button>
              </div>
            </div>
          )}

          <div className="rounded-2xl bg-neutral-900/60 border border-white/10 backdrop-blur-xl divide-y divide-white/5">
            {pats.length === 0 ? (
              <div className="p-8 text-center text-xs font-mono text-neutral-500">
                No Personal Access Tokens currently generated.
              </div>
            ) : (
              pats.map((p) => (
                <div key={p.id} className="p-4 flex items-center justify-between gap-4">
                  <div>
                    <h4 className="text-sm font-bold text-white">{p.name}</h4>
                    <div className="flex items-center gap-3 text-[11px] font-mono text-neutral-400 mt-1">
                      <span className="text-purple-400">{p.prefix}</span>
                      <span>Created: {p.created_at?.split('T')[0] || p.created_at}</span>
                      <span>Last used: {p.last_used_at || 'Never'}</span>
                    </div>
                  </div>
                  <button
                    onClick={() => handleRevokePat(p.id)}
                    className="p-2 rounded-xl text-neutral-500 hover:text-red-400 hover:bg-red-500/10 transition-colors cursor-pointer"
                    title="Revoke Token"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              ))
            )}
          </div>
        </div>
      )}

      {/* ─── SUB-TAB 4: NETWORK FIREWALL & IP BLACKLIST ───────────────────────── */}
      {activeSubTab === 'firewall' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
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
                <Ban className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-base font-semibold text-white">Ingress Blacklist</h3>
                <p className="text-xs text-neutral-400">Instantly drop incoming connections from malicious IPs.</p>
              </div>
            </div>

            <form onSubmit={handleBanSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-mono text-neutral-400 uppercase tracking-wider mb-1.5">
                  IP Address <span className="text-[#ff3366]">*</span>
                </label>
                <input
                  type="text"
                  required
                  value={newBan.ip}
                  onChange={(e) => setNewBan({ ...newBan, ip: e.target.value })}
                  placeholder="e.g. 192.168.1.100"
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
                disabled={isSubmittingBan}
                className="w-full py-2.5 rounded-xl bg-[#ff3366] hover:bg-[#ff4d7d] text-white font-semibold text-xs font-mono shadow-[0_0_20px_rgba(255,51,102,0.25)] transition-all cursor-pointer disabled:opacity-50"
              >
                {isSubmittingBan ? 'Blacklisting...' : 'Block IP Address'}
              </button>
            </form>
          </div>

          {/* Banned IPs List & Access Logs */}
          <div className="lg:col-span-2 space-y-6">
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
                        {b.reason && <p className="text-neutral-400 text-[11px] mt-0.5">{b.reason}</p>}
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
                Recent Ingress Audits ({accessLogs.length})
              </h3>
              {accessLogs.length === 0 ? (
                <div className="p-8 text-center text-xs font-mono text-neutral-500">
                  No ingress audit records found.
                </div>
              ) : (
                <div className="divide-y divide-white/5 max-h-[350px] overflow-y-auto">
                  {accessLogs.map((log, i) => (
                    <div key={i} className="py-2.5 flex items-center justify-between text-xs font-mono">
                      <div className="flex items-center gap-2">
                        <span className={`w-2 h-2 rounded-full ${log.status === 'SUCCESS' ? 'bg-[#89ff00]' : 'bg-[#ff3366]'}`} />
                        <span className="text-white">{log.ip}</span>
                      </div>
                      <span className="text-neutral-500">{log.timestamp}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* ─── MODAL: REGISTER NEW APPLICATION ─────────────────────────────────── */}
      {showAddAppModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md">
          <div className="w-full max-w-lg bg-[#0d0e12] border border-white/10 rounded-2xl p-6 text-white shadow-2xl">
            <h3 className="text-base font-bold text-white mb-1">Register New Ecosystem Application</h3>
            <p className="text-xs text-neutral-400 mb-5">
              Allow a new business project, web service, or game gateway to authenticate via Nexus ID.
            </p>

            <form onSubmit={handleCreateApp} className="space-y-4">
              <div>
                <label className="block text-xs font-mono text-neutral-300 uppercase mb-1">Application Name *</label>
                <input
                  type="text"
                  required
                  value={newApp.name}
                  onChange={(e) => setNewApp({ ...newApp, name: e.target.value })}
                  placeholder="e.g. Minecraft Portal"
                  className="w-full px-3.5 py-2 rounded-xl bg-neutral-900 border border-white/10 text-white text-xs font-mono focus:outline-none focus:border-[#89ff00]"
                />
              </div>

              <div>
                <label className="block text-xs font-mono text-neutral-300 uppercase mb-1">Identifier Slug (Optional)</label>
                <input
                  type="text"
                  value={newApp.slug}
                  onChange={(e) => setNewApp({ ...newApp, slug: e.target.value })}
                  placeholder="e.g. minecraft-portal"
                  className="w-full px-3.5 py-2 rounded-xl bg-neutral-900 border border-white/10 text-white text-xs font-mono focus:outline-none focus:border-[#89ff00]"
                />
              </div>

              <div>
                <label className="block text-xs font-mono text-neutral-300 uppercase mb-1">Description</label>
                <input
                  type="text"
                  value={newApp.description}
                  onChange={(e) => setNewApp({ ...newApp, description: e.target.value })}
                  placeholder="Purpose of this app"
                  className="w-full px-3.5 py-2 rounded-xl bg-neutral-900 border border-white/10 text-white text-xs font-mono focus:outline-none focus:border-[#89ff00]"
                />
              </div>

              <div>
                <label className="block text-xs font-mono text-neutral-300 uppercase mb-1">
                  Allowed Redirect URIs (One per line)
                </label>
                <textarea
                  rows={2}
                  value={newApp.allowed_redirect_uris}
                  onChange={(e) => setNewApp({ ...newApp, allowed_redirect_uris: e.target.value })}
                  placeholder="https://app.faizal.uk/callback"
                  className="w-full px-3.5 py-2 rounded-xl bg-neutral-900 border border-white/10 text-white text-xs font-mono focus:outline-none focus:border-[#89ff00]"
                />
              </div>

              <div>
                <label className="block text-xs font-mono text-neutral-300 uppercase mb-1">
                  Allowed Scopes (Comma-separated)
                </label>
                <input
                  type="text"
                  value={newApp.allowed_scopes}
                  onChange={(e) => setNewApp({ ...newApp, allowed_scopes: e.target.value })}
                  placeholder="profile:read, business:write, fleet:control"
                  className="w-full px-3.5 py-2 rounded-xl bg-neutral-900 border border-white/10 text-white text-xs font-mono focus:outline-none focus:border-[#89ff00]"
                />
              </div>

              <div className="flex justify-end gap-3 pt-3 border-t border-white/5">
                <button
                  type="button"
                  onClick={() => setShowAddAppModal(false)}
                  className="px-4 py-2 rounded-xl bg-white/5 hover:bg-white/10 text-neutral-400 text-xs font-semibold cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 rounded-xl bg-[#89ff00] text-black font-semibold text-xs tracking-wider uppercase hover:bg-[#7ce600] cursor-pointer"
                >
                  Create Application
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ─── MODAL: GENERATE PERSONAL ACCESS TOKEN ───────────────────────────── */}
      {showCreatePatModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md">
          <div className="w-full max-w-md bg-[#0d0e12] border border-white/10 rounded-2xl p-6 text-white shadow-2xl">
            <h3 className="text-base font-bold text-white mb-1">Generate Personal Access Token</h3>
            <p className="text-xs text-neutral-400 mb-5">
              Create a machine token with 'nex_live_' prefix for daemons and CLI operations.
            </p>

            <form onSubmit={handleCreatePat} className="space-y-4">
              <div>
                <label className="block text-xs font-mono text-neutral-300 uppercase mb-1">Token Name *</label>
                <input
                  type="text"
                  required
                  value={newPatName}
                  onChange={(e) => setNewPatName(e.target.value)}
                  placeholder="e.g. potato-pro-daemon or ci-runner"
                  className="w-full px-3.5 py-2 rounded-xl bg-neutral-900 border border-white/10 text-white text-xs font-mono focus:outline-none focus:border-purple-400"
                />
              </div>

              <div className="flex justify-end gap-3 pt-3 border-t border-white/5">
                <button
                  type="button"
                  onClick={() => setShowCreatePatModal(false)}
                  className="px-4 py-2 rounded-xl bg-white/5 text-neutral-400 text-xs font-semibold cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 rounded-xl bg-purple-600 text-white font-semibold text-xs tracking-wider uppercase hover:bg-purple-500 cursor-pointer"
                >
                  Generate
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Confirm Unban Modal */}
      {selectedUnbanIp && (
        <ConfirmModal
          isOpen={true}
          title="Unban IP Address"
          message={`Are you sure you want to restore access to IP address: ${selectedUnbanIp}?`}
          onConfirm={confirmUnban}
          onCancel={() => setSelectedUnbanIp(null)}
        />
      )}
    </div>
  );
}
