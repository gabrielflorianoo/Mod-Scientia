"""Laboratory furniture and measuring instruments."""
from common.geometry import Builder


def workbench():
    b = Builder("scientific_workbench")
    # table top with a white working surface inset
    b.box(0, 13, 0, 16, 15, 16, "wood", faces={"up": "wood"})
    b.box(1.5, 15, 1.5, 9.5, 15.2, 9.5, "white_enamel")
    for (x, z) in ((0.5, 0.5), (13.5, 0.5), (0.5, 13.5), (13.5, 13.5)):
        b.box(x, 0, z, x + 2, 13, z + 2, "steel_dark")
    b.box(1, 4, 1, 15, 5, 15, "wood_dark")                     # lower shelf
    b.box(2, 5, 9, 7, 8, 14, "graphite", faces={"north": "label_12v"})  # boxed parts on shelf
    b.box(9, 5, 3, 14, 6, 13, "paper")
    # tools on the bench: beaker, test tube rack, notebook, magnifier
    b.lathe(4.5, 4.5, [(1.6, 0), (1.6, 3.2), (1.9, 3.4)], "glass", seg=12, y0=15.2,
            inner=[(1.3, 0.25), (1.3, 3.4)])
    b.box(10.5, 15, 2, 15, 16.2, 4, "wood_dark")               # rack
    for x in (11, 12.5, 14):
        b.cylinder(x + 0.3, 15.2, 3, 0.5, 3.4, "glass", seg=8)
    b.box(10, 15, 9, 15, 15.6, 14.5, "paper", faces={"up": "paper"})
    b.box(9.8, 15, 9, 10.3, 15.7, 14.5, "red_plastic")
    b.cylinder(5.5, 15.2, 12, 1.6, 0.4, "lens", seg=12)
    b.box(6.8, 15.2, 11.6, 9.5, 15.6, 12.4, "graphite")
    return b


def balance():
    b = Builder("precision_balance")
    b.box(1, 0.6, 2, 15, 3.4, 15, "graphite")                  # body
    for (x, z) in ((2, 3), (13, 3), (2, 13), (13, 13)):
        b.cylinder(x + 0.5, 0, z + 0.5, 0.7, 0.6, "rubber", seg=8)
    b.box(3, 1.2, 0.9, 13, 5.0, 2.2, "graphite", faces={"north": "screen"})  # display (text drawn by renderer)
    b.box(2.0, 0.8, 1.6, 2.8, 1.6, 2.0, "emissive_green")      # status LED
    b.cylinder(14, 1.6, 1.3, 0.6, 0.8, "knob", axis="z", seg=8)
    b.cylinder(8, 3.4, 9.5, 1.0, 4.0, "chrome", seg=10)        # pan support column
    # draft shield: glass panes on three sides and the top, steel frame
    for x in (1.6, 14.0):
        b.box(x, 3.4, 3.4, x + 0.4, 13.4, 14.6, "glass")
    b.box(1.6, 3.4, 14.4, 14.4, 13.4, 14.8, "glass")
    b.box(1.6, 13.4, 3.4, 14.4, 13.8, 14.8, "glass")
    for (x, z) in ((1.4, 3.2), (14.0, 3.2), (1.4, 14.4), (14.0, 14.4)):
        b.box(x, 3.4, z, x + 0.6, 13.9, z + 0.6, "steel")
    pan = Builder("precision_balance_pan")
    pan.cylinder(8, 7.4, 9.5, 0.6, 1.0, "chrome", seg=8)
    pan.cylinder(8, 8.4, 9.5, 4.6, 0.6, "chrome", seg=20)
    pan.tube(8, 8.4, 9.5, 4.8, 4.4, 1.0, "steel", seg=20)
    return {"precision_balance": b, "precision_balance_pan": pan}


def cylinder():
    b = Builder("graduated_cylinder")
    b.cylinder(8, 0, 8, 4.2, 0.8, "blue_plastic", seg=6)       # hexagonal foot
    b.cylinder(8, 0.8, 8, 2.2, 0.7, "blue_plastic", seg=12)
    b.tube(8, 1.2, 8, 1.65, 1.3, 14.0, "glass", seg=14)
    b.cylinder(8, 1.2, 8, 1.3, 0.3, "glass", seg=14)            # glass base
    b.tube(8, 15.0, 8, 1.9, 1.3, 0.4, "glass_frost", seg=14)   # rim
    b.box(9.3, 15.1, 6.9, 10.4, 15.4, 7.6, "glass_frost")      # spout
    for i in range(10):
        y = 1.5 + 13.5 * (i + 1) / 10.0
        w = 1.2 if i % 5 == 4 else 0.7
        b.box(8 - w / 2, y - 0.08, 6.25, 8 + w / 2, y + 0.08, 6.35, "white_enamel")
    return b


def build():
    out = {"scientific_workbench": workbench(), "graduated_cylinder": cylinder()}
    out.update(balance())
    return out
