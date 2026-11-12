/**
 * loaderConfig.js - Single Entry Point for Loader Theme, Visual Tokens & Stage Behavior
 * 
 * Futuristic Cyber HUD Theme with linked progress illumination.
 */

export const LOADER_CONFIG = {
  /**
   * Keep Stage 1 (chevrons, corner HUD reticles) active in the background during Stage 2 & 3:
   *  - 'Y': Keeps chevrons & HUD corners visible in the background framing the sweep card.
   *  - 'N': Fades out the entire Stage 1 background to pure black with the disappear/scale effect as previous.
   */
  keepBackgroundActiveDuringSweep: 'N', // 'Y' | 'N'
};

export const LOADER_THEME = {
  // Primary Sweep & Accent Color
  accent: '#ff0055',

  // Master Card Gradient
  cardGradient: 'linear-gradient(135deg, #070309 0%, #ff0055 50%, #0a0410 100%)',

  // Canvas Dark Background
  background: '#000000',

  // Cyber Neon Colors
  neonGreen: '#55ff00',
  neonGreenBright: '#39ff14',
  neonRed: '#ff0055',
  neonRedHot: '#ff1475',
  neonRedDeep: '#b3003b',

  // Typography & UI
  textWhite: '#ffffff',
  textGreen: '#55ff00',

  // Glowing Borders & Accents
  borderColor: '#ffffff',
  cardGlow: '0 0 25px rgba(255, 255, 255, 0.7), 0 0 45px rgba(255, 0, 85, 0.45), inset 0 0 20px rgba(255, 255, 255, 0.35)',
  borderGlow: '0 0 10px rgba(255, 0, 85, 0.65)',

  // Monogram Colors
  monogramWings: '#55ff00',
  monogramCore: '#050507',
  monogramAccent: '#ffffff',
  monogramNeon: '#55ff00'
};
