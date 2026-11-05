import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Server,
  Cpu,
  Layers,
  ShieldCheck,
  RefreshCw,
  CheckCircle2,
  HardDrive,
  Activity,
  Globe,
  Radio,
  Zap,
  Boxes,
  ArrowRight,
  Database,
  Network,
  RotateCcw,
  ExternalLink,
  Play,
  Square,
  Sparkles,
  Gamepad2,
  Mail,
  Monitor,
  Terminal,
  Lock
} from 'lucide-react';
import ServiceCard from '../components/ServiceCard';
import ClusterNetworkVisualizer from '../components/ClusterNetworkVisualizer';

export default function ServicesTab({
  services = [],
  cluster = {},
  onServiceAction,
  onRefresh,
  refreshing,
  fetchWithAuth,
  showToast
}) {
  const [nodeFilter, setNodeFilter] = useState('all'); // 'all' | 'primary' | 'backup' | 'gcp' | 'both'
  const [categoryFilter, setCategoryFilter] = useState('all'); // 'all' | 'core' | 'mail' | 'desktop' | 'game'
  const [servingFilter, setServingFilter] = useState('all'); // 'all' | 'serving' | 'standby' | 'off'
  const [nodeActionLoading, setNodeActionLoading] = useState(null);

  // Extract cluster nodes
  const primaryNode = cluster?.master || cluster?.primary || {};
  const backupNode = cluster?.slave || cluster?.backup || {};
  const gcpNode = cluster?.gcp || {};

  const formatBytes = (bytes) => {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  const formatUptime = (seconds) => {
    if (!seconds) return '0h 0m';
    const d = Math.floor(seconds / (3600 * 24));
    const h = Math.floor((seconds % (3600 * 24)) / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    if (d > 0) return `${d}d ${h}h ${m}m`;
    return `${h}h ${m}m`;
  };

  // Node category mapper
  const getNodeCategory = (service) => {
    const node = (service.node || '').toLowerCase();
    if (node === 'both' || node === 'cluster' || node === 'all') return 'both';
    if (node === 'prometheus' || node === 'pro' || node === 'gcp') return 'gcp';
    if (node === 'container-of-souls' || node === 'cos' || node === 'secondary' || node === 'backup')
      return 'backup';
    return 'primary';
  };

  // Service Category mapper
  const getServiceCategory = (service) => {
    const id = (service.id || '').toLowerCase();
    const cat = (service.category || '').toLowerCase();
    if (cat === 'game' || id.includes('minecraft') || id.includes('mc-manager') || id.includes('cs-panel'))
      return 'game';
    if (cat === 'mail' || id.includes('stalwart') || id.includes('mail')) return 'mail';
    if (id.includes('macos') || id.includes('bloomberg')) return 'desktop';
    return 'core';
  };

  const getServingStatus = (service) => {
    return (
      service.serving_state ||
      (service.id === 'macos-server-green'
        ? 'standby'
        : service.node === 'both' ||
          service.node === 'cluster' ||
          service.id === 'personal-site-rust' ||
          service.id === 'cockpit'
        ? 'both'
        : service.status === 'running' || service.status === 'online'
        ? 'active'
        : 'idle')
    );
  };

  const isOnline = (s) => s.status === 'running' || s.status === 'online';
  const isActivelyServing = (s) => {
    const st = getServingStatus(s);
    return (st === 'active' || st === 'both' || st === 'cluster') && isOnline(s);
  };
  const isStandby = (s) => getServingStatus(s) === 'standby';
  const isOff = (s) => !isOnline(s);

  const matchesFilter = (s) => {
    // Node filter
    if (nodeFilter !== 'all' && getNodeCategory(s) !== nodeFilter) return false;
    // Category filter
    if (categoryFilter !== 'all' && getServiceCategory(s) !== categoryFilter) return false;
    // Serving status filter
    if (servingFilter === 'serving' && !isActivelyServing(s)) return false;
    if (servingFilter === 'standby' && !isStandby(s)) return false;
    if (servingFilter === 'off' && !isOff(s)) return false;
    return true;
  };

  // Status sorting: Actively Serving first (1), Standby replicas second (2), Other Online third (3), Stopped last (4)
  const serviceSortWeight = (s) => {
    if (isActivelyServing(s)) return 1;
    if (isStandby(s)) return 2;
    if (isOnline(s)) return 3;
    return 4;
  };

  const sortServices = (arr) =>
    [...arr].sort((a, b) => serviceSortWeight(a) - serviceSortWeight(b));

  const visibleServices = sortServices(services.filter(matchesFilter));

  const activeServingCount = services.filter(isActivelyServing).length;
  const standbyCount = services.filter(isStandby).length;
  const offCount = services.filter(isOff).length;
  const totalOnline = services.filter(isOnline).length;

  // 1-Click VM Node Action handler
  const handleNodeAction = async (targetNode, action) => {
    if (!fetchWithAuth) return;
    setNodeActionLoading(`${targetNode}_${action}`);
    try {
      const res = await fetchWithAuth('/api/cluster/action', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action, node: targetNode })
      });
      const data = await res.json();
      if (data.success) {
        if (showToast)
          showToast(data.message || `Action on ${targetNode} completed!`, 'success');
        if (onRefresh) onRefresh();
      } else {
        if (showToast)
          showToast(data.error || `Failed action on ${targetNode}`, 'error');
      }
    } catch (err) {
      if (showToast)
        showToast(`Error performing node action: ${err.message}`, 'error');
    } finally {
      setNodeActionLoading(null);
    }
  };

  return (
    <div
      className="space-y-8"
      style={{ display: 'flex', flexDirection: 'column', gap: '32px' }}
    >
      {/* ──────────────────────────────────────────────────────────── */}
      {/* 1. TOP HEADER BAR: CLOUD FLEET COMMAND & GLOBAL STATUS */}
      {/* ──────────────────────────────────────────────────────────── */}
      <div
        className="flex flex-col lg:flex-row lg:items-center justify-between gap-6 rounded-3xl backdrop-blur-xl"
        style={{
          padding: '28px',
          backgroundColor: 'rgba(14, 15, 19, 0.92)',
          border: '1px solid rgba(255, 255, 255, 0.09)',
          boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.5)',
          boxSizing: 'border-box'
        }}
      >
        <div>
          <div className="flex items-center gap-2 mb-1.5">
            <span className="w-2.5 h-2.5 rounded-full bg-[#89ff00] animate-pulse" />
            <span className="text-xs font-mono font-bold text-[#89ff00] tracking-widest uppercase">
              Multi-Cloud Autonomous Fleet Console
            </span>
            <span className="px-2 py-0.5 rounded-full bg-white/10 border border-white/10 text-[10px] font-mono text-neutral-300">
              OCI + GCP Mesh
            </span>
          </div>
          <h2 className="text-xl sm:text-2xl font-bold text-white tracking-tight">
            VM Cluster & App Orchestration Portal
          </h2>
          <p className="text-xs sm:text-sm text-neutral-400 mt-1 max-w-2xl leading-relaxed">
            Centralized management across <strong className="text-[#89ff00]">my-mac (Edge Ingress)</strong>,{' '}
            <strong className="text-sky-400">cos (High-Availability Failsafe)</strong>, and{' '}
            <strong className="text-amber-400">pro (Auxiliary & Game Host)</strong> with 1-click execution.
          </p>
        </div>

        {/* Global Controls & Status Indicators */}
        <div className="flex flex-wrap items-center gap-3">
          <div
            className="flex items-center gap-2 px-3.5 py-2 rounded-2xl text-xs font-mono"
            style={{
              backgroundColor: 'rgba(137, 255, 0, 0.08)',
              border: '1px solid rgba(137, 255, 0, 0.25)'
            }}
          >
            <span className="w-2 h-2 rounded-full bg-[#89ff00] animate-pulse" />
            <span className="text-neutral-300">
              <strong className="text-[#89ff00]">{activeServingCount}</strong> Actively Serving
            </span>
          </div>

          <div
            className="flex items-center gap-2 px-3.5 py-2 rounded-2xl text-xs font-mono"
            style={{
              backgroundColor: 'rgba(56, 189, 248, 0.08)',
              border: '1px solid rgba(56, 189, 248, 0.25)'
            }}
          >
            <span className="w-2 h-2 rounded-full bg-sky-400" />
            <span className="text-neutral-300">
              <strong className="text-sky-400">{standbyCount}</strong> Standby Replicas
            </span>
          </div>

          <div
            className="flex items-center gap-2 px-3.5 py-2 rounded-2xl text-xs font-mono"
            style={{
              backgroundColor: 'rgba(255, 51, 102, 0.08)',
              border: '1px solid rgba(255, 51, 102, 0.25)'
            }}
          >
            <span className="w-2 h-2 rounded-full bg-[#ff3366]" />
            <span className="text-neutral-300">
              <strong className="text-[#ff3366]">{offCount}</strong> Off / Stopped
            </span>
          </div>

          <div
            className="flex items-center gap-2 px-3.5 py-2 rounded-2xl text-xs font-mono"
            style={{
              backgroundColor: 'rgba(255, 255, 255, 0.04)',
              border: '1px solid rgba(255, 255, 255, 0.08)'
            }}
          >
            <span className="text-neutral-400">
              Total: <strong className="text-white">{totalOnline}</strong> / {services.length}
            </span>
          </div>

          <button
            onClick={onRefresh}
            disabled={refreshing}
            className="flex items-center gap-2 px-4 py-2.5 rounded-2xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-mono font-semibold text-white transition-all cursor-pointer disabled:opacity-50"
            title="Refresh fleet telemetry & status"
          >
            <RefreshCw
              className={`w-3.5 h-3.5 ${
                refreshing ? 'animate-spin text-[#89ff00]' : 'text-neutral-400'
              }`}
            />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* ──────────────────────────────────────────────────────────── */}
      {/* 2. REUSABLE MULTI-SERVICE LIVE TRAFFIC & PACKET FLOW MESH */}
      {/* ──────────────────────────────────────────────────────────── */}
      <ClusterNetworkVisualizer
        services={services}
        fetchWithAuth={fetchWithAuth}
        showToast={showToast}
      />

      {/* ──────────────────────────────────────────────────────────── */}
      {/* 3. 3-VM MULTI-CLOUD HARDWARE FLEET CARDS (AWS/OCI/GCP STYLE) */}
      {/* ──────────────────────────────────────────────────────────── */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Server className="w-4 h-4 text-[#89ff00]" />
            <h3 className="text-sm font-mono font-bold uppercase tracking-wider text-white">
              Multi-Cloud Compute Instances (3 VMs)
            </h3>
          </div>
          <span className="text-xs font-mono text-neutral-400">
            Cascade: my-mac (P1) ➔ cos (P2) ➔ pro (P3)
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
          {/* VM 1: my-mac */}
          <div
            className="rounded-2xl p-5 border relative overflow-hidden flex flex-col justify-between"
            style={{
              backgroundColor: 'rgba(16, 17, 22, 0.9)',
              borderColor: 'rgba(137, 255, 0, 0.3)',
              boxShadow: '0 8px 30px rgba(0, 0, 0, 0.4)'
            }}
          >
            <div>
              <div className="flex items-center justify-between mb-3">
                <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-[#89ff00]/15 text-[#89ff00] border border-[#89ff00]/30">
                  PRIORITY 1: ACTIVE INGRESS
                </span>
                <span className="w-2 h-2 rounded-full bg-[#89ff00] animate-pulse" />
              </div>

              <div className="flex items-center justify-between mb-1">
                <h4 className="text-base font-bold text-white flex items-center gap-2">
                  <Server className="w-4 h-4 text-[#89ff00]" />
                  <span>my-mac</span>
                </h4>
                <span className="text-[10px] font-mono text-neutral-400 px-2 py-0.5 rounded bg-white/5">
                  Oracle Cloud (UK)
                </span>
              </div>

              <div className="text-xs font-mono text-neutral-400 mb-4">
                Public: <strong className="text-white">68.233.115.138</strong> • VCN: 10.0.0.86
                <div className="text-[10px] text-neutral-500 mt-0.5">
                  Uptime: {formatUptime(primaryNode.uptime)} • {primaryNode.os || 'Ubuntu 24.04'}
                </div>
              </div>

              {/* Resource Bars */}
              <div className="space-y-3 pt-3 border-t border-white/5 text-xs font-mono">
                <div>
                  <div className="flex justify-between text-[11px] mb-1">
                    <span className="text-neutral-400">CPU Load</span>
                    <strong className="text-white">{primaryNode.cpu?.loadPercent || 12}%</strong>
                  </div>
                  <div className="h-1.5 rounded-full bg-white/5 overflow-hidden">
                    <div
                      className="h-full bg-[#89ff00] rounded-full transition-all"
                      style={{ width: `${primaryNode.cpu?.loadPercent || 12}%` }}
                    />
                  </div>
                </div>

                <div>
                  <div className="flex justify-between text-[11px] mb-1">
                    <span className="text-neutral-400">Memory (RAM)</span>
                    <strong className="text-white">{primaryNode.memory?.usedPercent || 42}%</strong>
                  </div>
                  <div className="h-1.5 rounded-full bg-white/5 overflow-hidden">
                    <div
                      className="h-full bg-sky-400 rounded-full transition-all"
                      style={{ width: `${primaryNode.memory?.usedPercent || 42}%` }}
                    />
                  </div>
                </div>

                <div>
                  <div className="flex justify-between text-[11px] mb-1">
                    <span className="text-neutral-400">Disk Storage</span>
                    <strong className="text-white">{primaryNode.disk?.usedPercent || 36}%</strong>
                  </div>
                  <div className="h-1.5 rounded-full bg-white/5 overflow-hidden">
                    <div
                      className="h-full bg-purple-400 rounded-full transition-all"
                      style={{ width: `${primaryNode.disk?.usedPercent || 36}%` }}
                    />
                  </div>
                </div>
              </div>
            </div>

            {/* 1-Click VM Controls */}
            <div className="mt-5 pt-3 border-t border-white/5 flex items-center justify-between gap-2">
              <button
                onClick={() => handleNodeAction('my-mac', 'restart_node')}
                disabled={nodeActionLoading === 'my-mac_restart_node'}
                className="flex-1 flex items-center justify-center gap-1.5 py-1.5 px-2.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-[11px] font-mono text-white transition-all cursor-pointer disabled:opacity-50"
              >
                <RotateCcw
                  className={`w-3 h-3 ${
                    nodeActionLoading === 'my-mac_restart_node' ? 'animate-spin text-[#89ff00]' : ''
                  }`}
                />
                <span>Restart Swarm</span>
              </button>

              <button
                onClick={() => handleNodeAction('my-mac', 'flush_dns')}
                disabled={nodeActionLoading === 'my-mac_flush_dns'}
                className="flex items-center justify-center gap-1 py-1.5 px-2.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-[11px] font-mono text-neutral-300 transition-all cursor-pointer disabled:opacity-50"
                title="Flush edge DNS caches"
              >
                <RefreshCw className="w-3 h-3 text-purple-400" />
                <span>Flush DNS</span>
              </button>
            </div>
          </div>

          {/* VM 2: cos */}
          <div
            className="rounded-2xl p-5 border relative overflow-hidden flex flex-col justify-between"
            style={{
              backgroundColor: 'rgba(16, 17, 22, 0.9)',
              borderColor: 'rgba(56, 189, 248, 0.3)',
              boxShadow: '0 8px 30px rgba(0, 0, 0, 0.4)'
            }}
          >
            <div>
              <div className="flex items-center justify-between mb-3">
                <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-sky-500/15 text-sky-400 border border-sky-500/30">
                  PRIORITY 2: FAILSAFE SLOT 1
                </span>
                <span className="w-2 h-2 rounded-full bg-sky-400 animate-pulse" />
              </div>

              <div className="flex items-center justify-between mb-1">
                <h4 className="text-base font-bold text-white flex items-center gap-2">
                  <Cpu className="w-4 h-4 text-sky-400" />
                  <span>container-of-souls (cos)</span>
                </h4>
                <span className="text-[10px] font-mono text-neutral-400 px-2 py-0.5 rounded bg-white/5">
                  Oracle Cloud (UK)
                </span>
              </div>

              <div className="text-xs font-mono text-neutral-400 mb-4">
                Public: <strong className="text-white">129.154.32.125</strong> • VCN Tunnel: 10.0.0.195
                <div className="text-[10px] text-neutral-500 mt-0.5">
                  Tunnel: 127.0.0.1:9100 • Latency: ~2.4ms (Low Latency)
                </div>
              </div>

              {/* Resource Bars */}
              <div className="space-y-3 pt-3 border-t border-white/5 text-xs font-mono">
                <div>
                  <div className="flex justify-between text-[11px] mb-1">
                    <span className="text-neutral-400">CPU Load</span>
                    <strong className="text-white">{backupNode.cpu?.loadPercent || 6}%</strong>
                  </div>
                  <div className="h-1.5 rounded-full bg-white/5 overflow-hidden">
                    <div
                      className="h-full bg-[#89ff00] rounded-full transition-all"
                      style={{ width: `${backupNode.cpu?.loadPercent || 6}%` }}
                    />
                  </div>
                </div>

                <div>
                  <div className="flex justify-between text-[11px] mb-1">
                    <span className="text-neutral-400">Memory (RAM)</span>
                    <strong className="text-white">{backupNode.memory?.usedPercent || 34}%</strong>
                  </div>
                  <div className="h-1.5 rounded-full bg-white/5 overflow-hidden">
                    <div
                      className="h-full bg-sky-400 rounded-full transition-all"
                      style={{ width: `${backupNode.memory?.usedPercent || 34}%` }}
                    />
                  </div>
                </div>

                <div>
                  <div className="flex justify-between text-[11px] mb-1">
                    <span className="text-neutral-400">Disk Storage</span>
                    <strong className="text-white">{backupNode.disk?.usedPercent || 5}%</strong>
                  </div>
                  <div className="h-1.5 rounded-full bg-white/5 overflow-hidden">
                    <div
                      className="h-full bg-purple-400 rounded-full transition-all"
                      style={{ width: `${backupNode.disk?.usedPercent || 5}%` }}
                    />
                  </div>
                </div>
              </div>
            </div>

            {/* 1-Click VM Controls */}
            <div className="mt-5 pt-3 border-t border-white/5 flex items-center justify-between gap-2">
              <button
                onClick={() => handleNodeAction('cos', 'restart_node')}
                disabled={nodeActionLoading === 'cos_restart_node'}
                className="flex-1 flex items-center justify-center gap-1.5 py-1.5 px-2.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-[11px] font-mono text-white transition-all cursor-pointer disabled:opacity-50"
              >
                <RotateCcw
                  className={`w-3 h-3 ${
                    nodeActionLoading === 'cos_restart_node' ? 'animate-spin text-sky-400' : ''
                  }`}
                />
                <span>Restart Swarm</span>
              </button>

              <button
                onClick={() => handleNodeAction('cos', 'sync_db_cascade')}
                disabled={nodeActionLoading === 'cos_sync_db_cascade'}
                className="flex items-center justify-center gap-1 py-1.5 px-2.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-[11px] font-mono text-neutral-300 transition-all cursor-pointer disabled:opacity-50"
                title="Sync database down to cos"
              >
                <Database className="w-3 h-3 text-sky-400" />
                <span>Pull DB</span>
              </button>
            </div>
          </div>

          {/* VM 3: pro */}
          <div
            className="rounded-2xl p-5 border relative overflow-hidden flex flex-col justify-between"
            style={{
              backgroundColor: 'rgba(16, 17, 22, 0.9)',
              borderColor: 'rgba(245, 158, 11, 0.3)',
              boxShadow: '0 8px 30px rgba(0, 0, 0, 0.4)'
            }}
          >
            <div>
              <div className="flex items-center justify-between mb-3">
                <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-amber-500/15 text-amber-400 border border-amber-500/30">
                  PRIORITY 3: FAILSAFE SLOT 2
                </span>
                <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse" />
              </div>

              <div className="flex items-center justify-between mb-1">
                <h4 className="text-base font-bold text-white flex items-center gap-2">
                  <Server className="w-4 h-4 text-amber-400" />
                  <span>prometheus (pro)</span>
                </h4>
                <span className="text-[10px] font-mono text-neutral-400 px-2 py-0.5 rounded bg-white/5">
                  Google Cloud (GCP)
                </span>
              </div>

              <div className="text-xs font-mono text-neutral-400 mb-4">
                Public: <strong className="text-white">35.208.86.56</strong> • GCP us-central1-a
                <div className="text-[10px] text-neutral-500 mt-0.5">
                  Tunnel: 127.0.0.1:9101 • Dedicated Game & Swarm Replica
                </div>
              </div>

              {/* Resource Bars */}
              <div className="space-y-3 pt-3 border-t border-white/5 text-xs font-mono">
                <div>
                  <div className="flex justify-between text-[11px] mb-1">
                    <span className="text-neutral-400">CPU Load</span>
                    <strong className="text-white">{gcpNode.cpu?.loadPercent || 4}%</strong>
                  </div>
                  <div className="h-1.5 rounded-full bg-white/5 overflow-hidden">
                    <div
                      className="h-full bg-[#89ff00] rounded-full transition-all"
                      style={{ width: `${gcpNode.cpu?.loadPercent || 4}%` }}
                    />
                  </div>
                </div>

                <div>
                  <div className="flex justify-between text-[11px] mb-1">
                    <span className="text-neutral-400">Memory (RAM)</span>
                    <strong className="text-white">{gcpNode.memory?.usedPercent || 35}%</strong>
                  </div>
                  <div className="h-1.5 rounded-full bg-white/5 overflow-hidden">
                    <div
                      className="h-full bg-sky-400 rounded-full transition-all"
                      style={{ width: `${gcpNode.memory?.usedPercent || 35}%` }}
                    />
                  </div>
                </div>

                <div>
                  <div className="flex justify-between text-[11px] mb-1">
                    <span className="text-neutral-400">Disk Storage</span>
                    <strong className="text-white">{gcpNode.disk?.usedPercent || 28}%</strong>
                  </div>
                  <div className="h-1.5 rounded-full bg-white/5 overflow-hidden">
                    <div
                      className="h-full bg-purple-400 rounded-full transition-all"
                      style={{ width: `${gcpNode.disk?.usedPercent || 28}%` }}
                    />
                  </div>
                </div>
              </div>
            </div>

            {/* 1-Click VM Controls */}
            <div className="mt-5 pt-3 border-t border-white/5 flex items-center justify-between gap-2">
              <button
                onClick={() => handleNodeAction('pro', 'restart_node')}
                disabled={nodeActionLoading === 'pro_restart_node'}
                className="flex-1 flex items-center justify-center gap-1.5 py-1.5 px-2.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-[11px] font-mono text-white transition-all cursor-pointer disabled:opacity-50"
              >
                <RotateCcw
                  className={`w-3 h-3 ${
                    nodeActionLoading === 'pro_restart_node' ? 'animate-spin text-amber-400' : ''
                  }`}
                />
                <span>Restart Swarm</span>
              </button>

              <button
                onClick={() => handleNodeAction('pro', 'sync_db_cascade')}
                disabled={nodeActionLoading === 'pro_sync_db_cascade'}
                className="flex items-center justify-center gap-1 py-1.5 px-2.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-[11px] font-mono text-neutral-300 transition-all cursor-pointer disabled:opacity-50"
                title="Sync database to GCP"
              >
                <Database className="w-3 h-3 text-amber-400" />
                <span>Pull DB</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* ──────────────────────────────────────────────────────────── */}
      {/* 4. SEGMENT NAVIGATION & SERVICE FILTERS */}
      {/* ──────────────────────────────────────────────────────────── */}
      <div
        className="flex flex-col xl:flex-row xl:items-center justify-between gap-4 p-3 rounded-2xl backdrop-blur-xl"
        style={{
          backgroundColor: 'rgba(18, 19, 24, 0.75)',
          border: '1px solid rgba(255, 255, 255, 0.08)'
        }}
      >
        {/* Node View Selector */}
        <div className="flex items-center gap-1.5 flex-wrap">
          <button
            onClick={() => setNodeFilter('all')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-xl text-xs font-mono transition-all cursor-pointer ${
              nodeFilter === 'all'
                ? 'bg-white/15 text-white font-bold shadow-sm border border-white/20'
                : 'text-neutral-400 hover:text-white hover:bg-white/5 border border-transparent'
            }`}
          >
            <Boxes className="w-3.5 h-3.5" />
            <span>All Nodes ({services.length})</span>
          </button>

          <button
            onClick={() => setNodeFilter('primary')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-xl text-xs font-mono transition-all cursor-pointer ${
              nodeFilter === 'primary'
                ? 'bg-[#89ff00]/20 text-[#89ff00] font-bold border border-[#89ff00]/40 shadow-sm'
                : 'text-neutral-400 hover:text-[#89ff00] hover:bg-[#89ff00]/5 border border-transparent'
            }`}
          >
            <Server className="w-3.5 h-3.5" />
            <span>Priority 1: my-mac</span>
          </button>

          <button
            onClick={() => setNodeFilter('backup')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-xl text-xs font-mono transition-all cursor-pointer ${
              nodeFilter === 'backup'
                ? 'bg-sky-500/20 text-sky-400 font-bold border border-sky-500/40 shadow-sm'
                : 'text-neutral-400 hover:text-sky-400 hover:bg-sky-500/5 border border-transparent'
            }`}
          >
            <Cpu className="w-3.5 h-3.5" />
            <span>Priority 2: cos</span>
          </button>

          <button
            onClick={() => setNodeFilter('gcp')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-xl text-xs font-mono transition-all cursor-pointer ${
              nodeFilter === 'gcp'
                ? 'bg-amber-500/20 text-amber-400 font-bold border border-amber-500/40 shadow-sm'
                : 'text-neutral-400 hover:text-amber-400 hover:bg-amber-500/5 border border-transparent'
            }`}
          >
            <Server className="w-3.5 h-3.5" />
            <span>Priority 3: pro</span>
          </button>

          <button
            onClick={() => setNodeFilter('both')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-xl text-xs font-mono transition-all cursor-pointer ${
              nodeFilter === 'both'
                ? 'bg-purple-500/20 text-purple-300 font-bold border border-purple-500/40 shadow-sm'
                : 'text-neutral-400 hover:text-purple-300 hover:bg-purple-500/5 border border-transparent'
            }`}
          >
            <Layers className="w-3.5 h-3.5" />
            <span>Swarm Fabric</span>
          </button>
        </div>

        {/* Category & Status Filter */}
        <div className="flex items-center gap-2.5 flex-wrap shrink-0">
          {/* Category Dropdown/Pills */}
          <div className="flex items-center p-1 rounded-xl bg-white/5 border border-white/5 text-xs font-mono">
            <button
              onClick={() => setCategoryFilter('all')}
              className={`px-2.5 py-1 rounded-lg transition-all cursor-pointer ${
                categoryFilter === 'all'
                  ? 'bg-white/10 text-white font-semibold'
                  : 'text-neutral-400 hover:text-white'
              }`}
            >
              All Categories
            </button>
            <button
              onClick={() => setCategoryFilter('core')}
              className={`px-2.5 py-1 rounded-lg transition-all cursor-pointer ${
                categoryFilter === 'core'
                  ? 'bg-[#89ff00]/20 text-[#89ff00] font-bold'
                  : 'text-neutral-400 hover:text-[#89ff00]'
              }`}
            >
              Core Web
            </button>
            <button
              onClick={() => setCategoryFilter('mail')}
              className={`px-2.5 py-1 rounded-lg transition-all cursor-pointer ${
                categoryFilter === 'mail'
                  ? 'bg-sky-500/20 text-sky-400 font-bold'
                  : 'text-neutral-400 hover:text-sky-400'
              }`}
            >
              Mail
            </button>
            <button
              onClick={() => setCategoryFilter('desktop')}
              className={`px-2.5 py-1 rounded-lg transition-all cursor-pointer ${
                categoryFilter === 'desktop'
                  ? 'bg-purple-500/20 text-purple-300 font-bold'
                  : 'text-neutral-400 hover:text-purple-300'
              }`}
            >
              Desktops
            </button>
            <button
              onClick={() => setCategoryFilter('game')}
              className={`px-2.5 py-1 rounded-lg transition-all cursor-pointer ${
                categoryFilter === 'game'
                  ? 'bg-rose-500/20 text-rose-400 font-bold'
                  : 'text-neutral-400 hover:text-rose-400'
              }`}
            >
              Games
            </button>
          </div>

          {/* Status Filter */}
          <div className="flex items-center p-1 rounded-xl bg-white/5 border border-white/5 text-xs font-mono">
            <button
              onClick={() => setServingFilter('all')}
              className={`px-2.5 py-1 rounded-lg transition-all cursor-pointer ${
                servingFilter === 'all'
                  ? 'bg-white/10 text-white font-semibold'
                  : 'text-neutral-400 hover:text-white'
              }`}
            >
              All Statuses
            </button>
            <button
              onClick={() => setServingFilter('serving')}
              className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg transition-all cursor-pointer ${
                servingFilter === 'serving'
                  ? 'bg-[#89ff00]/20 text-[#89ff00] font-bold'
                  : 'text-neutral-400 hover:text-[#89ff00]'
              }`}
            >
              <span className="w-1.5 h-1.5 rounded-full bg-[#89ff00] animate-pulse" />
              <span>Serving ({activeServingCount})</span>
            </button>
            <button
              onClick={() => setServingFilter('standby')}
              className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg transition-all cursor-pointer ${
                servingFilter === 'standby'
                  ? 'bg-sky-500/20 text-sky-400 font-bold'
                  : 'text-neutral-400 hover:text-sky-400'
              }`}
            >
              <span className="w-1.5 h-1.5 rounded-full bg-sky-400" />
              <span>Standby ({standbyCount})</span>
            </button>
          </div>
        </div>
      </div>

      {/* ──────────────────────────────────────────────────────────── */}
      {/* 5. ALL APPS & GAMES 1-CLICK ORCHESTRATION GRID */}
      {/* ──────────────────────────────────────────────────────────── */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Boxes className="w-4 h-4 text-sky-400" />
            <h3 className="text-sm font-mono font-bold uppercase tracking-wider text-white">
              Deployed Applications & Dedicated Game Engines ({visibleServices.length})
            </h3>
          </div>
          <span className="text-xs font-mono text-neutral-400">
            One-Click Start, Stop & Restart Controls
          </span>
        </div>

        {visibleServices.length === 0 ? (
          <div className="text-center py-12 rounded-2xl bg-white/[0.02] border border-white/5">
            <p className="text-sm font-mono text-neutral-400">
              No applications match the selected filter criteria.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {visibleServices.map((service) => (
              <ServiceCard
                key={service.id}
                service={service}
                onAction={onServiceAction}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
