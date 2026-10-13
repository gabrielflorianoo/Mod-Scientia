"""Machine model builders. Each module exposes build() -> {model_name: Builder}.
Animated parts are separate models (e.g. precision_balance_pan) rendered by block entity renderers;
their pivots must match the constants in client/render/MachineRenderers.java (see PIVOTS)."""
from . import laboratory, chemistry, electrical, optics, industrial

BUILDERS = [laboratory.build, chemistry.build, electrical.build, optics.build, industrial.build]

# Animated part pivots/axes in pixel space, mirrored in MachineRenderers.java.
PIVOTS = {
    "precision_balance_pan": {"type": "translate", "axis": "y", "range_px": [-1.5, 0], "driver": "mass (0–500 g)"},
    "thermal_generator_rotor": {"type": "rotate", "axis": "x", "pivot_px": [8, 9, 8], "driver": "electrical output"},
    "electric_motor_rotor": {"type": "rotate", "axis": "y", "pivot_px": [8, 0, 8], "driver": "voltage"},
    "lab_heater_coil": {"type": "emissive", "driver": "heater burning"},
    "reaction_vessel_stopper": {"type": "toggle", "driver": "sealed"},
}


def build_all():
    models = {}
    for b in BUILDERS:
        for name, builder in b().items():
            if name in models:
                raise ValueError("duplicate model " + name)
            models[name] = builder
    return models
