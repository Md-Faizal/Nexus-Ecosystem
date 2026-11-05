import bpy
from mathutils import Vector

TARGET_WIDTH_RATIO = 0.72
TARGET_HEIGHT_RATIO = 0.64
THICKNESS_SCALE = 1.6


def get_active_mesh():
    obj = bpy.context.active_object
    if obj is None:
        raise RuntimeError("Select the wall/building mesh first.")
    if obj.type != "MESH":
        raise RuntimeError(f"Active object '{obj.name}' is not a mesh.")
    return obj


def get_wall_axes(obj):
    dims = obj.dimensions
    max_axis = max(range(3), key=lambda i: dims[i])
    min_axis = min(range(3), key=lambda i: dims[i])
    other_axes = [i for i in range(3) if i not in (max_axis, min_axis)]
    return max_axis, min_axis, other_axes[0]


def create_cutout(obj):
    dims = obj.dimensions
    max_axis, min_axis, mid_axis = get_wall_axes(obj)

    hole_dim = [dims[i] * 0.12 for i in range(3)]
    hole_dim[max_axis] = dims[max_axis] * TARGET_WIDTH_RATIO
    hole_dim[mid_axis] = dims[mid_axis] * TARGET_HEIGHT_RATIO
    hole_dim[min_axis] = dims[min_axis] * THICKNESS_SCALE

    bpy.ops.object.select_all(action="DESELECT")
    bpy.ops.mesh.primitive_cube_add(location=obj.location)
    cutter = bpy.context.active_object
    cutter.name = "OpeningCutter"
    cutter.scale = tuple(hole_dim[i] / 2 for i in range(3))
    cutter.rotation_euler = obj.rotation_euler.copy()

    # Move the cutter to the object center, preserving the wall's transform.
    cutter.matrix_world = obj.matrix_world.copy()
    cutter.location = obj.location

    # Make sure the cutter is large enough to cut cleanly through the wall thickness.
    cutter.modifiers.new(name="SolidifyCut", type="SOLIDIFY")
    cutter.modifiers["SolidifyCut"].thickness = max(dims[min_axis] * 1.5, 0.1)
    cutter.modifiers["SolidifyCut"].offset = 0

    # Apply only to the selected wall.
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj

    mod = obj.modifiers.new(name="WallOpening", type="BOOLEAN")
    mod.operation = "DIFFERENCE"
    mod.object = cutter

    bpy.context.view_layer.update()
    bpy.ops.object.modifier_apply(modifier=mod.name)

    bpy.data.objects.remove(cutter, do_unlink=True)


def main():
    try:
        obj = get_active_mesh()
        create_cutout(obj)
        print(f"Created opening in: {obj.name}")
    except Exception as exc:
        print(f"ERROR: {exc}")
        raise


if __name__ == "__main__":
    main()
