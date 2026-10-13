package com.scientia.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/**
 * A knife switch. Right-click toggles it; a redstone signal also closes it, which is how Minecraft's
 * (fictional) redstone logic can control Scientia's (real) circuits.
 */
public class CircuitSwitchBlock extends ComponentBlock {
    public static final BooleanProperty CLOSED = BooleanProperty.create("closed");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** Contact resistance of a closed switch, in ohms. */
    public static final double CONTACT_RESISTANCE = 0.001;

    public CircuitSwitchBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CLOSED, false).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CLOSED, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return super.getStateForPlacement(ctx).setValue(POWERED, ctx.getLevel().hasNeighborSignal(ctx.getClickedPos()));
    }

    @Override
    public Role role(BlockState state) {
        return Role.SWITCH;
    }

    @Override
    public boolean conducts(BlockState state) {
        return state.getValue(CLOSED) || state.getValue(POWERED);
    }

    @Override
    public double resistance(BlockState state, Level level, BlockPos pos) {
        return CONTACT_RESISTANCE;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player,
                                          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof com.scientia.item.InstrumentItem) return InteractionResult.PASS;
        if (!level.isClientSide) {
            BlockState next = state.cycle(CLOSED);
            level.setBlock(pos, next, 3);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, next.getValue(CLOSED) ? 0.6f : 0.5f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos from, boolean moving) {
        if (level.isClientSide) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) level.setBlock(pos, state.setValue(POWERED, powered), 3);
    }
}
