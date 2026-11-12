import React from 'react';

export default function SiteFooter({ onNavigate }) {
  return (
    <footer className="relative flex min-h-svh w-screen flex-col overflow-hidden bg-[#757164] px-6 pb-8 pt-6 text-[#222423] md:px-12">
      <div className="relative z-0 -mx-6 h-[clamp(10rem,25vw,24rem)] flex-none overflow-hidden border-t-4 border-[#C3004F] md:-mx-12">
        <span className="absolute left-0 top-0 block w-full whitespace-nowrap text-center text-[clamp(3.5rem,10vw,9rem)] font-black uppercase leading-[0.82] tracking-[-0.07em] text-[#222423]/25">
          Mohammad
        </span>
        <span className="absolute left-1/2 top-[30%] z-10 block -translate-x-1/2 whitespace-nowrap text-[clamp(5rem,17vw,15rem)] font-black uppercase leading-[0.78] tracking-[-0.08em] text-[#222423]">
          Faizal
        </span>
      </div>

      <div className="relative z-10 mt-auto border-t border-[#222423]/30 pt-6">
        <div className="grid gap-10 md:grid-cols-[1fr_1fr_auto] md:gap-12">
          <div>
            <p className="mb-8 text-sm uppercase tracking-[0.18em] text-[#222423]/70">Menu</p>
            <nav className="flex flex-col items-start text-2xl leading-[1.15] md:text-3xl" aria-label="Footer navigation">
              <button type="button" onClick={() => onNavigate?.('home')} className="transition-colors hover:text-[#C3004F]">Home</button>
              <button type="button" onClick={() => onNavigate?.('about')} className="transition-colors hover:text-[#C3004F]">About</button>
              <button type="button" onClick={() => onNavigate?.('work')} className="transition-colors hover:text-[#C3004F]">Work</button>
              <button type="button" onClick={() => onNavigate?.('contact')} className="transition-colors hover:text-[#C3004F]">Contact</button>
            </nav>
          </div>

          <div>
            <p className="mb-8 text-sm uppercase tracking-[0.18em] text-[#222423]/70">Contact</p>
            <address className="not-italic text-base leading-relaxed">
              Dubai, UAE<br />
              Building A1, Dubai Digital Park
              <br /><br />
              hello@mohammadfaizal.com<br />
              jobs@mohammadfaizal.com
            </address>
          </div>

          <button type="button" onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })} className="self-start text-left text-sm uppercase tracking-[0.18em] transition-colors hover:text-[#C3004F] md:justify-self-end">
            Back to top
          </button>
        </div>

        <div className="mt-16 flex flex-col gap-3 border-t border-[#222423]/30 pt-4 text-xs uppercase tracking-[0.12em] text-[#222423]/70 md:flex-row md:items-center md:justify-between">
          <span>© 2026 Mohammad Faizal</span>
          <span>Designing with intent</span>
        </div>
      </div>
    </footer>
  );
}