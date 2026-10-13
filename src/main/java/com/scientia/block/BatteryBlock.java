package com.scientia.block;

import com.scientia.registry.ModBlockEntities;
import net.minecraft.world.level.block.Block;

/** A 12 V rechargeable battery (lead–acid-like: 7 Ah, 0.05 Ω internal resistance). */
public class BatteryBlock extends ElectricMachineBlock {
    public BatteryBlock(Properties props) {
        super(props, ModBlockEntities.BATTERY, Block.box(2, 0, 2, 14, 12, 14), Role.SOURCE, false);
    }
}
