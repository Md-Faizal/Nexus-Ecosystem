import bpy
import os

blend_path = "E:/Workspaces/core/nexus-fleet/apps/web/public/models/monolithic_amber_vault.blend"
models_dir = "E:/Workspaces/core/nexus-fleet/apps/web/public/models"

print("--- 1. FIXING MISSING TEXTURE PATHS ---")
for img in bpy.data.images:
    base_name = os.path.basename(img.filepath)
    local_target = os.path.join(models_dir, base_name).replace("\\", "/")
    if os.path.exists(local_target) and not os.path.isdir(local_target):
        print(f"Relinking image: {img.name} -> {local_target}")
        img.filepath = local_target
        img.reload()

print("\n--- 2. FIXING SHADER IN M_Concrete_Wall (REMOVING WAVE TEXTURE BANDS) ---")
mat = bpy.data.materials.get("M_Concrete_Wall")
if mat and mat.node_tree:
    mix = mat.node_tree.nodes.get("Mix")
    bsdf = next((n for n in mat.node_tree.nodes if n.type == "BSDF_PRINCIPLED"), None)
    
    if mix:
        # Find and remove any link into Mix Factor or Mix.B from Wave Texture
        for link in list(mat.node_tree.links):
            if link.to_node == mix and (link.to_socket.name in ["B", "Factor"]):
                sock_name = link.to_socket.name
                mat.node_tree.links.remove(link)
                print(f"Removed wave texture link into Mix.{sock_name}")
        
        # Set Mix Factor to 0 so only Input A (uniform concrete) is used
        for inp in mix.inputs:
            if "Factor" in inp.name and hasattr(inp, 'default_value'):
                try:
                    inp.default_value = 0.0
                except Exception:
                    pass
        print("M_Concrete_Wall now uses pure uniform monolithic concrete color.")

print("\n--- 3. UNIFYING WINDOW TOP LINTEL & WALL BULKHEAD GEOMETRY ---")
lintel = bpy.data.objects.get("Window_Top_Lintel")
wall_l = bpy.data.objects.get("Wall_Left_Section")

if lintel and wall_l:
    lintel.scale.x = 10.82  # slight overlap with walls to ensure airtight union
    
    # Remove any existing bevel modifiers to be clean
    for mod in list(lintel.modifiers):
        if mod.type == 'BEVEL':
            lintel.modifiers.remove(mod)
            
    bev = lintel.modifiers.new(name="Bevel", type='BEVEL')
    bev.width = 0.03
    bev.segments = 2
    bev.limit_method = 'ANGLE'
    bev.angle_limit = 0.523599
    print("Window_Top_Lintel configured with matching airtight bevel and slight overlap.")

print("\n--- 4. SAVING UPDATED BLEND FILE ---")
bpy.ops.wm.save_mainfile(filepath=blend_path)
print("Saved monolithic_amber_vault.blend successfully!")
