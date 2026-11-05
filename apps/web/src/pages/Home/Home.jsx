import React, { useEffect, useState, lazy, Suspense } from 'react';
import HeroSection from './components/01.HeroSection';

// Below-the-fold dynamic chunks (Zero bytes loaded during preloader)
const Marquee1Section = lazy(() => import('./components/02.Marquee1Section'));
const ServicesSection = lazy(() => import('./components/03.ServicesSection'));
const ApproachNoteSection = lazy(() => import('./components/04.ApproachNoteSection'));
const ContactSection = lazy(() => import('./components/05.ContactSection'));
const SiteFooter = lazy(() => import('./components/06.SiteFooter'));

/**
 * Home - Progressive Editorial Layout
 * 
 * Benchmarked against Pensatori Irrazionali:
 * - Mounts ONLY HeroSection in the critical viewport.
 * - Schedules below-the-fold components via requestIdleCallback
 *   so they stream progressively in the background without frame drops.
 */
export default function Home({ 
  isRevealed, 
  onOpenMenu, 
  isMenuOpen, 
  onNavigate,
  onLogoClick,
  isTransitioning = false,
  isPageReady = true
}) {
  const [loadBelowFold, setLoadBelowFold] = useState(true);

  useEffect(() => {
    document.title = 'Mohammad Faizal';
    if (typeof window !== 'undefined') {
      window.__HOME_PART2_READY__ = true;
      window.__HOME_DOM_READY__ = true;
    }
  }, []);

  // Progressive background idle streaming (Pensatori Irrazionali standard)
  useEffect(() => {
    if (isRevealed || loadBelowFold) {
      if (typeof window !== 'undefined' && 'requestIdleCallback' in window) {
        const id = window.requestIdleCallback(() => {
          setLoadBelowFold(true);
        }, { timeout: 1200 });
        return () => window.cancelIdleCallback(id);
      } else {
        const timer = setTimeout(() => {
          setLoadBelowFold(true);
        }, 300);
        return () => clearTimeout(timer);
      }
    }
  }, [isRevealed, loadBelowFold]);

  return (
    <main className="pb-38 md:pb-64 w-screen overflow-x-hidden">
      <h1 className="sr-only">Mohammad Faizal</h1>
      
      {/* 1. CRITICAL VIEWPORT: HERO SECTION */}
      <div id="section-hero">
        <HeroSection 
          isRevealed={isRevealed} 
          onOpenMenu={onOpenMenu}
          isMenuOpen={isMenuOpen}
          onNavigate={onNavigate}
          onLogoClick={onLogoClick}
          isTransitioning={isTransitioning}
          isPageReady={isPageReady}
        />
      </div>
      
      {/* 2. PROGRESSIVE STREAMING: BELOW-THE-FOLD SECTIONS */}
      {loadBelowFold && (
        <Suspense fallback={<div className="w-full min-h-[30vh]" aria-hidden="true" />}>
          <div id="section-marquee1">
            <Marquee1Section onNavigate={onNavigate} />
          </div>
          <ServicesSection onNavigate={onNavigate} />
          <ApproachNoteSection />
          <ContactSection onNavigate={onNavigate} />
          <SiteFooter onNavigate={onNavigate} />
        </Suspense>
      )}
    </main>
  );
}
