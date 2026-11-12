import React, { useEffect, useRef } from 'react';

/**
 * HyperspaceVFX - 60 FPS Pure Pitch Black VFX Background.
 * 
 * Features:
 *   1. 100% Pure Optical Progressive Blur (No white tone, completely transparent glass).
 *   2. Accurately Timed Hyperspace Sequence:
 *      - ZOOM_OUT: Spools up from stationary (0 -> 45).
 *      - SLIDING: Full peak hyperdrive streaks (45 -> 80).
 *      - ZOOMING_IN: Visibly decelerates from hyperdrive back into stationary star points (80 -> 0)
 *        across the full 1.1s zoom-in duration.
 *      - DONE: Fully stationary / stopped (0).
 *   3. Pitch Black (#000000) pure background with zero white tint.
 */
export default function HyperspaceVFX({ phase = 'SLIDING', progress = null }) {
  const canvasRef = useRef(null);
  const phaseRef = useRef(phase);
  phaseRef.current = phase;

  const progressRef = useRef(progress);
  progressRef.current = progress;
  const prevProgressRef = useRef(progress);

  const starsRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d', { alpha: false });
    if (!ctx) return;

    let animationFrameId;
    let width = (canvas.width = window.innerWidth);
    let height = (canvas.height = window.innerHeight);

    // High-DPI support (capped at 2 for optimal 60fps performance)
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = Math.floor(width * dpr);
    canvas.height = Math.floor(height * dpr);
    ctx.scale(dpr, dpr);

    const handleResize = () => {
      if (!canvas) return;
      width = window.innerWidth;
      height = window.innerHeight;
      canvas.width = Math.floor(width * dpr);
      canvas.height = Math.floor(height * dpr);
      ctx.scale(dpr, dpr);
    };

    window.addEventListener('resize', handleResize);

    // ==========================================
    // 3D STARFIELD SYSTEM
    // ==========================================
    const STAR_COUNT = 450;
    const FOV = 320;
    const MAX_DEPTH = 1200;

    const colorPalette = [
      'rgba(255, 255, 255,', // Pure Diamond White
      'rgba(248, 250, 252,', // Bright Silver White
      'rgba(241, 245, 249,', // Soft White
      'rgba(226, 232, 240,'  // Light Platinum
    ];

    const createStar = (initialZ = null) => {
      const angle = Math.random() * Math.PI * 2;
      const distance = Math.pow(Math.random(), 0.6) * Math.max(width, height) * 0.95 + 20;
      return {
        x: Math.cos(angle) * distance,
        y: Math.sin(angle) * distance,
        z: initialZ !== null ? initialZ : Math.random() * MAX_DEPTH + 1,
        pz: initialZ !== null ? initialZ : MAX_DEPTH,
        colorPrefix: colorPalette[Math.floor(Math.random() * colorPalette.length)],
        baseSize: Math.random() * 1.8 + 0.8,
        speedMultiplier: Math.random() * 0.4 + 0.8
      };
    };

    if (!starsRef.current) {
      starsRef.current = Array.from({ length: STAR_COUNT }, () => createStar(Math.random() * MAX_DEPTH + 1));
    }
    const stars = starsRef.current;

    let currentSpeed = 0;
    let phaseStartTime = performance.now();
    let prevActivePhase = phaseRef.current;

    // Main render frame
    const render = (now) => {
      const isScrubMode = progressRef.current !== null && progressRef.current !== undefined;
      const currentPhase = phaseRef.current;
      const cx = width / 2;
      const cy = height / 2;

      let speedDelta = 0;

      if (isScrubMode) {
        // --- DEV HUB SCRUB MODE ---
        const currentP = Number(progressRef.current) || 0;
        const prevP = prevProgressRef.current !== null && prevProgressRef.current !== undefined 
          ? Number(prevProgressRef.current) 
          : currentP;
        
        const deltaP = currentP - prevP;
        prevProgressRef.current = currentP;

        if (Math.abs(deltaP) > 0.001) {
          const pRatio = currentP / 100;
          const envelope = Math.sin(pRatio * Math.PI); // 0 at 0%, 1 at 50%, 0 at 100%
          speedDelta = deltaP * (20 + envelope * 30);
        } else {
          speedDelta = 0;
        }
      } else {
        // --- LIVE TRANSITION MODE WITH PRECISE PHASE-LOCKED TIMING ---
        if (currentPhase !== prevActivePhase) {
          phaseStartTime = now;
          prevActivePhase = currentPhase;
        }

        const elapsedInPhase = now - phaseStartTime;

        let targetSpeed = 0;
        let lerpFactor = 0.08;

        if (currentPhase === 'IDLE') {
          targetSpeed = 0;
          lerpFactor = 0.15;
        } else if (currentPhase === 'ZOOM_OUT') {
          // 900ms duration: Spool up visibly from 0 to 50
          const progress01 = Math.min(1, elapsedInPhase / 900);
          targetSpeed = Math.pow(progress01, 1.2) * 55;
          lerpFactor = 0.1;
        } else if (currentPhase === 'SLIDING') {
          // 1000ms duration: Peak warp speed
          targetSpeed = 80;
          lerpFactor = 0.09;
        } else if (currentPhase === 'ZOOMING_IN') {
          // 1100ms duration: Decelerate visibly from 80 down to 0
          const progress01 = Math.min(1, elapsedInPhase / 1100);
          targetSpeed = 80 * Math.pow(1 - progress01, 1.6); // Exponential deceleration curve
          lerpFactor = 0.12;
        } else if (currentPhase === 'DONE') {
          targetSpeed = 0;
          lerpFactor = 0.2;
        }

        currentSpeed += (targetSpeed - currentSpeed) * lerpFactor;
        speedDelta = currentSpeed;
      }

      // Pitch black clear
      ctx.fillStyle = '#000000';
      ctx.fillRect(0, 0, width, height);

      // Render 3D stars
      const isStationary = Math.abs(speedDelta) < 0.2;

      for (let i = 0; i < STAR_COUNT; i++) {
        const star = stars[i];

        star.pz = star.z;
        star.z -= speedDelta * star.speedMultiplier;

        // Wrap around depth
        if (star.z <= 1) {
          star.z += MAX_DEPTH;
          star.pz = star.z;
        } else if (star.z > MAX_DEPTH) {
          star.z -= MAX_DEPTH;
          star.pz = star.z;
        }

        const k = FOV / star.z;
        const px = star.x * k + cx;
        const py = star.y * k + cy;

        if (px < -80 || px > width + 80 || py < -80 || py > height + 80) {
          continue;
        }

        const depthRatio = Math.max(0, 1 - star.z / MAX_DEPTH);
        const alpha = Math.min(1, Math.max(0.15, Math.pow(depthRatio, 1.1) * 1.3));
        const size = Math.max(0.9, depthRatio * star.baseSize * 2.4);

        if (isStationary) {
          ctx.beginPath();
          ctx.arc(px, py, size * 0.7, 0, Math.PI * 2);
          ctx.fillStyle = `${star.colorPrefix} ${alpha})`;
          ctx.fill();
        } else {
          const prevK = FOV / star.pz;
          const prevPx = star.x * prevK + cx;
          const prevPy = star.y * prevK + cy;

          ctx.beginPath();
          ctx.moveTo(prevPx, prevPy);
          ctx.lineTo(px, py);
          ctx.strokeStyle = `${star.colorPrefix} ${alpha})`;
          ctx.lineWidth = size;
          ctx.lineCap = 'round';
          ctx.stroke();
        }
      }

      animationFrameId = requestAnimationFrame(render);
    };

    animationFrameId = requestAnimationFrame(render);

    return () => {
      cancelAnimationFrame(animationFrameId);
      window.removeEventListener('resize', handleResize);
    };
  }, []);

  // Smooth progressive mask curves (quintic falloff for seamless blending)
  const maskTop = 'linear-gradient(to bottom, rgba(0,0,0,1) 0%, rgba(0,0,0,0.8) 25%, rgba(0,0,0,0.5) 50%, rgba(0,0,0,0.2) 75%, rgba(0,0,0,0.05) 90%, rgba(0,0,0,0) 100%)';
  const maskBottom = 'linear-gradient(to top, rgba(0,0,0,1) 0%, rgba(0,0,0,0.8) 25%, rgba(0,0,0,0.5) 50%, rgba(0,0,0,0.2) 75%, rgba(0,0,0,0.05) 90%, rgba(0,0,0,0) 100%)';
  const maskLeft = 'linear-gradient(to right, rgba(0,0,0,1) 0%, rgba(0,0,0,0.8) 25%, rgba(0,0,0,0.5) 50%, rgba(0,0,0,0.2) 75%, rgba(0,0,0,0.05) 90%, rgba(0,0,0,0) 100%)';
  const maskRight = 'linear-gradient(to left, rgba(0,0,0,1) 0%, rgba(0,0,0,0.8) 25%, rgba(0,0,0,0.5) 50%, rgba(0,0,0,0.2) 75%, rgba(0,0,0,0.05) 90%, rgba(0,0,0,0) 100%)';

  return (
    <div
      style={{
        position: 'absolute',
        inset: 0,
        overflow: 'hidden',
        pointerEvents: 'none',
        zIndex: 1,
        backgroundColor: '#000000'
      }}
    >
      {/* =========================================================================
          1. HTML5 CANVAS: 3D HYPERSPACE WARP SPEED
      ========================================================================= */}
      <canvas
        ref={canvasRef}
        style={{
          position: 'absolute',
          inset: 0,
          width: '100%',
          height: '100%',
          display: 'block',
          zIndex: 2,
          mixBlendMode: 'screen'
        }}
      />

      {/* =========================================================================
          2. ALL 4 BORDERS: 100% PURE OPTICAL PROGRESSIVE BLUR (Zero White Tone)
      ========================================================================= */}

      {/* --- TOP BORDER --- */}
      <div
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          height: '64px',
          zIndex: 4,
          background: 'transparent',
          WebkitMaskImage: maskTop,
          maskImage: maskTop,
          backdropFilter: 'blur(14px)',
          WebkitBackdropFilter: 'blur(14px)',
          transform: 'translateZ(0)',
          pointerEvents: 'none'
        }}
      />

      {/* --- BOTTOM BORDER --- */}
      <div
        style={{
          position: 'absolute',
          bottom: 0,
          left: 0,
          right: 0,
          height: '64px',
          zIndex: 4,
          background: 'transparent',
          WebkitMaskImage: maskBottom,
          maskImage: maskBottom,
          backdropFilter: 'blur(14px)',
          WebkitBackdropFilter: 'blur(14px)',
          transform: 'translateZ(0)',
          pointerEvents: 'none'
        }}
      />

      {/* --- LEFT BORDER --- */}
      <div
        style={{
          position: 'absolute',
          top: 0,
          bottom: 0,
          left: 0,
          width: '64px',
          zIndex: 4,
          background: 'transparent',
          WebkitMaskImage: maskLeft,
          maskImage: maskLeft,
          backdropFilter: 'blur(14px)',
          WebkitBackdropFilter: 'blur(14px)',
          transform: 'translateZ(0)',
          pointerEvents: 'none'
        }}
      />

      {/* --- RIGHT BORDER --- */}
      <div
        style={{
          position: 'absolute',
          top: 0,
          bottom: 0,
          right: 0,
          width: '64px',
          zIndex: 4,
          background: 'transparent',
          WebkitMaskImage: maskRight,
          maskImage: maskRight,
          backdropFilter: 'blur(14px)',
          WebkitBackdropFilter: 'blur(14px)',
          transform: 'translateZ(0)',
          pointerEvents: 'none'
        }}
      />
    </div>
  );
}
