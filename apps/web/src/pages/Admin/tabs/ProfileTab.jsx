import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { UserCheck, Upload, Trash2, CheckCircle2, Image as ImageIcon } from 'lucide-react';
import ConfirmModal from '../components/ConfirmModal';

export default function ProfileTab({
  profileHistory = [],
  onUploadPic,
  onSelectPic,
  onDeletePic,
  refreshing
}) {
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [selectedDeleteId, setSelectedDeleteId] = useState(null);

  const handleUpload = async (e) => {
    e.preventDefault();
    if (!file) return;
    setUploading(true);
    try {
      const formData = new FormData();
      formData.append('picture', file);
      await onUploadPic(formData);
      setFile(null);
    } finally {
      setUploading(false);
    }
  };

  const confirmDelete = async () => {
    if (!selectedDeleteId) return;
    await onDeletePic(selectedDeleteId);
    setSelectedDeleteId(null);
  };

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
              Brand Identity & Persona
            </span>
          </div>
          <h2 className="text-xl sm:text-2xl font-bold text-white tracking-tight">
            Profile Avatar & Photo Vault
          </h2>
          <p className="text-xs sm:text-sm text-neutral-400 mt-1">
            Upload and switch active profile pictures displayed across the portfolio.
          </p>
        </div>

        <span className="px-3 py-1.5 rounded-xl bg-white/5 border border-white/10 text-xs font-mono text-neutral-300">
          History: <strong className="text-white">{profileHistory.length}</strong> photos
        </span>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8" style={{ gap: '28px' }}>
        {/* Upload Card */}
        <div
          className="rounded-3xl backdrop-blur-xl h-fit"
          style={{
            padding: '28px',
            backgroundColor: 'rgba(18, 19, 22, 0.85)',
            border: '1px solid rgba(255, 255, 255, 0.09)',
            boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.45)',
            boxSizing: 'border-box'
          }}
        >
          <div className="flex items-center gap-2.5 mb-4">
            <div className="p-2 rounded-xl bg-white/5 border border-white/5 text-neutral-300">
              <Upload className="w-4 h-4 text-[#89ff00]" />
            </div>
            <h3 className="text-base font-semibold text-white">Upload New Portrait</h3>
          </div>

          <form onSubmit={handleUpload} className="space-y-4">
            <div className="p-6 rounded-2xl border-2 border-dashed border-white/10 hover:border-white/20 text-center transition-colors">
              <input
                type="file"
                accept="image/*"
                onChange={(e) => setFile(e.target.files?.[0] || null)}
                className="hidden"
                id="profile-upload-input"
              />
              <label
                htmlFor="profile-upload-input"
                className="cursor-pointer flex flex-col items-center justify-center gap-2"
              >
                <div className="p-3 rounded-full bg-white/5 text-neutral-300">
                  <ImageIcon className="w-6 h-6" />
                </div>
                <span className="text-xs font-mono text-white">
                  {file ? file.name : 'Choose JPG, PNG, or WebP'}
                </span>
                <span className="text-[11px] text-neutral-500 font-mono">Max size 8MB</span>
              </label>
            </div>

            <button
              type="submit"
              disabled={!file || uploading}
              className="w-full py-2.5 rounded-xl bg-[#89ff00] hover:bg-[#9eff1a] text-black font-semibold text-xs font-mono shadow-[0_0_20px_rgba(137,255,0,0.2)] transition-all cursor-pointer disabled:opacity-50"
            >
              {uploading ? 'Uploading...' : 'Save & Set Active'}
            </button>
          </form>
        </div>

        {/* Gallery */}
        <div className="lg:col-span-2 space-y-4">
          {profileHistory.length === 0 ? (
            <div className="p-12 rounded-3xl bg-neutral-900/40 border border-white/10 backdrop-blur-xl text-center flex flex-col items-center justify-center">
              <UserCheck className="w-8 h-8 text-neutral-500 mb-2" />
              <h4 className="text-sm font-semibold text-white">No Profile Photos In Vault</h4>
              <p className="text-xs text-neutral-400 mt-1">Upload an image to populate your avatar gallery.</p>
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
              {profileHistory.map((item) => {
                const isActive = item.is_active || item.isActive;
                return (
                  <motion.div
                    key={item.id}
                    whileHover={{ y: -2 }}
                    className={`relative p-3 rounded-2xl bg-neutral-900/60 border backdrop-blur-xl overflow-hidden flex flex-col justify-between ${
                      isActive ? 'border-[#89ff00] shadow-[0_0_20px_rgba(137,255,0,0.2)]' : 'border-white/10'
                    }`}
                  >
                    <div className="w-full aspect-square rounded-xl overflow-hidden bg-neutral-800 mb-3">
                      <img
                        src={`/api/profile-pic/${item.id}`}
                        alt="Profile photo"
                        className="w-full h-full object-cover"
                      />
                    </div>

                    <div className="flex items-center justify-between gap-1">
                      {isActive ? (
                        <span className="text-[10px] font-mono font-bold text-[#89ff00] flex items-center gap-1">
                          <CheckCircle2 className="w-3 h-3" /> ACTIVE
                        </span>
                      ) : (
                        <button
                          onClick={() => onSelectPic(item.id)}
                          className="px-2.5 py-1 rounded-lg bg-white/5 hover:bg-white/10 text-[10px] font-mono text-neutral-300 hover:text-white transition-colors cursor-pointer"
                        >
                          Set Active
                        </button>
                      )}

                      <button
                        onClick={() => setSelectedDeleteId(item.id)}
                        className="p-1.5 rounded-lg bg-[#ff3366]/10 hover:bg-[#ff3366]/20 text-[#ff3366] transition-colors cursor-pointer"
                        title="Delete photo"
                      >
                        <Trash2 className="w-3 h-3" />
                      </button>
                    </div>
                  </motion.div>
                );
              })}
            </div>
          )}
        </div>
      </div>

      <ConfirmModal
        isOpen={Boolean(selectedDeleteId)}
        title="Delete Avatar Photo?"
        message="This photo will be removed from your image vault."
        confirmLabel="Delete Photo"
        onConfirm={confirmDelete}
        onCancel={() => setSelectedDeleteId(null)}
      />
    </div>
  );
}
