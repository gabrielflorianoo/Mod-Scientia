package com.scientia.blockentity;

import com.scientia.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A 12 V, 7 Ah lead–acid-style battery: capacity 12 V × 7 Ah = 302.4 kJ. Its open-circuit voltage falls
 * from about 12.7 V when full to 11.8 V when nearly empty (typical lead–acid behaviour, linearised).
 * Charging stores 85 % of the energy delivered (coulombic and voltage losses, simplified).
 */
public class BatteryBlockEntity extends ElectricalSourceBlockEntity {
    public static final double CAPACITY_J = 12 * 7 * 3600;
    public static final double INTERNAL_RESISTANCE = 0.05;
    public static final double CHARGE_EFFICIENCY = 0.85;

    private double energyJ = CAPACITY_J;

    public BatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BATTERY.get(), pos, state, 0);
    }

    public double stateOfCharge() {
        return energyJ / CAPACITY_J;
    }

    @Override
    public double emf() {
        double soc = stateOfCharge();
        return soc <= 0.001 ? 0 : 11.8 + 0.9 * soc;
    }

    @Override
    public double internalResistance() {
        return INTERNAL_RESISTANCE;
    }

    @Override
    protected void consume(double current, double dt) {
        double p = emf() * current;
        if (p > 0) energyJ = Math.max(0, energyJ - p * dt);
        else energyJ = Math.min(CAPACITY_J, energyJ - p * dt * CHARGE_EFFICIENCY);
        setChanged();
    }

    @Override
    public java.util.List<Reading> readings() {
        var r = new java.util.ArrayList<>(super.readings());
        r.add(new Reading("charge", stateOfCharge() * 100, 0.1, "%"));
        return r;
    }

    @Override
    protected void saveState(CompoundTag tag) {
        tag.putDouble("Energy", energyJ);
        tag.putDouble("I", lastCurrent);
        tag.putDouble("V", lastVoltage);
    }

    @Override
    protected void loadState(CompoundTag tag) {
        energyJ = tag.contains("Energy") ? tag.getDouble("Energy") : CAPACITY_J;
        lastCurrent = tag.getDouble("I");
        lastVoltage = tag.getDouble("V");
    }
}
