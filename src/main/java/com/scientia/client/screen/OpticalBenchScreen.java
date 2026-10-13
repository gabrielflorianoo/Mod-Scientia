package com.scientia.client.screen;

import com.scientia.core.physics.Optics;
import com.scientia.network.ObservePacket;
import com.scientia.network.ScientiaNetwork;
import com.scientia.util.Sci;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Interactive ray-optics bench: reflection at a mirror, refraction between media (with total internal
 * reflection), a converging thin lens with ray diagram, and prism dispersion. Rays are computed with
 * Snell's law, the law of reflection and the thin lens equation; this is a ray model, not a wave model.
 */
@OnlyIn(Dist.CLIENT)
public class OpticalBenchScreen extends Screen {
    enum Mode {REFLECTION, REFRACTION, LENS, PRISM}

    private static final List<String> MEDIA = List.of("air", "water", "crown_glass", "diamond");
    private final BlockPos pos;
    private Mode mode = Mode.REFRACTION;
    private int x0, y0, w, h;
    private double angle = 40;
    private int m1 = 0, m2 = 1;
    private double focal = 0.10, objectDist = 0.30;

    public OpticalBenchScreen(BlockPos pos) {
        super(Component.translatable("block.scientia.optical_bench"));
        this.pos = pos;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        w = Math.min(width - 20, 360);
        h = Math.min(height - 20, 230);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        clearWidgets();
        int bx = x0 + 6;
        for (Mode m : Mode.values()) {
            String label = m.name().charAt(0) + m.name().substring(1).toLowerCase();
            int bw = font.width(label) + 12;
            addRenderableWidget(new SciButton(bx, y0 + 4, bw, 14, () -> (mode == m ? "▸" : "") + label, () -> {
                mode = m;
                init();
            }).accent(UiKit.VIOLET));
            bx += bw + 2;
        }
        int by = y0 + h - 20;
        switch (mode) {
            case REFLECTION, REFRACTION, PRISM -> {
                addRenderableWidget(new SciButton(x0 + 6, by, 20, 14, () -> "−", () -> angle = Math.max(1, angle - 5)));
                addRenderableWidget(new SciButton(x0 + 28, by, 20, 14, () -> "+", () -> angle = Math.min(89, angle + 5)));
                if (mode == Mode.REFRACTION) {
                    addRenderableWidget(new SciButton(x0 + 54, by, 80, 14, () -> "from " + MEDIA.get(m1).replace('_', ' '), () -> m1 = (m1 + 1) % MEDIA.size()));
                    addRenderableWidget(new SciButton(x0 + 138, by, 80, 14, () -> "into " + MEDIA.get(m2).replace('_', ' '), () -> m2 = (m2 + 1) % MEDIA.size()));
                }
            }
            case LENS -> {
                addRenderableWidget(new SciButton(x0 + 6, by, 50, 14, () -> "object −", () -> objectDist = Math.max(0.02, objectDist - 0.02)));
                addRenderableWidget(new SciButton(x0 + 58, by, 50, 14, () -> "object +", () -> objectDist = Math.min(0.6, objectDist + 0.02)));
                addRenderableWidget(new SciButton(x0 + 114, by, 40, 14, () -> "f −", () -> focal = Math.max(0.04, focal - 0.01)));
                addRenderableWidget(new SciButton(x0 + 156, by, 40, 14, () -> "f +", () -> focal = Math.min(0.2, focal + 0.01)));
            }
        }
        addRenderableWidget(new SciButton(x0 + w - 96, by, 90, 14, () -> "Record result", this::record).accent(UiKit.GREEN));
    }

    private double n(int i) {
        return Optics.REFRACTIVE_INDEX.get(MEDIA.get(i));
    }

    private void record() {
        ObservePacket p = switch (mode) {
            case REFLECTION -> new ObservePacket(pos, ObservePacket.Kind.REFLECTION, angle, 0, 0, 0);
            case REFRACTION -> Double.isNaN(Optics.refractionAngle(n(m1), n(m2), angle))
                    ? new ObservePacket(pos, ObservePacket.Kind.TOTAL_INTERNAL_REFLECTION, angle, n(m1), n(m2), 0)
                    : new ObservePacket(pos, ObservePacket.Kind.REFRACTION, angle, n(m1), n(m2), 0);
            case LENS -> new ObservePacket(pos, ObservePacket.Kind.LENS, focal, objectDist, 0, 0);
            case PRISM -> new ObservePacket(pos, ObservePacket.Kind.DISPERSION, angle, 0, 0, 0);
        };
        ScientiaNetwork.CHANNEL.sendToServer(p);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        UiKit.frame(g, x0, y0, w, h);
        int ax = x0 + 6, ay = y0 + 22, aw = w - 12, ah = h - 64;
        UiKit.inset(g, ax, ay, aw, ah);
        int cx = ax + aw / 2, cy = ay + ah / 2;
        switch (mode) {
            case REFLECTION -> {
                g.fill(ax + 10, cy, ax + aw - 10, cy + 2, 0xFFB8C2CC);
                dashedNormal(g, cx, ay + 4, cy);
                double a = Math.toRadians(angle);
                int len = ah / 2 - 6;
                ray(g, cx - (int) (Math.sin(a) * len), cy - (int) (Math.cos(a) * len), cx, cy, 0xFFFF5050);
                ray(g, cx, cy, cx + (int) (Math.sin(a) * len), cy - (int) (Math.cos(a) * len), 0xFFFF9090);
                info(g, "Angle of incidence θi = " + (int) angle + "°   Angle of reflection θr = " + (int) Optics.reflectionAngle(angle) + "°");
            }
            case REFRACTION -> {
                g.fill(ax + 1, cy, ax + aw - 1, ay + ah - 1, mediumColor(m2));
                g.fill(ax + 1, ay + 1, ax + aw - 1, cy, mediumColor(m1));
                dashedNormal(g, cx, ay + 4, ay + ah - 4);
                double a = Math.toRadians(angle);
                int len = ah / 2 - 6;
                ray(g, cx - (int) (Math.sin(a) * len), cy - (int) (Math.cos(a) * len), cx, cy, 0xFFFF5050);
                double t2 = Optics.refractionAngle(n(m1), n(m2), angle);
                if (Double.isNaN(t2)) {
                    ray(g, cx, cy, cx + (int) (Math.sin(a) * len), cy - (int) (Math.cos(a) * len), 0xFFFF9090);
                    info(g, String.format("θ₁ = %d°: total internal reflection (critical angle %.1f°)", (int) angle, Optics.criticalAngle(n(m1), n(m2))));
                } else {
                    double b = Math.toRadians(t2);
                    ray(g, cx, cy, cx + (int) (Math.sin(b) * len), cy + (int) (Math.cos(b) * len), 0xFFFF5050);
                    info(g, String.format("n₁ = %.3f, n₂ = %.3f:  θ₁ = %d°  →  θ₂ = %.1f°   (n₁ sin θ₁ = n₂ sin θ₂)", n(m1), n(m2), (int) angle, t2));
                }
            }
            case LENS -> lens(g, ax, ay, aw, ah, cx, cy);
            case PRISM -> prism(g, cx, cy, ah);
        }
        super.render(g, mx, my, partial);
    }

    private int mediumColor(int i) {
        return switch (MEDIA.get(i)) {
            case "water" -> 0x403F76E4;
            case "crown_glass" -> 0x40A0E0E0;
            case "diamond" -> 0x40E0E0FF;
            default -> 0x10FFFFFF;
        };
    }

    private void lens(GuiGraphics g, int ax, int ay, int aw, int ah, int cx, int cy) {
        double scale = (aw / 2.0 - 10) / 0.6; // px per metre
        g.fill(ax + 4, cy, ax + aw - 4, cy + 1, 0x60FFFFFF);
        g.fill(cx - 1, ay + 6, cx + 1, ay + ah - 6, 0xFF9FE8F0);
        int f = (int) (focal * scale);
        g.fill(cx - f - 1, cy - 2, cx - f + 2, cy + 3, UiKit.AMBER);
        g.fill(cx + f - 1, cy - 2, cx + f + 2, cy + 3, UiKit.AMBER);
        int ox = cx - (int) (objectDist * scale), objH = ah / 4;
        g.fill(ox, cy - objH, ox + 2, cy, UiKit.GREEN);
        double di = Optics.imageDistance(focal, objectDist);
        double mag = Optics.magnification(objectDist, di);
        int top = cy - objH;
        // Ray 1: parallel to axis, then through far focal point
        ray(g, ox, top, cx, top, 0xFFFF5050);
        int ex = Double.isFinite(di) && di > 0 ? cx + (int) (di * scale) : ax + aw - 6;
        double slope = (double) (cy - top) / f;
        ray(g, cx, top, ex, (int) (top + slope * (ex - cx)), 0xFFFF5050);
        // Ray 2: through the centre undeviated
        double s2 = (double) (cy - top) / (cx - ox);
        ray(g, ox, top, ex, (int) (top + s2 * (ex - ox)), 0xFFFFB050);
        String text;
        if (!Double.isFinite(di)) {
            text = "Object at the focal point: rays emerge parallel, no image forms.";
        } else if (di > 0) {
            int ih = (int) (objH * mag);
            int ix = cx + (int) (di * scale);
            if (ix < ax + aw) g.fill(ix, cy, ix + 2, cy - ih, UiKit.VIOLET);
            text = String.format("dₒ = %.2f m, f = %.2f m → dᵢ = %.3f m, M = %.2f (real, %s)", objectDist, focal, di, mag, mag < 0 ? "inverted" : "upright");
        } else {
            text = String.format("dₒ < f: virtual, upright image at dᵢ = %.3f m (M = %.2f) — this is how a magnifying glass works", di, mag);
        }
        info(g, text);
    }

    private void prism(GuiGraphics g, int cx, int cy, int ah) {
        int s = ah / 2 - 8;
        for (int i = 0; i < s; i++) g.fill(cx - i / 2, cy - s / 2 + i, cx + i / 2 + 1, cy - s / 2 + i + 1, 0x50A0E0E0);
        int[] wl = {700, 620, 580, 530, 470, 420};
        ray(g, cx - s, cy + 4, cx - s / 4, cy + 4, 0xFFFFFFFF);
        for (int nm : wl) {
            double nIdx = Optics.crownGlassIndex(nm);
            double dev = (nIdx - 1) * Math.toRadians(angle); // thin-prism approximation δ ≈ (n − 1)A
            int ex = cx + s, ey = cy + 4 + (int) (Math.tan(dev) * s * 1.6);
            ray(g, cx + s / 4, cy + 4, ex, ey, 0xFF000000 | Optics.wavelengthToRgb(nm));
        }
        info(g, String.format("Apex angle %d°: n(420 nm) = %.4f > n(700 nm) = %.4f, so violet bends most (thin-prism δ ≈ (n−1)A)",
                (int) angle, Optics.crownGlassIndex(420), Optics.crownGlassIndex(700)));
    }

    private void info(GuiGraphics g, String text) {
        UiKit.wrap(g, font, text, x0 + 8, y0 + h - 40, w - 16, UiKit.text());
    }

    private static void ray(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        GraphRenderer.line(g, x0, y0, x1, y1, c);
        GraphRenderer.line(g, x0, y0 + 1, x1, y1 + 1, (c & 0x00FFFFFF) | 0x60000000);
    }

    private static void dashedNormal(GuiGraphics g, int x, int yTop, int yBottom) {
        for (int y = yTop; y < yBottom; y += 6) g.fill(x, y, x + 1, y + 3, 0x80FFFFFF);
    }

    @SuppressWarnings("unused")
    private static String f(double v) {
        return Sci.fixed(v, 2);
    }
}
