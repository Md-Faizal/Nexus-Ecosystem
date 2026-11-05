import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { CheckCircle2, AlertTriangle, Info, X } from 'lucide-react';

export default function ToastNotification({ toast, onClose }) {
  return (
    <AnimatePresence>
      {toast && (
        <motion.div
          initial={{ opacity: 0, y: -20, scale: 0.95 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: -15, scale: 0.95 }}
          transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
          className="fixed top-6 right-6 z-[999999] max-w-md w-full px-5 py-4 rounded-2xl border backdrop-blur-2xl shadow-2xl flex items-center justify-between gap-3 text-sm font-sans"
          style={{
            backgroundColor: 'rgba(18, 18, 20, 0.92)',
            borderColor:
              toast.type === 'error'
                ? 'rgba(255, 51, 102, 0.4)'
                : toast.type === 'info'
                ? 'rgba(56, 189, 248, 0.4)'
                : 'rgba(137, 255, 0, 0.4)',
            boxShadow:
              toast.type === 'error'
                ? '0 10px 40px -10px rgba(255, 51, 102, 0.3)'
                : '0 10px 40px -10px rgba(137, 255, 0, 0.25)'
          }}
        >
          <div className="flex items-center gap-3 min-w-0">
            {toast.type === 'error' ? (
              <div className="p-1.5 rounded-xl bg-[#ff3366]/15 text-[#ff3366] shrink-0">
                <AlertTriangle className="w-4 h-4" />
              </div>
            ) : toast.type === 'info' ? (
              <div className="p-1.5 rounded-xl bg-sky-500/15 text-sky-400 shrink-0">
                <Info className="w-4 h-4" />
              </div>
            ) : (
              <div className="p-1.5 rounded-xl bg-[#89ff00]/15 text-[#89ff00] shrink-0">
                <CheckCircle2 className="w-4 h-4" />
              </div>
            )}
            <p className="text-white text-xs sm:text-sm font-medium leading-tight truncate">
              {toast.message}
            </p>
          </div>
          {onClose && (
            <button
              onClick={onClose}
              className="text-neutral-400 hover:text-white p-1 rounded-lg hover:bg-white/5 transition-colors cursor-pointer shrink-0"
              aria-label="Dismiss notification"
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </motion.div>
      )}
    </AnimatePresence>
  );
}
