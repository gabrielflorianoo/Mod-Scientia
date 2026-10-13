package com.scientia.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * A block that opens a client-side screen (Scientific Workbench, Optical Bench, Motion Track). Anything
 * with gameplay consequences done in these screens is sent to the server and validated there.
 */
public class ScreenBlock extends HorizontalDirectionalBlock {
    public enum Kind {WORKBENCH, OPTICAL_BENCH, MOTION_TRACK}

    public static final VoxelShape SHAPE_TABLE = Block.box(0, 0, 0, 16, 15, 16);
    public static final VoxelShape SHAPE_TRACK = Block.box(0, 0, 0, 16, 8, 16);

    private final Kind kind;
    private final VoxelShape shape;

    public ScreenBlock(Properties props, Kind kind, VoxelShape shape) {
        super(props);
        this.kind = kind;
        this.shape = shape;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return shape;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player,
                                          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.scientia.client.ClientHooks.openBlockScreen(kind, pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
