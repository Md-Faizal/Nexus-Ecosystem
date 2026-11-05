import React from 'react';
import { m } from 'framer-motion';
import PageHeader from './PageHeader';

export default function PageCard({ 
  title, 
  label, 
  description, 
  onOpenMenu, 
  isMenuOpen, 
  onNavigate, 
  isTransitioning = false,
  children 
}) {
  const containerVariants = {
    hidden: { opacity: 0 },
    show: {
      opacity: 1,
      transition: {
        staggerChildren: 0.12,
        delayChildren: 0.1
      }
    },
    exit: {
      opacity: 0,
      y: -20,
      transition: { duration: 0.4 }
    }
  };

  const itemVariants = {
    hidden: { opacity: 0, y: 20 },
    show: { opacity: 1, y: 0, transition: { duration: 0.6, ease: [0.16, 1, 0.3, 1] } }
  };

  return (
    <div className="min-h-screen bg-black text-white relative">
      {/* Top Page Header (Logo & Menu Button) */}
      <PageHeader
        onOpenMenu={onOpenMenu}
        isMenuOpen={isMenuOpen}
        onNavigate={onNavigate}
        currentPage="secondary"
        isTransitioning={isTransitioning}
        isPageReady={true}
      />

      {/* Main Page Content */}
      <m.div
        variants={containerVariants}
        initial="hidden"
        animate="show"
        style={{
          padding: '8rem 2rem 5rem',
          minHeight: '100vh',
          display: 'flex',
          flexDirection: 'column',
          maxWidth: '1200px',
          margin: '0 auto'
        }}
      >
        {label && (
          <m.div variants={itemVariants} style={{ marginBottom: '1rem' }}>
            <span className="text-xs font-mono text-[#89ff00] uppercase tracking-widest font-semibold px-3 py-1 rounded-full bg-[#89ff00]/10 border border-[#89ff00]/20 inline-block">
              {label}
            </span>
          </m.div>
        )}
        
        {title && (
          <m.div variants={itemVariants} style={{ marginBottom: '1.5rem' }}>
            <h1 className="text-4xl sm:text-6xl md:text-7xl font-medium tracking-tight text-white m-0 leading-none">
              {title}
            </h1>
          </m.div>
        )}

        {description && (
          <m.div variants={itemVariants} style={{ marginBottom: '3.5rem', maxWidth: '650px' }}>
            <p className="text-lg md:text-xl text-neutral-400 leading-relaxed">
              {description}
            </p>
          </m.div>
        )}

        {/* Main Visual or Content */}
        <m.div variants={itemVariants} style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
          {children}
        </m.div>
      </m.div>
    </div>
  );
}
