package com.scientia.blockentity;

import com.scientia.core.chem.Mixture;
import com.scientia.item.BeakerItem;
import com.scientia.journal.JournalService;
import com.scientia.registry.ModBlockEntities;
import com.scientia.util.Liquids;
import com.scientia.util.Sci;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

/**
 * Liebig-style condenser placed above a Laboratory Heater. Water vapour is cooled back to liquid and
 * collected (100 % recovery is assumed). Place an empty beaker in the input slot to draw off distillate.
 */
public class CondenserBlockEntity extends MachineBlockEntity implements Measurable, MixtureHolder {
    public static final int SLOT_IN = 0, SLOT_OUT = 1;
    public static final double CAPACITY_G = 1000;

    private final Mixture distillate = new Mixture();
    private int flowTicks;

    public CondenserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONDENSER.get(), pos, state, 2);
    }

    @Override
    public List<SlotDef> slots() {
        return List.of(new SlotDef(SLOT_IN, 16, 60, false, "Beaker"), new SlotDef(SLOT_OUT, 16, 104, true, "Filled"));
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (slot == SLOT_IN) return stack.getItem() instanceof BeakerItem && BeakerItem.isEmpty(stack);
        return super.isItemValid(slot, stack);
    }

    /** Called by the heater below with the mass of water vapour produced this step. */
    public void collect(double grams, boolean fromSolution, UUID user) {
        if (Sci.waterGrams(distillate) + grams > CAPACITY_G) return;
        distillate.add("water", grams / Sci.db().substances().molarMass("water"));
        flowTicks = 40;
        if (fromSolution && Sci.waterGrams(distillate) >= 10 && serverLevel() != null) {
            JournalService.grant(serverLevel(), user, "separate:distillation");
        }
        requestSync();
    }

    public double distillateMl() {
        return Liquids.volumeMl(distillate);
    }

    public boolean flowing() {
        return flowTicks > 0;
    }

    @Override
    public void serverTick() {
        super.serverTick();
        if (flowTicks > 0 && --flowTicks == 0) requestSync();
        if (ticks % 10 != 0) return;
        ItemStack in = items.getStackInSlot(SLOT_IN);
        if (!in.isEmpty() && Sci.waterGrams(distillate) >= 1 && items.getStackInSlot(SLOT_OUT).isEmpty()) {
            Mixture portion = Liquids.takeLiquid(distillate, BeakerItem.CAPACITY_ML * 0.997);
            items.setStackInSlot(SLOT_OUT, BeakerItem.filled(in.getItem(), portion));
            in.shrink(1);
            items.setStackInSlot(SLOT_IN, in);
            requestSync();
        }
    }

    @Override
    public Mixture mixture() {
        return distillate;
    }

    @Override
    public double temperatureK() {
        return level == null ? 293.15 : Sci.ambientK(level, worldPosition) + 5;
    }

    @Override
    public List<Reading> readings() {
        return List.of(new Reading("volume", distillateMl(), 0.5, "mL"));
    }

    @Override
    protected void saveState(CompoundTag tag) {
        tag.put("Distillate", Sci.writeMixture(distillate));
        tag.putInt("Flow", flowTicks);
    }

    @Override
    protected void loadState(CompoundTag tag) {
        distillate.clear();
        Sci.readMixture(tag.getCompound("Distillate")).amounts().forEach(distillate::add);
        flowTicks = tag.getInt("Flow");
    }
}
