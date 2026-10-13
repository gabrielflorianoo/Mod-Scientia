package com.scientia.blockentity;

import com.scientia.block.MachineBlock;
import com.scientia.core.chem.Mixture;
import com.scientia.core.chem.ReactionEngine;
import com.scientia.core.math.Constants;
import com.scientia.electric.ElectricalLoad;
import com.scientia.item.BeakerItem;
import com.scientia.item.GasCanisterItem;
import com.scientia.journal.JournalService;
import com.scientia.registry.ModBlockEntities;
import com.scientia.util.Hazards;
import com.scientia.util.Liquids;
import com.scientia.util.Sci;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

import java.util.Set;

/**
 * Electrolysis cell in a circuit (front face = anode, + terminal). It behaves as a counter-EMF of
 * 1.48 V (the thermoneutral voltage of water splitting) plus 2 Ω of electrolyte resistance; it only
 * conducts when the applied voltage exceeds the counter-EMF. Products follow Faraday's law exactly:
 * n = Q / (zF). Gases are collected into canisters (0.01 mol each).
 */
public class ElectrolysisStationBlockEntity extends MachineBlockEntity implements ElectricalLoad, MixtureHolder, Measurable {
    public static final int SLOT_IN = 0, SLOT_OUT = 1, SLOT_CANISTER = 2, SLOT_FILLED = 3;
    public static final double COUNTER_EMF = 1.48, RESISTANCE = 2.0;
    /** 0.01 mol ≈ 224 mL at STP: at ~5 A, Faraday's law gives one hydrogen canister in about 6.5 minutes. */
    public static final double CANISTER_MOL = 0.01;
    /** The collection tubes hold about 0.02 mol of gas; more chlorine than that escapes. */
    public static final double CHLORINE_STORE_MOL = 0.02;
    public static final List<String> GASES = List.of("hydrogen", "oxygen", "chlorine");

    private final Mixture electrolyte = new Mixture();
    private final Mixture gas = new Mixture();
    private double current, voltage, chargeC;

    public ElectrolysisStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTROLYSIS_STATION.get(), pos, state, 4);
    }

    @Override
    public List<SlotDef> slots() {
        return List.of(new SlotDef(SLOT_IN, 16, 60, false, "Beaker"), new SlotDef(SLOT_OUT, 16, 104, true, "Out"),
                new SlotDef(SLOT_CANISTER, 228, 60, false, "Canister"), new SlotDef(SLOT_FILLED, 228, 104, true, "Gas"));
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (slot == SLOT_IN) return stack.getItem() instanceof BeakerItem;
        if (slot == SLOT_CANISTER) return stack.getItem() instanceof GasCanisterItem && Sci.contentsOf(stack).isEmpty();
        return super.isItemValid(slot, stack);
    }

    @Override
    public double counterEmf() {
        return electrolyte.contains("water") ? COUNTER_EMF : 1e3; // empty cell: effectively open
    }

    @Override
    public void onSolved(double v, double i, double p, double dt) {
        voltage = v;
        current = i;
        MachineBlock.setActive(level, worldPosition, i > 1e-3);
        if (i <= 1e-6 || dt <= 0) return;
        double q = i * dt;
        chargeC += q;
        ReactionEngine.StepResult r = Sci.db().reactionEngine().step(electrolyte,
                new ReactionEngine.Conditions(298.15, Set.of("electrolysis"), true, q), dt);
        for (String g : GASES) {
            double n = electrolyte.get(g);
            if (n > 0) {
                electrolyte.remove(g, n);
                gas.add(g, n);
            }
        }
        ServerLevel sl = serverLevel();
        if (sl != null && gas.get("chlorine") > CHLORINE_STORE_MOL) {
            // No canister to collect it: the gas space overflows and chlorine leaks out.
            double leak = gas.get("chlorine");
            gas.remove("chlorine", leak);
            Hazards.toxicGas(sl, worldPosition, "Chlorine", leak);
        }
        if (sl != null && gas.get("hydrogen") > 0.1 && Hazards.ignitionNear(sl, worldPosition)) {
            double h = gas.get("hydrogen");
            gas.remove("hydrogen", h);
            Hazards.hydrogenFlash(sl, worldPosition, h);
        }
        if (sl != null) {
            for (ReactionEngine.Event e : r.events()) {
                JournalService.grant(sl, lastUser, "reaction:" + e.reaction().id());
                JournalService.grant(sl, lastUser, "reaction:any");
            }
        }
        requestSync();
    }

    /** Moles of H₂ expected from the charge passed so far if all current split water (z = 2 per H₂). */
    public double faradayHydrogen() {
        return chargeC / (2 * Constants.FARADAY);
    }

    public Mixture gas() {
        return gas;
    }

    public double current() {
        return current;
    }

    public double voltage() {
        return voltage;
    }

    public double chargeC() {
        return chargeC;
    }

    @Override
    public void serverTick() {
        super.serverTick();
        if (ticks % 10 != 0) return;
        ItemStack in = items.getStackInSlot(SLOT_IN);
        if (!in.isEmpty() && items.getStackInSlot(SLOT_OUT).isEmpty()) {
            if (!BeakerItem.isEmpty(in)) {
                Sci.contentsOf(in).amounts().forEach(electrolyte::add);
                items.setStackInSlot(SLOT_OUT, new ItemStack(in.getItem()));
            } else {
                Mixture taken = Liquids.takeLiquid(electrolyte, BeakerItem.CAPACITY_ML * 0.997);
                if (taken.isEmpty()) return;
                items.setStackInSlot(SLOT_OUT, BeakerItem.filled(in.getItem(), taken));
            }
            in.shrink(1);
            items.setStackInSlot(SLOT_IN, in);
            requestSync();
        }
        ItemStack can = items.getStackInSlot(SLOT_CANISTER);
        if (!can.isEmpty() && items.getStackInSlot(SLOT_FILLED).isEmpty()) {
            for (String g : GASES) {
                if (gas.get(g) >= CANISTER_MOL) {
                    gas.remove(g, CANISTER_MOL);
                    items.setStackInSlot(SLOT_FILLED, GasCanisterItem.filled(can.getItem(), g, CANISTER_MOL));
                    can.shrink(1);
                    items.setStackInSlot(SLOT_CANISTER, can);
                    requestSync();
                    break;
                }
            }
        }
    }

    @Override
    public void clientTick() {
        super.clientTick();
        if (level != null && current > 1e-3 && level.random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.BUBBLE_POP, worldPosition.getX() + 0.25 + level.random.nextDouble() * 0.5,
                    worldPosition.getY() + 0.8, worldPosition.getZ() + 0.25 + level.random.nextDouble() * 0.5, 0, 0.02, 0);
        }
    }

    @Override
    public Mixture mixture() {
        return electrolyte;
    }

    @Override
    public double temperatureK() {
        return 298.15;
    }

    @Override
    public List<Reading> readings() {
        return List.of(new Reading("current", current, 0.001, "A"), new Reading("voltage", voltage, 0.01, "V"),
                new Reading("charge", chargeC, 0.1, "C"));
    }

    @Override
    protected void saveState(CompoundTag tag) {
        tag.put("Electrolyte", Sci.writeMixture(electrolyte));
        tag.put("Gas", Sci.writeMixture(gas));
        tag.putDouble("I", current);
        tag.putDouble("V", voltage);
        tag.putDouble("Q", chargeC);
    }

    @Override
    protected void loadState(CompoundTag tag) {
        electrolyte.clear();
        Sci.readMixture(tag.getCompound("Electrolyte")).amounts().forEach(electrolyte::add);
        gas.clear();
        Sci.readMixture(tag.getCompound("Gas")).amounts().forEach(gas::add);
        current = tag.getDouble("I");
        voltage = tag.getDouble("V");
        chargeC = tag.getDouble("Q");
    }

}
