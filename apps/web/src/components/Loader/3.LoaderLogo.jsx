import React, { useRef } from 'react';
import { m } from 'framer-motion';
import { LOADER_THEME } from './loaderConfig';

// Exact vector geometry extracted from Resources/Monogram/SVG/1.Complete.svg
export const MONOGRAM_PATHS = {
  wingDown: "M43.82,70.3l64.52.05,5.42-12.76-32.78-.36,41.09-19.08,7.56-18.43,27.69.85-25.84,61.15c-2.63,6.23-11.43,8.64-18.18,8.65l-111.12.13,41.64-20.2Z",
  wingUp: "M129.63,19.71l-65.22.74-5.49,12.65,32.87.55-41.14,19.05-6.83,17.6-28.38-.02L41.22,9.13C43.67,3.32,52.08.55,58.48.55l110.96-.05-39.82,19.21Z",
  centerPoly: "44.55 69.8 51.04 53.07 93.98 33.19 59.67 32.61 64.74 20.95 128.88 20.22 121.68 37.77 78.77 57.7 113 58.08 108.01 69.85 44.55 69.8",
  centerWhite: "M128.13,20.73l-6.84,16.67-40.73,18.92-4,1.86,4.41.05,31.28.34-4.57,10.78-62.4-.05,6.16-15.86,40.77-18.88,3.96-1.83-4.36-.07-31.37-.53,4.64-10.68,63.06-.71M129.63,19.71l-65.22.74-5.49,12.65,32.87.55-41.14,19.05-6.83,17.6,64.52.05,5.42-12.76-32.78-.36,41.09-19.08,7.56-18.43h0Z"
};

/**
 * LoaderLogo - High-Performance Animated Vector Monogram
 * 
 * Choreography:
 *   1. Instant synchronous reveal upon merge (0ms delay).
 *   2. Fast 0.45s DrawSVG outline trace + luminous fill reveals.
 *   3. Zero GPU filter redraw overhead during expansion.
 */
export default function LoaderLogo({ 
  isVisible = true,
  isExpanding = false,
  expansionProgress = null,
  speed = 1 
}) {
  const logoContainerRef = useRef(null);
  const spd = speed || 1;
  const isScrubbingExpansion = expansionProgress !== null && expansionProgress !== undefined;

  // Scrub Calculations: 0.0 to 1.0
  const expRatio = isScrubbingExpansion 
    ? Math.min(1, Math.max(0, Number(expansionProgress) / 100)) 
    : 0;

  // Z Center Dark Poly Opacity: Fades out smoothly in the first 15% of expansion
  const scrubZPolyOpacity = isScrubbingExpansion
    ? Math.max(0, 1 - (expRatio / 0.15))
    : (isExpanding ? 0 : 1);

  return (
    <div
      ref={logoContainerRef}
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        width: '100%',
        height: '100%',
        userSelect: 'none',
        pointerEvents: 'none',
        position: 'relative',
        opacity: isVisible ? 1 : 0,
        transform: 'translateZ(0)',
        zIndex: 20
      }}
    >
      <m.div
        initial={{ opacity: 0, scale: 0.92, y: 10 }}
        animate={{
          opacity: isVisible ? 1 : 0,
          scale: isVisible ? 1 : 0.92,
          y: isVisible ? 0 : 10
        }}
        transition={{ duration: 0.45 / spd, ease: [0.16, 1, 0.3, 1] }}
        className="w-[84vw] max-w-[360px] max-h-[52vh] md:w-full md:max-w-[clamp(220px,34vw,480px)] md:max-h-[clamp(200px,38vh,460px)]"
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          position: 'relative',
          filter: isExpanding ? 'none' : 'drop-shadow(0 15px 35px rgba(0, 0, 0, 0.3))',
          willChange: 'transform, opacity'
        }}
      >
        <svg
          viewBox="0 0 171.63 91"
          className="w-full h-full max-h-[52vh] md:max-h-[clamp(200px,38vh,460px)]"
          style={{ overflow: 'visible' }}
        >
          {/* LINKED / GROUPED ENTIRE FZ MONOGRAM */}
          <g
            key="entire-fz-monogram-group"
            style={{
              transformOrigin: '85.8px 45.5px'
            }}
          >
            {/* LAYER 1: Solid Color Wings (F) */}
            <m.g 
              initial={{ opacity: 0, scale: 0.95 }}
              animate={isVisible ? { opacity: 1, scale: 1 } : { opacity: 0, scale: 0.95 }}
              transition={{ duration: 0.45 / spd, ease: "easeOut" }}
              style={{ transformOrigin: 'center center' }}
            >
              <path
                d={MONOGRAM_PATHS.wingDown}
                fill={LOADER_THEME.monogramWings}
                stroke="#000000"
                strokeWidth="0.8"
                strokeMiterlimit="10"
              />
              <path
                d={MONOGRAM_PATHS.wingUp}
                fill={LOADER_THEME.monogramWings}
                stroke="#000000"
                strokeWidth="0.8"
                strokeMiterlimit="10"
              />
            </m.g>

            {/* LAYER 2: Outlines Tracing */}
            <g>
              <m.path
                d={MONOGRAM_PATHS.wingDown}
                fill="none"
                stroke="#ffffff"
                strokeWidth="1.2"
                strokeLinecap="round"
                strokeLinejoin="round"
                initial={{ pathLength: 0 }}
                animate={isVisible ? { pathLength: 1 } : { pathLength: 0 }}
                transition={{ duration: 0.55 / spd, ease: "easeOut" }}
              />
              <m.path
                d={MONOGRAM_PATHS.wingUp}
                fill="none"
                stroke="#ffffff"
                strokeWidth="1.2"
                strokeLinecap="round"
                strokeLinejoin="round"
                initial={{ pathLength: 0 }}
                animate={isVisible ? { pathLength: 1 } : { pathLength: 0 }}
                transition={{ duration: 0.55 / spd, ease: "easeOut" }}
              />
            </g>

            {/* LAYER 3: Center Dark Polygon Core (Z) */}
            <m.polygon
              points={MONOGRAM_PATHS.centerPoly}
              fill={LOADER_THEME.monogramCore}
              initial={{ opacity: 0, scale: 0.95 }}
              animate={
                isScrubbingExpansion
                  ? { opacity: scrubZPolyOpacity, scale: 1 }
                  : (isExpanding 
                      ? { opacity: 0, scale: 1 } 
                      : (isVisible ? { opacity: 1, scale: 1 } : { opacity: 0, scale: 0.95 }))
              }
              transition={
                isScrubbingExpansion
                  ? { duration: 0 }
                  : (isExpanding 
                      ? { duration: 0.15 / spd, ease: 'easeOut' }
                      : { duration: 0.45 / spd, ease: 'easeOut' })
              }
              style={{ transformOrigin: 'center center', willChange: 'opacity, transform' }}
            />

            {/* LAYER 4: Center White Accent (Z) */}
            <m.path
              d={MONOGRAM_PATHS.centerWhite}
              fill={LOADER_THEME.monogramAccent}
              initial={{ opacity: 0 }}
              animate={
                isScrubbingExpansion
                  ? { opacity: scrubZPolyOpacity }
                  : (isExpanding 
                      ? { opacity: 0 } 
                      : (isVisible ? { opacity: 1 } : { opacity: 0 }))
              }
              transition={
                isScrubbingExpansion
                  ? { duration: 0 }
                  : (isExpanding 
                      ? { duration: 0.15 / spd, ease: 'easeOut' }
                      : { duration: 0.45 / spd, ease: "easeOut" })
              }
            />
          </g>
        </svg>
      </m.div>
    </div>
  );
}
