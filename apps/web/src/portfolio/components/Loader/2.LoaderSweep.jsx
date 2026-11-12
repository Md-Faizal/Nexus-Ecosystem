import React from 'react';
import { m } from 'framer-motion';
import { LOADER_THEME } from './loaderConfig';

/**
 * LoaderSweep - High-Performance 5 -> 3 -> 1 Overlapped Sweep & Card Formation
 * 
 * Choreography (Cinematic 1.45s Pacing):
 *   Stage 1 (0ms - 650ms): 5 distinct horizontal bars (14.5% height each) clearly sweep across.
 *   Stage 2 (650ms - 1050ms): Bars 2 & 4 merge into Bar 3 -> 3 distinct panels (20%, 60%, 20%).
 *   Stage 3 (1050ms - 1450ms): Center panel merges with Bars 1 & 5 into 1 full solid card.
 *   Stage 4 (1450ms): White border locks into glowing frame with hero dwell.
 */
export default function LoaderSweep({ 
  solidMerged, 
  isTransitioning, 
  forcePhase, 
  phase,
  sweepProgress = null,
  speed = 1 
}) {
  const spd = speed || 1;
  const isScrubMode = sweepProgress !== null && sweepProgress !== undefined;

  if (!isTransitioning) return null;

  const isForceVisible = forcePhase === 'LOGO_VISIBLE' || forcePhase === 'EXPANDING';

  // =========================================================================
  // SCRUB CALCULATIONS (0% -> 100%)
  // =========================================================================
  let sScaleX1 = 1, sScaleX2 = 1, sScaleX3 = 1, sScaleX4 = 1, sScaleX5 = 1;
  let sClip1 = 'inset(0% 0% 80% 0%)';
  let sClip2 = 'inset(20% 0% 60% 0%)';
  let sClip3 = 'inset(0% 0% 0% 0%)';
  let sClip4 = 'inset(60% 0% 20% 0%)';
  let sClip5 = 'inset(80% 0% 0% 0%)';
  let sBorderPathLength = 1;
  let sSolidOpacity = 0;
  let sBarsOpacity = 1;

  if (isScrubMode) {
    const t = Math.min(100, Math.max(0, Number(sweepProgress))) / 100;

    if (t <= 0.45) {
      // Sub-Phase 1: 5 Distinct Lines Sweep In (0% -> 45%)
      const r1 = t / 0.45;
      sScaleX1 = Math.min(1, r1 * 1.15);
      sScaleX2 = Math.min(1, Math.max(0, (r1 - 0.05) * 1.15));
      sScaleX3 = Math.min(1, Math.max(0, (r1 - 0.10) * 1.15));
      sScaleX4 = Math.min(1, Math.max(0, (r1 - 0.05) * 1.15));
      sScaleX5 = Math.min(1, r1 * 1.15);

      sClip1 = `inset(0% ${(1 - sScaleX1) * 100}% 85.5% 0%)`;
      sClip2 = `inset(21.3% 0% 64.2% ${(1 - sScaleX2) * 100}%)`;
      sClip3 = `inset(42.7% ${(1 - sScaleX3) * 100}% 42.8% 0%)`;
      sClip4 = `inset(64.1% 0% 21.4% ${(1 - sScaleX4) * 100}%)`;
      sClip5 = `inset(85.5% ${(1 - sScaleX5) * 100}% 0% 0%)`;

      sBorderPathLength = r1 * 0.45;
      sSolidOpacity = 0;
      sBarsOpacity = 1;
    } else if (t <= 0.72) {
      // Sub-Phase 2: Bar 2 & 4 merge into Bar 3 (45% -> 72% = 5 -> 3 Panels)
      const r2 = (t - 0.45) / 0.27;

      sClip1 = 'inset(0% 0% 85.5% 0%)';

      const top2 = 21.3 - 1.3 * r2;
      const bot2 = 64.2 - 4.2 * r2;
      sClip2 = `inset(${top2}% 0% ${bot2}% 0%)`;

      const top3 = 42.7 - 22.7 * r2;
      const bot3 = 42.8 - 22.8 * r2;
      sClip3 = `inset(${top3}% 0% ${bot3}% 0%)`;

      const top4 = 64.1 - 4.1 * r2;
      const bot4 = 21.4 - 1.4 * r2;
      sClip4 = `inset(${top4}% 0% ${bot4}% 0%)`;

      sClip5 = 'inset(85.5% 0% 0% 0%)';

      sBorderPathLength = 0.45 + r2 * 0.35;
      sSolidOpacity = 0;
      sBarsOpacity = 1;
    } else {
      // Sub-Phase 3: Merged Center merges with Bar 1 & Bar 5 (72% -> 100% = 3 -> 1 Solid Card)
      const r3 = (t - 0.72) / 0.28;

      const bot1 = 85.5 - 5.5 * r3;
      sClip1 = `inset(0% 0% ${bot1}% 0%)`;

      sClip2 = 'inset(20% 0% 60% 0%)';

      const top3 = 20 - 20 * r3;
      const bot3 = 20 - 20 * r3;
      sClip3 = `inset(${top3}% 0% ${bot3}% 0%)`;

      sClip4 = 'inset(60% 0% 20% 0%)';

      const top5 = 85.5 - 5.5 * r3;
      sClip5 = `inset(${top5}% 0% 0% 0%)`;

      sBorderPathLength = 0.80 + r3 * 0.20;
      sSolidOpacity = r3 >= 0.95 ? 1 : 0;
      sBarsOpacity = r3 >= 0.95 ? 0 : 1;
    }
  }

  const isCardSolid = isScrubMode ? sSolidOpacity === 1 : (solidMerged || isForceVisible);

  return (
    <>
      {/* Outer Rounded Container */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          borderRadius: 'clamp(20px, 2.5vw, 32px)',
          overflow: 'hidden',
          boxShadow: isCardSolid
            ? '0 12px 45px rgba(0, 0, 0, 0.16), 0 0 25px rgba(255, 255, 255, 0.35), inset 0 0 20px rgba(255, 255, 255, 0.25)'
            : 'none',
          transition: 'box-shadow 0.4s ease-out'
        }}
      >
        {/* BASE LAYER: SOLID MASTER CARD GRADIENT */}
        <m.div
          animate={
            isScrubMode
              ? { opacity: sSolidOpacity }
              : { opacity: solidMerged || isForceVisible ? 1 : 0 }
          }
          transition={
            isScrubMode 
              ? { duration: 0 }
              : { duration: 0.3 / spd, ease: 'easeOut' }
          }
          style={{
            position: 'absolute',
            inset: 0,
            background: LOADER_THEME.cardGradient,
            opacity: isCardSolid ? 1 : 0,
            zIndex: 13
          }}
        />

        {/* 5 -> 3 -> 1 DYNAMIC STAGED SWEEP BARS (Paced & Visually Distinct) */}
        <m.div
          animate={
            isScrubMode
              ? { opacity: sBarsOpacity }
              : { opacity: solidMerged || isForceVisible ? 0 : 1 }
          }
          transition={
            isScrubMode
              ? { duration: 0 }
              : { duration: 0.3 / spd, ease: 'easeOut' }
          }
          style={{
            width: '100%',
            height: '100%',
            position: 'relative',
            zIndex: 12,
            pointerEvents: 'none'
          }}
        >
          {/* BAR 1 (Top Bar - Sweeps Left -> Right) */}
          <m.div
            initial={isScrubMode ? false : { clipPath: 'inset(0% 100% 85.5% 0%)' }}
            animate={
              isScrubMode
                ? { clipPath: sClip1 }
                : {
                    clipPath: [
                      'inset(0% 100% 85.5% 0%)',
                      'inset(0% 0% 85.5% 0%)',
                      'inset(0% 0% 85.5% 0%)',
                      'inset(0% 0% 80% 0%)'
                    ]
                  }
            }
            transition={
              isScrubMode
                ? { duration: 0 }
                : {
                    duration: 1.45 / spd,
                    times: [0, 0.45, 0.72, 1],
                    ease: [0.22, 1, 0.36, 1]
                  }
            }
            style={{
              position: 'absolute',
              inset: 0,
              background: LOADER_THEME.cardGradient,
              clipPath: isScrubMode ? sClip1 : 'inset(0% 100% 85.5% 0%)',
              willChange: 'clip-path'
            }}
          />

          {/* BAR 2 (Upper Mid Bar - Sweeps Right -> Left) */}
          <m.div
            initial={isScrubMode ? false : { clipPath: 'inset(21.3% 0% 64.2% 100%)' }}
            animate={
              isScrubMode
                ? { clipPath: sClip2 }
                : {
                    clipPath: [
                      'inset(21.3% 0% 64.2% 100%)',
                      'inset(21.3% 0% 64.2% 0%)',
                      'inset(20% 0% 60% 0%)',
                      'inset(20% 0% 60% 0%)'
                    ]
                  }
            }
            transition={
              isScrubMode
                ? { duration: 0 }
                : {
                    duration: 1.45 / spd,
                    times: [0, 0.45, 0.72, 1],
                    ease: [0.22, 1, 0.36, 1]
                  }
            }
            style={{
              position: 'absolute',
              inset: 0,
              background: LOADER_THEME.cardGradient,
              clipPath: isScrubMode ? sClip2 : 'inset(21.3% 0% 64.2% 100%)',
              willChange: 'clip-path'
            }}
          />

          {/* BAR 3 (Center Panel - Sweeps Left -> Right, then Expands) */}
          <m.div
            initial={isScrubMode ? false : { clipPath: 'inset(42.7% 100% 42.8% 0%)' }}
            animate={
              isScrubMode
                ? { clipPath: sClip3 }
                : {
                    clipPath: [
                      'inset(42.7% 100% 42.8% 0%)',
                      'inset(42.7% 0% 42.8% 0%)',
                      'inset(20% 0% 20% 0%)',
                      'inset(0% 0% 0% 0%)'
                    ]
                  }
            }
            transition={
              isScrubMode
                ? { duration: 0 }
                : {
                    duration: 1.45 / spd,
                    times: [0, 0.45, 0.72, 1],
                    ease: [0.22, 1, 0.36, 1]
                  }
            }
            style={{
              position: 'absolute',
              inset: 0,
              background: LOADER_THEME.cardGradient,
              clipPath: isScrubMode ? sClip3 : 'inset(42.7% 100% 42.8% 0%)',
              willChange: 'clip-path'
            }}
          />

          {/* BAR 4 (Lower Mid Bar - Sweeps Right -> Left) */}
          <m.div
            initial={isScrubMode ? false : { clipPath: 'inset(64.1% 0% 21.4% 100%)' }}
            animate={
              isScrubMode
                ? { clipPath: sClip4 }
                : {
                    clipPath: [
                      'inset(64.1% 0% 21.4% 100%)',
                      'inset(64.1% 0% 21.4% 0%)',
                      'inset(60% 0% 20% 0%)',
                      'inset(60% 0% 20% 0%)'
                    ]
                  }
            }
            transition={
              isScrubMode
                ? { duration: 0 }
                : {
                    duration: 1.45 / spd,
                    times: [0, 0.45, 0.72, 1],
                    ease: [0.22, 1, 0.36, 1]
                  }
            }
            style={{
              position: 'absolute',
              inset: 0,
              background: LOADER_THEME.cardGradient,
              clipPath: isScrubMode ? sClip4 : 'inset(64.1% 0% 21.4% 100%)',
              willChange: 'clip-path'
            }}
          />

          {/* BAR 5 (Bottom Bar - Sweeps Left -> Right) */}
          <m.div
            initial={isScrubMode ? false : { clipPath: 'inset(85.5% 100% 0% 0%)' }}
            animate={
              isScrubMode
                ? { clipPath: sClip5 }
                : {
                    clipPath: [
                      'inset(85.5% 100% 0% 0%)',
                      'inset(85.5% 0% 0% 0%)',
                      'inset(85.5% 0% 0% 0%)',
                      'inset(80% 0% 0% 0%)'
                    ]
                  }
            }
            transition={
              isScrubMode
                ? { duration: 0 }
                : {
                    duration: 1.45 / spd,
                    times: [0, 0.45, 0.72, 1],
                    ease: [0.22, 1, 0.36, 1]
                  }
            }
            style={{
              position: 'absolute',
              inset: 0,
              background: LOADER_THEME.cardGradient,
              clipPath: isScrubMode ? sClip5 : 'inset(85.5% 100% 0% 0%)',
              willChange: 'clip-path'
            }}
          />
        </m.div>
      </div>

      {/* PIXEL-PERFECT DUAL-CORNER SVG BORDER TRACE */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          pointerEvents: 'none',
          zIndex: 25
        }}
      >
        <svg
          style={{
            position: 'absolute',
            inset: 0,
            width: '100%',
            height: '100%',
            overflow: 'visible'
          }}
        >
          {/* Stroke 1: Top-Right Corner Origin */}
          <m.rect
            x="2"
            y="2"
            width="calc(100% - 4px)"
            height="calc(100% - 4px)"
            rx="clamp(20px, 2.5vw, 32px)"
            fill="none"
            stroke={isCardSolid ? 'rgba(255, 255, 255, 0.88)' : LOADER_THEME.borderColor}
            strokeWidth="3.5"
            strokeLinecap="round"
            pathLength="100"
            animate={
              isScrubMode
                ? {
                    strokeDasharray: `${50 * sBorderPathLength} ${100 - 50 * sBorderPathLength}`,
                    strokeDashoffset: -(25 - 25 * sBorderPathLength)
                  }
                : {
                    strokeDasharray: isTransitioning ? ['0 100', '50 50'] : '0 100',
                    strokeDashoffset: isTransitioning ? [-25, 0] : -25
                  }
            }
            transition={
              isScrubMode
                ? { duration: 0 }
                : { duration: 1.45 / spd, ease: [0.22, 1, 0.36, 1] }
            }
            style={{
              filter: (isCardSolid && phase !== 'EXPANDING') ? 'drop-shadow(0 0 10px rgba(255, 255, 255, 0.7))' : 'none',
              transition: 'stroke 0.3s, filter 0.3s'
            }}
          />

          {/* Stroke 2: Bottom-Left Corner Origin */}
          <m.rect
            x="2"
            y="2"
            width="calc(100% - 4px)"
            height="calc(100% - 4px)"
            rx="clamp(20px, 2.5vw, 32px)"
            fill="none"
            stroke={isCardSolid ? 'rgba(255, 255, 255, 0.88)' : LOADER_THEME.borderColor}
            strokeWidth="3.5"
            strokeLinecap="round"
            pathLength="100"
            animate={
              isScrubMode
                ? {
                    strokeDasharray: `${50 * sBorderPathLength} ${100 - 50 * sBorderPathLength}`,
                    strokeDashoffset: -(75 - 25 * sBorderPathLength)
                  }
                : {
                    strokeDasharray: isTransitioning ? ['0 100', '50 50'] : '0 100',
                    strokeDashoffset: isTransitioning ? [-75, -50] : -75
                  }
            }
            transition={
              isScrubMode
                ? { duration: 0 }
                : { duration: 1.45 / spd, ease: [0.22, 1, 0.36, 1] }
            }
            style={{
              filter: (isCardSolid && phase !== 'EXPANDING') ? 'drop-shadow(0 0 10px rgba(255, 255, 255, 0.7))' : 'none',
              transition: 'stroke 0.3s, filter 0.3s'
            }}
          />
        </svg>
      </div>

      {/* FROSTED WHITE OUTER OUTLINE WITH LUMINOUS GLOW */}
      <m.div
        initial={{ opacity: 0 }}
        animate={{ 
          opacity: (isCardSolid || phase === 'EXPANDING') ? 1 : 0
        }}
        transition={{ duration: 0.35 / spd, ease: 'easeOut' }}
        style={{
          position: 'absolute',
          inset: 0,
          borderRadius: 'clamp(20px, 2.5vw, 32px)',
          border: '3px solid rgba(255, 255, 255, 0.92)',
          boxShadow: phase === 'EXPANDING' ? 'none' : LOADER_THEME.cardGlow,
          pointerEvents: 'none',
          zIndex: 26
        }}
      />
    </>
  );
}
