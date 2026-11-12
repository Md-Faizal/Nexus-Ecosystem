import React, { useEffect } from 'react';
import PageCard from '../../components/common/PageCard';

export default function About({ onOpenMenu, isMenuOpen, onNavigate, isTransitioning, isPageReady }) {
  useEffect(() => {
    document.title = 'About - Mohammad Faizal';
  }, []);

  return (
    <PageCard 
      title="About" 
      label="Who We Are"
      description="We are a creative studio crafting luxury digital experiences where depth, precision, and essence are felt, not explained."
      onOpenMenu={onOpenMenu}
      isMenuOpen={isMenuOpen}
      onNavigate={onNavigate}
      isTransitioning={isTransitioning}
      isPageReady={isPageReady}
    >
      <div className="w-full h-80 md:h-96 rounded-2xl bg-neutral-900/60 border border-neutral-800 flex items-center justify-center relative overflow-hidden group">
        <div className="absolute inset-0 bg-gradient-to-br from-[#89ff00]/5 via-transparent to-transparent opacity-50" />
        <span className="text-neutral-500 font-mono text-sm tracking-wider uppercase z-10">
          Visual Presentation & Studio Philosophy
        </span>
      </div>
    </PageCard>
  );
}
