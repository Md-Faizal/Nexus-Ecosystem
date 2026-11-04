import React, { useState, useEffect, useRef } from 'react';
import LoaderBackground from './1.LoaderBackground';
import LoaderSweep from './2.LoaderSweep';
import LoaderLogo from './3.LoaderLogo';
import ZPortalTransition from './4.ZPortalTransition';
import { trackPreloading } from '../../utils/assetPreloader';

/**
 * Loader - Site Intro Sequence Orchestrator:
 *   Refactored for 60FPS Performance with full Dev HUD Stage & Sub-Scrubbing suite.
 */
export default function Loader({ 
  onComplete, 
  onPhaseChange,
  onProgressUpdate,
  forcePhase, 
  forcedProgress,
  sweepProgress = null,
  expansionProgress = null,
  speed = 1
}) {
  const [phase, setPhase] = useState(forcePhase || 'LOADING'); // 'LOADING' | 'SWEEPING' | 'LOGO_VISIBLE' | 'EXPANDING' | 'DONE'
  const [progress, setProgress] = useState(
    forcedProgress !== null && forcedProgress !== undefined 
      ? forcedProgress 
      : (typeof window !== 'undefined' && window.__SHELL_PROGRESS__ ? window.__SHELL_PROGRESS__ : 0)
  );

  const spd = speed || 1;
  const isScrubbingExp = expansionProgress !== null && expansionProgress !== undefined;
  const isScrubbingSweep = sweepProgress !== null && sweepProgress !== undefined;

  const [solidMerged, setSolidMerged] = useState(forcePhase === 'LOGO_VISIBLE' || forcePhase === 'EXPANDING');
  const [showMonogram, setShowMonogram] = useState(forcePhase === 'LOGO_VISIBLE' || forcePhase === 'EXPANDING');
  const [isExpanding, setIsExpanding] = useState(forcePhase === 'EXPANDING');

  useEffect(() => {
    onPhaseChange?.(phase);
  }, [phase, onPhaseChange]);

  useEffect(() => {
    onProgressUpdate?.(progress);
  }, [progress, onProgressUpdate]);

  useEffect(() => {
    if (forcePhase) {
      setPhase(forcePhase);
      setSolidMerged(forcePhase === 'LOGO_VISIBLE' || forcePhase === 'EXPANDING');
      setShowMonogram(forcePhase === 'LOGO_VISIBLE' || forcePhase === 'EXPANDING');
      setIsExpanding(forcePhase === 'EXPANDING');
    }
  }, [forcePhase]);

  useEffect(() => {
    if (forcedProgress !== null && forcedProgress !== undefined) {
      setProgress(forcedProgress);
    }
  }, [forcedProgress]);

  const hasTriggeredSweep = useRef(false);
  useEffect(() => {
    if (forcePhase || (forcedProgress !== null && forcedProgress !== undefined) || isScrubbingExp || isScrubbingSweep) return;

    hasTriggeredSweep.current = false;
    const cancelTracking = trackPreloading({
      minDuration: 1400 / spd,
      maxTimeout: 3000,
      onProgress: (p) => {
        setProgress(p);
        if (p >= 100 && !hasTriggeredSweep.current) {
          hasTriggeredSweep.current = true;
          setTimeout(() => {
            setPhase('SWEEPING');
          }, 40 / spd);
        }
      }
    });

    return () => {
      cancelTracking();
    };
  }, [forcePhase, forcedProgress, isScrubbingExp, isScrubbingSweep, spd]);

  useEffect(() => {
    if (forcePhase || isScrubbingExp || isScrubbingSweep) return;

    if (phase === 'SWEEPING') {
      // Paced Cinematic Choreography:
      // Stage 1 (0ms - 650ms): 5 distinct lines clearly sweep across in alternating directions
      // Stage 2 (650ms - 1050ms): Bars 2 & 4 merge into Bar 3 (5 -> 3 panels)
      // At 1050ms: Monogram vector trace begins INSIDE the expanding center panel
      const monogramTimer = setTimeout(() => {
        setShowMonogram(true);
      }, 1050 / spd);

      // Stage 3 (1050ms - 1450ms): Outer bars merge into full solid card
      // At 1450ms: Solid card locks in and outer white glowing border illuminates
      const solidTimer = setTimeout(() => {
        setSolidMerged(true);
      }, 1450 / spd);

      // At 1600ms (crisp 150ms hero dwell after solid card and logo completion):
      // Smoothly launch into Z-expansion punch ONLY when Part 2 (Home DOM) is 100% ready
      let pollTimer = null;
      const expandTimer = setTimeout(() => {
        const isPart2Ready = () => {
          if (typeof document === 'undefined') return true;
          return Boolean((window.__HOME_PART2_READY__ || window.__HOME_DOM_READY__) && document.getElementById('section-hero'));
        };

        const launchExpansion = () => {
          setPhase('EXPANDING');
          setIsExpanding(true);
        };

        if (isPart2Ready()) {
          launchExpansion();
        } else {
          // Listen for Part 2 completion event or poll until Home DOM is confirmed
          const onPart2Done = () => {
            window.removeEventListener('home-part2-ready', onPart2Done);
            if (pollTimer) clearInterval(pollTimer);
            launchExpansion();
          };
          window.addEventListener('home-part2-ready', onPart2Done);

          pollTimer = setInterval(() => {
            if (isPart2Ready()) {
              window.removeEventListener('home-part2-ready', onPart2Done);
              clearInterval(pollTimer);
              launchExpansion();
            }
          }, 20);
        }
      }, 1500 / spd);

      return () => {
        clearTimeout(monogramTimer);
        clearTimeout(solidTimer);
        clearTimeout(expandTimer);
        if (pollTimer) clearInterval(pollTimer);
      };
    }
  }, [phase, forcePhase, isScrubbingExp, isScrubbingSweep, spd]);

  const handleComplete = () => {
    setPhase('DONE');
    onComplete?.();
  };

  const isTransitioning = phase !== 'LOADING' || isScrubbingSweep;

  // Instant unmount for payload optimization when done
  if (phase === 'DONE' && !forcePhase && !isScrubbingExp && !isScrubbingSweep) {
    return null;
  }

  return (
    <ZPortalTransition
      phase={phase}
      expansionProgress={expansionProgress}
      speed={spd}
      onComplete={handleComplete}
      backgroundContent={
        <LoaderBackground 
          progress={progress} 
          isTransitioning={isTransitioning} 
          speed={spd} 
        />
      }
      sweepContent={
        <LoaderSweep 
          solidMerged={solidMerged} 
          isTransitioning={isTransitioning} 
          forcePhase={forcePhase} 
          showMonogram={showMonogram} 
          phase={phase}
          sweepProgress={sweepProgress}
          speed={spd} 
        />
      }
      logoContent={
        (showMonogram || phase === 'LOGO_VISIBLE' || phase === 'EXPANDING' || (isScrubbingSweep && Number(sweepProgress) >= 90) || isScrubbingExp) ? (
          <LoaderLogo 
            isVisible={showMonogram || phase === 'LOGO_VISIBLE' || phase === 'EXPANDING' || (isScrubbingSweep && Number(sweepProgress) >= 90)}
            isExpanding={isExpanding || phase === 'EXPANDING'}
            expansionProgress={expansionProgress}
            speed={spd}
          />
        ) : null
      }
    />
  );
}
