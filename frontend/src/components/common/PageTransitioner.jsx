import React, { useState, useEffect } from 'react';
import { m as motion } from 'framer-motion';
import HyperspaceVFX from './HyperspaceVFX';

/**
 * PageTransitioner - Reusable 60 FPS Standalone Page Transition Package.
 * 
 * Used for navigating between different pages across the site (e.g., Home <-> Projects, Contact, etc.).
 * 
 * Features:
 *   1. Hardware-accelerated GPU transforms for buttery 60 FPS.
 *   2. Pitch black backdrop (#000000) with immersive Hyperspace VFX warp.
 *   3. Smooth Zoom Out -> Card Slide -> Zoom In choreography.
 *   4. Supports real-time Dev Hub scrubbing (`progress` 0..100) and live autonomous mode.
 *   5. Full speed multiplier scaling.
 * 
 * Props:
 *   - outgoing: React node for the page being exited
 *   - incoming: React node for the page being entered
 *   - isTransitioning: Trigger the transition animation
 *   - forcePhase: 'IDLE' | 'ZOOM_OUT' | 'SLIDING' | 'ZOOMING_IN' | 'DONE'
 *   - progress: Scrub percentage (0..100) or null
 *   - speed: Playback speed multiplier (default: 1)
 *   - onTransitionComplete: Callback fired when transition completes
 */
export default function PageTransitioner({ 
  outgoing, 
  incoming, 
  isTransitioning, 
  forcePhase,
  progress = null,
  speed = 1,
  onTransitionComplete 
}) {
  const [phase, setPhase] = useState(forcePhase || 'IDLE');
  const isScrubMode = progress !== null && progress !== undefined;
  const spd = speed || 1;

  useEffect(() => {
    if (forcePhase) {
      setPhase(forcePhase);
    } else if (isTransitioning) {
      setPhase((prev) => (prev === 'IDLE' ? 'ZOOM_OUT' : prev));
    } else {
      setPhase('IDLE');
    }
  }, [forcePhase, isTransitioning]);

  // Autonomous Mode Phase Timers (scaled by speed)
  // ZOOM_OUT → 900ms / spd → SLIDING
  useEffect(() => {
    if (forcePhase || isScrubMode) return;
    if (phase === 'ZOOM_OUT') {
      const timer = setTimeout(() => setPhase('SLIDING'), 900 / spd);
      return () => clearTimeout(timer);
    }
  }, [phase, forcePhase, isScrubMode, spd]);

  // SLIDING → 1000ms / spd → ZOOMING_IN
  useEffect(() => {
    if (forcePhase || isScrubMode) return;
    if (phase === 'SLIDING') {
      const timer = setTimeout(() => setPhase('ZOOMING_IN'), 1000 / spd);
      return () => clearTimeout(timer);
    }
  }, [phase, forcePhase, isScrubMode, spd]);

  // ZOOMING_IN → 1100ms / spd → DONE
  useEffect(() => {
    if (forcePhase || isScrubMode) return;
    if (phase === 'ZOOMING_IN') {
      const timer = setTimeout(() => {
        setPhase('DONE');
        onTransitionComplete?.();
      }, 1100 / spd);
      return () => clearTimeout(timer);
    }
  }, [phase, forcePhase, isScrubMode, onTransitionComplete, spd]);

  const isIdle = phase === 'IDLE' && !isScrubMode;
  const isZoomOut = phase === 'ZOOM_OUT' && !isScrubMode;
  const isSliding = phase === 'SLIDING' && !isScrubMode;
  const isZoomingIn = phase === 'ZOOMING_IN' && !isScrubMode;
  const isDone = phase === 'DONE' && !isScrubMode;

  // PROGRESSIVE SCRUB CALCULATIONS (0% -> 100%)
  let pOutgoingScale = 1;
  let pOutgoingY = '0vh';
  let pOutgoingRadius = '0px';
  let pShowOutgoing = true;

  let pIncomingScale = 0.84;
  let pIncomingY = '110vh';
  let pIncomingRadius = '24px';
  let pShowIncoming = false;

  if (isScrubMode) {
    const t = Math.min(100, Math.max(0, Number(progress))) / 100;

    if (t <= 0.25) {
      // Sub-Phase 1: Zoom Out
      const r1 = t / 0.25;
      pOutgoingScale = 1.0 - 0.16 * r1;
      pOutgoingY = '0vh';
      pOutgoingRadius = `${Math.round(24 * r1)}px`;
      pShowOutgoing = true;
      pShowIncoming = false;
    } else if (t <= 0.75) {
      // Sub-Phase 2: Sliding Cards
      const r2 = (t - 0.25) / 0.50;
      pOutgoingScale = 0.84;
      pOutgoingY = `${-110 * r2}vh`;
      pOutgoingRadius = '24px';
      pShowOutgoing = true;

      pIncomingScale = 0.84;
      pIncomingY = `${110 * (1 - r2)}vh`;
      pIncomingRadius = '24px';
      pShowIncoming = true;
    } else {
      // Sub-Phase 3: Zoom In
      const r3 = (t - 0.75) / 0.25;
      pShowOutgoing = false;

      pIncomingScale = 0.84 + 0.16 * r3;
      pIncomingY = '0vh';
      pIncomingRadius = `${Math.round(24 * (1 - r3))}px`;
      pShowIncoming = true;
    }
  } else {
    pShowOutgoing = isIdle || isZoomOut || isSliding;
    pShowIncoming = isSliding || isZoomingIn || isDone;
  }

  const isTransitionActive = isScrubMode ? (Number(progress) > 0 && Number(progress) < 100) : !isDone;

  return (
    <div
      style={{
        position: isDone ? 'static' : 'fixed',
        inset: 0,
        backgroundColor: '#000000',
        display: isDone ? 'block' : 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        overflow: isDone ? 'visible' : 'hidden',
        userSelect: (isIdle || isDone) ? 'auto' : 'none',
        minHeight: isDone ? '100vh' : 'auto',
        zIndex: 9900
      }}
    >
      {/* HYPERSPACE & VFX BACKGROUND */}
      {isTransitionActive && (
        <div
          style={{
            position: 'absolute',
            inset: 0,
            zIndex: 1,
            pointerEvents: 'none'
          }}
        >
          <HyperspaceVFX phase={phase} progress={progress} />
        </div>
      )}

      {/* OUTGOING PAGE CARD */}
      {pShowOutgoing && outgoing && (
        <motion.div
          key="outgoing-card"
          animate={
            isScrubMode
              ? {
                  scale: pOutgoingScale,
                  y: pOutgoingY,
                  opacity: 1,
                  borderRadius: pOutgoingRadius
                }
              : {
                  scale: (isZoomOut || isSliding) ? 0.84 : 1,
                  y: isSliding ? '-110vh' : '0vh',
                  opacity: 1,
                  borderRadius: (isZoomOut || isSliding) ? '24px' : '0px'
                }
          }
          transition={
            isScrubMode
              ? { duration: 0 }
              : {
                  duration: (isSliding ? 1.0 : 0.9) / spd,
                  ease: [0.16, 1, 0.3, 1]
                }
          }
          style={{
            position: 'absolute',
            width: '100vw',
            height: '100vh',
            overflow: 'hidden',
            transformOrigin: 'center center',
            zIndex: isIdle ? 20 : 10,
            willChange: 'transform, opacity',
            boxShadow: (isZoomOut || isSliding || (isScrubMode && Number(progress) < 75))
              ? '0 30px 100px rgba(0, 0, 0, 0.95), 0 0 40px rgba(0, 240, 255, 0.12)' 
              : 'none',
            border: (isZoomOut || isSliding || (isScrubMode && Number(progress) < 75))
              ? '1px solid rgba(255, 255, 255, 0.1)' 
              : 'none'
          }}
        >
          {outgoing}
        </motion.div>
      )}

      {/* INCOMING PAGE CARD */}
      {pShowIncoming && incoming && (
        <motion.div
          key="incoming-card"
          initial={isScrubMode ? false : { y: '110vh', scale: 0.84, borderRadius: '24px' }}
          animate={
            isScrubMode
              ? {
                  y: pIncomingY,
                  scale: pIncomingScale,
                  borderRadius: pIncomingRadius
                }
              : {
                  y: (isSliding || isZoomingIn || isDone) ? '0vh' : '110vh',
                  scale: (isZoomingIn || isDone) ? 1.0 : 0.84,
                  borderRadius: isDone ? '0px' : (isZoomingIn ? '0px' : '24px')
                }
          }
          transition={
            isScrubMode
              ? { duration: 0 }
              : {
                  duration: (isZoomingIn ? 1.1 : 1.0) / spd,
                  ease: [0.16, 1, 0.3, 1]
                }
          }
          style={{
            position: isDone ? 'relative' : 'absolute',
            width: '100vw',
            height: isDone ? 'auto' : '100vh',
            overflow: isDone ? 'visible' : 'hidden',
            transformOrigin: 'center center',
            zIndex: 15,
            willChange: 'transform, opacity',
            boxShadow: isDone ? 'none' : '0 30px 100px rgba(0, 0, 0, 0.95), 0 0 40px rgba(168, 85, 247, 0.12)',
            border: isDone ? 'none' : '1px solid rgba(255, 255, 255, 0.1)',
            transform: isDone ? 'none !important' : undefined
          }}
          transformTemplate={isDone ? () => 'none' : undefined}
        >
          {incoming}
        </motion.div>
      )}
    </div>
  );
}
