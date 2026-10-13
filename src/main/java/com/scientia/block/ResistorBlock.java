package com.scientia.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/** A fixed resistor; right-click cycles through standard E-series values (10, 47, 100, 470, 1000 Ω). */
public class ResistorBlock extends ComponentBlock {
    public static final double[] VALUES = {10, 47, 100, 470, 1000};
    public static final IntegerProperty VALUE = IntegerProperty.create("value", 0, VALUES.length - 1);

    public ResistorBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(VALUE, 2));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, VALUE);
    }

    @Override
    public Role role(BlockState state) {
        return Role.LOAD;
    }

    @Override
    public double resistance(BlockState state, Level level, BlockPos pos) {
        return VALUES[state.getValue(VALUE)];
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player,
                                          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (!player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            int next = (state.getValue(VALUE) + 1) % VALUES.length;
            level.setBlock(pos, state.setValue(VALUE, next), 3);
            player.displayClientMessage(Component.translatable("scientia.msg.resistor", (int) VALUES[next]), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
