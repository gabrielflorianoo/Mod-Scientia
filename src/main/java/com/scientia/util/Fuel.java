package com.scientia.util;

import com.scientia.core.data.MaterialEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.Tags;

/** Chemical energy of fuel items, based on the combustion enthalpy of carbon (393.5 kJ/mol). */
public final class Fuel {
    public static final double CARBON_COMBUSTION_J_PER_MOL = 393_500;
    /** Energy of one vanilla coal item under the Scientia material model (85 g of carbon). */
    public static final double COAL_J = 100 * 0.85 / 12.011 * CARBON_COMBUSTION_J_PER_MOL;

    private Fuel() {
    }

    /**
     * Joules released by burning one item. Carbon-bearing materials use their modelled carbon content;
     * other vanilla fuels are scaled from their burn time relative to coal (a game approximation).
     */
    public static double energyJ(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        MaterialEntry m = Sci.material(stack);
        if (m != null && m.composition() != null) {
            double carbonG = 0;
            for (MaterialEntry.Component c : m.composition()) {
                if (c.substance().startsWith("carbon_graphite")) carbonG += m.sampleMassG() * c.massFraction();
            }
            if (carbonG > 0) return carbonG / 12.011 * CARBON_COMBUSTION_J_PER_MOL;
        }
        if (stack.is(Tags.Items.STORAGE_BLOCKS_COAL)) return 9 * COAL_J;
        int burn = ForgeHooks.getBurnTime(stack, null);
        return burn <= 0 ? 0 : burn / 1600.0 * COAL_J;
    }
}
