package com.scientia.block;

import com.scientia.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/** A small DC motor. Its comparator output is proportional to mechanical power. */
public class ElectricMotorBlock extends ElectricMachineBlock {
    /** Winding resistance plus a simplified constant load, in ohms. */
    public static final double RESISTANCE = 12.0;

    public ElectricMotorBlock(Properties props) {
        super(props, ModBlockEntities.ELECTRIC_MOTOR, SHAPE_FULL, Role.LOAD, false);
    }

    @Override
    public double resistance(BlockState state, Level level, BlockPos pos) {
        return RESISTANCE;
    }

    @Override
    public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(@NotNull BlockState state, Level level, @NotNull BlockPos pos) {
        return level.getBlockEntity(pos) instanceof com.scientia.blockentity.ElectricMotorBlockEntity m ? m.comparatorLevel() : 0;
    }
}
