package com.scientia.client.screen;

import com.scientia.core.stats.DataSeries;
import com.scientia.core.stats.LinearFit;
import com.scientia.util.Sci;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Plots real data series with labelled axes, units, tick marks, uncertainty bars and an optional
 * least-squares line. Points are drawn as small squares; the fitted line as a dashed series.
 */
@OnlyIn(Dist.CLIENT)
public final class GraphRenderer {
    private GraphRenderer() {
    }

    public static void draw(GuiGraphics g, Font font, int x, int y, int w, int h, DataSeries s, boolean showFit, int color) {
        draw(g, font, x, y, w, h, List.of(s), showFit, new int[]{color});
    }

    public static void draw(GuiGraphics g, Font font, int x, int y, int w, int h, List<DataSeries> series, boolean showFit, int[] colors) {
        UiKit.inset(g, x, y, w, h);
        int left = x + 34, right = x + w - 6, top = y + 6, bottom = y + h - 22;
        if (series.isEmpty() || series.get(0).size() == 0) {
            g.drawString(font, "No data recorded yet", x + (w - font.width("No data recorded yet")) / 2, y + h / 2 - 4, UiKit.muted(), false);
            return;
        }
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (DataSeries s : series) {
            if (s.size() == 0) continue;
            double[] b = s.bounds();
            minX = Math.min(minX, b[0]);
            maxX = Math.max(maxX, b[1]);
            minY = Math.min(minY, b[2]);
            maxY = Math.max(maxY, b[3]);
        }
        double stepX = DataSeries.niceStep(maxX - minX, 5), stepY = DataSeries.niceStep(maxY - minY, 4);
        // grid and ticks
        for (double tx = Math.ceil(minX / stepX) * stepX; tx <= maxX + 1e-9; tx += stepX) {
            int px = (int) (left + (tx - minX) / (maxX - minX) * (right - left));
            g.fill(px, top, px + 1, bottom, 0x22FFFFFF);
            String lbl = Sci.fmt(tx, 3);
            g.drawString(font, lbl, px - font.width(lbl) / 2, bottom + 2, UiKit.muted(), false);
        }
        for (double ty = Math.ceil(minY / stepY) * stepY; ty <= maxY + 1e-9; ty += stepY) {
            int py = (int) (bottom - (ty - minY) / (maxY - minY) * (bottom - top));
            g.fill(left, py, right, py + 1, 0x22FFFFFF);
            String lbl = Sci.fmt(ty, 4);
            g.pose().pushPose();
            g.pose().translate(left - 2 - font.width(lbl) * 0.75f, py - 3, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawString(font, lbl, 0, 0, UiKit.muted(), false);
            g.pose().popPose();
        }
        g.fill(left, top, left + 1, bottom, UiKit.border());
        g.fill(left, bottom, right, bottom + 1, UiKit.border());
        DataSeries first = series.get(0);
        String xl = first.xLabel() + " (" + first.xUnit() + ")";
        g.drawString(font, xl, right - font.width(xl), bottom + 11, UiKit.text(), false);
        String yl = first.yLabel() + (first.yUnit().isEmpty() ? "" : " (" + first.yUnit() + ")");
        g.drawString(font, yl, left + 3, top + 1, UiKit.text(), false);
        for (int si = 0; si < series.size(); si++) {
            DataSeries s = series.get(si);
            int c = colors[si % colors.length];
            int prevX = Integer.MIN_VALUE, prevY = 0;
            for (DataSeries.Point p : s.points()) {
                int px = (int) (left + (p.x() - minX) / (maxX - minX) * (right - left));
                int py = (int) (bottom - (p.y() - minY) / (maxY - minY) * (bottom - top));
                if (p.yUncertainty() > 0) {
                    int du = (int) Math.round(p.yUncertainty() / (maxY - minY) * (bottom - top));
                    if (du >= 2) g.fill(px, py - du, px + 1, py + du, (c & 0x00FFFFFF) | 0x80000000);
                }
                if (prevX != Integer.MIN_VALUE && s.size() > 40) line(g, prevX, prevY, px, py, (c & 0x00FFFFFF) | 0xA0000000);
                g.fill(px - 1, py - 1, px + 2, py + 2, c);
                prevX = px;
                prevY = py;
            }
            if (showFit && s.size() >= 2) {
                LinearFit f = s.fit();
                if (f != null) {
                    int steps = 60;
                    for (int i = 0; i < steps; i += 2) {
                        double xa = minX + (maxX - minX) * i / steps, xb = minX + (maxX - minX) * (i + 1) / steps;
                        int ax = (int) (left + (xa - minX) / (maxX - minX) * (right - left));
                        int bx = (int) (left + (xb - minX) / (maxX - minX) * (right - left));
                        int ay = clampY((int) (bottom - (f.predict(xa) - minY) / (maxY - minY) * (bottom - top)), top, bottom);
                        int by = clampY((int) (bottom - (f.predict(xb) - minY) / (maxY - minY) * (bottom - top)), top, bottom);
                        line(g, ax, ay, bx, by, UiKit.AMBER);
                    }
                }
            }
        }
    }

    private static int clampY(int v, int top, int bottom) {
        return Math.max(top, Math.min(bottom, v));
    }

    /** Simple line by stepping (Bresenham-like) with 1×1 fills. */
    public static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int n = Math.max(dx, dy);
        if (n == 0) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            return;
        }
        for (int i = 0; i <= n; i++) {
            int x = x0 + (x1 - x0) * i / n, y = y0 + (y1 - y0) * i / n;
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
