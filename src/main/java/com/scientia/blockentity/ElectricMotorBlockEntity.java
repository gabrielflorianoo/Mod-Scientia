package com.scientia.blockentity;

import com.scientia.electric.ElectricalLoad;
import com.scientia.journal.JournalService;
import com.scientia.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A small permanent-magnet DC motor (12 V rated). Shaft speed is taken as proportional to the voltage
 * (no-load speed 3000 rpm at 12 V) and mechanical output as 70 % of electrical input — a simplified
 * constant-efficiency model; a real motor's efficiency depends on load.
 */
public class ElectricMotorBlockEntity extends MachineBlockEntity implements ElectricalLoad, Measurable {
    public static final double EFFICIENCY = 0.70, RATED_V = 12, NO_LOAD_RPM = 3000;

    private double voltage, current, power;
    public float angle, prevAngle;

    public ElectricMotorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTRIC_MOTOR.get(), pos, state, 0);
    }

    @Override
    public void onSolved(double v, double i, double p, double dt) {
        boolean changed = Math.abs(p - power) > 0.05;
        voltage = Math.abs(v);
        current = Math.abs(i);
        power = Math.abs(p);
        if (changed) {
            requestSync();
            if (level != null) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            if (power > 0.5) JournalService.grant(serverLevel(), lastUser, "measure:power");
        }
    }

    public double rpm() {
        return NO_LOAD_RPM * Math.min(1.5, voltage / RATED_V);
    }

    public double mechanicalPower() {
        return power * EFFICIENCY;
    }

    public int comparatorLevel() {
        return (int) Math.max(0, Math.min(15, Math.round(15 * mechanicalPower() / (RATED_V * RATED_V / 12 * EFFICIENCY))));
    }

    @Override
    public void clientTick() {
        super.clientTick();
        prevAngle = angle;
        angle = (float) ((angle + rpm() / 60.0 * 360.0 / 20.0 * 0.05) % 360.0); // slowed ×20 for visibility
    }

    @Override
    public List<Reading> readings() {
        return List.of(new Reading("power", power, 0.01, "W"), new Reading("voltage", voltage, 0.01, "V"),
                new Reading("current", current, 0.001, "A"));
    }

    @Override
    protected void saveState(CompoundTag tag) {
        tag.putDouble("V", voltage);
        tag.putDouble("I", current);
        tag.putDouble("P", power);
    }

    @Override
    protected void loadState(CompoundTag tag) {
        voltage = tag.getDouble("V");
        current = tag.getDouble("I");
        power = tag.getDouble("P");
    }
}
