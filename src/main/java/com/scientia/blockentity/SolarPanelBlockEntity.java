package com.scientia.blockentity;

import com.scientia.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A small photovoltaic panel (open-circuit 18 V, short-circuit 1 A in full sun). The real non-linear
 * I–V curve is approximated by a straight line (an EMF with 18 Ω internal resistance), so maximum power,
 * 4.5 W, is delivered into an 18 Ω load. Irradiance follows sky light, the sun's height and rain.
 */
public class SolarPanelBlockEntity extends ElectricalSourceBlockEntity {
    public static final double VOC = 18, ISC = 1;

    private double irradiance;

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_PANEL.get(), pos, state, 0);
    }

    @Override
    public void serverTick() {
        super.serverTick();
        if (ticks % 20 != 0 || level == null) return;
        double irr = 0;
        if (level.dimensionType().hasSkyLight()) {
            double sky = level.getBrightness(LightLayer.SKY, worldPosition.above()) / 15.0;
            double sun = Math.cos(level.getSunAngle(1.0f)); // 1 at noon, ≤0 at night
            irr = Math.max(0, sun) * sky * (level.isRaining() ? 0.3 : 1.0);
        }
        if (Math.abs(irr - irradiance) > 0.01) {
            irradiance = irr;
            requestSync();
        }
    }

    public double irradiance() {
        return irradiance;
    }

    @Override
    public double emf() {
        return irradiance > 0.01 ? VOC * Math.min(1, 0.85 + 0.15 * irradiance) : 0;
    }

    @Override
    public double internalResistance() {
        // Short-circuit current scales with irradiance: r = Voc / (Isc × irradiance)
        return irradiance > 0.01 ? emf() / (ISC * irradiance) : 1e6;
    }

    @Override
    protected void consume(double current, double dt) {
    }

    @Override
    protected void saveState(CompoundTag tag) {
        tag.putDouble("Irr", irradiance);
        tag.putDouble("I", lastCurrent);
        tag.putDouble("V", lastVoltage);
    }

    @Override
    protected void loadState(CompoundTag tag) {
        irradiance = tag.getDouble("Irr");
        lastCurrent = tag.getDouble("I");
        lastVoltage = tag.getDouble("V");
    }
}
