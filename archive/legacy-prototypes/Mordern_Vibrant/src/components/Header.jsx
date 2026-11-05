import React, { useState, useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { motion, AnimatePresence, useScroll } from 'framer-motion';
import { useTheme } from '../context/ThemeContext';
import { MapPin, Mail } from 'lucide-react';
import SectionSelector from './SectionSelector';

const SunIcon = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" style={{ filter: 'drop-shadow(0px 0px 4px rgba(245, 158, 11, 0.3))' }}>
    <circle cx="12" cy="12" r="11" fill="white" />
    <circle cx="12" cy="12" r="3.5" fill="#f59e0b" />
    {[0, 45, 90, 135, 180, 225, 270, 315].map((angle, i) => (
      <circle 
        key={i} 
        cx={12 + Math.cos(angle * Math.PI / 180) * 7.5} 
        cy={12 + Math.sin(angle * Math.PI / 180) * 7.5} 
        r="1.2" 
        fill="#f59e0b" 
      />
    ))}
  </svg>
);

const MoonIcon = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" style={{ filter: 'drop-shadow(0px 0px 4px rgba(254, 240, 138, 0.2))' }}>
    <circle cx="12" cy="12" r="11" fill="#0f172a" />
    <path d="M12 5.5a6.5 6.5 0 0 1 5.87 9.42 7 7 0 0 0-8.8-8.8A6.5 6.5 0 0 1 12 5.5z" fill="#fef08a" />
  </svg>
);

export default function Header() {
  const location = useLocation();
  const { theme, toggleTheme } = useTheme();
  
  const { scrollY } = useScroll();
  const [isScrolled, setIsScrolled] = useState(false);

  useEffect(() => {
    return scrollY.onChange((latest) => {
      setIsScrolled(latest > 50);
    });
  }, [scrollY]);

  const navItems = [
    { name: 'Work', path: '/' },
    { name: 'About', path: '/about' },
    { name: 'Playground', path: '/playground' }
  ];

  return (
    <>
      {/* Top Left Section Selector */}
      <motion.div
        initial={{ opacity: 0, x: -20 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ delay: 0.1 }}
      >
        <SectionSelector />
      </motion.div>

      {/* Top Right Theme Toggle */}
      <motion.button
        onClick={toggleTheme}
        whileHover={{ scale: 1.1 }}
        whileTap={{ scale: 0.9 }}
        data-cursor-expand="true"
        initial={{ opacity: 0, x: 20 }}
        animate={{ opacity: 1, x: 0 }}
        style={{
          position: 'fixed',
          top: '2.2rem',
          right: '3rem',
          zIndex: 60,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: '40px',
          height: '40px',
          borderRadius: '50%',
          backgroundColor: 'rgba(255, 255, 255, 0.2)',
          backdropFilter: 'blur(10px)',
          border: '1px solid rgba(255, 255, 255, 0.4)',
          color: 'var(--text-primary)',
          cursor: 'none'
        }}
      >
        {theme === 'light' ? <SunIcon /> : <MoonIcon />}
      </motion.button>

      {/* Centered Frosted Page Selector */}
      <motion.header 
        initial={{ y: -100, x: '-50%' }}
        animate={{ y: 0, x: '-50%' }}
        transition={{ type: 'spring', stiffness: 100, damping: 20 }}
        style={{
          position: 'fixed',
          top: '1.5rem',
          left: '50%',
          zIndex: 50,
          display: 'flex',
          alignItems: 'center',
          backgroundColor: 'rgba(255, 255, 255, 0.2)',
          backdropFilter: 'blur(12px)',
          WebkitBackdropFilter: 'blur(12px)',
          padding: '0.35rem 0.35rem 0.35rem 0.5rem',
          borderRadius: '3rem',
          border: '1px solid rgba(255, 255, 255, 0.3)',
          boxShadow: '0 4px 15px rgba(0,0,0,0.05)',
          gap: '1rem'
        }}
      >
        <AnimatePresence>
          {/* Profile Pic */}
          {!isScrolled && (
            <motion.div 
              data-cursor-expand="true"
              initial={{ opacity: 0, width: 0, scale: 0 }}
              animate={{ opacity: 1, width: '36px', scale: 1 }}
              exit={{ opacity: 0, width: 0, scale: 0 }}
              whileHover={{ scale: 1.1 }}
              style={{
                height: '36px',
                borderRadius: '50%',
                overflow: 'hidden',
                backgroundColor: '#ccc',
                flexShrink: 0,
                transformOrigin: 'center'
              }}
            >
              <img src="https://i.pravatar.cc/100?img=5" alt="Profile" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
            </motion.div>
          )}
        </AnimatePresence>

        {/* Nav Items */}
        <nav style={{ display: 'flex', gap: '0.25rem', fontWeight: 500, fontSize: '0.95rem' }}>
          {navItems.map((item) => {
            const isActive = location.pathname === item.path;
            
            return (
              <Link 
                key={item.name} 
                to={item.path}
                data-cursor-expand="true"
                style={{ 
                  position: 'relative',
                  padding: '0.6rem 1.25rem', 
                  color: 'var(--text-primary)',
                  textDecoration: 'none',
                  zIndex: 1
                }}
              >
                {item.name}
                
                {/* Active Background (Click only) */}
                {isActive && (
                  <motion.div
                    layoutId="nav-pill"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{ type: "spring", bounce: 0.25, duration: 0.5 }}
                    style={{
                      position: 'absolute',
                      inset: 0,
                      backgroundColor: 'rgba(255, 255, 255, 0.8)',
                      borderRadius: '1.5rem',
                      zIndex: -1,
                      border: '1px solid rgba(0,0,0,0.05)'
                    }}
                  />
                )}
              </Link>
            )
          })}
        </nav>

        <AnimatePresence>
          {/* Work with me button */}
          {!isScrolled && (
            <motion.div
              initial={{ opacity: 0, width: 0, scale: 0 }}
              animate={{ opacity: 1, width: 'auto', scale: 1 }}
              exit={{ opacity: 0, width: 0, scale: 0 }}
              style={{ overflow: 'hidden' }}
            >
              <motion.div whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}>
                <Link 
                  to="/contact"
                  data-cursor-expand="true"
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.5rem',
                    padding: '0.6rem 1.25rem',
                    backgroundColor: 'white',
                    color: '#1a1a1a',
                    borderRadius: '2rem',
                    fontWeight: 600,
                    fontSize: '0.9rem',
                    textDecoration: 'none',
                    marginLeft: '0.5rem',
                    boxShadow: '0 2px 10px rgba(0,0,0,0.1)',
                    whiteSpace: 'nowrap'
                  }}
                >
                  <Mail size={16} /> Work with me
                </Link>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>

      </motion.header>
    </>
  );
}
