import sharp from 'sharp';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const w = 3840;
const h = 2160;

const svg = `
<svg width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="cardGlow" x1="1" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="rgba(245, 158, 11, 0.12)" />
      <stop offset="100%" stop-color="transparent" />
    </linearGradient>
  </defs>

  <!-- Background -->
  <rect width="${w}" height="${h}" fill="#0a0a0e" />

  <!-- 1. Marquee Ticker Bar -->
  <rect y="40" width="${w}" height="140" fill="#0d0d13" fill-opacity="0.9" />
  <line x1="0" y1="40" x2="${w}" y2="40" stroke="rgba(255,255,255,0.12)" stroke-width="2" />
  <line x1="0" y1="180" x2="${w}" y2="180" stroke="rgba(255,255,255,0.12)" stroke-width="2" />

  <g transform="translate(120, 132)" font-family="system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif">
    <!-- Segment 1 -->
    <text x="0" y="0" font-family="serif" font-style="italic" font-size="76" font-weight="300" fill="#ffffff">Works</text>
    
    <rect x="230" y="-54" width="130" height="58" rx="29" fill="rgba(245, 158, 11, 0.15)" stroke="rgba(245, 158, 11, 0.4)" stroke-width="2" />
    <text x="262" y="-14" font-family="monospace" font-size="30" font-weight="bold" fill="#f59e0b">( 01 )</text>
    
    <text x="400" y="-12" font-size="42" font-weight="600" fill="#c8cbd5" letter-spacing="2">BY FZ MONOGRAM</text>
    <circle cx="840" cy="-22" r="10" fill="#f59e0b" />
    <text x="880" y="-14" font-family="monospace" font-size="34" fill="rgba(255,255,255,0.35)" letter-spacing="4">// SELECTED ARCHITECTURE</text>
    <line x1="1500" y1="-24" x2="1640" y2="-24" stroke="rgba(255,255,255,0.25)" stroke-width="3" />

    <!-- Segment 2 -->
    <text x="1740" y="0" font-family="serif" font-style="italic" font-size="76" font-weight="300" fill="#ffffff">Works</text>
    <rect x="1970" y="-54" width="130" height="58" rx="29" fill="rgba(245, 158, 11, 0.15)" stroke="rgba(245, 158, 11, 0.4)" stroke-width="2" />
    <text x="2002" y="-14" font-family="monospace" font-size="30" font-weight="bold" fill="#f59e0b">( 01 )</text>
    <text x="2140" y="-12" font-size="42" font-weight="600" fill="#c8cbd5" letter-spacing="2">BY FZ MONOGRAM</text>
    <circle cx="2580" cy="-22" r="10" fill="#f59e0b" />
    <text x="2620" y="-14" font-family="monospace" font-size="34" fill="rgba(255,255,255,0.35)" letter-spacing="4">// SELECTED ARCHITECTURE</text>
    <line x1="3240" y1="-24" x2="3380" y2="-24" stroke="rgba(255,255,255,0.25)" stroke-width="3" />
  </g>

  <!-- 2. Editorial Section Header -->
  <g transform="translate(240, 340)" font-family="system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif">
    <circle cx="10" cy="0" r="8" fill="#f59e0b" />
    <text x="32" y="7" font-family="monospace" font-size="26" fill="#f59e0b" letter-spacing="4">FEATURED WORKS // 2026 ARCHIVE</text>
    <text x="0" y="90" font-size="96" font-weight="200" fill="#ffffff" letter-spacing="-1">Curated Systems &amp; Spaces</text>
    <text x="2100" y="70" font-size="30" font-weight="300" fill="rgba(255,255,255,0.5)">A showcase of bespoke web applications, interactive 3D spaces,</text>
    <text x="2100" y="115" font-size="30" font-weight="300" fill="rgba(255,255,255,0.5)">and product architectures crafted with precision.</text>
    <line x1="0" y1="170" x2="3360" y2="170" stroke="rgba(255,255,255,0.12)" stroke-width="2" />
  </g>

  <!-- 3. Featured Project Cards Grid -->
  <!-- Card 1 -->
  <g transform="translate(240, 580)" font-family="system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif">
    <rect width="1640" height="720" rx="24" fill="rgba(18, 18, 24, 0.75)" stroke="rgba(255, 255, 255, 0.12)" stroke-width="2" />
    <rect width="1640" height="720" rx="24" fill="url(#cardGlow)" />
    <text x="60" y="80" font-family="monospace" font-size="32" fill="#f59e0b">01 // LIVE PORTAL</text>
    <text x="60" y="190" font-size="64" font-weight="300" fill="#ffffff">MacOS for Web</text>
    <text x="60" y="260" font-size="32" font-weight="300" fill="rgba(255,255,255,0.5)">Interactive Operating System</text>
    <text x="60" y="340" font-size="26" font-weight="300" fill="rgba(255,255,255,0.4)">Full desktop-grade browser interface, window manager, and responsive shell.</text>
  </g>

  <!-- Card 2 -->
  <g transform="translate(1960, 580)" font-family="system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif">
    <rect width="1640" height="720" rx="24" fill="rgba(18, 18, 24, 0.75)" stroke="rgba(255, 255, 255, 0.12)" stroke-width="2" />
    <rect width="1640" height="720" rx="24" fill="url(#cardGlow)" />
    <text x="60" y="80" font-family="monospace" font-size="32" fill="#e88a38">02 // HERO SPACE</text>
    <text x="60" y="190" font-size="64" font-weight="300" fill="#ffffff">Monolithic Amber Vault</text>
    <text x="60" y="260" font-size="32" font-weight="300" fill="rgba(255,255,255,0.5)">3D Spatial Architecture</text>
    <text x="60" y="340" font-size="26" font-weight="300" fill="rgba(255,255,255,0.4)">Cinematic ray-traced spatial environment with volumetric dust scattering.</text>
  </g>

  <!-- Card 3 & 4 (peek at bottom) -->
  <g transform="translate(240, 1360)" font-family="system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif">
    <rect width="1640" height="720" rx="24" fill="rgba(18, 18, 24, 0.75)" stroke="rgba(255, 255, 255, 0.12)" stroke-width="2" />
    <text x="60" y="80" font-family="monospace" font-size="32" fill="#f59e0b">03 // SYSTEM</text>
    <text x="60" y="190" font-size="64" font-weight="300" fill="#ffffff">Vector Motion Engine</text>
    <text x="60" y="260" font-size="32" font-weight="300" fill="rgba(255,255,255,0.5)">Frontend Physics &amp; Shaders</text>
  </g>
  <g transform="translate(1960, 1360)" font-family="system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif">
    <rect width="1640" height="720" rx="24" fill="rgba(18, 18, 24, 0.75)" stroke="rgba(255, 255, 255, 0.12)" stroke-width="2" />
    <text x="60" y="80" font-family="monospace" font-size="32" fill="#d97706">04 // ARCHIVE</text>
    <text x="60" y="190" font-size="64" font-weight="300" fill="#ffffff">Cyber Monogram</text>
    <text x="60" y="260" font-size="32" font-weight="300" fill="rgba(255,255,255,0.5)">Brand &amp; Generative Typography</text>
  </g>
</svg>
`;

async function main() {
  const outPath = path.join(__dirname, 'public/models/monitor_editorial_ui.png');
  await sharp(Buffer.from(svg))
    .resize(2048, 860, { fit: 'fill' })
    .png()
    .toFile(outPath);
  console.log('Saved monitor_editorial_ui.png at 2048x860');

  const out4K = path.join(__dirname, 'public/models/section2_screen_4k.png');
  await sharp(Buffer.from(svg))
    .png()
    .toFile(out4K);
  console.log('Saved section2_screen_4k.png at 3840x2160');
}

main().catch(console.error);