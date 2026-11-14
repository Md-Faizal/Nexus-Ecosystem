import React from 'react';

export const NEXUS_MONOGRAM_PATHS = {
  wingDown: "M43.82,70.3l64.52.05,5.42-12.76-32.78-.36,41.09-19.08,7.56-18.43,27.69.85-25.84,61.15c-2.63,6.23-11.43,8.64-18.18,8.65l-111.12.13,41.64-20.2Z",
  wingUp: "M129.63,19.71l-65.22.74-5.49,12.65,32.87.55-41.14,19.05-6.83,17.6-28.38-.02L41.22,9.13C43.67,3.32,52.08.55,58.48.55l110.96-.05-39.82,19.21Z",
  centerPoly: "44.55 69.8 51.04 53.07 93.98 33.19 59.67 32.61 64.74 20.95 128.88 20.22 121.68 37.77 78.77 57.7 113 58.08 108.01 69.85 44.55 69.8",
  centerWhite: "M128.13,20.73l-6.84,16.67-40.73,18.92-4,1.86,4.41.05,31.28.34-4.57,10.78-62.4-.05,6.16-15.86,40.77-18.88,3.96-1.83-4.36-.07-31.37-.53,4.64-10.68,63.06-.71M129.63,19.71l-65.22.74-5.49,12.65,32.87.55-41.14,19.05-6.83,17.6,64.52.05,5.42-12.76-32.78-.36,41.09-19.08,7.56-18.43h0Z"
};

export function NexusMonogramIcon({ className = "w-5 h-5", glow = false }) {
  return (
    <svg
      viewBox="0 0 171.16 89.17"
      className={`${className} ${glow ? 'drop-shadow-[0_0_8px_rgba(137,255,0,0.6)]' : ''}`}
      fill="currentColor"
    >
      <path d={NEXUS_MONOGRAM_PATHS.wingDown} fill="#89ff00" />
      <path d={NEXUS_MONOGRAM_PATHS.wingUp} fill="#00e5ff" />
      <polygon points={NEXUS_MONOGRAM_PATHS.centerPoly} fill="#ffffff" />
      <path d={NEXUS_MONOGRAM_PATHS.centerWhite} fill="#ffffff" />
    </svg>
  );
}

/**
 * Official "Sign in with Nexus" Button
 * @param {'cyber' | 'glass' | 'minimal'} variant
 * @param {string} label
 */
export default function NexusSignInButton({
  onClick,
  variant = 'cyber',
  label = 'Sign in with Nexus',
  className = '',
  iconOnly = false,
  disabled = false
}) {
  const baseStyles = "relative inline-flex items-center justify-center gap-3 px-5 py-2.5 rounded-xl font-medium text-sm transition-all duration-300 disabled:opacity-50 disabled:cursor-not-allowed select-none group";

  const variants = {
    cyber: "bg-[#0b0c10] text-white border border-[#89ff00]/40 hover:border-[#89ff00] hover:shadow-[0_0_20px_rgba(137,255,0,0.3)] active:scale-[0.98]",
    glass: "bg-white/[0.08] backdrop-blur-xl text-white border border-white/20 hover:bg-white/[0.14] hover:border-white/30 shadow-[0_8px_32px_rgba(0,0,0,0.3)] active:scale-[0.98]",
    minimal: "bg-white text-black hover:bg-neutral-100 border border-neutral-200 shadow-sm active:scale-[0.98]"
  };

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`${baseStyles} ${variants[variant] || variants.cyber} ${className}`}
    >
      <NexusMonogramIcon
        className="w-5 h-5 shrink-0 transition-transform group-hover:scale-110"
        glow={variant === 'cyber'}
      />
      {!iconOnly && <span className="tracking-wide font-semibold">{label}</span>}
    </button>
  );
}

