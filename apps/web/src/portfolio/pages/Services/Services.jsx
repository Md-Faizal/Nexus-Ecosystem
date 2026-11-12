import React, { useEffect } from 'react';
import PageCard from '../../components/common/PageCard';

export default function Services({ onOpenMenu, isMenuOpen, onNavigate }) {
  useEffect(() => {
    document.title = 'Services - Mohammad Faizal';
  }, []);

  const services = [
    { title: 'Interactive Web Systems', desc: 'Custom creative web experiences with 60fps animations, 3D graphics, and responsive architecture.' },
    { title: 'Design Engineering', desc: 'Bridging high-end aesthetic visual craft with robust, accessible frontend engineering.' },
    { title: 'Creative Brand Systems', desc: 'Complete monogram systems, custom typography, motion identities, and digital visual language.' }
  ];

  return (
    <PageCard 
      title="Services" 
      label="Capabilities"
      description="We engineer bespoke visual & digital solutions with relentless attention to craft and technical fidelity."
      onOpenMenu={onOpenMenu}
      isMenuOpen={isMenuOpen}
      onNavigate={onNavigate}
      isTransitioning={isTransitioning}
      isPageReady={isPageReady}
    >
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 w-full">
        {services.map((item, idx) => (
          <div 
            key={idx} 
            className="rounded-2xl bg-neutral-900/60 border border-neutral-800 hover:border-[#89ff00]/40 transition-all duration-300 p-8 flex flex-col justify-between group"
          >
            <div>
              <span className="text-xs font-mono text-[#89ff00] mb-4 block">0{idx + 1} //</span>
              <h2 className="text-xl font-medium text-white mb-3">{item.title}</h2>
              <p className="text-sm text-neutral-400 leading-relaxed">{item.desc}</p>
            </div>
          </div>
        ))}
      </div>
    </PageCard>
  );
}
