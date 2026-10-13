"""Preview rendering with Cycles (CPU): neutral studio lighting, consistent camera, simple backdrop.
The material samples the same atlas the game uses, so previews show the in-game texturing."""
import math
import os

import bpy
from mathutils import Vector

from . import raster as R


def reset_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = 24
    scene.cycles.use_denoising = False
    scene.render.resolution_x = 384
    scene.render.resolution_y = 384
    scene.render.film_transparent = False
    scene.view_settings.view_transform = "Standard"
    world = bpy.data.worlds.new("studio")
    world.use_nodes = True
    bg = world.node_tree.nodes["Background"]
    bg.inputs[0].default_value = (0.20, 0.22, 0.25, 1)
    bg.inputs[1].default_value = 0.6
    scene.world = world
    return scene


def atlas_material(atlas_path, emission_path):
    mat = bpy.data.materials.new("scientia_atlas")
    mat.use_nodes = True
    nt = mat.node_tree
    nodes, links = nt.nodes, nt.links
    for n in list(nodes):
        nodes.remove(n)
    out = nodes.new("ShaderNodeOutputMaterial")
    bsdf = nodes.new("ShaderNodeBsdfPrincipled")
    tex = nodes.new("ShaderNodeTexImage")
    tex.image = bpy.data.images.load(atlas_path, check_existing=True)
    tex.interpolation = "Closest"
    emi = nodes.new("ShaderNodeTexImage")
    emi.image = bpy.data.images.load(emission_path, check_existing=True)
    emi.interpolation = "Closest"
    links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    links.new(tex.outputs["Alpha"], bsdf.inputs["Alpha"])
    links.new(emi.outputs["Color"], bsdf.inputs["Emission Color"])
    bsdf.inputs["Emission Strength"].default_value = 2.0
    bsdf.inputs["Roughness"].default_value = 0.55
    links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    try:
        mat.surface_render_method = "BLENDED"
    except AttributeError:
        pass
    return mat


def setup_stage(scene):
    bpy.ops.mesh.primitive_plane_add(size=6, location=(0, 0, -0.001))
    floor = bpy.context.active_object
    fm = bpy.data.materials.new("floor")
    fm.use_nodes = True
    fm.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.32, 0.34, 0.37, 1)
    fm.node_tree.nodes["Principled BSDF"].inputs["Roughness"].default_value = 0.8
    floor.data.materials.append(fm)
    key = bpy.data.lights.new("key", "AREA")
    key.energy = 220
    key.size = 2.5
    ko = bpy.data.objects.new("key", key)
    ko.location = (2.2, 2.6, 3.2)
    ko.rotation_euler = (math.radians(45), 0, math.radians(140))
    scene.collection.objects.link(ko)
    fill = bpy.data.lights.new("fill", "AREA")
    fill.energy = 90
    fill.size = 3
    fo = bpy.data.objects.new("fill", fill)
    fo.location = (-2.8, 1.4, 2.0)
    fo.rotation_euler = (math.radians(60), 0, math.radians(-120))
    scene.collection.objects.link(fo)
    rim = bpy.data.lights.new("rim", "AREA")
    rim.energy = 120
    ro = bpy.data.objects.new("rim", rim)
    ro.location = (0, -3, 2.5)
    ro.rotation_euler = (math.radians(-50), 0, 0)
    scene.collection.objects.link(ro)
    cam = bpy.data.cameras.new("cam")
    cam.lens = 50
    co = bpy.data.objects.new("cam", cam)
    scene.collection.objects.link(co)
    scene.camera = co
    return co


def aim_camera(cam, target=(0, 0, 0.45), distance=3.1, yaw=35, pitch=28):
    """Camera at the front-right (the model front faces Blender +Y) looking at target."""
    y, p = math.radians(yaw), math.radians(pitch)
    t = Vector(target)
    cam.location = t + Vector((math.sin(y) * math.cos(p), math.cos(y) * math.cos(p), math.sin(p))) * distance
    direction = t - cam.location
    cam.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()


def render(path):
    bpy.context.scene.render.filepath = path
    bpy.ops.render.render(write_still=True)


def contact_sheet(paths, out_path, cols=6, cell=192):
    """Assembles previews into one sheet (uses Blender to read PNGs)."""
    import numpy as np
    rows = (len(paths) + cols - 1) // cols
    sheet = R.canvas(cols * cell, rows * cell, (30, 32, 36, 255))
    for i, p in enumerate(paths):
        img = bpy.data.images.load(p, check_existing=False)
        w, h = img.size
        px = np.array(img.pixels[:], dtype=np.float32).reshape(h, w, 4)[::-1]
        ys = (np.arange(cell) * h / cell).astype(int)
        xs = (np.arange(cell) * w / cell).astype(int)
        small = px[ys][:, xs]
        r, c = divmod(i, cols)
        sheet[r * cell:(r + 1) * cell, c * cell:(c + 1) * cell] = small
        bpy.data.images.remove(img)
    R.write_png(out_path, sheet)
