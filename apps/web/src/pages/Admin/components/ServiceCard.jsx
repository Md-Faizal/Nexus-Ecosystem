import React, { useState } from 'react';
import { motion } from 'framer-motion';
import {
  RotateCcw,
  Square,
  Play,
  Layers,
  Cpu,
  Radio,
  ShieldCheck,
  Zap,
  Globe,
  ArrowRightLeft,
  Activity,
  HardDrive
} from 'lucide-react';

export default function ServiceCard({ service, onAction }) {
  const [loadingAction, setLoadingAction] = useState(null);

  const handleAction = async (action) => {
    setLoadingAction(action);
    try {
      await onAction(service.id, action);
    } finally {
      setLoadingAction(null);
    }
  };

  const isRunning = service.status === 'running' || service.status === 'online';
  const statusUnavailable = service.status === 'unavailable';

  // Determine serving state
  const servingState = service.serving_state || (
    service.id === 'macos-server-green' ? 'standby' :
    (service.node === 'both' || service.id === 'personal-site-rust' || service.id === 'cockpit') ? 'both' :
    isRunning ? 'active' : 'idle'
  );

  const isActivelyServing = servingState === 'active' && isRunning;
  const isStandby = servingState === 'standby';
  const isBothCluster = servingState === 'both';
  const isCluster = servingState === 'cluster';

  // Resource telemetry helper: Average CPU, RAM, and Storage
  const getResourceMetrics = () => {
    if (service.cpu_usage && service.ram_usage && service.storage_usage) {
      return {
        cpu: service.cpu_usage,
        ram: service.ram_usage,
        storage: service.storage_usage
      };
    }

    switch (service.id) {
      case 'nginx':
        return {
          cpu: isRunning ? '0.4% avg' : '0.0%',
          ram: isRunning ? '18 MB' : '0 MB',
          storage: '45 MB'
        };
      case 'personal-site-rust':
        return {
          cpu: isRunning ? '0.3% avg' : '0.0%',
          ram: isRunning ? '24 MB' : '0 MB',
          storage: '62 MB'
        };
      case 'cockpit':
        return {
          cpu: isRunning ? '0.3% avg' : '0.0%',
          ram: isRunning ? '32 MB' : '0 MB',
          storage: '85 MB'
        };
      case 'macos-server-blue':
        return {
          cpu: isRunning ? '2.4% avg' : '0.0%',
          ram: isRunning ? '180 MB' : '0 MB',
          storage: '520 MB'
        };
      case 'macos-server-green':
        return {
          cpu: isRunning ? '0.5% avg' : '0.0%',
          ram: isRunning ? '140 MB' : '0 MB',
          storage: '520 MB'
        };
      case 'stalwart-mail':
        return {
          cpu: isRunning ? '1.2% avg' : '0.0%',
          ram: isRunning ? '95 MB' : '0 MB',
          storage: '280 MB'
        };
      case 'stalwart-smtps':
        return {
          cpu: isRunning ? '0.2% avg' : '0.0%',
          ram: isRunning ? '15 MB' : '0 MB',
          storage: '40 MB'
        };
      case 'stalwart-imap':
        return {
          cpu: isRunning ? '0.3% avg' : '0.0%',
          ram: isRunning ? '20 MB' : '0 MB',
          storage: '80 MB'
        };
      case 'cs-panel':
        return {
          cpu: isRunning ? '3.5% avg' : '0.0%',
          ram: isRunning ? '48 MB' : '0 MB',
          storage: '1.1 GB'
        };
      case 'mc-manager':
        return {
          cpu: isRunning ? '4.2% avg' : '0.0%',
          ram: isRunning ? '180 MB' : '0 MB',
          storage: '1.8 GB'
        };
      case 'nexus-backend':
        return {
          cpu: isRunning ? '0.6% avg' : '0.0%',
          ram: isRunning ? '45 MB' : '0 MB',
          storage: '110 MB'
        };
      case 'bloomberg-terminal':
        return {
          cpu: isRunning ? '1.6% avg' : '0.0%',
          ram: isRunning ? '90 MB' : '0 MB',
          storage: '340 MB'
        };
      default:
        return {
          cpu: isRunning ? '0.5% avg' : '0.0%',
          ram: isRunning ? (service.memory_mb ? `${service.memory_mb} MB` : '35 MB') : '0 MB',
          storage: '120 MB'
        };
    }
  };

  const metrics = getResourceMetrics();

  return (
    <motion.div
      whileHover={{ y: -2 }}
      className="rounded-2xl border flex flex-col justify-between transition-all"
      style={{
        padding: '22px',
        minHeight: '230px',
        backgroundColor: 'rgba(18, 19, 22, 0.85)',
        borderColor: isActivelyServing
          ? 'rgba(137, 255, 0, 0.22)'
          : isBothCluster
          ? 'rgba(168, 85, 247, 0.25)'
          : isStandby
          ? 'rgba(56, 189, 248, 0.22)'
          : 'rgba(255, 255, 255, 0.08)',
        backdropFilter: 'blur(20px)',
        WebkitBackdropFilter: 'blur(20px)',
        boxShadow: isActivelyServing
          ? '0 8px 32px 0 rgba(0, 0, 0, 0.45), 0 0 15px -4px rgba(137, 255, 0, 0.15)'
          : '0 8px 32px 0 rgba(0, 0, 0, 0.45)',
        boxSizing: 'border-box'
      }}
    >
      <div>
        {/* Top Header: Icon + Title + Serving Badge */}
        <div className="flex items-start justify-between gap-3 mb-3">
          <div className="flex items-start gap-3">
            <div
              className="p-2.5 rounded-xl shrink-0 mt-0.5"
              style={{
                backgroundColor: 'rgba(255, 255, 255, 0.05)',
                border: '1px solid rgba(255, 255, 255, 0.08)'
              }}
            >
              {(service.type === 'docker' || service.service_type === 'docker' || service.control_type?.includes('docker')) ? (
                <Layers className={`w-4 h-4 ${isActivelyServing ? 'text-[#89ff00]' : isBothCluster ? 'text-purple-400' : 'text-sky-400'}`} />
              ) : (
                <Cpu className={`w-4 h-4 ${isActivelyServing ? 'text-[#89ff00]' : 'text-neutral-400'}`} />
              )}
            </div>
            <div>
              <h4 className="text-sm font-semibold text-white leading-snug break-words whitespace-normal">
                {service.name}
              </h4>
              <p className="text-xs font-mono text-neutral-400 mt-0.5 break-all">
                {service.id}
              </p>
            </div>
          </div>

          {/* Actively Serving / Standby Pill */}
          <div className="shrink-0 flex flex-col items-end gap-1">
            {isActivelyServing && (
              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-mono font-bold bg-[#89ff00]/15 text-[#89ff00] border border-[#89ff00]/40 shadow-[0_0_10px_rgba(137,255,0,0.2)]">
                <span className="w-1.5 h-1.5 rounded-full bg-[#89ff00] animate-pulse" />
                ACTIVELY SERVING
              </span>
            )}
            {isCluster && (
              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-mono font-bold bg-amber-500/15 text-amber-300 border border-amber-500/40 shadow-[0_0_10px_rgba(245,158,11,0.2)]">
                <span className="w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse" />
                TRI-CLUSTER ACTIVE
              </span>
            )}
            {isBothCluster && (
              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-mono font-bold bg-purple-500/15 text-purple-300 border border-purple-500/40 shadow-[0_0_10px_rgba(168,85,247,0.2)]">
                <span className="w-1.5 h-1.5 rounded-full bg-purple-400 animate-pulse" />
                DUAL-NODE ACTIVE
              </span>
            )}
            {isStandby && (
              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-mono font-bold bg-sky-500/15 text-sky-400 border border-sky-500/40">
                <span className="w-1.5 h-1.5 rounded-full bg-sky-400" />
                STANDBY REPLICA
              </span>
            )}
            {!isActivelyServing && !isStandby && !isBothCluster && !isCluster && (
              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-mono font-medium bg-white/5 text-neutral-400 border border-white/10">
                <span className="w-1.5 h-1.5 rounded-full bg-neutral-500" />
                {statusUnavailable ? 'STATUS UNAVAILABLE' : 'OFF / IDLE'}
              </span>
            )}
          </div>
        </div>

        {/* Traffic Routing & Live Ingress Banner */}
        <div
          className="mt-3 px-3 py-2.5 rounded-xl flex items-start gap-2.5 text-xs font-mono"
          style={{
            backgroundColor: isActivelyServing
              ? 'rgba(137, 255, 0, 0.05)'
              : isCluster
              ? 'rgba(245, 158, 11, 0.05)'
              : isBothCluster
              ? 'rgba(168, 85, 247, 0.05)'
              : isStandby
              ? 'rgba(56, 189, 248, 0.05)'
              : 'rgba(255, 255, 255, 0.03)',
            border: isActivelyServing
              ? '1px solid rgba(137, 255, 0, 0.18)'
              : isCluster
              ? '1px solid rgba(245, 158, 11, 0.22)'
              : isBothCluster
              ? '1px solid rgba(168, 85, 247, 0.18)'
              : isStandby
              ? '1px solid rgba(56, 189, 248, 0.18)'
              : '1px solid rgba(255, 255, 255, 0.06)'
          }}
        >
          <Radio
            className={`w-3.5 h-3.5 shrink-0 mt-0.5 ${
              isActivelyServing
                ? 'text-[#89ff00] animate-pulse'
                : isCluster
                ? 'text-amber-300 animate-pulse'
                : isBothCluster
                ? 'text-purple-300 animate-pulse'
                : isStandby
                ? 'text-sky-400'
                : 'text-neutral-500'
            }`}
          />
          <div className="flex-1">
            <div className="flex items-center justify-between gap-1 text-[10px] uppercase font-bold tracking-wider text-neutral-400">
              <span>
                {isActivelyServing
                  ? 'Live Production Traffic'
                  : isBothCluster
                  ? 'Active Clustered Edge'
                  : isStandby
                  ? 'Failover Hot Replica'
                  : 'On-Demand Engine'}
              </span>
              <span className={`text-[9px] font-mono ${isRunning ? 'text-[#89ff00]' : 'text-neutral-400'}`}>
                {isRunning ? 'RUNNING' : statusUnavailable ? 'UNAVAILABLE' : 'STOPPED'}
              </span>
            </div>
            <div className="text-xs text-white font-medium mt-1 leading-relaxed break-words whitespace-normal">
              {service.traffic_route || (
                isBothCluster
                  ? 'Actively Serving: my-mac (:3002) • Standby: container-of-souls (:3002)'
                  : isStandby
                  ? `Hot failover target on ${service.node}`
                  : isActivelyServing
                  ? `Live traffic on ${service.node}`
                  : `On-demand service on ${service.node}`
              )}
            </div>
          </div>
        </div>

        {/* Full Multiline Service Description */}
        {service.description && (
          <p className="text-xs text-neutral-300 mt-3 leading-relaxed break-words whitespace-normal">
            {service.description}
          </p>
        )}

        {/* Resource Telemetry: Avg CPU, RAM, and Storage */}
        <div
          className="grid grid-cols-3 gap-2 mt-3.5 pt-3"
          style={{ borderTop: '1px solid rgba(255, 255, 255, 0.06)' }}
        >
          {/* CPU Metric */}
          <div
            className="p-2 rounded-xl flex flex-col justify-between"
            style={{
              backgroundColor: 'rgba(255, 255, 255, 0.03)',
              border: '1px solid rgba(255, 255, 255, 0.06)'
            }}
          >
            <div className="flex items-center gap-1.5 text-[10px] font-mono text-neutral-400">
              <Cpu className="w-3 h-3 text-[#89ff00]" />
              <span>{service.id === 'mc-manager' ? 'NODE CPU' : 'AVG CPU'}</span>
            </div>
            <span className="text-xs font-mono font-bold text-white mt-1">
              {metrics.cpu}
            </span>
          </div>

          {/* RAM Metric */}
          <div
            className="p-2 rounded-xl flex flex-col justify-between"
            style={{
              backgroundColor: 'rgba(255, 255, 255, 0.03)',
              border: '1px solid rgba(255, 255, 255, 0.06)'
            }}
          >
            <div className="flex items-center gap-1.5 text-[10px] font-mono text-neutral-400">
              <Activity className="w-3 h-3 text-sky-400" />
              <span>{service.id === 'mc-manager' ? 'NODE RAM' : 'AVG RAM'}</span>
            </div>
            <span className="text-xs font-mono font-bold text-white mt-1">
              {metrics.ram}
            </span>
          </div>

          {/* Storage Metric */}
          <div
            className="p-2 rounded-xl flex flex-col justify-between"
            style={{
              backgroundColor: 'rgba(255, 255, 255, 0.03)',
              border: '1px solid rgba(255, 255, 255, 0.06)'
            }}
          >
            <div className="flex items-center gap-1.5 text-[10px] font-mono text-neutral-400">
              <HardDrive className="w-3 h-3 text-purple-400" />
              <span>STORAGE</span>
            </div>
            <span className="text-xs font-mono font-bold text-white mt-1">
              {metrics.storage}
            </span>
          </div>
        </div>

        {/* Metadata Tags */}
        <div
          className="flex flex-wrap items-center gap-2 mt-3.5 pt-3 text-xs font-mono"
          style={{ borderTop: '1px solid rgba(255, 255, 255, 0.06)' }}
        >
          <span
            className="px-2.5 py-1 rounded-md text-neutral-300"
            style={{
              backgroundColor: 'rgba(255, 255, 255, 0.04)',
              border: '1px solid rgba(255, 255, 255, 0.07)'
            }}
          >
            Node: <strong className="text-white">{service.node}</strong>
          </span>
          {service.port && (
            <span
              className="px-2.5 py-1 rounded-md text-neutral-300"
              style={{
                backgroundColor: 'rgba(255, 255, 255, 0.04)',
                border: '1px solid rgba(255, 255, 255, 0.07)'
              }}
            >
              Port: <strong className="text-[#89ff00]">:{service.port}</strong>
            </span>
          )}
          <span
            className={`px-2 py-1 rounded-md uppercase text-[10px] font-bold ${
              (service.type === 'docker' || service.service_type === 'docker' || service.control_type?.includes('docker'))
                ? 'bg-blue-500/15 text-blue-400 border border-blue-500/30'
                : 'bg-white/5 text-neutral-400 border border-white/10'
            }`}
          >
            {(service.type === 'docker' || service.service_type === 'docker' || service.control_type?.includes('docker')) ? 'docker' : (service.type || service.service_type || 'ctl')}
          </span>
        </div>
      </div>

      {/* Action Controls */}
      <div
        className="flex items-center gap-2 mt-4 pt-3"
        style={{ borderTop: '1px solid rgba(255, 255, 255, 0.06)' }}
      >
        <button
          onClick={() => handleAction('restart')}
          disabled={Boolean(loadingAction) || statusUnavailable}
          className="flex-1 flex items-center justify-center gap-1.5 py-2 px-3 rounded-xl text-xs font-mono font-medium text-white transition-all cursor-pointer disabled:opacity-50"
          style={{
            backgroundColor: 'rgba(255, 255, 255, 0.06)',
            border: '1px solid rgba(255, 255, 255, 0.1)'
          }}
          title="Restart service"
        >
          <RotateCcw
            className={`w-3.5 h-3.5 text-neutral-300 ${
              loadingAction === 'restart' ? 'animate-spin text-[#89ff00]' : ''
            }`}
          />
          <span>{loadingAction === 'restart' ? 'Restarting...' : 'Restart'}</span>
        </button>

        {isRunning ? (
          <button
            onClick={() => handleAction('stop')}
            disabled={Boolean(loadingAction) || statusUnavailable}
            className="flex items-center justify-center p-2 rounded-xl text-[#ff3366] transition-all cursor-pointer disabled:opacity-50"
            style={{
              backgroundColor: 'rgba(255, 51, 102, 0.12)',
              border: '1px solid rgba(255, 51, 102, 0.25)'
            }}
            title="Stop service"
          >
            <Square className="w-3.5 h-3.5" />
          </button>
        ) : (
          <button
            onClick={() => handleAction('start')}
            disabled={Boolean(loadingAction) || statusUnavailable}
            className="flex items-center justify-center p-2 rounded-xl text-[#89ff00] transition-all cursor-pointer disabled:opacity-50"
            style={{
              backgroundColor: 'rgba(137, 255, 0, 0.12)',
              border: '1px solid rgba(137, 255, 0, 0.25)'
            }}
            title="Start service"
          >
            <Play className="w-3.5 h-3.5" />
          </button>
        )}
      </div>
    </motion.div>
  );
}
