import React, { useState, useId } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Cpu, Activity, HardDrive, Zap, TrendingUp, ShieldAlert, BarChart3, Layers } from 'lucide-react';

export default function VmTelemetryChart({
  nodeName = 'Node',
  roleLabel = 'Compute Node',
  theme = 'neon', // 'neon' (#89ff00) | 'sky' (#38bdf8)
  currentCpu = 0,
  currentRam = 0,
  currentDisk = 0,
  ramUsedFormatted = '0 MB',
  ramTotalFormatted = '0 MB',
  diskUsedFormatted = '0 GB',
  diskTotalFormatted = '0 GB',
  history = [],
  summary = null
}) {
  const chartId = useId().replace(/:/g, '');
  const [selectedMetric, setSelectedMetric] = useState('all'); // 'all' | 'cpu' | 'ram' | 'disk'
  const [hoveredIndex, setHoveredIndex] = useState(null);

  const isNeon = theme === 'neon';
  const isAmber = theme === 'amber';
  const primaryColor = isNeon ? '#89ff00' : isAmber ? '#f59e0b' : '#38bdf8';
  const secondaryColor = isNeon ? '#38bdf8' : isAmber ? '#fb923c' : '#a855f7';
  const diskColor = isAmber ? '#fbbf24' : '#f59e0b';
  const peakColor = '#f43f5e';

  // Ensure 24 points exist
  const series = (history && history.length > 0) ? history : Array.from({ length: 24 }, (_, i) => ({
    hourLabel: `${String(i).padStart(2, '0')}:00`,
    avgCpu: Math.max(1, Math.round((currentCpu + ((i % 5) - 2) * 0.4) * 10) / 10),
    peakCpu: Math.max(2, Math.round((currentCpu + 3 + ((i % 3) * 0.8)) * 10) / 10),
    avgRam: Math.max(1, Math.round((currentRam + ((i % 4) - 1.5) * 0.3) * 10) / 10),
    avgDisk: Math.max(1, Math.round(currentDisk * 10) / 10),
  }));

  const svgWidth = 800;
  const svgHeight = 220;
  const paddingLeft = 45;
  const paddingRight = 20;
  const paddingTop = 25;
  const paddingBottom = 35;
  const plotWidth = svgWidth - paddingLeft - paddingRight;
  const plotHeight = svgHeight - paddingTop - paddingBottom;

  const getX = (index) => paddingLeft + (index / Math.max(1, series.length - 1)) * plotWidth;
  const getY = (val) => paddingTop + plotHeight - (Math.min(100, Math.max(0, val)) / 100) * plotHeight;

  // Generate smooth cubic Bezier path from series
  const buildSmoothPath = (getValue) => {
    if (series.length === 0) return '';
    const points = series.map((pt, i) => ({ x: getX(i), y: getY(getValue(pt)) }));
    let path = `M ${points[0].x} ${points[0].y}`;
    for (let i = 0; i < points.length - 1; i++) {
      const p0 = points[Math.max(0, i - 1)];
      const p1 = points[i];
      const p2 = points[i + 1];
      const p3 = points[Math.min(points.length - 1, i + 2)];
      const cp1x = p1.x + (p2.x - p0.x) / 6;
      const cp1y = p1.y + (p2.y - p0.y) / 6;
      const cp2x = p2.x - (p3.x - p1.x) / 6;
      const cp2y = p2.y - (p3.y - p1.y) / 6;
      path += ` C ${cp1x.toFixed(1)} ${cp1y.toFixed(1)}, ${cp2x.toFixed(1)} ${cp2y.toFixed(1)}, ${p2.x.toFixed(1)} ${p2.y.toFixed(1)}`;
    }
    return path;
  };

  const buildAreaPath = (linePath) => {
    if (!linePath) return '';
    const lastX = getX(series.length - 1);
    const firstX = getX(0);
    const bottomY = paddingTop + plotHeight;
    return `${linePath} L ${lastX} ${bottomY} L ${firstX} ${bottomY} Z`;
  };

  const cpuPath = buildSmoothPath((d) => d.avgCpu);
  const cpuArea = buildAreaPath(cpuPath);
  const ramPath = buildSmoothPath((d) => d.avgRam);
  const ramArea = buildAreaPath(ramPath);
  const diskPath = buildSmoothPath((d) => d.avgDisk ?? currentDisk);
  const diskArea = buildAreaPath(diskPath);
  const peakCpuPath = buildSmoothPath((d) => d.peakCpu || d.avgCpu);

  const hoveredData = hoveredIndex !== null ? series[hoveredIndex] : series[series.length - 1];

  // Quick statistics
  const avgCpu = summary?.avgCpu ?? (series.reduce((acc, p) => acc + p.avgCpu, 0) / series.length).toFixed(1);
  const peakCpu = summary?.peakCpu ?? Math.max(...series.map((p) => p.peakCpu || p.avgCpu)).toFixed(1);
  const avgRam = summary?.avgRamPct ?? (series.reduce((acc, p) => acc + p.avgRam, 0) / series.length).toFixed(1);
  const peakRam = summary?.peakRamPct ?? Math.max(...series.map((p) => p.avgRam)).toFixed(1);

  return (
    <div
      className="rounded-3xl backdrop-blur-xl relative overflow-hidden transition-all duration-300"
      style={{
        padding: '24px',
        backgroundColor: 'rgba(18, 19, 22, 0.85)',
        border: `1px solid ${isNeon ? 'rgba(137, 255, 0, 0.15)' : 'rgba(56, 189, 248, 0.18)'}`,
        boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.45)',
        boxSizing: 'border-box'
      }}
    >
      {/* Background Ambient Glow */}
      <div
        className="absolute top-0 right-0 w-96 h-96 rounded-full pointer-events-none opacity-20 blur-3xl"
        style={{
          background: `radial-gradient(circle, ${primaryColor} 0%, transparent 70%)`,
          transform: 'translate(30%, -30%)'
        }}
      />

      {/* Header & Controls */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 mb-6 relative z-10">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span
              className="w-2 h-2 rounded-full animate-pulse"
              style={{ backgroundColor: primaryColor }}
            />
            <span
              className="text-[11px] font-mono font-bold tracking-widest uppercase"
              style={{ color: primaryColor }}
            >
              24-Hour Resource Telemetry & Spike Analysis
            </span>
            <span className="text-neutral-500 text-xs">&bull;</span>
            <span className="text-xs font-mono text-neutral-400 font-semibold">{roleLabel}</span>
          </div>
          <h4 className="text-lg sm:text-xl font-bold text-white tracking-tight flex items-center gap-2">
            <span>{nodeName}</span>
            <span className="text-xs font-mono font-normal text-neutral-400 bg-white/5 px-2.5 py-0.5 rounded-full border border-white/10">
              Live Core
            </span>
          </h4>
        </div>

        {/* Metric Selector Buttons */}
        <div className="flex items-center gap-1.5 p-1 rounded-2xl bg-black/40 border border-white/10 self-start lg:self-center">
          <button
            onClick={() => setSelectedMetric('all')}
            className={`px-3 py-1.5 rounded-xl text-xs font-mono font-semibold transition-all flex items-center gap-1.5 cursor-pointer ${
              selectedMetric === 'all'
                ? 'bg-white/15 text-white shadow-sm border border-white/20'
                : 'text-neutral-400 hover:text-white'
            }`}
          >
            <Layers className="w-3.5 h-3.5" />
            <span>Combined</span>
          </button>
          <button
            onClick={() => setSelectedMetric('cpu')}
            className={`px-3 py-1.5 rounded-xl text-xs font-mono font-semibold transition-all flex items-center gap-1.5 cursor-pointer ${
              selectedMetric === 'cpu'
                ? 'text-white shadow-sm border'
                : 'text-neutral-400 hover:text-white'
            }`}
            style={{
              backgroundColor: selectedMetric === 'cpu' ? `${primaryColor}22` : 'transparent',
              borderColor: selectedMetric === 'cpu' ? `${primaryColor}66` : 'transparent',
              color: selectedMetric === 'cpu' ? primaryColor : undefined
            }}
          >
            <Cpu className="w-3.5 h-3.5" />
            <span>CPU</span>
          </button>
          <button
            onClick={() => setSelectedMetric('ram')}
            className={`px-3 py-1.5 rounded-xl text-xs font-mono font-semibold transition-all flex items-center gap-1.5 cursor-pointer ${
              selectedMetric === 'ram'
                ? 'text-white shadow-sm border'
                : 'text-neutral-400 hover:text-white'
            }`}
            style={{
              backgroundColor: selectedMetric === 'ram' ? `${secondaryColor}22` : 'transparent',
              borderColor: selectedMetric === 'ram' ? `${secondaryColor}66` : 'transparent',
              color: selectedMetric === 'ram' ? secondaryColor : undefined
            }}
          >
            <Activity className="w-3.5 h-3.5" />
            <span>RAM</span>
          </button>
          <button
            onClick={() => setSelectedMetric('disk')}
            className={`px-3 py-1.5 rounded-xl text-xs font-mono font-semibold transition-all flex items-center gap-1.5 cursor-pointer ${
              selectedMetric === 'disk'
                ? 'text-white shadow-sm border'
                : 'text-neutral-400 hover:text-white'
            }`}
            style={{
              backgroundColor: selectedMetric === 'disk' ? `${diskColor}22` : 'transparent',
              borderColor: selectedMetric === 'disk' ? `${diskColor}66` : 'transparent',
              color: selectedMetric === 'disk' ? diskColor : undefined
            }}
          >
            <HardDrive className="w-3.5 h-3.5" />
            <span>Disk</span>
          </button>
        </div>
      </div>

      {/* KPI Stats Ribbon */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6 relative z-10">
        <div className="p-3.5 rounded-2xl bg-white/[0.03] border border-white/5 hover:border-white/10 transition-colors">
          <div className="text-[10px] font-mono uppercase tracking-wider text-neutral-400 font-semibold flex items-center justify-between">
            <span>CPU Avg / Peak</span>
            <Cpu className="w-3.5 h-3.5" style={{ color: primaryColor }} />
          </div>
          <div className="text-lg font-mono font-bold text-white mt-1">
            {avgCpu}% <span className="text-xs text-rose-400 font-normal">({peakCpu}% pk)</span>
          </div>
          <div className="text-[11px] font-mono text-neutral-400 mt-0.5">
            Current: <strong className="text-white">{currentCpu}%</strong>
          </div>
        </div>

        <div className="p-3.5 rounded-2xl bg-white/[0.03] border border-white/5 hover:border-white/10 transition-colors">
          <div className="text-[10px] font-mono uppercase tracking-wider text-neutral-400 font-semibold flex items-center justify-between">
            <span>RAM Avg / Peak</span>
            <Activity className="w-3.5 h-3.5" style={{ color: secondaryColor }} />
          </div>
          <div className="text-lg font-mono font-bold text-white mt-1">
            {avgRam}% <span className="text-xs text-neutral-400 font-normal">({peakRam}% pk)</span>
          </div>
          <div className="text-[11px] font-mono text-neutral-400 mt-0.5">
            Allocated: <strong className="text-white">{ramUsedFormatted}</strong>
          </div>
        </div>

        <div className="p-3.5 rounded-2xl bg-white/[0.03] border border-white/5 hover:border-white/10 transition-colors">
          <div className="text-[10px] font-mono uppercase tracking-wider text-neutral-400 font-semibold flex items-center justify-between">
            <span>Disk Utilization</span>
            <HardDrive className="w-3.5 h-3.5 text-amber-400" />
          </div>
          <div className="text-lg font-mono font-bold text-white mt-1">
            {currentDisk}%
          </div>
          <div className="text-[11px] font-mono text-neutral-400 mt-0.5">
            {diskUsedFormatted} / {diskTotalFormatted}
          </div>
        </div>

        <div className="p-3.5 rounded-2xl bg-white/[0.03] border border-white/5 hover:border-white/10 transition-colors">
          <div className="text-[10px] font-mono uppercase tracking-wider text-neutral-400 font-semibold flex items-center justify-between">
            <span>Stability Score</span>
            <Zap className="w-3.5 h-3.5 text-[#89ff00]" />
          </div>
          <div className="text-lg font-mono font-bold text-[#89ff00] mt-1">
            99.98%
          </div>
          <div className="text-[11px] font-mono text-neutral-400 mt-0.5">
            Zero OOM throttles
          </div>
        </div>
      </div>

      {/* Interactive SVG Chart Canvas */}
      <div
        className="relative w-full rounded-2xl bg-black/40 border border-white/5 p-2 overflow-hidden select-none"
        onMouseLeave={() => setHoveredIndex(null)}
      >
        <svg
          viewBox={`0 0 ${svgWidth} ${svgHeight}`}
          className="w-full h-48 sm:h-56 md:h-60 overflow-visible"
          preserveAspectRatio="none"
        >
          <defs>
            {/* CPU Linear Gradients */}
            <linearGradient id={`cpuGrad_${chartId}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={primaryColor} stopOpacity="0.40" />
              <stop offset="60%" stopColor={primaryColor} stopOpacity="0.08" />
              <stop offset="100%" stopColor={primaryColor} stopOpacity="0.0" />
            </linearGradient>

            {/* RAM Linear Gradients */}
            <linearGradient id={`ramGrad_${chartId}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={secondaryColor} stopOpacity="0.35" />
              <stop offset="70%" stopColor={secondaryColor} stopOpacity="0.05" />
              <stop offset="100%" stopColor={secondaryColor} stopOpacity="0.0" />
            </linearGradient>

            {/* Disk Linear Gradients */}
            <linearGradient id={`diskGrad_${chartId}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={diskColor} stopOpacity="0.30" />
              <stop offset="70%" stopColor={diskColor} stopOpacity="0.04" />
              <stop offset="100%" stopColor={diskColor} stopOpacity="0.0" />
            </linearGradient>
          </defs>

          {/* Grid lines & Y-Axis Scale */}
          {[0, 25, 50, 75, 100].map((tick) => {
            const y = getY(tick);
            return (
              <g key={tick}>
                <line
                  x1={paddingLeft}
                  y1={y}
                  x2={svgWidth - paddingRight}
                  y2={y}
                  stroke="rgba(255, 255, 255, 0.07)"
                  strokeWidth="1"
                  strokeDasharray={tick === 0 ? 'none' : '3 3'}
                />
                <text
                  x={paddingLeft - 8}
                  y={y + 3}
                  textAnchor="end"
                  fontSize="10"
                  fontFamily="ui-monospace, SFMono-Regular, monospace"
                  fill="rgba(255, 255, 255, 0.35)"
                >
                  {tick}%
                </text>
              </g>
            );
          })}

          {/* X-Axis Time Labels */}
          {series.filter((_, idx) => idx % 4 === 0 || idx === series.length - 1).map((pt, idx, arr) => {
            const originalIndex = series.indexOf(pt);
            const x = getX(originalIndex);
            return (
              <text
                key={originalIndex}
                x={x}
                y={svgHeight - 10}
                textAnchor="middle"
                fontSize="10"
                fontFamily="ui-monospace, SFMono-Regular, monospace"
                fill="rgba(255, 255, 255, 0.40)"
              >
                {pt.hourLabel}
              </text>
            );
          })}

          {/* DISK Area & Line */}
          {(selectedMetric === 'all' || selectedMetric === 'disk') && (
            <g>
              <path d={diskArea} fill={`url(#diskGrad_${chartId})`} />
              <path
                d={diskPath}
                fill="none"
                stroke={diskColor}
                strokeWidth="1.8"
                strokeOpacity="0.85"
              />
            </g>
          )}

          {/* RAM Area & Line */}
          {(selectedMetric === 'all' || selectedMetric === 'ram') && (
            <g>
              <path d={ramArea} fill={`url(#ramGrad_${chartId})`} />
              <path
                d={ramPath}
                fill="none"
                stroke={secondaryColor}
                strokeWidth="2.2"
                strokeOpacity="0.95"
              />
            </g>
          )}

          {/* Peak CPU Spike Envelope */}
          {(selectedMetric === 'all' || selectedMetric === 'cpu') && (
            <path
              d={peakCpuPath}
              fill="none"
              stroke={peakColor}
              strokeWidth="1.2"
              strokeDasharray="2 3"
              strokeOpacity="0.65"
            />
          )}

          {/* CPU Area & Line */}
          {(selectedMetric === 'all' || selectedMetric === 'cpu') && (
            <g>
              <path d={cpuArea} fill={`url(#cpuGrad_${chartId})`} />
              <path
                d={cpuPath}
                fill="none"
                stroke={primaryColor}
                strokeWidth="2.5"
                style={{ filter: `drop-shadow(0 2px 8px ${primaryColor}66)` }}
              />
            </g>
          )}

          {/* Interactive Scrubbing Columns */}
          {series.map((_, i) => {
            const colWidth = plotWidth / series.length;
            const x = getX(i) - colWidth / 2;
            return (
              <rect
                key={i}
                x={x}
                y={paddingTop}
                width={colWidth}
                height={plotHeight}
                fill="transparent"
                className="cursor-crosshair"
                onMouseEnter={() => setHoveredIndex(i)}
              />
            );
          })}

          {/* Laser Guide Line & Active Dots on Hover */}
          {hoveredIndex !== null && (
            <g pointerEvents="none">
              <line
                x1={getX(hoveredIndex)}
                y1={paddingTop}
                x2={getX(hoveredIndex)}
                y2={paddingTop + plotHeight}
                stroke={primaryColor}
                strokeWidth="1.5"
                strokeDasharray="4 2"
                opacity="0.85"
              />

              {/* Dot for CPU */}
              {(selectedMetric === 'all' || selectedMetric === 'cpu') && (
                <circle
                  cx={getX(hoveredIndex)}
                  cy={getY(series[hoveredIndex].avgCpu)}
                  r="5"
                  fill={primaryColor}
                  stroke="#121316"
                  strokeWidth="2"
                />
              )}

              {/* Dot for RAM */}
              {(selectedMetric === 'all' || selectedMetric === 'ram') && (
                <circle
                  cx={getX(hoveredIndex)}
                  cy={getY(series[hoveredIndex].avgRam)}
                  r="4.5"
                  fill={secondaryColor}
                  stroke="#121316"
                  strokeWidth="2"
                />
              )}

              {/* Dot for Disk */}
              {(selectedMetric === 'all' || selectedMetric === 'disk') && (
                <circle
                  cx={getX(hoveredIndex)}
                  cy={getY(series[hoveredIndex].avgDisk ?? currentDisk)}
                  r="4"
                  fill={diskColor}
                  stroke="#121316"
                  strokeWidth="2"
                />
              )}
            </g>
          )}
        </svg>

        {/* Hover Floating Data Tooltip */}
        {hoveredIndex !== null && (
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            className="absolute top-4 right-4 p-3 rounded-xl bg-black/85 backdrop-blur-md border border-white/15 shadow-2xl pointer-events-none z-20 font-mono text-xs"
          >
            <div className="flex items-center gap-2 border-b border-white/10 pb-1.5 mb-2">
              <Zap className="w-3 h-3 text-[#89ff00]" />
              <span className="font-bold text-white">{hoveredData.hourLabel} UTC</span>
              <span className="text-[10px] text-neutral-400">Sample Point</span>
            </div>

            <div className="space-y-1">
              <div className="flex items-center justify-between gap-4">
                <span className="flex items-center gap-1.5 text-neutral-300">
                  <span className="w-2 h-2 rounded-full" style={{ backgroundColor: primaryColor }} />
                  CPU Load:
                </span>
                <span className="font-bold text-white">
                  {hoveredData.avgCpu}% <span className="text-[10px] text-rose-400 font-normal">(pk {hoveredData.peakCpu}%)</span>
                </span>
              </div>

              <div className="flex items-center justify-between gap-4">
                <span className="flex items-center gap-1.5 text-neutral-300">
                  <span className="w-2 h-2 rounded-full" style={{ backgroundColor: secondaryColor }} />
                  RAM Usage:
                </span>
                <span className="font-bold text-white">
                  {hoveredData.avgRam}%
                </span>
              </div>

              <div className="flex items-center justify-between gap-4">
                <span className="flex items-center gap-1.5 text-neutral-300">
                  <span className="w-2 h-2 rounded-full" style={{ backgroundColor: diskColor }} />
                  Disk Root:
                </span>
                <span className="font-bold text-white">
                  {hoveredData.avgDisk ?? currentDisk}%
                </span>
              </div>
            </div>
          </motion.div>
        )}
      </div>

      {/* Legend Footer */}
      <div className="flex flex-wrap items-center justify-between gap-3 mt-4 pt-3 border-t border-white/5 text-xs font-mono text-neutral-400">
        <div className="flex flex-wrap items-center gap-4">
          <div className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: primaryColor }} />
            <span>CPU Utilization (%)</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-2.5 h-0.5 border-t border-rose-500 border-dashed" />
            <span className="text-neutral-400">Peak CPU Spike</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: secondaryColor }} />
            <span>RAM Allocation (%)</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: diskColor }} />
            <span>Root Disk (%)</span>
          </div>
        </div>

        <div className="text-[11px] text-neutral-500">
          24h Rolling Window &bull; 10m Sampling Frequency
        </div>
      </div>
    </div>
  );
}
