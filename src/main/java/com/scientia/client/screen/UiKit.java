package com.scientia.client.screen;

import com.scientia.config.ScientiaConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Scientia's instrument-panel design system: dark graphite panels, thin steel borders and restrained
 * cyan/amber/green/violet accents. Every state shown in colour is also given in text or symbols.
 */
@OnlyIn(Dist.CLIENT)
public final class UiKit {
    public static final int BG = 0xF0141920;
    public static final int PANEL = 0xFF1C232B;
    public static final int PANEL_LIGHT = 0xFF26303A;
    public static final int INSET = 0xFF10151A;
    public static final int CYAN = 0xFF4FD8E8, AMBER = 0xFFF0B040, GREEN = 0xFF6BD47A, VIOLET = 0xFFB28CFF, RED = 0xFFFF6B6B;

    private UiKit() {
    }

    public static boolean highContrast() {
        try {
            return ScientiaConfig.HIGH_CONTRAST.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static int text() {
        return highContrast() ? 0xFFFFFFFF : 0xFFE6EDF3;
    }

    public static int muted() {
        return highContrast() ? 0xFFD0D8E0 : 0xFF8B98A5;
    }

    public static int border() {
        return highContrast() ? 0xFF9FB0C0 : 0xFF3A4654;
    }

    public static void frame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BG);
        outline(g, x, y, w, h, border());
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x40FFFFFF);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        outline(g, x, y, w, h, border());
    }

    public static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, INSET);
        outline(g, x, y, w, h, 0xFF2C3640);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void slot(GuiGraphics g, int x, int y, boolean output) {
        g.fill(x - 1, y - 1, x + 17, y + 17, output ? 0xFF3A2E14 : 0xFF0C1014);
        outline(g, x - 1, y - 1, 18, 18, output ? AMBER : 0xFF3A4654);
    }

    public static void header(GuiGraphics g, Font font, String title, int x, int y, int accent) {
        g.fill(x, y + 3, x + 3, y + 8, accent);
        g.drawString(font, title, x + 6, y + 1, text(), false);
    }

    /** Horizontal gauge with a label and value text (value text keeps the information colour-independent). */
    public static void gauge(GuiGraphics g, Font font, int x, int y, int w, double fraction, int color, String label, String value) {
        g.drawString(font, label, x, y, muted(), false);
        g.drawString(font, value, x + w - font.width(value), y, text(), false);
        inset(g, x, y + 10, w, 6);
        int fw = (int) Math.round((w - 2) * Math.max(0, Math.min(1, fraction)));
        if (fw > 0) g.fill(x + 1, y + 11, x + 1 + fw, y + 15, color);
    }

    public static void kv(GuiGraphics g, Font font, int x, int y, int w, String key, String value, int valueColor) {
        g.drawString(font, key, x, y, muted(), false);
        g.drawString(font, value, x + w - font.width(value), y, valueColor, false);
    }

    /** Draws wrapped text and returns the y coordinate after the last line. */
    public static int wrap(GuiGraphics g, Font font, String text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = font.split(FormattedText.of(text), width);
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, x, y, color, false);
            y += 10;
        }
        return y;
    }

    public static int wrapHeight(Font font, String text, int width) {
        return font.split(FormattedText.of(text), width).size() * 10;
    }

    public static void status(GuiGraphics g, Font font, int x, int y, boolean on, String onText, String offText) {
        g.fill(x, y + 1, x + 6, y + 7, on ? GREEN : 0xFF505A64);
        g.drawString(font, (on ? "● " : "○ ") + (on ? onText : offText), x + 9, y, on ? GREEN : muted(), false);
    }

    public static Component lit(String s) {
        return Component.literal(s);
    }
}
