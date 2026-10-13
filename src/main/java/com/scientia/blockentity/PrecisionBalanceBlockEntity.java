package com.scientia.blockentity;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Precision balance: 0.01 g resolution, ±0.01 g uncertainty. A sample's mass comes from the material data
 * (one item = a nominal sample with ±4 % specimen-to-specimen variation), beakers add their contents.
 */
public class PrecisionBalanceBlockEntity extends MachineBlockEntity implements Measurable {
    public static final double RESOLUTION = 0.01;
    public static final int ACTION_RECORD = 0, ACTION_TARE = 1;

    private double massG = Double.NaN;
    private double tareG = 0;
    private String subject = "";
    private String sampleKey = "";
    /** Client-side animated pan offset (0..1). */
    public float panOffset, prevPanOffset;

    public PrecisionBalanceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PRECISION_BALANCE.get(), pos, state, 1);
    }

    @Override
    public List<SlotDef> slots() {
        return List.of(new SlotDef(0, 16, 60, false, "Pan"));
    }

    @Override
    protected void onSlotChanged(int slot) {
        if (level == null || level.isClientSide) return;
        ItemStack s = items.getStackInSlot(0);
        subject = "";
        sampleKey = "";
        if (s.isEmpty()) {
            massG = 0;
        } else if (s.getItem() instanceof BeakerItem) {
            massG = BeakerItem.GLASS_MASS_G * s.getCount() + Sci.contentsOf(s).massGrams(Sci.db().substances());
            subject = "beaker";
        } else {
            MaterialEntry m = Sci.material(s);
            if (m == null) {
                massG = Double.NaN;
            } else {
                massG = Sci.sampleMass(s, level);
                subject = m.id();
                sampleKey = Sci.sampleKey(s, level);
            }
        }
        requestSync();
    }

    /** Displayed reading, rounded to the balance resolution. */
    public double reading() {
        if (Double.isNaN(massG)) return Double.NaN;
        return Math.round((massG - tareG) / RESOLUTION) * RESOLUTION;
    }

    public double rawMass() {
        return massG;
    }

    public double tare() {
        return tareG;
    }

    @Override
    public void handleAction(ServerPlayer player, int action, int arg) {
        if (action == ACTION_TARE) {
            tareG = Double.isNaN(massG) ? 0 : massG;
            requestSync();
            return;
        }
        if (action != ACTION_RECORD) return;
        double r = reading();
        if (Double.isNaN(r) || items.getStackInSlot(0).isEmpty()) {
            player.displayClientMessage(Component.translatable("scientia.msg.no_sample_data").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        if (tareG != 0) {
            player.displayClientMessage(Component.translatable("scientia.msg.tared").withStyle(ChatFormatting.GRAY), false);
        }
        String subj = subject.isEmpty() ? "unknown" : subject;
        JournalService.record(player, "mass", subj, r, RESOLUTION, "g", "precision_balance|" + sampleKey);
        player.displayClientMessage(Component.translatable("scientia.msg.recorded_mass", Sci.fixed(r, 2)).withStyle(ChatFormatting.AQUA), false);
        MaterialEntry m = Sci.material(items.getStackInSlot(0));
        if (m != null && tareG == 0) JournalService.tryDeriveDensity(player, m, sampleKey);
    }

    @Override
    public List<Reading> readings() {
        return List.of(new Reading("mass", reading(), RESOLUTION, "g"));
    }

    @Override
    public void clientTick() {
        super.clientTick();
        prevPanOffset = panOffset;
        double m = Double.isNaN(massG) ? 0 : massG;
        float target = (float) Math.min(1.0, m / 500.0);
        panOffset += (target - panOffset) * 0.15f;
    }

    @Override
    protected void saveState(CompoundTag tag) {
        tag.putDouble("Mass", massG);
        tag.putDouble("Tare", tareG);
        tag.putString("Subject", subject);
        tag.putString("SampleKey", sampleKey);
    }

    @Override
    protected void loadState(CompoundTag tag) {
        massG = tag.contains("Mass") ? tag.getDouble("Mass") : Double.NaN;
        tareG = tag.getDouble("Tare");
        subject = tag.getString("Subject");
        sampleKey = tag.getString("SampleKey");
    }
}
