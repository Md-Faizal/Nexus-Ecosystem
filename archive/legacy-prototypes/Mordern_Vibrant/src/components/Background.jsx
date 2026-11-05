import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence, useScroll, useTransform, useSpring } from 'framer-motion';
import { useTheme } from '../context/ThemeContext';

const GrainOverlay = () => (
  <svg
    style={{
      position: 'absolute',
      inset: 0,
      width: '100%',
      height: '100%',
      pointerEvents: 'none',
      zIndex: 10,
      opacity: 0.35,
      mixBlendMode: 'overlay'
    }}
  >
    <filter id="noiseFilter">
      <feTurbulence type="fractalNoise" baseFrequency="0.8" numOctaves="3" stitchTiles="stitch" />
    </filter>
    <rect width="100%" height="100%" filter="url(#noiseFilter)" />
  </svg>
);

const StripedOrb = ({ isDark, mouseX, mouseY, scrollY }) => {
  const springConfig = { damping: 30, stiffness: 100 };
  const yScroll = useTransform(scrollY, [0, 800], [0, -100]);
  const xMouse = useSpring(mouseX * -30, springConfig);
  const yMouse = useSpring(mouseY * -30, springConfig);

  const colors = isDark 
    ? { primary: '#f8fafc', secondary: '#cbd5e1', glow: 'rgba(255,255,255,0.2)' } // Moon
    : { primary: '#fde047', secondary: '#facc15', glow: 'rgba(253, 224, 71, 0.45)' }; // Sun

  return (
    <motion.div
      style={{
        position: 'absolute',
        top: '6%',
        right: '4%',
        width: 'clamp(220px, 25vw, 320px)',
        height: 'clamp(220px, 25vw, 320px)',
        zIndex: 2,
        x: xMouse,
        y: yScroll,
      }}
    >
      <motion.div style={{ width: '100%', height: '100%', y: yMouse, position: 'relative' }}>
        {/* Glow/Rays behind the orb */}
        <motion.div 
          animate={{ scale: [1, 1.05, 1], opacity: [0.8, 1, 0.8] }}
          transition={{ duration: 4, repeat: Infinity, ease: "easeInOut" }}
          style={{
            position: 'absolute',
            inset: '-30%',
            background: `radial-gradient(circle, ${colors.glow} 0%, transparent 70%)`,
            zIndex: -1,
            borderRadius: '50%',
            filter: 'blur(15px)'
          }} 
        />
        
        <svg viewBox="0 0 100 100" width="100%" height="100%">
          <defs>
            <pattern id="stripes" width="12" height="12" patternTransform="rotate(45 0 0)" patternUnits="userSpaceOnUse">
              <rect width="12" height="12" fill={colors.secondary} />
              <line x1="0" y1="0" x2="0" y2="12" stroke={colors.primary} strokeWidth="6" />
            </pattern>
            <mask id="circleMask">
              <circle cx="50" cy="50" r="50" fill="white" />
            </mask>
          </defs>
          <circle cx="50" cy="50" r="50" fill="url(#stripes)" mask="url(#circleMask)" />
        </svg>
      </motion.div>
    </motion.div>
  );
};

const CloudLayer = ({ path, fillId, opacity, zIndex, mouseX, parallaxFactor, scrollY, scrollFactor, blur }) => {
  const springConfig = { damping: 40, stiffness: 90 };
  const x = useSpring(mouseX * parallaxFactor, springConfig);
  const y = useTransform(scrollY, [0, 1000], [0, scrollFactor]);

  return (
    <motion.div
      style={{
        position: 'absolute',
        bottom: 0,
        left: '-10%',
        right: '-10%',
        width: '120%',
        height: '60%',
        zIndex,
        x,
        y,
        filter: blur ? `blur(${blur}px)` : 'none',
        opacity
      }}
    >
      <svg viewBox="0 0 1200 400" preserveAspectRatio="none" width="100%" height="100%">
        <path d={path} fill={`url(#${fillId})`} />
      </svg>
    </motion.div>
  );
};

const Starfield = () => {
  const [stars, setStars] = useState([]);
  const [shootingStars, setShootingStars] = useState([]);

  useEffect(() => {
    // Generate static stars once on mount
    setStars(Array.from({ length: 80 }).map((_, i) => ({
      id: i,
      left: `${Math.random() * 100}%`,
      top: `${Math.random() * 100}%`,
      size: Math.random() * 2.5 + 0.5,
      baseOpacity: Math.random() * 0.5 + 0.3,
      duration: Math.random() * 3 + 2,
      delay: Math.random() * 2
    })));

    setShootingStars(Array.from({ length: 4 }).map((_, i) => ({
      id: i,
      top: `${Math.random() * 40}%`,
      left: `${Math.random() * 40 + 60}%`, // Start mostly from top right
      delay: Math.random() * 15 + 2
    })));
  }, []);

  return (
    <div style={{ position: 'absolute', inset: 0, zIndex: 1, pointerEvents: 'none', overflow: 'hidden' }}>
      {stars.map((star) => (
        <motion.div
          key={`star-${star.id}`}
          initial={{ opacity: star.baseOpacity }}
          animate={{ opacity: [star.baseOpacity, star.baseOpacity * 0.1, star.baseOpacity] }}
          transition={{ duration: star.duration, delay: star.delay, repeat: Infinity, ease: 'easeInOut' }}
          style={{
            position: 'absolute',
            left: star.left,
            top: star.top,
            width: `${star.size}px`,
            height: `${star.size}px`,
            backgroundColor: 'white',
            borderRadius: '50%',
            boxShadow: `0 0 ${star.size * 2}px rgba(255,255,255,0.8)`
          }}
        />
      ))}

      {shootingStars.map((star) => (
        <motion.div
          key={`shooting-${star.id}`}
          initial={{ x: 200, y: -200, opacity: 0, scale: 0 }}
          animate={{ x: -1200, y: 1200, opacity: [0, 1, 1, 0], scale: [0, 1, 1, 0] }}
          transition={{ duration: 1.5, delay: star.delay, repeat: Infinity, repeatDelay: Math.random() * 10 + 5, ease: 'linear' }}
          style={{
            position: 'absolute',
            left: star.left,
            top: star.top,
            width: '120px',
            height: '2px',
            background: 'linear-gradient(90deg, rgba(255,255,255,1) 0%, rgba(255,255,255,0) 100%)',
            transform: 'rotate(-45deg)',
            transformOrigin: 'top right',
            boxShadow: '0 0 10px rgba(255,255,255,0.8)'
          }}
        />
      ))}
    </div>
  );
};

const FogLayer = ({ isDark }) => {
  return (
    <AnimatePresence>
      {!isDark && (
        <motion.div
          initial={{ opacity: 0, x: '-5%' }}
          animate={{ opacity: 1, x: '5%' }}
          exit={{ opacity: 0 }}
          transition={{ duration: 25, repeat: Infinity, repeatType: 'reverse', ease: 'easeInOut' }}
          style={{ position: 'absolute', top: '15%', left: 0, width: '100%', height: '50%', zIndex: 3, pointerEvents: 'none' }}
        >
          <div style={{ position: 'absolute', top: '20%', left: '10%', width: '60%', height: '40%', background: 'radial-gradient(ellipse at center, rgba(255,255,255,0.4) 0%, transparent 70%)', filter: 'blur(40px)', transform: 'rotate(-5deg)' }} />
          <div style={{ position: 'absolute', top: '40%', right: '10%', width: '70%', height: '50%', background: 'radial-gradient(ellipse at center, rgba(255,255,255,0.25) 0%, transparent 70%)', filter: 'blur(50px)', transform: 'rotate(10deg)' }} />
          <div style={{ position: 'absolute', top: '10%', left: '40%', width: '50%', height: '30%', background: 'radial-gradient(ellipse at center, rgba(255,255,255,0.35) 0%, transparent 70%)', filter: 'blur(35px)', transform: 'rotate(-2deg)' }} />
        </motion.div>
      )}
    </AnimatePresence>
  );
};

export default function Background() {
  const { theme } = useTheme();
  const isDark = theme === 'dark';
  
  const [mousePosition, setMousePosition] = useState({ x: 0, y: 0 });
  const { scrollY } = useScroll();

  useEffect(() => {
    const handleMouseMove = (e) => {
      setMousePosition({
        x: (e.clientX / window.innerWidth) - 0.5,
        y: (e.clientY / window.innerHeight) - 0.5
      });
    };
    window.addEventListener('mousemove', handleMouseMove);
    return () => window.removeEventListener('mousemove', handleMouseMove);
  }, []);

  const cloudPathBack = "M0,400 L0,220 C100,180 200,250 350,220 C500,190 600,280 750,240 C900,200 1050,280 1200,230 L1200,400 Z";
  const cloudPathMid = "M0,400 L0,280 C150,240 300,310 450,270 C600,230 800,320 950,280 C1100,240 1150,300 1200,290 L1200,400 Z";
  const cloudPathFront = "M0,400 L0,340 C200,300 400,360 600,330 C800,300 1000,370 1200,350 L1200,400 Z";

  const lightColors = {
    bg: 'linear-gradient(180deg, #38bdf8 0%, #bae6fd 100%)',
    cloudBack: { top: 'rgba(255, 255, 255, 0.4)', bottom: 'rgba(255, 255, 255, 0.8)' },
    cloudMid: { top: 'rgba(255, 255, 255, 0.6)', bottom: 'rgba(255, 255, 255, 1)' },
    cloudFront: { top: '#f0f9ff', bottom: '#ffffff' }, // Slightly off-white to pure white
  };

  const darkColors = {
    bg: 'linear-gradient(180deg, #020617 0%, #1e1b4b 100%)',
    cloudBack: { top: 'rgba(15, 23, 42, 0.4)', bottom: 'rgba(15, 23, 42, 0.7)' },
    cloudMid: { top: 'rgba(15, 23, 42, 0.7)', bottom: 'rgba(15, 23, 42, 0.95)' },
    cloudFront: { top: '#0f172a', bottom: '#050b14' }, // Matches clean background of next section
  };

  const currentColors = isDark ? darkColors : lightColors;

  return (
    <div style={{ position: 'absolute', inset: 0, overflow: 'hidden', zIndex: 0 }}>
      {/* Dynamic Background Gradient with Radial Wipe Transition */}
      <AnimatePresence>
        <motion.div
          key={isDark ? 'dark-bg' : 'light-bg'}
          initial={{ clipPath: 'circle(0% at 95% 5%)', zIndex: 1 }}
          animate={{ clipPath: 'circle(150% at 95% 5%)', zIndex: 1 }}
          exit={{ zIndex: 0 }} // Stays behind while the new one wipes over
          transition={{ duration: 1.2, ease: [0.65, 0, 0.35, 1] }}
          style={{ position: 'absolute', inset: 0, background: currentColors.bg }}
        />
      </AnimatePresence>

      {/* Aurora glow for dark mode */}
      <AnimatePresence>
        {isDark && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 1.5, ease: "easeInOut" }}
            style={{
              position: 'absolute',
              inset: 0,
              background: 'radial-gradient(circle at 15% 25%, rgba(16, 185, 129, 0.12) 0%, transparent 60%), radial-gradient(circle at 85% 65%, rgba(139, 92, 246, 0.12) 0%, transparent 60%)',
              zIndex: 1
            }}
          />
        )}
      </AnimatePresence>
      
      {/* Stars in Dark Mode */}
      <AnimatePresence>
        {isDark && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 1.2 }}
            style={{ position: 'absolute', inset: 0, zIndex: 1 }}
          >
            <Starfield />
          </motion.div>
        )}
      </AnimatePresence>
      
      <FogLayer isDark={isDark} />

      {/* Sun or Moon */}
      <StripedOrb isDark={isDark} mouseX={mousePosition.x} mouseY={mousePosition.y} scrollY={scrollY} />

      {/* Cloud Gradient Definitions */}
      <svg style={{ width: 0, height: 0, position: 'absolute' }}>
        <defs>
          <linearGradient id="cloudGradBack" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={currentColors.cloudBack.top} />
            <stop offset="100%" stopColor={currentColors.cloudBack.bottom} />
          </linearGradient>
          <linearGradient id="cloudGradMid" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={currentColors.cloudMid.top} />
            <stop offset="100%" stopColor={currentColors.cloudMid.bottom} />
          </linearGradient>
          <linearGradient id="cloudGradFront" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={currentColors.cloudFront.top} />
            <stop offset="100%" stopColor={currentColors.cloudFront.bottom} />
          </linearGradient>
        </defs>
      </svg>

      {/* Animated Parallax Cloud Layers with blur on deeper layers */}
      <CloudLayer 
        path={cloudPathBack} 
        fillId="cloudGradBack"
        opacity={0.8}
        zIndex={3} 
        mouseX={mousePosition.x} 
        parallaxFactor={20}
        scrollY={scrollY}
        scrollFactor={-40}
        blur={4}
      />
      <CloudLayer 
        path={cloudPathMid} 
        fillId="cloudGradMid"
        opacity={0.9}
        zIndex={4} 
        mouseX={mousePosition.x} 
        parallaxFactor={50}
        scrollY={scrollY}
        scrollFactor={-80}
        blur={1.5}
      />
      <CloudLayer 
        path={cloudPathFront} 
        fillId="cloudGradFront"
        opacity={1}
        zIndex={5} 
        mouseX={mousePosition.x} 
        parallaxFactor={90}
        scrollY={scrollY}
        scrollFactor={-130}
      />

      {/* Film Grain Texture Overlay */}
      <GrainOverlay />
    </div>
  );
}
