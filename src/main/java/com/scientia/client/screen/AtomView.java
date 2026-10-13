package com.scientia.client.screen;

import com.scientia.core.chem.Element;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Animated Bohr-style shell diagram generated from the element data. It is explicitly labelled as a
 * simplified model: in quantum mechanics electrons occupy orbitals (probability clouds), they do not
 * orbit like planets. The view can be tilted by dragging.
 */
@OnlyIn(Dist.CLIENT)
public final class AtomView {
    private AtomView() {
    }

    public static void draw(GuiGraphics g, Font font, int cx, int cy, int radius, Element e, float time, float tilt) {
        List<Integer> shells = e.shells();
        int n = shells == null ? 0 : shells.size();
        // nucleus: protons (red) and neutrons (grey) packed in a small cluster
        int protons = e.z(), neutrons = e.approximateNeutrons();
        int shown = Math.min(protons + neutrons, 40);
        for (int i = 0; i < shown; i++) {
            double a = i * 2.39996, r = Math.sqrt(i) * 1.6;
            int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r * Math.cos(tilt));
            boolean proton = (i * 37 % shown) < (long) shown * protons / Math.max(1, protons + neutrons);
            g.fill(px - 1, py - 1, px + 2, py + 2, proton ? 0xFFE0524A : 0xFFB8C2CC);
        }
        for (int s = 0; s < n; s++) {
            int r = (int) (10 + (radius - 10) * (s + 1) / (double) n);
            ellipse(g, cx, cy, r, (int) Math.max(2, r * Math.abs(Math.cos(tilt))), 0x554FD8E8);
            int count = shells.get(s);
            double speed = 0.6 / (s + 1);
            for (int k = 0; k < count; k++) {
                double a = time * speed + k * 2 * Math.PI / count + s;
                int ex = cx + (int) Math.round(Math.cos(a) * r);
                int ey = cy + (int) Math.round(Math.sin(a) * r * Math.cos(tilt));
                g.fill(ex - 1, ey - 1, ex + 2, ey + 2, 0xFF6CFFE8);
            }
            String label = String.valueOf(count);
            g.drawString(font, label, cx + r - font.width(label) / 2, cy - 4, 0xFF9FE8F0, false);
        }
    }

    private static void ellipse(GuiGraphics g, int cx, int cy, int rx, int ry, int color) {
        int steps = Math.max(24, rx * 3);
        for (int i = 0; i < steps; i++) {
            double a = i * 2 * Math.PI / steps;
            int x = cx + (int) Math.round(Math.cos(a) * rx), y = cy + (int) Math.round(Math.sin(a) * ry);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
