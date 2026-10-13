package com.scientia.item;

import com.scientia.core.chem.ChemicalFormula;
import com.scientia.core.chem.Mixture;
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
import java.util.Locale;

/** A sealed steel canister holding a measured amount of one gas (filled at the Electrolysis Station). */
public class GasCanisterItem extends Item {
    public GasCanisterItem(Properties props) {
        super(props);
    }

    public static ItemStack filled(Item item, String gas, double moles) {
        ItemStack s = new ItemStack(item);
        Mixture m = new Mixture();
        m.add(gas, moles);
        Sci.setContents(s, m);
        return s;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return Sci.contentsOf(stack).isEmpty() ? 16 : 1;
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        Mixture m = Sci.contentsOf(stack);
        if (m.isEmpty()) return Component.translatable("item.scientia.gas_canister");
        String id = m.amounts().keySet().iterator().next();
        var s = Sci.db().substances().get(id);
        return Component.translatable("item.scientia.gas_canister.filled", s == null ? id : s.name());
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tips, @NotNull TooltipFlag flag) {
        Mixture m = Sci.contentsOf(stack);
        for (var e : m.amounts().entrySet()) {
            var s = Sci.db().substances().get(e.getKey());
            if (s == null) continue;
            double v = e.getValue() * 22.414; // litres at 0 °C, 1 atm (ideal gas)
            tips.add(Component.literal(String.format(Locale.ROOT, "%s: %.3f mol (%.2f g, %.2f L at STP)", ChemicalFormula.pretty(s.formula()),
                    e.getValue(), e.getValue() * Sci.db().substances().molarMass(e.getKey()), v)).withStyle(ChatFormatting.DARK_AQUA));
            if (s.hazard() != null) tips.add(Component.literal(s.hazard()).withStyle(ChatFormatting.RED));
        }
    }
}
