import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Send, Monitor, Smartphone, RefreshCw, CheckCircle2 } from 'lucide-react';

export default function ReportPreviewModal({
  isOpen,
  onClose,
  html,
  loading,
  onRefresh,
  onSend,
  sending,
  recipient
}) {
  const [viewport, setViewport] = useState('desktop');

  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-[99999] flex items-center justify-center p-3 sm:p-6 bg-black/80 backdrop-blur-md">
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: 10 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 10 }}
          transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
          className="relative w-full max-w-5xl h-[90vh] bg-[#0c0c0e] border border-white/10 rounded-2xl sm:rounded-3xl shadow-2xl flex flex-col overflow-hidden"
        >
          {/* Header */}
          <div className="px-5 py-4 border-b border-white/10 flex items-center justify-between gap-3 bg-neutral-900/40">
            <div className="flex items-center gap-3 min-w-0">
              <span className="w-2.5 h-2.5 rounded-full bg-[#89ff00] animate-pulse shrink-0" />
              <div>
                <h3 className="text-sm sm:text-base font-semibold text-white leading-tight">
                  Daily Infrastructure Digest Preview
                </h3>
                <p className="text-xs font-mono text-neutral-400 truncate mt-0.5">
                  Recipient: <span className="text-white">{recipient || 'me@faizal.uk'}</span>
                </p>
              </div>
            </div>

            {/* Viewport Toggles & Actions */}
            <div className="flex items-center gap-2">
              <div className="hidden sm:flex items-center p-1 rounded-xl bg-white/5 border border-white/5">
                <button
                  onClick={() => setViewport('desktop')}
                  className={`p-1.5 rounded-lg text-xs font-mono flex items-center gap-1.5 transition-all cursor-pointer ${
                    viewport === 'desktop'
                      ? 'bg-[#89ff00]/15 text-[#89ff00] font-semibold'
                      : 'text-neutral-400 hover:text-white'
                  }`}
                  title="Desktop View"
                >
                  <Monitor className="w-4 h-4" />
                  <span>Desktop</span>
                </button>
                <button
                  onClick={() => setViewport('mobile')}
                  className={`p-1.5 rounded-lg text-xs font-mono flex items-center gap-1.5 transition-all cursor-pointer ${
                    viewport === 'mobile'
                      ? 'bg-[#89ff00]/15 text-[#89ff00] font-semibold'
                      : 'text-neutral-400 hover:text-white'
                  }`}
                  title="Mobile View"
                >
                  <Smartphone className="w-4 h-4" />
                  <span>Mobile</span>
                </button>
              </div>

              <button
                onClick={onRefresh}
                disabled={loading}
                className="p-2 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-neutral-300 hover:text-white transition-all cursor-pointer disabled:opacity-50"
                title="Reload Preview"
              >
                <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-[#89ff00]' : ''}`} />
              </button>

              <button
                onClick={onSend}
                disabled={sending || loading}
                className="flex items-center gap-2 px-3 sm:px-4 py-2 rounded-xl bg-[#89ff00] hover:bg-[#9eff1a] text-black font-semibold text-xs sm:text-sm font-mono shadow-[0_0_20px_rgba(137,255,0,0.3)] transition-all cursor-pointer disabled:opacity-50"
              >
                <Send className={`w-3.5 h-3.5 ${sending ? 'animate-bounce' : ''}`} />
                <span className="hidden sm:inline">{sending ? 'Dispatching...' : 'Dispatch Email'}</span>
                <span className="sm:hidden">{sending ? 'Sending...' : 'Send'}</span>
              </button>

              <button
                onClick={onClose}
                className="p-2 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-neutral-400 hover:text-white transition-all cursor-pointer"
                title="Close"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* Iframe Preview Container */}
          <div className="flex-1 bg-[#050506] p-3 sm:p-6 overflow-auto flex items-center justify-center">
            {loading ? (
              <div className="flex flex-col items-center justify-center gap-3 text-neutral-400">
                <RefreshCw className="w-8 h-8 animate-spin text-[#89ff00]" />
                <span className="text-xs font-mono">Generating live report HTML...</span>
              </div>
            ) : (
              <div
                className={`h-full transition-all duration-300 rounded-xl overflow-hidden shadow-2xl border border-white/10 bg-[#090a0c] ${
                  viewport === 'mobile' ? 'w-[375px]' : 'w-full max-w-[700px]'
                }`}
              >
                <iframe
                  title="24h Health Report Preview"
                  srcDoc={html}
                  className="w-full h-full border-0"
                  sandbox="allow-same-origin"
                />
              </div>
            )}
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
