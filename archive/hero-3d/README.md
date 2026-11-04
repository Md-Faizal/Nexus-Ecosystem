# 3D Showroom Archive (McLaren 765LT & Penthouse Environment)

This directory preserves the complete, production-ready 3D interactive showroom originally built for the hero section:

## Archived Components
- `ThreeScene.jsx`: Complete React Three Fiber (`@react-three/fiber`, `@react-three/drei`, `three`) canvas with cinematic camera paths, car body color switchers, wheel rotations, emissive headlight/taillight masks, and free-camera debug mode.
- `PenthouseEnvironment.jsx`: High-performance procedural penthouse environment with marble floor reflection (`MeshReflectorMaterial`), illuminated light panels, and night city skyline textures.
- `2022_mclaren_765lt.glb`: 3.16 MB Draco-compressed GLB model with WebP textures and optimized mesh geometry.
- `draco/`: Offline Draco WebAssembly decoders (`draco_decoder_gltf.wasm` and `draco_wasm_wrapper.js`).

## How to Restore or Repurpose (e.g. Dedicated 3D Interactive Lab Page)
1. Copy `ThreeScene.jsx` and `PenthouseEnvironment.jsx` into your desired component folder.
2. Copy `2022_mclaren_765lt.glb` into `frontend/public/models/`.
3. Copy `draco/` into `frontend/public/draco/`.
4. Ensure `three`, `@react-three/fiber`, and `@react-three/drei` are installed in `package.json`.
5. Lazy-load `ThreeScene` on demand so it does not block the initial critical page load.
