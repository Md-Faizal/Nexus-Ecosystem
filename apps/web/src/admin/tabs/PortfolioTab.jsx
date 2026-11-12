import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { FolderGit2, Plus, Trash2, ExternalLink, Image as ImageIcon } from 'lucide-react';
import ConfirmModal from '../components/ConfirmModal';

export default function PortfolioTab({ projects = [], onAddProject, onDeleteProject }) {
  const [newProject, setNewProject] = useState({ title: '', description: '', link: '', image: '' });
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [selectedDeleteId, setSelectedDeleteId] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!newProject.title.trim()) return;
    setIsSubmitting(true);
    try {
      await onAddProject(newProject);
      setNewProject({ title: '', description: '', link: '', image: '' });
    } finally {
      setIsSubmitting(false);
    }
  };

  const confirmDelete = async () => {
    if (!selectedDeleteId) return;
    await onDeleteProject(selectedDeleteId);
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
              Portfolio Content Management
            </span>
          </div>
          <h2 className="text-xl sm:text-2xl font-bold text-white tracking-tight">
            Curated Projects & Engineering Showcase
          </h2>
          <p className="text-xs sm:text-sm text-neutral-400 mt-1">
            Publish, edit, and organize selected works featured on the public site.
          </p>
        </div>

        <span className="px-3 py-1.5 rounded-xl bg-white/5 border border-white/10 text-xs font-mono text-neutral-300">
          Projects: <strong className="text-white">{projects.length}</strong>
        </span>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8" style={{ gap: '28px' }}>
        {/* Add Project Form */}
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
              <Plus className="w-4 h-4 text-[#89ff00]" />
            </div>
            <h3 className="text-base font-semibold text-white">Add New Project</h3>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-mono text-neutral-400 uppercase tracking-wider mb-1.5">
                Project Title
              </label>
              <input
                type="text"
                required
                value={newProject.title}
                onChange={(e) => setNewProject({ ...newProject, title: e.target.value })}
                placeholder="e.g. Atelier Hyper-Engine"
                className="w-full px-3.5 py-2.5 rounded-xl bg-black/40 border border-white/10 text-white text-xs font-mono placeholder:text-neutral-600 focus:outline-none focus:border-[#89ff00]/50"
              />
            </div>

            <div>
              <label className="block text-xs font-mono text-neutral-400 uppercase tracking-wider mb-1.5">
                Brief Description
              </label>
              <textarea
                rows={3}
                value={newProject.description}
                onChange={(e) => setNewProject({ ...newProject, description: e.target.value })}
                placeholder="Details of the architecture, stack, and results..."
                className="w-full px-3.5 py-2.5 rounded-xl bg-black/40 border border-white/10 text-white text-xs font-mono placeholder:text-neutral-600 focus:outline-none focus:border-[#89ff00]/50 resize-none"
              />
            </div>

            <div>
              <label className="block text-xs font-mono text-neutral-400 uppercase tracking-wider mb-1.5">
                External Link or Repo
              </label>
              <input
                type="text"
                value={newProject.link}
                onChange={(e) => setNewProject({ ...newProject, link: e.target.value })}
                placeholder="https://github.com/..."
                className="w-full px-3.5 py-2.5 rounded-xl bg-black/40 border border-white/10 text-white text-xs font-mono placeholder:text-neutral-600 focus:outline-none focus:border-[#89ff00]/50"
              />
            </div>

            <div>
              <label className="block text-xs font-mono text-neutral-400 uppercase tracking-wider mb-1.5">
                Image URL (Optional)
              </label>
              <input
                type="text"
                value={newProject.image}
                onChange={(e) => setNewProject({ ...newProject, image: e.target.value })}
                placeholder="https://... or /uploads/..."
                className="w-full px-3.5 py-2.5 rounded-xl bg-black/40 border border-white/10 text-white text-xs font-mono placeholder:text-neutral-600 focus:outline-none focus:border-[#89ff00]/50"
              />
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full py-2.5 rounded-xl bg-[#89ff00] hover:bg-[#9eff1a] text-black font-semibold text-xs font-mono shadow-[0_0_20px_rgba(137,255,0,0.2)] transition-all cursor-pointer disabled:opacity-50"
            >
              {isSubmitting ? 'Publishing...' : 'Publish to Portfolio'}
            </button>
          </form>
        </div>

        {/* Existing Projects Grid */}
        <div className="lg:col-span-2 space-y-4">
          {projects.length === 0 ? (
            <div className="p-12 rounded-3xl bg-neutral-900/40 border border-white/10 backdrop-blur-xl text-center flex flex-col items-center justify-center">
              <FolderGit2 className="w-8 h-8 text-neutral-500 mb-2" />
              <h4 className="text-sm font-semibold text-white">No Projects Registered</h4>
              <p className="text-xs text-neutral-400 mt-1">Use the form to add projects to the portfolio.</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {projects.map((project) => (
                <motion.div
                  key={project.id}
                  whileHover={{ y: -2 }}
                  className="rounded-2xl border backdrop-blur-xl flex flex-col justify-between"
                  style={{
                    padding: '24px',
                    backgroundColor: 'rgba(18, 19, 22, 0.85)',
                    borderColor: 'rgba(255, 255, 255, 0.09)',
                    boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.45)',
                    boxSizing: 'border-box'
                  }}
                >
                  <div>
                    {project.image ? (
                      <div className="w-full h-36 rounded-xl overflow-hidden mb-3 bg-neutral-800">
                        <img
                          src={project.image}
                          alt={project.title}
                          className="w-full h-full object-cover"
                        />
                      </div>
                    ) : (
                      <div className="w-full h-24 rounded-xl mb-3 bg-white/5 border border-white/5 flex items-center justify-center text-neutral-500">
                        <ImageIcon className="w-6 h-6" />
                      </div>
                    )}

                    <h4 className="text-sm font-semibold text-white leading-snug">
                      {project.title}
                    </h4>
                    <p className="text-xs text-neutral-400 mt-1 line-clamp-2 leading-relaxed">
                      {project.description || 'No description provided.'}
                    </p>
                  </div>

                  <div className="flex items-center justify-between gap-2 mt-4 pt-3 border-t border-white/5">
                    {project.link ? (
                      <a
                        href={project.link}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex items-center gap-1.5 text-xs font-mono text-[#89ff00] hover:underline"
                      >
                        <span>Visit</span>
                        <ExternalLink className="w-3 h-3" />
                      </a>
                    ) : (
                      <span className="text-[10px] font-mono text-neutral-500">Internal</span>
                    )}

                    <button
                      onClick={() => setSelectedDeleteId(project.id)}
                      className="p-2 rounded-xl bg-[#ff3366]/10 hover:bg-[#ff3366]/20 border border-[#ff3366]/20 text-[#ff3366] transition-all cursor-pointer"
                      title="Delete project"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </motion.div>
              ))}
            </div>
          )}
        </div>
      </div>

      <ConfirmModal
        isOpen={Boolean(selectedDeleteId)}
        title="Remove Project?"
        message="Are you sure you want to remove this project from your live portfolio showcase?"
        confirmLabel="Remove Project"
        onConfirm={confirmDelete}
        onCancel={() => setSelectedDeleteId(null)}
      />
    </div>
  );
}
