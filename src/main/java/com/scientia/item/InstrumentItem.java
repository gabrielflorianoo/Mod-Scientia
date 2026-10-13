package com.scientia.item;

import com.scientia.block.ResistorBlock;
import com.scientia.blockentity.Measurable;
import com.scientia.blockentity.MixtureHolder;
import com.scientia.core.chem.AcidBaseSolver;
import com.scientia.core.chem.Indicator;
import com.scientia.core.chem.Mixture;
import com.scientia.electric.CircuitManager;
import com.scientia.electric.ElectricalBlock;
import com.scientia.journal.JournalService;
import com.scientia.util.Sci;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** Hand-held instruments: digital thermometer, multimeter and pH meter. Readings are made on the server. */
public class InstrumentItem extends Item {
    public enum Kind {THERMOMETER, MULTIMETER, PH_METER}

    public static final double THERMOMETER_UNCERTAINTY = 0.5;
    private final Kind kind;

    public InstrumentItem(Properties props, Kind kind) {
        super(props);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(ctx.getPlayer() instanceof ServerPlayer p)) return InteractionResult.PASS;
        BlockPos pos = ctx.getClickedPos();
        switch (kind) {
            case THERMOMETER -> thermometer(p, (ServerLevel) level, pos);
            case MULTIMETER -> multimeter(p, (ServerLevel) level, pos);
            case PH_METER -> {
                if (level.getBlockEntity(pos) instanceof MixtureHolder h) ph(p, h.mixture(), level.getBlockState(pos).getBlock().getName().getString());
                else p.displayClientMessage(Component.translatable("scientia.msg.ph_nothing").withStyle(ChatFormatting.GRAY), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (kind != Kind.PH_METER) return InteractionResultHolder.pass(stack);
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!(other.getItem() instanceof BeakerItem) || BeakerItem.isEmpty(other)) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide && player instanceof ServerPlayer sp) ph(sp, Sci.contentsOf(other), other.getHoverName().getString());
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // ------------------------------------------------------------------ thermometer

    private static void thermometer(ServerPlayer p, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);
        double t = Double.NaN;
        String note = null;
        if (be instanceof MixtureHolder h && !h.mixture().isEmpty()) t = h.temperatureK();
        if (Double.isNaN(t) && be instanceof Measurable m) {
            for (Measurable.Reading r : m.readings()) if (r.quantity().equals("temperature")) t = r.value();
        }
        double ambient = Sci.ambientK(level, pos.above());
        if (Double.isNaN(t)) {
            if (level.getFluidState(pos).is(FluidTags.LAVA) || state.is(Blocks.LAVA_CAULDRON)) {
                t = 273.15 + 1100;
                note = "scientia.msg.temp_lava";
            } else if (state.is(BlockTags.FIRE) || (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT))) {
                t = 273.15 + 800;
                note = "scientia.msg.temp_flame";
            } else if (state.is(Blocks.MAGMA_BLOCK)) {
                t = 273.15 + 700;
                note = "scientia.msg.temp_estimate";
            } else if (state.is(BlockTags.ICE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW) || state.is(Blocks.POWDER_SNOW)) {
                t = Math.min(ambient, 273.15);
            } else if (level.getFluidState(pos).is(FluidTags.WATER) || state.is(Blocks.WATER_CAULDRON)) {
                t = Math.max(ambient, 273.15);
            } else {
                t = ambient;
                note = "scientia.msg.temp_air";
            }
        }
        double shown = Math.round(t * 2) / 2.0;
        p.displayClientMessage(Component.translatable("scientia.msg.temperature", Sci.temperature(shown, true), THERMOMETER_UNCERTAINTY)
                .withStyle(ChatFormatting.AQUA), false);
        if (note != null) p.displayClientMessage(Component.translatable(note).withStyle(ChatFormatting.GRAY), false);
        JournalService.record(p, "temperature", Sci.itemId(state.getBlock().asItem()), shown, THERMOMETER_UNCERTAINTY, "K", "thermometer");
    }

    // ------------------------------------------------------------------ multimeter

    private static void multimeter(ServerPlayer p, ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof ElectricalBlock eb)) {
            p.displayClientMessage(Component.translatable("scientia.msg.multimeter_nothing").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        CircuitManager.Reading r = CircuitManager.get(level).reading(pos);
        String name = s.getBlock().getName().getString();
        String subject = Sci.itemId(s.getBlock().asItem());
        if (eb.role(s) == ElectricalBlock.Role.WIRE) {
            if (r == null) {
                p.displayClientMessage(Component.translatable("scientia.msg.unpowered", name).withStyle(ChatFormatting.GRAY), false);
            } else {
                p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "%s: potential %s relative to the source's − terminal",
                        name, Sci.si(r.potential(), "V"))).withStyle(ChatFormatting.AQUA), false);
                JournalService.record(p, "voltage", subject, r.potential(), 0.01, "V", "multimeter");
            }
            return;
        }
        double nominal = eb.role(s) == ElectricalBlock.Role.LOAD ? eb.resistance(s, level, pos) : Double.NaN;
        if (r == null || Math.abs(r.current()) < 1e-6) {
            String text = Double.isNaN(nominal)
                    ? String.format(Locale.ROOT, "%s: no current flowing", name)
                    : String.format(Locale.ROOT, "%s: no current flowing — ohmmeter reads %s", name, Sci.si(nominal, "Ω"));
            p.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.AQUA), false);
            if (!Double.isNaN(nominal)) JournalService.record(p, "resistance", subject, nominal, nominal * 0.01, "Ω", "multimeter");
            if (r != null) JournalService.record(p, "voltage", subject, r.voltage(), 0.01, "V", "multimeter");
            return;
        }
        double v = Math.abs(r.voltage()), i = Math.abs(r.current()), pw = Math.abs(r.power());
        p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "%s:  V = %s   I = %s   P = %s%s", name, Sci.si(v, "V"), Sci.si(i, "A"),
                Sci.si(pw, "W"), i > 1e-6 && eb.role(s) == ElectricalBlock.Role.LOAD ? "   V/I = " + Sci.si(v / i, "Ω") : "")).withStyle(ChatFormatting.AQUA), false);
        JournalService.record(p, "voltage", subject, v, Math.max(0.01, v * 0.005), "V", "multimeter");
        JournalService.record(p, "current", subject, i, Math.max(0.001, i * 0.01), "A", "multimeter");
        if (pw > 0.01) JournalService.record(p, "power", subject, pw, Math.max(0.01, pw * 0.015), "W", "multimeter");
        if (s.getBlock() instanceof ResistorBlock && Math.abs(v / i - nominal) / nominal < 0.02) {
            JournalService.record(p, "resistance", subject, v / i, v / i * 0.012, "Ω", "multimeter (V/I)");
            JournalService.grant(p, "derived:resistance");
        }
    }

    // ------------------------------------------------------------------ pH

    private static void ph(ServerPlayer p, Mixture m, String what) {
        double ph = AcidBaseSolver.pH(m, Sci.db().substances());
        if (Double.isNaN(ph)) {
            p.displayClientMessage(Component.translatable("scientia.msg.ph_dry").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        double shown = Math.round(ph * 100) / 100.0;
        p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "%s: pH %.2f ± 0.05 (%s)", what, shown, AcidBaseSolver.classify(ph)))
                .withStyle(ChatFormatting.AQUA), false);
        StringBuilder ind = new StringBuilder();
        for (Indicator i : Indicator.values()) {
            if (ind.length() > 0) ind.append(" · ");
            ind.append(i.displayName).append(": ").append(i.describe(ph));
        }
        p.displayClientMessage(Component.literal(ind.toString()).withStyle(ChatFormatting.GRAY), false);
        JournalService.record(p, "ph", what, shown, 0.05, "", "ph_meter");
        if (ph < 6.5) JournalService.grant(p, "analyze:acid");
        if (ph > 7.5) JournalService.grant(p, "analyze:base");
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tips, @NotNull TooltipFlag flag) {
        tips.add(Component.translatable("item.scientia." + switch (kind) {
            case THERMOMETER -> "thermometer";
            case MULTIMETER -> "multimeter";
            case PH_METER -> "ph_meter";
        } + ".tip").withStyle(ChatFormatting.GRAY));
    }
}
