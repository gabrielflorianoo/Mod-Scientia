package com.scientia.blockentity;

import com.scientia.block.MachineBlock;
import com.scientia.registry.ModBlockEntities;
import com.scientia.util.Fuel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Fuel → heat → mechanical work → electricity. Overall efficiency is taken as 25 %, typical of a small
 * heat engine and dynamo; the rest of the fuel energy is rejected as heat (it is never "lost").
 * A running generator also burns 50 W of fuel power at idle (friction and heat losses).
 */
public class ThermalGeneratorBlockEntity extends ElectricalSourceBlockEntity {
    public static final double EFFICIENCY = 0.25, EMF = 12, INTERNAL_RESISTANCE = 0.1, IDLE_W = 50;

    private double fuelJ, fuelItemJ;
    /** Rotor angular speed for rendering (fraction of full speed). */
    private float rotorSpeed;
    public float rotorAngle, prevRotorAngle;

    public ThermalGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THERMAL_GENERATOR.get(), pos, state, 1);
    }

    @Override
    public List<SlotDef> slots() {
        return List.of(new SlotDef(0, 16, 100, false, "Fuel"));
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return Fuel.energyJ(stack) > 0;
    }

    @Override
    public double emf() {
        return fuelJ > 0 ? EMF : 0;
    }

    @Override
    public double internalResistance() {
        return INTERNAL_RESISTANCE;
    }

    @Override
    protected void consume(double current, double dt) {
        if (fuelJ <= 0) {
            ItemStack f = items.getStackInSlot(0);
            double e = Fuel.energyJ(f);
            if (e > 0) {
                fuelJ = e;
                fuelItemJ = e;
                f.shrink(1);
                items.setStackInSlot(0, f);
            }
        }
        double electrical = Math.max(0, emf() * current);
        if (fuelJ > 0) fuelJ = Math.max(0, fuelJ - (electrical / EFFICIENCY + IDLE_W) * dt);
        float speed = fuelJ > 0 ? (float) Math.min(1.0, 0.3 + electrical / 60.0) : 0f;
        if (Math.abs(speed - rotorSpeed) > 0.02f) {
            rotorSpeed = speed;
            requestSync();
        }
        MachineBlock.setActive(level, worldPosition, fuelJ > 0);
    }

    public double fuelFraction() {
        return fuelItemJ <= 0 ? 0 : fuelJ / fuelItemJ;
    }

    public float rotorSpeed() {
        return rotorSpeed;
    }

    @Override
    public void clientTick() {
        super.clientTick();
        prevRotorAngle = rotorAngle;
        rotorAngle = (rotorAngle + rotorSpeed * 24f) % 360f;
    }

    @Override
    protected void saveState(CompoundTag tag) {
        tag.putDouble("Fuel", fuelJ);
        tag.putDouble("FuelItem", fuelItemJ);
        tag.putFloat("Speed", rotorSpeed);
        tag.putDouble("I", lastCurrent);
        tag.putDouble("V", lastVoltage);
    }

    @Override
    protected void loadState(CompoundTag tag) {
        fuelJ = tag.getDouble("Fuel");
        fuelItemJ = tag.getDouble("FuelItem");
        rotorSpeed = tag.getFloat("Speed");
        lastCurrent = tag.getDouble("I");
        lastVoltage = tag.getDouble("V");
    }
}
