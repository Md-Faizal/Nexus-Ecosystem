import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Server,
  Cpu,
  HardDrive,
  Activity,
  Zap,
  Terminal,
  RefreshCw,
  Power,
  PowerOff,
  Gamepad2,
  Monitor,
  Moon,
  Bot,
  Copy,
  Check,
  Sliders,
  Sparkles,
  Send,
  X,
  Shield,
  Layers,
} from 'lucide-react';

export default function FleetMeshSection() {
  const [nodes, setNodes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [copiedOs, setCopiedOs] = useState(null);
  const [actionLoading, setActionLoading] = useState({});
  const [selectedAiNode, setSelectedAiNode] = useState(null);
  const [aiModel, setAiModel] = useState('');
  const [aiPrompt, setAiPrompt] = useState('');
  const [aiResponses, setAiResponses] = useState([]);
  const [isGenerating, setIsGenerating] = useState(false);
  const [featureModalNode, setFeatureModalNode] = useState(null);

  const getAuthHeaders = () => {
    const pin = localStorage.getItem('faizal_admin_pin') || localStorage.getItem('admin_token') || '2004';
    return {
      'Authorization': pin,
      'X-Admin-Pin': pin,
    };
  };

  // Fetch nodes from central mesh hub
  const fetchNodes = async () => {
    try {
      const res = await fetch('/api/fleet/nodes', {
        headers: getAuthHeaders(),
      });
      if (res.ok) {
        const data = await res.json();
        setNodes(data);
      }
    } catch (e) {
      console.error('Failed to fetch fleet nodes', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchNodes();
    const interval = setInterval(fetchNodes, 3500);
    return () => clearInterval(interval);
  }, []);

  const copyToClipboard = (text, os) => {
    navigator.clipboard.writeText(text);
    setCopiedOs(os);
    setTimeout(() => setCopiedOs(null), 2000);
  };

  const handleDispatchCommand = async (nodeId, action, args = []) => {
    setActionLoading((prev) => ({ ...prev, [`${nodeId}-${action}`]: true }));
    try {
      const res = await fetch(`/api/fleet/nodes/${nodeId}/command`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...getAuthHeaders(),
        },
        body: JSON.stringify({ action, args }),
      });
      const data = await res.json();
      if (res.ok) {
        alert(`Action '${action}' executed successfully!\nOutput: ${data.stdout || 'Done'}`);
      } else {
        alert(`Action failed: ${data.error || 'Unknown error'}`);
      }
    } catch (e) {
      alert(`Network error: ${e.message}`);
    } finally {
      setActionLoading((prev) => ({ ...prev, [`${nodeId}-${action}`]: false }));
      fetchNodes();
    }
  };

  const handleToggleFeature = async (nodeId, featureKey, currentValue) => {
    const node = nodes.find((n) => n.node_id === nodeId);
    if (!node) return;

    const updatedFeatures = {
      ...node.features,
      [featureKey]: !currentValue,
    };

    try {
      await fetch(`/api/fleet/nodes/${nodeId}/features`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...getAuthHeaders(),
        },
        body: JSON.stringify({ features: updatedFeatures }),
      });
      fetchNodes();
    } catch (e) {
      console.error('Failed to update features', e);
    }
  };

  const handleSendAiPrompt = async (e) => {
    e.preventDefault();
    if (!aiPrompt.trim() || !selectedAiNode || isGenerating) return;

    const userMessage = aiPrompt;
    setAiPrompt('');
    setAiResponses((prev) => [
      ...prev,
      { role: 'user', text: userMessage },
      { role: 'assistant', text: '', model: aiModel },
    ]);
    setIsGenerating(true);

    try {
      const res = await fetch(`/api/fleet/nodes/${selectedAiNode.node_id}/ai/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...getAuthHeaders(),
        },
        body: JSON.stringify({
          model: aiModel || selectedAiNode.models[0] || 'llama3.2:3b',
          prompt: userMessage,
        }),
      });

      const reader = res.body.getReader();
      const decoder = new TextDecoder();

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;

        const chunk = decoder.decode(value);
        const lines = chunk.split('\n');

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const token = line.replace('data:', '');
            setAiResponses((prev) => {
              const updated = [...prev];
              const last = updated[updated.length - 1];
              if (last && last.role === 'assistant') {
                last.text += token;
              }
              return updated;
            });
          }
        }
      }
    } catch (e) {
      setAiResponses((prev) => [
        ...prev,
        { role: 'assistant', text: `Error: ${e.message}`, error: true },
      ]);
    } finally {
      setIsGenerating(false);
    }
  };

  const formatBytes = (bytes) => {
    if (!bytes) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  return (
    <div className="space-y-6">
      {/* ─── 0-TOUCH INSTALLER BANNER ────────────────────────────────────────── */}
      <div
        className="rounded-3xl p-6 relative overflow-hidden backdrop-blur-xl"
        style={{
          backgroundColor: 'rgba(18, 19, 22, 0.9)',
          border: '1px solid rgba(137, 255, 0, 0.25)',
          boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.5)',
        }}
      >
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="w-2 h-2 rounded-full bg-[#89ff00] animate-ping" />
              <span className="text-xs font-mono font-bold text-[#89ff00] tracking-wider uppercase">
                Zero-Touch Universal Fleet Installer
              </span>
            </div>
            <h3 className="text-lg font-bold text-white tracking-tight">
              Add Any PC, Mac, or Linux Server to Your Private Mesh in 1 Command
            </h3>
            <p className="text-xs text-neutral-400 mt-1 max-w-2xl">
              No registration token, no open ports, and no firewall configuration. Run the command below on any machine — it connects outbound via encrypted TLS and automatically appears in your fleet dashboard within seconds.
            </p>
          </div>

          <div className="flex flex-col sm:flex-row gap-2 shrink-0">
            {/* Windows 1-liner */}
            <div className="flex items-center gap-2 px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-xs font-mono text-neutral-200">
              <span className="text-blue-400 font-semibold">Windows:</span>
              <code className="text-neutral-300">irm https://faizal.uk/install.ps1 | iex</code>
              <button
                onClick={() => copyToClipboard('irm https://faizal.uk/install.ps1 | iex', 'win')}
                className="p-1 hover:text-[#89ff00] transition-colors cursor-pointer"
                title="Copy Windows command"
              >
                {copiedOs === 'win' ? <Check className="w-4 h-4 text-[#89ff00]" /> : <Copy className="w-4 h-4" />}
              </button>
            </div>

            {/* Linux / macOS 1-liner */}
            <div className="flex items-center gap-2 px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-xs font-mono text-neutral-200">
              <span className="text-amber-400 font-semibold">Linux/Mac:</span>
              <code className="text-neutral-300">curl -fsSL https://faizal.uk/install.sh | bash</code>
              <button
                onClick={() => copyToClipboard('curl -fsSL https://faizal.uk/install.sh | bash', 'nix')}
                className="p-1 hover:text-[#89ff00] transition-colors cursor-pointer"
                title="Copy Linux/macOS command"
              >
                {copiedOs === 'nix' ? <Check className="w-4 h-4 text-[#89ff00]" /> : <Copy className="w-4 h-4" />}
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* ─── CONNECTED FLEET GRID ────────────────────────────────────────────── */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-xl bg-[#89ff00]/10 border border-[#89ff00]/20 text-[#89ff00]">
            <Layers className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-white tracking-tight">Active Edge & Workstation Nodes</h3>
            <p className="text-xs text-neutral-400">
              {nodes.filter((n) => n.status === 'online').length} online / {nodes.length} registered systems
            </p>
          </div>
        </div>

        <button
          onClick={fetchNodes}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white/5 hover:bg-white/10 border border-white/10 text-xs text-neutral-300 transition-all cursor-pointer"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          <span>Refresh</span>
        </button>
      </div>

      {loading && nodes.length === 0 ? (
        <div className="p-8 text-center text-neutral-500 font-mono text-sm">
          Scanning Nexus Mesh Hub for enrolled nodes...
        </div>
      ) : nodes.length === 0 ? (
        <div className="p-8 rounded-2xl bg-white/[0.02] border border-white/5 text-center text-neutral-400 text-sm">
          No edge nodes connected yet. Run the 1-liner install command above on your local PC to join the mesh!
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {nodes.map((node) => {
            const isOnline = node.status === 'online';
            const telemetry = node.telemetry || {};
            const cpuUsage = telemetry.cpu?.usage_pct?.toFixed(1) || '0.0';
            const memUsedPct = telemetry.memory?.used_pct?.toFixed(1) || '0.0';
            const memUsedBytes = telemetry.memory?.used_bytes || 0;
            const memTotalBytes = telemetry.memory?.total_bytes || 0;
            const hasGpu = !!telemetry.gpu;
            const hasAi = node.models && node.models.length > 0;

            return (
              <motion.div
                key={node.node_id}
                layout
                className="rounded-2xl p-5 backdrop-blur-xl relative flex flex-col justify-between gap-4"
                style={{
                  backgroundColor: 'rgba(24, 25, 29, 0.85)',
                  border: isOnline
                    ? hasAi
                      ? '1px solid rgba(168, 85, 247, 0.4)'
                      : '1px solid rgba(137, 255, 0, 0.25)'
                    : '1px solid rgba(255, 255, 255, 0.08)',
                  boxShadow: '0 8px 24px rgba(0, 0, 0, 0.4)',
                }}
              >
                {/* Header */}
                <div className="flex items-start justify-between">
                  <div>
                    <div className="flex items-center gap-2">
                      <span
                        className={`w-2.5 h-2.5 rounded-full ${isOnline ? 'bg-[#89ff00] animate-pulse' : 'bg-neutral-600'
                          }`}
                      />
                      <h4 className="text-sm font-bold text-white tracking-tight">{node.hostname}</h4>
                      <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-white/5 text-neutral-300 border border-white/10 uppercase">
                        {node.os} {node.arch}
                      </span>
                    </div>
                    <p className="text-[11px] font-mono text-neutral-500 mt-0.5">{node.node_id}</p>
                  </div>

                  {/* Badges */}
                  <div className="flex items-center gap-1.5">
                    {hasAi && (
                      <span className="flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-mono font-semibold bg-purple-500/20 text-purple-300 border border-purple-500/30">
                        <Bot className="w-3 h-3" />
                        AI Station
                      </span>
                    )}
                    <button
                      onClick={() => setFeatureModalNode(node)}
                      className="p-1 rounded-lg bg-white/5 hover:bg-white/10 text-neutral-400 hover:text-white transition-colors cursor-pointer"
                      title="Remote Feature Toggles"
                    >
                      <Sliders className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>

                {/* Hardware Gauges */}
                {isOnline && (
                  <div className="grid grid-cols-2 gap-2 text-xs">
                    {/* CPU */}
                    <div className="p-2.5 rounded-xl bg-white/[0.03] border border-white/5">
                      <div className="flex items-center justify-between text-neutral-400 mb-1">
                        <span className="flex items-center gap-1">
                          <Cpu className="w-3.5 h-3.5 text-[#89ff00]" /> CPU
                        </span>
                        <span className="font-mono text-white font-semibold">{cpuUsage}%</span>
                      </div>
                      <div className="w-full bg-white/5 rounded-full h-1.5 overflow-hidden">
                        <div
                          className="bg-[#89ff00] h-full rounded-full transition-all"
                          style={{ width: `${Math.min(100, Math.max(0, cpuUsage))}%` }}
                        />
                      </div>
                    </div>

                    {/* RAM */}
                    <div className="p-2.5 rounded-xl bg-white/[0.03] border border-white/5">
                      <div className="flex items-center justify-between text-neutral-400 mb-1">
                        <span className="flex items-center gap-1">
                          <Activity className="w-3.5 h-3.5 text-cyan-400" /> RAM
                        </span>
                        <span className="font-mono text-white font-semibold">{memUsedPct}%</span>
                      </div>
                      <div className="w-full bg-white/5 rounded-full h-1.5 overflow-hidden">
                        <div
                          className="bg-cyan-400 h-full rounded-full transition-all"
                          style={{ width: `${Math.min(100, Math.max(0, memUsedPct))}%` }}
                        />
                      </div>
                      <div className="text-[10px] font-mono text-neutral-500 mt-1">
                        {formatBytes(memUsedBytes)} / {formatBytes(memTotalBytes)}
                      </div>
                    </div>

                    {/* GPU (if present) */}
                    {hasGpu && (
                      <div className="col-span-2 p-2.5 rounded-xl bg-white/[0.03] border border-white/5">
                        <div className="flex items-center justify-between text-neutral-400 mb-1">
                          <span className="flex items-center gap-1 text-purple-300">
                            <Sparkles className="w-3.5 h-3.5 text-purple-400" />
                            {telemetry.gpu.vendor} {telemetry.gpu.name}
                          </span>
                          <span className="font-mono text-white font-semibold">
                            {telemetry.gpu.utilization_pct?.toFixed(0)}%
                          </span>
                        </div>
                        <div className="text-[10px] font-mono text-neutral-400">
                          VRAM: {telemetry.gpu.vram_used_mb} MB / {telemetry.gpu.vram_total_mb} MB
                          {telemetry.gpu.temperature_c && ` • ${telemetry.gpu.temperature_c}°C`}
                        </div>
                      </div>
                    )}

                    {/* Local AI Models Roster */}
                    {hasAi && (
                      <div className="col-span-2 p-2.5 rounded-xl bg-purple-500/10 border border-purple-500/20">
                        <div className="flex items-center justify-between text-purple-300 mb-1">
                          <span className="font-semibold flex items-center gap-1">
                            <Bot className="w-3.5 h-3.5" /> Ollama Local LLMs:
                          </span>
                          <button
                            onClick={() => {
                              setSelectedAiNode(node);
                              setAiModel(node.models[0] || 'llama3.2:3b');
                            }}
                            className="px-2 py-0.5 rounded bg-purple-500 hover:bg-purple-600 text-white font-mono text-[10px] transition-colors cursor-pointer"
                          >
                            Chat Live →
                          </button>
                        </div>
                        <div className="flex flex-wrap gap-1 mt-1">
                          {node.models.map((m) => (
                            <span
                              key={m}
                              className="px-1.5 py-0.5 rounded text-[10px] font-mono bg-purple-950/60 border border-purple-500/30 text-purple-200"
                            >
                              {m}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                )}

                {/* Quick Actions Footer */}
                <div className="pt-2 border-t border-white/5 flex items-center justify-between gap-1">
                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => {
                        if (window.confirm(`Reboot node '${node.hostname}'?`)) {
                          handleDispatchCommand(node.node_id, 'reboot');
                        }
                      }}
                      disabled={!isOnline || actionLoading[`${node.node_id}-reboot`]}
                      className="p-1.5 rounded-lg bg-white/5 hover:bg-yellow-500/20 text-neutral-400 hover:text-yellow-400 transition-colors disabled:opacity-30 cursor-pointer"
                      title="Reboot Machine"
                    >
                      <Power className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => {
                        if (window.confirm(`Turn OFF and shutdown node '${node.hostname}'? This will power down the machine completely.`)) {
                          handleDispatchCommand(node.node_id, 'shutdown');
                        }
                      }}
                      disabled={!isOnline || actionLoading[`${node.node_id}-shutdown`]}
                      className="p-1.5 rounded-lg bg-white/5 hover:bg-red-500/20 text-neutral-400 hover:text-red-400 transition-colors disabled:opacity-30 cursor-pointer"
                      title="Turn Off / Power Off Machine"
                    >
                      <PowerOff className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => {
                        if (window.confirm(`🎮 Switch '${node.hostname}' to dedicated Minecraft Mode?\n\n• If someone is actively using the PC, an on-screen prompt with a 30s cancellation window will appear.\n• GNOME desktop will exit to free 1.2+ GB RAM.\n• Minecraft Forge Server will start.`)) {
                          handleDispatchCommand(node.node_id, 'minecraft_mode');
                        }
                      }}
                      disabled={!isOnline || actionLoading[`${node.node_id}-minecraft_mode`]}
                      className="p-1.5 rounded-lg bg-white/5 hover:bg-emerald-500/20 text-neutral-400 hover:text-emerald-400 transition-colors disabled:opacity-30 cursor-pointer"
                      title="Switch to Minecraft Server Mode (Headless, Free 1.2GB RAM)"
                    >
                      <Gamepad2 className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => {
                        if (window.confirm(`🖥️ Restore '${node.hostname}' to Normal Desktop Mode?\n\n• Minecraft Server will stop.\n• GNOME Graphical Desktop will be restored for family use.`)) {
                          handleDispatchCommand(node.node_id, 'normal_mode');
                        }
                      }}
                      disabled={!isOnline || actionLoading[`${node.node_id}-normal_mode`]}
                      className="p-1.5 rounded-lg bg-white/5 hover:bg-cyan-500/20 text-neutral-400 hover:text-cyan-400 transition-colors disabled:opacity-30 cursor-pointer"
                      title="Restore GNOME Desktop (Normal Mode)"
                    >
                      <Monitor className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => handleDispatchCommand(node.node_id, 'sleep')}
                      disabled={!isOnline || actionLoading[`${node.node_id}-sleep`]}
                      className="p-1.5 rounded-lg bg-white/5 hover:bg-blue-500/20 text-neutral-400 hover:text-blue-400 transition-colors disabled:opacity-30 cursor-pointer"
                      title="Sleep / Suspend"
                    >
                      <Moon className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => handleDispatchCommand(node.node_id, 'docker_prune')}
                      disabled={!isOnline}
                      className="p-1.5 rounded-lg bg-white/5 hover:bg-white/10 text-neutral-400 hover:text-white transition-colors disabled:opacity-30 cursor-pointer"
                      title="Clean Docker Caches"
                    >
                      <HardDrive className="w-3.5 h-3.5" />
                    </button>
                  </div>

                  <span className="text-[10px] font-mono text-neutral-500">
                    {isOnline ? `v${node.agent_version}` : `Offline (${node.last_seen_secs}s ago)`}
                  </span>
                </div>
              </motion.div>
            );
          })}
        </div>
      )}

      {/* ─── LIVE AI STATION CHAT DRAWER / MODAL ──────────────────────────────── */}
      <AnimatePresence>
        {selectedAiNode && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md"
          >
            <motion.div
              initial={{ scale: 0.95 }}
              animate={{ scale: 1 }}
              exit={{ scale: 0.95 }}
              className="w-full max-w-2xl rounded-3xl p-6 bg-[#16171b] border border-purple-500/30 shadow-2xl flex flex-col h-[600px]"
            >
              <div className="flex items-center justify-between pb-4 border-b border-white/10">
                <div className="flex items-center gap-2">
                  <div className="p-2 rounded-xl bg-purple-500/20 text-purple-300">
                    <Bot className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="font-bold text-white tracking-tight">
                      Local AI Station: {selectedAiNode.hostname}
                    </h3>
                    <p className="text-xs text-neutral-400">
                      Tunnelling live to local Ollama on {selectedAiNode.node_id}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <select
                    value={aiModel}
                    onChange={(e) => setAiModel(e.target.value)}
                    className="px-3 py-1.5 rounded-xl bg-white/5 border border-white/10 text-xs font-mono text-purple-200 focus:outline-none"
                  >
                    {selectedAiNode.models.map((m) => (
                      <option key={m} value={m} className="bg-neutral-900 text-white">
                        {m}
                      </option>
                    ))}
                  </select>
                  <button
                    onClick={() => setSelectedAiNode(null)}
                    className="p-1.5 rounded-xl bg-white/5 hover:bg-white/10 text-neutral-400 hover:text-white"
                  >
                    <X className="w-4 h-4" />
                  </button>
                </div>
              </div>

              {/* Chat Message Window */}
              <div className="flex-1 overflow-y-auto py-4 space-y-3 font-mono text-xs">
                {aiResponses.length === 0 ? (
                  <div className="h-full flex flex-col items-center justify-center text-neutral-500">
                    <Sparkles className="w-8 h-8 mb-2 text-purple-500/40" />
                    <span>Send a prompt to query {aiModel} running locally on {selectedAiNode.hostname}</span>
                  </div>
                ) : (
                  aiResponses.map((msg, idx) => (
                    <div
                      key={idx}
                      className={`p-3 rounded-2xl ${msg.role === 'user'
                          ? 'bg-purple-600/20 text-purple-200 border border-purple-500/30 ml-8'
                          : 'bg-white/5 text-neutral-200 border border-white/5 mr-8'
                        }`}
                    >
                      <div className="text-[10px] text-neutral-400 font-semibold mb-1 uppercase">
                        {msg.role === 'user' ? 'You' : `${msg.model || 'AI'} (Local)`}
                      </div>
                      <div className="whitespace-pre-wrap">{msg.text || (isGenerating && 'Thinking...')}</div>
                    </div>
                  ))
                )}
              </div>

              {/* Chat Input */}
              <form onSubmit={handleSendAiPrompt} className="pt-3 border-t border-white/10 flex gap-2">
                <input
                  type="text"
                  value={aiPrompt}
                  onChange={(e) => setAiPrompt(e.target.value)}
                  placeholder={`Ask ${aiModel} on your local PC...`}
                  disabled={isGenerating}
                  className="flex-1 px-4 py-2.5 rounded-xl bg-white/5 border border-white/10 text-xs text-white placeholder-neutral-500 focus:outline-none focus:border-purple-500/50"
                />
                <button
                  type="submit"
                  disabled={isGenerating || !aiPrompt.trim()}
                  className="px-4 py-2.5 rounded-xl bg-purple-600 hover:bg-purple-500 text-white text-xs font-semibold flex items-center gap-1.5 disabled:opacity-40 transition-colors cursor-pointer"
                >
                  <Send className="w-3.5 h-3.5" />
                  <span>Send</span>
                </button>
              </form>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* ─── REMOTE FEATURE TOGGLES MODAL ─────────────────────────────────────── */}
      <AnimatePresence>
        {featureModalNode && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md"
          >
            <motion.div
              initial={{ scale: 0.95 }}
              animate={{ scale: 1 }}
              exit={{ scale: 0.95 }}
              className="w-full max-w-md rounded-3xl p-6 bg-[#16171b] border border-white/10 shadow-2xl space-y-4"
            >
              <div className="flex items-center justify-between pb-3 border-b border-white/10">
                <div className="flex items-center gap-2">
                  <Sliders className="w-4 h-4 text-[#89ff00]" />
                  <h3 className="font-bold text-white text-sm">
                    Remote Feature Controls: {featureModalNode.hostname}
                  </h3>
                </div>
                <button
                  onClick={() => setFeatureModalNode(null)}
                  className="p-1 rounded-lg bg-white/5 text-neutral-400 hover:text-white"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              <div className="space-y-3">
                {[
                  { key: 'telemetry', label: 'Hardware & OS Telemetry', desc: 'Streams CPU, RAM, Disk, Net metrics' },
                  { key: 'gpu', label: 'GPU Acceleration Monitor', desc: 'Queries NVML/Metal hardware counters' },
                  { key: 'ai_bridge', label: 'Local Ollama AI Tunnel', desc: 'Enables remote chat with local LLMs' },
                  { key: 'docker', label: 'Docker Container Supervision', desc: 'Tracks running container statuses' },
                  { key: 'auto_update', label: 'Silent OTA Auto-Update', desc: 'Updates agent safely in background' },
                ].map((item) => {
                  const enabled = featureModalNode.features?.[item.key] ?? false;
                  return (
                    <div
                      key={item.key}
                      className="flex items-center justify-between p-3 rounded-xl bg-white/[0.03] border border-white/5"
                    >
                      <div>
                        <div className="text-xs font-semibold text-white">{item.label}</div>
                        <div className="text-[10px] text-neutral-400">{item.desc}</div>
                      </div>
                      <button
                        onClick={() => handleToggleFeature(featureModalNode.node_id, item.key, enabled)}
                        className={`w-11 h-6 flex items-center rounded-full p-1 transition-colors cursor-pointer ${enabled ? 'bg-[#89ff00]' : 'bg-neutral-700'
                          }`}
                      >
                        <div
                          className={`bg-black w-4 h-4 rounded-full shadow-md transform transition-transform ${enabled ? 'translate-x-5' : 'translate-x-0'
                            }`}
                        />
                      </button>
                    </div>
                  );
                })}
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

