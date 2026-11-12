import React from "react";

const FEATURED_PROJECTS = [
  {
    number: "01",
    title: "MacOS for Web",
    category: "Interactive Operating System",
    description: "Full desktop-grade browser interface, window manager, and responsive application shell.",
    year: "2026",
    status: "LIVE PORTAL",
    image: "/projects/macos_web.png",
    accent: "#f59e0b"
  },
  {
    number: "02",
    title: "Monolithic Amber Vault",
    category: "3D Spatial Architecture",
    description: "Cinematic ray-traced spatial environment with volumetric dust scattering and interactive camera dolly.",
    year: "2026",
    status: "HERO SPACE",
    image: "/projects/amber_vault.png",
    accent: "#e88a38"
  },
  {
    number: "03",
    title: "Vector Motion Engine",
    category: "Frontend Physics & Shaders",
    description: "GPU-accelerated vector deformation, inertia scroll controllers, and micro-interaction frameworks.",
    year: "2025",
    status: "SYSTEM",
    image: "/projects/vector_motion.png",
    accent: "#f59e0b"
  },
  {
    number: "04",
    title: "Cyber Monogram",
    category: "Brand & Generative Typography",
    description: "Procedural geometry system, kinetic identity guidelines, and editorial web layout benchmarks.",
    year: "2025",
    status: "ARCHIVE",
    image: "/projects/cyber_monogram.webp",
    accent: "#d97706"
  }
];

export default function Marquee1Section({ onNavigate }) {
  return (
    <div className="w-screen relative z-10 overflow-hidden bg-[#0a0a0e] pt-0 pb-24 text-white">
      {/* 1. SEAMLESS MARQUEE TICKER */}
      <div className="w-full border-y border-white/10 py-4 md:py-6 overflow-hidden bg-[#0d0d13]/80 backdrop-blur-md">
        <div className="flex w-max animate-marquee select-none whitespace-nowrap">
          {[...Array(4)].map((_, idx) => (
            <div key={idx} className="flex items-center gap-8 md:gap-16 px-6 md:px-12">
              <span className="font-serif italic text-4xl md:text-7xl font-light text-white tracking-tight">
                Works
              </span>
              <span className="text-[#f59e0b] font-mono text-base md:text-xl font-bold tracking-widest px-3 py-1 rounded-full border border-[#f59e0b]/30 bg-[#f59e0b]/10">
                ( 01 )
              </span>
              <span className="text-xl md:text-3xl font-semibold text-[#c8cbd5] tracking-tight uppercase">
                By FZ Monogram
              </span>
              <div className="w-3 h-3 rounded-full bg-[#f59e0b] animate-pulse" />
              <span className="text-lg md:text-2xl font-mono text-white/30 tracking-widest uppercase">
                // SELECTED ARCHITECTURE
              </span>
              <div className="w-12 md:w-20 h-[1.5px] bg-white/20" />
            </div>
          ))}
        </div>
      </div>

      {/* 2. EDITORIAL SECTION HEADER */}
      <div className="max-w-7xl mx-auto px-6 md:px-12 pt-16 md:pt-24 pb-8 flex flex-col md:flex-row md:items-end justify-between border-b border-white/10">
        <div>
          <div className="flex items-center gap-3 text-xs md:text-sm font-mono text-[#f59e0b] uppercase tracking-widest mb-3">
            <span className="inline-block w-2 h-2 rounded-full bg-[#f59e0b]" />
            FEATURED WORKS // 2026 ARCHIVE
          </div>
          <h2 className="text-3xl md:text-5xl font-light tracking-tight text-white">
            Curated Systems &amp; Spaces
          </h2>
        </div>
        <p className="mt-4 md:mt-0 text-sm md:text-base font-light text-white/50 max-w-md">
          A showcase of bespoke web applications, interactive 3D spaces, and product architectures crafted with precision.
        </p>
      </div>

      {/* 3. FEATURED PROJECTS SHOWCASE GRID */}
      <div className="max-w-7xl mx-auto px-6 md:px-12 pt-12">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          {FEATURED_PROJECTS.map((project) => (
            <div
              key={project.number}
              onClick={() => onNavigate?.('work')}
              className="group relative cursor-pointer rounded-2xl p-8 bg-[#121218]/70 border border-white/10 hover:border-[#f59e0b]/50 transition-all duration-500 hover:shadow-2xl hover:shadow-[#f59e0b]/5 hover:-translate-y-1 overflow-hidden"
            >
              {/* Subtle Card Background Glow on Hover */}
              <div className="absolute top-0 right-0 w-64 h-64 bg-radial from-[#f59e0b]/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none" />

              <div className="flex items-center justify-between pb-6 border-b border-white/10">
                <span className="font-mono text-xs md:text-sm tracking-wider text-[#f59e0b] font-semibold">
                  INDEX // {project.number}
                </span>
                <span className="text-[10px] md:text-xs font-mono px-2.5 py-1 rounded-md bg-white/5 text-white/70 border border-white/10">
                  {project.status}
                </span>
              </div>

              <div className="pt-6">
                <div className="text-xs font-mono uppercase tracking-widest text-white/40 mb-2">
                  {project.category}
                </div>
                <h3 className="text-2xl md:text-3xl font-normal tracking-tight text-white group-hover:text-[#f59e0b] transition-colors duration-300">
                  {project.title}
                </h3>
                <p className="mt-3 text-sm font-light text-white/60 leading-relaxed">
                  {project.description}
                </p>

                {/* Editorial Visual Preview Box */}
                <div className="relative w-full h-48 md:h-60 mt-6 rounded-xl overflow-hidden bg-black/40 border border-white/5">
                  <img
                    src={project.image}
                    alt={project.title}
                    className="w-full h-full object-cover object-center group-hover:scale-105 transition-transform duration-700 ease-out"
                    loading="lazy"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-[#121218]/90 via-transparent to-transparent opacity-60" />
                </div>
              </div>

              <div className="pt-8 flex items-center justify-between text-xs font-mono text-white/40">
                <span>RELEASE // {project.year}</span>
                <span className="flex items-center gap-2 text-white group-hover:text-[#f59e0b] transition-colors duration-300 font-semibold">
                  EXPLORE CASE STUDY
                  <span className="transition-transform duration-300 group-hover:translate-x-1.5">→</span>
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>

      <style>{`
        @keyframes marqueeScroll {
          0% { transform: translateX(0%); }
          100% { transform: translateX(-50%); }
        }
        .animate-marquee {
          animation: marqueeScroll 28s linear infinite;
        }
        .animate-marquee:hover {
          animation-play-state: paused;
        }
      `}</style>
    </div>
  );
}
