package com.scientia.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * A 12 V, 6 W filament lamp, modelled as a fixed 24 Ω resistance (R = V²/P). Its light level is set by the
 * circuit solver from the actual power dissipated. Real filaments have a lower resistance when cold.
 */
public class FilamentLampBlock extends ComponentBlock {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 15);
    public static final double RATED_VOLTAGE = 12, RATED_POWER = 6;
    public static final double RESISTANCE = RATED_VOLTAGE * RATED_VOLTAGE / RATED_POWER;
    private static final VoxelShape SHAPE = Block.box(3, 3, 3, 13, 13, 13);

    public FilamentLampBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEVEL);
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public Role role(BlockState state) {
        return Role.LOAD;
    }

    @Override
    public double resistance(BlockState state, Level level, BlockPos pos) {
        return RESISTANCE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 20);
    }

    /** Self-check: a lamp that is lit but no longer part of a solved, powered network switches off. */
    @Override
    @SuppressWarnings("deprecation")
    public void tick(@NotNull BlockState state, @NotNull net.minecraft.server.level.ServerLevel level, @NotNull BlockPos pos,
                     @NotNull net.minecraft.util.RandomSource random) {
        var reading = com.scientia.electric.CircuitManager.get(level).reading(pos);
        if (state.getValue(LEVEL) > 0 && reading == null) level.setBlock(pos, state.setValue(LEVEL, 0), 3);
        level.scheduleTick(pos, this, 40);
    }

    /** Light level 0–15 from dissipated power: full brightness at rated power. */
    public static int levelFor(double power) {
        if (power < 0.05) return 0;
        return (int) Math.max(1, Math.min(15, Math.round(15 * power / RATED_POWER)));
    }
}
