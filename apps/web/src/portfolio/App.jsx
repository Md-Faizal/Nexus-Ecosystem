import React, { useState, useCallback, useEffect, useRef, lazy, Suspense } from 'react';
import { LazyMotion, domAnimation } from 'framer-motion';
import { ThemeProvider } from './context/ThemeContext';
import Loader from './components/Loader/Loader';
import PageTransitioner from './components/common/PageTransitioner';
import DevFloatingHUD from './components/common/DevFloatingHUD';

// Lazy load page components
const Home = lazy(() => import('./pages/Home/Home'));
const About = lazy(() => import('./pages/About/About'));
const Work = lazy(() => import('./pages/Work/Work'));
const Services = lazy(() => import('./pages/Services/Services'));
const Contact = lazy(() => import('./pages/Contact/Contact'));

import NavigationMenu from './components/common/NavigationMenu';
import PasscodeModal from '../shared/PasscodeModal';
import CustomScrollbar from './components/common/CustomScrollbar';

// Initialize Part 2 chunk readiness
window.__HOME_PART2_READY__ = false;

const STORAGE_KEY = 'PI_DEV_STAGE';

function getInitialDevState() {
  if (typeof window === 'undefined') {
    return { stage: null, progress: null, section: null, speed: 1 };
  }

  const params = new URLSearchParams(window.location.search);
  const devParam = params.get('dev') || params.get('stage') || params.get('freeze');
  if (devParam) {
    const p = Number(params.get('p')) || (devParam === 'loader' ? 50 : 100);
    const section = params.get('section');
    const speed = Number(params.get('speed')) || 1;
    return { stage: devParam, progress: p, section, speed };
  }

  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) {
      const parsed = JSON.parse(saved);
      return {
        stage: parsed.stage || null,
        progress: parsed.progress !== undefined ? parsed.progress : null,
        section: parsed.section || null,
        speed: parsed.speed || 1
      };
    }
  } catch {}

  return { stage: null, progress: null, section: null, speed: 1 };
}

function saveDevState(stage, progress = null, section = null, speed = 1) {
  try {
    if (!stage && !section && speed === 1) {
      localStorage.removeItem(STORAGE_KEY);
    } else {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ stage, progress, section, speed, timestamp: Date.now() }));
    }
  } catch {}
}

function App() {
  const initialDev = useRef(getInitialDevState()).current;

  // Dismiss instant HTML preloader shell immediately once React has mounted (zero ghosting)
  useEffect(() => {
    // Preload Home page chunk in background so section-hero is immediately ready before Z-expansion
    import('./pages/Home/Home').catch(() => {});

    const shell = document.getElementById('app-shell-preloader');
    if (shell) {
      shell.style.display = 'none';
      if (shell.parentNode) {
        shell.parentNode.removeChild(shell);
      }
    }
  }, []);

  const [freezeStage, setFreezeStage] = useState(initialDev.stage); // 'loader' | 'logo' | 'expansion' | 'home' | null
  const [forcedProgress, setForcedProgress] = useState(initialDev.progress);
  const [targetSection, setTargetSection] = useState(initialDev.section);
  const [playbackSpeed, setPlaybackSpeed] = useState(initialDev.speed || 1);

  // Intro Sequence Lifecycle
  const [introDone, setIntroDone] = useState(initialDev.stage === 'home');
  const [heroRevealed, setHeroRevealed] = useState(initialDev.stage === 'home');

  // Inter-Page Navigation State (for PageTransitioner package)
  const [activePage, setActivePage] = useState('home'); // 'home' | 'about' | 'work' | 'services' | 'contact'
  const [navTargetPage, setNavTargetPage] = useState(null);
  const [isNavigating, setIsNavigating] = useState(false);
  const [isMenuOpen, setIsMenuOpen] = useState(false);

  // Easter Egg 5-Click Passcode Drawer State
  const [isPasscodeOpen, setIsPasscodeOpen] = useState(false);
  const logoClickCountRef = useRef(0);
  const logoClickTimerRef = useRef(null);

  const handleHomeLogoClick = useCallback(() => {
    logoClickCountRef.current += 1;
    if (logoClickTimerRef.current) clearTimeout(logoClickTimerRef.current);

    if (logoClickCountRef.current >= 5) {
      logoClickCountRef.current = 0;
      setIsPasscodeOpen(true);
      return;
    }

    // Reset click count if idle for 2.5 seconds
    logoClickTimerRef.current = setTimeout(() => {
      logoClickCountRef.current = 0;
    }, 2500);
  }, []);

  // Dynamic Document Title Synchronization
  useEffect(() => {
    const pageTitles = {
      home: 'Mohammad Faizal',
      about: 'About - Mohammad Faizal',
      work: 'Work - Mohammad Faizal',
      services: 'Services - Mohammad Faizal',
      contact: 'Contact - Mohammad Faizal'
    };
    document.title = pageTitles[activePage] || 'Mohammad Faizal';
  }, [activePage]);

  const handleNavigate = useCallback((target) => {
    if (!target) return;
    setIsMenuOpen(false);
    if (target === activePage) {
      if (target === 'home') {
        window.scrollTo({ top: 0, behavior: 'smooth' });
      }
      return;
    }
    setNavTargetPage(target);
    setIsNavigating(true);
  }, [activePage]);

  // Live real-time playback synchronization for Dev HUD
  const [liveLoaderPhase, setLiveLoaderPhase] = useState('LOADING'); // 'LOADING' | 'SWEEPING' | 'EXPANDING' | 'DONE'
  const [liveLoaderProgress, setLiveLoaderProgress] = useState(0);
  const [liveScrollPercent, setLiveScrollPercent] = useState(0);

  // Real-time window scroll tracker to synchronize master scrubber with live page position
  useEffect(() => {
    const handleScroll = () => {
      const maxScroll = Math.max(1, document.documentElement.scrollHeight - window.innerHeight);
      const currentScroll = window.scrollY || window.pageYOffset || 0;
      const ratio = Math.min(1, Math.max(0, currentScroll / maxScroll));
      setLiveScrollPercent(ratio);
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const handleLoaderComplete = useCallback(() => {
    if (freezeStage === 'loader' || freezeStage === 'logo' || freezeStage === 'expansion') {
      return;
    }
    setHeroRevealed(true);
    setLiveLoaderPhase('DONE');
    setIntroDone(true);
  }, [freezeStage]);

  const [detectedSection, setDetectedSection] = useState(initialDev.section || 'hero');

  // Dynamic live IntersectionObserver / scroll tracker to detect current section
  useEffect(() => {
    if (!heroRevealed && freezeStage !== 'home') return;

    const sectionIds = [
      'section-hero',
      'section-marquee1',
      'section-services',
      'section-approach-note',
      'section-cta'
    ];

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            const cleanId = entry.target.id.replace('section-', '');
            setDetectedSection(cleanId);
          }
        });
      },
      {
        root: null,
        rootMargin: '-20% 0px -40% 0px',
        threshold: 0.15
      }
    );

    sectionIds.forEach((id) => {
      const el = document.getElementById(id);
      if (el) observer.observe(el);
    });

    return () => observer.disconnect();
  }, [heroRevealed, freezeStage]);

  // Handle initial section scrolling if restored from persistence
  useEffect(() => {
    if (targetSection) {
      setTimeout(() => {
        const el = document.getElementById(`section-${targetSection}`);
        if (el) {
          el.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
      }, 300);
    }
  }, [targetSection]);

  const scrollToSection = useCallback((id, label, sectionKey) => {
    setFreezeStage('home');
    setForcedProgress(100);
    setIntroDone(true);
    setHeroRevealed(true);
    setTargetSection(sectionKey);
    saveDevState('home', 100, sectionKey, playbackSpeed);

    setTimeout(() => {
      const el = document.getElementById(id);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
      } else {
        window.scrollTo({ top: 0, behavior: 'smooth' });
      }
    }, 100);

    const msg = `🚀 [Dev] Navigated to ${label}`;
    console.log(msg);
    return msg;
  }, [playbackSpeed]);

  // Stage Action Handlers
  const handleFreezeLoader = useCallback((percent = 50) => {
    const p = Number(percent) || 50;
    setFreezeStage('loader');
    setForcedProgress(p);
    setIntroDone(false);
    setHeroRevealed(false);
    setTargetSection(null);
    saveDevState('loader', p, null, playbackSpeed);
    const msg = `🧊 [Dev] Frozen at LOADER (${p}%)`;
    console.log(msg);
    return msg;
  }, [playbackSpeed]);

  const handleFreezeLogo = useCallback((percent = 100) => {
    const p = percent !== undefined && percent !== null ? Number(percent) : 100;
    setFreezeStage('logo');
    setForcedProgress(p);
    setIntroDone(false);
    setHeroRevealed(false);
    setTargetSection(null);
    saveDevState('logo', p, null, playbackSpeed);
    const msg = `🧊 [Dev] Frozen at SWEEP / MONOGRAM (${p}%)`;
    console.log(msg);
    return msg;
  }, [playbackSpeed]);

  const handleFreezeExpansion = useCallback((percent = 50) => {
    const p = percent !== undefined && percent !== null ? Number(percent) : 50;
    setFreezeStage('expansion');
    setForcedProgress(p);
    setIntroDone(false);
    setHeroRevealed(true);
    setTargetSection(null);
    saveDevState('expansion', p, null, playbackSpeed);
    const msg = `🧊 [Dev] Frozen at MONOGRAM EXPANSION (${p}%)`;
    console.log(msg);
    return msg;
  }, [playbackSpeed]);

  const handleFreezeHome = useCallback(() => {
    setFreezeStage('home');
    setForcedProgress(100);
    setIntroDone(true);
    setHeroRevealed(true);
    setTargetSection(null);
    saveDevState('home', 100, null, playbackSpeed);
    window.scrollTo({ top: 0, behavior: 'smooth' });
    const msg = '🧊 [Dev] Set to HOME stage';
    console.log(msg);
    return msg;
  }, [playbackSpeed]);

  const handleLoadSection = useCallback((name) => {
    const key = String(name).toLowerCase().trim();
    const sectionMap = {
      'hero': { id: 'section-hero', label: 'Hero (01)' },
      'marquee1': { id: 'section-marquee1', label: 'Works transition' },
      'services': { id: 'section-services', label: 'Services' },
      'approach': { id: 'section-approach-note', label: 'Approach note' },
      'cta': { id: 'section-cta', label: 'Work / Contact' },
      'contact': { id: 'section-cta', label: 'Work / Contact' }
    };

    const target = sectionMap[key] || { id: `section-${key}`, label: key };
    return scrollToSection(target.id, target.label, key);
  }, [scrollToSection]);

  const handleSetSpeed = useCallback((spd) => {
    const val = Number(spd) || 1;
    setPlaybackSpeed(val);
    saveDevState(freezeStage, forcedProgress, targetSection, val);
    const msg = `⏱️ [Dev] Speed set to ${val}x`;
    console.log(msg);
    return msg;
  }, [freezeStage, forcedProgress, targetSection]);

  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTimelinePercent, setCurrentTimelinePercent] = useState(0);
  const timelineRef = useRef(0);
  const playRafRef = useRef(null);
  const lastTimeRef = useRef(null);

  const handleMasterScrub = useCallback((percent, persist = false) => {
    const val = Math.min(100, Math.max(0, Number(percent) || 0));
    timelineRef.current = val;
    setCurrentTimelinePercent(val);

    // Phase 1: 0% -> 25% = Cyber HUD Counter (0% -> 100%)
    if (val < 25) {
      const loaderProgress = Math.round((val / 25) * 100);
      setFreezeStage('loader');
      setForcedProgress(loaderProgress);
      setIntroDone(false);
      setHeroRevealed(false);
      setTargetSection(null);
      if (window.scrollY > 0) window.scrollTo({ top: 0, behavior: 'auto' });
      if (persist) {
        saveDevState('loader', loaderProgress, null, playbackSpeed);
      }
      return;
    }

    // Phase 2: 25% -> 45% = 5-Bar Sweep (5 -> 3 -> 1) & Monogram Vector Draw
    if (val < 45) {
      const sweepRatio = (val - 25) / 20; // 0.0 to 1.0
      const sweepProg = Math.round(sweepRatio * 100);
      setFreezeStage('logo');
      setForcedProgress(sweepProg);
      setIntroDone(false);
      setHeroRevealed(false);
      setTargetSection(null);
      if (window.scrollY > 0) window.scrollTo({ top: 0, behavior: 'auto' });
      if (persist) {
        saveDevState('logo', sweepProg, null, playbackSpeed);
      }
      return;
    }

    // Phase 3: 45% -> 60% = Seamless Monogram Z-Expansion Zoom Reveal into Home
    if (val < 60) {
      const expRatio = (val - 45) / 15; // 0.0 to 1.0
      const expProgress = Math.round(expRatio * 100);
      setFreezeStage('expansion');
      setForcedProgress(expProgress);
      setIntroDone(false);
      setHeroRevealed(true);
      setTargetSection(null);
      if (window.scrollY > 0) window.scrollTo({ top: 0, behavior: 'auto' });
      if (persist) {
        saveDevState('expansion', expProgress, null, playbackSpeed);
      }
      return;
    }

    // Phase 4: 60% -> 100% = Home Page with Continuous Smooth Scroll to bottom
    setFreezeStage('home');
    setForcedProgress(100);
    setIntroDone(true);
    setHeroRevealed(true);

    const scrollRatio = (val - 60) / 40; // 0.0 at 60%, 1.0 at 100%
    const maxScroll = Math.max(0, document.documentElement.scrollHeight - window.innerHeight);
    window.scrollTo({
      top: scrollRatio * maxScroll,
      behavior: 'auto'
    });

    if (persist) {
      saveDevState('home', 100, null, playbackSpeed);
    }
  }, [playbackSpeed]);

  // Continuous Playback linked to Master Scrubber (High performance, zero disk thrashing)
  useEffect(() => {
    if (!isPlaying) {
      if (playRafRef.current) cancelAnimationFrame(playRafRef.current);
      lastTimeRef.current = null;
      return;
    }

    const step = (now) => {
      if (!lastTimeRef.current) lastTimeRef.current = now;
      const delta = now - lastTimeRef.current;
      lastTimeRef.current = now;

      const totalDurationMs = 12000 / (playbackSpeed || 1); // 12 seconds full timeline at 1x
      const next = timelineRef.current + (delta / totalDurationMs) * 100;

      if (next >= 100) {
        timelineRef.current = 100;
        handleMasterScrub(100, true);
        setIsPlaying(false);
        return;
      }

      timelineRef.current = next;
      handleMasterScrub(next, false);
      playRafRef.current = requestAnimationFrame(step);
    };

    playRafRef.current = requestAnimationFrame(step);
    return () => {
      if (playRafRef.current) cancelAnimationFrame(playRafRef.current);
    };
  }, [isPlaying, playbackSpeed, handleMasterScrub]);

  const handleTogglePlay = useCallback(() => {
    setIsPlaying((prev) => {
      if (!prev && timelineRef.current >= 100) {
        handleMasterScrub(0, false);
      }
      return !prev;
    });
  }, [handleMasterScrub]);

  const handleResume = useCallback(() => {
    setIsPlaying(false);
    setFreezeStage(null);
    setForcedProgress(null);
    setTargetSection(null);
    setIntroDone(false);
    setHeroRevealed(false);
    timelineRef.current = 0;
    setCurrentTimelinePercent(0);
    saveDevState(null);
    window.scrollTo({ top: 0, behavior: 'auto' });
  }, []);

  const handleClear = useCallback(() => {
    setIsPlaying(false);
    saveDevState(null);
  }, []);

  // Compute Master percentage representation for HUD
  const computeMasterPercent = () => {
    if (isPlaying || currentTimelinePercent > 0) {
      return currentTimelinePercent;
    }
    if (freezeStage === 'loader') {
      const p = forcedProgress !== null && forcedProgress !== undefined ? forcedProgress : 50;
      return (p / 100) * 25;
    }
    if (freezeStage === 'logo') {
      const p = forcedProgress !== null && forcedProgress !== undefined ? forcedProgress : 100;
      return 25 + (p / 100) * 20;
    }
    if (freezeStage === 'expansion') {
      const p = forcedProgress !== null && forcedProgress !== undefined ? forcedProgress : 50;
      return 45 + (p / 100) * 15;
    }
    if (freezeStage === 'home') {
      return 60 + liveScrollPercent * 40;
    }

    // LIVE REAL-TIME AUTONOMOUS PLAYBACK:
    if (!introDone) {
      if (liveLoaderPhase === 'LOADING') {
        return (liveLoaderProgress / 100) * 25;
      }
      if (liveLoaderPhase === 'SWEEPING') {
        return 35;
      }
      if (liveLoaderPhase === 'EXPANDING') {
        return 52;
      }
    }

    // LIVE HOME PAGE SCROLL:
    return 60 + liveScrollPercent * 40;
  };

  // Determine real-time active stage
  const currentActiveStage = freezeStage 
    ? freezeStage 
    : (introDone || heroRevealed 
        ? 'home' 
        : (liveLoaderPhase === 'LOADING' ? 'loader' : (liveLoaderPhase === 'SWEEPING' ? 'logo' : 'expansion')));

  // Helper to render current page
  const renderPageComponent = useCallback((pageKey) => {
    const isLoaded = introDone || heroRevealed || freezeStage === 'home';
    switch (pageKey) {
      case 'about':
        return (
          <About 
            onOpenMenu={() => setIsMenuOpen(true)} 
            isMenuOpen={isMenuOpen} 
            onNavigate={handleNavigate} 
            isTransitioning={isNavigating}
            isPageReady={isLoaded}
          />
        );
      case 'work':
        return (
          <Work 
            onOpenMenu={() => setIsMenuOpen(true)} 
            isMenuOpen={isMenuOpen} 
            onNavigate={handleNavigate} 
            isTransitioning={isNavigating}
            isPageReady={isLoaded}
          />
        );
      case 'services':
        return (
          <Services 
            onOpenMenu={() => setIsMenuOpen(true)} 
            isMenuOpen={isMenuOpen} 
            onNavigate={handleNavigate} 
            isTransitioning={isNavigating}
            isPageReady={isLoaded}
          />
        );
      case 'contact':
        return (
          <Contact 
            onOpenMenu={() => setIsMenuOpen(true)} 
            isMenuOpen={isMenuOpen} 
            onNavigate={handleNavigate} 
            isTransitioning={isNavigating}
            isPageReady={isLoaded}
          />
        );
      case 'home':
      default:
        return (
          <Home 
            isRevealed={heroRevealed || liveLoaderPhase === 'EXPANDING' || freezeStage === 'home'} 
            onOpenMenu={() => setIsMenuOpen(true)}
            isMenuOpen={isMenuOpen}
            onNavigate={handleNavigate}
            onLogoClick={handleHomeLogoClick}
            isTransitioning={isNavigating}
            isPageReady={isLoaded}
          />
        );
    }
  }, [heroRevealed, liveLoaderPhase, freezeStage, introDone, isMenuOpen, handleNavigate, handleHomeLogoClick, isNavigating]);

  return (
    <ThemeProvider>
      <LazyMotion features={domAnimation}>
        {/* 1. MAIN APPLICATION PAGE CONTENT */}
        <div style={{ position: 'relative', width: '100%', minHeight: '100vh' }}>
          <Suspense fallback={null}>
            {renderPageComponent(activePage)}
          </Suspense>
        </div>

        {/* 2. INTRO LOADER WITH SEAMLESS MONOGRAM Z-EXPANSION PORTAL REVEAL */}
        {(!introDone || freezeStage === 'loader' || freezeStage === 'logo' || freezeStage === 'expansion') && (
          <Loader 
            onComplete={handleLoaderComplete}
            onPhaseChange={setLiveLoaderPhase}
            onProgressUpdate={setLiveLoaderProgress}
            forcePhase={
              freezeStage === 'loader' 
                ? 'LOADING' 
                : (freezeStage === 'logo' 
                    ? 'SWEEPING' 
                    : (freezeStage === 'expansion' ? 'EXPANDING' : null))
            }
            forcedProgress={freezeStage === 'loader' ? forcedProgress : null}
            sweepProgress={freezeStage === 'logo' ? (forcedProgress !== null && forcedProgress !== undefined ? forcedProgress : 100) : null}
            expansionProgress={freezeStage === 'expansion' ? forcedProgress : null}
            speed={playbackSpeed}
          />
        )}

        {/* 3. REUSABLE STANDALONE PAGE TRANSITIONER (For inter-page transitions) */}
        {isNavigating && (
          <PageTransitioner
            isTransitioning={isNavigating}
            speed={playbackSpeed}
            outgoing={<Suspense fallback={null}>{renderPageComponent(activePage)}</Suspense>}
            incoming={<Suspense fallback={null}>{renderPageComponent(navTargetPage)}</Suspense>}
            onTransitionComplete={() => {
              setActivePage(navTargetPage || 'home');
              setIsNavigating(false);
              setNavTargetPage(null);
              window.scrollTo({ top: 0, behavior: 'auto' });
            }}
          />
        )}

        {/* 4. FULLSCREEN LUXURY NAVIGATION MENU MODAL */}
        <NavigationMenu
          isOpen={isMenuOpen}
          activePage={activePage}
          onClose={() => setIsMenuOpen(false)}
          onNavigate={handleNavigate}
        />

        {/* 5. 4-DIGIT SECURITY PASSCODE DRAWER MODAL */}
        <PasscodeModal
          isOpen={isPasscodeOpen}
          onClose={() => setIsPasscodeOpen(false)}
          onSuccess={() => {
            setIsPasscodeOpen(false);
            window.location.href = '/admin.html';
          }}
        />

        {/* 6. PENTORI RIGHT-SIDE MINIMALIST DRAGGABLE SCROLLBAR */}
        <CustomScrollbar 
          isVisible={introDone || freezeStage === 'home'} 
          isMenuOpen={isMenuOpen}
        />

        {/* 7. FLOATING DEVELOPER HUD GUI */}
        <DevFloatingHUD
          activeStage={currentActiveStage}
          activeSection={detectedSection || targetSection}
          progress={forcedProgress}
          masterPercent={computeMasterPercent()}
          isPlaying={isPlaying}
          hasPersistence={Boolean(freezeStage || targetSection)}
          speed={playbackSpeed}
          onFreezeLoader={handleFreezeLoader}
          onFreezeLogo={handleFreezeLogo}
          onFreezeExpansion={handleFreezeExpansion}
          onFreezeHome={handleFreezeHome}
          onMasterScrub={handleMasterScrub}
          onLoadSection={handleLoadSection}
          onSetSpeed={handleSetSpeed}
          onTogglePlay={handleTogglePlay}
          onResume={handleResume}
          onClear={handleClear}
        />
      </LazyMotion>
    </ThemeProvider>
  );
}

export default App;
