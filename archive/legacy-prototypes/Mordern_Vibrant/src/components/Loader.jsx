import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

export default function Loader() {
  const [progress, setProgress] = useState(0);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isMounted = true;
    const interval = setInterval(() => {
      setProgress((prev) => {
        if (prev >= 100) {
          clearInterval(interval);
          setTimeout(() => {
            if (isMounted) setIsLoading(false);
          }, 300);
          return 100;
        }
        const next = prev + Math.floor(Math.random() * 8) + 3;
        return next > 100 ? 100 : next;
      });
    }, 35);

    const handleLoad = () => {
      setProgress(100);
    };

    if (document.readyState === 'complete') {
      setProgress(100);
    } else {
      window.addEventListener('load', handleLoad);
    }

    return () => {
      isMounted = false;
      clearInterval(interval);
      window.removeEventListener('load', handleLoad);
    };
  }, []);

  return (
    <AnimatePresence>
      {isLoading && (
        <motion.div
          key="fullscreen-loader"
          initial={{ y: 0 }}
          exit={{ y: '-100%' }}
          transition={{ duration: 0.9, ease: [0.76, 0, 0.24, 1] }}
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 99999,
            backgroundColor: '#8ce0ff',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'center',
            alignItems: 'center',
            overflow: 'hidden'
          }}
        >
          {/* Subtle Background Grid Overlay */}
          <div 
            style={{
              position: 'absolute',
              inset: 0,
              pointerEvents: 'none',
              backgroundImage: `
                radial-gradient(circle at 0px 0px, #0284c7 2px, transparent 2px),
                linear-gradient(rgba(2, 132, 199, 0.2) 1px, transparent 1px), 
                linear-gradient(90deg, rgba(2, 132, 199, 0.2) 1px, transparent 1px)
              `,
              backgroundSize: '45px 45px, 45px 45px, 45px 45px',
              opacity: 0.4
            }} 
          />

          {/* Large Playfair Display % Display */}
          <motion.div
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.4 }}
            style={{
              fontFamily: '"Playfair Display", serif',
              fontStyle: 'italic',
              fontWeight: 600,
              fontSize: 'clamp(6rem, 16vw, 16rem)',
              color: '#0a1f2c',
              lineHeight: 1,
              userSelect: 'none',
              letterSpacing: '-0.03em',
              zIndex: 2
            }}
          >
            {progress}%
          </motion.div>

          {/* Bottom Loading Subtext */}
          <div 
            style={{
              position: 'absolute',
              bottom: '3rem',
              fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
              fontSize: '0.8rem',
              letterSpacing: '0.2em',
              color: '#0a1f2c',
              opacity: 0.75,
              textTransform: 'uppercase'
            }}
          >
            Loading Experience...
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
