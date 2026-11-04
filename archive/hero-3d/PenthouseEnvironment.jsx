import React, { useMemo } from 'react';
import { MeshReflectorMaterial } from '@react-three/drei';
import * as THREE from 'three';

// Cached texture singletons
let cachedMarbleTex = null;
let cachedCityTex = null;

// ============================================================
// Helper: Procedural Nero Marquina Marble Floor Texture (Canvas 2D)
// ============================================================
function createMarbleTexture() {
  if (typeof document === 'undefined') return null;
  if (cachedMarbleTex) return cachedMarbleTex;

  const canvas = document.createElement('canvas');
  canvas.width = 512;
  canvas.height = 512;
  const ctx = canvas.getContext('2d');
  const scale = 512 / 2048; // Proportional 0.25 scale factor

  // Fast typed-array noise buffer for Nero Marquina marble surface
  const imgData = ctx.createImageData(canvas.width, canvas.height);
  const data = new Uint32Array(imgData.data.buffer);
  const total = canvas.width * canvas.height;
  for (let i = 0; i < total; i++) {
    const n = (Math.random() * 8) | 0;
    // Base #1a1918 with subtle micro-grain: R=24+n, G=23+n, B=22+n, A=255
    data[i] = (255 << 24) | ((22 + n) << 16) | ((23 + n) << 8) | (24 + n);
  }
  ctx.putImageData(imgData, 0, 0);

  // Organic marble veins — bezier curves with branching (scaled proportionally)
  const drawVein = (sx, sy, ex, ey, width, opacity) => {
    ctx.beginPath();
    ctx.moveTo(sx, sy);
    const cp1x = sx + (ex - sx) * 0.3 + (Math.random() - 0.5) * (300 * scale);
    const cp1y = sy + (ey - sy) * 0.3 + (Math.random() - 0.5) * (300 * scale);
    const cp2x = sx + (ex - sx) * 0.7 + (Math.random() - 0.5) * (300 * scale);
    const cp2y = sy + (ey - sy) * 0.7 + (Math.random() - 0.5) * (300 * scale);
    ctx.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, ex, ey);
    ctx.strokeStyle = `rgba(185, 180, 172, ${opacity})`;
    ctx.lineWidth = width;
    ctx.stroke();
  };

  // Major veins with branches
  for (let i = 0; i < 8; i++) {
    const sx = Math.random() * canvas.width;
    const sy = Math.random() * canvas.height;
    const angle = Math.random() * Math.PI;
    const length = (600 + Math.random() * 1200) * scale;
    const ex = sx + Math.cos(angle) * length;
    const ey = sy + Math.sin(angle) * length;
    drawVein(sx, sy, ex, ey, (1.5 + Math.random() * 2.5) * scale, 0.25 + Math.random() * 0.3);

    // Branch veins from main trunk
    for (let j = 0; j < 4; j++) {
      const t = 0.15 + Math.random() * 0.7;
      const bx = sx + (ex - sx) * t;
      const by = sy + (ey - sy) * t;
      const bAngle = angle + (Math.random() - 0.5) * 1.4;
      const bLen = (150 + Math.random() * 350) * scale;
      drawVein(bx, by, bx + Math.cos(bAngle) * bLen, by + Math.sin(bAngle) * bLen, (0.4 + Math.random() * 1.0) * scale, 0.12 + Math.random() * 0.18);
    }
  }

  // Fine hairline micro-veins
  for (let i = 0; i < 20; i++) {
    const sx = Math.random() * canvas.width;
    const sy = Math.random() * canvas.height;
    const angle = Math.random() * Math.PI * 2;
    const length = (100 + Math.random() * 400) * scale;
    drawVein(sx, sy, sx + Math.cos(angle) * length, sy + Math.sin(angle) * length, (0.3 + Math.random() * 0.5) * scale, 0.06 + Math.random() * 0.1);
  }

  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = THREE.RepeatWrapping;
  tex.wrapT = THREE.RepeatWrapping;
  tex.repeat.set(4, 4);
  tex.needsUpdate = true;
  cachedMarbleTex = tex;
  return tex;
}

// ============================================================
// Helper: Procedural Night City Skyline Panorama (Canvas 2D)
// ============================================================
function createCitySkylineTexture() {
  if (typeof document === 'undefined') return null;
  if (cachedCityTex) return cachedCityTex;
  const canvas = document.createElement('canvas');
  canvas.width = 1024;
  canvas.height = 512;
  const ctx = canvas.getContext('2d');

  // Night sky gradient
  const skyGrad = ctx.createLinearGradient(0, 0, 0, canvas.height);
  skyGrad.addColorStop(0, '#060810');
  skyGrad.addColorStop(0.25, '#0c1020');
  skyGrad.addColorStop(0.55, '#151c30');
  skyGrad.addColorStop(1, '#1e2438');
  ctx.fillStyle = skyGrad;
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  // Stars
  for (let i = 0; i < 180; i++) {
    const x = Math.random() * canvas.width;
    const y = Math.random() * canvas.height * 0.45;
    const size = 0.5 + Math.random() * 1.2;
    ctx.fillStyle = `rgba(255, 255, 255, ${0.25 + Math.random() * 0.5})`;
    ctx.beginPath();
    ctx.arc(x, y, size, 0, Math.PI * 2);
    ctx.fill();
  }

  // Far background buildings — dense urban fabric
  const bgColors = ['#161c2a', '#1a2030', '#141826', '#1e2436'];
  for (let i = 0; i < 70; i++) {
    const x = (i / 70) * canvas.width - 15;
    const w = 18 + Math.random() * 40;
    const h = 80 + Math.random() * 280;
    const y = canvas.height - h;
    ctx.fillStyle = bgColors[Math.floor(Math.random() * bgColors.length)];
    ctx.fillRect(x, y, w, h);

    // Lit windows — warm amber/white randomised
    for (let wy = y + 6; wy < canvas.height - 8; wy += 10 + Math.random() * 5) {
      for (let wx = x + 3; wx < x + w - 3; wx += 6 + Math.random() * 4) {
        if (Math.random() > 0.35) {
          const warmth = Math.random();
          const r = Math.floor(200 + warmth * 55);
          const g = Math.floor(155 + warmth * 65);
          const b = Math.floor(70 + warmth * 70);
          ctx.fillStyle = `rgba(${r}, ${g}, ${b}, ${0.45 + Math.random() * 0.55})`;
          ctx.fillRect(wx, wy, 2.5, 3.5);
        }
      }
    }
  }

  // Tall landmark skyscrapers
  for (let i = 0; i < 10; i++) {
    const x = 80 + Math.random() * (canvas.width - 160);
    const w = 22 + Math.random() * 35;
    const h = 380 + Math.random() * 400;
    const y = canvas.height - h;

    ctx.fillStyle = '#141a28';
    ctx.fillRect(x, y, w, h);

    // Spire / antenna
    if (Math.random() > 0.4) {
      const spireH = 25 + Math.random() * 20;
      ctx.fillStyle = '#1c2438';
      ctx.fillRect(x + w / 2 - 1, y - spireH, 2, spireH);
      // Aircraft warning light
      ctx.fillStyle = 'rgba(255, 60, 60, 0.8)';
      ctx.beginPath();
      ctx.arc(x + w / 2, y - spireH, 2, 0, Math.PI * 2);
      ctx.fill();
    }

    // Windows
    for (let wy = y + 5; wy < canvas.height - 8; wy += 9) {
      for (let wx = x + 3; wx < x + w - 3; wx += 6) {
        if (Math.random() > 0.28) {
          const warmth = Math.random();
          ctx.fillStyle = `rgba(${195 + Math.floor(warmth * 60)}, ${165 + Math.floor(warmth * 55)}, ${90 + Math.floor(warmth * 90)}, ${0.4 + Math.random() * 0.6})`;
          ctx.fillRect(wx, wy, 2.5, 3.5);
        }
      }
    }
  }

  // City haze at horizon — warm #757164 taupe
  const hazeGrad = ctx.createLinearGradient(0, canvas.height * 0.55, 0, canvas.height);
  hazeGrad.addColorStop(0, 'rgba(117, 113, 100, 0)');
  hazeGrad.addColorStop(0.6, 'rgba(117, 113, 100, 0.12)');
  hazeGrad.addColorStop(1, 'rgba(117, 113, 100, 0.28)');
  ctx.fillStyle = hazeGrad;
  ctx.fillRect(0, canvas.height * 0.55, canvas.width, canvas.height * 0.45);

  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = THREE.ClampToEdgeWrapping;
  tex.needsUpdate = true;
  cachedCityTex = tex;
  return tex;
}

// ============================================================
// Green #69DF0A Neon Ceiling Strip — Sculptural tube light
// ============================================================
function NeonCeilingStrip() {
  const tubeGeo = useMemo(() => {
    const points = [
      new THREE.Vector3(-10, 8.72, -8),
      new THREE.Vector3(-5, 8.72, -5.5),
      new THREE.Vector3(1, 8.72, -3),
      new THREE.Vector3(6, 8.72, -0.5),
      new THREE.Vector3(10, 8.72, 3),
      new THREE.Vector3(9, 8.72, 7),
      new THREE.Vector3(5, 8.72, 9),
    ];
    const curve = new THREE.CatmullRomCurve3(points);
    return new THREE.TubeGeometry(curve, 80, 0.05, 8, false);
  }, []);

  return (
    <group>
      {/* Neon tube mesh */}
      <mesh geometry={tubeGeo}>
        <meshBasicMaterial color="#69DF0A" toneMapped={false} />
      </mesh>
      {/* Glow point lights distributed along the strip path */}
      <pointLight position={[-7, 8.4, -6.5]} color="#69DF0A" intensity={1.8} distance={6} />
      <pointLight position={[1, 8.4, -2]} color="#69DF0A" intensity={2.0} distance={6} />
      <pointLight position={[9, 8.4, 5]} color="#69DF0A" intensity={1.5} distance={5} />
    </group>
  );
}

// ============================================================
// Reflective Nero Marquina Marble Floor
// ============================================================
export function MarbleFloor() {
  const marbleTex = useMemo(() => createMarbleTexture(), []);

  return (
    <mesh position={[0, -0.01, 0]} rotation={[-Math.PI / 2, 0, 0]}>
      <planeGeometry args={[50, 50]} />
      <MeshReflectorMaterial
        map={marbleTex}
        blur={[200, 80]}
        resolution={512}
        mirror={0.75}
        mixBlur={0.55}
        mixStrength={42}
        roughness={0.08}
        depthScale={1.0}
        minDepthThreshold={0.4}
        maxDepthThreshold={1.4}
        color="#1a1918"
        metalness={0.88}
      />
    </mesh>
  );
}

// ============================================================
// Penthouse Showroom — Glass walls, crimson wall, ceiling, city
// ============================================================
export default function PenthouseShowroom() {
  const cityTex = useMemo(() => createCitySkylineTexture(), []);

  return (
    <group>
      {/* =====================================================
          CONCRETE CEILING SLAB
      ===================================================== */}
      <mesh position={[0, 9, 0]}>
        <boxGeometry args={[30, 0.35, 26]} />
        <meshStandardMaterial color="#2a2825" roughness={0.85} metalness={0.05} />
      </mesh>

      {/* Upper mezzanine level visible through a ceiling cutout */}
      <mesh position={[-5, 9.65, -5]}>
        <boxGeometry args={[10, 0.9, 8]} />
        <meshStandardMaterial color="#252320" roughness={0.8} metalness={0.1} />
      </mesh>
      {/* Cutout edge trims — dark chrome */}
      <mesh position={[-5, 9.18, -1]}>
        <boxGeometry args={[10.4, 0.04, 0.08]} />
        <meshStandardMaterial color="#1a1c22" roughness={0.25} metalness={0.88} />
      </mesh>
      <mesh position={[-10, 9.18, -5]}>
        <boxGeometry args={[0.08, 0.04, 8.1]} />
        <meshStandardMaterial color="#1a1c22" roughness={0.25} metalness={0.88} />
      </mesh>
      <mesh position={[0, 9.18, -5]}>
        <boxGeometry args={[0.08, 0.04, 8.1]} />
        <meshStandardMaterial color="#1a1c22" roughness={0.25} metalness={0.88} />
      </mesh>

      {/* Track lighting rails on ceiling */}
      <mesh position={[2, 8.78, -3]}>
        <boxGeometry args={[12, 0.04, 0.04]} />
        <meshStandardMaterial color="#1a1c22" roughness={0.3} metalness={0.9} />
      </mesh>
      <mesh position={[2, 8.78, 4]}>
        <boxGeometry args={[12, 0.04, 0.04]} />
        <meshStandardMaterial color="#1a1c22" roughness={0.3} metalness={0.9} />
      </mesh>

      {/* =====================================================
          BACK WALL — Dark with Crimson #C3004F Feature Panel
      ===================================================== */}
      {/* Full back wall */}
      <mesh position={[0, 4.5, -12]}>
        <boxGeometry args={[28, 9, 0.3]} />
        <meshStandardMaterial color="#1a1816" roughness={0.75} metalness={0.08} />
      </mesh>

      {/* Crimson illuminated feature panel behind the car */}
      <mesh position={[0, 4.8, -11.7]}>
        <boxGeometry args={[6.5, 7.5, 0.1]} />
        <meshBasicMaterial color="#C3004F" toneMapped={false} />
      </mesh>
      {/* Thin dark frame around crimson panel — top */}
      <mesh position={[0, 8.6, -11.65]}>
        <boxGeometry args={[6.8, 0.08, 0.03]} />
        <meshStandardMaterial color="#0e0c0a" roughness={0.3} metalness={0.9} />
      </mesh>
      {/* Frame — bottom */}
      <mesh position={[0, 1.02, -11.65]}>
        <boxGeometry args={[6.8, 0.08, 0.03]} />
        <meshStandardMaterial color="#0e0c0a" roughness={0.3} metalness={0.9} />
      </mesh>
      {/* Frame — left */}
      <mesh position={[-3.3, 4.8, -11.65]}>
        <boxGeometry args={[0.08, 7.7, 0.03]} />
        <meshStandardMaterial color="#0e0c0a" roughness={0.3} metalness={0.9} />
      </mesh>
      {/* Frame — right */}
      <mesh position={[3.3, 4.8, -11.65]}>
        <boxGeometry args={[0.08, 7.7, 0.03]} />
        <meshStandardMaterial color="#0e0c0a" roughness={0.3} metalness={0.9} />
      </mesh>

      {/* Crimson wall glow — SpotLight pointing into room */}
      <spotLight
        position={[0, 7.5, -9.5]}
        target-position={[0, 1.5, -11]}
        color="#C3004F"
        intensity={4.5}
        angle={0.65}
        penumbra={0.85}
        distance={12}
      />
      {/* Fill light bleeding from crimson wall */}
      <pointLight position={[0, 3, -9]} color="#C3004F" intensity={2.5} distance={8} />

      {/* =====================================================
          LEFT SOLID WALL
      ===================================================== */}
      <mesh position={[-14, 4.5, 0]}>
        <boxGeometry args={[0.3, 9, 24]} />
        <meshStandardMaterial color="#1e1c1a" roughness={0.75} metalness={0.08} />
      </mesh>

      {/* =====================================================
          GLASS WALL PANELS — Right Side (5 panels)
      ===================================================== */}
      {[0, 1, 2, 3, 4].map((i) => (
        <group key={`glass-right-${i}`}>
          {/* Glass pane */}
          <mesh position={[14, 4.5, -10 + i * 5]}>
            <boxGeometry args={[0.06, 9, 4.5]} />
            <meshPhysicalMaterial
              color="#8a939e"
              transmission={0.75}
              roughness={0.08}
              metalness={0.05}
              transparent
              opacity={0.28}
              side={THREE.DoubleSide}
            />
          </mesh>
          {/* Vertical mullion — dark chrome frame */}
          <mesh position={[14, 4.5, -12.25 + i * 5]}>
            <boxGeometry args={[0.14, 9.1, 0.07]} />
            <meshStandardMaterial color="#1a1c22" roughness={0.25} metalness={0.88} />
          </mesh>
        </group>
      ))}
      {/* Final right-side vertical mullion */}
      <mesh position={[14, 4.5, 12.75]}>
        <boxGeometry args={[0.14, 9.1, 0.07]} />
        <meshStandardMaterial color="#1a1c22" roughness={0.25} metalness={0.88} />
      </mesh>
      {/* Horizontal mullion — right wall mid-height */}
      <mesh position={[14, 4.5, 0]}>
        <boxGeometry args={[0.14, 0.06, 25.1]} />
        <meshStandardMaterial color="#1a1c22" roughness={0.25} metalness={0.88} />
      </mesh>

      {/* =====================================================
          GLASS WALL PANELS — Front Side (3 panels)
      ===================================================== */}
      {[0, 1, 2].map((i) => (
        <group key={`glass-front-${i}`}>
          <mesh position={[-3 + i * 7, 4.5, 12]}>
            <boxGeometry args={[6.5, 9, 0.06]} />
            <meshPhysicalMaterial
              color="#8a939e"
              transmission={0.75}
              roughness={0.08}
              metalness={0.05}
              transparent
              opacity={0.28}
              side={THREE.DoubleSide}
            />
          </mesh>
          {/* Vertical mullion */}
          <mesh position={[-6.25 + i * 7, 4.5, 12]}>
            <boxGeometry args={[0.07, 9.1, 0.14]} />
            <meshStandardMaterial color="#1a1c22" roughness={0.25} metalness={0.88} />
          </mesh>
        </group>
      ))}
      {/* Final front vertical mullion */}
      <mesh position={[14.75, 4.5, 12]}>
        <boxGeometry args={[0.07, 9.1, 0.14]} />
        <meshStandardMaterial color="#1a1c22" roughness={0.25} metalness={0.88} />
      </mesh>
      {/* Horizontal mullion — front wall mid-height */}
      <mesh position={[4, 4.5, 12]}>
        <boxGeometry args={[22, 0.06, 0.14]} />
        <meshStandardMaterial color="#1a1c22" roughness={0.25} metalness={0.88} />
      </mesh>

      {/* Corner junction post — right-front corner */}
      <mesh position={[14, 4.5, 12]}>
        <boxGeometry args={[0.16, 9.1, 0.16]} />
        <meshStandardMaterial color="#151820" roughness={0.2} metalness={0.92} />
      </mesh>

      {/* =====================================================
          CITY SKYLINE BACKDROP — Behind glass walls
      ===================================================== */}
      {/* Behind right glass wall */}
      <mesh position={[16.5, 5, 0]} rotation={[0, -Math.PI / 2, 0]}>
        <planeGeometry args={[28, 12]} />
        {cityTex ? (
          <meshBasicMaterial map={cityTex} toneMapped={false} />
        ) : (
          <meshBasicMaterial color="#111828" />
        )}
      </mesh>
      {/* Behind front glass wall */}
      <mesh position={[4, 5, 14.5]} rotation={[0, Math.PI, 0]}>
        <planeGeometry args={[28, 12]} />
        {cityTex ? (
          <meshBasicMaterial map={cityTex} toneMapped={false} />
        ) : (
          <meshBasicMaterial color="#111828" />
        )}
      </mesh>

      {/* =====================================================
          GREEN #69DF0A NEON CEILING STRIP
      ===================================================== */}
      <NeonCeilingStrip />

      {/* =====================================================
          RECESSED CEILING SPOTLIGHTS (5 positions)
      ===================================================== */}
      {[[-5, 8.78, -3], [5, 8.78, -3], [-5, 8.78, 4], [5, 8.78, 4], [0, 8.78, 0.5]].map((pos, i) => (
        <group key={`ceiling-spot-${i}`}>
          {/* Recessed can housing */}
          <mesh position={pos}>
            <cylinderGeometry args={[0.12, 0.16, 0.06, 12]} />
            <meshStandardMaterial color="#2a2825" roughness={0.5} metalness={0.3} />
          </mesh>
          {/* Bulb face */}
          <mesh position={[pos[0], pos[1] - 0.035, pos[2]]}>
            <cylinderGeometry args={[0.07, 0.07, 0.015, 10]} />
            <meshBasicMaterial color="#fff5e6" toneMapped={false} />
          </mesh>
          {/* Actual SpotLight casting down */}
          <spotLight
            position={pos}
            target-position={[pos[0], 0, pos[2]]}
            color="#f5f0e8"
            intensity={3.5}
            angle={0.55}
            penumbra={0.8}
            distance={11}
          />
        </group>
      ))}

      {/* =====================================================
          WARM CITY AMBIENT FILL — Fakes GI from glass walls
      ===================================================== */}
      <hemisphereLight color="#757164" groundColor="#0a0b0e" intensity={0.3} />
    </group>
  );
}
