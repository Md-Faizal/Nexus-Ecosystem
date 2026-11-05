import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Mail, Trash2, Calendar, User, MessageSquare, RefreshCw, Inbox } from 'lucide-react';
import ConfirmModal from '../components/ConfirmModal';

export default function MessagesTab({ messages = [], onDeleteMessage, onRefresh, refreshing }) {
  const [selectedDeleteId, setSelectedDeleteId] = useState(null);

  const confirmDelete = async () => {
    if (!selectedDeleteId) return;
    await onDeleteMessage(selectedDeleteId);
    setSelectedDeleteId(null);
  };

  return (
    <div className="space-y-6" style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
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
              Client & Inquiries Inbox
            </span>
          </div>
          <h2 className="text-xl sm:text-2xl font-bold text-white tracking-tight">
            Contact Form Submissions
          </h2>
          <p className="text-xs sm:text-sm text-neutral-400 mt-1">
            Direct communications submitted through the portfolio contact portal.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <span className="px-3 py-1.5 rounded-xl bg-white/5 border border-white/10 text-xs font-mono text-neutral-300">
            Total: <strong className="text-white">{messages.length}</strong>
          </span>
          <button
            onClick={onRefresh}
            disabled={refreshing}
            className="p-2.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-neutral-300 hover:text-white transition-all cursor-pointer disabled:opacity-50"
            title="Refresh messages"
          >
            <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin text-[#89ff00]' : ''}`} />
          </button>
        </div>
      </div>

      {/* Messages List */}
      {messages.length === 0 ? (
        <div
          className="rounded-3xl backdrop-blur-xl text-center flex flex-col items-center justify-center"
          style={{
            padding: '48px 24px',
            backgroundColor: 'rgba(18, 19, 22, 0.65)',
            border: '1px solid rgba(255, 255, 255, 0.08)'
          }}
        >
          <div className="p-4 rounded-2xl bg-white/5 text-neutral-400 mb-3">
            <Inbox className="w-8 h-8" />
          </div>
          <h3 className="text-base font-semibold text-white">No Messages In Inbox</h3>
          <p className="text-xs text-neutral-400 max-w-sm mt-1">
            Incoming inquiries submitted via the website's contact form will appear here in real time.
          </p>
        </div>
      ) : (
        <div className="space-y-4" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {messages.map((msg) => (
            <motion.div
              key={msg.id}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              className="rounded-2xl border backdrop-blur-xl transition-all hover:border-white/20"
              style={{
                padding: '24px',
                backgroundColor: 'rgba(18, 19, 22, 0.85)',
                borderColor: 'rgba(255, 255, 255, 0.09)',
                boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.45)',
                boxSizing: 'border-box'
              }}
            >
              <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
                <div className="space-y-2 min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-semibold text-white">
                      {msg.name || 'Anonymous Sender'}
                    </span>
                    <a
                      href={`mailto:${msg.email}`}
                      className="text-xs font-mono text-[#89ff00] hover:underline"
                    >
                      {msg.email}
                    </a>
                  </div>

                  {msg.subject && (
                    <h4 className="text-xs font-mono font-medium text-neutral-300">
                      Subject: {msg.subject}
                    </h4>
                  )}

                  <p className="text-xs sm:text-sm text-neutral-300 leading-relaxed whitespace-pre-wrap pt-1">
                    {msg.message}
                  </p>

                  <div className="flex items-center gap-4 text-[11px] font-mono text-neutral-500 pt-2 border-t border-white/5">
                    <span className="flex items-center gap-1.5">
                      <Calendar className="w-3 h-3" />
                      {msg.created_at || 'Recently'}
                    </span>
                    {msg.ip && (
                      <span>
                        IP: <strong className="text-neutral-400">{msg.ip}</strong>
                      </span>
                    )}
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <a
                    href={`mailto:${msg.email}?subject=Re: ${encodeURIComponent(msg.subject || 'Portfolio Inquiry')}`}
                    className="px-3 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-mono text-neutral-300 hover:text-white transition-all"
                  >
                    Reply
                  </a>
                  <button
                    onClick={() => setSelectedDeleteId(msg.id)}
                    className="p-2 rounded-xl bg-[#ff3366]/10 hover:bg-[#ff3366]/20 border border-[#ff3366]/20 text-[#ff3366] transition-all cursor-pointer"
                    title="Delete message"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            </motion.div>
          ))}
        </div>
      )}

      {/* Delete Confirmation Modal */}
      <ConfirmModal
        isOpen={Boolean(selectedDeleteId)}
        title="Delete Contact Message?"
        message="This action will permanently delete this client inquiry from the server database."
        confirmLabel="Delete Message"
        onConfirm={confirmDelete}
        onCancel={() => setSelectedDeleteId(null)}
      />
    </div>
  );
}
