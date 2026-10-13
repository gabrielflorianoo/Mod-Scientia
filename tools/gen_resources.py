#!/usr/bin/env python3
"""Generates the JSON resources that accompany the Blender-built models: blockstates, item models,
wire models, loot tables, tool tags, crafting recipes and the English language file.

Usage: python3 tools/gen_resources.py          (idempotent; run after assets_source/blender/generate_all.py)
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/scientia")
DATA = os.path.join(ROOT, "src/main/resources/data")

FACING6 = {"north": (0, 0), "east": (0, 90), "south": (0, 180), "west": (0, 270), "up": (270, 0), "down": (90, 0)}
FACING4 = {k: v for k, v in FACING6.items() if k not in ("up", "down")}

# block id -> (english name, model for the item, kind)
BLOCKS = {
    "scientific_workbench": ("Scientific Workbench", "scientific_workbench", "screen"),
    "precision_balance": ("Precision Balance", "precision_balance", "machine"),
    "graduated_cylinder": ("Graduated Cylinder", "graduated_cylinder", "machine"),
    "lab_heater": ("Laboratory Heater", "lab_heater", "machine"),
    "condenser": ("Condenser", "condenser", "machine"),
    "reaction_vessel": ("Reaction Vessel", "reaction_vessel", "machine"),
    "material_analyzer": ("Material Analyzer", "material_analyzer", "machine"),
    "microscope": ("Microscope", "microscope", "machine"),
    "data_recorder": ("Data Recorder", "data_recorder", "machine"),
    "insulated_kiln": ("Insulated Kiln", "insulated_kiln", "kiln"),
    "optical_bench": ("Optical Bench", "optical_bench", "screen"),
    "motion_track": ("Motion Track", "motion_track", "screen"),
    "copper_wire": ("Copper Wire", "copper_wire_inventory", "wire"),
    "battery": ("Battery (12 V)", "battery", "machine"),
    "resistor": ("Resistor", "resistor_100", "resistor"),
    "circuit_switch": ("Circuit Switch", "circuit_switch_open", "switch"),
    "filament_lamp": ("Filament Lamp (12 V, 6 W)", "filament_lamp_off", "lamp"),
    "electric_motor": ("Electric Motor", "electric_motor", "machine"),
    "thermal_generator": ("Thermal Generator", "thermal_generator", "machine"),
    "solar_panel": ("Solar Panel", "solar_panel", "machine"),
    "electrolysis_station": ("Electrolysis Station", "electrolysis_station", "machine"),
    "reinforced_concrete": ("Reinforced Concrete", "reinforced_concrete", "cube"),
}
WOODEN = {"scientific_workbench", "motion_track"}
GLASSY = {"graduated_cylinder", "condenser", "reaction_vessel", "filament_lamp", "electrolysis_station"}

ITEMS = {
    "science_journal": "Science Journal", "calculator": "Scientific Calculator", "thermometer": "Digital Thermometer",
    "multimeter": "Multimeter", "ph_meter": "pH Meter", "beaker": "Beaker", "gas_canister": "Gas Canister",
    "mortar_and_pestle": "Mortar and Pestle", "salt_crystals": "Salt Crystals (NaCl, 10 g)",
    "calcite_powder": "Calcite Powder (CaCO₃, 10 g)", "quicklime": "Quicklime (CaO, 10 g)",
    "slaked_lime": "Slaked Lime (Ca(OH)₂, 10 g)", "copper_oxide_powder": "Copper(II) Oxide (CuO, 10 g)",
    "steel_ingot": "Steel Ingot", "portland_cement": "Portland Cement",
    "steel_pickaxe": "Steel Pickaxe", "steel_axe": "Steel Axe", "steel_shovel": "Steel Shovel",
    "steel_sword": "Steel Sword", "steel_hoe": "Steel Hoe",
}
TOOLS = {"steel_pickaxe", "steel_axe", "steel_shovel", "steel_sword", "steel_hoe"}
FILLED = {"beaker", "gas_canister"}

LANG_EXTRA = {
    "itemGroup.scientia": "Scientia",
    "item.scientia.beaker.filled": "Beaker of %s",
    "item.scientia.beaker.tip": "Right-click water to fill (ocean water is salty). Holds 250 mL.",
    "item.scientia.gas_canister.filled": "Gas Canister (%s)",
    "item.scientia.thermometer.tip": "Right-click a block or machine to measure temperature (±0.5 K).",
    "item.scientia.multimeter.tip": "Right-click a circuit component to measure voltage, current, resistance and power.",
    "item.scientia.ph_meter.tip": "Right-click a vessel, or use with a filled beaker in your other hand.",
    "scientia.msg.element": "Element discovered: %s (%s), Z = %s",
    "scientia.msg.experiment": "Experiment completed: %s",
    "scientia.msg.research": "Research complete: %s",
    "scientia.msg.unlocks": "Unlocked: %s",
    "scientia.msg.density": "%s: density = %s",
    "scientia.msg.identified": "Identified as %s (reference density %s g/cm³).",
    "scientia.msg.ambiguous": "Also consistent with: %s. Another test (e.g. a spectrum) can tell them apart.",
    "scientia.msg.nomatch": "Density does not match the reference value (%s g/cm³) within the uncertainty — measure again.",
    "scientia.msg.hypothesis_supported": "Hypothesis supported by your data.",
    "scientia.msg.hypothesis_refuted": "Hypothesis not supported — a useful result: the data say something else.",
    "scientia.msg.locked": "Research this concept first (see the journal).",
    "scientia.msg.missing": "Missing ingredients.",
    "scientia.msg.cart_static": "The cart does not move: friction is at least as strong as the slope's pull (tan θ ≤ μ).",
    "scientia.msg.resistor": "Resistance set to %s Ω",
    "scientia.msg.no_sample_data": "This item has no data in the science database.",
    "scientia.msg.tared": "Note: the balance is tared, so this reading is a difference.",
    "scientia.msg.recorded_mass": "Recorded mass: %s g ± 0.01 g",
    "scientia.msg.cylinder_empty": "Fill the cylinder with water first.",
    "scientia.msg.cylinder_overflow": "The water would overflow: use a smaller sample.",
    "scientia.msg.recorded_volume": "Reading %s mL − initial %s mL = displaced volume %s mL",
    "scientia.msg.floating": "The sample floats, so it displaced only its own weight of water. Density from this volume will be wrong.",
    "scientia.msg.full": "The beaker is full.",
    "scientia.msg.run_mass": "Mass before %s g → after %s g (change %s g)",
    "scientia.msg.gas_escaped": "Mass decreased because gas escaped from the open vessel.",
    "scientia.msg.open_pressure": "The vessel is open: pressure equals the atmosphere (101.3 kPa).",
    "scientia.msg.output_full": "Empty the output slot first.",
    "scientia.msg.nothing_to_filter": "Nothing undissolved to filter out.",
    "scientia.msg.spectrum": "%s: emission lines of %s",
    "scientia.msg.no_slide": "Place a specimen on the stage.",
    "scientia.msg.need_points": "Record at least %s points first.",
    "scientia.msg.cooling_needs_heater": "Place the recorder next to a Laboratory Heater and record its temperature.",
    "scientia.msg.cooling_heater_on": "Switch the heater off (or let it run out of fuel) to measure cooling.",
    "scientia.msg.cooling_more_data": "Record at least 10 points over 60 s while the sample cools well above room temperature.",
    "scientia.msg.ph_nothing": "Nothing to measure here.",
    "scientia.msg.ph_dry": "No aqueous solution: pH is only defined in water.",
    "scientia.msg.temperature": "Temperature: %s ± %s K",
    "scientia.msg.temp_lava": "Lava: game approximation of basaltic lava (≈1100–1200 °C in reality).",
    "scientia.msg.temp_flame": "Flame: approximate value (wood flames are roughly 600–1100 °C).",
    "scientia.msg.temp_estimate": "Approximate value for this Minecraft block.",
    "scientia.msg.temp_air": "Air temperature (derived from the biome: a game approximation).",
    "scientia.msg.multimeter_nothing": "Not part of a circuit.",
    "scientia.msg.unpowered": "%s: not connected to a powered circuit.",
}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(data, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def rot(model, xy):
    v = {"model": f"scientia:block/{model}"}
    if xy[0]:
        v["x"] = xy[0]
    if xy[1]:
        v["y"] = xy[1]
    return v


def blockstates():
    bs = os.path.join(ASSETS, "blockstates")
    for block, (_, _, kind) in BLOCKS.items():
        variants = {}
        if kind == "machine":
            for f, xy in FACING6.items():
                for active in ("false", "true"):
                    variants[f"active={active},facing={f}"] = rot(block, xy)
        elif kind == "kiln":
            for f, xy in FACING6.items():
                variants[f"active=false,facing={f}"] = rot("insulated_kiln", xy)
                variants[f"active=true,facing={f}"] = rot("insulated_kiln_active", xy)
        elif kind == "screen":
            for f, xy in FACING4.items():
                variants[f"facing={f}"] = rot(block, xy)
        elif kind == "resistor":
            for f, xy in FACING6.items():
                for i, val in enumerate((10, 47, 100, 470, 1000)):
                    variants[f"facing={f},value={i}"] = rot(f"resistor_{val}", xy)
        elif kind == "switch":
            for f, xy in FACING6.items():
                for closed in ("false", "true"):
                    for powered in ("false", "true"):
                        model = "circuit_switch_closed" if "true" in (closed, powered) else "circuit_switch_open"
                        variants[f"closed={closed},facing={f},powered={powered}"] = rot(model, xy)
        elif kind == "lamp":
            for f, xy in FACING6.items():
                for lvl in range(16):
                    variants[f"broken=false,facing={f},level={lvl}"] = rot("filament_lamp_on" if lvl else "filament_lamp_off", xy)
                    variants[f"broken=true,facing={f},level={lvl}"] = rot("filament_lamp_broken", xy)
        elif kind == "cube":
            variants[""] = {"model": f"scientia:block/{block}"}
        elif kind == "wire":
            parts = [{"apply": {"model": "scientia:block/copper_wire_core"}}]
            for d, xy in FACING6.items():
                parts.append({"when": {d: "true"}, "apply": dict(rot("copper_wire_side", xy), uvlock=False)})
            write(os.path.join(bs, block + ".json"), {"multipart": parts})
            continue
        write(os.path.join(bs, block + ".json"), {"variants": variants})


def wire_models():
    md = os.path.join(ASSETS, "models", "block")
    tex = {"particle": "scientia:block/copper_wire", "wire": "scientia:block/copper_wire", "insulation": "scientia:block/wire_insulation"}

    def cube(fr, to, t):
        return {"from": fr, "to": to, "faces": {f: {"texture": t} for f in ("north", "south", "east", "west", "up", "down")}}

    write(os.path.join(md, "copper_wire_core.json"), {"textures": tex, "elements": [cube([6, 6, 6], [10, 10, 10], "#insulation"),
                                                                                    cube([6.5, 6.5, 6.5], [9.5, 9.5, 9.5], "#wire")]})
    write(os.path.join(md, "copper_wire_side.json"), {"textures": tex, "elements": [cube([6.5, 6.5, 0], [9.5, 9.5, 6], "#insulation"),
                                                                                    cube([7, 7, -0.01], [9, 9, 0.5], "#wire")]})
    write(os.path.join(md, "reinforced_concrete.json"), {"parent": "minecraft:block/cube_all",
                                                         "textures": {"all": "scientia:block/reinforced_concrete"}})
    write(os.path.join(md, "copper_wire_inventory.json"), {
        "parent": "block/block", "textures": tex,
        "elements": [cube([6, 6, 0], [10, 10, 16], "#insulation"), cube([7, 7, -0.01], [9, 9, 16.01], "#wire")]})


def item_models():
    im = os.path.join(ASSETS, "models", "item")
    for block, (_, model, _) in BLOCKS.items():
        write(os.path.join(im, block + ".json"), {"parent": f"scientia:block/{model}"})
    for item in ITEMS:
        data = {"parent": "minecraft:item/generated", "textures": {"layer0": f"scientia:item/{item}"}}
        if item in FILLED:
            data["overrides"] = [{"predicate": {"scientia:filled": 1}, "model": f"scientia:item/{item}_filled"}]
            write(os.path.join(im, item + "_filled.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"scientia:item/{item}_filled"}})
        if item in ("thermometer", "multimeter", "ph_meter", "calculator") or item in TOOLS:
            data["parent"] = "minecraft:item/handheld"
        write(os.path.join(im, item + ".json"), data)


def loot_and_tags():
    for block in BLOCKS:
        write(os.path.join(DATA, "scientia/loot_tables/blocks", block + ".json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"scientia:{block}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    pick = sorted(b for b in BLOCKS if b not in WOODEN)
    write(os.path.join(DATA, "minecraft/tags/blocks/mineable/pickaxe.json"), {"replace": False, "values": [f"scientia:{b}" for b in pick]})
    write(os.path.join(DATA, "minecraft/tags/blocks/mineable/axe.json"), {"replace": False, "values": [f"scientia:{b}" for b in sorted(WOODEN)]})
    write(os.path.join(DATA, "minecraft/tags/blocks/needs_iron_tool.json"), {"replace": False, "values": ["scientia:reinforced_concrete"]})


def recipes():
    rd = os.path.join(DATA, "scientia/recipes")

    def shaped(name, pattern, key, result, count=1, nbt=None):
        res = {"item": f"scientia:{result}", "count": count}
        if nbt:
            res["nbt"] = nbt
        write(os.path.join(rd, name + ".json"), {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
                                                 "key": {k: ({"tag": v[1:]} if v.startswith("#") else {"item": v}) for k, v in key.items()},
                                                 "result": res})

    def shapeless(name, ingredients, result, count=1, nbt=None):
        res = {"item": f"scientia:{result}", "count": count}
        if nbt:
            res["nbt"] = nbt
        write(os.path.join(rd, name + ".json"), {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                 "ingredients": [{"tag": i[1:]} if i.startswith("#") else {"item": i} for i in ingredients],
                                                 "result": res})

    shapeless("science_journal", ["minecraft:book", "minecraft:ink_sac", "minecraft:feather"], "science_journal")
    shaped("scientific_workbench", ["III", "PCP", "P P"], {"I": "minecraft:iron_ingot", "P": "#minecraft:planks", "C": "minecraft:crafting_table"}, "scientific_workbench")
    shaped("precision_balance", [" G ", "IRI", "SSS"], {"G": "minecraft:glass", "I": "minecraft:iron_ingot", "R": "minecraft:redstone",
                                                        "S": "minecraft:smooth_stone_slab"}, "precision_balance")
    shaped("beaker", ["G G", "GGG"], {"G": "minecraft:glass"}, "beaker", 2)
    shaped("calculator", ["GGG", "BRB", "SSS"], {"G": "minecraft:glass_pane", "B": "minecraft:stone_button", "R": "minecraft:redstone",
                                                 "S": "minecraft:stone"}, "calculator")
    shapeless("mortar_and_pestle", ["minecraft:bowl", "minecraft:smooth_stone", "minecraft:stick"], "mortar_and_pestle")
    shaped("optical_bench", ["GTG", "PPP", "I I"], {"G": "minecraft:glass_pane", "T": "minecraft:redstone_torch", "P": "#minecraft:planks",
                                                    "I": "minecraft:iron_ingot"}, "optical_bench")
    shaped("motion_track", ["  R", " RP", "PPP"], {"R": "minecraft:rail", "P": "#minecraft:planks"}, "motion_track")
    shapeless("calcite_powder", ["minecraft:calcite", "scientia:mortar_and_pestle"], "calcite_powder", 10)
    # Steel tools: same shapes as vanilla tools.
    S, T = "scientia:steel_ingot", "minecraft:stick"
    shaped("steel_pickaxe", ["SSS", " T ", " T "], {"S": S, "T": T}, "steel_pickaxe")
    shaped("steel_axe", ["SS", "ST", " T"], {"S": S, "T": T}, "steel_axe")
    shaped("steel_shovel", ["S", "T", "T"], {"S": S, "T": T}, "steel_shovel")
    shaped("steel_sword", ["S", "S", "T"], {"S": S, "T": T}, "steel_sword")
    shaped("steel_hoe", ["SS", " T", " T"], {"S": S, "T": T}, "steel_hoe")
    # Concrete: about 1 part cement : 2 sand : 3 gravel by volume (here 1 : 4 : 4 items) → concrete powder that
    # sets in water exactly as vanilla concrete does.
    write(os.path.join(rd, "concrete_from_cement.json"), {"type": "minecraft:crafting_shapeless", "category": "building",
        "ingredients": [{"item": "scientia:portland_cement"}] + [{"tag": "minecraft:sand"}] * 4 + [{"item": "minecraft:gravel"}] * 4,
        "result": {"item": "minecraft:light_gray_concrete_powder", "count": 8}})
    # Reinforced concrete: steel bars (rebar) carry tension, concrete carries compression.
    write(os.path.join(rd, "reinforced_concrete.json"), {"type": "minecraft:crafting_shaped", "category": "building",
        "pattern": ["CCC", "CSC", "CCC"], "key": {"C": {"item": "minecraft:light_gray_concrete"}, "S": {"item": "scientia:steel_ingot"}},
        "result": {"item": "scientia:reinforced_concrete", "count": 8}})
    # Vinegar (≈5 % acetic acid): a gameplay shortcut for fermenting fruit sugars (yeast → ethanol, then
    # acetic acid bacteria → acetic acid). 250 mL ≈ 238.6 g water + 12.6 g acetic acid.
    shapeless("vinegar_beaker", ["scientia:beaker", "minecraft:apple", "minecraft:apple", "minecraft:sugar"], "beaker", 1,
              "{Contents:{water:13.244d,acetic_acid:0.2098d}}")


def lang():
    data = {}
    for block, (name, _, _) in BLOCKS.items():
        data[f"block.scientia.{block}"] = name
    for item, name in ITEMS.items():
        data[f"item.scientia.{item}"] = name
    data.update(LANG_EXTRA)
    write(os.path.join(ASSETS, "lang", "en_us.json"), dict(sorted(data.items())))


def main():
    blockstates()
    wire_models()
    item_models()
    loot_and_tags()
    recipes()
    lang()
    print("resources generated")


if __name__ == "__main__":
    main()
