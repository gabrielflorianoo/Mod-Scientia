package com.scientia.block;

import com.scientia.electric.ElectricalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/** A two-terminal circuit component without a block entity (resistor, switch, lamp). */
public abstract class ComponentBlock extends DirectionalBlock implements ElectricalBlock {
    private static final VoxelShape Y = Block.box(4, 0, 4, 12, 16, 12);
    private static final VoxelShape X = Block.box(0, 4, 4, 16, 12, 12);
    private static final VoxelShape Z = Block.box(4, 4, 0, 12, 12, 16);

    protected ComponentBlock(Properties props) {
        super(props);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getNearestLookingDirection().getOpposite());
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        Direction.Axis a = state.getValue(FACING).getAxis();
        return a == Direction.Axis.Y ? Y : a == Direction.Axis.X ? X : Z;
    }
}
