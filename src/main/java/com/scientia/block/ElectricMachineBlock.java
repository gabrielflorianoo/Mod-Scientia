package com.scientia.block;

import com.scientia.blockentity.MachineBlockEntity;
import com.scientia.electric.ElectricalBlock;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/** A circuit component with a block entity. The front face (towards the player when placed) is the + terminal. */
public class ElectricMachineBlock extends MachineBlock implements ElectricalBlock {
    private final Role role;
    private final boolean horizontalOnly;

    public ElectricMachineBlock(Properties props, Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type,
                                VoxelShape shape, Role role, boolean horizontalOnly) {
        super(props, type, shape);
        this.role = role;
        this.horizontalOnly = horizontalOnly;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (horizontalOnly) return super.getStateForPlacement(ctx);
        return defaultBlockState().setValue(FACING, ctx.getNearestLookingDirection().getOpposite());
    }

    @Override
    public Role role(BlockState state) {
        return role;
    }
}
