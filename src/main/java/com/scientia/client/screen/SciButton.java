package com.scientia.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.Supplier;

/** A flat instrument-style button. Its label can be dynamic (e.g. "Heater: ON"). */
@OnlyIn(Dist.CLIENT)
public class SciButton extends AbstractButton {
    private final Runnable action;
    private final Supplier<String> label;
    private int accent = UiKit.CYAN;

    public SciButton(int x, int y, int w, int h, Supplier<String> label, Runnable action) {
        super(x, y, w, h, Component.literal(label.get()));
        this.label = label;
        this.action = action;
    }

    public SciButton accent(int color) {
        this.accent = color;
        return this;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        boolean hover = isHoveredOrFocused();
        int bg = !active ? 0xFF181D22 : hover ? 0xFF2E3A46 : 0xFF222B34;
        g.fill(getX(), getY(), getX() + width, getY() + height, bg);
        UiKit.outline(g, getX(), getY(), width, height, hover && active ? accent : UiKit.border());
        if (active) g.fill(getX() + 1, getY() + height - 2, getX() + width - 1, getY() + height - 1, (accent & 0x00FFFFFF) | 0x80000000);
        String text = label.get();
        var font = Minecraft.getInstance().font;
        int color = active ? UiKit.text() : UiKit.muted();
        g.drawString(font, text, getX() + (width - font.width(text)) / 2, getY() + (height - 8) / 2, color, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }

    @Override
    public Component getMessage() {
        return Component.literal(label.get());
    }
}
