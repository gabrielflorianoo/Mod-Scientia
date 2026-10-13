package com.scientia.blockentity;

import com.scientia.electric.CircuitManager;
import com.scientia.electric.ElectricalSource;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Shared registration and metering for batteries, generators and solar panels. */
public abstract class ElectricalSourceBlockEntity extends MachineBlockEntity implements ElectricalSource, Measurable {
    protected double lastCurrent, lastVoltage;

    protected ElectricalSourceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots) {
        super(type, pos, state, slots);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl) CircuitManager.get(sl).addSource(worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel sl) CircuitManager.get(sl).removeSource(worldPosition);
    }

    @Override
    public void onSolved(double current, double terminalVoltage, double dt) {
        boolean changed = Math.abs(current - lastCurrent) > 1e-4 || Math.abs(terminalVoltage - lastVoltage) > 1e-3;
        lastCurrent = current;
        lastVoltage = terminalVoltage;
        consume(current, dt);
        if (changed || ticks % 20 == 0) requestSync();
    }

    /** Updates the energy store for the energy delivered by the EMF (emf × I × dt). */
    protected abstract void consume(double current, double dt);

    public double lastCurrent() {
        return lastCurrent;
    }

    public double lastVoltage() {
        return lastVoltage;
    }

    @Override
    public List<Reading> readings() {
        List<Reading> r = new ArrayList<>();
        r.add(new Reading("voltage", lastVoltage, 0.01, "V"));
        r.add(new Reading("current", lastCurrent, 0.001, "A"));
        r.add(new Reading("power", lastVoltage * lastCurrent, 0.01, "W"));
        return r;
    }
}
