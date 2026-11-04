import React from 'react';

export default function ContactSection({ onNavigate }) {
  return (
    <section id="section-cta" className="w-screen px-6 pb-32 text-white md:px-12 md:pb-52 bg-[#0a0a0e]" aria-labelledby="contact-heading">
      <div className="max-w-7xl mx-auto border-t border-white/15 pt-8">
        <div className="flex items-center justify-between text-sm tracking-tight mb-8">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-[#f59e0b]" />
            <h2 id="contact-heading" className="font-mono text-xs md:text-sm uppercase tracking-widest text-[#f59e0b]">Contact</h2>
          </div>
          <span className="font-mono text-xs md:text-sm text-white/50">OPEN FOR COLLABORATION</span>
        </div>
        <p className="mt-4 max-w-3xl text-3xl leading-tight tracking-tight md:text-6xl font-light text-white">
          Have a product, workflow, or interface to figure out?
        </p>
        <div className="mt-12 flex flex-wrap gap-x-8 gap-y-4 text-sm font-mono">
          <button
            type="button"
            onClick={() => onNavigate?.('contact')}
            className="px-6 py-3 rounded-full bg-[#f59e0b] text-black font-semibold tracking-wider hover:bg-[#d97706] transition-all duration-300 hover:scale-105"
          >
            START A CONVERSATION →
          </button>
          <button
            type="button"
            onClick={() => onNavigate?.('work')}
            className="px-6 py-3 rounded-full border border-white/20 text-white font-medium tracking-wider hover:border-[#f59e0b] hover:text-[#f59e0b] transition-all duration-300"
          >
            VIEW ARCHIVE →
          </button>
        </div>
      </div>
    </section>
  );
}
