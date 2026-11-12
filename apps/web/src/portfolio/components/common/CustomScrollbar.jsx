import React, { useState, useEffect, useRef, useCallback } from 'react';

const TRACK_HEIGHT = 176; // h-44 = 176px (11rem)
const THUMB_HEIGHT = 48;  // h-12 = 48px (3rem)
const MAX_TRANSLATE = TRACK_HEIGHT - THUMB_HEIGHT; // 128px
const HERO_RANGE = 0.18; // Top 18% of scrollbar track controls 3D Hero zoom into monitor

export default function CustomScrollbar({ isVisible = true, isMenuOpen = false }) {
  const [thumbY, setThumbY] = useState(0);
  const [isDragging, setIsDragging] = useState(false);
  const [isScrollActive, setIsScrollActive] = useState(false);
  const [isHovered, setIsHovered] = useState(false);

  const trackRef = useRef(null);
  const thumbGrabOffset = useRef(THUMB_HEIGHT / 2);
  const hideTimeoutRef = useRef(null);
  const heroZoomRef = useRef(0); // 0.0 to 1.0

  // When menu opens, immediately cancel dragging and hide scrollbar
  useEffect(() => {
    if (isMenuOpen) {
      setIsDragging(false);
      setIsHovered(false);
      setIsScrollActive(false);
      if (hideTimeoutRef.current) clearTimeout(hideTimeoutRef.current);
    }
  }, [isMenuOpen]);

  // Unified position computer combining Hero Zoom (0-18%) and Page Content (18-100%)
  const updateThumbPosition = useCallback(() => {
    if (typeof window === 'undefined' || isMenuOpen) return;

    const currentScroll = window.scrollY || 0;
    const maxScroll = Math.max(1, document.documentElement.scrollHeight - window.innerHeight);
    const zoom = heroZoomRef.current;

    let overallProgress = 0;
    if (currentScroll <= 5 && zoom < 0.999) {
      // Phase 1: 3D Hero Monitor Zoom Phase (0% -> 18% of track)
      overallProgress = Math.min(HERO_RANGE, Math.max(0, zoom * HERO_RANGE));
    } else {
      // Phase 2: Page Content Scroll Phase (18% -> 100% of track)
      const contentRatio = Math.min(1, Math.max(0, currentScroll / maxScroll));
      overallProgress = HERO_RANGE + contentRatio * (1 - HERO_RANGE);
    }

    setThumbY(overallProgress * MAX_TRANSLATE);
  }, [isMenuOpen]);

  // Sync scrollbar thumb position with window scroll
  const handleScroll = useCallback(() => {
    if (typeof window === 'undefined' || isMenuOpen) return;

    updateThumbPosition();

    // Show on scroll
    setIsScrollActive(true);

    // Auto-disappear if user stops scrolling for 1.2 seconds
    if (hideTimeoutRef.current) clearTimeout(hideTimeoutRef.current);
    hideTimeoutRef.current = setTimeout(() => {
      setIsScrollActive(false);
    }, 1200);
  }, [isMenuOpen, updateThumbPosition]);

  // Listen to 3D Hero zoom events in real time
  useEffect(() => {
    const handleHeroZoom = (e) => {
      heroZoomRef.current = Number(e.detail?.progress) || 0;
      updateThumbPosition();
      setIsScrollActive(true);
      if (hideTimeoutRef.current) clearTimeout(hideTimeoutRef.current);
      hideTimeoutRef.current = setTimeout(() => {
        setIsScrollActive(false);
      }, 1200);
    };

    window.addEventListener('hero-zoom', handleHeroZoom);
    return () => window.removeEventListener('hero-zoom', handleHeroZoom);
  }, [updateThumbPosition]);

  useEffect(() => {
    if (isMenuOpen) return;

    window.addEventListener('scroll', handleScroll, { passive: true });
    window.addEventListener('resize', handleScroll, { passive: true });
    handleScroll();

    // Global click listener: disappear immediately if user clicks outside the scrollbar
    const handleGlobalPointerDown = (e) => {
      if (trackRef.current && !trackRef.current.contains(e.target)) {
        setIsScrollActive(false);
      }
    };

    // Right-edge proximity detection: wake up scrollbar when cursor approaches right edge
    const handleGlobalMouseMove = (e) => {
      if (isMenuOpen) return;
      const isNearRightEdge = e.clientX >= window.innerWidth - 36;
      const isNearCenterY = Math.abs(e.clientY - window.innerHeight / 2) <= (TRACK_HEIGHT / 2 + 40);
      
      if (isNearRightEdge && isNearCenterY) {
        setIsHovered(true);
      }
    };

    document.addEventListener('pointerdown', handleGlobalPointerDown, { passive: true });
    window.addEventListener('mousemove', handleGlobalMouseMove, { passive: true });

    return () => {
      window.removeEventListener('scroll', handleScroll);
      window.removeEventListener('resize', handleScroll);
      document.removeEventListener('pointerdown', handleGlobalPointerDown);
      window.removeEventListener('mousemove', handleGlobalMouseMove);
      if (hideTimeoutRef.current) clearTimeout(hideTimeoutRef.current);
    };
  }, [handleScroll, isMenuOpen]);

  // 1:1 Accurate Pointer Capture Dragging with Hero Zoom & Threshold Integration
  const handleThumbPointerDown = (e) => {
    if (isMenuOpen) return;
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
    setIsScrollActive(true);

    if (trackRef.current) {
      const rect = trackRef.current.getBoundingClientRect();
      const pointerOffset = (e.clientY - rect.top) - thumbY;
      thumbGrabOffset.current = Math.min(Math.max(pointerOffset, 0), THUMB_HEIGHT);
    }

    try {
      e.currentTarget.setPointerCapture(e.pointerId);
    } catch {}
  };

  const handleThumbPointerMove = (e) => {
    if (!isDragging || !trackRef.current || isMenuOpen) return;
    e.preventDefault();

    const rect = trackRef.current.getBoundingClientRect();
    const rawY = (e.clientY - rect.top) - thumbGrabOffset.current;
    const clampedY = Math.min(Math.max(rawY, 0), MAX_TRANSLATE);
    const progress = clampedY / MAX_TRANSLATE;

    if (progress <= HERO_RANGE) {
      // Zone 1: Scrubbing inside 3D Hero Zoom
      const targetZoom = Math.min(1, Math.max(0, progress / HERO_RANGE));
      heroZoomRef.current = targetZoom;
      setThumbY(clampedY);

      if (window.scrollY > 0) {
        window.scrollTo({ top: 0, behavior: 'auto' });
      }

      window.dispatchEvent(new CustomEvent('hero-zoom-set', { 
        detail: { zoom: targetZoom, snap: false } 
      }));
    } else {
      // Zone 2: Scrubbing page content
      heroZoomRef.current = 1.0;
      window.dispatchEvent(new CustomEvent('hero-zoom-set', { 
        detail: { zoom: 1.0, snap: false } 
      }));

      const contentRatio = (progress - HERO_RANGE) / (1 - HERO_RANGE);
      const maxScroll = Math.max(1, document.documentElement.scrollHeight - window.innerHeight);

      setThumbY(clampedY);
      window.scrollTo({
        top: contentRatio * maxScroll,
        behavior: 'auto'
      });
    }
  };

  const handleThumbPointerUp = (e) => {
    if (!isDragging) return;
    setIsDragging(false);
    try {
      e.currentTarget.releasePointerCapture(e.pointerId);
    } catch {}

    // Apply Threshold Snap if pointer released within Hero Zoom range
    if (trackRef.current) {
      const rect = trackRef.current.getBoundingClientRect();
      const rawY = (e.clientY - rect.top) - thumbGrabOffset.current;
      const clampedY = Math.min(Math.max(rawY, 0), MAX_TRANSLATE);
      const progress = clampedY / MAX_TRANSLATE;

      if (progress <= HERO_RANGE) {
        const targetZoom = progress / HERO_RANGE;
        const SCROLL_THRESHOLD = 0.35;
        const snapTarget = targetZoom >= SCROLL_THRESHOLD ? 1.0 : 0.0;
        heroZoomRef.current = snapTarget;

        window.dispatchEvent(new CustomEvent('hero-zoom-snap', { 
          detail: { target: snapTarget } 
        }));

        setThumbY(snapTarget * HERO_RANGE * MAX_TRANSLATE);
      }
    }

    // Reset auto-hide timer after release
    if (hideTimeoutRef.current) clearTimeout(hideTimeoutRef.current);
    hideTimeoutRef.current = setTimeout(() => {
      setIsScrollActive(false);
    }, 1200);
  };

  // Handle Track Click Jump with Threshold Snapping
  const handleTrackClick = (e) => {
    if (!trackRef.current || isDragging || isMenuOpen) return;
    const rect = trackRef.current.getBoundingClientRect();
    const clickY = e.clientY - rect.top;
    const clampedY = Math.min(Math.max(clickY - THUMB_HEIGHT / 2, 0), MAX_TRANSLATE);
    const progress = clampedY / MAX_TRANSLATE;

    if (progress <= HERO_RANGE) {
      // Click in Hero range: evaluate threshold jump
      const targetZoom = progress / HERO_RANGE;
      const snapTarget = targetZoom >= 0.35 ? 1.0 : 0.0;
      heroZoomRef.current = snapTarget;

      if (window.scrollY > 0) {
        window.scrollTo({ top: 0, behavior: 'smooth' });
      }

      window.dispatchEvent(new CustomEvent('hero-zoom-snap', { 
        detail: { target: snapTarget } 
      }));

      setThumbY(snapTarget * HERO_RANGE * MAX_TRANSLATE);
    } else {
      // Click in Page Content range: snap hero to full and smooth-scroll content
      heroZoomRef.current = 1.0;
      window.dispatchEvent(new CustomEvent('hero-zoom-snap', { 
        detail: { target: 1.0 } 
      }));

      const contentRatio = (progress - HERO_RANGE) / (1 - HERO_RANGE);
      const maxScroll = Math.max(1, document.documentElement.scrollHeight - window.innerHeight);

      setThumbY(clampedY);
      window.scrollTo({
        top: contentRatio * maxScroll,
        behavior: 'smooth'
      });
    }
  };

  const shouldShow = isVisible && !isMenuOpen && (isScrollActive || isHovered || isDragging);

  return (
    <div 
      ref={trackRef}
      onMouseEnter={() => {
        if (!isMenuOpen) setIsHovered(true);
      }}
      onMouseLeave={() => {
        setIsHovered(false);
        if (hideTimeoutRef.current) clearTimeout(hideTimeoutRef.current);
        hideTimeoutRef.current = setTimeout(() => {
          setIsScrollActive(false);
        }, 800);
      }}
      onClick={handleTrackClick}
      className={`fixed right-4 top-1/2 -translate-y-1/2 z-998 w-1 h-44 transition-opacity duration-300 mix-blend-exclusion hidden md:block select-none ${
        shouldShow ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'
      }`}
      style={{
        zIndex: 998,
        height: `${TRACK_HEIGHT}px`,
        width: '4px'
      }}
      aria-hidden={!shouldShow}
    >
      {/* Scroll Track */}
      <div 
        className="absolute inset-0 bg-white/20 rounded-full cursor-pointer transition-all duration-300 hover:bg-white/35" 
      />

      {/* Threshold Snap Visual Indicator on Track (at 40% of Hero Range) */}
      <div 
        className="absolute left-1/2 -translate-x-1/2 w-2 h-[1px] bg-[#f59e0b]/50 pointer-events-none"
        style={{
          top: `${0.40 * HERO_RANGE * MAX_TRANSLATE + THUMB_HEIGHT / 2}px`
        }}
        title="Hero Threshold Snap Mark (40%)"
      />

      {/* Hero to Page Content Boundary Mark (at 18% of Track) */}
      <div 
        className="absolute left-1/2 -translate-x-1/2 w-2.5 h-[1px] bg-white/30 pointer-events-none"
        style={{
          top: `${HERO_RANGE * MAX_TRANSLATE + THUMB_HEIGHT / 2}px`
        }}
        title="Hero Zoom Boundary (18%)"
      />

      {/* Draggable Scroll Thumb (100% Inset Aligned) */}
      <div 
        onPointerDown={handleThumbPointerDown}
        onPointerMove={handleThumbPointerMove}
        onPointerUp={handleThumbPointerUp}
        onPointerCancel={handleThumbPointerUp}
        className={`absolute inset-x-0 top-0 w-full h-12 bg-white rounded-full will-change-transform cursor-grab hover:scale-x-180 transition-[scale] origin-center duration-300 ease-out active:cursor-grabbing ${
          isDragging ? 'cursor-grabbing scale-x-200 bg-white' : ''
        }`}
        style={{
          height: `${THUMB_HEIGHT}px`,
          transform: `translateY(${thumbY}px)`,
          touchAction: 'none'
        }}
      />
    </div>
  );
}
