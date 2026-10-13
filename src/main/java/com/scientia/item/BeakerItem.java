package com.scientia.item;

import com.scientia.core.chem.ChemicalFormula;
import com.scientia.core.chem.Mixture;
import com.scientia.core.chem.SubstanceDatabase;
import com.scientia.util.Liquids;
import com.scientia.util.Sci;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * A 250 mL borosilicate beaker. Its contents are stored as a mixture (mol of each substance).
 * Filling it from water in an ocean biome gives seawater (≈3.5 % dissolved salt, modelled as NaCl).
 */
public class BeakerItem extends Item {
    public static final double CAPACITY_ML = 250;
    public static final double GLASS_MASS_G = 50;
    /** Seawater: ~35 g of salts per kg; the game models all of it as NaCl. */
    public static final double SEAWATER_SALT_FRACTION = 0.035;

    public BeakerItem(Properties props) {
        super(props);
    }

    public static boolean isEmpty(ItemStack stack) {
        return Sci.contentsOf(stack).isEmpty();
    }

    public static boolean isPureWater(ItemStack stack) {
        Mixture m = Sci.contentsOf(stack);
        return m.amounts().size() == 1 && m.contains("water");
    }

    public static ItemStack filled(Item beaker, Mixture m) {
        ItemStack s = new ItemStack(beaker);
        Sci.setContents(s, m);
        return s;
    }

    public static Mixture water(double ml, boolean sea) {
        SubstanceDatabase subs = Sci.db().substances();
        double grams = ml * 0.997;
        Mixture m = new Mixture();
        if (sea) {
            double total = grams / (1 - SEAWATER_SALT_FRACTION);
            m.add("sodium_chloride", total * SEAWATER_SALT_FRACTION / subs.molarMass("sodium_chloride"));
        }
        m.add("water", grams / subs.molarMass("water"));
        return m;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isEmpty(stack)) return InteractionResultHolder.pass(stack);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(stack);
        BlockPos pos = hit.getBlockPos();
        FluidState fluid = level.getFluidState(pos);
        if (!fluid.is(Fluids.WATER)) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            boolean sea = Sci.isOcean(level, pos);
            ItemStack full = filled(this, water(CAPACITY_ML, sea));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1, 1);
            if (stack.getCount() == 1 && !player.isCreative()) return InteractionResultHolder.success(full);
            if (!player.isCreative()) stack.shrink(1);
            if (!player.getInventory().add(full)) player.drop(full, false);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return isEmpty(stack) ? 16 : 1;
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        Mixture m = Sci.contentsOf(stack);
        if (m.isEmpty()) return Component.translatable("item.scientia.beaker");
        return Component.translatable("item.scientia.beaker.filled", describe(m));
    }

    /** Short name for the dominant contents, e.g. "seawater", "water", "vinegar", "solution". */
    public static String describe(Mixture m) {
        if (m.amounts().size() == 1 && m.contains("water")) return "water";
        if (m.contains("water") && m.contains("sodium_chloride") && m.amounts().size() == 2) return "salt water";
        if (m.contains("acetic_acid") && m.contains("water") && m.amounts().size() == 2) return "vinegar";
        if (m.contains("calcium_hydroxide") && m.contains("water") && m.amounts().size() == 2) return "limewater";
        if (m.contains("hydrogen_chloride") && m.contains("water") && m.amounts().size() == 2) return "hydrochloric acid";
        if (m.contains("sodium_hydroxide") && m.contains("water") && m.amounts().size() == 2) return "sodium hydroxide solution";
        return m.contains("water") ? "solution" : "mixture";
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tips, @NotNull TooltipFlag flag) {
        Mixture m = Sci.contentsOf(stack);
        if (m.isEmpty()) {
            tips.add(Component.translatable("item.scientia.beaker.tip").withStyle(ChatFormatting.GRAY));
            return;
        }
        SubstanceDatabase subs = Sci.db().substances();
        tips.add(Component.literal(String.format(Locale.ROOT, "%.0f mL, %.1f g of contents", Liquids.volumeMl(m), m.massGrams(subs)))
                .withStyle(ChatFormatting.GRAY));
        for (var e : m.amounts().entrySet()) {
            var s = subs.get(e.getKey());
            if (s == null) continue;
            tips.add(Component.literal(String.format(Locale.ROOT, " %s  %s: %.2f g (%.3g mol)", ChemicalFormula.pretty(s.formula()), s.name(),
                    e.getValue() * subs.molarMass(e.getKey()), e.getValue())).withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
