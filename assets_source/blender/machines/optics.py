"""Optical bench and data recorder."""
from common.geometry import Builder


def optical_bench():
    b = Builder("optical_bench")
    b.box(0, 10.5, 0, 16, 11.5, 16, "wood")                      # table top
    for (x, z) in ((0.5, 0.5), (14, 0.5), (0.5, 14), (14, 14)):
        b.box(x, 0, z, x + 1.5, 10.5, z + 1.5, "steel_dark")
    b.box(1, 11.5, 7, 15, 12.3, 9, "steel")                      # optical rail
    b.box(1.2, 12.3, 5.5, 4.2, 15.0, 10.5, "graphite")           # light source
    b.cylinder(4.2, 13.7, 8, 1.1, 0.6, "emissive_amber", axis="x", seg=12)
    b.box(7.5, 12.3, 7.4, 8.5, 13.4, 8.6, "steel")               # lens carriage
    b.cylinder(7.7, 15.0, 8, 2.2, 0.6, "lens", axis="x", seg=16)
    b.box(7.8, 13.4, 7.8, 8.2, 12.8 + 2.2, 8.2, "steel")
    b.box(12.5, 12.3, 5.0, 13.1, 15.8, 11.0, "white_enamel")     # screen
    b.box(12.3, 12.3, 7.4, 13.3, 13.0, 8.6, "steel")
    b.box(3, 11.5, 11.5, 7, 11.9, 14.5, "paper")                 # ray diagram sheet
    return b


def recorder():
    b = Builder("data_recorder")
    b.box(2, 0, 3, 14, 11.5, 13, "graphite", faces={"east": "vent"})
    b.box(3, 3.5, 2.6, 13, 10.5, 3.0, "graphite", faces={"north": "screen_graph"})
    for i, x in enumerate((3.5, 5.5, 7.5, 9.5)):
        b.box(x, 1.2, 2.6, x + 1.4, 2.6, 3.0, "emissive_green" if i == 0 else "knob")
    b.box(11.5, 1.2, 2.6, 12.9, 2.6, 3.0, "emissive_red")
    b.cylinder(3, 12.8, 8, 1.4, 10, "paper", axis="x", seg=12)  # chart paper roll
    b.box(2.5, 11.5, 6.5, 3.5, 13.5, 9.5, "steel")
    b.box(12.5, 11.5, 6.5, 13.5, 13.5, 9.5, "steel")
    b.box(4, 11.5, 9.6, 12, 11.7, 13, "paper")                   # chart output
    b.sweep([(14, 4, 8), (15.2, 4, 8), (15.2, 1, 8), (16, 1, 8)], 0.35, "rubber", seg=6)  # probe cable
    return b


def build():
    return {"optical_bench": optical_bench(), "data_recorder": recorder()}
