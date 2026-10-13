package com.scientia.item;

import com.scientia.core.chem.ChemicalFormula;
import com.scientia.core.data.MaterialEntry;
import com.scientia.util.Sci;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A 10 g portion of a chemical. The tooltip shows its formula and classification from the database. */
public class ReagentItem extends Item {
    public ReagentItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tips, @NotNull TooltipFlag flag) {
        MaterialEntry m = Sci.material(stack);
        if (m == null || m.composition() == null || m.composition().isEmpty()) return;
        String formula = "";
        if (m.composition().size() == 1) {
            var s = Sci.db().substances().get(m.composition().get(0).substance());
            if (s != null) formula = ChemicalFormula.pretty(s.formula()) + " — ";
        }
        tips.add(Component.literal(formula + m.classification().replace('_', ' ') + ", " + (int) m.sampleMassG() + " g")
                .withStyle(ChatFormatting.DARK_AQUA));
        if (m.description() != null) tips.add(Component.literal(m.description()).withStyle(ChatFormatting.GRAY));
    }

    /**
     * Salt on ice or snow melts it by freezing-point depression (ΔT_f = i K_f b), the reason roads are salted.
     * Below about −21 °C (the NaCl–water eutectic, 252 K) even saturated brine freezes, so salt stops working.
     */
    @Override
    public @NotNull net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        ItemStack stack = ctx.getItemInHand();
        if (!stack.is(com.scientia.registry.ModItems.SALT_CRYSTALS.get())) return net.minecraft.world.InteractionResult.PASS;
        Level level = ctx.getLevel();
        net.minecraft.core.BlockPos pos = ctx.getClickedPos();
        var state = level.getBlockState(pos);
        boolean ice = state.is(net.minecraft.world.level.block.Blocks.ICE) || state.is(net.minecraft.world.level.block.Blocks.FROSTED_ICE);
        boolean snow = state.is(net.minecraft.world.level.block.Blocks.SNOW) || state.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)
                || state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW);
        if (!ice && !snow) return net.minecraft.world.InteractionResult.PASS;
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        var player = ctx.getPlayer();
        if (Sci.ambientK(level, pos) < 252.15) {
            if (player != null) player.displayClientMessage(Component.literal(
                    "Too cold: below −21 °C even saturated salt water freezes (the NaCl–water eutectic). Real roads use CaCl₂ in deep cold.").withStyle(ChatFormatting.GRAY), false);
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        level.setBlockAndUpdate(pos, ice ? net.minecraft.world.level.block.Blocks.WATER.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.SNOW_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
        if (player != null) {
            player.displayClientMessage(Component.literal("Melted: dissolved Na⁺ and Cl⁻ ions lower the freezing point of water.").withStyle(ChatFormatting.AQUA), true);
            if (!player.isCreative()) stack.shrink(1);
        }
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    /** A tool used in crafting that is returned afterwards (mortar and pestle). */
    public static class Remainder extends Item {
        public Remainder(Properties props) {
            super(props);
        }

        @Override
        public boolean hasCraftingRemainingItem(ItemStack stack) {
            return true;
        }

        @Override
        public ItemStack getCraftingRemainingItem(ItemStack stack) {
            return new ItemStack(this);
        }
    }
}
