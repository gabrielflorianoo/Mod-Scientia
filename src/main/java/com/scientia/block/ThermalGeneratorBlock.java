package com.scientia.block;

import com.scientia.registry.ModBlockEntities;

/** Burns fuel to drive a dynamo: chemical → thermal → mechanical → electrical energy. */
public class ThermalGeneratorBlock extends ElectricMachineBlock {
    public ThermalGeneratorBlock(Properties props) {
        super(props, ModBlockEntities.THERMAL_GENERATOR, SHAPE_FULL, Role.SOURCE, true);
    }
}
