import React, { useState, useEffect, useRef, useCallback } from 'react';

const TRACK_HEIGHT = 176; // h-44 = 176px (11rem)
const THUMB_HEIGHT = 48;  // h-12 = 48px (3rem)
const MAX_TRANSLATE = TRACK_HEIGHT - THUMB_HEIGHT; // 128px

export default function CustomScrollbar({ isVisible = true, isMenuOpen = false }) {
  const [thumbY, setThumbY] = useState(0);
  const [isDragging, setIsDragging] = useState(false);
  const [isScrollActive, setIsScrollActive] = useState(false);
  const [isHovered, setIsHovered] = useState(false);

  const trackRef = useRef(null);
  const thumbGrabOffset = useRef(THUMB_HEIGHT / 2);
  const hideTimeoutRef = useRef(null);

  // When menu opens, immediately cancel dragging and hide scrollbar
  useEffect(() => {
    if (isMenuOpen) {
      setIsDragging(false);
      setIsHovered(false);
      setIsScrollActive(false);
      if (hideTimeoutRef.current) clearTimeout(hideTimeoutRef.current);
    }
  }, [isMenuOpen]);

  // Sync scrollbar thumb position with window scroll
  const handleScroll = useCallback(() => {
    if (typeof window === 'undefined' || isMenuOpen) return;

    const maxScroll = document.documentElement.scrollHeight - window.innerHeight;
    if (maxScroll <= 0) {
      setThumbY(0);
      return;
    }

    const currentScroll = window.scrollY;
    const progress = Math.min(Math.max(currentScroll / maxScroll, 0), 1);
    setThumbY(progress * MAX_TRANSLATE);

    // Show on scroll
    setIsScrollActive(true);

    // Auto-disappear if user stops scrolling for 1.2 seconds
    if (hideTimeoutRef.current) clearTimeout(hideTimeoutRef.current);
    hideTimeoutRef.current = setTimeout(() => {
      setIsScrollActive(false);
    }, 1200);
  }, [isMenuOpen]);

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

  // 1:1 Accurate Pointer Capture Dragging
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
    const maxScroll = document.documentElement.scrollHeight - window.innerHeight;

    if (maxScroll > 0) {
      window.scrollTo({
        top: progress * maxScroll,
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

    // Reset auto-hide timer after release
    if (hideTimeoutRef.current) clearTimeout(hideTimeoutRef.current);
    hideTimeoutRef.current = setTimeout(() => {
      setIsScrollActive(false);
    }, 1200);
  };

  // Handle Track Click Jump
  const handleTrackClick = (e) => {
    if (!trackRef.current || isDragging || isMenuOpen) return;
    const rect = trackRef.current.getBoundingClientRect();
    const clickY = e.clientY - rect.top;
    const clampedY = Math.min(Math.max(clickY - THUMB_HEIGHT / 2, 0), MAX_TRANSLATE);
    const progress = clampedY / MAX_TRANSLATE;
    const maxScroll = document.documentElement.scrollHeight - window.innerHeight;

    if (maxScroll > 0) {
      window.scrollTo({
        top: progress * maxScroll,
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
      className={`fixed right-4 top-1/2 -translate-y-1/2 z-40 w-1 h-44 transition-opacity duration-300 mix-blend-exclusion hidden md:block select-none ${
        shouldShow ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'
      }`}
      style={{
        zIndex: 40,
        height: `${TRACK_HEIGHT}px`,
        width: '4px'
      }}
      aria-hidden={!shouldShow}
    >
      {/* Scroll Track */}
      <div 
        className="absolute inset-0 bg-white/20 rounded-full cursor-pointer transition-all duration-300 hover:bg-white/35" 
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



