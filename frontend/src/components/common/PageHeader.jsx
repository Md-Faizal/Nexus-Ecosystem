import React, { useState } from 'react';
import { m as motion } from 'framer-motion';

const CLIP_PATH_SLANTED = 'polygon(10px 0, 100% 0, 100% calc(100% - 10px), calc(100% - 10px) 100%, 0 100%, 0 10px)';

export default function PageHeader({ 
  onOpenMenu, 
  isMenuOpen, 
  onNavigate, 
  onLogoClick,
  currentPage = 'home',
  isTransitioning = false,
  isPageReady = true,
  showHomeLogo = false,
  logoColorStage = 'lime' // 'red' | 'lime'
}) {
  const [isMuted, setIsMuted] = useState(false);
  const isHome = currentPage === 'home';
  // On home page, hide immediately if showHomeLogo is false OR during transition
  const isVisible = isHome ? (showHomeLogo && !isTransitioning) : true;
  const isLime = isHome ? (logoColorStage === 'lime') : true;

  const handleLogoClick = (e) => {
    e.preventDefault();
    // 1. Not clickable during page change or when not fully loaded or hidden
    if (isTransitioning || !isPageReady || !isVisible) {
      return;
    }

    if (isHome) {
      // 3. On home page: counts 5 clicks to open 4-digit passcode slide down
      onLogoClick?.();
    } else {
      // On other pages: navigates to home
      onNavigate?.('home');
    }
  };

  // Wing color transition: #A30033 (Crimson Red) -> #89ff00 (Neon Lime)
  const wingColor = isLime ? '#89ff00' : '#A30033';

  return (
    <header className="fixed top-0 left-0 right-0 z-997 pointer-events-none w-full">
      {/* 1. BRAND LOGO (TOP-LEFT) WITH 40PX TOP PADDING */}
      <motion.a
        data-header-brand-logo="true"
        data-no-click-sfx="true"
        aria-label="Mohammad Faizal - Brand Logo"
        initial={isHome ? { opacity: 0, scale: 0.9, y: -4 } : false}
        animate={{ 
          opacity: isVisible ? 1 : 0, 
          scale: isVisible ? 1 : 0.9,
          y: isVisible ? 0 : -4
        }}
        transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }}
        className={`fixed left-6 md:left-12 top-0 w-16 md:w-20 flex flex-col select-none transition-transform duration-300 ${
          isVisible ? 'pointer-events-auto' : 'pointer-events-none'
        } ${
          isHome ? 'cursor-default' : 'cursor-pointer hover:scale-105'
        } ${isTransitioning || !isPageReady ? 'pointer-events-none' : ''}`}
        style={{
          paddingTop: '40px',
          zIndex: 997
        }}
        href="/"
        onClick={handleLogoClick}
      >
        <div className="relative w-full h-auto">
          {/* Backdrop Inverted Silhouette (like custom scrollbar thumb) */}
          <div 
            className="absolute inset-0 mix-blend-exclusion pointer-events-none"
            aria-hidden="true"
          >
            <svg 
              viewBox="0 0 171.63 91" 
              className="w-full h-auto" 
              fill="none" 
              xmlns="http://www.w3.org/2000/svg"
            >
              {/* White inversion silhouette behind monogram */}
              <path 
                d="M43.82,70.3l64.52.05,5.42-12.76-32.78-.36,41.09-19.08,7.56-18.43,27.69.85-25.84,61.15c-2.63,6.23-11.43,8.64-18.18,8.65l-111.12.13,41.64-20.2Z" 
                fill="#ffffff" 
              />
              <path 
                d="M129.63,19.71l-65.22.74-5.49,12.65,32.87.55-41.14,19.05-6.83,17.6-28.38-.02L41.22,9.13C43.67,3.32,52.08.55,58.48.55l110.96-.05-39.82,19.21Z" 
                fill="#ffffff" 
              />
              <polygon 
                points="44.55 69.8 51.04 53.07 93.98 33.19 59.67 32.61 64.74 20.95 128.88 20.22 121.68 37.77 78.77 57.7 113 58.08 108.01 69.85 44.55 69.8" 
                fill="#ffffff" 
              />
            </svg>
          </div>

          {/* Dynamic Color Wings (Red -> Neon Lime transition) */}
          <svg 
            viewBox="0 0 171.63 91" 
            className="w-full h-auto relative z-10 drop-shadow-md" 
            xmlns="http://www.w3.org/2000/svg"
          >
            {/* Luminous Wings with smooth color shift */}
            <motion.path 
              d="M43.82,70.3l64.52.05,5.42-12.76-32.78-.36,41.09-19.08,7.56-18.43,27.69.85-25.84,61.15c-2.63,6.23-11.43,8.64-18.18,8.65l-111.12.13,41.64-20.2Z" 
              initial={isHome ? { fill: '#A30033' } : false}
              animate={{ fill: wingColor }}
              transition={{ duration: 0.9, ease: [0.16, 1, 0.3, 1] }}
              stroke="#000000" 
              strokeWidth="0.8"
            />
            <motion.path 
              d="M129.63,19.71l-65.22.74-5.49,12.65,32.87.55-41.14,19.05-6.83,17.6-28.38-.02L41.22,9.13C43.67,3.32,52.08.55,58.48.55l110.96-.05-39.82,19.21Z" 
              initial={isHome ? { fill: '#A30033' } : false}
              animate={{ fill: wingColor }}
              transition={{ duration: 0.9, ease: [0.16, 1, 0.3, 1] }}
              stroke="#000000" 
              strokeWidth="0.8"
            />

            {/* Dark Body Poly */}
            <polygon 
              points="44.55 69.8 51.04 53.07 93.98 33.19 59.67 32.61 64.74 20.95 128.88 20.22 121.68 37.77 78.77 57.7 113 58.08 108.01 69.85 44.55 69.8" 
              fill="#201d1a" 
            />

            {/* Crisp White Inner Accent Lines */}
            <path 
              d="M128.13,20.73l-6.84,16.67-40.73,18.92-4,1.86,4.41.05,31.28.34-4.57,10.78-62.4-.05,6.16-15.86,40.77-18.88,3.96-1.83-4.36-.07-31.37-.53,4.64-10.68,63.06-.71M129.63,19.71l-65.22.74-5.49,12.65,32.87.55-41.14,19.05-6.83,17.6,64.52.05,5.42-12.76-32.78-.36,41.09-19.08,7.56-18.43h0Z" 
              fill="#ffffff" 
            />
          </svg>
        </div>
      </motion.a>

      {/* 2. HEADER ACTION BUTTONS (TOP-RIGHT) AT 40PX TOP - FIXED ON SCROLL */}
      <div
        className="fixed right-6 md:right-12 pointer-events-auto flex items-center gap-3"
        style={{
          top: '40px',
          zIndex: 998
        }}
      >
        {/* Audio Sound Button */}
        <div
          className="relative size-11 hidden sm:flex items-center justify-center transition-all duration-300 hover:scale-105"
          style={{
            clipPath: CLIP_PATH_SLANTED,
            backgroundColor: 'rgba(30, 30, 30, 0.85)',
            backdropFilter: 'blur(8px)',
            WebkitBackdropFilter: 'blur(8px)',
            border: '1px solid rgba(255, 255, 255, 0.08)'
          }}
        >
          <button
            type="button"
            aria-label={isMuted ? 'Enable website sounds' : 'Disable website sounds'}
            aria-pressed={!isMuted}
            onClick={() => setIsMuted((prev) => !prev)}
            className="size-full cursor-pointer text-white flex items-center justify-center bg-transparent border-0 outline-none transition-transform duration-200 active:scale-95"
          >
            {isMuted ? (
              <svg aria-hidden="true" viewBox="0 0 24 24" className="size-5 text-neutral-400" fill="none" stroke="currentColor" strokeWidth="1.8">
                <path d="M11 5L6 9H3V15H6L11 19V5Z" strokeLinejoin="round" />
                <line x1="23" y1="9" x2="17" y2="15" strokeLinecap="round" />
                <line x1="17" y1="9" x2="23" y2="15" strokeLinecap="round" />
              </svg>
            ) : (
              <svg aria-hidden="true" viewBox="0 0 24 24" className="size-5 text-white" fill="none" stroke="currentColor" strokeWidth="1.8">
                <path d="M11 5L6 9H3V15H6L11 19V5Z" strokeLinejoin="round" />
                <path d="M15.5 9.5C16.7 10.7 17.3 12.3 17.3 14C17.3 15.7 16.7 17.3 15.5 18.5" strokeLinecap="round" />
                <path d="M18.5 7.5C20.4 9.3 21.3 11.6 21.3 14C21.3 16.4 20.4 18.7 18.5 20.5" strokeLinecap="round" />
              </svg>
            )}
          </button>
        </div>

        {/* Hamburger Menu Button */}
        <div
          className="relative size-11 flex items-center justify-center transition-all duration-300 hover:scale-105"
          style={{
            clipPath: CLIP_PATH_SLANTED,
            backgroundColor: 'rgba(30, 30, 30, 0.85)',
            backdropFilter: 'blur(8px)',
            WebkitBackdropFilter: 'blur(8px)',
            border: '1px solid rgba(255, 255, 255, 0.08)'
          }}
        >
          <div
            className="hamburger-react pointer-events-none relative flex flex-col justify-center items-center w-6 h-6"
            aria-hidden="true"
          >
            <div
              style={{
                background: '#fff',
                height: '2px',
                width: '18px',
                borderRadius: '1px',
                transform: isMenuOpen ? 'translateY(3px) rotate(45deg)' : 'translateY(-3px)',
                transition: '0.35s cubic-bezier(0, 0, 0, 1)'
              }}
            />
            <div
              style={{
                background: '#fff',
                height: '2px',
                width: '18px',
                borderRadius: '1px',
                transform: isMenuOpen ? 'translateY(-3px) rotate(-45deg)' : 'translateY(3px)',
                transition: '0.35s cubic-bezier(0, 0, 0, 1)'
              }}
            />
          </div>

          <button
            type="button"
            aria-label={isMenuOpen ? 'Close navigation menu' : 'Open navigation menu'}
            aria-expanded={Boolean(isMenuOpen)}
            aria-controls="primary-navigation"
            onClick={() => onOpenMenu?.()}
            className="absolute inset-0 z-10 cursor-pointer bg-transparent border-0 p-0 outline-none"
          />
        </div>
      </div>
    </header>
  );
}
