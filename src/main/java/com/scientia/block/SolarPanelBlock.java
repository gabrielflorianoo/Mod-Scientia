package com.scientia.block;

import com.scientia.registry.ModBlockEntities;
import net.minecraft.world.level.block.Block;

/** A small photovoltaic panel; output follows sky light and the sun's height. */
public class SolarPanelBlock extends ElectricMachineBlock {
    public SolarPanelBlock(Properties props) {
        super(props, ModBlockEntities.SOLAR_PANEL, Block.box(0, 0, 0, 16, 6, 16), Role.SOURCE, true);
    }
}
