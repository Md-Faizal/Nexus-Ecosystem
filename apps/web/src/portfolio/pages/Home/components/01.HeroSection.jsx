import React, { useState, useEffect, useRef, useCallback } from 'react';
import { motion } from 'framer-motion';
import AmberVaultHeroCanvas from './AmberVaultHeroCanvas';
import Marquee1Section from './02.Marquee1Section';

const THEME_COLOR = '#A30033'; // Deep crimson red matching the logo

const MONOGRAM_PATHS = {
  wingDown: "M43.82,70.3l64.52.05,5.42-12.76-32.78-.36,41.09-19.08,7.56-18.43,27.69.85-25.84,61.15c-2.63,6.23-11.43,8.64-18.18,8.65l-111.12.13,41.64-20.2Z",
  wingUp: "M129.63,19.71l-65.22.74-5.49,12.65,32.87.55-41.14,19.05-6.83,17.6-28.38-.02L41.22,9.13C43.67,3.32,52.08.55,58.48.55l110.96-.05-39.82,19.21Z",
  centerPoly: "44.55 69.8 51.04 53.07 93.98 33.19 59.67 32.61 64.74 20.95 128.88 20.22 121.68 37.77 78.77 57.7 113 58.08 108.01 69.85 44.55 69.8",
  centerWhite: "M128.13,20.73l-6.84,16.67-40.73,18.92-4,1.86,4.41.05,31.28.34-4.57,10.78-62.4-.05,6.16-15.86,40.77-18.88,3.96-1.83-4.36-.07-31.37-.53,4.64-10.68,63.06-.71M129.63,19.71l-65.22.74-5.49,12.65,32.87.55-41.14,19.05-6.83,17.6,64.52.05,5.42-12.76-32.78-.36,41.09-19.08,7.56-18.43h0Z"
};

function ConcentricBackground({
  isRevealed = true,
  isTransitioning = false,
  heroStage = 'logo' // 'logo' | 'video-portal' | 'video-fullscreen'
}) {
  const isRadiating = isRevealed;
  const isStableView = isTransitioning;
  const isFullscreenVideo = !isTransitioning && heroStage === 'video-fullscreen';
  const isPortalActive = !isTransitioning && (heroStage === 'video-portal' || heroStage === 'video-fullscreen');

  const centerX = 400, centerY = 400;
  const ringCount = 40;
  const maxRadius = 390;
  const minRadius = 148; // Large middle circle framing the logo & portal matching reference

  const rings = [];
  for (let i = 0; i < ringCount; i++) {
    const t = i / (ringCount - 1);
    const radius = minRadius + t * (maxRadius - minRadius);

    // slight center drift so rings aren't perfectly concentric
    const drift = 10 * Math.sin(t * Math.PI);
    const cx = centerX + drift;
    const cy = centerY - drift * 0.3;

    // Thinner alternating rings with high-density texture matching reference
    const isLight = i % 2 === 0;
    const stroke = isLight ? "rgba(255,255,255,0.9)" : "rgba(138,146,158,0.42)";
    const strokeWidth = isLight ? 1.6 : 1.0;
    const edgeFade = 1 - Math.pow(t, 2) * 0.55;

    rings.push({
      index: i,
      t,
      cx,
      cy,
      radius,
      stroke,
      strokeWidth,
      targetOpacity: Number(edgeFade.toFixed(2))
    });
  }

  return (
    <div
      className="absolute inset-0 w-full h-full pointer-events-none overflow-hidden flex items-center justify-center"
    >
      <svg
        viewBox="0 0 800 800"
        className="absolute pointer-events-none"
        style={{
          top: '50%',
          left: '50%',
          transform: 'translate(-50%, -50%)',
          width: '215vmin',
          height: '215vmin'
        }}
        xmlns="http://www.w3.org/2000/svg"
      >
        <defs>
          {/* Porcelain Studio Base Radial Gradient */}
          <radialGradient id="porcelain-studio-gradient" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="#f6f8fb" />
            <stop offset="45%" stopColor="#edf0f5" />
            <stop offset="80%" stopColor="#E5E8ED" />
            <stop offset="100%" stopColor="#d8dde5" />
          </radialGradient>

          {/* Soft Directional Sheen */}
          <linearGradient id="porcelain-sheen-gradient" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="rgba(255,255,255,0.8)" />
            <stop offset="45%" stopColor="rgba(255,255,255,0)" />
            <stop offset="55%" stopColor="rgba(0,0,0,0.02)" />
            <stop offset="100%" stopColor="rgba(255,255,255,0.6)" />
          </linearGradient>

          {/* Soft Edge Vignette */}
          <radialGradient id="porcelain-vignette-gradient" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="rgba(255,255,255,0)" />
            <stop offset="50%" stopColor="rgba(255,255,255,0)" />
            <stop offset="80%" stopColor="rgba(210,213,218,0.2)" />
            <stop offset="100%" stopColor="rgba(180,186,195,0.35)" />
          </radialGradient>

          {/* Concentric Ridge Micro Drop Shadow Filter */}
          <filter id="groove-shadow" x="-10%" y="-10%" width="120%" height="120%">
            <feDropShadow dx="0.5" dy="1" stdDeviation="0.8" floodColor="rgba(0,0,0,0.06)" />
          </filter>

          {/* DYNAMIC CONCENTRIC APERTURE MASK: 
              - White = visible porcelain canvas
              - Black = cutout hole revealing 3D scene underneath
              - Stage 1 (logo): 100% white porcelain canvas
              - Stage 2 (video-portal): Center circle (r=148) turns black, opening the center portal
              - Stage 3 (video-fullscreen): Each ring band expands to black sequentially,
                dissolving the porcelain band-by-band into the full 3D frame
          */}
          <mask id="porcelain-aperture-mask">
            {/* Base White Canvas = 100% Opaque Porcelain Background */}
            <rect x="-1000" y="-1000" width="2800" height="2800" fill="#ffffff" />

            {/* Center Portal Cutout: radius = 148 (matches innermost ring 0 exactly) */}
            <motion.circle
              cx={centerX}
              cy={centerY}
              r={minRadius}
              fill="#000000"
              initial={false}
              animate={{
                opacity: isPortalActive ? 1 : 0
              }}
              transition={{
                duration: isStableView ? 0 : 0.5,
                ease: [0.16, 1, 0.3, 1]
              }}
            />

            {/* Concentric Aperture Unmasking Bands */}
            {rings.map((ring, i) => {
              const nextRadius = rings[i + 1] ? rings[i + 1].radius : 1600;
              const bandUnmaskDelay = 0.05 + i * 0.028;

              return (
                <motion.circle
                  key={`mask-band-${i}`}
                  cx={ring.cx}
                  cy={ring.cy}
                  r={nextRadius}
                  fill="#000000"
                  initial={false}
                  animate={{
                    opacity: isFullscreenVideo ? 1 : 0
                  }}
                  transition={{
                    duration: isStableView ? 0 : (isFullscreenVideo ? 0.32 : 0),
                    delay: isStableView ? 0 : (isFullscreenVideo ? bandUnmaskDelay : 0),
                    ease: [0.22, 1, 0.36, 1]
                  }}
                />
              );
            })}
          </mask>
        </defs>

        {/* 1. MASKED PORCELAIN STUDIO BACKGROUND LAYERS */}
        <g mask="url(#porcelain-aperture-mask)">
          {/* Porcelain studio base */}
          <rect x="-1000" y="-1000" width="2800" height="2800" fill="url(#porcelain-studio-gradient)" />
          {/* Soft directional sheen */}
          <rect x="-1000" y="-1000" width="2800" height="2800" fill="url(#porcelain-sheen-gradient)" opacity="0.4" />
          {/* Soft edge vignette */}
          <rect x="-1000" y="-1000" width="2800" height="2800" fill="url(#porcelain-vignette-gradient)" />
        </g>

        {/* 2. THE 40 CONCENTRIC DEPTH RINGS (Carved onto Porcelain Surface) */}
        <g
          fill="none"
          filter="url(#groove-shadow)"
          style={{
            animation: 'heroSpin 40s linear infinite',
            transformOrigin: '400px 400px'
          }}
        >
          {rings.map((ring) => {
            const appearDelay = ring.index === 0 ? 0 : 0.05 + ring.index * 0.015;
            const collapseDelay = ring.index === 0 ? 0 : 0.05 + ring.index * 0.020;
            const shouldShowRing = (!isFullscreenVideo && ring.index === 0) || isStableView || (isRadiating && !isFullscreenVideo);

            return (
              <motion.circle
                key={`ring-${ring.index}`}
                cx={ring.cx}
                cy={ring.cy}
                r={ring.radius}
                stroke={ring.stroke}
                strokeWidth={ring.strokeWidth}
                initial={isStableView ? false : {
                  opacity: ring.index === 0 ? ring.targetOpacity : 0,
                  strokeWidth: ring.index === 0 ? ring.strokeWidth : 0
                }}
                animate={shouldShowRing ? {
                  opacity: ring.targetOpacity,
                  strokeWidth: ring.strokeWidth
                } : {
                  opacity: 0,
                  strokeWidth: 0
                }}
                transition={{
                  duration: isStableView ? 0 : (shouldShowRing ? 0.75 : 0.32),
                  delay: isStableView ? 0 : (shouldShowRing ? appearDelay : collapseDelay),
                  ease: [0.22, 1, 0.36, 1]
                }}
              />
            );
          })}
        </g>
      </svg>

      <style>{`
        @keyframes heroSpin {
          from { transform: rotate(0deg); }
          to   { transform: rotate(360deg); }
        }
        @media (prefers-reduced-motion: reduce) {
          svg g { animation: none !important; }
        }
      `}</style>
    </div>
  );
}

export default function HeroSection({
  isRevealed,
  onOpenMenu,
  isMenuOpen,
  onNavigate,
  onLogoClick,
  isTransitioning = false,
  isPageReady = true,
  onHeaderStageChange
}) {
  const [heroStage, setHeroStage] = useState('logo');
  const [headerLogoStage, setHeaderLogoStage] = useState('hidden'); // 'hidden' | 'red' | 'lime'
  const sectionRef = useRef(null);
  const monitorPortalRef = useRef(null);
  const [scrollProgress, setScrollProgress] = useState(0);

  // Dedicated progress reference for active scroll locking & threshold snapping
  const progressState = useRef({
    current: 0,
    target: 0,
    isLocked: true,
    lastTouchY: 0,
    snapTimer: null
  });

  // If page loads already scrolled down, jump directly to full frame
  useEffect(() => {
    if (typeof window !== 'undefined' && window.scrollY > 50) {
      setHeroStage('video-fullscreen');
      setHeaderLogoStage('lime');
      progressState.current.current = 1.0;
      progressState.current.target = 1.0;
      setScrollProgress(1.0);
    }
  }, []);

  // 1. Stage Timeline Sequence (Harmonized Portal Hold & Aperture Reveal)
  useEffect(() => {
    if (!isRevealed || isTransitioning) {
      setHeroStage('logo');
      setHeaderLogoStage('hidden');
      return;
    }

    // T = 0.5s: Center logo disappears, portal opens revealing close-up monitor view
    const portalTimer = setTimeout(() => {
      setHeroStage('video-portal');
      setHeaderLogoStage('red');
    }, 500);

    // T = 1.0s: Header logo turns neon lime
    const limeLogoTimer = setTimeout(() => {
      setHeaderLogoStage('lime');
    }, 1000);

    // T = 1.6s: Concentric circles unmask outward into full frame
    // Held for 1.1s so user clearly sees the monitor UI before the synchronous dolly-out starts
    const fullscreenVideoTimer = setTimeout(() => {
      setHeroStage('video-fullscreen');
    }, 1600);

    return () => {
      clearTimeout(portalTimer);
      clearTimeout(limeLogoTimer);
      clearTimeout(fullscreenVideoTimer);
    };
  }, [isRevealed, isTransitioning]);

  const isFullscreen = !isTransitioning && heroStage === 'video-fullscreen';
  const showCenterLogo = !isFullscreen && heroStage === 'logo';
  const showTopLeftLogo = isRevealed && !isTransitioning && (headerLogoStage === 'red' || headerLogoStage === 'lime');

  // Notify parent Home component of header stage changes
  useEffect(() => {
    onHeaderStageChange?.({
      showLogo: showTopLeftLogo,
      stage: headerLogoStage === 'lime' ? 'lime' : 'red'
    });
  }, [showTopLeftLogo, headerLogoStage, onHeaderStageChange]);

  // 2. Threshold Snap & Bounce-back Evaluator
  const scheduleThresholdSnap = useCallback((direction) => {
    const state = progressState.current;
    if (state.snapTimer) clearTimeout(state.snapTimer);

    state.snapTimer = setTimeout(() => {
      const cur = state.target;
      // Only evaluate if within intermediate scrub zone
      if (cur > 0.01 && cur < 0.99) {
        const SCROLL_THRESHOLD = 0.40;
        const REVERSE_THRESHOLD = 0.70;
        const SCROLL_THRESHOLD = 0.35;
        const REVERSE_THRESHOLD = 0.88;

        if (direction === 'down') {
          if (cur >= SCROLL_THRESHOLD) {
            // Threshold exceeded -> auto-commit all the way forward into Section 2 (1.0)
            state.target = 1.0;
          } else {
            // Below threshold -> bounce back smoothly to wide vault view (0.0)
            state.target = 0.0;
          }
        } else if (direction === 'up') {
          if (cur <= REVERSE_THRESHOLD) {
            // Reverse threshold exceeded -> auto-complete pullback to wide vault view (0.0)
            state.target = 0.0;
          } else {
            state.target = 1.0;
          }
        } else {
          state.target = cur >= 0.40 ? 1.0 : 0.0;
          state.target = cur >= 0.35 ? 1.0 : 0.0;
        }
      }
    }, 140);
  }, []);

  // 3. Bidirectional Scrollbar Event Listeners ('hero-zoom-set', 'hero-zoom-snap')
  useEffect(() => {
    const handleHeroZoomSet = (e) => {
      const targetZoom = Number(e.detail?.zoom);
      if (!isNaN(targetZoom)) {
        progressState.current.target = Math.min(1, Math.max(0, targetZoom));
        if (targetZoom < 0.999) {
          progressState.current.isLocked = true;
          window.lenis?.stop();
        }
      }
    };

    const handleHeroZoomSnap = (e) => {
      const snapTarget = Number(e.detail?.target);
      if (!isNaN(snapTarget)) {
        progressState.current.target = snapTarget;
        if (snapTarget >= 0.999) {
          progressState.current.isLocked = false;
          window.lenis?.start();
        } else {
          progressState.current.isLocked = true;
          window.lenis?.stop();
        }
      }
    };

    window.addEventListener('hero-zoom-set', handleHeroZoomSet);
    window.addEventListener('hero-zoom-snap', handleHeroZoomSnap);

    return () => {
      window.removeEventListener('hero-zoom-set', handleHeroZoomSet);
      window.removeEventListener('hero-zoom-snap', handleHeroZoomSnap);
    };
  }, []);

  // 4. Active Scroll-Lock Mechanism: Intercept wheel/touch with threshold snapping
  useEffect(() => {
    const state = progressState.current;

    const handleWheel = (e) => {
      if (heroStage !== 'video-fullscreen' || isMenuOpen) return;

      const isAtTop = window.scrollY <= 10;

      // Phase 1: In Hero scrub zone (progress 0.0 to 0.999) while at top of page: Lock page & zoom into monitor
      if (isAtTop && state.current < 0.999) {
        if (e.deltaY > 0 || state.current > 0.005) {
          e.preventDefault();
          window.lenis?.stop();
          state.isLocked = true;

          const speed = 0.00095;
          const speed = 0.0016;
          state.target = Math.min(1, Math.max(0, state.target + e.deltaY * speed));
          scheduleThresholdSnap(e.deltaY > 0 ? 'down' : 'up');
        }
      }
      // Phase 2: Zoom complete (progress >= 0.999) and user scrolls UP while at top of page:
      // Re-engage zoom-out pullback into the 3D room!
      else if (isAtTop && state.current >= 0.999 && e.deltaY < 0) {
        e.preventDefault();
        window.lenis?.stop();
        state.isLocked = true;

        const speed = 0.00095;
        const speed = 0.0024;
        state.target = Math.min(1, Math.max(0, state.target + e.deltaY * speed));
        scheduleThresholdSnap('up');
      }
      // Phase 3: Zoom complete (progress >= 0.999) and user scrolls DOWN:
      // Normal page scrolling proceeds smoothly through downstream sections!
      else if (state.current >= 0.999) {
        state.isLocked = false;
        window.lenis?.start();
      }
    };

    const handleTouchStart = (e) => {
      if (e.touches.length > 0) {
        state.lastTouchY = e.touches[0].clientY;
      }
    };

    const handleTouchMove = (e) => {
      if (heroStage !== 'video-fullscreen' || isMenuOpen) return;
      if (e.touches.length === 0) return;

      const curY = e.touches[0].clientY;
      const deltaY = state.lastTouchY - curY;
      state.lastTouchY = curY;

      const isAtTop = window.scrollY <= 10;

      if (isAtTop && state.current < 0.999) {
        if (deltaY > 0 || state.current > 0.005) {
          e.preventDefault();
          window.lenis?.stop();
          state.isLocked = true;
          state.target = Math.min(1, Math.max(0, state.target + deltaY * 0.0022));
          scheduleThresholdSnap(deltaY > 0 ? 'down' : 'up');
        }
      } else if (isAtTop && state.current >= 0.999 && deltaY < 0) {
        e.preventDefault();
        window.lenis?.stop();
        state.isLocked = true;
        state.target = Math.min(1, Math.max(0, state.target + deltaY * 0.0022));
        state.target = Math.min(1, Math.max(0, state.target + deltaY * 0.0032));
        scheduleThresholdSnap('up');
      } else if (state.current >= 0.999) {
        state.isLocked = false;
        window.lenis?.start();
      }
    };

    window.addEventListener('wheel', handleWheel, { passive: false });
    window.addEventListener('touchstart', handleTouchStart, { passive: true });
    window.addEventListener('touchmove', handleTouchMove, { passive: false });

    return () => {
      window.removeEventListener('wheel', handleWheel);
      window.removeEventListener('touchstart', handleTouchStart);
      window.removeEventListener('touchmove', handleTouchMove);
      if (state.snapTimer) clearTimeout(state.snapTimer);
      window.lenis?.start();
    };
  }, [heroStage, isMenuOpen, scheduleThresholdSnap]);

  // 5. Smooth Damping Render Loop for Frame Scrubbing (60/120Hz)
  useEffect(() => {
    let animId;
    const state = progressState.current;

    const tick = () => {
      animId = requestAnimationFrame(tick);

      const diff = state.target - state.current;
      if (Math.abs(diff) > 0.0004) {
        state.current += diff * 0.14;
        setScrollProgress(state.current);
        window.dispatchEvent(new CustomEvent('hero-zoom', { detail: { progress: state.current } }));
      } else if (state.current !== state.target) {
        state.current = state.target;
        setScrollProgress(state.current);
        window.dispatchEvent(new CustomEvent('hero-zoom', { detail: { progress: state.current } }));
      }
    };

    tick();
    return () => cancelAnimationFrame(animId);
  }, []);

  // Compute Monitor Screen Zoom & Power-On Parameters
  const portalScale = 0.26 + Math.pow(scrollProgress, 1.15) * 0.74;
  const screenContentOpacity = Math.min(1, Math.max(0, (scrollProgress - 0.05) / 0.11));
  const edgeTaper = scrollProgress >= 0.82 ? Math.max(0, 1 - (scrollProgress - 0.82) / 0.18) : 1;
  const portalBorderRadius = 12 * edgeTaper;
  const portalBorderWidth = 3 * edgeTaper;
  const portalGlow = (screenContentOpacity > 0 ? 1 : 0.3) * edgeTaper;

  return (
    <section
      ref={sectionRef}
      id="section-hero"
      className="relative w-screen h-svh md:h-screen flex items-center justify-center overflow-hidden bg-[#0a0a0e]"
      style={{
        position: 'relative',
        width: '100vw',
        height: '100vh',
        backgroundColor: '#0a0a0e'
      }}
    >
      {/* 1. PHOTOREALISTIC 4K CYCLES FRAME CANVAS (At z-0, visible behind aperture) */}
      <div
        className="absolute inset-0 w-full h-full z-0 flex items-center justify-center overflow-hidden pointer-events-none"
      >
        <AmberVaultHeroCanvas
          isRevealed={isRevealed}
          heroStage={heroStage}
          scrollProgress={scrollProgress}
        />
      </div>

      {/* 2. CONCENTRIC PORCELAIN BACKGROUND & DEPTH CIRCLES WITH STEP-BY-STEP APERTURE MASK */}
      <div
        className="absolute inset-0 w-full h-full z-10 pointer-events-none transition-opacity duration-500"
        style={{ opacity: isFullscreen ? (scrollProgress > 0.02 ? Math.max(0, 1 - scrollProgress * 4.0) : 1) : 1 }}
      >
        <ConcentricBackground
          isRevealed={isRevealed}
          isTransitioning={isTransitioning}
          heroStage={heroStage}
        />
      </div>

      {/* 3. MONITOR SCREEN PORTAL - POWERS ON & ZOOMS IN FROM DESK TO FULLSCREEN */}
      {heroStage === 'video-fullscreen' && (
        <div
          ref={monitorPortalRef}
          className="absolute pointer-events-none z-25 will-change-transform flex items-center justify-center"
          style={{
            left: '50%',
            top: '50%',
            width: '100vw',
            height: '100vh',
            transform: `translate(-50%, -50%) scale(${portalScale})`,
            transformOrigin: 'center center',
            borderRadius: `${portalBorderRadius}px`,
            border: portalBorderWidth > 0 ? `${portalBorderWidth}px solid rgba(26, 26, 34, 0.95)` : 'none',
            boxShadow: portalGlow > 0 ? `0 0 ${portalGlow * 40}px rgba(245, 158, 11, ${portalGlow * 0.35}), 0 0 ${portalGlow * 80}px rgba(245, 158, 11, ${portalGlow * 0.12})` : 'none',
            overflow: 'hidden'
          }}
        >
          {/* Inner Screen Surface */}
          <div className="relative w-full h-full bg-[#0a0a0e] overflow-hidden">
            {/* A. Live Section 2 View (Fades in when powered on) */}
            <div
              className="w-full h-full overflow-hidden"
              style={{
                opacity: screenContentOpacity,
                filter: screenContentOpacity < 1 ? `brightness(${0.8 + screenContentOpacity * 0.3})` : 'none',
                pointerEvents: scrollProgress >= 0.999 ? 'auto' : 'none'
              }}
            >
              <Marquee1Section onNavigate={onNavigate} />
            </div>

            {/* B. Standby Glossy Glass Surface (Active when monitor is off) */}
            {screenContentOpacity < 0.99 && (
              <div
                className="absolute inset-0 pointer-events-none transition-opacity duration-200"
                style={{
                  opacity: 1 - screenContentOpacity,
                  background: 'linear-gradient(135deg, rgba(255,255,255,0.06) 0%, rgba(255,255,255,0.01) 40%, rgba(0,0,0,0.7) 100%)'
                }}
              >
                {/* Subtle amber standby pulse indicator in bottom right */}
                <div className="absolute bottom-4 right-4 flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-[#f59e0b] shadow-[0_0_8px_#f59e0b] animate-pulse" />
                </div>
              </div>
            )}

            {/* C. Power-On Boot Beam / Scanline Flash */}
            {scrollProgress >= 0.05 && scrollProgress < 0.20 && (
              <div
                className="absolute inset-0 pointer-events-none z-30 flex items-center justify-center"
                style={{
                  opacity: Math.sin(((scrollProgress - 0.05) / 0.15) * Math.PI)
                }}
              >
                <div className="w-full h-[2px] bg-[#f59e0b] shadow-[0_0_20px_#f59e0b,0_0_40px_#f59e0b]" />
              </div>
            )}

            {/* D. CRT Scanline Texture overlay while on the desk (fades out as camera approaches) */}
            {scrollProgress < 0.80 && screenContentOpacity > 0 && (
              <div
                className="absolute inset-0 pointer-events-none z-20 mix-blend-overlay"
                style={{
                  opacity: Math.max(0, 0.35 * (1 - scrollProgress / 0.8)),
                  backgroundImage: 'repeating-linear-gradient(0deg, rgba(0,0,0,0.2) 0px, rgba(0,0,0,0.2) 1px, transparent 1px, transparent 3px)'
                }}
              />
            )}
          </div>
        </div>
      )}

      {/* 4. MAIN CENTER RED MONOGRAM LOGO */}
      {/* 3. MAIN CENTER RED MONOGRAM LOGO */}
      <motion.div
        initial={{ opacity: 1, scale: 1, y: 0 }}
        animate={{
          opacity: showCenterLogo ? 1 : 0,
          scale: showCenterLogo ? 1 : 0.92,
          y: showCenterLogo ? 0 : -8
        }}
        transition={{
          duration: showCenterLogo ? (isTransitioning ? 0 : 0.4) : 0.45,
          ease: [0.16, 1, 0.3, 1]
        }}
        style={{
          position: 'relative',
          zIndex: 30,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          pointerEvents: showCenterLogo ? 'auto' : 'none'
        }}
      >
        <div style={{ width: 'clamp(180px, 22vw, 320px)', height: 'clamp(95px, 11.6vw, 170px)' }}>
          <svg viewBox="0 0 171.63 91" className="w-full h-full" style={{ overflow: 'visible' }}>
            <path d={MONOGRAM_PATHS.wingDown} fill={THEME_COLOR} />
            <path d={MONOGRAM_PATHS.wingUp} fill={THEME_COLOR} />
            <polygon points={MONOGRAM_PATHS.centerPoly} fill={THEME_COLOR} />
            <path d={MONOGRAM_PATHS.centerWhite} fill="#ffffff" />
          </svg>
        </div>
      </motion.div>
    </section>
  );
}
