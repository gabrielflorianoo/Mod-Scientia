"""Chemistry apparatus: heater, reaction vessel, condenser, electrolysis station, analyser, microscope."""
from common.geometry import Builder


def heater():
    b = Builder("lab_heater")
    b.box(1, 0, 1, 15, 6, 15, "white_enamel", faces={"north": "white_enamel"})
    b.box(1.5, 6, 1.5, 14.5, 7.8, 14.5, "ceramic")              # hot plate
    b.box(2.5, 1.5, 0.6, 13.5, 4.5, 1.0, "graphite")            # control strip
    for x in (4.5, 8):
        b.cylinder(x, 3, 0.6, 1.1, 0.8, "knob", axis="z", seg=10)
        b.box(x - 0.15, 3.6, -0.3, x + 0.15, 4.1, 0.0, "white_enamel")
    b.box(11, 2.4, 0.4, 12.6, 3.6, 0.7, "emissive_amber")       # heating indicator
    b.box(1, 0, 15, 15, 6, 15.2, "vent")
    # beaker sitting on the plate (liquid drawn by the renderer: inner 5.2..10.8, base 8.3)
    b.lathe(8, 8, [(3.2, 0), (3.2, 6.6), (3.5, 6.9)], "glass", seg=16, y0=7.8,
            inner=[(2.85, 0.5), (2.85, 6.9)])
    b.box(10.9, 13.9, 7.6, 11.6, 14.5, 8.4, "glass_frost")      # pouring lip
    coil = Builder("lab_heater_coil")
    for r in (5.4, 3.8, 2.2):
        coil.tube(8, 7.8, 8, r, r - 0.6, 0.15, "emissive_red", seg=20)
    return {"lab_heater": b, "lab_heater_coil": coil}


def vessel():
    b = Builder("reaction_vessel")
    b.box(2, 0, 2, 14, 1.4, 14, "steel_dark")                   # base plate
    b.tube(8, 1.4, 8, 4.4, 4.1, 0.6, "steel", seg=20)            # base collar
    b.lathe(8, 8, [(4.0, 0), (4.0, 9.0), (3.0, 10.4), (1.6, 11.2), (1.6, 13.0)], "glass", seg=20, y0=1.4,
            inner=[(3.6, 0.2), (3.6, 9.0), (2.7, 10.2), (1.3, 11.0), (1.3, 13.0)])
    b.tube(8, 14.0, 8, 2.0, 1.3, 0.5, "steel", seg=16)          # neck flange
    # pressure gauge on a side arm
    b.cylinder(10.5, 11.6, 8, 0.4, 2.6, "steel", axis="x", seg=8)
    b.cylinder(13.0, 11.6, 8, 1.6, 0.6, "steel", axis="x", seg=14, cap_tile="dial")
    # thermometer probe
    b.cylinder(6.3, 4, 6.8, 0.25, 10.5, "chrome", seg=6)
    b.box(5.8, 14.5, 6.3, 6.8, 15.5, 7.3, "red_plastic")
    stopper = Builder("reaction_vessel_stopper")
    stopper.cylinder(8, 13.6, 8, 1.25, 1.9, "rubber", seg=12, r_top=1.55)
    return {"reaction_vessel": b, "reaction_vessel_stopper": stopper}


def condenser():
    b = Builder("condenser")
    # retort stand
    b.box(1.5, 0, 1.5, 6.5, 0.8, 6.5, "steel_dark")
    b.cylinder(3, 0.8, 3, 0.35, 15, "chrome", seg=8)
    b.box(3, 10.5, 2.7, 9.4, 11.1, 3.3, "steel")                # clamp arm
    # still head adapter rising from the heater's beaker
    b.cylinder(8, 0, 8, 0.75, 12.0, "glass", seg=10)
    b.sphere(8, 12.0, 8, 0.95, "glass", seg=10, rings=6)
    # jacketed Liebig condenser sloping down to the receiver
    path = [(8, 12.0, 8), (9.75, 9.9, 9.75), (11.5, 7.8, 11.5)]
    b.sweep(path, 1.35, "glass", seg=12)
    b.sweep(path, 0.45, "glass_frost", seg=8, caps=False)
    b.sweep([(8.9, 11.6, 9.4), (8.3, 12.6, 12.5), (6.0, 12.8, 15.4)], 0.32, "rubber", seg=6)   # coolant in/out hoses
    b.sweep([(11.0, 8.6, 10.7), (13.6, 9.2, 9.0), (15.4, 9.0, 6.0)], 0.32, "rubber", seg=6)
    b.cylinder(11.5, 5.8, 11.5, 0.35, 2.1, "glass", seg=8)      # outlet (stream drawn by renderer)
    # receiving flask (liquid drawn: 9.6..13.4, base 1.2)
    b.lathe(11.5, 11.5, [(2.2, 0), (2.4, 2.0), (2.0, 3.8), (0.8, 4.8), (0.8, 5.6)], "glass", seg=16, y0=1.0,
            inner=[(1.9, 0.2), (2.1, 2.0), (1.7, 3.7), (0.55, 4.7), (0.55, 5.6)])
    return b


def electrolysis():
    b = Builder("electrolysis_station")
    b.box(1, 0, 1, 15, 2, 15, "white_enamel")
    b.box(2.2, 2, 3.2, 13.8, 10, 3.6, "glass")
    b.box(2.2, 2, 12.4, 13.8, 10, 12.8, "glass")
    b.box(2.2, 2, 3.6, 2.6, 10, 12.4, "glass")
    b.box(13.4, 2, 3.6, 13.8, 10, 12.4, "glass")
    b.box(2.6, 2, 3.6, 13.4, 2.2, 12.4, "glass")
    b.box(2.0, 10, 7.4, 14.0, 10.8, 8.6, "graphite")            # electrode bar
    for x, cap in ((5, "red_plastic"), (11, "graphite")):
        b.cylinder(x, 3.0, 8, 0.6, 8.2, "graphite_rod", seg=8)  # electrodes
        b.tube(x, 5.0, 8, 1.7, 1.4, 10.5, "glass", seg=12)       # inverted collection tubes
        b.cylinder(x, 15.5, 8, 1.7, 0.4, "glass_frost", seg=12)
        b.box(x - 0.6, 15.9, 7.4, x + 0.6, 16.3, 8.6, cap)        # gas tap
    # terminals: + at the front (north), − at the back
    b.box(6.8, 0.5, 0.0, 9.2, 3.0, 1.0, "red_plastic", faces={"north": "label_plus"})
    b.box(6.8, 0.5, 15.0, 9.2, 3.0, 16.0, "graphite", faces={"south": "label_minus"})
    b.sweep([(8, 2.6, 0.8), (6, 10.4, 3.0), (5, 10.4, 8)], 0.25, "copper", seg=6)
    b.sweep([(8, 2.6, 15.2), (10, 10.4, 13.0), (11, 10.4, 8)], 0.25, "copper", seg=6)
    return b


def analyzer():
    b = Builder("material_analyzer")
    b.box(1, 0, 2, 15, 10, 15, "graphite", faces={"east": "vent", "west": "vent"})
    b.box(1.5, 0, 1, 14.5, 1, 2, "steel_dark")
    b.box(3, 3.5, 1.4, 8.5, 8.5, 2.0, "steel")                  # sample chamber door
    b.box(3.8, 4.3, 1.2, 7.7, 7.7, 1.4, "glass_amber")          # chamber window (plasma glow)
    b.box(9.5, 3.5, 1.5, 13.5, 8.5, 2.0, "graphite", faces={"north": "screen_spectrum"})
    b.box(10, 1.5, 1.6, 11, 2.5, 2.0, "emissive_green")
    b.box(12, 1.5, 1.6, 13, 2.5, 2.0, "knob")
    # spectrograph: collimator tube and grating housing on top
    b.cylinder(2.5, 11.5, 7, 1.3, 10.5, "steel", axis="x", seg=12)
    b.box(12.5, 10, 4.5, 15, 14, 9.5, "graphite")
    b.cylinder(13.8, 14, 7, 0.8, 1.2, "chrome", seg=8)          # eyepiece
    b.box(2, 10, 11, 9, 11, 14, "pcb")
    return b


def microscope():
    b = Builder("microscope")
    b.box(4, 0, 4, 12, 1.8, 13.5, "graphite")                   # foot
    b.box(6.5, 1.8, 10.5, 9.5, 12.5, 12.8, "white_enamel")      # arm
    b.box(6.8, 12.5, 7.0, 9.2, 14.0, 12.8, "white_enamel")
    b.box(4, 6, 3.5, 12, 6.8, 10, "graphite")                   # stage
    for x in (5.2, 10.8):
        b.box(x - 0.3, 6.8, 5.5, x + 0.3, 7.0, 8.5, "chrome")   # stage clips
    b.box(7.0, 1.8, 6.0, 9.0, 2.6, 8.0, "emissive_amber")       # illuminator
    b.cylinder(8, 3.0, 7, 1.0, 3.0, "graphite", seg=10)         # condenser
    b.cylinder(8, 10.2, 7.2, 1.7, 1.0, "chrome", seg=14)        # nosepiece turret
    for dx, dz, h in ((0, 0, 2.2), (1.0, 0.8, 1.6), (-1.0, 0.8, 1.2)):
        b.cylinder(8 + dx, 10.2 - h, 7.2 + dz, 0.45, h, "chrome", seg=8)
    b.cylinder(8, 11.2, 7.6, 1.15, 3.4, "white_enamel", seg=12, rot=("x", -18, (8, 11.2, 7.6)))   # body tube
    b.cylinder(8, 14.4, 8.7, 0.8, 1.6, "graphite", seg=10, rot=("x", -18, (8, 11.2, 7.6)))
    for x in (5.6, 9.6):
        b.cylinder(x, 4.6, 11.6, 1.2, 0.8, "knob", axis="x", seg=12)   # focus knobs
    return b


def build():
    out = {"reaction_vessel": None}
    out.update(heater())
    out.update(vessel())
    out["condenser"] = condenser()
    out["electrolysis_station"] = electrolysis()
    out["material_analyzer"] = analyzer()
    out["microscope"] = microscope()
    return {k: v for k, v in out.items() if v is not None}
