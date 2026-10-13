"""Circuit components. Two-terminal parts have their terminals on the north (front, +) and south faces;
the block's FACING rotates the model so the terminals line up with the circuit."""
from common.geometry import Builder

# Resistor colour code (4-band): value -> (band1, band2, multiplier); tolerance band is gold (±5 %).
RESISTOR_BANDS = {
    10: ("band_brown", "band_black", "band_black"),
    47: ("band_yellow", "band_violet", "band_black"),
    100: ("band_brown", "band_black", "band_brown"),
    470: ("band_yellow", "band_violet", "band_brown"),
    1000: ("band_brown", "band_black", "band_red"),
}


def resistor(value):
    b = Builder(f"resistor_{value}")
    b.cylinder(8, 8, 0, 0.45, 4.2, "copper", axis="z", seg=8)
    b.cylinder(8, 8, 11.8, 0.45, 4.2, "copper", axis="z", seg=8)
    b.cylinder(8, 8, 4.0, 2.2, 8.0, "resistor_body", axis="z", seg=14)
    b.cylinder(8, 8, 3.6, 1.6, 0.6, "resistor_body", axis="z", seg=14)
    b.cylinder(8, 8, 11.8, 1.6, 0.6, "resistor_body", axis="z", seg=14)
    bands = RESISTOR_BANDS[value] + ("band_gold",)
    for z, tile in zip((5.0, 6.6, 8.2, 10.4), bands):
        b.cylinder(8, 8, z, 2.3, 0.8, tile, axis="z", seg=14)
    b.box(6.5, 4, 1, 9.5, 4.6, 15, "graphite")                  # insulating mount
    b.box(7.6, 4.6, 1, 8.4, 7.6, 1.8, "graphite")
    b.box(7.6, 4.6, 14.2, 8.4, 7.6, 15, "graphite")
    return b


def switch(closed):
    b = Builder("circuit_switch_" + ("closed" if closed else "open"))
    b.box(4.5, 4, 0, 11.5, 5.5, 16, "wood_dark")                # insulating base along the circuit axis
    b.box(7, 5.5, 0, 9, 6.2, 3, "copper")                        # front terminal strap
    b.box(7, 5.5, 13, 9, 6.2, 16, "copper")                      # back terminal strap
    b.box(6.4, 5.5, 3.2, 9.6, 8.6, 4.4, "brass")                 # jaw clip
    b.box(6.4, 5.5, 11.4, 9.6, 8.6, 12.6, "brass")               # hinge
    angle = 0 if closed else -55
    pivot = (8, 8, 12)
    b.box(7.6, 7.4, 3.6, 8.4, 8.6, 12.4, "copper", rot=("x", angle, pivot))   # blade
    b.box(7.0, 7.4, 1.6, 9.0, 11.0, 3.6, "red_plastic", rot=("x", angle, pivot))  # insulated handle
    return b


def lamp(lit):
    b = Builder("filament_lamp_" + ("on" if lit else "off"))
    b.box(5, 3, 0, 11, 6, 16, "graphite")                        # lamp holder rail
    b.box(6.5, 3.5, -0.01, 9.5, 5.5, 0.6, "brass")               # front contact
    b.box(6.5, 3.5, 15.4, 9.5, 5.5, 16.01, "brass")              # back contact
    b.cylinder(8, 6, 8, 1.8, 1.6, "brass", seg=12)               # screw cap
    b.lathe(8, 8, [(1.6, 0), (2.6, 1.0), (3.6, 2.8), (3.8, 4.2), (3.2, 5.8), (1.8, 6.8), (0.2, 7.2)],
            "glass_amber" if lit else "glass", seg=16, y0=7.6, closed_bottom=False)
    tile = "emissive_amber" if lit else "filament"
    b.cylinder(7.2, 8.2, 8, 0.12, 3.2, tile, seg=4)
    b.cylinder(8.8, 8.2, 8, 0.12, 3.2, tile, seg=4)
    b.box(7.1, 11.3, 7.9, 8.9, 11.5, 8.1, tile)                   # coiled filament
    return b


def battery():
    b = Builder("battery")
    b.box(2, 0, 2.5, 14, 10.5, 13.5, "graphite", faces={"east": "label_12v", "west": "label_12v"})
    b.box(2, 4, 2.4, 14, 6.5, 2.5, "white_enamel")               # label band (front)
    b.box(2.4, 10.5, 2.9, 13.6, 11.5, 13.1, "steel_dark")        # lid
    b.cylinder(8, 11.5, 5, 1.0, 1.2, "red_plastic", seg=10)      # + post
    b.cylinder(8, 11.5, 11, 1.0, 1.2, "graphite", seg=10)        # − post
    b.box(7, 9, 0, 9, 12.0, 2.5, "copper", faces={"north": "label_plus"})   # + strap to the front face
    b.box(7, 9, 13.5, 9, 12.0, 16, "copper", faces={"south": "label_minus"})
    for x in (3.5, 6, 10, 12.5):
        b.box(x, 11.5, 7.5, x + 0.8, 11.7, 8.5, "steel")          # cell vent caps
    return b


def motor():
    b = Builder("electric_motor")
    b.box(2, 0, 2, 14, 2, 14, "steel_dark")                      # mounting foot
    b.cylinder(8, 2, 8, 5.0, 8.0, "graphite", seg=20)            # housing
    for y in (3, 5, 7, 9):
        b.tube(8, y, 8, 5.4, 4.9, 0.6, "steel", seg=20)          # cooling fins
    b.cylinder(8, 10, 8, 4.2, 0.8, "steel", seg=20)              # end bell
    b.box(6.5, 3, 0, 9.5, 6, 3.4, "red_plastic", faces={"north": "label_plus"})
    b.box(6.5, 3, 12.6, 9.5, 6, 16, "graphite", faces={"south": "label_minus"})
    rotor = Builder("electric_motor_rotor")
    rotor.cylinder(8, 10.8, 8, 0.6, 3.2, "chrome", seg=10)
    rotor.cylinder(8, 12.6, 8, 1.2, 0.8, "steel", seg=10)
    for k, rot in enumerate((0, 90, 180, 270)):
        rotor.box(8.6, 12.7, 7.4, 13.6, 13.3, 8.6, "steel", rot=("y", rot, (8, 13, 8)))
    return {"electric_motor": b, "electric_motor_rotor": rotor}


def generator():
    b = Builder("thermal_generator")
    b.box(0, 0, 1, 16, 2.5, 15, "steel_dark")                    # frame
    b.box(1, 2.5, 2, 7, 13, 14, "graphite", faces={"west": "vent"})   # firebox
    b.box(2, 3.5, 1.6, 6, 7.5, 2, "ash_grate")                    # fire door grate (glows when burning)
    b.box(2.5, 8.5, 1.7, 5.5, 11.5, 2.0, "dial")
    b.cylinder(4, 13, 11, 1.1, 3.5, "steel", seg=10)             # flue
    b.cylinder(8.6, 9, 8, 4.4, 6.4, "steel", axis="x", seg=20, cap_tile="vent")   # dynamo housing
    b.box(9.5, 2.5, 4, 14.5, 4.8, 12, "steel_dark")
    b.tube(8.6, 9, 8, 4.7, 4.3, 0.5, "copper", axis="x", seg=20)
    b.tube(14.5, 9, 8, 4.7, 4.3, 0.5, "copper", axis="x", seg=20)
    # terminals: + front, − back
    b.box(11, 2.5, 0.0, 13, 5, 1.0, "red_plastic", faces={"north": "label_plus"})
    b.box(11, 2.5, 15.0, 13, 5, 16.0, "graphite", faces={"south": "label_minus"})
    rotor = Builder("thermal_generator_rotor")
    rotor.cylinder(7.2, 9, 8, 5.2, 1.0, "steel_dark", axis="x", seg=24)     # flywheel rim
    rotor.cylinder(7.0, 9, 8, 1.0, 1.6, "chrome", axis="x", seg=10)         # hub/shaft
    for ang in (0, 60, 120):
        rotor.box(7.3, 8.6, 3.4, 8.1, 9.4, 12.6, "steel", rot=("x", ang, (8, 9, 8)))   # spokes
    rotor.box(7.1, 13.0, 7.4, 8.3, 14.2, 8.6, "hazard")                     # marker shows rotation
    return {"thermal_generator": b, "thermal_generator_rotor": rotor}


def solar():
    b = Builder("solar_panel")
    b.box(0, 0, 0, 16, 1, 16, "steel_dark")
    b.box(6.5, 1, 6.5, 9.5, 3, 9.5, "steel")                     # pedestal
    tilt = ("x", 12, (8, 3, 8))
    b.box(0.2, 3, 0.2, 15.8, 4, 15.8, "steel", faces={"up": "solar_cell"}, rot=tilt)
    b.box(5, 1, 0, 11, 2.2, 1.2, "graphite", faces={"north": "label_plus"})   # junction leads
    b.box(5, 1, 14.8, 11, 2.2, 16, "graphite", faces={"south": "label_minus"})
    return b


def build():
    out = {}
    for v in RESISTOR_BANDS:
        out[f"resistor_{v}"] = resistor(v)
    out["circuit_switch_open"] = switch(False)
    out["circuit_switch_closed"] = switch(True)
    out["filament_lamp_off"] = lamp(False)
    out["filament_lamp_on"] = lamp(True)
    out["battery"] = battery()
    out.update(motor())
    out.update(generator())
    out["solar_panel"] = solar()
    return out
