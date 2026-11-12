import bpy

for name in ['Window_Top_Lintel', 'Wall_Left_Section', 'Wall_Right_Section', 'Pillar_1_LeftCenter', 'Pillar_2_RightCenter']:
    obj = bpy.data.objects.get(name)
    if obj:
        print(f"\nObject: {name}")
        print(f"  loc: {obj.location}")
        print(f"  scale: {obj.scale}")
        print(f"  dims: {obj.dimensions}")
        print(f"  modifiers: {[m.name for m in obj.modifiers]}")
        for m in obj.modifiers:
            if m.type == 'BEVEL':
                print(f"    Bevel width={m.width}, segments={m.segments}, limit_method={m.limit_method}, angle_limit={getattr(m, 'angle_limit', None)}")
