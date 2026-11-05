import React, { useEffect } from 'react';
import PageCard from '../../components/common/PageCard';

export default function Work({ onOpenMenu, isMenuOpen, onNavigate, isTransitioning, isPageReady }) {
  useEffect(() => {
    document.title = 'Work - Mohammad Faizal';
  }, []);

  return (
    <PageCard 
      title="Work" 
      label="Selected Projects"
      description="A curation of our recent work across digital architecture, brand systems, and interactive media."
      onOpenMenu={onOpenMenu}
      isMenuOpen={isMenuOpen}
      onNavigate={onNavigate}
      isTransitioning={isTransitioning}
      isPageReady={isPageReady}
    >
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 w-full">
        {[
          { title: 'Melodrama Interface', tag: 'Web Application / 3D' },
          { title: 'Cyber Monogram Experience', tag: 'Creative Direction' },
          { title: 'Vector Motion Engine', tag: 'Frontend Engineering' },
          { title: 'Interactive Showcase', tag: 'UI / UX Design' }
        ].map((item, idx) => (
          <div 
            key={idx} 
            className="h-64 rounded-2xl bg-neutral-900/60 border border-neutral-800 hover:border-[#89ff00]/40 transition-all duration-300 p-6 flex flex-col justify-between group"
          >
            <span className="text-xs font-mono text-neutral-500 uppercase tracking-wider group-hover:text-[#89ff00] transition-colors">
              {item.tag}
            </span>
            <span className="text-xl font-medium text-white group-hover:translate-x-1 transition-transform">
              {item.title}
            </span>
          </div>
        ))}
      </div>
    </PageCard>
  );
}
