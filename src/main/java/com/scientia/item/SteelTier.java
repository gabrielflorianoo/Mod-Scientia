package com.scientia.item;

import com.scientia.registry.ModItems;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

/**
 * Plain carbon steel (≈0.8 % C) as a tool material. Real steel is much harder and tougher than
 * wrought iron, so the tools last about four times longer than iron ones and cut a little faster;
 * it mines the same blocks as iron (diamond is still needed for obsidian).
 */
public final class SteelTier implements Tier {
    public static final SteelTier INSTANCE = new SteelTier();

    private SteelTier() {
    }

    @Override
    public int getUses() {
        return 1000; // iron 250, diamond 1561
    }

    @Override
    public float getSpeed() {
        return 7.0f; // iron 6, diamond 8
    }

    @Override
    public float getAttackDamageBonus() {
        return 2.5f; // iron 2, diamond 3
    }

    @Override
    public int getLevel() {
        return 2; // same harvest level as iron
    }

    @Override
    public int getEnchantmentValue() {
        return 12;
    }

    @Override
    public @NotNull Ingredient getRepairIngredient() {
        return Ingredient.of(ModItems.STEEL_INGOT.get());
    }
}
