import React, { useRef, useEffect, useMemo, useState } from 'react';
import { Canvas, useFrame, useThree } from '@react-three/fiber';
import { useGLTF, PerspectiveCamera, OrbitControls } from '@react-three/drei';
import * as THREE from 'three';
import gsap from 'gsap';
import PenthouseShowroom, { MarbleFloor } from './PenthouseEnvironment';

// Preload and cache module-level assets
const texLoader = new THREE.TextureLoader();
const taillightMask = texLoader.load('/models/taillight_emissive_mask.png');
taillightMask.flipY = false;

// Preload the GLTF Model with local Draco decoder
useGLTF.preload('/models/2022_mclaren_765lt.glb', '/draco/gltf/');

// 2. McLaren 765LT Component with Dynamic Drive-in Animation, Spinning Wheels, and Emissive Lights
function McLaren({ isActive }) {
  const { scene } = useGLTF('/models/2022_mclaren_765lt.glb', '/draco/gltf/');
  const carRef = useRef();
  const hasAnimated = useRef(false);
  const isMovingRef = useRef(false);
  const wheelsRef = useRef([]);

  // Setup materials (headlights, tail lights) and find wheel nodes
  const clonedScene = useMemo(() => {
    const cloned = scene.clone(true);

    // 1. Configure Lights Materials (Front LED DRL / Projectors and Rear Tail Glow)
    cloned.traverse((child) => {
      if (child.isMesh && child.material && child.material.name === 'McLaren_765LT_2021LightA_Material') {
        const geo = child.geometry;
        
        // Fast-path: If geometry was already split, simply re-attach the cached materials
        if (geo._isSplitDone && geo._cachedMaterials) {
          child.material = geo._cachedMaterials;
          return;
        }

        const pos = geo.attributes.position;
        const index = geo.index;

        if (index && pos) {
          // Split geometry into:
          // 0: Front Headlights
          // 1: Rear Taillights (Upper blades + Lower fog/reverse lamps - all 4 rear lights)
          // 2: Front Side Markers (Amber glowing reflector strip)
          // 3: Rear Side Markers (Red glowing marker strip)
          const frontIndices = [];
          const rearIndices = [];
          const frontSideIndices = [];
          const rearSideIndices = [];

          for (let i = 0; i < index.count; i += 3) {
            const a = index.getX(i);
            const b = index.getX(i + 1);
            const c = index.getX(i + 2);
            const avgZ = (pos.getZ(a) + pos.getZ(b) + pos.getZ(c)) / 3;
            const avgX = (pos.getX(a) + pos.getX(b) + pos.getX(c)) / 3;
            const avgY = (pos.getY(a) + pos.getY(b) + pos.getY(c)) / 3;
            const absX = Math.abs(avgX);

            if (avgZ > 0) {
              // Front side markers are on outer front fender flares (|X| > 0.88)
              if (absX > 0.88 && avgZ > 1.3 && avgY < 0.68) {
                frontSideIndices.push(a, b, c);
              } else {
                frontIndices.push(a, b, c);
              }
            } else {
              // Rear side markers are strictly on the outer quarter panel (|X| > 0.94, Y: 0.50..0.68, Z: -1.82..-1.68)
              if (absX > 0.94 && avgY > 0.50 && avgY < 0.68 && avgZ < -1.65 && avgZ > -1.85) {
                rearSideIndices.push(a, b, c);
              } else {
                rearIndices.push(a, b, c);
              }
            }
          }

          geo.clearGroups();
          geo.addGroup(0, frontIndices.length, 0);
          geo.addGroup(frontIndices.length, rearIndices.length, 1);
          geo.addGroup(frontIndices.length + rearIndices.length, frontSideIndices.length, 2);
          geo.addGroup(frontIndices.length + rearIndices.length + frontSideIndices.length, rearSideIndices.length, 3);

          const reorderedIndices = new Uint32Array([
            ...frontIndices,
            ...rearIndices,
            ...frontSideIndices,
            ...rearSideIndices
          ]);
          geo.setIndex(new THREE.BufferAttribute(reorderedIndices, 1));

          // Preserve original high-res texture and use module-cached mask
          const origTexture = child.material.map || null;

          // 1. FRONT HEADLIGHTS: Full Illumination of All Projectors, DRL & Lower Blade
          const frontLightMat = new THREE.MeshStandardMaterial({
            map: origTexture,
            color: '#ffffff',
            roughness: 0.1,
            metalness: 0.2,
            side: THREE.DoubleSide
          });

          frontLightMat.onBeforeCompile = (shader) => {
            shader.fragmentShader = shader.fragmentShader.replace(
              '#include <dithering_fragment>',
              `
              #include <dithering_fragment>
              #ifdef USE_MAP
                vec4 tex = texture2D(map, vMapUv);
                float lum = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
                if (lum > 0.08) {
                  float norm = (lum - 0.08) / 0.92;
                  float glow = 2.6 + pow(norm, 1.2) * 9.0;
                  vec3 lightColor = mix(vec3(0.75, 0.88, 1.0), vec3(1.0, 1.0, 1.0), norm);
                  gl_FragColor.rgb += lightColor * glow;
                }
              #endif
              `
            );
          };

          // 2. REAR TAILLIGHTS: Powered by taillight_emissive_mask for exact per-diode & reflector texture detail
          const rearLightMat = new THREE.MeshStandardMaterial({
            map: origTexture,
            color: '#ffffff',
            roughness: 0.15,
            metalness: 0.1,
            side: THREE.DoubleSide
          });

          rearLightMat.onBeforeCompile = (shader) => {
            shader.uniforms.taillightMask = { value: taillightMask };
            shader.fragmentShader = `
              uniform sampler2D taillightMask;
            ` + shader.fragmentShader;

            shader.fragmentShader = shader.fragmentShader.replace(
              '#include <dithering_fragment>',
              `
              #include <dithering_fragment>
              #ifdef USE_MAP
                vec4 maskTex = texture2D(taillightMask, vMapUv);
                vec4 origTex = texture2D(map, vMapUv);
                
                // Use the precise extracted taillight emissive mask
                float maskVal = maskTex.r;
                float origLum = dot(origTex.rgb, vec3(0.299, 0.587, 0.114));
                
                if (maskVal > 0.04 || origLum > 0.06) {
                  float val = max(maskVal, clamp((origLum - 0.06) / 0.94, 0.0, 1.0));
                  float glow = 3.2 + pow(val, 1.1) * 9.0;
                  
                  // Color discrimination:
                  // Bottom reverse diffuser lamps (U > 0.88 & V < 0.15) emit bright xenon ice-white
                  vec3 lightColor;
                  if (vMapUv.x > 0.88 && vMapUv.y < 0.15) {
                    lightColor = mix(vec3(1.0, 0.88, 0.85), vec3(1.0, 1.0, 1.0), val);
                  } else {
                    // Curved blade, individual diodes, middle top and reflectors emit deep crimson with scarlet glow
                    lightColor = mix(vec3(0.95, 0.02, 0.05), vec3(1.0, 0.35, 0.35), val);
                  }
                  
                  // Blend illumination while keeping texture grain & diode definition intact
                  gl_FragColor.rgb = origTex.rgb * 0.4 + lightColor * glow * (0.3 + 0.7 * val);
                }
              #endif
              `
            );
          };

          // 3. FRONT SIDE MARKERS: Vivid Amber / Tangerine Marker Lights
          const frontSideMarkerMat = new THREE.MeshStandardMaterial({
            map: origTexture,
            color: '#ffffff',
            roughness: 0.15,
            metalness: 0.1,
            side: THREE.DoubleSide
          });

          frontSideMarkerMat.onBeforeCompile = (shader) => {
            shader.fragmentShader = shader.fragmentShader.replace(
              '#include <dithering_fragment>',
              `
              #include <dithering_fragment>
              #ifdef USE_MAP
                vec4 tex = texture2D(map, vMapUv);
                float lum = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
                if (lum > 0.05) {
                  float norm = clamp((lum - 0.05) / 0.95, 0.0, 1.0);
                  float glow = 2.8 + pow(norm, 1.2) * 7.5;
                  vec3 amberColor = mix(vec3(1.0, 0.45, 0.0), vec3(1.0, 0.7, 0.2), norm);
                  gl_FragColor.rgb = tex.rgb * 0.35 + amberColor * glow * (0.3 + 0.7 * norm);
                }
              #endif
              `
            );
          };

          // 4. REAR SIDE MARKERS: Ruby Red Marker Optics with Preserved Micro-Reflector Texture
          const rearSideMarkerMat = new THREE.MeshStandardMaterial({
            map: origTexture,
            color: '#ffffff',
            roughness: 0.15,
            metalness: 0.1,
            side: THREE.DoubleSide
          });

          rearSideMarkerMat.onBeforeCompile = (shader) => {
            shader.uniforms.taillightMask = { value: taillightMask };
            shader.fragmentShader = `
              uniform sampler2D taillightMask;
            ` + shader.fragmentShader;

            shader.fragmentShader = shader.fragmentShader.replace(
              '#include <dithering_fragment>',
              `
              #include <dithering_fragment>
              #ifdef USE_MAP
                vec4 maskTex = texture2D(taillightMask, vMapUv);
                vec4 origTex = texture2D(map, vMapUv);
                float maskVal = maskTex.r;
                float origLum = dot(origTex.rgb, vec3(0.299, 0.587, 0.114));
                
                if (maskVal > 0.04 || origLum > 0.05) {
                  float norm = max(maskVal, clamp((origLum - 0.05) / 0.95, 0.0, 1.0));
                  float glow = 2.8 + pow(norm, 1.2) * 7.0;
                  vec3 redColor = mix(vec3(0.95, 0.02, 0.05), vec3(1.0, 0.28, 0.28), norm);
                  gl_FragColor.rgb = origTex.rgb * 0.35 + redColor * glow * (0.3 + 0.7 * norm);
                }
              #endif
              `
            );
          };

          const mats = [frontLightMat, rearLightMat, frontSideMarkerMat, rearSideMarkerMat];
          geo._cachedMaterials = mats;
          geo._isSplitDone = true;
          child.material = mats;
        }
      } else if (child.isMesh && child.material && child.material.name === 'McLaren_765LT_2021Window_Material') {
        // Ensure transparent window/lens glass allows glowing light structures inside to shine through without z-fighting
        child.material.transparent = true;
        child.material.depthWrite = false;
      }
    });

    // 2. Identify Wheel children for rotation
    const wheelRoot = cloned.getObjectByName('Combined3DWheel_3DWheel_Front_L Instance1_Src4') || 
                      cloned.getObjectByName('Combined3DWheel_3DWheel_Front_L_Instance1_Src4');
    if (wheelRoot && wheelRoot.children) {
      wheelsRef.current = wheelRoot.children;
    }

    return cloned;
  }, [scene]);

  useEffect(() => {
    if (!carRef.current) return;

    if (isActive && !hasAnimated.current) {
      hasAnimated.current = true;
      isMovingRef.current = true;

      // Start off-camera to the left and drive in
      carRef.current.position.set(-15, 0, -1);
      gsap.to(carRef.current.position, {
        x: -1.8,
        y: 0,
        z: 2.0,
        duration: 2.4,
        ease: 'power3.out',
        delay: 0.15,
        onComplete: () => {
          isMovingRef.current = false;
        }
      });
    } else if (!hasAnimated.current) {
      carRef.current.position.set(-1.8, 0, 2.0);
    }
  }, [isActive]);

  // Animate wheels spinning along X axis while the car is driving in
  useFrame((_, delta) => {
    if (isMovingRef.current && wheelsRef.current.length > 0) {
      const spinSpeed = 16.0 * delta;
      for (let i = 0; i < wheelsRef.current.length; i++) {
        wheelsRef.current[i].rotateX(spinSpeed);
      }
    }
  });

  return (
    <group ref={carRef} position={[-1.8, 0, 2.0]} rotation={[0, Math.PI * 0.23, 0]}>
      {/* 140x scale gives realistic dominant supercar proportion matching workstation and studio camera */}
      <primitive object={clonedScene} scale={140} />
      
      {/* ------------------------------------------------------------- */}
      {/* FRONT HEADLIGHTS: DUAL PROJECTOR BEAMS & ROAD ILLUMINATION   */}
      {/* ------------------------------------------------------------- */}
      {/* Left Xenon Projector Light Beam with soft penumbra falloff */}
      <spotLight
        position={[-1.04, 0.77, 2.46]}
        target-position={[-1.3, 0, 9.5]}
        color="#c7d2fe"
        intensity={14}
        angle={0.52}
        penumbra={0.85}
        distance={24}
      />
      {/* Right Xenon Projector Light Beam with soft penumbra falloff */}
      <spotLight
        position={[1.04, 0.77, 2.46]}
        target-position={[1.3, 0, 9.5]}
        color="#c7d2fe"
        intensity={14}
        angle={0.52}
        penumbra={0.85}
        distance={24}
      />

      {/* Direct Floor Light Reflection beneath front bumper */}
      <pointLight position={[0, 0.35, 3.4]} color="#dbeafe" intensity={4.5} distance={6.5} />

      {/* ------------------------------------------------------------- */}
      {/* REAR LIGHTS: DUAL UPPER BLADES, MIDDLE TOP & DUAL LOWER LAMPS */}
      {/* ------------------------------------------------------------- */}
      {/* 1. Upper Left Curved LED Blade Light Beam */}
      <spotLight
        position={[-0.90, 1.10, -2.78]}
        target-position={[-1.3, 0, -8.0]}
        color="#ef4444"
        intensity={9.0}
        angle={0.68}
        penumbra={0.8}
        distance={14}
      />
      {/* 2. Upper Right Curved LED Blade Light Beam */}
      <spotLight
        position={[0.90, 1.10, -2.78]}
        target-position={[1.3, 0, -8.0]}
        color="#ef4444"
        intensity={9.0}
        angle={0.68}
        penumbra={0.8}
        distance={14}
      />
      
      {/* 3. Middle Top Center Accent / High Mount Light */}
      <pointLight position={[0, 1.12, -2.96]} color="#ff3333" intensity={3.8} distance={4.5} />
    </group>
  );
}

// Cached canvas texture singleton
let cachedFZLogoTex = null;

// Helper: FZ Logo Canvas Texture for the monitor screen (Optimized 512x256)
function createFZLogoTexture() {
  if (typeof document === 'undefined') return null;
  if (cachedFZLogoTex) return cachedFZLogoTex;
  const canvas = document.createElement('canvas');
  canvas.width = 512;
  canvas.height = 256;
  const ctx = canvas.getContext('2d');

  // Deep dark background — VS Code-like IDE
  ctx.fillStyle = '#0d0f17';
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  // Titlebar
  ctx.fillStyle = '#161820';
  ctx.fillRect(0, 0, canvas.width, 22);
  // Traffic light dots
  const dots = ['#ff5f56', '#ffbd2e', '#27c93f'];
  dots.forEach((c, i) => {
    ctx.fillStyle = c;
    ctx.beginPath(); ctx.arc(11 + i * 11, 11, 3, 0, Math.PI * 2); ctx.fill();
  });
  // Tab
  ctx.fillStyle = '#0d0f17';
  ctx.fillRect(45, 3, 65, 16);
  ctx.fillStyle = '#5c6374';
  ctx.font = '7px monospace';
  ctx.fillText('index.jsx', 50, 13);

  // Sidebar
  ctx.fillStyle = '#11131c';
  ctx.fillRect(0, 22, 28, canvas.height);

  // Line numbers gutter
  ctx.fillStyle = '#1a1d28';
  ctx.fillRect(28, 22, 25, canvas.height);

  // Code lines with syntax highlighting
  const lineHeight = 11;
  const startY = 32;
  const codeSnippet = [
    [['import ', '#c792ea'], ['React ', '#ffffff'], ['from ', '#c792ea'], ['"react"', '#c3e88d']],
    [['import ', '#c792ea'], ['{ ', '#ffffff'], ['motion ', '#89ddff'], ['} ', '#ffffff'], ['from ', '#c792ea'], ['"framer-motion"', '#c3e88d']],
    [['']],
    [['const ', '#c792ea'], ['Portfolio ', '#82aaff'], ['= ', '#ffffff'], ['() => {', '#ffffff']],
    [['  return ', '#c792ea'], ['(', '#ffffff']],
    [['    <', '#89ddff'], ['section ', '#f07178'], ['className', '#c792ea'], ['=', '#ffffff'], ['"hero"', '#c3e88d'], ['>', '#89ddff']],
    [['      <', '#89ddff'], ['h1', '#f07178'], ['> FZ Studio </'], ['h1', '#f07178'], ['>', '#89ddff']],
    [['    </', '#89ddff'], ['section', '#f07178'], ['>', '#89ddff']],
    [['  )', '#ffffff']],
    [['}', '#ffffff']],
    [['']],
    [['export ', '#c792ea'], ['default ', '#c792ea'], ['Portfolio', '#82aaff']],
  ];

  codeSnippet.forEach((line, li) => {
    let x = 60;
    const y = startY + li * lineHeight;
    // Line number
    ctx.fillStyle = '#3d4257';
    ctx.font = '6.5px monospace';
    ctx.fillText(String(li + 1), 31, y);
    // Code tokens
    line.forEach(([text, color]) => {
      ctx.fillStyle = color || '#ffffff';
      ctx.font = '7px monospace';
      ctx.fillText(text, x, y);
      x += ctx.measureText(text).width;
    });
  });

  // BIG CENTERED FZ MONOGRAM on right panel area
  const logoX = 360;
  const logoY = 70;
  const scale = 1.5;

  ctx.save();
  ctx.translate(logoX, logoY);
  ctx.scale(scale, scale);

  // Glow pass
  ctx.shadowColor = '#A30033';
  ctx.shadowBlur = 14;

  const pathWingDown = new Path2D('M43.82,70.3l64.52.05,5.42-12.76-32.78-.36,41.09-19.08,7.56-18.43,27.69.85-25.84,61.15c-2.63,6.23-11.43,8.64-18.18,8.65l-111.12.13,41.64-20.2Z');
  const pathWingUp = new Path2D('M129.63,19.71l-65.22.74-5.49,12.65,32.87.55-41.14,19.05-6.83,17.6-28.38-.02L41.22,9.13C43.67,3.32,52.08.55,58.48.55l110.96-.05-39.82,19.21Z');

  ctx.translate(-85, -45);
  ctx.fillStyle = '#A30033';
  ctx.fill(pathWingDown);
  ctx.fill(pathWingUp);

  const whiteInner = new Path2D('M128.13,20.73l-6.84,16.67-40.73,18.92-4,1.86,4.41.05,31.28.34-4.57,10.78-62.4-.05,6.16-15.86,40.77-18.88,3.96-1.83-4.36-.07-31.37-.53,4.64-10.68,63.06-.71M129.63,19.71l-65.22.74-5.49,12.65,32.87.55-41.14,19.05-6.83,17.6,64.52.05,5.42-12.76-32.78-.36,41.09-19.08,7.56-18.43h0Z');
  ctx.fill(whiteInner);

  ctx.restore();

  ctx.fillStyle = 'rgba(163,0,51,0.55)';
  ctx.font = 'bold 8px Inter, sans-serif';
  ctx.textAlign = 'center';
  ctx.fillText('FZ STUDIO', logoX, logoY + 65);
  ctx.textAlign = 'left';

  const tex = new THREE.CanvasTexture(canvas);
  tex.needsUpdate = true;
  cachedFZLogoTex = tex;
  return tex;
}

// Shared material instances to reduce draw calls
const darkMetalMat = new THREE.MeshStandardMaterial({ color: '#1a1c22', roughness: 0.3, metalness: 0.85 });
const darkBodyMat = new THREE.MeshStandardMaterial({ color: '#111318', roughness: 0.3, metalness: 0.8 });
const frameMat = new THREE.MeshStandardMaterial({ color: '#1e2128', roughness: 0.3, metalness: 0.8 });

// 3. Workstation — Optimized desk + flat monitor (FZ logo) + PC tower on desk + accessories + Razer gaming chair
function Workstation() {
  const screenTex = useMemo(() => createFZLogoTexture(), []);

  return (
    <group position={[-4.5, 0, -1.0]} rotation={[0, Math.PI * 0.22, 0]}>

      {/* =====================================================
          DESK — thick surface + chamfered edge
      ===================================================== */}
      {/* Main desk top surface */}
      <mesh position={[0, 1.06, 0]}>
        <boxGeometry args={[3.6, 0.06, 1.55]} />
        <meshStandardMaterial color="#1b1a18" roughness={0.45} metalness={0.05} />
      </mesh>
      {/* Desk edge lip (front) */}
      <mesh position={[0, 1.038, 0.74]}>
        <boxGeometry args={[3.6, 0.022, 0.06]} />
        <meshStandardMaterial color="#111010" roughness={0.55} metalness={0.1} />
      </mesh>

      {/* Desk frame crossbar (back) */}
      <mesh position={[0, 0.62, -0.68]}>
        <boxGeometry args={[3.5, 0.06, 0.06]} />
        <primitive object={frameMat} attach="material" />
      </mesh>
      {/* Desk frame crossbar (front) */}
      <mesh position={[0, 0.62, 0.68]}>
        <boxGeometry args={[3.5, 0.06, 0.06]} />
        <primitive object={frameMat} attach="material" />
      </mesh>

      {/* ---- Desk legs: steel inverted-U style ---- */}
      {/* Left leg panel */}
      <mesh position={[-1.7, 0.53, 0]}>
        <boxGeometry args={[0.055, 1.06, 1.38]} />
        <primitive object={darkMetalMat} attach="material" />
      </mesh>
      {/* Left leg foot */}
      <mesh position={[-1.7, 0.028, 0]}>
        <boxGeometry args={[0.42, 0.056, 1.4]} />
        <meshStandardMaterial color="#141418" roughness={0.4} metalness={0.7} />
      </mesh>
      {/* Right leg panel */}
      <mesh position={[1.7, 0.53, 0]}>
        <boxGeometry args={[0.055, 1.06, 1.38]} />
        <primitive object={darkMetalMat} attach="material" />
      </mesh>
      {/* Right leg foot */}
      <mesh position={[1.7, 0.028, 0]}>
        <boxGeometry args={[0.42, 0.056, 1.4]} />
        <meshStandardMaterial color="#141418" roughness={0.4} metalness={0.7} />
      </mesh>

      {/* RGB LED strip under desk — emissive crimson underglow */}
      <mesh position={[0, 1.02, 0.72]}>
        <boxGeometry args={[3.4, 0.01, 0.015]} />
        <meshBasicMaterial color="#A30033" toneMapped={false} />
      </mesh>

      {/* =====================================================
          ULTRAWIDE MONITOR — flat plane (renders correctly)
      ===================================================== */}
      <group position={[0, 1.74, -0.45]}>
        {/* Monitor outer frame / bezel */}
        <mesh>
          <boxGeometry args={[2.1, 0.95, 0.05]} />
          <meshStandardMaterial color="#0a0b0e" roughness={0.25} metalness={0.85} />
        </mesh>
        {/* Screen face — flat plane with FZ logo texture */}
        <mesh position={[0, 0, 0.026]}>
          <planeGeometry args={[2.0, 0.88]} />
          {screenTex ? (
            <meshBasicMaterial map={screenTex} toneMapped={false} />
          ) : (
            <meshBasicMaterial color="#0d0f17" toneMapped={false} />
          )}
        </mesh>
        {/* Screen glow light — single combined light */}
        <pointLight position={[0, 0, 0.25]} color="#c7d2fe" intensity={1.5} distance={2.5} />

        {/* Monitor stand neck */}
        <mesh position={[0, -0.58, -0.08]}>
          <boxGeometry args={[0.06, 0.28, 0.06]} />
          <meshStandardMaterial color="#252830" roughness={0.3} metalness={0.88} />
        </mesh>
        {/* Monitor stand base plate */}
        <mesh position={[0, -0.74, -0.08]}>
          <boxGeometry args={[0.55, 0.028, 0.24]} />
          <primitive object={darkMetalMat} attach="material" />
        </mesh>
      </group>

      {/* =====================================================
          PC TOWER — ON TOP OF DESK (right side)
      ===================================================== */}
      <group position={[1.38, 1.54, -0.35]}>
        {/* Main chassis body */}
        <mesh>
          <boxGeometry args={[0.28, 0.88, 0.52]} />
          <primitive object={darkBodyMat} attach="material" />
        </mesh>
        {/* Front panel */}
        <mesh position={[0.141, 0, 0]}>
          <boxGeometry args={[0.002, 0.88, 0.52]} />
          <meshStandardMaterial color="#1a1d24" roughness={0.4} metalness={0.7} />
        </mesh>
        {/* Tempered glass side panel (facing viewer) */}
        <mesh position={[0, 0, 0.262]}>
          <boxGeometry args={[0.28, 0.88, 0.003]} />
          <meshStandardMaterial color="#141720" roughness={0} metalness={0.1} transparent opacity={0.35} />
        </mesh>

        {/* RGB Fan ring 1 — top fan */}
        <mesh position={[0.02, 0.22, 0.24]}>
          <torusGeometry args={[0.085, 0.012, 6, 20]} />
          <meshBasicMaterial color="#A30033" toneMapped={false} />
        </mesh>
        {/* RGB Fan ring 2 — mid fan */}
        <mesh position={[0.02, 0.04, 0.24]}>
          <torusGeometry args={[0.085, 0.012, 6, 20]} />
          <meshBasicMaterial color="#3b82f6" toneMapped={false} />
        </mesh>
        {/* RGB Fan ring 3 — bottom fan */}
        <mesh position={[0.02, -0.14, 0.24]}>
          <torusGeometry args={[0.085, 0.012, 6, 20]} />
          <meshBasicMaterial color="#8b5cf6" toneMapped={false} />
        </mesh>
        {/* Single combined RGB glow light */}
        <pointLight position={[0.02, 0.04, 0.38]} color="#A30033" intensity={0.8} distance={0.8} />

        {/* GPU bracket */}
        <mesh position={[0.0, -0.28, 0.2]}>
          <boxGeometry args={[0.18, 0.018, 0.22]} />
          <meshStandardMaterial color="#252830" roughness={0.4} metalness={0.85} />
        </mesh>

        {/* Front power button LED ring */}
        <mesh position={[0.143, 0.30, 0.0]}>
          <torusGeometry args={[0.014, 0.003, 4, 12]} />
          <meshBasicMaterial color="#A30033" toneMapped={false} />
        </mesh>

        {/* Ventilation slots — top (merged into fewer meshes) */}
        {[0, 0.08, 0.16].map((offset, i) => (
          <mesh key={i} position={[0.0, 0.42, offset - 0.08]}>
            <boxGeometry args={[0.22, 0.006, 0.016]} />
            <meshStandardMaterial color="#0d0e11" roughness={0.9} />
          </mesh>
        ))}
      </group>

      {/* =====================================================
          EXTENDED DESK MAT — XL gaming mat
      ===================================================== */}
      <mesh position={[0.15, 1.092, 0.22]}>
        <boxGeometry args={[2.0, 0.006, 0.68]} />
        <meshStandardMaterial color="#0e0e11" roughness={0.88} />
      </mesh>
      {/* Desk mat stitched edge (front) */}
      <mesh position={[0.15, 1.096, 0.555]}>
        <boxGeometry args={[2.0, 0.005, 0.012]} />
        <meshStandardMaterial color="#A30033" roughness={0.7} />
      </mesh>

      {/* =====================================================
          MECHANICAL KEYBOARD
      ===================================================== */}
      <group position={[0.15, 1.11, 0.3]}>
        {/* Keyboard body */}
        <mesh>
          <boxGeometry args={[0.72, 0.022, 0.25]} />
          <meshStandardMaterial color="#1c1f26" roughness={0.4} metalness={0.6} />
        </mesh>
        {/* Keycap rows */}
        {[0, 1, 2, 3].map((row) => (
          <mesh key={row} position={[0, 0.026, -0.08 + row * 0.055]}>
            <boxGeometry args={[0.68, 0.018, 0.044]} />
            <meshStandardMaterial color={row === 0 ? '#222530' : '#1e2028'} roughness={0.3} metalness={0.3} />
          </mesh>
        ))}
        {/* RGB underglow strip */}
        <mesh position={[0, 0.004, 0.115]}>
          <boxGeometry args={[0.68, 0.004, 0.008]} />
          <meshBasicMaterial color="#A30033" toneMapped={false} />
        </mesh>
      </group>

      {/* =====================================================
          WIRELESS MOUSE
      ===================================================== */}
      <group position={[0.72, 1.1, 0.3]}>
        <mesh>
          <capsuleGeometry args={[0.028, 0.065, 4, 8]} />
          <meshStandardMaterial color="#1a1d24" roughness={0.35} metalness={0.5} />
        </mesh>
        {/* Scroll wheel */}
        <mesh position={[0, 0.025, -0.02]} rotation={[Math.PI / 2, 0, 0]}>
          <cylinderGeometry args={[0.008, 0.008, 0.028, 8]} />
          <meshStandardMaterial color="#2c303a" roughness={0.4} metalness={0.7} />
        </mesh>
      </group>

      {/* =====================================================
          HEADPHONE STAND (right side of desk — moved left since PC is now on desk)
      ===================================================== */}
      <group position={[-1.42, 1.10, 0.15]}>
        {/* Stand base */}
        <mesh>
          <cylinderGeometry args={[0.09, 0.11, 0.018, 12]} />
          <primitive object={darkMetalMat} attach="material" />
        </mesh>
        {/* Stand pole */}
        <mesh position={[0, 0.21, 0]}>
          <cylinderGeometry args={[0.013, 0.015, 0.41, 8]} />
          <meshStandardMaterial color="#252830" roughness={0.25} metalness={0.9} />
        </mesh>
        {/* Stand arm hook */}
        <mesh position={[0, 0.42, -0.06]}>
          <torusGeometry args={[0.06, 0.011, 6, 12, Math.PI]} />
          <meshStandardMaterial color="#252830" roughness={0.25} metalness={0.9} />
        </mesh>
        {/* Headphone — left earcup */}
        <mesh position={[-0.085, 0.38, -0.055]}>
          <cylinderGeometry args={[0.055, 0.055, 0.032, 12]} />
          <meshStandardMaterial color="#111317" roughness={0.4} metalness={0.7} />
        </mesh>
        {/* Headphone — right earcup */}
        <mesh position={[0.085, 0.38, -0.055]}>
          <cylinderGeometry args={[0.055, 0.055, 0.032, 12]} />
          <meshStandardMaterial color="#111317" roughness={0.4} metalness={0.7} />
        </mesh>
        {/* Headphone band */}
        <mesh position={[0, 0.42, -0.055]}>
          <torusGeometry args={[0.088, 0.009, 4, 16, Math.PI]} />
          <meshStandardMaterial color="#1c1f26" roughness={0.35} metalness={0.8} />
        </mesh>
      </group>

      {/* =====================================================
          COFFEE MUG
      ===================================================== */}
      <group position={[-0.85, 1.1, 0.45]}>
        <mesh>
          <cylinderGeometry args={[0.04, 0.036, 0.09, 12]} />
          <meshStandardMaterial color="#1a1d24" roughness={0.6} metalness={0.2} />
        </mesh>
        {/* Mug handle */}
        <mesh position={[0.052, 0, 0]}>
          <torusGeometry args={[0.022, 0.007, 4, 8, Math.PI]} />
          <meshStandardMaterial color="#1a1d24" roughness={0.6} metalness={0.2} />
        </mesh>
        {/* Coffee surface */}
        <mesh position={[0, 0.043, 0]}>
          <cylinderGeometry args={[0.036, 0.036, 0.004, 12]} />
          <meshStandardMaterial color="#3a1e0a" roughness={0.8} />
        </mesh>
      </group>

      {/* =====================================================
          SMALL SUCCULENT PLANT
      ===================================================== */}
      <group position={[-1.42, 1.10, -0.35]}>
        {/* Pot */}
        <mesh>
          <cylinderGeometry args={[0.048, 0.038, 0.07, 8]} />
          <meshStandardMaterial color="#2d3139" roughness={0.7} metalness={0.2} />
        </mesh>
        {/* Soil */}
        <mesh position={[0, 0.038, 0]}>
          <cylinderGeometry args={[0.045, 0.045, 0.006, 8]} />
          <meshStandardMaterial color="#1a1209" roughness={0.95} />
        </mesh>
        {/* Succulent leaves */}
        {[0, 1, 2, 3, 4].map((i) => (
          <mesh key={i} position={[
            Math.sin((i / 5) * Math.PI * 2) * 0.022,
            0.076,
            Math.cos((i / 5) * Math.PI * 2) * 0.022
          ]} rotation={[
            Math.sin((i / 5) * Math.PI * 2) * 0.4,
            (i / 5) * Math.PI * 2,
            Math.cos((i / 5) * Math.PI * 2) * 0.4
          ]}>
            <coneGeometry args={[0.015, 0.055, 4]} />
            <meshStandardMaterial color="#2d5a27" roughness={0.8} />
          </mesh>
        ))}
      </group>

      {/* =====================================================
          RAZER-STYLE GAMING CHAIR — Black with green accent piping
      ===================================================== */}
      <group position={[0, 0, 0.9]}>
        {/* ---- Five-star caster base ---- */}
        {[0, 1, 2, 3, 4].map((i) => {
          const angle = (i / 5) * Math.PI * 2;
          return (
            <group key={i}>
              {/* Arm */}
              <mesh position={[Math.cos(angle) * 0.22, 0.07, Math.sin(angle) * 0.22]}
                rotation={[0, -angle, 0]}>
                <boxGeometry args={[0.42, 0.035, 0.05]} />
                <primitive object={darkMetalMat} attach="material" />
              </mesh>
              {/* Caster wheel */}
              <mesh position={[Math.cos(angle) * 0.38, 0.038, Math.sin(angle) * 0.38]}
                rotation={[Math.PI / 2, 0, 0]}>
                <cylinderGeometry args={[0.035, 0.035, 0.032, 10]} />
                <meshStandardMaterial color="#111317" roughness={0.6} metalness={0.4} />
              </mesh>
            </group>
          );
        })}

        {/* Pneumatic cylinder */}
        <mesh position={[0, 0.23, 0]}>
          <cylinderGeometry args={[0.038, 0.042, 0.32, 10]} />
          <meshStandardMaterial color="#181b22" roughness={0.2} metalness={0.92} />
        </mesh>
        {/* Piston inner chrome */}
        <mesh position={[0, 0.38, 0]}>
          <cylinderGeometry args={[0.024, 0.026, 0.12, 8]} />
          <meshStandardMaterial color="#4a5060" roughness={0.1} metalness={0.98} />
        </mesh>

        {/* ---- BUCKET SEAT ---- */}
        {/* Seat base frame */}
        <mesh position={[0, 0.46, 0]}>
          <boxGeometry args={[0.64, 0.04, 0.62]} />
          <primitive object={frameMat} attach="material" />
        </mesh>

        {/* Seat cushion — thick black leather */}
        <mesh position={[0, 0.52, -0.02]}>
          <boxGeometry args={[0.60, 0.08, 0.56]} />
          <meshStandardMaterial color="#0e1014" roughness={0.72} metalness={0.03} />
        </mesh>

        {/* Seat front edge roll */}
        <mesh position={[0, 0.528, 0.26]} rotation={[0, 0, Math.PI / 2]}>
          <cylinderGeometry args={[0.038, 0.038, 0.56, 8]} />
          <meshStandardMaterial color="#0e1014" roughness={0.72} />
        </mesh>

        {/* ---- Racing wing bolsters (left) ---- */}
        <mesh position={[-0.28, 0.57, -0.02]}>
          <boxGeometry args={[0.08, 0.12, 0.52]} />
          <meshStandardMaterial color="#0c0e12" roughness={0.68} metalness={0.04} />
        </mesh>
        {/* Left bolster GREEN piping (inner edge) */}
        <mesh position={[-0.235, 0.59, -0.02]}>
          <boxGeometry args={[0.006, 0.08, 0.50]} />
          <meshBasicMaterial color="#22c55e" toneMapped={false} />
        </mesh>

        {/* ---- Racing wing bolsters (right) ---- */}
        <mesh position={[0.28, 0.57, -0.02]}>
          <boxGeometry args={[0.08, 0.12, 0.52]} />
          <meshStandardMaterial color="#0c0e12" roughness={0.68} metalness={0.04} />
        </mesh>
        {/* Right bolster GREEN piping (inner edge) */}
        <mesh position={[0.235, 0.59, -0.02]}>
          <boxGeometry args={[0.006, 0.08, 0.50]} />
          <meshBasicMaterial color="#22c55e" toneMapped={false} />
        </mesh>

        {/* ---- TALL RACING BACKREST ---- */}
        {/* Backrest spine column */}
        <mesh position={[0, 0.82, 0.295]}>
          <boxGeometry args={[0.06, 0.6, 0.045]} />
          <meshStandardMaterial color="#1c1f28" roughness={0.28} metalness={0.84} />
        </mesh>

        {/* Backrest main panel — tall bucket shape */}
        <mesh position={[0, 1.05, 0.31]}>
          <boxGeometry args={[0.52, 0.92, 0.035]} />
          <meshStandardMaterial color="#0c0e12" roughness={0.65} metalness={0.06} />
        </mesh>

        {/* Backrest outer frame / shell */}
        <mesh position={[0, 1.05, 0.33]}>
          <boxGeometry args={[0.58, 0.96, 0.015]} />
          <meshStandardMaterial color="#16181e" roughness={0.35} metalness={0.75} />
        </mesh>

        {/* ---- Backrest wing bolsters (left) ---- */}
        <mesh position={[-0.26, 1.0, 0.30]}>
          <boxGeometry args={[0.08, 0.82, 0.055]} />
          <meshStandardMaterial color="#0c0e12" roughness={0.68} metalness={0.04} />
        </mesh>
        {/* Left backrest GREEN piping */}
        <mesh position={[-0.215, 1.0, 0.28]}>
          <boxGeometry args={[0.006, 0.80, 0.01]} />
          <meshBasicMaterial color="#22c55e" toneMapped={false} />
        </mesh>

        {/* ---- Backrest wing bolsters (right) ---- */}
        <mesh position={[0.26, 1.0, 0.30]}>
          <boxGeometry args={[0.08, 0.82, 0.055]} />
          <meshStandardMaterial color="#0c0e12" roughness={0.68} metalness={0.04} />
        </mesh>
        {/* Right backrest GREEN piping */}
        <mesh position={[0.215, 1.0, 0.28]}>
          <boxGeometry args={[0.006, 0.80, 0.01]} />
          <meshBasicMaterial color="#22c55e" toneMapped={false} />
        </mesh>

        {/* Center panel GREEN accent stripe (down the spine) */}
        <mesh position={[0, 1.05, 0.29]}>
          <boxGeometry args={[0.008, 0.72, 0.006]} />
          <meshBasicMaterial color="#22c55e" toneMapped={false} />
        </mesh>

        {/* Lumbar support bulge */}
        <mesh position={[0, 0.80, 0.295]}>
          <boxGeometry args={[0.40, 0.14, 0.04]} />
          <meshStandardMaterial color="#0e1014" roughness={0.62} metalness={0.06} />
        </mesh>

        {/* ---- Headrest with green stitching ---- */}
        <mesh position={[0, 1.56, 0.30]}>
          <boxGeometry args={[0.22, 0.14, 0.06]} />
          <meshStandardMaterial color="#0c0e12" roughness={0.70} metalness={0.04} />
        </mesh>
        {/* Headrest GREEN border piping */}
        <mesh position={[0, 1.56, 0.265]}>
          <boxGeometry args={[0.24, 0.16, 0.003]} />
          <meshStandardMaterial color="#16181e" roughness={0.35} metalness={0.5} />
        </mesh>
        {/* Headrest green top accent */}
        <mesh position={[0, 1.635, 0.30]}>
          <boxGeometry args={[0.18, 0.005, 0.04]} />
          <meshBasicMaterial color="#22c55e" toneMapped={false} />
        </mesh>
        {/* Headrest neck column */}
        <mesh position={[0, 1.48, 0.32]}>
          <boxGeometry args={[0.04, 0.08, 0.035]} />
          <meshStandardMaterial color="#1c1f28" roughness={0.28} metalness={0.84} />
        </mesh>

        {/* ---- Armrests ---- */}
        {/* Left armrest post */}
        <mesh position={[-0.32, 0.78, 0.08]}>
          <boxGeometry args={[0.04, 0.46, 0.04]} />
          <primitive object={frameMat} attach="material" />
        </mesh>
        {/* Left armrest pad */}
        <mesh position={[-0.32, 1.02, 0.04]}>
          <boxGeometry args={[0.07, 0.025, 0.24]} />
          <meshStandardMaterial color="#0e1014" roughness={0.72} metalness={0.03} />
        </mesh>
        {/* Right armrest post */}
        <mesh position={[0.32, 0.78, 0.08]}>
          <boxGeometry args={[0.04, 0.46, 0.04]} />
          <primitive object={frameMat} attach="material" />
        </mesh>
        {/* Right armrest pad */}
        <mesh position={[0.32, 1.02, 0.04]}>
          <boxGeometry args={[0.07, 0.025, 0.24]} />
          <meshStandardMaterial color="#0e1014" roughness={0.72} metalness={0.03} />
        </mesh>
      </group>

    </group>
  );
}

// 4. Interactive Smooth Mouse Parallax Rig
function SceneParallaxRig({ isFullscreen, isFreeCam }) {
  const { camera, pointer } = useThree();
  const vec = new THREE.Vector3();

  useFrame(() => {
    if (isFreeCam) return; // OrbitControls handles the camera in free-cam mode
    const targetX = pointer.x * (isFullscreen ? 1.4 : 0.6);
    const targetY = 1.45 + pointer.y * (isFullscreen ? 0.35 : 0.18);
    const targetZ = 7.8;

    camera.position.lerp(vec.set(targetX, targetY, targetZ), 0.045);
    camera.lookAt(0, 1.1, 0);
  });

  return null;
}

// 5. Master ThreeScene Component
export default function ThreeScene({ isFullscreen = false, isActive = true, isFreeCam: propFreeCam = false }) {
  const [isFreeCam, setIsFreeCam] = useState(propFreeCam);

  // Synchronize with prop and window custom event for Dev HUD
  useEffect(() => {
    setIsFreeCam(propFreeCam);
  }, [propFreeCam]);

  useEffect(() => {
    const handleToggle = (e) => {
      const enabled = Boolean(e?.detail?.enabled);
      setIsFreeCam(enabled);
    };

    window.addEventListener('toggle-dev-freecam', handleToggle);
    return () => window.removeEventListener('toggle-dev-freecam', handleToggle);
  }, []);

  return (
    <div
      className="w-full h-full relative"
      style={{
        width: '100%',
        height: '100%',
        backgroundColor: '#07080a',
        transition: 'opacity 0.6s ease'
      }}
    >
      <Canvas dpr={[1, 1.5]} gl={{ antialias: true, alpha: false, powerPreference: 'high-performance' }}>
        <React.Suspense fallback={null}>
          <PerspectiveCamera makeDefault position={[0, 1.45, 7.8]} fov={44} />
          
          {/* Luxury Penthouse warm-dark background & atmospheric fog */}
          <color attach="background" args={['#0a0b0e']} />
          <fog attach="fog" args={['#0a0b0e', 18, 40]} />

          {/* Studio Lights — tuned for penthouse showroom atmosphere */}
          <ambientLight intensity={0.35} />
          
          {/* Main Key Light — single shadow caster */}
          <directionalLight
            castShadow
            position={[4, 12, 5]}
            intensity={2.5}
            shadow-mapSize={[1024, 1024]}
            shadow-bias={-0.0001}
          />
          
          {/* Cool Rim Light for hypercar curves */}
          <directionalLight
            position={[-6, 8, -2]}
            intensity={1.8}
            color="#b8c4d4"
          />

          {/* Underglow & Fill */}
          <directionalLight position={[0, 1, 4]} intensity={0.5} />

          {/* Luxury Penthouse Showroom (Glass walls, city panorama, crimson feature wall, neon ceiling) */}
          <PenthouseShowroom />

          {/* Reflective Nero Marquina Marble Floor */}
          <MarbleFloor />

          {/* McLaren 765LT Supercar */}
          <McLaren isActive={isActive} />

          {/* Workstation & Gaming Chair */}
          <Workstation />

          {/* Camera Controls: Free 360 mouse orbit when enabled, smooth parallax otherwise */}
          {isFreeCam ? (
            <OrbitControls
              makeDefault
              enableDamping
              dampingFactor={0.05}
              target={[0, 1.1, 0]}
              minDistance={2.5}
              maxDistance={22}
              maxPolarAngle={Math.PI / 2 - 0.02} // Keep camera above floor
            />
          ) : (
            <SceneParallaxRig isFullscreen={isFullscreen} isFreeCam={false} />
          )}
        </React.Suspense>
      </Canvas>
    </div>
  );
}
