import React, { useEffect, useState, useCallback } from 'react';
import HeroSection from './components/01.HeroSection';
import Marquee1Section from './components/02.Marquee1Section';
import ServicesSection from './components/03.ServicesSection';
import ApproachNoteSection from './components/04.ApproachNoteSection';
import ContactSection from './components/05.ContactSection';
import SiteFooter from './components/06.SiteFooter';
import PageHeader from '../../components/common/PageHeader';

/**
 * Home - Seamless Editorial Layout with Monitor Zoom-In Illusion
 * 
 * - Section 2 (Marquee1Section) is docked at top: 0 in the normal page flow.
 * - The 3D Hero Section is pinned as an interactive spatial overlay when scrollY <= 5.
 * - During Hero zoom (progress 0 -> 1), the monitor on the desk turns on and displays Section 2,
 *   scaling up until it fills 100% of the viewport (scale 1.0).
 * - There is zero scrolling down to reach Section 2. At progress = 1.0, the user is already at Section 2.
 * - Normal downward page scrolling proceeds seamlessly from Section 2 through all downstream sections.
 * - Scrolling up to scrollY = 0 smoothly re-engages the zoom-out into the monitor on the desk.
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
  const [isScrolledDown, setIsScrolledDown] = useState(false);
  const [headerLogoStage, setHeaderLogoStage] = useState('hidden'); // 'hidden' | 'red' | 'lime'
  const [showTopLeftLogo, setShowTopLeftLogo] = useState(false);

  useEffect(() => {
    document.title = 'Mohammad Faizal';
    if (typeof window !== 'undefined') {
      window.__HOME_PART2_READY__ = true;
      window.__HOME_DOM_READY__ = true;
    }

    const checkScroll = () => {
      setIsScrolledDown(window.scrollY > 5);
      setIsScrolledDown(window.scrollY > 2);
    };

    window.addEventListener('scroll', checkScroll, { passive: true });
    return () => window.removeEventListener('scroll', checkScroll);
  }, []);

  const handleHeaderStageChange = useCallback(({ showLogo, stage }) => {
    setShowTopLeftLogo(showLogo);
    setHeaderLogoStage(stage);
  }, []);

  return (
    <main className="pb-38 md:pb-64 w-screen overflow-x-hidden min-h-screen relative">
      <h1 className="sr-only">Mohammad Faizal</h1>
      
      {/* 1. SEAMLESS GLOBAL PAGE HEADER (Floating at z-997 across hero and all sections) */}
      <PageHeader 
        isMenuOpen={isMenuOpen}
        onOpenMenu={onOpenMenu}
        onNavigate={onNavigate}
        onLogoClick={onLogoClick}
        currentPage="home"
        isTransitioning={isTransitioning}
        isPageReady={isPageReady}
        showHomeLogo={showTopLeftLogo}
        logoColorStage={headerLogoStage}
      />

      {/* 2. SEAMLESS PAGE CONTENT: Starts at top: 0 with Section 2 (Marquee1Section) */}
      <div id="page-content" className="relative z-10 w-full">
        <div id="section-marquee1">
          <Marquee1Section onNavigate={onNavigate} />
        </div>
        <ServicesSection onNavigate={onNavigate} />
        <ApproachNoteSection />
        <ContactSection onNavigate={onNavigate} />
        <SiteFooter onNavigate={onNavigate} />
      </div>

      {/* 3. 3D HERO SECTION OVERLAY: Fixed over viewport when scrollY <= 5 */}
      {/* 3. 3D HERO SECTION OVERLAY: Fixed over viewport when scrollY <= 2 */}
      <div 
        className="fixed inset-0 z-30 transition-opacity duration-200 pointer-events-none"
        className="fixed inset-0 z-30 pointer-events-none"
        style={{
          opacity: isScrolledDown ? 0 : 1,
          visibility: isScrolledDown ? 'hidden' : 'visible'
        }}
      >
        <div className="w-full h-full pointer-events-auto">
          <HeroSection 
            isRevealed={isRevealed} 
            onOpenMenu={onOpenMenu}
            isMenuOpen={isMenuOpen}
            onNavigate={onNavigate}
            onLogoClick={onLogoClick}
            isTransitioning={isTransitioning}
            isPageReady={isPageReady}
            onHeaderStageChange={handleHeaderStageChange}
          />
        </div>
      </div>
    </main>
  );
}
