import React, { useEffect } from 'react';
import PageCard from '../../components/common/PageCard';

export default function Contact({ onOpenMenu, isMenuOpen, onNavigate, isTransitioning, isPageReady }) {
  useEffect(() => {
    document.title = 'Contact - Mohammad Faizal';
  }, []);

  return (
    <PageCard 
      title="Contact" 
      label="Get In Touch"
      description="Have a project in mind or want to explore collaborative opportunities? Reach out directly."
      onOpenMenu={onOpenMenu}
      isMenuOpen={isMenuOpen}
      onNavigate={onNavigate}
      isTransitioning={isTransitioning}
      isPageReady={isPageReady}
    >
      <div className="grid grid-cols-1 md:grid-cols-2 gap-8 max-w-2xl">
        <div className="p-6 rounded-2xl bg-neutral-900/60 border border-neutral-800">
          <p className="text-xs font-mono text-[#89ff00] uppercase tracking-wider mb-2">Direct Channel</p>
          <p className="font-semibold text-lg text-white mb-1">General Inquiries</p>
          <a 
            href="mailto:contact@fzmonogram.com" 
            className="text-neutral-400 hover:text-white transition-colors"
          >
            contact@fzmonogram.com
          </a>
        </div>

        <div className="p-6 rounded-2xl bg-neutral-900/60 border border-neutral-800">
          <p className="text-xs font-mono text-[#89ff00] uppercase tracking-wider mb-2">Studio Base</p>
          <p className="font-semibold text-lg text-white mb-1">Headquarters</p>
          <p className="text-neutral-400">Milan, Italy</p>
        </div>
      </div>
    </PageCard>
  );
}
