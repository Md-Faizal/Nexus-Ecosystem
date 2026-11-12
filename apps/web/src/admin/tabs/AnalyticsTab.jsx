import React from 'react';
import { motion } from 'framer-motion';
import { Users, Eye, Globe, UserCheck, Clock, Monitor } from 'lucide-react';
import MetricCard from '../components/MetricCard';

export default function AnalyticsTab({ analytics = {} }) {
  const totalViews = analytics.totalViews || 0;
  const uniqueVisitors = analytics.uniqueVisitors || 0;
  const recentVisitors = analytics.recentVisitors || [];

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
              Audience & Traffic Telemetry
            </span>
          </div>
          <h2 className="text-xl sm:text-2xl font-bold text-white tracking-tight">
            Portfolio Traffic Analytics
          </h2>
          <p className="text-xs sm:text-sm text-neutral-400 mt-1">
            Real-time tracking of page visits, unique client fingerprints, and access patterns.
          </p>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <MetricCard
          title="Total Page Views"
          value={totalViews.toLocaleString()}
          subvalue="All recorded page loads"
          icon={Eye}
          badge="HITS"
        />
        <MetricCard
          title="Unique Visitors"
          value={uniqueVisitors.toLocaleString()}
          subvalue="Distinct visitor sessions"
          icon={Users}
          badge="AUDIENCE"
          badgeColor="sky"
        />
        <MetricCard
          title="Avg Views per Visitor"
          value={uniqueVisitors > 0 ? (totalViews / uniqueVisitors).toFixed(1) : '1.0'}
          subvalue="Engagement density"
          icon={Globe}
          badge="RATIO"
        />
      </div>

      {/* Recent Visitors Table */}
      <div
        className="rounded-3xl backdrop-blur-xl overflow-hidden"
        style={{
          padding: '28px',
          backgroundColor: 'rgba(18, 19, 22, 0.85)',
          border: '1px solid rgba(255, 255, 255, 0.09)',
          boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.45)',
          boxSizing: 'border-box'
        }}
      >
        <h3 className="text-base font-semibold text-white mb-4">
          Recent Visitor Telemetry ({recentVisitors.length})
        </h3>

        {recentVisitors.length === 0 ? (
          <div className="p-8 text-center text-xs font-mono text-neutral-500">
            No recent visitor logs recorded yet.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs font-mono">
              <thead>
                <tr className="border-b border-white/10 text-neutral-400">
                  <th className="pb-3 pr-4 font-semibold uppercase tracking-wider">IP Address</th>
                  <th className="pb-3 px-4 font-semibold uppercase tracking-wider">Target Path</th>
                  <th className="pb-3 px-4 font-semibold uppercase tracking-wider">Client Platform</th>
                  <th className="pb-3 pl-4 font-semibold uppercase tracking-wider text-right">Timestamp</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {recentVisitors.slice(0, 25).map((v, i) => (
                  <tr key={i} className="hover:bg-white/[0.02] transition-colors">
                    <td className="py-3 pr-4 text-white font-medium">{v.ip || '—'}</td>
                    <td className="py-3 px-4 text-[#89ff00]">{v.path || '/'}</td>
                    <td className="py-3 px-4 text-neutral-400 max-w-xs truncate">
                      {v.user_agent || v.userAgent || 'Modern Web Client'}
                    </td>
                    <td className="py-3 pl-4 text-neutral-500 text-right">
                      {v.created_at || v.timestamp || 'Just now'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
