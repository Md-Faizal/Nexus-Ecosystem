import React, { useState } from 'react';
import { m as motion } from 'framer-motion';
import { monogramComplete } from '../../assets';

const CLIP_PATH_SLANTED = 'polygon(10px 0, 100% 0, 100% calc(100% - 10px), calc(100% - 10px) 100%, 0 100%, 0 10px)';

export default function GlobalHeader({ isVisible = true, isMenuOpen, onToggleMenu, onNavigate }) {
  const [isMuted, setIsMuted] = useState(false);

  if (!isVisible) return null;

  return (
    <>
      {/* 1. FIXED BRAND LOGO (TOP-LEFT) WITH 40px TOP PADDING & HIGH Z-INDEX */}
      <motion.a
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
        data-header-brand-logo="true"
        data-no-click-sfx="true"
        aria-label="Mohammad Faizal - Home"
        className="fixed left-6 md:left-12 top-0 w-16 md:w-20 mix-blend-exclusion flex flex-col cursor-pointer transition-transform duration-300 hover:scale-105"
        style={{
          paddingTop: '40px',
          zIndex: 100000
        }}
        href="/"
        onClick={(e) => {
          e.preventDefault();
          onNavigate?.('home');
        }}
      >
        <img
          src={monogramComplete}
          alt="Mohammad Faizal"
          className="w-full h-auto drop-shadow-md"
          style={{ width: '100%', height: 'auto', display: 'block' }}
        />
      </motion.a>

      {/* 2. FIXED HEADER ACTION BUTTONS (TOP-RIGHT) AT 40px TOP & HIGH Z-INDEX */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
        className="fixed right-6 md:right-12 flex items-center gap-3"
        style={{
          top: '40px',
          zIndex: 100000
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
            onClick={() => onToggleMenu?.()}
            className="absolute inset-0 z-10 cursor-pointer bg-transparent border-0 p-0 outline-none"
          />
        </div>
      </motion.div>
    </>
  );
}
