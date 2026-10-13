package com.scientia.block;

import com.scientia.blockentity.ElectrolysisStationBlockEntity;
import com.scientia.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** An electrolysis cell: a counter-EMF plus electrolyte resistance in the circuit. */
public class ElectrolysisStationBlock extends ElectricMachineBlock {
    public ElectrolysisStationBlock(Properties props) {
        super(props, ModBlockEntities.ELECTROLYSIS_STATION, SHAPE_FULL, Role.CELL, true);
    }

    @Override
    public double resistance(BlockState state, Level level, BlockPos pos) {
        return ElectrolysisStationBlockEntity.RESISTANCE;
    }
}
