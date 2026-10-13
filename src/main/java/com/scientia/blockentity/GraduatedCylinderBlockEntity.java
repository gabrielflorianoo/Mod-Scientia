package com.scientia.blockentity;

import com.scientia.core.data.MaterialAnalysis;
import com.scientia.core.data.MaterialEntry;
import com.scientia.item.BeakerItem;
import com.scientia.journal.JournalService;
import com.scientia.registry.ModBlockEntities;
import com.scientia.util.Sci;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A 100 mL graduated cylinder for volume by displacement (Archimedes). It is read to ±0.2 mL; a
 * displaced volume is the difference of two readings, so its uncertainty is √2 × 0.2 ≈ 0.28 mL.
 * A floating sample only displaces its own weight of water, which the cylinder reports faithfully.
 */
public class GraduatedCylinderBlockEntity extends MachineBlockEntity implements Measurable {
    public static final double CAPACITY_ML = 100, FILL_ML = 50.0, READ_UNCERTAINTY = 0.2;
    public static final double WATER_DENSITY = 0.997;
    public static final int ACTION_RECORD = 0, ACTION_EMPTY = 1;

    private double waterMl = 0;
    private double displacedMl = 0;
    private boolean floating, overflow, unknown;
    private String subject = "";
    private String sampleKey = "";

    public GraduatedCylinderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRADUATED_CYLINDER.get(), pos, state, 1);
    }

    @Override
    public List<SlotDef> slots() {
        return List.of(new SlotDef(0, 16, 60, false, "Sample"));
    }

    @Override
    public InteractionResult onUse(ServerPlayer player, InteractionHand hand, ItemStack held) {
        boolean water = held.is(Items.WATER_BUCKET) || (held.getItem() instanceof BeakerItem && BeakerItem.isPureWater(held)
                && held.getCount() == 1 && com.scientia.util.Liquids.volumeMl(com.scientia.util.Sci.contentsOf(held)) >= FILL_ML);
        if (water && waterMl < 1) {
            waterMl = FILL_ML;
            if (held.is(Items.WATER_BUCKET) && !player.isCreative()) player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            if (held.getItem() instanceof BeakerItem && !player.isCreative()) {
                com.scientia.core.chem.Mixture m = com.scientia.util.Sci.contentsOf(held);
                m.remove("water", FILL_ML * WATER_DENSITY / com.scientia.util.Sci.db().substances().molarMass("water"));
                com.scientia.util.Sci.setContents(held, m);
            }
            level.playSound(null, worldPosition, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.7f, 1.0f);
            recompute();
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void onSlotChanged(int slot) {
        if (level != null && !level.isClientSide) recompute();
    }

    private void recompute() {
        ItemStack s = items.getStackInSlot(0);
        displacedMl = 0;
        floating = overflow = unknown = false;
        subject = "";
        sampleKey = "";
        if (!s.isEmpty() && waterMl > 0) {
            MaterialEntry m = Sci.material(s);
            Double rho = m == null ? null : MaterialAnalysis.referenceDensity(m, Sci.db());
            if (m == null || rho == null) {
                unknown = true;
            } else {
                double mass = Sci.sampleMass(s, level);
                if (rho < WATER_DENSITY) {
                    floating = true;
                    displacedMl = mass / WATER_DENSITY;
                } else {
                    displacedMl = mass / rho;
                }
                if (waterMl + displacedMl > CAPACITY_ML) overflow = true;
                subject = m.id();
                sampleKey = Sci.sampleKey(s, level);
            }
        }
        requestSync();
    }

    /** Level read off the scale, rounded to 0.1 mL. */
    public double reading() {
        double v = overflow ? CAPACITY_ML : waterMl + displacedMl;
        return Math.round(v * 10) / 10.0;
    }

    public double waterMl() {
        return waterMl;
    }

    public double displacedReading() {
        return Math.round((reading() - waterMl) * 10) / 10.0;
    }

    public boolean floating() {
        return floating;
    }

    public boolean overflow() {
        return overflow;
    }

    public boolean unknown() {
        return unknown;
    }

    @Override
    public void handleAction(ServerPlayer player, int action, int arg) {
        if (action == ACTION_EMPTY) {
            waterMl = 0;
            recompute();
            return;
        }
        if (action != ACTION_RECORD) return;
        if (waterMl <= 0) {
            player.displayClientMessage(Component.translatable("scientia.msg.cylinder_empty").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        if (items.getStackInSlot(0).isEmpty() || unknown || overflow) {
            player.displayClientMessage(Component.translatable(overflow ? "scientia.msg.cylinder_overflow" : "scientia.msg.no_sample_data")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }
        double v = displacedReading();
        double u = Math.sqrt(2) * READ_UNCERTAINTY;
        JournalService.record(player, "volume", subject, v, u, "mL", "graduated_cylinder|" + sampleKey);
        player.displayClientMessage(Component.translatable("scientia.msg.recorded_volume", Sci.fixed(reading(), 1), Sci.fixed(waterMl, 1), Sci.fixed(v, 1))
                .withStyle(ChatFormatting.AQUA), false);
        if (floating) player.displayClientMessage(Component.translatable("scientia.msg.floating").withStyle(ChatFormatting.YELLOW), false);
        MaterialEntry m = Sci.material(items.getStackInSlot(0));
        if (m != null) JournalService.tryDeriveDensity(player, m, sampleKey);
    }

    @Override
    public List<Reading> readings() {
        return List.of(new Reading("volume", reading(), READ_UNCERTAINTY, "mL"));
    }

    @Override
    protected void saveState(CompoundTag tag) {
        tag.putDouble("Water", waterMl);
        tag.putDouble("Displaced", displacedMl);
        tag.putBoolean("Floating", floating);
        tag.putBoolean("Overflow", overflow);
        tag.putBoolean("Unknown", unknown);
        tag.putString("Subject", subject);
        tag.putString("SampleKey", sampleKey);
    }

    @Override
    protected void loadState(CompoundTag tag) {
        waterMl = tag.getDouble("Water");
        displacedMl = tag.getDouble("Displaced");
        floating = tag.getBoolean("Floating");
        overflow = tag.getBoolean("Overflow");
        unknown = tag.getBoolean("Unknown");
        subject = tag.getString("Subject");
        sampleKey = tag.getString("SampleKey");
    }
}
