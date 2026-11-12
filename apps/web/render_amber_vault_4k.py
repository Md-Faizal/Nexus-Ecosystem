r"""
render_amber_vault_4k.py - Automated High-Resolution 4K Render Engine for Monolithic Amber Vault

Usage via Blender:
  & "E:\Blender\blender.exe" -b "E:\Workspaces\core\nexus-fleet\apps\web\public\models\monolithic_amber_vault.blend" --python "E:\Workspaces\core\nexus-fleet\apps\web\render_amber_vault_4k.py" -- [options]

Options:
  --sequence scroll|intro|all   (default: all)
  --start-frame N               (default: 0)
  --end-frame N                 (default: -1, all)
  --res-x 3840 --res-y 2160     (default: 3840x2160 4K UHD)
  --samples 10                  (default: 10 with OIDN)
  --force                       (overwrite existing frames)
"""

import bpy
import mathutils
import sys
import os
import time

def parse_args():
    argv = sys.argv
    args = {
        'sequence': 'scroll',
        'start_frame': 0,
        'end_frame': -1,
        'res_x': 3840,
        'res_y': 2160,
        'samples': 10,
        'force': False
    }
    if "--" in argv:
        custom_args = argv[argv.index("--") + 1:]
        i = 0
        while i < len(custom_args):
            opt = custom_args[i]
            if opt == '--sequence' and i + 1 < len(custom_args):
                args['sequence'] = custom_args[i+1]
                i += 2
            elif opt == '--start-frame' and i + 1 < len(custom_args):
                args['start_frame'] = int(custom_args[i+1])
                i += 2
            elif opt == '--end-frame' and i + 1 < len(custom_args):
                args['end_frame'] = int(custom_args[i+1])
                i += 2
            elif opt == '--res-x' and i + 1 < len(custom_args):
                args['res_x'] = int(custom_args[i+1])
                i += 2
            elif opt == '--res-y' and i + 1 < len(custom_args):
                args['res_y'] = int(custom_args[i+1])
                i += 2
            elif opt == '--samples' and i + 1 < len(custom_args):
                args['samples'] = int(custom_args[i+1])
                i += 2
            elif opt == '--force':
                args['force'] = True
                i += 1
            else:
                i += 1
    return args

def configure_render_engine(scene, res_x, res_y, samples):
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'CPU'
    scene.cycles.samples = samples
    scene.cycles.use_denoising = True
    
    # Optimized architectural path tracing bounces
    scene.cycles.max_bounces = 4
    scene.cycles.diffuse_bounces = 2
    scene.cycles.glossy_bounces = 2
    scene.cycles.transmission_bounces = 3
    scene.cycles.volume_bounces = 1
    scene.cycles.transparent_max_bounces = 4
    scene.cycles.caustics_reflective = False
    scene.cycles.caustics_refractive = False

    scene.render.resolution_x = res_x
    scene.render.resolution_y = res_y
    scene.render.resolution_percentage = 100

def smoothstep(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3.0 - 2.0 * t)

def get_scroll_camera_transform(progress):
    # progress: 0.0 -> 1.0 (frame 00 to frame 39)
    # Start: Wide vault view at Y = -11.50, Z = 1.38
    # End: Tight monitor framing at Y = 0.88, Z = 1.37
    t = smoothstep(progress)
    start_loc = mathutils.Vector((0.0, -11.50, 1.38))
    end_loc = mathutils.Vector((0.0, 0.88, 1.37))
    loc = start_loc.lerp(end_loc, t)
    rot = mathutils.Euler((1.5499, 0.0, 0.0), 'XYZ')
    return loc, rot

def get_intro_camera_transform(progress):
    # progress: 0.0 -> 1.0 (intro 00 to intro 15)
    # Starts close on desk (Y = -2.80, Z = 1.37)
    # Dollies back to wide vault view (Y = -11.50, Z = 1.38)
    # Uses cubic ease-out for smooth settle
    t = 1.0 - (1.0 - progress) ** 2.8
    start_loc = mathutils.Vector((0.0, -2.80, 1.37))
    end_loc = mathutils.Vector((0.0, -11.50, 1.38))
    loc = start_loc.lerp(end_loc, t)
    rot = mathutils.Euler((1.5499, 0.0, 0.0), 'XYZ')
    return loc, rot

def render_frame(scene, cam, loc, rot, out_jpg, out_webp, force=False):
    if not force and os.path.exists(out_jpg) and os.path.exists(out_webp):
        print(f"  [SKIP] Frame exists: {os.path.basename(out_jpg)}")
        return

    cam.location = loc
    cam.rotation_euler = rot

    # 1. Render JPG
    scene.render.image_settings.file_format = 'JPEG'
    scene.render.image_settings.quality = 90
    scene.render.filepath = out_jpg
    t0 = time.time()
    bpy.ops.render.render(write_still=True)
    dt = time.time() - t0

    # 2. Convert / Save WebP
    try:
        img = bpy.data.images.load(out_jpg)
        img.file_format = 'WEBP'
        img.save_render(out_webp)
        bpy.data.images.remove(img)
    except Exception as e:
        print(f"  Warning converting WebP: {e}")

    print(f"  Rendered {os.path.basename(out_jpg)} & {os.path.basename(out_webp)} in {dt:.1f}s")

def main():
    args = parse_args()
    print("=" * 60)
    print("4K AMBER VAULT BATCH RENDERER")
    print(f"Resolution: {args['res_x']}x{args['res_y']} | Samples: {args['samples']}")
    print(f"Sequence: {args['sequence']} | Force: {args['force']}")
    print("=" * 60)

    scene = bpy.context.scene
    cam = scene.camera or bpy.data.objects.get("Main_EyeLevel_Camera")
    if not cam:
        print("ERROR: Main camera not found!")
        return

    configure_render_engine(scene, args['res_x'], args['res_y'], args['samples'])

    base_dir = "E:/Workspaces/core/nexus-fleet/apps/web/public/sequence"
    os.makedirs(base_dir, exist_ok=True)
    os.makedirs(os.path.join(base_dir, "intro"), exist_ok=True)

    # 1. Render Scroll Sequence
    if args['sequence'] in ['scroll', 'all']:
        total_frames = 40
        start = max(0, args['start_frame'])
        end = total_frames if args['end_frame'] < 0 else min(total_frames, args['end_frame'] + 1)
        print(f"\n--- RENDERING SCROLL FRAMES ({start} to {end - 1}) ---")
        for i in range(start, end):
            p = i / (total_frames - 1)
            loc, rot = get_scroll_camera_transform(p)
            num_str = f"{i:02d}"
            out_jpg = os.path.join(base_dir, f"frame_{num_str}.jpg")
            out_webp = os.path.join(base_dir, f"frame_{num_str}.webp")
            render_frame(scene, cam, loc, rot, out_jpg, out_webp, args['force'])

    # 2. Render Intro Sequence
    if args['sequence'] in ['intro', 'all']:
        total_intro = 16
        start = max(0, args['start_frame'])
        end = total_intro if args['end_frame'] < 0 else min(total_intro, args['end_frame'] + 1)
        print(f"\n--- RENDERING INTRO FRAMES ({start} to {end - 1}) ---")
        for i in range(start, end):
            p = i / (total_intro - 1)
            loc, rot = get_intro_camera_transform(p)
            num_str = f"{i:02d}"
            out_jpg = os.path.join(base_dir, "intro", f"intro_{num_str}.jpg")
            out_webp = os.path.join(base_dir, "intro", f"intro_{num_str}.webp")
            render_frame(scene, cam, loc, rot, out_jpg, out_webp, args['force'])

    print("\nRender pipeline finished successfully!")

if __name__ == "__main__":
    main()
