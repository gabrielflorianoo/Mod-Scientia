package com.scientia.block;

import com.scientia.blockentity.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** A machine with a block entity, a facing and an "active" flag used for models, light and animation. */
public class MachineBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public static final VoxelShape SHAPE_FULL = Shapes.block();
    public static final VoxelShape SHAPE_LOW = Block.box(1, 0, 1, 15, 10, 15);
    public static final VoxelShape SHAPE_SLIM = Block.box(3, 0, 3, 13, 16, 13);
    public static final VoxelShape SHAPE_HEATER = Block.box(1, 0, 1, 15, 12, 15);
    public static final VoxelShape SHAPE_VESSEL = Block.box(2, 0, 2, 14, 15, 14);

    private final Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type;
    private final VoxelShape shape;

    public MachineBlock(Properties props, Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type, VoxelShape shape) {
        super(props);
        this.type = type;
        this.shape = shape;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public @NotNull BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public @NotNull BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return shape;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return type.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> beType) {
        if ((Object) beType != type.get()) return null;
        return level.isClientSide
                ? (lvl, pos, st, be) -> ((MachineBlockEntity) be).clientTick()
                : (lvl, pos, st, be) -> ((MachineBlockEntity) be).serverTick();
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player,
                                          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof com.scientia.item.InstrumentItem) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity be && player instanceof ServerPlayer sp) {
            be.markUser(sp);
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof com.scientia.item.InstrumentItem) return InteractionResult.PASS; // let the instrument measure
            InteractionResult r = be.onUse(sp, hand, held);
            if (r != InteractionResult.PASS) return r;
            NetworkHooks.openScreen(sp, be, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, @NotNull Level level, @NotNull BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof MachineBlockEntity be) be.dropContents();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /** Sets the ACTIVE flag (used by models and lighting) only when it actually changes. */
    public static void setActive(Level level, BlockPos pos, boolean active) {
        BlockState s = level.getBlockState(pos);
        if (s.hasProperty(ACTIVE) && s.getValue(ACTIVE) != active) level.setBlock(pos, s.setValue(ACTIVE, active), 3);
    }
}
