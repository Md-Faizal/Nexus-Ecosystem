import React from 'react';
import { m } from 'framer-motion';
import { LOADER_CONFIG } from './loaderConfig';

// Exact vector geometry extracted from Resources/Monogram/SVG/1.Complete.svg
const MONOGRAM_PATHS = {
  wingDown: "M43.82,70.3l64.52.05,5.42-12.76-32.78-.36,41.09-19.08,7.56-18.43,27.69.85-25.84,61.15c-2.63,6.23-11.43,8.64-18.18,8.65l-111.12.13,41.64-20.2Z",
  wingUp: "M129.63,19.71l-65.22.74-5.49,12.65,32.87.55-41.14,19.05-6.83,17.6-28.38-.02L41.22,9.13C43.67,3.32,52.08.55,58.48.55l110.96-.05-39.82,19.21Z",
  centerPoly: "44.55 69.8 51.04 53.07 93.98 33.19 59.67 32.61 64.74 20.95 128.88 20.22 121.68 37.77 78.77 57.7 113 58.08 108.01 69.85 44.55 69.8",
  centerWhite: "M128.13,20.73l-6.84,16.67-40.73,18.92-4,1.86,4.41.05,31.28.34-4.57,10.78-62.4-.05,6.16-15.86,40.77-18.88,3.96-1.83-4.36-.07-31.37-.53,4.64-10.68,63.06-.71M129.63,19.71l-65.22.74-5.49,12.65,32.87.55-41.14,19.05-6.83,17.6,64.52.05,5.42-12.76-32.78-.36,41.09-19.08,7.56-18.43h0Z"
};

/**
 * Micro Dot Matrix Grid for HUD corners
 */
function DotMatrix({ rows = 3, cols = 3 }) {
  const dots = [];
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      dots.push(
        <div
          key={`${r}-${c}`}
          style={{
            width: '2px',
            height: '2px',
            borderRadius: '50%',
            backgroundColor: '#55ff00',
            opacity: 0.45,
            boxShadow: '0 0 4px rgba(85, 255, 0, 0.6)'
          }}
        />
      );
    }
  }
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: `repeat(${cols}, 4px)`,
        gridGap: '4px',
        pointerEvents: 'none'
      }}
    >
      {dots}
    </div>
  );
}

/**
 * Corner Reticle Bracket with Sharp 45° Chamfer and Acute Pointy Needle Ends (No Glow)
 */
function CornerBracket({ type = 'tl' }) {
  let points = '';
  if (type === 'tl') {
    points = '0,38 3.5,29 3.5,10.5 10.5,3.5 29,3.5 38,0 8,0 0,8';
  } else if (type === 'tr') {
    points = '0,0 30,0 38,8 38,38 34.5,29 34.5,10.5 27.5,3.5 9,3.5';
  } else if (type === 'bl') {
    points = '0,0 3.5,9 3.5,27.5 10.5,34.5 29,34.5 38,38 8,38 0,30';
  } else if (type === 'br') {
    points = '38,0 34.5,9 34.5,27.5 27.5,34.5 9,34.5 0,38 30,38 38,30';
  }

  return (
    <svg
      width="38"
      height="38"
      viewBox="0 0 38 38"
      style={{
        overflow: 'visible'
      }}
    >
      <polygon
        points={points}
        fill="#55ff00"
      />
    </svg>
  );
}

/**
 * LoaderBackground - Pitch Black Cyber HUD Loader with Responsive Multi-Device Chevrons
 * High Performance Architecture: Dual-Mode SVG layout for 100% visible, razor-sharp chevrons on Mobile & Desktop.
 */
export default function LoaderBackground({ progress = 0, isTransitioning = false, speed = 1 }) {
  const spd = speed || 1;
  const p = Math.min(100, Math.max(0, Number(progress) || 0));
  const keepBg = (LOADER_CONFIG?.keepBackgroundActiveDuringSweep === 'Y' || LOADER_CONFIG?.keepBackgroundActiveDuringSweep === true);

  return (
    <m.div
      animate={
        keepBg
          ? { opacity: 1, scale: 1 }
          : {
              scale: isTransitioning ? 0.92 : 1,
              opacity: isTransitioning ? 0 : 1
            }
      }
      transition={{ duration: 0.75 / spd, ease: [0.16, 1, 0.3, 1] }}
      style={{
        position: 'absolute',
        inset: 0,
        backgroundColor: '#000000',
        overflow: 'hidden',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        userSelect: 'none',
        pointerEvents: 'none',
        transform: 'translateZ(0)',
        willChange: keepBg ? 'auto' : 'opacity, transform'
      }}
    >
      {/* 1. FOUR CORNER HUD RETICLES WITH CHAMFERED PATHS & DOT MATRICES */}
      <div
        style={{
          position: 'absolute',
          inset: 'clamp(12px, 2.5vw, 28px)',
          pointerEvents: 'none',
          zIndex: 20
        }}
      >
        {/* Top-Left Corner */}
        <div style={{ position: 'absolute', top: 0, left: 0, display: 'flex', gap: '8px' }}>
          <CornerBracket type="tl" />
          <div style={{ marginTop: '6px' }}>
            <DotMatrix rows={3} cols={3} />
          </div>
        </div>

        {/* Top-Right Corner */}
        <div style={{ position: 'absolute', top: 0, right: 0, display: 'flex', gap: '8px', flexDirection: 'row-reverse' }}>
          <CornerBracket type="tr" />
          <div style={{ marginTop: '6px' }}>
            <DotMatrix rows={3} cols={3} />
          </div>
        </div>

        {/* Bottom-Left Corner */}
        <div style={{ position: 'absolute', bottom: 0, left: 0, display: 'flex', gap: '8px', alignItems: 'flex-end' }}>
          <CornerBracket type="bl" />
          <div style={{ marginBottom: '6px' }}>
            <DotMatrix rows={3} cols={3} />
          </div>
        </div>

        {/* Bottom-Right Corner */}
        <div style={{ position: 'absolute', bottom: 0, right: 0, display: 'flex', gap: '8px', flexDirection: 'row-reverse', alignItems: 'flex-end' }}>
          <CornerBracket type="br" />
          <div style={{ marginBottom: '6px' }}>
            <DotMatrix rows={3} cols={3} />
          </div>
        </div>
      </div>

      {/* 2. RESPONSIVE LASER CHEVRONS (< >) - DUAL LAYOUT ENGINE */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 5,
          pointerEvents: 'none'
        }}
      >
        <style>{`
          /* Continuous, energetic aerodynamic pulse sweep (0.85s cycle with 0.14s stagger) */
          @keyframes pulseMiddleToBottom {
            0% {
              stroke-dashoffset: 180;
              opacity: 0;
            }
            8% {
              opacity: 1;
            }
            58% {
              stroke-dashoffset: -880;
              opacity: 1;
            }
            65% {
              opacity: 0;
              stroke-dashoffset: -880;
            }
            100% {
              opacity: 0;
              stroke-dashoffset: -880;
            }
          }

          /* Inward-to-Outward Loading Wave Glow for Facet Polygons */
          @keyframes loadingWaveFacet {
            0% {
              opacity: 0.45;
            }
            25% {
              opacity: 0.95;
            }
            50% {
              opacity: 0.45;
            }
            100% {
              opacity: 0.45;
            }
          }

          /* Inward-to-Outward Loading Wave for Neon Baseline Lines */
          @keyframes loadingWaveLine {
            0% {
              opacity: 0.75;
            }
            25% {
              opacity: 1;
            }
            50% {
              opacity: 0.75;
            }
            100% {
              opacity: 0.75;
            }
          }

          /* Aerodynamic Layering Dash Arrays */
          .aero-tail {
            stroke-dasharray: 230 950;
          }
          .aero-body {
            stroke-dasharray: 125 950;
          }
          .aero-head-bloom {
            stroke-dasharray: 55 950;
          }
          .aero-core {
            stroke-dasharray: 28 950;
          }

          /* Sequential Flow: Innermost (0s) -> Middle (0.14s) -> Outermost (0.28s) in 0.85s Cycle */
          .pulse-wave-inner {
            animation: pulseMiddleToBottom 0.85s cubic-bezier(0.22, 0.75, 0.3, 1) infinite;
            animation-delay: 0s;
          }
          .pulse-wave-mid {
            animation: pulseMiddleToBottom 0.85s cubic-bezier(0.22, 0.75, 0.3, 1) infinite;
            animation-delay: 0.14s;
          }
          .pulse-wave-outer {
            animation: pulseMiddleToBottom 0.85s cubic-bezier(0.22, 0.75, 0.3, 1) infinite;
            animation-delay: 0.28s;
          }

          /* Linked Inward-to-Outward Facet Glow Wave */
          .facet-wave-inner {
            animation: loadingWaveFacet 0.85s cubic-bezier(0.22, 0.75, 0.3, 1) infinite;
            animation-delay: 0s;
          }
          .facet-wave-mid {
            animation: loadingWaveFacet 0.85s cubic-bezier(0.22, 0.75, 0.3, 1) infinite;
            animation-delay: 0.14s;
          }
          .facet-wave-outer {
            animation: loadingWaveFacet 0.85s cubic-bezier(0.22, 0.75, 0.3, 1) infinite;
            animation-delay: 0.28s;
          }

          /* Linked Inward-to-Outward Line Glow Wave */
          .line-wave-inner {
            animation: loadingWaveLine 0.85s cubic-bezier(0.22, 0.75, 0.3, 1) infinite;
            animation-delay: 0s;
          }
          .line-wave-mid {
            animation: loadingWaveLine 0.85s cubic-bezier(0.22, 0.75, 0.3, 1) infinite;
            animation-delay: 0.14s;
          }
          .line-wave-outer {
            animation: loadingWaveLine 0.85s cubic-bezier(0.22, 0.75, 0.3, 1) infinite;
            animation-delay: 0.28s;
          }
          /* Responsive Dual-Mode SVG Display Engine */
          .loader-svg-desktop {
            display: none !important;
          }
          .loader-svg-mobile {
            display: block !important;
          }
          @media (min-width: 768px) {
            .loader-svg-desktop {
              display: block !important;
            }
            .loader-svg-mobile {
              display: none !important;
            }
          }
        `}</style>

        {/* ------------------------------------------------------------- */}
        {/* DESKTOP / TABLET SVG (viewBox 1920x1080) */}
        {/* ------------------------------------------------------------- */}
        <svg
          viewBox="0 0 1920 1080"
          preserveAspectRatio="xMidYMid slice"
          className="loader-svg-desktop"
          style={{
            width: '100%',
            height: '100%',
            overflow: 'visible'
          }}
        >
          <defs>
            <linearGradient id="facet-grad-left-1" x1="100%" y1="0%" x2="0%" y2="0%">
              <stop offset="0%" stopColor="#d9005b" stopOpacity="0.95" />
              <stop offset="35%" stopColor="#800033" stopOpacity="0.75" />
              <stop offset="70%" stopColor="#330014" stopOpacity="0.4" />
              <stop offset="100%" stopColor="#0a0004" stopOpacity="0.1" />
            </linearGradient>
            <linearGradient id="facet-grad-left-2" x1="100%" y1="0%" x2="0%" y2="0%">
              <stop offset="0%" stopColor="#b3004b" stopOpacity="0.9" />
              <stop offset="40%" stopColor="#5e0026" stopOpacity="0.65" />
              <stop offset="75%" stopColor="#1f000c" stopOpacity="0.3" />
              <stop offset="100%" stopColor="#000000" stopOpacity="0.05" />
            </linearGradient>
            <linearGradient id="facet-grad-left-3" x1="100%" y1="0%" x2="0%" y2="0%">
              <stop offset="0%" stopColor="#8c0038" stopOpacity="0.8" />
              <stop offset="45%" stopColor="#400019" stopOpacity="0.5" />
              <stop offset="80%" stopColor="#140008" stopOpacity="0.2" />
              <stop offset="100%" stopColor="#000000" stopOpacity="0.0" />
            </linearGradient>

            <linearGradient id="facet-grad-right-1" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor="#d9005b" stopOpacity="0.95" />
              <stop offset="35%" stopColor="#800033" stopOpacity="0.75" />
              <stop offset="70%" stopColor="#330014" stopOpacity="0.4" />
              <stop offset="100%" stopColor="#0a0004" stopOpacity="0.1" />
            </linearGradient>
            <linearGradient id="facet-grad-right-2" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor="#b3004b" stopOpacity="0.9" />
              <stop offset="40%" stopColor="#5e0026" stopOpacity="0.65" />
              <stop offset="75%" stopColor="#1f000c" stopOpacity="0.3" />
              <stop offset="100%" stopColor="#000000" stopOpacity="0.05" />
            </linearGradient>
            <linearGradient id="facet-grad-right-3" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor="#8c0038" stopOpacity="0.8" />
              <stop offset="45%" stopColor="#400019" stopOpacity="0.5" />
              <stop offset="80%" stopColor="#140008" stopOpacity="0.2" />
              <stop offset="100%" stopColor="#000000" stopOpacity="0.0" />
            </linearGradient>

            <filter id="laser-bloom-outer" x="-30%" y="-30%" width="160%" height="160%">
              <feGaussianBlur stdDeviation="8" result="blur1" />
              <feGaussianBlur stdDeviation="16" result="blur2" />
              <feMerge>
                <feMergeNode in="blur2" />
                <feMergeNode in="blur1" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>

            <filter id="laser-bloom-mid" x="-30%" y="-30%" width="160%" height="160%">
              <feGaussianBlur stdDeviation="5" result="blur1" />
              <feGaussianBlur stdDeviation="11" result="blur2" />
              <feMerge>
                <feMergeNode in="blur2" />
                <feMergeNode in="blur1" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
          </defs>

          {/* Left Chevrons (<) */}
          <g opacity={0.95}>
            <polygon points="340,0 -40,540 340,1080 0,1080 0,0" fill="url(#facet-grad-left-1)" className="facet-wave-outer" />
            <polyline points="340,0 -40,540 340,1080" fill="none" stroke="#ff0055" strokeWidth={3.0} className="line-wave-outer" strokeLinecap="round" strokeLinejoin="round" style={{ filter: 'drop-shadow(0 0 10px rgba(255, 0, 85, 0.95))' }} />
            <path d="M -40 540 L 340 0" fill="none" stroke="#ff0055" strokeWidth={1.2} className="pulse-wave-outer aero-tail" strokeLinecap="round" opacity={0.45} />
            <path d="M -40 540 L 340 0" fill="none" stroke="#ff1475" strokeWidth={3.4} className="pulse-wave-outer aero-body" strokeLinecap="round" />
            <path d="M -40 540 L 340 0" fill="none" stroke="#ff0055" strokeWidth={10} className="pulse-wave-outer aero-head-bloom" strokeLinecap="round" filter="url(#laser-bloom-outer)" />
            <path d="M -40 540 L 340 0" fill="none" stroke="#ffffff" strokeWidth={2.4} className="pulse-wave-outer aero-core" strokeLinecap="round" />
            <path d="M -40 540 L 340 1080" fill="none" stroke="#ff0055" strokeWidth={1.2} className="pulse-wave-outer aero-tail" strokeLinecap="round" opacity={0.45} />
            <path d="M -40 540 L 340 1080" fill="none" stroke="#ff1475" strokeWidth={3.4} className="pulse-wave-outer aero-body" strokeLinecap="round" />
            <path d="M -40 540 L 340 1080" fill="none" stroke="#ff0055" strokeWidth={10} className="pulse-wave-outer aero-head-bloom" strokeLinecap="round" filter="url(#laser-bloom-outer)" />
            <path d="M -40 540 L 340 1080" fill="none" stroke="#ffffff" strokeWidth={2.4} className="pulse-wave-outer aero-core" strokeLinecap="round" />
          </g>

          <g opacity={0.9}>
            <polygon points="540,0 160,540 540,1080 340,1080 -40,540 340,0" fill="url(#facet-grad-left-2)" className="facet-wave-mid" />
            <polyline points="540,0 160,540 540,1080" fill="none" stroke="#ff1475" strokeWidth={2.2} className="line-wave-mid" strokeLinecap="round" strokeLinejoin="round" style={{ filter: 'drop-shadow(0 0 8px rgba(255, 20, 117, 0.85))' }} />
            <path d="M 160 540 L 540 0" fill="none" stroke="#ff1475" strokeWidth={1.0} className="pulse-wave-mid aero-tail" strokeLinecap="round" opacity={0.4} />
            <path d="M 160 540 L 540 0" fill="none" stroke="#ff267e" strokeWidth={2.8} className="pulse-wave-mid aero-body" strokeLinecap="round" />
            <path d="M 160 540 L 540 0" fill="none" stroke="#ff1475" strokeWidth={8} className="pulse-wave-mid aero-head-bloom" strokeLinecap="round" filter="url(#laser-bloom-mid)" />
            <path d="M 160 540 L 540 0" fill="none" stroke="#ffffff" strokeWidth={1.8} className="pulse-wave-mid aero-core" strokeLinecap="round" />
            <path d="M 160 540 L 540 1080" fill="none" stroke="#ff1475" strokeWidth={1.0} className="pulse-wave-mid aero-tail" strokeLinecap="round" opacity={0.4} />
            <path d="M 160 540 L 540 1080" fill="none" stroke="#ff267e" strokeWidth={2.8} className="pulse-wave-mid aero-body" strokeLinecap="round" />
            <path d="M 160 540 L 540 1080" fill="none" stroke="#ff1475" strokeWidth={8} className="pulse-wave-mid aero-head-bloom" strokeLinecap="round" filter="url(#laser-bloom-mid)" />
            <path d="M 160 540 L 540 1080" fill="none" stroke="#ffffff" strokeWidth={1.8} className="pulse-wave-mid aero-core" strokeLinecap="round" />
          </g>

          <g opacity={0.85}>
            <polygon points="740,0 360,540 740,1080 540,1080 160,540 540,0" fill="url(#facet-grad-left-3)" className="facet-wave-inner" />
            <polyline points="740,0 360,540 740,1080" fill="none" stroke="#e61a6b" strokeWidth={1.6} className="line-wave-inner" strokeLinecap="round" strokeLinejoin="round" style={{ filter: 'drop-shadow(0 0 6px rgba(230, 26, 107, 0.75))' }} />
            <path d="M 360 540 L 740 0" fill="none" stroke="#e61a6b" strokeWidth={0.9} className="pulse-wave-inner aero-tail" strokeLinecap="round" opacity={0.35} />
            <path d="M 360 540 L 740 0" fill="none" stroke="#e61a6b" strokeWidth={2.2} className="pulse-wave-inner aero-body" strokeLinecap="round" />
            <path d="M 360 540 L 740 0" fill="none" stroke="#ffffff" strokeWidth={1.4} className="pulse-wave-inner aero-core" strokeLinecap="round" />
            <path d="M 360 540 L 740 1080" fill="none" stroke="#e61a6b" strokeWidth={0.9} className="pulse-wave-inner aero-tail" strokeLinecap="round" opacity={0.35} />
            <path d="M 360 540 L 740 1080" fill="none" stroke="#e61a6b" strokeWidth={2.2} className="pulse-wave-inner aero-body" strokeLinecap="round" />
            <path d="M 360 540 L 740 1080" fill="none" stroke="#ffffff" strokeWidth={1.4} className="pulse-wave-inner aero-core" strokeLinecap="round" />
          </g>

          {/* Right Chevrons (>) */}
          <g opacity={0.95}>
            <polygon points="1580,0 1960,540 1580,1080 1920,1080 1920,0" fill="url(#facet-grad-right-1)" className="facet-wave-outer" />
            <polyline points="1580,0 1960,540 1580,1080" fill="none" stroke="#ff0055" strokeWidth={3.0} className="line-wave-outer" strokeLinecap="round" strokeLinejoin="round" style={{ filter: 'drop-shadow(0 0 10px rgba(255, 0, 85, 0.95))' }} />
            <path d="M 1960 540 L 1580 0" fill="none" stroke="#ff0055" strokeWidth={1.2} className="pulse-wave-outer aero-tail" strokeLinecap="round" opacity={0.45} />
            <path d="M 1960 540 L 1580 0" fill="none" stroke="#ff1475" strokeWidth={3.4} className="pulse-wave-outer aero-body" strokeLinecap="round" />
            <path d="M 1960 540 L 1580 0" fill="none" stroke="#ff0055" strokeWidth={10} className="pulse-wave-outer aero-head-bloom" strokeLinecap="round" filter="url(#laser-bloom-outer)" />
            <path d="M 1960 540 L 1580 0" fill="none" stroke="#ffffff" strokeWidth={2.4} className="pulse-wave-outer aero-core" strokeLinecap="round" />
            <path d="M 1960 540 L 1580 1080" fill="none" stroke="#ff0055" strokeWidth={1.2} className="pulse-wave-outer aero-tail" strokeLinecap="round" opacity={0.45} />
            <path d="M 1960 540 L 1580 1080" fill="none" stroke="#ff1475" strokeWidth={3.4} className="pulse-wave-outer aero-body" strokeLinecap="round" />
            <path d="M 1960 540 L 1580 1080" fill="none" stroke="#ff0055" strokeWidth={10} className="pulse-wave-outer aero-head-bloom" strokeLinecap="round" filter="url(#laser-bloom-outer)" />
            <path d="M 1960 540 L 1580 1080" fill="none" stroke="#ffffff" strokeWidth={2.4} className="pulse-wave-outer aero-core" strokeLinecap="round" />
          </g>

          <g opacity={0.9}>
            <polygon points="1380,0 1760,540 1380,1080 1580,1080 1960,540 1580,0" fill="url(#facet-grad-right-2)" className="facet-wave-mid" />
            <polyline points="1380,0 1760,540 1380,1080" fill="none" stroke="#ff1475" strokeWidth={2.2} className="line-wave-mid" strokeLinecap="round" strokeLinejoin="round" style={{ filter: 'drop-shadow(0 0 8px rgba(255, 20, 117, 0.85))' }} />
            <path d="M 1760 540 L 1380 0" fill="none" stroke="#ff1475" strokeWidth={1.0} className="pulse-wave-mid aero-tail" strokeLinecap="round" opacity={0.4} />
            <path d="M 1760 540 L 1380 0" fill="none" stroke="#ff267e" strokeWidth={2.8} className="pulse-wave-mid aero-body" strokeLinecap="round" />
            <path d="M 1760 540 L 1380 0" fill="none" stroke="#ff1475" strokeWidth={8} className="pulse-wave-mid aero-head-bloom" strokeLinecap="round" filter="url(#laser-bloom-mid)" />
            <path d="M 1760 540 L 1380 0" fill="none" stroke="#ffffff" strokeWidth={1.8} className="pulse-wave-mid aero-core" strokeLinecap="round" />
            <path d="M 1760 540 L 1380 1080" fill="none" stroke="#ff1475" strokeWidth={1.0} className="pulse-wave-mid aero-tail" strokeLinecap="round" opacity={0.4} />
            <path d="M 1760 540 L 1380 1080" fill="none" stroke="#ff267e" strokeWidth={2.8} className="pulse-wave-mid aero-body" strokeLinecap="round" />
            <path d="M 1760 540 L 1380 1080" fill="none" stroke="#ff1475" strokeWidth={8} className="pulse-wave-mid aero-head-bloom" strokeLinecap="round" filter="url(#laser-bloom-mid)" />
            <path d="M 1760 540 L 1380 1080" fill="none" stroke="#ffffff" strokeWidth={1.8} className="pulse-wave-mid aero-core" strokeLinecap="round" />
          </g>

          <g opacity={0.85}>
            <polygon points="1180,0 1560,540 1180,1080 1380,1080 1760,540 1380,0" fill="url(#facet-grad-right-3)" className="facet-wave-inner" />
            <polyline points="1180,0 1560,540 1180,1080" fill="none" stroke="#e61a6b" strokeWidth={1.6} className="line-wave-inner" strokeLinecap="round" strokeLinejoin="round" style={{ filter: 'drop-shadow(0 0 6px rgba(230, 26, 107, 0.75))' }} />
            <path d="M 1560 540 L 1180 0" fill="none" stroke="#e61a6b" strokeWidth={0.9} className="pulse-wave-inner aero-tail" strokeLinecap="round" opacity={0.35} />
            <path d="M 1560 540 L 1180 0" fill="none" stroke="#e61a6b" strokeWidth={2.2} className="pulse-wave-inner aero-body" strokeLinecap="round" />
            <path d="M 1560 540 L 1180 0" fill="none" stroke="#ffffff" strokeWidth={1.4} className="pulse-wave-inner aero-core" strokeLinecap="round" />
            <path d="M 1560 540 L 1180 1080" fill="none" stroke="#e61a6b" strokeWidth={0.9} className="pulse-wave-inner aero-tail" strokeLinecap="round" opacity={0.35} />
            <path d="M 1560 540 L 1180 1080" fill="none" stroke="#e61a6b" strokeWidth={2.2} className="pulse-wave-inner aero-body" strokeLinecap="round" />
            <path d="M 1560 540 L 1180 1080" fill="none" stroke="#ffffff" strokeWidth={1.4} className="pulse-wave-inner aero-core" strokeLinecap="round" />
          </g>
        </svg>

        {/* ------------------------------------------------------------- */}
        {/* MOBILE PORTRAIT SVG (viewBox 750x1334) - 100% FRAMED CHEVRONS */}
        {/* ------------------------------------------------------------- */}
        <svg
          viewBox="0 0 750 1334"
          preserveAspectRatio="xMidYMid slice"
          className="loader-svg-mobile"
          style={{
            width: '100%',
            height: '100%',
            overflow: 'visible'
          }}
        >
          <defs>
            <linearGradient id="mob-facet-grad-left-1" x1="100%" y1="0%" x2="0%" y2="0%">
              <stop offset="0%" stopColor="#d9005b" stopOpacity="0.95" />
              <stop offset="35%" stopColor="#800033" stopOpacity="0.75" />
              <stop offset="70%" stopColor="#330014" stopOpacity="0.4" />
              <stop offset="100%" stopColor="#0a0004" stopOpacity="0.1" />
            </linearGradient>
            <linearGradient id="mob-facet-grad-left-2" x1="100%" y1="0%" x2="0%" y2="0%">
              <stop offset="0%" stopColor="#b3004b" stopOpacity="0.9" />
              <stop offset="40%" stopColor="#5e0026" stopOpacity="0.65" />
              <stop offset="75%" stopColor="#1f000c" stopOpacity="0.3" />
              <stop offset="100%" stopColor="#000000" stopOpacity="0.05" />
            </linearGradient>
            <linearGradient id="mob-facet-grad-left-3" x1="100%" y1="0%" x2="0%" y2="0%">
              <stop offset="0%" stopColor="#8c0038" stopOpacity="0.8" />
              <stop offset="45%" stopColor="#400019" stopOpacity="0.5" />
              <stop offset="80%" stopColor="#140008" stopOpacity="0.2" />
              <stop offset="100%" stopColor="#000000" stopOpacity="0.0" />
            </linearGradient>

            <linearGradient id="mob-facet-grad-right-1" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor="#d9005b" stopOpacity="0.95" />
              <stop offset="35%" stopColor="#800033" stopOpacity="0.75" />
              <stop offset="70%" stopColor="#330014" stopOpacity="0.4" />
              <stop offset="100%" stopColor="#0a0004" stopOpacity="0.1" />
            </linearGradient>
            <linearGradient id="mob-facet-grad-right-2" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor="#b3004b" stopOpacity="0.9" />
              <stop offset="40%" stopColor="#5e0026" stopOpacity="0.65" />
              <stop offset="75%" stopColor="#1f000c" stopOpacity="0.3" />
              <stop offset="100%" stopColor="#000000" stopOpacity="0.05" />
            </linearGradient>
            <linearGradient id="mob-facet-grad-right-3" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor="#8c0038" stopOpacity="0.8" />
              <stop offset="45%" stopColor="#400019" stopOpacity="0.5" />
              <stop offset="80%" stopColor="#140008" stopOpacity="0.2" />
              <stop offset="100%" stopColor="#000000" stopOpacity="0.0" />
            </linearGradient>
          </defs>

          {/* Mobile Left Chevrons (<) */}
          <g opacity={0.95}>
            <polygon points="160,0 20,667 160,1334 0,1334 0,0" fill="url(#mob-facet-grad-left-1)" className="facet-wave-outer" />
            <polyline points="160,0 20,667 160,1334" fill="none" stroke="#ff0055" strokeWidth={3.0} className="line-wave-outer" strokeLinecap="round" strokeLinejoin="round" style={{ filter: 'drop-shadow(0 0 10px rgba(255, 0, 85, 0.95))' }} />
            <path d="M 20 667 L 160 0" fill="none" stroke="#ff0055" strokeWidth={1.2} className="pulse-wave-outer aero-tail" strokeLinecap="round" opacity={0.45} />
            <path d="M 20 667 L 160 0" fill="none" stroke="#ff1475" strokeWidth={3.4} className="pulse-wave-outer aero-body" strokeLinecap="round" />
            <path d="M 20 667 L 160 0" fill="none" stroke="#ff0055" strokeWidth={10} className="pulse-wave-outer aero-head-bloom" strokeLinecap="round" filter="url(#laser-bloom-outer)" />
            <path d="M 20 667 L 160 0" fill="none" stroke="#ffffff" strokeWidth={2.4} className="pulse-wave-outer aero-core" strokeLinecap="round" />
            <path d="M 20 667 L 160 1334" fill="none" stroke="#ff0055" strokeWidth={1.2} className="pulse-wave-outer aero-tail" strokeLinecap="round" opacity={0.45} />
            <path d="M 20 667 L 160 1334" fill="none" stroke="#ff1475" strokeWidth={3.4} className="pulse-wave-outer aero-body" strokeLinecap="round" />
            <path d="M 20 667 L 160 1334" fill="none" stroke="#ff0055" strokeWidth={10} className="pulse-wave-outer aero-head-bloom" strokeLinecap="round" filter="url(#laser-bloom-outer)" />
            <path d="M 20 667 L 160 1334" fill="none" stroke="#ffffff" strokeWidth={2.4} className="pulse-wave-outer aero-core" strokeLinecap="round" />
          </g>

          <g opacity={0.9}>
            <polygon points="230,0 80,667 230,1334 160,1334 20,667 160,0" fill="url(#mob-facet-grad-left-2)" className="facet-wave-mid" />
            <polyline points="230,0 80,667 230,1334" fill="none" stroke="#ff1475" strokeWidth={2.2} className="line-wave-mid" strokeLinecap="round" strokeLinejoin="round" style={{ filter: 'drop-shadow(0 0 8px rgba(255, 20, 117, 0.85))' }} />
            <path d="M 80 667 L 230 0" fill="none" stroke="#ff1475" strokeWidth={1.0} className="pulse-wave-mid aero-tail" strokeLinecap="round" opacity={0.4} />
            <path d="M 80 667 L 230 0" fill="none" stroke="#ff267e" strokeWidth={2.8} className="pulse-wave-mid aero-body" strokeLinecap="round" />
            <path d="M 80 667 L 230 0" fill="none" stroke="#ff1475" strokeWidth={8} className="pulse-wave-mid aero-head-bloom" strokeLinecap="round" filter="url(#laser-bloom-mid)" />
            <path d="M 80 667 L 230 0" fill="none" stroke="#ffffff" strokeWidth={1.8} className="pulse-wave-mid aero-core" strokeLinecap="round" />
            <path d="M 80 667 L 230 1334" fill="none" stroke="#ff1475" strokeWidth={1.0} className="pulse-wave-mid aero-tail" strokeLinecap="round" opacity={0.4} />
            <path d="M 80 667 L 230 1334" fill="none" stroke="#ff267e" strokeWidth={2.8} className="pulse-wave-mid aero-body" strokeLinecap="round" />
            <path d="M 80 667 L 230 1334" fill="none" stroke="#ff1475" strokeWidth={8} className="pulse-wave-mid aero-head-bloom" strokeLinecap="round" filter="url(#laser-bloom-mid)" />
            <path d="M 80 667 L 230 1334" fill="none" stroke="#ffffff" strokeWidth={1.8} className="pulse-wave-mid aero-core" strokeLinecap="round" />
          </g>

          <g opacity={0.85}>
            <polygon points="300,0 140,667 300,1334 230,1334 80,667 230,0" fill="url(#mob-facet-grad-left-3)" className="facet-wave-inner" />
            <polyline points="300,0 140,667 300,1334" fill="none" stroke="#e61a6b" strokeWidth={1.6} className="line-wave-inner" strokeLinecap="round" strokeLinejoin="round" style={{ filter: 'drop-shadow(0 0 6px rgba(230, 26, 107, 0.75))' }} />
            <path d="M 140 667 L 300 0" fill="none" stroke="#e61a6b" strokeWidth={0.9} className="pulse-wave-inner aero-tail" strokeLinecap="round" opacity={0.35} />
            <path d="M 140 667 L 300 0" fill="none" stroke="#e61a6b" strokeWidth={2.2} className="pulse-wave-inner aero-body" strokeLinecap="round" />
            <path d="M 140 667 L 300 0" fill="none" stroke="#ffffff" strokeWidth={1.4} className="pulse-wave-inner aero-core" strokeLinecap="round" />
            <path d="M 140 667 L 300 1334" fill="none" stroke="#e61a6b" strokeWidth={0.9} className="pulse-wave-inner aero-tail" strokeLinecap="round" opacity={0.35} />
            <path d="M 140 667 L 300 1334" fill="none" stroke="#e61a6b" strokeWidth={2.2} className="pulse-wave-inner aero-body" strokeLinecap="round" />
            <path d="M 140 667 L 300 1334" fill="none" stroke="#ffffff" strokeWidth={1.4} className="pulse-wave-inner aero-core" strokeLinecap="round" />
          </g>

          {/* Mobile Right Chevrons (>) */}
          <g opacity={0.95}>
            <polygon points="590,0 730,667 590,1334 750,1334 750,0" fill="url(#mob-facet-grad-right-1)" className="facet-wave-outer" />
            <polyline points="590,0 730,667 590,1334" fill="none" stroke="#ff0055" strokeWidth={3.0} className="line-wave-outer" strokeLinecap="round" strokeLinejoin="round" style={{ filter: 'drop-shadow(0 0 10px rgba(255, 0, 85, 0.95))' }} />
            <path d="M 730 667 L 590 0" fill="none" stroke="#ff0055" strokeWidth={1.2} className="pulse-wave-outer aero-tail" strokeLinecap="round" opacity={0.45} />
            <path d="M 730 667 L 590 0" fill="none" stroke="#ff1475" strokeWidth={3.4} className="pulse-wave-outer aero-body" strokeLinecap="round" />
            <path d="M 730 667 L 590 0" fill="none" stroke="#ff0055" strokeWidth={10} className="pulse-wave-outer aero-head-bloom" strokeLinecap="round" filter="url(#laser-bloom-outer)" />
            <path d="M 730 667 L 590 0" fill="none" stroke="#ffffff" strokeWidth={2.4} className="pulse-wave-outer aero-core" strokeLinecap="round" />
            <path d="M 730 667 L 590 1334" fill="none" stroke="#ff0055" strokeWidth={1.2} className="pulse-wave-outer aero-tail" strokeLinecap="round" opacity={0.45} />
            <path d="M 730 667 L 590 1334" fill="none" stroke="#ff1475" strokeWidth={3.4} className="pulse-wave-outer aero-body" strokeLinecap="round" />
            <path d="M 730 667 L 590 1334" fill="none" stroke="#ff0055" strokeWidth={10} className="pulse-wave-outer aero-head-bloom" strokeLinecap="round" filter="url(#laser-bloom-outer)" />
            <path d="M 730 667 L 590 1334" fill="none" stroke="#ffffff" strokeWidth={2.4} className="pulse-wave-outer aero-core" strokeLinecap="round" />
          </g>

          <g opacity={0.9}>
            <polygon points="520,0 670,667 520,1334 590,1334 730,667 590,0" fill="url(#mob-facet-grad-right-2)" className="facet-wave-mid" />
            <polyline points="520,0 670,667 520,1334" fill="none" stroke="#ff1475" strokeWidth={2.2} className="line-wave-mid" strokeLinecap="round" strokeLinejoin="round" style={{ filter: 'drop-shadow(0 0 8px rgba(255, 20, 117, 0.85))' }} />
            <path d="M 670 667 L 520 0" fill="none" stroke="#ff1475" strokeWidth={1.0} className="pulse-wave-mid aero-tail" strokeLinecap="round" opacity={0.4} />
            <path d="M 670 667 L 520 0" fill="none" stroke="#ff267e" strokeWidth={2.8} className="pulse-wave-mid aero-body" strokeLinecap="round" />
            <path d="M 670 667 L 520 0" fill="none" stroke="#ff1475" strokeWidth={8} className="pulse-wave-mid aero-head-bloom" strokeLinecap="round" filter="url(#laser-bloom-mid)" />
            <path d="M 670 667 L 520 0" fill="none" stroke="#ffffff" strokeWidth={1.8} className="pulse-wave-mid aero-core" strokeLinecap="round" />
            <path d="M 670 667 L 520 1334" fill="none" stroke="#ff1475" strokeWidth={1.0} className="pulse-wave-mid aero-tail" strokeLinecap="round" opacity={0.4} />
            <path d="M 670 667 L 520 1334" fill="none" stroke="#ff267e" strokeWidth={2.8} className="pulse-wave-mid aero-body" strokeLinecap="round" />
            <path d="M 670 667 L 520 1334" fill="none" stroke="#ff1475" strokeWidth={8} className="pulse-wave-mid aero-head-bloom" strokeLinecap="round" filter="url(#laser-bloom-mid)" />
            <path d="M 670 667 L 520 1334" fill="none" stroke="#ffffff" strokeWidth={1.8} className="pulse-wave-mid aero-core" strokeLinecap="round" />
          </g>

          <g opacity={0.85}>
            <polygon points="450,0 610,667 450,1334 520,1334 670,667 520,0" fill="url(#mob-facet-grad-right-3)" className="facet-wave-inner" />
            <polyline points="450,0 610,667 450,1334" fill="none" stroke="#e61a6b" strokeWidth={1.6} className="line-wave-inner" strokeLinecap="round" strokeLinejoin="round" style={{ filter: 'drop-shadow(0 0 6px rgba(230, 26, 107, 0.75))' }} />
            <path d="M 610 667 L 450 0" fill="none" stroke="#e61a6b" strokeWidth={0.9} className="pulse-wave-inner aero-tail" strokeLinecap="round" opacity={0.35} />
            <path d="M 610 667 L 450 0" fill="none" stroke="#e61a6b" strokeWidth={2.2} className="pulse-wave-inner aero-body" strokeLinecap="round" />
            <path d="M 610 667 L 450 0" fill="none" stroke="#ffffff" strokeWidth={1.4} className="pulse-wave-inner aero-core" strokeLinecap="round" />
            <path d="M 610 667 L 450 1334" fill="none" stroke="#e61a6b" strokeWidth={0.9} className="pulse-wave-inner aero-tail" strokeLinecap="round" opacity={0.35} />
            <path d="M 610 667 L 450 1334" fill="none" stroke="#e61a6b" strokeWidth={2.2} className="pulse-wave-inner aero-body" strokeLinecap="round" />
            <path d="M 610 667 L 450 1334" fill="none" stroke="#ffffff" strokeWidth={1.4} className="pulse-wave-inner aero-core" strokeLinecap="round" />
          </g>
        </svg>
      </div>

      {/* 3. CENTER HERO STACK (PIXEL-PERFECT STACKING MATCHING HTML SHELL) */}
      <m.div
        animate={{
          opacity: isTransitioning ? 0 : 1,
          scale: isTransitioning ? 0.94 : 1
        }}
        transition={{ duration: 0.45 / spd, ease: [0.16, 1, 0.3, 1] }}
        style={{
          position: 'relative',
          zIndex: 30,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          width: '100%',
          maxWidth: '560px',
          padding: '0 1rem',
          boxSizing: 'border-box'
        }}
      >
        {/* Top Stylized Neon Green FZ Monogram Emblem */}
        <div
          style={{
            width: 'clamp(118px, 20vw, 198px)',
            height: 'clamp(62px, 10.6vw, 106px)',
            marginBottom: 'clamp(1.8rem, 5vh, 4.2rem)'
          }}
        >
          <svg viewBox="0 0 171.63 91" className="w-full h-full" style={{ overflow: 'visible' }}>
            <path
              d={MONOGRAM_PATHS.wingDown}
              fill="#55ff00"
              stroke="#000000"
              strokeWidth="0.8"
              strokeMiterlimit="10"
            />
            <path
              d={MONOGRAM_PATHS.wingUp}
              fill="#55ff00"
              stroke="#000000"
              strokeWidth="0.8"
              strokeMiterlimit="10"
            />
            <polygon points={MONOGRAM_PATHS.centerPoly} fill="#000000" />
            <path d={MONOGRAM_PATHS.centerWhite} fill="#ffffff" />
          </svg>
        </div>

        {/* Large Pure White Chakra Petch Percentage Counter */}
        <div
          style={{
            fontFamily: '"Chakra Petch", sans-serif',
            fontWeight: 400,
            fontSize: 'clamp(3.6rem, 10vw, 7.2rem)',
            color: '#ffffff',
            lineHeight: 0.95,
            letterSpacing: '0.02em',
            textAlign: 'center',
            textShadow: '0 0 25px rgba(255, 255, 255, 0.35)',
            willChange: 'transform'
          }}
        >
          {p}%
        </div>

        {/* Sleek Horizontal Glowing Progress Bar with Smooth Jitter-Free Stream */}
        <div
          style={{
            width: 'clamp(220px, 64vw, 360px)',
            height: '3px',
            background: 'rgba(255, 255, 255, 0.12)',
            border: '1px solid rgba(85, 255, 0, 0.18)',
            borderRadius: '2px',
            marginTop: 'clamp(1.2rem, 2.8vh, 1.8rem)',
            position: 'relative',
            boxShadow: 'inset 0 0 6px rgba(0, 0, 0, 0.8)',
            boxSizing: 'content-box',
            display: 'flex',
            alignItems: 'center'
          }}
        >
          {/* Progress Fill Bar */}
          <div
            style={{
              height: '100%',
              width: `${p}%`,
              background: 'linear-gradient(90deg, #39ff14 0%, #55ff00 100%)',
              borderRadius: '2px',
              boxShadow: '0 0 10px #55ff00, 0 0 20px rgba(85, 255, 0, 0.6)'
            }}
          />

          {/* Traveling Luminous Lens Flare Hotspot (Locks at marker head p%) */}
          {p > 0 && (
            <div
              style={{
                position: 'absolute',
                top: '50%',
                left: `${p}%`,
                transform: 'translate(-50%, -50%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                pointerEvents: 'none',
                willChange: 'left',
                zIndex: 10
              }}
            >
              {/* Horizontal Flare Ray */}
              <div
                style={{
                  position: 'absolute',
                  width: '64px',
                  height: '2px',
                  background: 'linear-gradient(90deg, transparent 0%, rgba(85, 255, 0, 0.7) 35%, #ffffff 50%, rgba(85, 255, 0, 0.7) 65%, transparent 100%)',
                  filter: 'drop-shadow(0 0 6px #55ff00)'
                }}
              />
              {/* Intense Glowing Core */}
              <div
                style={{
                  width: '7px',
                  height: '7px',
                  borderRadius: '50%',
                  backgroundColor: '#ffffff',
                  boxShadow: '0 0 10px 2px #55ff00, 0 0 22px 6px rgba(85, 255, 0, 0.9), 0 0 38px 10px rgba(85, 255, 0, 0.4)'
                }}
              />
            </div>
          )}
        </div>

        {/* Wide-Spaced Share Tech Mono Subtitle */}
        <div
          style={{
            fontFamily: '"Share Tech Mono", monospace',
            fontWeight: 500,
            fontSize: 'clamp(0.78rem, 2.0vw, 1.25rem)',
            letterSpacing: 'clamp(0.28em, 0.9vw, 0.42em)',
            color: '#55ff00',
            textTransform: 'uppercase',
            marginTop: 'clamp(1.2rem, 2.8vh, 1.9rem)',
            textAlign: 'center',
            whiteSpace: 'nowrap',
            textShadow: '0 0 12px #55ff00, 0 0 24px rgba(85, 255, 0, 0.7), 0 0 36px rgba(85, 255, 0, 0.35)'
          }}
        >
          LOADING EXPERIENCE...
        </div>
      </m.div>
    </m.div>
  );
}
