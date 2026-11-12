import sharp from 'sharp';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const TOTAL_FRAMES = 40;
const CANVAS_W = 3840;
const CANVAS_H = 2160;

function smoothstep(t) {
  t = Math.max(0, Math.min(1, t));
  return t * t * (3.0 - 2.0 * t);
}

// Blender verified camera dolly & monitor bounding box interpolation
function getMonitorBounds(p) {
  const t = smoothstep(p);
  const cx = 0.500;
  const cy = 0.572 - t * 0.002;

  // Width: 0.093 at p=0 -> 1.000 at p=1
  // Height: 0.057 at p=0 -> 0.636 at p=1
  const wFrac = Math.min(1.0, 0.093 + t * (1.000 - 0.093));
  const hFrac = Math.min(1.0, 0.057 + t * (0.636 - 0.057));

  const w = Math.min(CANVAS_W, Math.round(wFrac * CANVAS_W));
  const h = Math.min(CANVAS_H, Math.round(hFrac * CANVAS_H));
  const left = Math.max(0, Math.min(CANVAS_W - w, Math.round(cx * CANVAS_W - w / 2)));
  const top = Math.max(0, Math.min(CANVAS_H - h, Math.round(cy * CANVAS_H - h / 2)));

  return { left, top, width: w, height: h };
}

async function compositeAllFrames() {
  const baseDir = path.join(__dirname, 'public/sequence');
  const distDir = path.join(__dirname, 'dist/sequence');
  fs.mkdirSync(distDir, { recursive: true });

  const screenImgPath = path.join(__dirname, 'public/models/section2_screen_4k.png');
  const screenBuffer = fs.readFileSync(screenImgPath);

  // High-res base beauty frame 00
  const baseFramePath = path.join(baseDir, 'frame_00.webp');
  const baseFrameBuf = fs.readFileSync(baseFramePath);

  console.log('--- STARTING 4K COMPOSITE PIPELINE FOR 40 FRAMES ---');
  const t0 = Date.now();

  for (let i = 0; i < TOTAL_FRAMES; i++) {
    const p = i / (TOTAL_FRAMES - 1);
    const numStr = String(i).padStart(2, '0');
    const existingFramePath = path.join(baseDir, `frame_${numStr}.webp`);
    
    // 1. Frame 00: Standby mode on desk (dark obsidian screen with subtle reflection & amber standby dot)
    if (i === 0) {
      let bg = sharp(baseFrameBuf).resize(CANVAS_W, CANVAS_H, { fit: 'cover' });
      const b = getMonitorBounds(0);
      const standbySvg = `
      <svg width="${b.width}" height="${b.height}" xmlns="http://www.w3.org/2000/svg">
        <rect width="100%" height="100%" rx="6" fill="#06060a" stroke="rgba(255,255,255,0.06)" stroke-width="2" />
        <circle cx="${b.width - 12}" cy="${b.height - 12}" r="3.5" fill="#f59e0b" />
      </svg>
      `;
      const standbyOverlay = await sharp(Buffer.from(standbySvg)).png().toBuffer();
      const finalImg = bg.composite([{
        input: standbyOverlay,
        left: b.left,
        top: b.top
      }]);

      const webpOut = await finalImg.webp({ quality: 90 }).toBuffer();
      const jpegOut = await sharp(webpOut).jpeg({ quality: 90 }).toBuffer();

      fs.writeFileSync(path.join(baseDir, `frame_${numStr}.webp`), webpOut);
      fs.writeFileSync(path.join(distDir, `frame_${numStr}.webp`), webpOut);
      fs.writeFileSync(path.join(baseDir, `frame_${numStr}.jpg`), jpegOut);
      fs.writeFileSync(path.join(distDir, `frame_${numStr}.jpg`), jpegOut);
      console.log(`  Processed frame_${numStr}`);
      continue;
    }

    // 2. Frames 01 to 03: Power-on boot beam flash on desk
    if (i >= 1 && i <= 3) {
      let bgBuf = fs.existsSync(existingFramePath) ? fs.readFileSync(existingFramePath) : baseFrameBuf;
      let bg = sharp(bgBuf).resize(CANVAS_W, CANVAS_H, { fit: 'cover' });
      const b = getMonitorBounds(p);
      const flashAlpha = i === 2 ? 0.95 : 0.6;
      const flashSvg = `
      <svg width="${b.width}" height="${b.height}" xmlns="http://www.w3.org/2000/svg">
        <rect width="100%" height="100%" rx="6" fill="#08080f" />
        <rect y="${Math.round(b.height / 2 - 2)}" width="100%" height="4" fill="#f59e0b" fill-opacity="${flashAlpha}" />
        <rect y="${Math.round(b.height / 2 - 12)}" width="100%" height="24" fill="#f59e0b" fill-opacity="${flashAlpha * 0.3}" />
      </svg>
      `;
      const flashOverlay = await sharp(Buffer.from(flashSvg)).png().toBuffer();
      const finalImg = bg.composite([{
        input: flashOverlay,
        left: b.left,
        top: b.top
      }]);

      const webpOut = await finalImg.webp({ quality: 90 }).toBuffer();
      const jpegOut = await sharp(webpOut).jpeg({ quality: 90 }).toBuffer();

      fs.writeFileSync(path.join(baseDir, `frame_${numStr}.webp`), webpOut);
      fs.writeFileSync(path.join(distDir, `frame_${numStr}.webp`), webpOut);
      fs.writeFileSync(path.join(baseDir, `frame_${numStr}.jpg`), jpegOut);
      fs.writeFileSync(path.join(distDir, `frame_${numStr}.jpg`), jpegOut);
      console.log(`  Processed frame_${numStr}`);
      continue;
    }

    // 3. Frames 04 to 36: Monitor is ON, displaying Section 2, dollying in
    if (i >= 4 && i <= 36) {
      let bgBuf = fs.existsSync(existingFramePath) ? fs.readFileSync(existingFramePath) : baseFrameBuf;
      let bg = sharp(bgBuf).resize(CANVAS_W, CANVAS_H, { fit: 'cover' });
      const b = getMonitorBounds(p);

      const resizedScreen = await sharp(screenBuffer)
        .resize(b.width, b.height, { fit: 'fill' })
        .png()
        .toBuffer();

      const finalImg = bg.composite([{
        input: resizedScreen,
        left: b.left,
        top: b.top
      }]);

      const webpOut = await finalImg.webp({ quality: 90 }).toBuffer();
      const jpegOut = await sharp(webpOut).jpeg({ quality: 90 }).toBuffer();

      fs.writeFileSync(path.join(baseDir, `frame_${numStr}.webp`), webpOut);
      fs.writeFileSync(path.join(distDir, `frame_${numStr}.webp`), webpOut);
      fs.writeFileSync(path.join(baseDir, `frame_${numStr}.jpg`), jpegOut);
      fs.writeFileSync(path.join(distDir, `frame_${numStr}.jpg`), jpegOut);
      console.log(`  Processed frame_${numStr}`);
      continue;
    }

    // 4. Frames 37 to 39: Monitor covers entire 4K screen (pure Section 2!)
    if (i >= 37) {
      const finalImg = sharp(screenBuffer).resize(CANVAS_W, CANVAS_H, { fit: 'fill' });

      const webpOut = await finalImg.webp({ quality: 92 }).toBuffer();
      const jpegOut = await sharp(webpOut).jpeg({ quality: 92 }).toBuffer();

      fs.writeFileSync(path.join(baseDir, `frame_${numStr}.webp`), webpOut);
      fs.writeFileSync(path.join(distDir, `frame_${numStr}.webp`), webpOut);
      fs.writeFileSync(path.join(baseDir, `frame_${numStr}.jpg`), jpegOut);
      fs.writeFileSync(path.join(distDir, `frame_${numStr}.jpg`), jpegOut);
      console.log(`  Processed frame_${numStr}`);
    }
  }

  const dt = ((Date.now() - t0) / 1000).toFixed(1);
  console.log(`--- FINISHED ALL 40 4K FRAMES IN ${dt}s ---`);
}

compositeAllFrames().catch(console.error);