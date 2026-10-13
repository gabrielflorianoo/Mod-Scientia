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

    /**
     * An acidic solution (pH &lt; 4, e.g. vinegar or hydrochloric acid) dissolves the green patina — basic copper
     * carbonate, Cu₂CO₃(OH)₂ — returning oxidised copper blocks one stage. About 4 mmol of H⁺ are used per stage:
     * Cu₂CO₃(OH)₂ + 4 H⁺ → 2 Cu²⁺ + CO₂ + 3 H₂O.
     */
    @Override
    public @NotNull net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        ItemStack stack = ctx.getItemInHand();
        Level level = ctx.getLevel();
        var state = level.getBlockState(ctx.getClickedPos());
        if (isEmpty(stack) || !(state.getBlock() instanceof net.minecraft.world.level.block.WeatheringCopper)) return net.minecraft.world.InteractionResult.PASS;
        var previous = net.minecraft.world.level.block.WeatheringCopper.getPrevious(state);
        if (previous.isEmpty()) return net.minecraft.world.InteractionResult.PASS;
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        Mixture m = Sci.contentsOf(stack);
        double ph = com.scientia.core.chem.AcidBaseSolver.pH(m, Sci.db().substances());
        Player player = ctx.getPlayer();
        double needH = 0.004;
        double acid = m.get("hydrogen_chloride") + m.get("acetic_acid");
        if (Double.isNaN(ph) || ph >= 4 || acid < needH) {
            if (player != null) player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                    "Nothing happens: the patina (basic copper carbonate) only dissolves in acid. This solution has pH %s.",
                    Double.isNaN(ph) ? "—" : String.format(Locale.ROOT, "%.1f", ph))).withStyle(ChatFormatting.GRAY), false);
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        // Use strong acid first, then acetic acid; products stay in the beaker as dissolved salts and CO2 escapes.
        double fromHcl = Math.min(m.get("hydrogen_chloride"), needH);
        m.remove("hydrogen_chloride", fromHcl);
        m.remove("acetic_acid", needH - fromHcl);
        m.add("water", needH * 0.75);
        setContents(stack, m);
        level.setBlockAndUpdate(ctx.getClickedPos(), previous.get());
        level.playSound(null, ctx.getClickedPos(), SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6f, 1.4f);
        if (player != null) player.displayClientMessage(Component.literal(
                "The acid dissolved the green patina: Cu₂CO₃(OH)₂ + 4 H⁺ → 2 Cu²⁺ + CO₂ + 3 H₂O.").withStyle(ChatFormatting.AQUA), true);
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    private static void setContents(ItemStack s, Mixture m) {
        Sci.setContents(s, m);
    }

    /** Sneak + use: drinking from laboratory glassware, with realistic consequences. */
    private InteractionResultHolder<ItemStack> drink(Level level, Player player, ItemStack stack) {
        if (level.isClientSide || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) return InteractionResultHolder.success(stack);
        Mixture m = Sci.contentsOf(stack);
        var subs = Sci.db().substances();
        double ph = com.scientia.core.chem.AcidBaseSolver.pH(m, subs);
        double waterG = Sci.waterGrams(m);
        double salt = waterG > 0 ? m.massOf("sodium_chloride", subs) / (waterG + m.massOf("sodium_chloride", subs)) : 0;
        boolean toxic = m.contains("copper_ii_chloride") || m.contains("copper_ii_oxide") || m.contains("iron_iii_chloride") || m.contains("chlorine");
        level.playSound(null, sp.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1f, 1f);
        if (!Double.isNaN(ph) && (ph < 2.5 || ph > 11.5)) {
            sp.hurt(level.damageSources().magic(), 6f);
            sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 200, 0));
            com.scientia.util.Hazards.lesson(sp, "chemical_burn", String.format(Locale.ROOT, "You drank a solution of pH %.1f", ph),
                    ph < 7 ? "Strong acids burn the mouth and throat by denaturing proteins. Never taste anything in a laboratory."
                            : "Strong bases (alkalis) are even more damaging than acids: they dissolve fats and tissue. Never taste anything in a laboratory.");
        } else if (toxic) {
            sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 200, 0));
            com.scientia.util.Hazards.lesson(sp, "poisoning", "You drank a toxic solution",
                    "Dissolved copper and iron(III) salts are toxic in quantity. Laboratory glassware is never for drinking.");
        } else if (salt > 0.01) {
            sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HUNGER, 600, 1));
            com.scientia.util.Hazards.lesson(sp, "dehydration", String.format(Locale.ROOT, "You drank water with %.1f %% salt", salt * 100),
                    "Seawater is saltier than blood: the kidneys need more water to remove the salt than you drank, so it dehydrates you. Distil it first.");
        } else if (m.amounts().size() == 1 && m.contains("water")) {
            sp.displayClientMessage(Component.literal("Just water — but in a real lab, never drink from lab glassware: it may be contaminated.").withStyle(ChatFormatting.GRAY), false);
        } else {
            sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 160, 0));
            com.scientia.util.Hazards.lesson(sp, "ingestion", "You drank an unknown laboratory solution",
                    "Unknown chemicals can be harmful even when they look like water. Label everything; never taste.");
        }
        if (player.isCreative()) return InteractionResultHolder.consume(stack);
        return InteractionResultHolder.consume(new ItemStack(this));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isEmpty(stack) && player.isShiftKeyDown()) return drink(level, player, stack);
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
