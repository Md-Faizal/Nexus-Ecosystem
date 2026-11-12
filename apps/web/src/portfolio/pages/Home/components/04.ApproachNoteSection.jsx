import React from 'react';

export default function ApproachNoteSection() {
  return (
    <section id="section-approach-note" className="w-screen px-6 pb-28 text-white md:px-12 md:pb-44 bg-[#0a0a0e]" aria-labelledby="approach-note-heading">
      <div className="max-w-7xl mx-auto">
        <div className="border-y border-white/15 py-4 text-sm tracking-tight flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-[#f59e0b]" />
            <h2 id="approach-note-heading" className="font-mono text-xs md:text-sm uppercase tracking-widest text-[#f59e0b]">Philosophy</h2>
          </div>
          <span className="font-mono text-xs md:text-sm text-white/50">APPROACH NOTE</span>
        </div>
        <p className="mt-10 max-w-4xl text-2xl leading-relaxed tracking-tight md:text-4xl font-light text-white/90">
          I prefer working close to the product: understanding the workflow, making the interface clear, and carrying the important details through to implementation with rigorous architectural precision.
        </p>
      </div>
    </section>
  );
}
