import React from 'react';
import { motion } from 'framer-motion';

export default function MetricCard({
  title,
  value,
  subvalue,
  icon: Icon,
  percent,
  alertThreshold = 85,
  warningThreshold = 70,
  badge,
  badgeColor = 'lime'
}) {
  const isAlert = typeof percent === 'number' && percent >= alertThreshold;
  const isWarning = typeof percent === 'number' && percent >= warningThreshold && !isAlert;

  const barColor = isAlert
    ? 'bg-[#ff3366]'
    : isWarning
    ? 'bg-amber-400'
    : 'bg-[#89ff00]';

  const glowColor = isAlert
    ? 'rgba(255, 51, 102, 0.25)'
    : 'rgba(137, 255, 0, 0.2)';

  return (
    <motion.div
      whileHover={{ y: -2, transition: { duration: 0.2 } }}
      className="relative rounded-2xl border transition-all flex flex-col justify-between overflow-hidden group"
      style={{
        padding: '24px',
        minHeight: '160px',
        backgroundColor: 'rgba(18, 19, 22, 0.85)',
        borderColor: 'rgba(255, 255, 255, 0.09)',
        backdropFilter: 'blur(20px)',
        WebkitBackdropFilter: 'blur(20px)',
        boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.45)',
        boxSizing: 'border-box'
      }}
    >
      {/* Background Accent Subtle Glow */}
      <div
        className="absolute top-0 right-0 w-32 h-32 rounded-full blur-3xl pointer-events-none opacity-20 group-hover:opacity-30 transition-opacity"
        style={{ backgroundColor: isAlert ? '#ff3366' : '#89ff00' }}
      />

      <div style={{ width: '100%' }}>
        <div className="flex items-center justify-between gap-2 mb-3.5">
          <span className="text-xs font-mono font-semibold text-neutral-400 uppercase tracking-wider truncate">
            {title}
          </span>
          <div className="flex items-center gap-2">
            {badge && (
              <span
                className={`text-[10px] font-mono px-2.5 py-0.5 rounded-full font-bold border ${
                  badgeColor === 'crimson'
                    ? 'text-[#ff3366] bg-[#ff3366]/15 border-[#ff3366]/30'
                    : badgeColor === 'sky'
                    ? 'text-sky-400 bg-sky-400/15 border-sky-400/30'
                    : badgeColor === 'amber'
                    ? 'text-amber-400 bg-amber-400/15 border-amber-400/30'
                    : 'text-[#89ff00] bg-[#89ff00]/15 border-[#89ff00]/30'
                }`}
              >
                {badge}
              </span>
            )}
            {Icon && (
              <div
                className="p-2 rounded-xl text-neutral-300"
                style={{
                  backgroundColor: 'rgba(255, 255, 255, 0.05)',
                  border: '1px solid rgba(255, 255, 255, 0.08)'
                }}
              >
                <Icon className="w-4 h-4" />
              </div>
            )}
          </div>
        </div>

        <div className="flex items-baseline gap-2 mb-1.5">
          <span className="text-2xl sm:text-3xl font-bold font-mono tracking-tight text-white">
            {value}
          </span>
          {typeof percent === 'number' && (
            <span
              className={`text-xs font-mono font-bold ${
                isAlert ? 'text-[#ff3366]' : isWarning ? 'text-amber-400' : 'text-[#89ff00]'
              }`}
            >
              {percent}%
            </span>
          )}
        </div>

        {subvalue && (
          <p className="text-xs text-neutral-400 font-mono truncate">{subvalue}</p>
        )}
      </div>

      {typeof percent === 'number' && (
        <div
          className="mt-4 pt-3.5"
          style={{ borderTop: '1px solid rgba(255, 255, 255, 0.06)' }}
        >
          <div
            className="w-full rounded-full h-1.5 overflow-hidden"
            style={{ backgroundColor: 'rgba(255, 255, 255, 0.07)' }}
          >
            <div
              className={`h-full rounded-full transition-all duration-700 ${barColor}`}
              style={{
                width: `${Math.min(Math.max(percent, 0), 100)}%`,
                boxShadow: `0 0 10px ${glowColor}`
              }}
            />
          </div>
        </div>
      )}
    </motion.div>
  );
}
