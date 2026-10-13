"""Kiln and motion track."""
from common.geometry import Builder


def kiln(active):
    b = Builder("insulated_kiln" + ("_active" if active else ""))
    b.box(0, 0, 0, 16, 13, 16, "brick")
    b.box(1, 13, 1, 15, 15, 15, "brick")
    b.box(2.5, 15, 2.5, 13.5, 15.6, 13.5, "steel_dark")
    b.cylinder(11.5, 15.6, 11.5, 1.6, 3.4, "steel_dark", seg=12)       # chimney
    b.box(3.5, 2, -0.6, 12.5, 10, 0, "steel")                           # door
    b.box(5.5, 4.5, -0.8, 10.5, 7.5, -0.6, "emissive_amber" if active else "graphite")   # spy window
    b.box(11.4, 5.2, -1.2, 12.0, 7.0, -0.6, "chrome")                  # handle
    b.box(13, 9, -0.4, 15.5, 11.5, 0, "graphite", faces={"north": "dial"})   # pyrometer
    b.box(0, 0, 0, 16, 1.2, 16, "steel_dark")
    for y in (4, 8, 12):
        b.box(-0.2, y, -0.2, 16.2, y + 0.6, 0.2, "steel_dark")            # steel bands
    return b


def motion_track():
    b = Builder("motion_track")
    b.box(0, 0, 2, 16, 1, 14, "wood")
    b.box(13.5, 1, 6, 15.5, 6.2, 10, "wood_dark")                       # ramp support
    ramp = ("z", 20, (1, 1, 8))
    b.box(1, 1, 6.5, 16.2, 1.6, 9.5, "steel", rot=ramp)                 # track
    b.box(1, 1.6, 6.3, 16.2, 2.2, 6.7, "steel_dark", rot=ramp)
    b.box(1, 1.6, 9.3, 16.2, 2.2, 9.7, "steel_dark", rot=ramp)
    for x in (5, 10):                                                    # light gates
        b.box(x, 1, 5.0, x + 0.8, 7, 5.6, "graphite", rot=ramp)
        b.box(x, 1, 10.4, x + 0.8, 7, 11.0, "graphite", rot=ramp)
        b.box(x, 6.4, 5.0, x + 0.8, 7, 11.0, "graphite", rot=ramp)
        b.box(x + 0.2, 5.2, 5.6, x + 0.6, 5.6, 6.0, "emissive_red", rot=ramp)
    b.box(11.6, 1.6, 7.0, 14.0, 3.0, 9.0, "red_plastic", rot=ramp)      # cart
    for x in (12.0, 13.4):
        for z in (6.8, 9.2):
            b.cylinder(x, 1.9, z - 0.3, 0.45, 0.6, "rubber", axis="z", seg=8, rot=ramp)
    b.box(1, 1, 3, 4, 2.5, 5, "graphite", faces={"up": "screen_digits"})   # timer
    return b


def build():
    return {"insulated_kiln": kiln(False), "insulated_kiln_active": kiln(True), "motion_track": motion_track()}
