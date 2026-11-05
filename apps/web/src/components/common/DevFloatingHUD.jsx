import React, { useState, useEffect, useRef } from 'react';
import { m as motion } from 'framer-motion';

/**
 * DevFloatingHUD - Industrial-Standard Developer Control & Animation Suite
 * 
 * Features:
 *   1. Zero-Jitter Dual-Way Timeline Scrubbing (Forward & Reverse with zero race conditions)
 *   2. Isolated Performance & FPS Telemetry (Zero re-render overhead)
 *   3. Stage-Locked Presets and Sub-Scrubbers
 *   4. Live Section Observer with Smooth Jump Navigation
 *   5. Playback Speed Multipliers (0.1x to 2.0x)
 *   6. Draggable, Minimized, and Keyboard-Accessible (`~`, `Shift+1..4`, `Shift+Space`)
 */
const HUD_STORAGE_KEY = 'PI_DEV_HUD_VISIBLE';

// Isolated Telemetry Badge to prevent HUD re-render cascades
function FpsTelemetryBadge() {
  const [metrics, setMetrics] = useState({ fps: 60, ms: 16.6 });
  const frameCountRef = useRef(0);
  const lastTimeRef = useRef(performance.now());
  const rafRef = useRef(null);

  useEffect(() => {
    const loop = (now) => {
      frameCountRef.current++;
      const elapsed = now - lastTimeRef.current;
      if (elapsed >= 800) {
        const currentFps = Math.round((frameCountRef.current / elapsed) * 1000);
        const clampedFps = Math.min(120, Math.max(1, currentFps));
        const ms = parseFloat((1000 / clampedFps).toFixed(1));
        setMetrics({ fps: clampedFps, ms });
        frameCountRef.current = 0;
        lastTimeRef.current = now;
      }
      rafRef.current = requestAnimationFrame(loop);
    };

    rafRef.current = requestAnimationFrame(loop);
    return () => {
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, []);

  const isGood = metrics.fps >= 55;

  return (
    <span
      style={{
        backgroundColor: isGood ? 'rgba(16, 185, 129, 0.16)' : 'rgba(245, 158, 11, 0.16)',
        color: isGood ? '#34d399' : '#fbbf24',
        border: `1px solid ${isGood ? 'rgba(16, 185, 129, 0.35)' : 'rgba(245, 158, 11, 0.35)'}`,
        borderRadius: '4px',
        padding: '1px 6px',
        fontSize: '0.62rem',
        fontWeight: 700,
        fontFamily: 'monospace'
      }}
      title="Live GPU / Animation frame rate"
    >
      {metrics.fps} FPS • {metrics.ms}ms
    </span>
  );
}

export default function DevFloatingHUD({
  activeStage,
  activeSection,
  progress,
  masterPercent = 0,
  isPlaying = false,
  hasPersistence = false,
  speed = 1,
  onFreezeLoader,
  onFreezeLogo,
  onFreezeExpansion,
  onFreezeHome,
  onMasterScrub,
  onLoadSection,
  onSetSpeed,
  onTogglePlay,
  onResume,
  onClear
}) {
  const [isOpen, setIsOpen] = useState(() => {
    try {
      const saved = localStorage.getItem(HUD_STORAGE_KEY);
      return saved !== null ? JSON.parse(saved) : true;
    } catch {
      return true;
    }
  });

  const toggleHUD = (stateOrFn) => {
    setIsOpen((prev) => {
      const next = typeof stateOrFn === 'function' ? stateOrFn(prev) : stateOrFn;
      try {
        localStorage.setItem(HUD_STORAGE_KEY, JSON.stringify(next));
      } catch {}
      return next;
    });
  };

  const [isMinimized, setIsMinimized] = useState(false);
  const [scrubberVal, setScrubberVal] = useState(masterPercent || 0);
  const isDraggingMasterRef = useRef(false);

  // Synchronize master scrubber ONLY when not actively dragging
  useEffect(() => {
    if (!isDraggingMasterRef.current) {
      setScrubberVal(masterPercent);
    }
  }, [masterPercent]);

  // Global Keyboard Shortcuts
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (['INPUT', 'TEXTAREA'].includes(document.activeElement?.tagName)) return;

      if (e.key === '`' || (e.shiftKey && (e.key === 'D' || e.key === 'd'))) {
        e.preventDefault();
        toggleHUD((prev) => !prev);
      }

      // Shift + C: Toggle 3D Free Camera View
      if (e.shiftKey && (e.key === 'C' || e.key === 'c')) {
        e.preventDefault();
        toggleFreeCam();
      }

      if (e.shiftKey && e.key === '1') {
        e.preventDefault();
        onFreezeLoader(50);
      }
      if (e.shiftKey && e.key === '2') {
        e.preventDefault();
        onFreezeLogo();
      }
      if (e.shiftKey && e.key === '3') {
        e.preventDefault();
        onFreezeExpansion(50);
      }
      if (e.shiftKey && e.key === '4') {
        e.preventDefault();
        onFreezeHome();
      }
      if (e.shiftKey && e.code === 'Space') {
        e.preventDefault();
        if (onTogglePlay) onTogglePlay();
        else onResume();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onFreezeLoader, onFreezeLogo, onFreezeExpansion, onFreezeHome, onTogglePlay, onResume]);

  if (!isOpen) {
    return null;
  }

  const stages = [
    { key: 'loader', label: '01 Loader', shortcut: '⇧1', action: () => onFreezeLoader(50) },
    { key: 'logo', label: '02 Monogram', shortcut: '⇧2', action: () => onFreezeLogo() },
    { key: 'expansion', label: '03 Expansion', shortcut: '⇧3', action: () => onFreezeExpansion(50) },
    { key: 'home', label: '04 Home', shortcut: '⇧4', action: () => onFreezeHome() }
  ];

  const sections = [
    { key: 'hero', label: 'Hero' },
    { key: 'projects', label: 'Projects' },
    { key: 'proof', label: 'Proof' },
    { key: 'services', label: 'Services' },
    { key: 'quote', label: 'Quote' },
    { key: 'reel', label: 'Reel' },
    { key: 'cta', label: 'Contact' },
    { key: 'showcase', label: 'Showcase' }
  ];

  const speeds = [0.1, 0.25, 0.5, 1, 2];

  const getTimelineLabel = (val) => {
    if (val < 25) return `Cyber HUD Counter (${Math.round((val / 25) * 100)}%)`;
    if (val < 45) return `5-Bar Sweep & Monogram Vector Draw`;
    if (val < 60) return `Seamless Monogram Z-Expansion Reveal (${Math.round(((val - 45) / 15) * 100)}%)`;
    if (val < 70) return `Home: Hero (01)`;
    if (val < 80) return `Home: Projects / Proof (03-04)`;
    if (val < 90) return `Home: Services & Reel (06-08)`;
    return `Home: Contact & Showcase (09-10)`;
  };

  return (
    <motion.div
      drag
      dragMomentum={false}
      dragElastic={0.04}
      initial={{ opacity: 0, y: 15, scale: 0.96 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, scale: 0.96 }}
      style={{
        position: 'fixed',
        bottom: '1.25rem',
        right: '1.25rem',
        zIndex: 9999999,
        backgroundColor: 'rgba(9, 13, 22, 0.95)',
        backdropFilter: 'blur(24px)',
        border: '1px solid rgba(56, 189, 248, 0.25)',
        borderRadius: '16px',
        padding: isMinimized ? '0.6rem 1rem' : '1.1rem',
        color: '#f4f4f6',
        fontFamily: 'Inter, system-ui, -apple-system, sans-serif',
        fontSize: '0.8rem',
        width: isMinimized ? 'auto' : '370px',
        maxWidth: 'calc(100vw - 2.5rem)',
        boxShadow: '0 25px 60px rgba(0,0,0,0.8), 0 0 25px rgba(56, 189, 248, 0.15)',
        userSelect: 'none',
        touchAction: 'none'
      }}
    >
      {/* 1. HEADER BAR & TELEMETRY */}
      <div 
        style={{ 
          display: 'flex', 
          justifyContent: 'space-between', 
          alignItems: 'center', 
          marginBottom: isMinimized ? 0 : '0.85rem',
          cursor: 'grab',
          borderBottom: isMinimized ? 'none' : '1px solid rgba(255, 255, 255, 0.08)',
          paddingBottom: isMinimized ? 0 : '0.65rem'
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
          <span style={{ fontSize: '1rem', color: '#38bdf8', opacity: 0.8 }}>⋮⋮</span>
          <span style={{ fontWeight: 800, letterSpacing: '0.06em', color: '#38bdf8', fontSize: '0.82rem', fontFamily: 'monospace' }}>
            DEV CONTROLLER
          </span>

          <FpsTelemetryBadge />

          {activeStage && (
            <span
              style={{
                backgroundColor: 'rgba(56, 189, 248, 0.18)',
                color: '#38bdf8',
                border: '1px solid rgba(56, 189, 248, 0.4)',
                borderRadius: '4px',
                padding: '1px 6px',
                fontSize: '0.62rem',
                fontWeight: 700,
                textTransform: 'uppercase'
              }}
            >
              {activeStage === 'logo' ? 'MONOGRAM' : (activeStage === 'expansion' ? 'Z-EXPANSION' : activeStage)}
            </span>
          )}



          {hasPersistence && (
            <span
              style={{
                backgroundColor: 'rgba(245, 158, 11, 0.2)',
                color: '#fbbf24',
                border: '1px solid rgba(245, 158, 11, 0.5)',
                borderRadius: '4px',
                padding: '1px 5px',
                fontSize: '0.6rem',
                fontWeight: 700,
                letterSpacing: '0.04em'
              }}
              title="Stage is persistent across browser refresh"
            >
              💾 SAVED
            </span>
          )}
        </div>

        <div style={{ display: 'flex', gap: '6px' }}>
          <button
            onClick={() => setIsMinimized((prev) => !prev)}
            style={{
              background: 'rgba(255, 255, 255, 0.06)',
              border: '1px solid rgba(255, 255, 255, 0.1)',
              borderRadius: '4px',
              color: '#a1a1aa',
              cursor: 'pointer',
              fontSize: '0.8rem',
              padding: '2px 6px'
            }}
            title={isMinimized ? 'Expand' : 'Minimize'}
          >
            {isMinimized ? '□' : '–'}
          </button>
          <button
            onClick={() => toggleHUD(false)}
            style={{
              background: 'rgba(255, 255, 255, 0.06)',
              border: '1px solid rgba(255, 255, 255, 0.1)',
              borderRadius: '4px',
              color: '#a1a1aa',
              cursor: 'pointer',
              fontSize: '0.8rem',
              padding: '2px 6px'
            }}
            title="Hide [~] (Persists across reload)"
          >
            ✕
          </button>
        </div>
      </div>

      {!isMinimized && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem' }}>
          {/* 2. STAGE BUTTONS */}
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.35rem' }}>
              <span style={{ fontSize: '0.66rem', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 700 }}>
                STAGES (Shift+1..4)
              </span>
              <span style={{ fontSize: '0.62rem', color: '#64748b' }}>
                {isPlaying ? '▶ TIMELINE PLAY' : (hasPersistence ? '🧊 STAGE LOCKED' : '● LIVE SYNC')}
              </span>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '5px' }}>
              {stages.map((st) => {
                const isActive = activeStage === st.key;
                return (
                  <button
                    key={st.key}
                    onClick={st.action}
                    style={{
                      padding: '0.45rem 0.2rem',
                      backgroundColor: isActive ? 'rgba(2, 132, 199, 0.95)' : 'rgba(255, 255, 255, 0.04)',
                      color: isActive ? '#ffffff' : '#cbd5e1',
                      border: isActive ? '1px solid #38bdf8' : '1px solid rgba(255, 255, 255, 0.08)',
                      borderRadius: '6px',
                      fontSize: '0.68rem',
                      fontWeight: isActive ? 800 : 500,
                      cursor: 'pointer',
                      transition: 'all 0.15s ease',
                      boxShadow: isActive ? '0 0 15px rgba(56, 189, 248, 0.4)' : 'none',
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      gap: '2px'
                    }}
                  >
                    <span>{st.label}</span>
                    <span style={{ fontSize: '0.55rem', opacity: 0.6, fontFamily: 'monospace' }}>{st.shortcut}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* 3. MASTER TIMELINE SCRUBBER */}
          <div style={{ backgroundColor: 'rgba(255, 255, 255, 0.03)', padding: '0.7rem', borderRadius: '10px', border: '1px solid rgba(56, 189, 248, 0.28)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
              <span style={{ fontSize: '0.68rem', color: '#38bdf8', fontWeight: 800, textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                MASTER TIMELINE SCRUBBER
              </span>
              <span style={{ color: '#38bdf8', fontWeight: 800, fontFamily: 'monospace', fontSize: '0.8rem' }}>
                {Math.round(scrubberVal)}%
              </span>
            </div>

            <div style={{ fontSize: '0.68rem', color: '#f1f5f9', marginBottom: '6px', fontStyle: 'italic', opacity: 0.95 }}>
              📍 {getTimelineLabel(scrubberVal)}
            </div>

            <input
              type="range"
              min="0"
              max="100"
              step="0.2"
              value={scrubberVal}
              onPointerDown={() => {
                isDraggingMasterRef.current = true;
              }}
              onChange={(e) => {
                const val = parseFloat(e.target.value);
                setScrubberVal(val);
                onMasterScrub(val, false);
              }}
              onPointerUp={() => {
                isDraggingMasterRef.current = false;
                onMasterScrub(scrubberVal, true);
              }}
              style={{
                width: '100%',
                cursor: 'pointer',
                accentColor: '#38bdf8',
                height: '6px',
                borderRadius: '3px'
              }}
            />

            {/* Scrubber Legend / Quick Milestones */}
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.6rem', color: '#94a3b8', marginTop: '6px', fontFamily: 'monospace' }}>
              <span onClick={() => { setScrubberVal(0); onMasterScrub(0, true); }} style={{ cursor: 'pointer' }}>0% Load</span>
              <span onClick={() => { setScrubberVal(35); onMasterScrub(35, true); }} style={{ cursor: 'pointer' }}>35% Logo</span>
              <span onClick={() => { setScrubberVal(52); onMasterScrub(52, true); }} style={{ cursor: 'pointer' }}>52% Z-Zoom</span>
              <span onClick={() => { setScrubberVal(70); onMasterScrub(70, true); }} style={{ cursor: 'pointer' }}>70% Hero</span>
              <span onClick={() => { setScrubberVal(100); onMasterScrub(100, true); }} style={{ cursor: 'pointer' }}>100% End</span>
            </div>
          </div>

          {/* 4. PER-STAGE DEDICATED SCRUBBERS & QUICK PRESETS */}
          <div style={{ backgroundColor: 'rgba(255, 255, 255, 0.03)', padding: '0.65rem', borderRadius: '8px', border: '1px solid rgba(255, 255, 255, 0.08)' }}>
            <div style={{ fontSize: '0.66rem', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: '0.45rem', fontWeight: 700 }}>
              {activeStage === 'loader' && '🔍 Stage Scrubber: Cyber HUD %'}
              {activeStage === 'logo' && '🔍 Stage Controller: Monogram Reveal'}
              {activeStage === 'expansion' && '🔍 Stage Scrubber: Monogram Z-Expansion %'}
              {activeStage === 'home' && '🔍 Stage Scrubber: Page Scroll'}
              {!activeStage && '🔍 Stage Diagnostics'}
            </div>

            {/* 01 Loader Percentage Scrubber */}
            {activeStage === 'loader' && (
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px', fontSize: '0.72rem' }}>
                  <span style={{ color: '#cbd5e1' }}>Percentage Counter:</span>
                  <span style={{ color: '#38bdf8', fontWeight: 800, fontFamily: 'monospace' }}>{progress !== null && progress !== undefined ? progress : 50}%</span>
                </div>
                <input
                  type="range"
                  min="0"
                  max="100"
                  value={progress !== null && progress !== undefined ? progress : 50}
                  onChange={(e) => onFreezeLoader(Number(e.target.value))}
                  style={{ width: '100%', cursor: 'pointer', accentColor: '#38bdf8' }}
                />
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: '4px', marginTop: '6px' }}>
                  {[0, 25, 50, 75, 99, 100].map((val) => (
                    <button
                      key={val}
                      onClick={() => onFreezeLoader(val)}
                      style={{
                        flex: 1,
                        padding: '2px 0',
                        fontSize: '0.58rem',
                        fontFamily: 'monospace',
                        backgroundColor: progress === val ? 'rgba(56, 189, 248, 0.25)' : 'rgba(255, 255, 255, 0.04)',
                        border: '1px solid rgba(255, 255, 255, 0.08)',
                        borderRadius: '3px',
                        color: progress === val ? '#38bdf8' : '#94a3b8',
                        cursor: 'pointer'
                      }}
                    >
                      {val}%
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* 02 Sweep & Monogram Stage Controller */}
            {activeStage === 'logo' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '2px', fontSize: '0.72rem' }}>
                  <span style={{ color: '#cbd5e1' }}>
                    {Number(progress !== null && progress !== undefined ? progress : 100) <= 35
                      ? '1. 5 Bars Sweeping Across' 
                      : (Number(progress !== null && progress !== undefined ? progress : 100) <= 70 
                          ? '2. Bars 2 & 4 Merge into 3 (5 → 3)' 
                          : (Number(progress !== null && progress !== undefined ? progress : 100) < 95 
                              ? '3. Merging into 1 Solid Card (3 → 1)' 
                              : '4. Solid Card & Monogram Active'))}
                  </span>
                  <span style={{ color: '#38bdf8', fontWeight: 800, fontFamily: 'monospace' }}>
                    {progress !== null && progress !== undefined ? progress : 100}%
                  </span>
                </div>

                <input
                  type="range"
                  min="0"
                  max="100"
                  value={progress !== null && progress !== undefined ? progress : 100}
                  onChange={(e) => onFreezeLogo(Number(e.target.value))}
                  style={{ width: '100%', cursor: 'pointer', accentColor: '#38bdf8' }}
                />

                <div style={{ display: 'flex', justifyContent: 'space-between', gap: '4px' }}>
                  {[
                    { label: '0% Start', val: 0 },
                    { label: '35% 5-Bars', val: 35 },
                    { label: '70% 3-Panels', val: 70 },
                    { label: '100% Solid', val: 100 }
                  ].map((item) => (
                    <button
                      key={item.val}
                      onClick={() => onFreezeLogo(item.val)}
                      style={{
                        flex: 1,
                        padding: '3px 0',
                        fontSize: '0.58rem',
                        fontFamily: 'monospace',
                        backgroundColor: (progress === item.val || (progress === null && item.val === 100)) ? 'rgba(56, 189, 248, 0.25)' : 'rgba(255, 255, 255, 0.04)',
                        border: '1px solid rgba(255, 255, 255, 0.08)',
                        borderRadius: '3px',
                        color: (progress === item.val || (progress === null && item.val === 100)) ? '#38bdf8' : '#94a3b8',
                        cursor: 'pointer'
                      }}
                    >
                      {item.label}
                    </button>
                  ))}
                </div>

                <button
                  onClick={() => onFreezeLogo(100)}
                  style={{
                    marginTop: '2px',
                    padding: '0.4rem 0.5rem',
                    backgroundColor: 'rgba(56, 189, 248, 0.15)',
                    color: '#38bdf8',
                    border: '1px solid rgba(56, 189, 248, 0.35)',
                    borderRadius: '5px',
                    fontSize: '0.68rem',
                    fontWeight: 700,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '6px'
                  }}
                >
                  <span>✨ Reset to 100% Monogram Reveal</span>
                </button>
              </div>
            )}

            {/* 03 Monogram Z-Expansion Interactive Scrubber */}
            {activeStage === 'expansion' && (
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px', fontSize: '0.72rem' }}>
                  <span style={{ color: '#cbd5e1' }}>
                    {Number(progress || 50) <= 35
                      ? '1. Z-Transparency Core' 
                      : (Number(progress || 50) <= 75 
                          ? '2. Vector Zoom Aperture' 
                          : '3. Revealing Main Page')}
                  </span>
                  <span style={{ color: '#38bdf8', fontWeight: 800, fontFamily: 'monospace' }}>
                    {progress !== null && progress !== undefined ? progress : 50}%
                  </span>
                </div>
                <input
                  type="range"
                  min="0"
                  max="100"
                  value={progress !== null && progress !== undefined ? progress : 50}
                  onChange={(e) => onFreezeExpansion(Number(e.target.value))}
                  style={{ width: '100%', cursor: 'pointer', accentColor: '#38bdf8' }}
                />
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: '4px', marginTop: '6px' }}>
                  {[
                    { label: '0% Core', val: 0 },
                    { label: '35% Poly', val: 35 },
                    { label: '65% Zoom', val: 65 },
                    { label: '100% Home', val: 100 }
                  ].map((item) => (
                    <button
                      key={item.val}
                      onClick={() => onFreezeExpansion(item.val)}
                      style={{
                        flex: 1,
                        padding: '2px 0',
                        fontSize: '0.58rem',
                        fontFamily: 'monospace',
                        backgroundColor: progress === item.val ? 'rgba(56, 189, 248, 0.25)' : 'rgba(255, 255, 255, 0.04)',
                        border: '1px solid rgba(255, 255, 255, 0.08)',
                        borderRadius: '3px',
                        color: progress === item.val ? '#38bdf8' : '#94a3b8',
                        cursor: 'pointer'
                      }}
                    >
                      {item.label}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* 04 Home Scroll & Section Status */}
            {(activeStage === 'home' || !activeStage) && (
              <div>
                <div style={{ fontSize: '0.68rem', color: '#94a3b8', marginBottom: '0.35rem', display: 'flex', justifyContent: 'space-between' }}>
                  <span>Active Section:</span>
                  <span style={{ color: '#38bdf8', fontWeight: 700 }}>#{activeSection || 'hero'}</span>
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '4px' }}>
                  {sections.map((sec) => {
                    const isSecActive = activeSection === sec.key;
                    return (
                      <button
                        key={sec.key}
                        onClick={() => onLoadSection(sec.key)}
                        style={{
                          padding: '0.35rem 0',
                          backgroundColor: isSecActive ? 'rgba(56, 189, 248, 0.25)' : 'rgba(255, 255, 255, 0.03)',
                          color: isSecActive ? '#38bdf8' : '#94a3b8',
                          border: isSecActive ? '1px solid #38bdf8' : '1px solid rgba(255, 255, 255, 0.06)',
                          borderRadius: '4px',
                          fontSize: '0.66rem',
                          fontWeight: isSecActive ? 700 : 500,
                          cursor: 'pointer'
                        }}
                      >
                        {sec.label}
                      </button>
                    );
                  })}
                </div>
              </div>
            )}
          </div>



          {/* 6. PLAYBACK SPEED SUITE */}
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.35rem' }}>
              <span style={{ fontSize: '0.66rem', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 700 }}>
                PLAYBACK SPEED
              </span>
              <span style={{ fontSize: '0.68rem', color: '#38bdf8', fontWeight: 800, fontFamily: 'monospace' }}>
                {speed || 1}x
              </span>
            </div>
            <div style={{ display: 'flex', gap: '4px' }}>
              {speeds.map((sp) => {
                const isSpeedActive = Number(speed) === sp;
                return (
                  <button
                    key={sp}
                    onClick={() => onSetSpeed(sp)}
                    style={{
                      flex: 1,
                      padding: '0.35rem 0',
                      backgroundColor: isSpeedActive ? '#0284c7' : 'rgba(255, 255, 255, 0.04)',
                      color: isSpeedActive ? '#ffffff' : '#94a3b8',
                      border: isSpeedActive ? '1px solid #38bdf8' : '1px solid rgba(255, 255, 255, 0.08)',
                      borderRadius: '5px',
                      fontSize: '0.68rem',
                      fontWeight: isSpeedActive ? 800 : 500,
                      cursor: 'pointer',
                      transition: 'all 0.15s ease'
                    }}
                    title={sp < 1 ? `Slow Motion ${sp}x` : `${sp}x Speed`}
                  >
                    {sp}x
                  </button>
                );
              })}
            </div>
          </div>

          {/* 6. PLAY / RESET / PERSISTENCE ACTION BAR */}
          <div style={{ display: 'flex', gap: '6px', marginTop: '0.1rem', paddingTop: '0.6rem', borderTop: '1px solid rgba(255, 255, 255, 0.08)' }}>
            <button
              onClick={onTogglePlay || onResume}
              style={{
                flex: 2,
                padding: '0.55rem',
                backgroundColor: isPlaying ? '#e11d48' : '#10b981',
                color: '#ffffff',
                border: 'none',
                borderRadius: '6px',
                fontWeight: 800,
                fontSize: '0.75rem',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '6px',
                boxShadow: isPlaying ? '0 0 15px rgba(225, 29, 72, 0.4)' : '0 0 15px rgba(16, 185, 129, 0.35)'
              }}
              title={isPlaying ? 'Pause Scrubber Playback' : 'Play continuous timeline from start across all stages (Shift+Space)'}
            >
              <span>{isPlaying ? '⏸ Pause' : '▶ Play All (Scrubber)'}</span>
            </button>

            <button
              onClick={onResume}
              style={{
                padding: '0.55rem 0.65rem',
                backgroundColor: 'rgba(255, 255, 255, 0.06)',
                color: '#f1f5f9',
                border: '1px solid rgba(255, 255, 255, 0.12)',
                borderRadius: '6px',
                fontSize: '0.75rem',
                fontWeight: 600,
                cursor: 'pointer'
              }}
              title="Reset full intro sequence from 0%"
            >
              🔄 Reset
            </button>

            <button
              onClick={onClear}
              style={{
                padding: '0.55rem 0.7rem',
                backgroundColor: hasPersistence ? 'rgba(245, 158, 11, 0.2)' : 'rgba(255, 255, 255, 0.04)',
                color: hasPersistence ? '#fbbf24' : '#94a3b8',
                border: hasPersistence ? '1px solid #f59e0b' : '1px solid rgba(255, 255, 255, 0.08)',
                borderRadius: '6px',
                fontSize: '0.72rem',
                fontWeight: hasPersistence ? 800 : 500,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '4px',
                boxShadow: hasPersistence ? '0 0 12px rgba(245, 158, 11, 0.3)' : 'none'
              }}
              title={hasPersistence ? "Persistence is SAVED in localStorage. Click to clear." : "No persistence active"}
            >
              <span>🧹</span>
              {hasPersistence && <span>SAVED</span>}
            </button>
          </div>
        </div>
      )}
    </motion.div>
  );
}
