import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

export default function SectionSelector() {
  const [isHovered, setIsHovered] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);

  const sections = [
    { name: 'INSPIRATION', id: 0 },
    { name: 'ABOUT', id: 1 },
    { name: 'CASE STUDY', id: 2 },
    { name: 'CONTACT', id: 3 }
  ];

  useEffect(() => {
    const handleScroll = () => {
      const scrollPos = window.scrollY;
      const vh = window.innerHeight;
      const current = Math.min(
        sections.length - 1,
        Math.floor((scrollPos + vh * 0.4) / vh)
      );
      setActiveIndex(current);
    };

    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, [sections.length]);

  const scrollToSection = (idx) => {
    window.scrollTo({
      top: idx * window.innerHeight,
      behavior: 'smooth'
    });
  };

  return (
    <div 
      style={{ 
        position: 'fixed', 
        top: '2.5rem', 
        left: '2.5rem', 
        zIndex: 60,
        display: 'flex',
        flexDirection: 'column',
        gap: '12px',
        cursor: 'none'
      }}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      data-cursor-expand="true"
    >
      {sections.map((sec, idx) => {
        const isActive = activeIndex === idx;

        // Width logic matching Image 1 (collapsed equal width) and Image 2 (expanded hierarchy)
        let dashWidth = 20;
        if (isHovered) {
          if (isActive) dashWidth = 22;
          else if (Math.abs(activeIndex - idx) === 1) dashWidth = 14;
          else dashWidth = 10;
        }

        return (
          <div 
            key={sec.name}
            onClick={() => scrollToSection(idx)}
            style={{ 
              display: 'flex', 
              alignItems: 'center', 
              gap: '10px',
              cursor: 'none'
            }}
          >
            {/* Rounded Pill Dash */}
            <motion.div
              animate={{
                width: `${dashWidth}px`,
                backgroundColor: isActive ? 'var(--text-primary)' : 'rgba(10, 31, 44, 0.4)'
              }}
              transition={{ type: 'spring', stiffness: 350, damping: 25 }}
              style={{
                height: '4px',
                borderRadius: '2px',
                flexShrink: 0
              }}
            />

            {/* Label next to active or hovered section line */}
            <AnimatePresence>
              {(isActive || isHovered) && (
                <motion.span
                  initial={{ opacity: 0, x: -4 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: -4 }}
                  transition={{ duration: 0.15 }}
                  style={{
                    color: isActive ? 'var(--text-primary)' : 'rgba(10, 31, 44, 0.65)',
                    fontWeight: isActive ? 700 : 600,
                    fontSize: '0.85rem',
                    fontFamily: '"Spline Sans Mono", monospace',
                    letterSpacing: '0.12em',
                    whiteSpace: 'nowrap',
                    userSelect: 'none'
                  }}
                >
                  {sec.name}
                </motion.span>
              )}
            </AnimatePresence>
          </div>
        );
      })}
    </div>
  );
}
