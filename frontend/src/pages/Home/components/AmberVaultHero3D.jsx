import React, { useRef, useEffect, useState } from 'react';
import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import gsap from 'gsap';

/**
 * Creates an ultra-crisp procedural canvas texture for the monitor screen
 * showing a high-end editorial preview of the next section ("Works (01) By FZ Monogram").
 */
function createMonitorScreenTexture() {
  if (typeof document === 'undefined') return null;
  const canvas = document.createElement('canvas');
  canvas.width = 1920;
  canvas.height = 800; // 21:9 ultrawide monitor ratio
  const ctx = canvas.getContext('2d');

  const draw = () => {
    // 1. Deep charcoal/obsidian background
    ctx.fillStyle = '#0d0d12';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    // 2. Subtle architectural grid lines
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.06)';
    ctx.lineWidth = 1;
    for (let x = 0; x < canvas.width; x += 120) {
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, canvas.height);
      ctx.stroke();
    }
    for (let y = 0; y < canvas.height; y += 80) {
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(canvas.width, y);
      ctx.stroke();
    }

    // 3. Editorial Top Bar with warm amber glow
    ctx.fillStyle = '#e8923e';
    ctx.font = '600 20px monospace';
    ctx.fillText('PORTFOLIO INDEX // SECTION 01', 80, 70);

    ctx.fillStyle = 'rgba(255, 255, 255, 0.55)';
    ctx.font = '500 18px monospace';
    ctx.fillText('PROJECT METADATA / 2026', canvas.width - 360, 70);

    // Horizontal divider
    ctx.strokeStyle = 'rgba(232, 146, 62, 0.28)';
    ctx.beginPath();
    ctx.moveTo(80, 95);
    ctx.lineTo(canvas.width - 80, 95);
    ctx.stroke();

    // 4. Hero Marquee Title ("Works")
    ctx.fillStyle = '#ffffff';
    ctx.font = 'italic 300 190px "Melodrama", "Playfair Display", serif';
    ctx.fillText('Works', 120, 390);

    // Pill badge: ( 01 )
    ctx.fillStyle = '#f5a04a'; // Bright warm amber accent
    ctx.font = '600 28px monospace';
    ctx.fillText('( 01 )', 650, 260);

    // 5. Subtitle: "By FZ Monogram"
    ctx.fillStyle = '#d2d5de';
    ctx.font = '600 54px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
    ctx.fillText('By FZ Monogram', 130, 490);

    // Amber accent bar
    ctx.fillStyle = '#f5a04a';
    ctx.fillRect(630, 460, 70, 10);

    // 6. Bottom Status HUD
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.15)';
    ctx.beginPath();
    ctx.moveTo(80, canvas.height - 95);
    ctx.lineTo(canvas.width - 80, canvas.height - 95);
    ctx.stroke();

    ctx.fillStyle = '#f5a04a';
    ctx.font = '600 18px monospace';
    ctx.fillText('SCROLL TO ENTER PORTAL', 80, canvas.height - 58);

    ctx.fillStyle = 'rgba(255, 255, 255, 0.5)';
    ctx.font = '500 16px monospace';
    ctx.fillText('MONOLITHIC AMBER VAULT // ACTIVE', canvas.width - 440, canvas.height - 58);
  };

  draw();
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.generateMipmaps = true;
  texture.minFilter = THREE.LinearMipmapLinearFilter;
  return texture;
}

/**
 * Custom Volumetric God-Ray Shader Material
 * Renders smooth atmospheric downlight cones with radial Fresnel falloff
 * and vertical gradient fading, eliminating hard polygonal cone edges.
 */
function createVolumetricBeamMaterial() {
  return new THREE.ShaderMaterial({
    uniforms: {
      uColorCore: { value: new THREE.Color('#fff4e0') },
      uColorEdge: { value: new THREE.Color('#d48c3a') },
      uHeight: { value: 6.55 },
      uBaseY: { value: 0.0 }
    },
    vertexShader: `
      varying vec3 vWorldPosition;
      varying vec3 vNormal;
      void main() {
        vNormal = normalize(normalMatrix * normal);
        vec4 worldPos = modelMatrix * vec4(position, 1.0);
        vWorldPosition = worldPos.xyz;
        gl_Position = projectionMatrix * viewMatrix * worldPos;
      }
    `,
    fragmentShader: `
      uniform vec3 uColorCore;
      uniform vec3 uColorEdge;
      uniform float uHeight;
      uniform float uBaseY;
      varying vec3 vWorldPosition;
      varying vec3 vNormal;

      void main() {
        vec3 viewDir = normalize(cameraPosition - vWorldPosition);
        // Soft radial Fresnel falloff: fades to zero at glancing angle silhouette edges
        float fresnel = clamp(abs(dot(vNormal, viewDir)), 0.0, 1.0);
        float radialFade = pow(fresnel, 1.4);

        // Soft vertical falloff: smoothstep at top fixture and floor contact
        float normY = clamp((vWorldPosition.y - uBaseY) / uHeight, 0.0, 1.0);
        float vertFade = smoothstep(0.0, 0.22, normY) * smoothstep(1.0, 0.78, normY);

        float alpha = radialFade * vertFade * 0.16;
        vec3 col = mix(uColorEdge, uColorCore, radialFade);
        gl_FragColor = vec4(col, alpha);
      }
    `,
    transparent: true,
    blending: THREE.AdditiveBlending,
    side: THREE.DoubleSide,
    depthWrite: false
  });
}

/**
 * AmberVaultHero3D - Interactive 3D Hero Scene
 * 
 * Choreography:
 * 1. Intro Reveal: Camera dollies from near monitor ([0, 1.37, -0.60]) back to wide vault ([0, 1.38, 11.50]).
 * 2. Idle: Smooth mouse parallax.
 * 3. Scroll: Camera dollies from wide vault straight into the monitor screen ([0, 1.37, -2.06]),
 *    creating a seamless portal transition into the section below the hero.
 */
export default function AmberVaultHero3D({
  isRevealed = false,
  scrollProgress = 0, // 0 to 1
  onTransitionComplete
}) {
  const mountRef = useRef(null);
  const sceneRef = useRef(null);
  const cameraRef = useRef(null);
  const rendererRef = useRef(null);
  const monitorMeshRef = useRef(null);
  const animFrameRef = useRef(null);

  // Camera coordinates (Three.js space: +Y is Up, -Z is forward toward monitor)
  // Monitor is at [0, 1.37, -2.16]
  const cameraState = useRef({
    nearZ: -0.60,
    wideZ: 11.50,
    screenZ: -2.06,
    x: 0,
    y: 1.38,
    z: -0.60,
    targetZ: -0.60,
    lookAtY: 1.37,
    lookAtZ: -2.16,
    mouseX: 0,
    mouseY: 0,
    targetMouseX: 0,
    targetMouseY: 0,
    introFactor: 0
  });

  const [isLoaded, setIsLoaded] = useState(false);

  // 1. Initialize Three.js Scene, Camera, Renderer, and Lights
  useEffect(() => {
    const container = mountRef.current;
    if (!container) return;

    const width = container.clientWidth || window.innerWidth;
    const height = container.clientHeight || window.innerHeight;

    // SCENE
    const scene = new THREE.Scene();
    scene.background = new THREE.Color('#0a0806');
    sceneRef.current = scene;

    // CAMERA (FOV 32° matches 35.4mm focal length on standard 16:9 sensor)
    const camera = new THREE.PerspectiveCamera(32, width / height, 0.1, 100);
    camera.position.set(0, 1.38, cameraState.current.nearZ);
    camera.lookAt(0, 1.37, -2.16);
    cameraRef.current = camera;

    // RENDERER
    const renderer = new THREE.WebGLRenderer({
      antialias: true,
      powerPreference: 'high-performance',
      alpha: true
    });
    renderer.setSize(width, height);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1.15;
    renderer.shadowMap.enabled = false;
    rendererRef.current = renderer;

    container.appendChild(renderer.domElement);

    // LIGHTING SETUP (Calibrated architectural illumination matching final_render.png)
    // 1. Warm ambient bounce filling the vault
    const ambientLight = new THREE.AmbientLight('#ffe5c4', 0.70);
    scene.add(ambientLight);

    // 2. Hemisphere fill (warm ceiling bounce, dark floor tone)
    const hemiLight = new THREE.HemisphereLight('#ffeacc', '#221810', 0.65);
    scene.add(hemiLight);

    // 3. Golden desert sun spilling through the 3 window apertures
    const windowSun = new THREE.DirectionalLight('#ffc87a', 1.8);
    windowSun.position.set(0, 4.0, -12.0);
    windowSun.target.position.set(0, 1.2, 0);
    scene.add(windowSun.target);
    scene.add(windowSun);

    // 4. Dais Upward Bounce Light (Simulates warm glow bouncing off stone dais into chair underside & desk frame)
    const daisBounceLight = new THREE.PointLight('#ffbe78', 2.2, 4.5, 1.2);
    daisBounceLight.position.set(0, 0.45, -1.8);
    scene.add(daisBounceLight);

    // 5. Front Key Light (Soft key illuminating chair seat, armrests, and desk chamfers)
    const deskKeyLight = new THREE.DirectionalLight('#ffe8c8', 1.3);
    deskKeyLight.position.set(0, 3.2, 4.5);
    deskKeyLight.target.position.set(0, 1.2, -2.16);
    scene.add(deskKeyLight.target);
    scene.add(deskKeyLight);

    // 6. Back-Rim Light (Crisp edge separation for chair and desk against windows)
    const rimLight = new THREE.DirectionalLight('#ffa840', 1.1);
    rimLight.position.set(0, 2.5, -6.5);
    rimLight.target.position.set(0, 1.2, -2.16);
    scene.add(rimLight.target);
    scene.add(rimLight);

    // 7. 4 Recessed Ceiling Downlights & Soft Volumetric God Rays
    const downlightCoords = [
      { name: 'Jamb_L', x: -5.4, intensity: 32 },
      { name: 'Pillar_1', x: -2.0, intensity: 40 },
      { name: 'Pillar_2', x: 2.0, intensity: 40 },
      { name: 'Jamb_R', x: 5.4, intensity: 32 }
    ];

    const beamMaterial = createVolumetricBeamMaterial();

    downlightCoords.forEach((dl) => {
      // Physical Spotlight
      const spot = new THREE.SpotLight('#fff4e0', dl.intensity);
      spot.position.set(dl.x, 6.55, -7.1);
      spot.target.position.set(dl.x, 0, -7.1);
      spot.angle = 0.44;
      spot.penumbra = 0.85;
      spot.distance = 22;
      spot.decay = 1.3;
      scene.add(spot.target);
      scene.add(spot);

      // Atmospheric Volumetric Light Cone
      const coneHeight = 6.55;
      const coneGeo = new THREE.CylinderGeometry(0.24, 1.85, coneHeight, 32, 1, true);
      const beamMesh = new THREE.Mesh(coneGeo, beamMaterial);
      beamMesh.position.set(dl.x, coneHeight / 2, -7.1);
      scene.add(beamMesh);
    });

    // 2. Load GLB Model & Textures
    const loader = new GLTFLoader();
    const textureLoader = new THREE.TextureLoader();
    const screenTexture = createMonitorScreenTexture();

    const bgTexture = textureLoader.load('/models/clean_sandstorm_sky.png');
    bgTexture.colorSpace = THREE.SRGBColorSpace;

    const bakedAtlasTexture = textureLoader.load('/models/vault_architecture_bake.jpg');
    bakedAtlasTexture.colorSpace = THREE.SRGBColorSpace;
    bakedAtlasTexture.flipY = false;

    loader.load(
      '/models/monolithic_amber_vault.glb',
      (gltf) => {
        const root = gltf.scene;

        root.traverse((child) => {
          if (child.isMesh) {
            // 1. Baked Architectural Shell (Main Floor, Dais, Pillars, Walls, Ceilings)
            // Displays the 100% baked Cycles global illumination, direct light pools, and AO
            if (child.name === 'Vault_Chamber_Baked') {
              child.material = new THREE.MeshStandardMaterial({
                map: bakedAtlasTexture,
                roughness: 0.38,
                metalness: 0.05
              });
              return;
            }

            // 2. Sandstorm Sky Backdrop: Golden desert dunes & sun rays
            if (child.name === 'Sandstorm_Sky_Backdrop') {
              child.material = new THREE.MeshBasicMaterial({
                map: bgTexture,
                toneMapped: false
              });
              return;
            }

            // 3. Monitor Active Screen: Dynamic Section 02 Preview with Planar UVs
            if (child.name === 'Monitor_Active_Screen') {
              monitorMeshRef.current = child;
              if (screenTexture) {
                child.material = new THREE.MeshBasicMaterial({
                  map: screenTexture,
                  toneMapped: false,
                  side: THREE.DoubleSide
                });
              }
              return;
            }

            // 4. Dais LED Ring: Emissive warm glow
            if (child.name === 'Dais_LED_Ring') {
              child.material = new THREE.MeshBasicMaterial({
                color: new THREE.Color('#ffe4b8'),
                toneMapped: false
              });
              return;
            }

            // 5. 4 Ceiling Spotlight Lenses: Intense warm circular light cores
            if (child.name.startsWith('Ceiling_Downlight_Lens_')) {
              child.material = new THREE.MeshBasicMaterial({
                color: new THREE.Color('#fff6e0'),
                toneMapped: false
              });
              return;
            }

            // 6. 2 Ceiling Side Lines & Cove Strips: Visible architectural illumination
            if (child.name.startsWith('Ceiling_Side_') || child.name === 'Cove_LED_Strips') {
              child.material = new THREE.MeshBasicMaterial({
                color: new THREE.Color('#ffd894'),
                toneMapped: false
              });
              return;
            }

            // 7. Ceiling Can Trim
            if (child.name.startsWith('Ceiling_Downlight_Fixture_')) {
              child.material = new THREE.MeshStandardMaterial({
                color: new THREE.Color('#222026'),
                roughness: 0.35,
                metalness: 0.8
              });
              return;
            }

            // 8. Chair: High-end ergonomic nylon, mesh, and polished chrome
            if (child.name.startsWith('Chair_')) {
              if (child.name === 'Chair_Chrome_Shaft' || child.name === 'Chair_Pneumatic_Cylinder') {
                child.material = new THREE.MeshStandardMaterial({
                  color: new THREE.Color('#e0e0e8'),
                  metalness: 0.95,
                  roughness: 0.12
                });
              } else if (child.name === 'Chair_Armrests' || child.name === 'Chair_5Star_Base') {
                child.material = new THREE.MeshStandardMaterial({
                  color: new THREE.Color('#28272b'),
                  roughness: 0.42,
                  metalness: 0.18
                });
              } else {
                // Backrest, seat pan (ergonomic mesh)
                child.material = new THREE.MeshStandardMaterial({
                  color: new THREE.Color('#252428'),
                  roughness: 0.46,
                  metalness: 0.08
                });
              }
              return;
            }

            // 9. Desk: Parsons black oak surface & satin powder-coated steel
            if (child.name.startsWith('Desk_')) {
              if (child.name === 'Desk_Top_Slab') {
                child.material = new THREE.MeshStandardMaterial({
                  color: new THREE.Color('#252220'),
                  roughness: 0.34,
                  metalness: 0.08
                });
              } else if (child.name === 'Desk_Parsons_Frame' || child.name === 'Desk_Cable_Slot') {
                child.material = new THREE.MeshStandardMaterial({
                  color: new THREE.Color('#2a292e'),
                  roughness: 0.38,
                  metalness: 0.65
                });
              } else {
                // Blotter pad
                child.material = new THREE.MeshStandardMaterial({
                  color: new THREE.Color('#1c1b1e'),
                  roughness: 0.65,
                  metalness: 0.02
                });
              }
              return;
            }

            // 10. Monitor chassis, stand, and ambient bias strip
            if (child.name.startsWith('Monitor_')) {
              if (child.name === 'Monitor_Ambient_LED_Strip') {
                child.material = new THREE.MeshBasicMaterial({
                  color: new THREE.Color('#ffb356'),
                  toneMapped: false
                });
                return;
              }
              child.material = new THREE.MeshStandardMaterial({
                color: new THREE.Color('#252428'),
                roughness: 0.35,
                metalness: 0.72
              });
              return;
            }
          }
        });

        scene.add(root);
        setIsLoaded(true);
      },
      undefined,
      (err) => {
        console.error('Error loading monolithic_amber_vault.glb:', err);
      }
    );

    // 3. Mouse Movement Listener for Subtle Parallax
    const handleMouseMove = (e) => {
      const normX = (e.clientX / window.innerWidth) * 2 - 1;
      const normY = -(e.clientY / window.innerHeight) * 2 + 1;
      cameraState.current.targetMouseX = normX * 0.35;
      cameraState.current.targetMouseY = normY * 0.15;
    };
    window.addEventListener('mousemove', handleMouseMove, { passive: true });

    // 4. Resize Handler
    const handleResize = () => {
      if (!mountRef.current || !rendererRef.current || !cameraRef.current) return;
      const w = mountRef.current.clientWidth || window.innerWidth;
      const h = mountRef.current.clientHeight || window.innerHeight;
      cameraRef.current.aspect = w / h;
      cameraRef.current.updateProjectionMatrix();
      rendererRef.current.setSize(w, h);
    };
    window.addEventListener('resize', handleResize);

    // 5. Animation Render Loop
    const animate = () => {
      animFrameRef.current = requestAnimationFrame(animate);

      const state = cameraState.current;
      const cam = cameraRef.current;
      const rend = rendererRef.current;
      const sc = sceneRef.current;

      if (!cam || !rend || !sc) return;

      // Smooth mouse parallax damping
      state.mouseX += (state.targetMouseX - state.mouseX) * 0.05;
      state.mouseY += (state.targetMouseY - state.mouseY) * 0.05;

      // Apply camera position with parallax
      cam.position.x = state.x + state.mouseX;
      cam.position.y = state.y + state.mouseY;
      cam.position.z = state.z;

      cam.lookAt(state.mouseX * 0.2, state.lookAtY, state.lookAtZ);

      rend.render(sc, cam);
    };
    animate();

    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('resize', handleResize);
      if (animFrameRef.current) cancelAnimationFrame(animFrameRef.current);
      if (rendererRef.current && rendererRef.current.domElement) {
        container.removeChild(rendererRef.current.domElement);
        rendererRef.current.dispose();
      }
    };
  }, []);

  // -------------------------------------------------------------
  // ANIMATION PHASE 1: Intro Reveal (Dolly from Near Screen -> Wide Vault)
  // -------------------------------------------------------------
  useEffect(() => {
    if (!isRevealed) return;

    // Smooth dolly out from near monitor ([0, 1.37, -0.60]) to wide vault ([0, 1.38, 11.50])
    gsap.to(cameraState.current, {
      introFactor: 1,
      z: cameraState.current.wideZ,
      y: 1.38,
      duration: 1.9,
      ease: 'power3.out',
      onComplete: () => {
        cameraState.current.targetZ = cameraState.current.wideZ;
      }
    });
  }, [isRevealed]);

  // -------------------------------------------------------------
  // ANIMATION PHASE 2: Scroll-Driven Dolly into Monitor
  // -------------------------------------------------------------
  useEffect(() => {
    // Only engage scroll dolly if intro is complete
    if (cameraState.current.introFactor < 0.95) return;

    const state = cameraState.current;
    const p = Math.min(1, Math.max(0, scrollProgress));

    // Smooth power curve for cinematic zoom into screen
    const easedP = Math.pow(p, 1.25);
    const targetZ = state.wideZ - easedP * (state.wideZ - state.screenZ);

    gsap.to(state, {
      z: targetZ,
      duration: 0.35,
      ease: 'power1.out',
      overwrite: 'auto'
    });

    if (p >= 0.98 && onTransitionComplete) {
      onTransitionComplete();
    }
  }, [scrollProgress, onTransitionComplete]);

  return (
    <div
      ref={mountRef}
      className="absolute inset-0 w-full h-full pointer-events-none select-none overflow-hidden"
      style={{ opacity: isLoaded ? 1 : 0, transition: 'opacity 0.6s ease' }}
    />
  );
}
