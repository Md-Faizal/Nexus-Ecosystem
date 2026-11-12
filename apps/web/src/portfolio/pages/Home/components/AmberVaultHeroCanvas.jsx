import React, { useRef, useEffect, useState, useCallback } from 'react';

const TOTAL_SCROLL_FRAMES = 40;
const TOTAL_INTRO_FRAMES = 16;
const ASSET_CACHE_BUST = 'v4k_clean_4';

export default function AmberVaultHeroCanvas({
  isRevealed = true,
  heroStage = 'logo', // 'logo' | 'video-portal' | 'video-fullscreen'
  scrollProgress = 0, // 0 to 1
  onTransitionComplete
}) {
  const canvasRef = useRef(null);
  const containerRef = useRef(null);
  const [isReady, setIsReady] = useState(false);
  const animFrameRef = useRef(null);

  // Frame image buffers
  const scrollFramesRef = useRef([]);
  const introFramesRef = useRef([]);

  // Timing state for intro dolly-out
  const introState = useRef({
    startTime: null,
    isComplete: false,
    progress: 0
  });

  // Smooth mouse parallax coordinates
  const mouseState = useRef({
    x: 0,
    y: 0,
    targetX: 0,
    targetY: 0
  });

  // Current active frame identifiers to avoid redundant draws
  const lastDrawnRef = useRef({
    type: null, // 'intro' | 'parallax' | 'scroll'
    index: -1,
    alpha: 0
  });

  // 1. Helper to draw any loaded image onto the canvas with cover sizing
  const drawCoverImage = useCallback((img, opacity = 1.0) => {
    const canvas = canvasRef.current;
    if (!canvas || !img || !img.complete || img.naturalWidth === 0) return false;
    const ctx = canvas.getContext('2d');
    if (!ctx) return false;

    const cw = canvas.width;
    const ch = canvas.height;
    if (cw === 0 || ch === 0) return false;

    const iw = img.naturalWidth;
    const ih = img.naturalHeight;

    const hRatio = cw / iw;
    const vRatio = ch / ih;
    const ratio = Math.max(hRatio, vRatio);

    const nw = iw * ratio;
    const nh = ih * ratio;
    const nx = (cw - nw) / 2;
    const ny = (ch - nh) / 2;

    if (opacity < 1.0) {
      ctx.globalAlpha = Math.max(0, Math.min(1, opacity));
    } else {
      ctx.globalAlpha = 1.0;
    }

    ctx.drawImage(img, nx, ny, nw, nh);
    ctx.globalAlpha = 1.0;
    return true;
  }, []);

  // 2. Preload Scroll Sequence (40 Frames)
  useEffect(() => {
    let isCancelled = false;
    const images = [];

    for (let i = 0; i < TOTAL_SCROLL_FRAMES; i++) {
      const img = new Image();
      const numStr = String(i).padStart(2, '0');
      img.src = `/sequence/frame_${numStr}.webp?v=${ASSET_CACHE_BUST}`;

      img.onload = () => {
        if (isCancelled) return;
        if (i === 0 && !isReady) {
          setIsReady(true);
          drawCoverImage(img);
        }
      };

      img.onerror = () => {
        if (isCancelled) return;
        if (!img.src.includes('.jpg')) {
          img.src = `/sequence/frame_${numStr}.jpg?v=${ASSET_CACHE_BUST}`;
        }
      };

      images.push(img);
    }
    scrollFramesRef.current = images;

    if (images[0]?.complete && images[0]?.naturalWidth > 0) {
      setIsReady(true);
      drawCoverImage(images[0]);
    }

    return () => {
      isCancelled = true;
    };
  }, [drawCoverImage, isReady]);

  // 3. Preload Intro Dolly-Out Sequence (16 Frames)
  useEffect(() => {
    let isCancelled = false;
    const images = [];

    for (let i = 0; i < TOTAL_INTRO_FRAMES; i++) {
      const img = new Image();
      const numStr = String(i).padStart(2, '0');
      img.src = `/sequence/intro/intro_${numStr}.webp`;

      img.onerror = () => {
        if (isCancelled) return;
        if (!img.src.endsWith('.jpg')) {
          img.src = `/sequence/intro/intro_${numStr}.jpg`;
        }
      };

      images.push(img);
    }
    introFramesRef.current = images;

    return () => {
      isCancelled = true;
    };
  }, []);

  // 4. Synchronized Timing Controller
  // Stage 1 (video-portal): Hold camera firmly on close monitor view (intro_00)
  // Stage 2 (video-fullscreen): Trigger dolly-out IN SYNCHRONIZATION with expanding concentric rings
  useEffect(() => {
    if (heroStage === 'logo') {
      introState.current.startTime = null;
      introState.current.isComplete = false;
      introState.current.progress = 0;
    } else if (heroStage === 'video-portal') {
      // Hold firmly on close view inside circular aperture
      introState.current.startTime = null;
      introState.current.isComplete = false;
      introState.current.progress = 0;
    } else if (heroStage === 'video-fullscreen') {
      // Concentric rings unmask outward -> start dolly-out pullback simultaneously
      if (!introState.current.startTime) {
        introState.current.startTime = performance.now();
        introState.current.isComplete = false;
        introState.current.progress = 0;
      }
    }
  }, [heroStage]);

  // 6. Resize Handler with High-DPI scaling
  useEffect(() => {
    const handleResize = () => {
      const canvas = canvasRef.current;
      const container = containerRef.current;
      if (!canvas || !container) return;

      const w = container.clientWidth || window.innerWidth;
      const h = container.clientHeight || window.innerHeight;
      const dpr = Math.min(window.devicePixelRatio || 1, 2);

      canvas.width = Math.floor(w * dpr);
      canvas.height = Math.floor(h * dpr);
      canvas.style.width = `${w}px`;
      canvas.style.height = `${h}px`;

      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = 'high';
      }

      // Draw initial frame
      const initialImg = scrollFramesRef.current[0];
      if (initialImg?.complete && initialImg.naturalWidth > 0) {
        drawCoverImage(initialImg);
      }
    };

    window.addEventListener('resize', handleResize);
    handleResize();

    return () => window.removeEventListener('resize', handleResize);
  }, [drawCoverImage]);

  // 7. Mouse Movement Listener for Parallax
  useEffect(() => {
    const handleMouseMove = (e) => {
      const normX = (e.clientX / window.innerWidth) * 2 - 1;
      const normY = (e.clientY / window.innerHeight) * 2 - 1;
      mouseState.current.targetX = normX;
      mouseState.current.targetY = normY;
    };

    window.addEventListener('mousemove', handleMouseMove, { passive: true });
    return () => window.removeEventListener('mousemove', handleMouseMove);
  }, []);

  // 8. Main 60 FPS Render & Animation Loop
  useEffect(() => {
    const animate = (timestamp) => {
      animFrameRef.current = requestAnimationFrame(animate);

      const ms = mouseState.current;
      const container = containerRef.current;
      const scrollFrames = scrollFramesRef.current;
      const introFrames = introFramesRef.current;

      // Smooth mouse lerp with responsive damping
      ms.x += (ms.targetX - ms.x) * 0.08;
      ms.y += (ms.targetY - ms.y) * 0.08;

      // Compute intro dolly-out progress (Synchronized with concentric aperture unmasking)
      if (introState.current.startTime && !introState.current.isComplete) {
        const elapsed = timestamp - introState.current.startTime;
        const dur = 1200; // Synchronized with radial aperture expansion
        const t = Math.min(1, elapsed / dur);
        // Cinematic cubic ease-out
        introState.current.progress = 1.0 - Math.pow(1.0 - t, 3.0);
        if (t >= 1) {
          introState.current.isComplete = true;
        }
      }

      const isScrolled = scrollProgress > 0.01;
      const isPortalHold = (heroStage === 'video-portal');
      const isIntroActive = (heroStage === 'video-fullscreen') && !introState.current.isComplete;

      // Apply container 3D perspective tilt (Fluid 120Hz spatial orbit)
      if (container) {
        const zoomDamping = Math.max(0, 1 - scrollProgress * 1.5);
        const rotY = ms.x * 2.2 * zoomDamping;
        const rotX = -ms.y * 1.4 * zoomDamping;
        const transX = ms.x * -12 * zoomDamping;
        const transY = ms.y * -8 * zoomDamping;
        container.style.transform = `perspective(1400px) rotateY(${rotY}deg) rotateX(${rotX}deg) translate3d(${transX}px, ${transY}px, 0px) scale(${1.02 + scrollProgress * 0.02})`;
        const targetScale = scrollProgress >= 0.85 
          ? 1.02 - ((scrollProgress - 0.85) / 0.15) * 0.02 
          : 1.02;
        container.style.transform = `perspective(1400px) rotateY(${rotY}deg) rotateX(${rotX}deg) translate3d(${transX}px, ${transY}px, 0px) scale(${targetScale})`;
      }

      // ========================================================
      // SCENARIO 1: SCROLL DOLLY-IN (User scrolling on page)
      // ========================================================
      if (isScrolled) {
        const p = Math.min(1, Math.max(0, scrollProgress));
        const frameIdx = Math.min(TOTAL_SCROLL_FRAMES - 1, Math.max(0, Math.round(p * (TOTAL_SCROLL_FRAMES - 1))));

        let img = scrollFrames[frameIdx];
        if (!img || !img.complete || img.naturalWidth === 0) {
          // Look backward for nearest ready frame
          for (let off = 1; off <= frameIdx; off++) {
            if (scrollFrames[frameIdx - off]?.complete && scrollFrames[frameIdx - off]?.naturalWidth > 0) {
              img = scrollFrames[frameIdx - off];
              break;
            }
          }
        }
        if (!img || !img.complete || img.naturalWidth === 0) {
          img = scrollFrames[0];
        }

        if (img) {
          drawCoverImage(img);
        }

        if (p >= 0.985 && onTransitionComplete) {
          onTransitionComplete();
        }
        return;
      }

      // ========================================================
      // SCENARIO 2: PORTAL HOLD (Camera held near monitor)
      // Clearly reveals the monitor UI & glowing desk inside circle
      // ========================================================
      if (isPortalHold) {
        let img = introFrames[0];
        if (!img || !img.complete || img.naturalWidth === 0) {
          img = scrollFrames[28] || scrollFrames[0];
        }
        if (img) {
          drawCoverImage(img);
        }
        return;
      }

      // ========================================================
      // SCENARIO 3: SYNCHRONIZED APERTURE DOLLY-OUT
      // Camera pulls back in exact lockstep as circles expand
      // ========================================================
      if (isIntroActive) {
        const p = introState.current.progress;
        const introIdx = Math.min(TOTAL_INTRO_FRAMES - 1, Math.floor(p * TOTAL_INTRO_FRAMES));
        let img = introFrames[introIdx];

        if (!img || !img.complete || img.naturalWidth === 0) {
          const fallbackIdx = Math.max(0, Math.round((1.0 - p) * 28));
          img = scrollFrames[fallbackIdx] || scrollFrames[0];
        }

        if (img) {
          drawCoverImage(img);
        }
        return;
      }

      // ========================================================
      // SCENARIO 4: IDLE RESTING STATE (Pristine 4K Base Frame)
      // Silky-smooth 120Hz hardware 2.5D perspective tracking
      // ========================================================
      const baseFrame = scrollFrames[0] || introFrames[TOTAL_INTRO_FRAMES - 1];
      if (baseFrame && baseFrame.complete && baseFrame.naturalWidth > 0) {
        drawCoverImage(baseFrame, 1.0);
      }
    };

    const id = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(id);
  }, [scrollProgress, heroStage, drawCoverImage, onTransitionComplete]);

  return (
    <div
      ref={containerRef}
      className="absolute inset-0 w-full h-full pointer-events-none select-none overflow-hidden will-change-transform"
      style={{
        opacity: isReady && isRevealed ? 1 : 0,
        transition: 'opacity 0.4s cubic-bezier(0.16, 1, 0.3, 1)',
        transformOrigin: 'center center'
      }}
    >
      <canvas
        ref={canvasRef}
        className="block w-full h-full object-cover"
      />

      {/* Dynamic Specular Lens Sheen responding to cursor */}
      {/* Dynamic Specular Lens Sheen responding to cursor (fades out as camera approaches screen) */}
      <div
        className="absolute inset-0 pointer-events-none mix-blend-screen opacity-25 transition-opacity duration-300"
        className="absolute inset-0 pointer-events-none mix-blend-screen transition-opacity duration-300"
        style={{
          opacity: Math.max(0, 0.25 * (1 - scrollProgress * 1.4)),
          background: `radial-gradient(circle at ${50 + mouseState.current.x * 20}% ${50 + mouseState.current.y * 20}%, rgba(255, 220, 160, 0.22) 0%, rgba(220, 140, 40, 0.04) 45%, transparent 70%)`
        }}
      />
    </div>
  );
}
