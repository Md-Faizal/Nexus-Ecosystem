import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Server,
  Activity,
  Bot,
  Zap,
  Mail,
  FolderGit2,
  UserCheck,
  Users,
  Shield,
  Sliders,
  RefreshCw,
  LogOut,
  ExternalLink,
  Menu,
  X,
  Eye
} from 'lucide-react';
import { MONOGRAM_PATHS } from './Admin';

export default function AdminLayout({
  activeTab,
  onTabChange,
  onExit,
  onRefresh,
  refreshing,
  onOpenReportPreview,
  inboxCount = 0,
  projectCount = 0,
  children
}) {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const tabs = [
    { id: 'cluster', number: '01', label: 'Cluster', icon: Server, desc: 'Hardware & nodes' },
    { id: 'services', number: '02', label: 'Services', icon: Activity, desc: 'Containers & units' },
    { id: 'ai-studio', number: '03', label: 'Fleet AI', icon: Bot, desc: 'Local LLM Studio & VRAM' },
    { id: 'reports', number: '04', label: 'Supervision', icon: Zap, desc: '24h health & email' },
    { id: 'messages', number: '05', label: 'Inbox', icon: Mail, desc: 'Client inquiries', badge: inboxCount },
    { id: 'portfolio', number: '06', label: 'Portfolio', icon: FolderGit2, desc: 'Curated projects', badge: projectCount },
    { id: 'profile', number: '07', label: 'Profile', icon: UserCheck, desc: 'Avatars & vault' },
    { id: 'analytics', number: '08', label: 'Analytics', icon: Users, desc: 'Audience & traffic' },
    { id: 'security', number: '09', label: 'Security', icon: Shield, desc: 'Firewall & bans' },
    { id: 'settings', number: '10', label: 'Settings', icon: Sliders, desc: 'Engine preferences' }
  ];

  const handleTabSelect = (tabId) => {
    onTabChange(tabId);
    setMobileMenuOpen(false);
  };

  return (
    <div className="min-h-screen bg-[#000000] text-[#f8fafc] flex flex-col font-sans selection:bg-[#89ff00] selection:text-black">
      {/* ─── STICKY GLASSMORPHIC HEADER ─── */}
      <header className="sticky top-0 z-40 bg-[#070709]/80 backdrop-blur-2xl border-b border-white/10 transition-all">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-12 h-20 flex items-center justify-between gap-4">

          {/* Brand & Monogram */}
          <div className="flex items-center gap-3 sm:gap-4 min-w-0">
            <a
              href="/"
              className="w-10 h-8 flex items-center justify-center transition-transform hover:scale-105 shrink-0"
              title="Return to site"
            >
              <svg viewBox="0 0 172 80" className="w-10 h-7 drop-shadow-md">
                <path d={MONOGRAM_PATHS.wingDown} fill="#ff3366" />
                <path d={MONOGRAM_PATHS.wingUp} fill="#89ff00" />
                <polygon points={MONOGRAM_PATHS.centerPoly} fill="#111113" />
                <path d={MONOGRAM_PATHS.centerWhite} fill="#ffffff" />
              </svg>
            </a>

            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="font-mono text-xs sm:text-sm font-bold uppercase tracking-[0.16em] text-white truncate">
                  MOHAMMAD FAIZAL
                </span>
                <span className="font-mono text-[10px] sm:text-xs font-bold uppercase tracking-wider text-[#89ff00] hidden sm:inline">
                  // ATELIER CONSOLE
                </span>
              </div>
              <div className="flex items-center gap-2.5 text-[11px] font-mono text-neutral-400 mt-0.5 truncate">
                <span className="flex items-center gap-1.5">
                  <span className="size-1.5 rounded-full bg-[#89ff00] animate-pulse" />
                  <span className="text-white">my-mac</span>
                </span>
                <span className="opacity-30">|</span>
                <span className="flex items-center gap-1.5">
                  <span className="size-1.5 rounded-full bg-sky-400" />
                  <span className="text-white">container-of-souls</span>
                </span>
              </div>
            </div>
          </div>

          {/* Quick Header Actions */}
          <div className="flex items-center gap-2 sm:gap-3">
            <button
              onClick={onOpenReportPreview}
              className="hidden lg:flex items-center gap-2 px-3.5 py-2 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-mono font-medium text-white transition-all cursor-pointer"
            >
              <Eye className="w-3.5 h-3.5 text-[#89ff00]" />
              <span>Report Preview</span>
            </button>

            <a
              href="https://cockpit.faizal.uk"
              target="_blank"
              rel="noopener noreferrer"
              className="hidden md:flex items-center gap-1.5 px-3 py-2 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-mono font-medium text-neutral-300 hover:text-white transition-all"
            >
              <span>Cockpit</span>
              <ExternalLink className="w-3 h-3 text-neutral-400" />
            </a>

            <button
              onClick={onRefresh}
              disabled={refreshing}
              className="p-2 sm:p-2.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-neutral-300 hover:text-white transition-all cursor-pointer disabled:opacity-50"
              title="Refresh Telemetry"
            >
              <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin text-[#89ff00]' : ''}`} />
            </button>

            <button
              onClick={onExit}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-[#ff3366]/10 hover:bg-[#ff3366]/20 border border-[#ff3366]/30 text-[#ff3366] text-xs font-mono font-semibold transition-all cursor-pointer"
            >
              <LogOut className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Exit</span>
            </button>

            {/* Mobile Hamburger Menu Toggle */}
            <button
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="md:hidden p-2 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-neutral-300 hover:text-white transition-all cursor-pointer"
              aria-label="Toggle navigation drawer"
            >
              {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
            </button>
          </div>
        </div>

        {/* ─── DESKTOP HORIZONTAL PILL NAVIGATION ─── */}
        <div className="hidden md:flex max-w-7xl mx-auto px-4 sm:px-6 lg:px-12 gap-1.5 overflow-x-auto pb-3 scrollbar-none">
          {tabs.map((tab) => {
            const active = activeTab === tab.id;
            const Icon = tab.icon;
            return (
              <button
                key={tab.id}
                onClick={() => handleTabSelect(tab.id)}
                className={`flex items-center gap-2 px-3.5 py-1.5 rounded-xl text-xs font-mono font-medium transition-all whitespace-nowrap cursor-pointer relative ${active
                  ? 'bg-[#89ff00]/15 text-[#89ff00] border border-[#89ff00]/30 shadow-[0_0_20px_rgba(137,255,0,0.15)] font-semibold'
                  : 'text-neutral-400 hover:text-white hover:bg-white/5 border border-transparent'
                  }`}
              >
                <span className={`text-[10px] ${active ? 'text-[#89ff00]' : 'text-neutral-500'}`}>
                  {tab.number}
                </span>
                <Icon className="w-3.5 h-3.5" />
                <span>{tab.label}</span>
                {typeof tab.badge === 'number' && tab.badge > 0 && (
                  <span
                    className={`px-1.5 py-0.2 rounded-full text-[10px] font-bold ${active ? 'bg-[#89ff00] text-black' : 'bg-white/10 text-white'
                      }`}
                  >
                    {tab.badge}
                  </span>
                )}
              </button>
            );
          })}
        </div>
      </header>

      {/* ─── MOBILE SLIDE-OVER NAVIGATION DRAWER ─── */}
      <AnimatePresence>
        {mobileMenuOpen && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 md:hidden bg-black/80 backdrop-blur-xl flex flex-col justify-end"
            onClick={() => setMobileMenuOpen(false)}
          >
            <motion.div
              initial={{ y: '100%' }}
              animate={{ y: 0 }}
              exit={{ y: '100%' }}
              transition={{ type: 'spring', damping: 25, stiffness: 200 }}
              className="w-full bg-[#0d0d10] border-t border-white/10 rounded-t-3xl p-6 max-h-[85vh] overflow-y-auto space-y-4"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-center justify-between pb-3 border-b border-white/10">
                <span className="text-xs font-mono font-semibold text-neutral-400 uppercase tracking-widest">
                  Navigation Menu
                </span>
                <button
                  onClick={() => setMobileMenuOpen(false)}
                  className="p-1 text-neutral-400 hover:text-white"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                {tabs.map((tab) => {
                  const active = activeTab === tab.id;
                  const Icon = tab.icon;
                  return (
                    <button
                      key={tab.id}
                      onClick={() => handleTabSelect(tab.id)}
                      className={`flex items-center justify-between p-3.5 rounded-2xl border text-left transition-all cursor-pointer ${active
                        ? 'bg-[#89ff00]/15 border-[#89ff00]/40 text-[#89ff00]'
                        : 'bg-white/5 border-white/5 text-neutral-300 hover:bg-white/10'
                        }`}
                    >
                      <div className="flex items-center gap-3">
                        <div
                          className={`p-2 rounded-xl ${active ? 'bg-[#89ff00]/20 text-[#89ff00]' : 'bg-white/5 text-neutral-400'
                            }`}
                        >
                          <Icon className="w-4 h-4" />
                        </div>
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="text-xs font-mono font-semibold text-white">
                              {tab.label}
                            </span>
                            {typeof tab.badge === 'number' && tab.badge > 0 && (
                              <span className="px-1.5 py-0.2 rounded-full text-[10px] font-bold bg-[#89ff00] text-black">
                                {tab.badge}
                              </span>
                            )}
                          </div>
                          <p className="text-[11px] text-neutral-500 font-mono mt-0.5">{tab.desc}</p>
                        </div>
                      </div>
                      <span className="text-xs font-mono text-neutral-500">{tab.number}</span>
                    </button>
                  );
                })}
              </div>

              <div className="pt-2">
                <button
                  onClick={onOpenReportPreview}
                  className="w-full py-3 rounded-2xl bg-white/5 border border-white/10 text-xs font-mono text-white flex items-center justify-center gap-2"
                >
                  <Eye className="w-4 h-4 text-[#89ff00]" />
                  <span>Open 24h Supervision Report</span>
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* ─── MAIN DASHBOARD CONTENT AREA ─── */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-12 py-6 sm:py-8">
        {children}
      </main>

      {/* ─── FOOTER ─── */}
      <footer className="border-t border-white/5 py-6 text-center text-xs font-mono text-neutral-500">
        MOHAMMAD FAIZAL &bull; ATELIER CLUSTER TELEMETRY &bull; PURE RUST AXUM
      </footer>
    </div>
  );
}
