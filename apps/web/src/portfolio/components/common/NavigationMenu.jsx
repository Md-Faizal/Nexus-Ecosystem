import React, { useState, useEffect } from 'react';
import { AnimatePresence, m as motion } from 'framer-motion';
import { monogramComplete } from '../../assets';

// 2 Slanted Corners Geometry (top-left & bottom-right beveled)
const CLIP_PATH_SLANTED_CARD = 'polygon(24px 0, 100% 0, 100% calc(100% - 24px), calc(100% - 24px) 100%, 0 100%, 0 24px)';
const CLIP_PATH_SLANTED_PREVIEW = 'polygon(16px 0, 100% 0, 100% calc(100% - 16px), calc(100% - 16px) 100%, 0 100%, 0 16px)';

// 3 Strict Predominant Flat Colors
const COLOR_MAGENTA = '#C3004F';
const COLOR_SAGE_OCHRE = '#757164';
const COLOR_DARK_CHARCOAL = '#222423';

const NAV_PAGES = [
  { 
    id: 'home', 
    label: 'Home', 
    number: '01',
    category: 'Showcase',
    tagline: 'Spatial brand experience & interactive vector architecture',
    previewType: 'home'
  },
  { 
    id: 'work', 
    label: 'Work', 
    number: '02',
    category: 'Selected Projects',
    tagline: 'High-performance interactive engines, web applications & 3D',
    previewType: 'work'
  },
  { 
    id: 'services', 
    label: 'Services', 
    number: '03',
    category: 'Capabilities',
    tagline: 'Brand systems, bespoke frontend engineering & design direction',
    previewType: 'services'
  },
  { 
    id: 'about', 
    label: 'About', 
    number: '04',
    category: 'Studio Philosophy',
    tagline: 'A creative atelier based in Milan crafting luxury experiences worldwide',
    previewType: 'about'
  },
  { 
    id: 'contact', 
    label: 'Contact', 
    number: '05',
    category: 'Get In Touch',
    tagline: 'Available for selected studio commissions & collaborative projects',
    previewType: 'contact'
  }
];

export default function NavigationMenu({ isOpen, activePage, onClose, onNavigate }) {
  const [hoveredPageId, setHoveredPageId] = useState(activePage || 'home');
  const [cursorPos, setCursorPos] = useState({ x: -100, y: -100 });
  const [isOverBackdrop, setIsOverBackdrop] = useState(false);

  // Sync hovered item with active page when opened
  useEffect(() => {
    if (isOpen) {
      setHoveredPageId(activePage || 'home');
    }
  }, [isOpen, activePage]);

  // Lock scroll wheel, touchmove, and key scrolling when menu is open
  useEffect(() => {
    if (!isOpen) return;

    const handleWheel = (e) => {
      const menuEl = document.getElementById('floating-flat-menu');
      if (menuEl && menuEl.contains(e.target)) {
        return;
      }
      e.preventDefault();
    };

    const handleTouch = (e) => {
      const menuEl = document.getElementById('floating-flat-menu');
      if (menuEl && menuEl.contains(e.target)) {
        return;
      }
      e.preventDefault();
    };

    const handleKeyScroll = (e) => {
      if (['Space', 'PageUp', 'PageDown', 'End', 'Home', 'ArrowUp', 'ArrowDown'].includes(e.code)) {
        const menuEl = document.getElementById('floating-flat-menu');
        if (menuEl && menuEl.contains(e.target)) {
          return;
        }
        e.preventDefault();
      }
    };

    window.addEventListener('wheel', handleWheel, { passive: false });
    window.addEventListener('touchmove', handleTouch, { passive: false });
    window.addEventListener('keydown', handleKeyScroll, { passive: false });

    document.body.style.overflow = 'hidden';
    document.documentElement.style.overflow = 'hidden';

    return () => {
      window.removeEventListener('wheel', handleWheel);
      window.removeEventListener('touchmove', handleTouch);
      window.removeEventListener('keydown', handleKeyScroll);
      document.body.style.overflow = '';
      document.documentElement.style.overflow = '';
    };
  }, [isOpen]);

  // Close on Escape key
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && isOpen) {
        onClose?.();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  const handleMouseMove = (e) => {
    setCursorPos({ x: e.clientX, y: e.clientY });
  };

  const handleLinkClick = (id) => {
    onNavigate?.(id);
  };

  const currentPage = NAV_PAGES.find((p) => p.id === hoveredPageId) || NAV_PAGES[0];

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* 1. Backdrop with custom 'X' cursor on hover */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.3, ease: 'easeOut' }}
            onClick={onClose}
            onMouseMove={handleMouseMove}
            onMouseEnter={() => setIsOverBackdrop(true)}
            onMouseLeave={() => setIsOverBackdrop(false)}
            className="fixed inset-0 z-9990 bg-black/75 backdrop-blur-md cursor-none"
            aria-hidden="true"
          />

          {/* Floating 'X' cursor indicator on backdrop */}
          {isOverBackdrop && (
            <motion.div
              initial={{ scale: 0, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0, opacity: 0 }}
              transition={{ duration: 0.12 }}
              style={{
                left: cursorPos.x,
                top: cursorPos.y,
                transform: 'translate(-50%, -50%)',
                zIndex: 9992,
                backgroundColor: COLOR_DARK_CHARCOAL,
                borderColor: COLOR_MAGENTA
              }}
              className="fixed pointer-events-none size-12 rounded-full border text-white flex items-center justify-center shadow-2xl"
            >
              <svg className="w-5 h-5 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </motion.div>
          )}

          {/* 2. Flat Menu Card - Pulled inward from the left side, keeping bottom edge comfortable */}
          <motion.div
            id="floating-flat-menu"
            initial={{ opacity: 0, scale: 0.05, transformOrigin: 'top right' }}
            animate={{ opacity: 1, scale: 1, transformOrigin: 'top right' }}
            exit={{ 
              opacity: 0, 
              scale: 0.05, 
              transformOrigin: 'top right', 
              transition: { duration: 0.35, ease: [0.16, 1, 0.3, 1] } 
            }}
            transition={{ duration: 0.42, ease: [0.16, 1, 0.3, 1] }}
            style={{
              clipPath: CLIP_PATH_SLANTED_CARD,
              transformOrigin: 'top right',
              backgroundColor: COLOR_SAGE_OCHRE,
              zIndex: 9999
            }}
            onMouseEnter={() => setIsOverBackdrop(false)}
            onMouseLeave={() => setIsOverBackdrop(true)}
            className="fixed top-8 sm:top-10 bottom-8 sm:bottom-10 left-6 sm:left-14 md:left-24 lg:left-36 xl:left-52 right-6 sm:right-10 md:right-12 overflow-hidden flex flex-col justify-between pointer-events-auto select-none shadow-[0_35px_110px_rgba(0,0,0,0.9)] p-6 sm:p-8 lg:p-9"
          >
            {/* Top Bar Header */}
            <div className="w-full flex items-center justify-between pb-4 sm:pb-5 border-b border-[#222423]/25 shrink-0">
              <div className="flex items-center gap-3.5">
                <img
                  src={monogramComplete}
                  alt="Mohammad Faizal"
                  className="w-7 sm:w-8 h-auto brightness-0"
                />
                <div className="flex items-center gap-2.5 sm:gap-3">
                  <span 
                    style={{ color: COLOR_DARK_CHARCOAL }}
                    className="font-mono text-xs sm:text-sm font-bold uppercase tracking-[0.18em] leading-none"
                  >
                    MOHAMMAD FAIZAL
                  </span>
                  <span 
                    style={{ color: COLOR_MAGENTA }}
                    className="hidden sm:inline font-mono text-xs font-bold uppercase tracking-wider"
                  >
                    // ATELIER
                  </span>
                </div>
              </div>

              {/* Close Button */}
              <button
                type="button"
                onClick={onClose}
                aria-label="Close navigation menu"
                className="p-2 transition-transform duration-200 hover:scale-110 active:scale-95 cursor-pointer text-[#222423] hover:text-[#C3004F]"
              >
                <svg className="w-6 h-6 sm:w-7 sm:h-7" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <line x1="18" y1="6" x2="6" y2="18" />
                  <line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              </button>
            </div>

            {/* Main Content Area: Page Names List (Left) + Page Picture Showcase (Right) */}
            <div className="flex-1 w-full grid grid-cols-1 lg:grid-cols-12 gap-6 lg:gap-8 xl:gap-10 items-stretch pt-4 sm:pt-6 min-h-0 overflow-y-auto lg:overflow-hidden">
              
              {/* ================= LEFT COLUMN: CLEAN STRUCTURED PAGE LIST ================= */}
              <div className="lg:col-span-5 flex flex-col justify-between h-full min-h-[320px]">
                <nav className="flex flex-col gap-1.5 sm:gap-2 my-auto w-full">
                  {NAV_PAGES.map((page, idx) => {
                    const isSelected = hoveredPageId === page.id;
                    const isCurrent = activePage === page.id;

                    return (
                      <motion.div
                        key={page.id}
                        initial={{ opacity: 0, x: -15 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: 0.04 + idx * 0.03, duration: 0.3 }}
                        className="w-full relative"
                      >
                        <button
                          type="button"
                          onMouseEnter={() => setHoveredPageId(page.id)}
                          onClick={() => handleLinkClick(page.id)}
                          className="group relative w-full text-left py-2 sm:py-2.5 lg:py-3 px-3 sm:px-4 rounded-lg flex items-center justify-between transition-colors duration-200 cursor-pointer"
                        >
                          {/* Smooth Spring Active Background Pill */}
                          {isSelected && (
                            <motion.div
                              layoutId="activeMenuPill"
                              transition={{ type: 'spring', stiffness: 400, damping: 32 }}
                              className="absolute inset-0 rounded-lg bg-[#222423]/12 border border-[#222423]/25"
                            />
                          )}

                          {/* Left Index Number & Large Page Name */}
                          <div className="relative z-10 flex items-baseline gap-3 sm:gap-4 lg:gap-5">
                            <span 
                              style={{
                                color: isSelected 
                                  ? COLOR_MAGENTA 
                                  : 'rgba(34, 36, 35, 0.45)'
                              }}
                              className="font-mono text-xs sm:text-sm font-bold transition-colors duration-200 w-6 sm:w-7 shrink-0"
                            >
                              {page.number}
                            </span>
                            <span 
                              style={{
                                color: isSelected 
                                  ? COLOR_DARK_CHARCOAL 
                                  : 'rgba(34, 36, 35, 0.48)'
                              }}
                              className="text-3xl sm:text-4xl md:text-5xl lg:text-5xl xl:text-6xl font-medium tracking-tight transition-colors duration-200 group-hover:text-[#222423]"
                            >
                              {page.label}
                            </span>
                          </div>

                          {/* Right Status Dot & Directional Arrow */}
                          <div className="relative z-10 flex items-center gap-2.5 sm:gap-3 shrink-0">
                            {isCurrent && (
                              <span 
                                style={{ backgroundColor: COLOR_MAGENTA }}
                                className="size-2 rounded-full shadow-sm"
                              />
                            )}
                            <span 
                              style={{
                                color: isSelected 
                                  ? COLOR_DARK_CHARCOAL 
                                  : 'rgba(34, 36, 35, 0.35)'
                              }}
                              className={`text-xl sm:text-2xl font-light transition-all duration-200 ${
                                isSelected ? 'translate-x-1 text-[#C3004F] font-normal' : 'group-hover:translate-x-1'
                              }`}
                            >
                              →
                            </span>
                          </div>
                        </button>
                      </motion.div>
                    );
                  })}
                </nav>

                {/* Footer Metadata */}
                <div 
                  style={{ color: COLOR_DARK_CHARCOAL }}
                  className="flex items-center justify-between text-xs font-mono font-bold pt-4 border-t border-[#222423]/20 shrink-0"
                >
                  <div className="flex gap-4 sm:gap-6">
                    <a 
                      href="https://linkedin.com" 
                      target="_blank" 
                      rel="noopener noreferrer" 
                      className="hover:text-[#C3004F] transition-colors"
                    >
                      LINKEDIN ↗
                    </a>
                    <a 
                      href="https://instagram.com" 
                      target="_blank" 
                      rel="noopener noreferrer" 
                      className="hover:text-[#C3004F] transition-colors"
                    >
                      INSTAGRAM ↗
                    </a>
                    <a 
                      href="https://twitter.com" 
                      target="_blank" 
                      rel="noopener noreferrer" 
                      className="hover:text-[#C3004F] transition-colors"
                    >
                      TWITTER ↗
                    </a>
                  </div>
                  <span className="opacity-75">MILAN // 2026</span>
                </div>
              </div>

              {/* ================= RIGHT COLUMN: PAGE PICTURE & VISUAL SHOWCASE CANVAS ================= */}
              <div className="lg:col-span-7 flex flex-col justify-between h-full min-h-[340px] lg:min-h-0">
                <div 
                  style={{
                    clipPath: CLIP_PATH_SLANTED_PREVIEW,
                    backgroundColor: COLOR_DARK_CHARCOAL
                  }}
                  className="w-full h-full relative overflow-hidden flex flex-col justify-between p-6 sm:p-7 lg:p-8 shadow-2xl group border border-[#757164]/25"
                >
                  {/* Dynamic Showcase Visual depending on hovered page */}
                  <AnimatePresence mode="wait">
                    <motion.div
                      key={currentPage.id}
                      initial={{ opacity: 0, scale: 1.04 }}
                      animate={{ opacity: 1, scale: 1 }}
                      exit={{ opacity: 0, scale: 0.98 }}
                      transition={{ duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
                      className="absolute inset-0 size-full pointer-events-none"
                    >
                      {/* 1. Home / Artwork Picture */}
                      {currentPage.previewType === 'home' && (
                        <div className="relative size-full">
                          <img 
                            src="/media/hero_poster.webp" 
                            alt="Visual Showcase" 
                            className="size-full object-cover opacity-60 mix-blend-luminosity scale-105"
                          />
                          <div className="absolute inset-0 bg-gradient-to-t from-[#222423] via-[#222423]/55 to-transparent" />
                        </div>
                      )}

                      {/* 2. Work / Selected Projects Graphic Grid */}
                      {currentPage.previewType === 'work' && (
                        <div className="relative size-full flex items-center justify-center p-6">
                          <div className="grid grid-cols-2 gap-3.5 w-full max-w-sm opacity-70">
                            <div className="h-24 rounded-lg border border-[#757164]/60 bg-[#757164]/10 p-3 flex flex-col justify-between">
                              <span className="text-[10px] font-mono text-[#C3004F] font-bold">01 // 3D</span>
                              <span className="text-xs font-semibold text-white">Melodrama Interface</span>
                            </div>
                            <div className="h-24 rounded-lg border border-[#C3004F]/60 bg-[#C3004F]/10 p-3 flex flex-col justify-between">
                              <span className="text-[10px] font-mono text-[#757164] font-bold">02 // BRAND</span>
                              <span className="text-xs font-semibold text-white">Monogram System</span>
                            </div>
                            <div className="h-24 rounded-lg border border-[#C3004F]/60 bg-[#C3004F]/10 p-3 flex flex-col justify-between">
                              <span className="text-[10px] font-mono text-[#757164] font-bold">03 // ENGINE</span>
                              <span className="text-xs font-semibold text-white">Vector Motion</span>
                            </div>
                            <div className="h-24 rounded-lg border border-[#757164]/60 bg-[#757164]/10 p-3 flex flex-col justify-between">
                              <span className="text-[10px] font-mono text-[#C3004F] font-bold">04 // UI/UX</span>
                              <span className="text-xs font-semibold text-white">Digital Atelier</span>
                            </div>
                          </div>
                        </div>
                      )}

                      {/* 3. Services / Capabilities Flat Geometric Visual */}
                      {currentPage.previewType === 'services' && (
                        <div className="relative size-full flex items-center justify-center">
                          <div className="size-52 rounded-full border-2 border-[#757164]/40 flex items-center justify-center">
                            <div className="size-36 rounded-full border-2 border-[#C3004F]/80 flex items-center justify-center">
                              <div className="size-20 rounded-full bg-[#C3004F]/25 flex items-center justify-center">
                                <span className="font-mono text-[10px] font-bold text-white uppercase tracking-widest">ARCHITECT</span>
                              </div>
                            </div>
                          </div>
                        </div>
                      )}

                      {/* 4. About Studio Visual */}
                      {currentPage.previewType === 'about' && (
                        <div className="relative size-full flex flex-col items-center justify-center p-6">
                          <img 
                            src={monogramComplete} 
                            alt="Monogram" 
                            className="w-36 h-auto opacity-35 drop-shadow-2xl mb-2"
                          />
                          <span className="font-mono text-[11px] uppercase tracking-widest text-[#757164]">MILAN HEADQUARTERS // EST. 2026</span>
                        </div>
                      )}

                      {/* 5. Contact Direct Channel Visual */}
                      {currentPage.previewType === 'contact' && (
                        <div className="relative size-full flex items-center justify-center p-6">
                          <div className="text-center p-5 rounded-xl border border-[#757164]/30 bg-[#757164]/10 max-w-sm">
                            <span className="font-mono text-[11px] uppercase tracking-widest text-[#C3004F] block font-bold mb-1.5">DIRECT COMMUNICATION</span>
                            <span className="text-lg sm:text-xl font-mono font-bold text-white block mb-1">contact@fzmonogram.com</span>
                            <span className="text-[11px] font-mono text-[#757164]">Global Studio Commissions // 2026</span>
                          </div>
                        </div>
                      )}
                    </motion.div>
                  </AnimatePresence>

                  {/* Top Pill Category Tag on Picture */}
                  <div className="relative z-10 flex items-center justify-between">
                    <span 
                      style={{ 
                        backgroundColor: COLOR_MAGENTA,
                        color: '#ffffff'
                      }}
                      className="font-mono text-xs font-bold uppercase tracking-wider px-3.5 py-1 rounded-full shadow-md"
                    >
                      {currentPage.category}
                    </span>

                    <span 
                      style={{ color: COLOR_SAGE_OCHRE }}
                      className="text-xs font-mono font-bold uppercase tracking-widest"
                    >
                      {currentPage.number} // 05
                    </span>
                  </div>

                  {/* Bottom Information on Picture */}
                  <div className="relative z-10 flex flex-col gap-1.5 sm:gap-2 pt-4">
                    <h3 className="text-2xl sm:text-3xl lg:text-3xl xl:text-4xl font-bold text-white tracking-tight leading-none">
                      {currentPage.label}
                    </h3>
                    <p className="text-xs sm:text-sm text-neutral-300 max-w-md leading-relaxed line-clamp-2">
                      {currentPage.tagline}
                    </p>

                    {/* Direct Explore CTA Button */}
                    <button
                      type="button"
                      onClick={() => handleLinkClick(currentPage.id)}
                      style={{ backgroundColor: COLOR_MAGENTA }}
                      className="mt-2 w-fit py-2 px-5 rounded-full text-xs font-mono font-bold uppercase tracking-wider text-white hover:brightness-110 transition-all duration-200 flex items-center gap-2 cursor-pointer shadow-xl hover:translate-x-1"
                    >
                      <span>Explore {currentPage.label}</span>
                      <span>→</span>
                    </button>
                  </div>
                </div>
              </div>

            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
