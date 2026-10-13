package com.scientia.client;

import com.scientia.blockentity.Measurable;
import com.scientia.blockentity.MixtureHolder;
import com.scientia.client.screen.UiKit;
import com.scientia.config.ScientiaConfig;
import com.scientia.core.chem.AcidBaseSolver;
import com.scientia.item.InstrumentItem;
import com.scientia.util.Sci;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Contextual HUD: only while holding an instrument and looking at something it can read. It shows the
 * machine's synchronised live values (circuit values need a right-click, because they are solved on the server).
 */
@OnlyIn(Dist.CLIENT)
public class MeasurementHud implements IGuiOverlay {
    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        try {
            if (!ScientiaConfig.SHOW_HUD.get()) return;
        } catch (IllegalStateException e) {
            return;
        }
        ItemStack held = mc.player.getMainHandItem();
        if (!(held.getItem() instanceof InstrumentItem inst)) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockEntity be = mc.level.getBlockEntity(hit.getBlockPos());
        String line = null;
        switch (inst.kind()) {
            case THERMOMETER -> {
                if (be instanceof MixtureHolder h && !h.mixture().isEmpty()) line = "T = " + Sci.temperature(h.temperatureK(), true);
                else if (be instanceof Measurable m) {
                    for (Measurable.Reading r : m.readings()) if (r.quantity().equals("temperature")) line = "T = " + Sci.temperature(r.value(), true);
                }
                if (line == null) line = "Right-click to measure temperature (±0.5 K)";
            }
            case PH_METER -> {
                if (be instanceof MixtureHolder h) {
                    double ph = AcidBaseSolver.pH(h.mixture(), Sci.db().substances());
                    line = Double.isNaN(ph) ? "No aqueous solution" : "pH ≈ " + Sci.fixed(ph, 1) + " (right-click to record)";
                }
            }
            case MULTIMETER -> line = "Right-click a circuit component to measure V, I, R, P";
        }
        if (line == null) return;
        int w = mc.font.width(line) + 10;
        int x = (width - w) / 2, y = height / 2 + 12;
        g.fill(x, y, x + w, y + 14, 0xC0141920);
        UiKit.outline(g, x, y, w, 14, 0xFF3A4654);
        g.drawString(mc.font, line, x + 5, y + 3, 0xFF6CFFE8, false);
    }
}
