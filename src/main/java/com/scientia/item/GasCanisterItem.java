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

    /** Resting human oxygen consumption ≈ 250 mL/min at STP ≈ 0.0112 mol/min. */
    public static final double O2_MOL_PER_MINUTE = 0.0112;

    private static String gasOf(ItemStack s) {
        Mixture m = Sci.contentsOf(s);
        return m.isEmpty() ? null : m.amounts().keySet().iterator().next();
    }

    private static double molesOf(ItemStack s) {
        Mixture m = Sci.contentsOf(s);
        return m.isEmpty() ? 0 : m.amounts().values().iterator().next();
    }

    /**
     * Using a filled canister:
     * oxygen → breathe underwater for as long as the oxygen lasts at a resting consumption rate;
     * carbon dioxide → put out your own fire (CO₂ displaces the oxygen a flame needs);
     * chlorine → released into your face (toxic: a lesson); hydrogen → rises and disperses (or flashes near a flame).
     */
    @Override
    public @NotNull net.minecraft.world.InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull net.minecraft.world.entity.player.Player player,
                                                                               @NotNull net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        String gas = gasOf(stack);
        if (gas == null) return net.minecraft.world.InteractionResultHolder.pass(stack);
        if (level.isClientSide || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) return net.minecraft.world.InteractionResultHolder.success(stack);
        double n = molesOf(stack);
        var sl = sp.serverLevel();
        switch (gas) {
            case "oxygen" -> {
                int ticks = (int) Math.round(n / O2_MOL_PER_MINUTE * 60 * 20);
                sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WATER_BREATHING, ticks, 0));
                sp.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                        "Breathing from the canister: %.3f mol O₂ lasts about %d s at rest (≈250 mL O₂ per minute).", n, ticks / 20)).withStyle(ChatFormatting.AQUA), false);
            }
            case "carbon_dioxide" -> {
                if (!sp.isOnFire()) {
                    sp.displayClientMessage(Component.literal("Aim at a fire (use on the burning block) or use while you are on fire.").withStyle(ChatFormatting.GRAY), true);
                    return net.minecraft.world.InteractionResultHolder.pass(stack);
                }
                sp.clearFire();
                sp.displayClientMessage(Component.literal("CO₂ smothered the flames: without oxygen, combustion stops.").withStyle(ChatFormatting.AQUA), false);
            }
            case "chlorine" -> com.scientia.util.Hazards.toxicGas(sl, sp.blockPosition(), "Chlorine", n);
            case "hydrogen" -> {
                if (com.scientia.util.Hazards.ignitionNear(sl, sp.blockPosition())) com.scientia.util.Hazards.hydrogenFlash(sl, sp.blockPosition(), n);
                else sp.displayClientMessage(Component.literal("Hydrogen is 14× lighter than air: it rose and dispersed harmlessly.").withStyle(ChatFormatting.GRAY), false);
            }
            default -> sp.displayClientMessage(Component.literal("The gas dispersed into the air.").withStyle(ChatFormatting.GRAY), false);
        }
        return net.minecraft.world.InteractionResultHolder.consume(empty(stack, sp));
    }

    /** CO₂ on a fire: removes fire blocks around the clicked block. */
    @Override
    public @NotNull net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        ItemStack stack = ctx.getItemInHand();
        if (!"carbon_dioxide".equals(gasOf(stack)) || ctx.getPlayer() == null) return net.minecraft.world.InteractionResult.PASS;
        Level level = ctx.getLevel();
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        int put = 0;
        for (net.minecraft.core.BlockPos q : net.minecraft.core.BlockPos.betweenClosed(ctx.getClickedPos().offset(-1, -1, -1), ctx.getClickedPos().offset(1, 2, 1))) {
            if (level.getBlockState(q).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock) {
                level.removeBlock(q, false);
                put++;
            }
        }
        if (put == 0) return net.minecraft.world.InteractionResult.PASS;
        level.playSound(null, ctx.getClickedPos(), net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
        ctx.getPlayer().displayClientMessage(Component.literal("Fire out: CO₂ is denser than air and blankets the flame, cutting off oxygen.").withStyle(ChatFormatting.AQUA), true);
        ctx.getPlayer().setItemInHand(ctx.getHand(), empty(stack, ctx.getPlayer()));
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    private ItemStack empty(ItemStack stack, net.minecraft.world.entity.player.Player p) {
        if (p.isCreative()) return stack;
        return new ItemStack(this);
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
