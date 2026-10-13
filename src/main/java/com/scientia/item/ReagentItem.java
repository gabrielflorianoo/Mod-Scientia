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
        var s = Sci.db().substances().get(m.composition().get(0).substance());
        if (s != null) {
            tips.add(Component.literal(ChemicalFormula.pretty(s.formula()) + " — " + s.kind().replace('_', ' ') + " compound, "
                    + (int) m.sampleMassG() + " g").withStyle(ChatFormatting.DARK_AQUA));
        }
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
