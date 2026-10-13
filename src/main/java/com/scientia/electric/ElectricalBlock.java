package com.scientia.electric;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A block that takes part in Scientia's DC circuits. Wires connect on all six sides; every other component
 * has exactly two terminals, on its front ({@code FACING}) and back faces. For sources and electrolysis cells
 * the front terminal is the positive (+) one.
 */
public interface ElectricalBlock {
    enum Role {WIRE, LOAD, SOURCE, SWITCH, CELL}

    Role role(BlockState state);

    default Direction front(BlockState state) {
        return state.getValue(BlockStateProperties.FACING);
    }

    /** Resistance in ohms of a load (or the contact resistance of a closed switch). */
    default double resistance(BlockState state, Level level, BlockPos pos) {
        return 1.0;
    }

    /** False for an open switch. */
    default boolean conducts(BlockState state) {
        return true;
    }

    /** True if this block has a terminal (or wire end) on the given side. */
    default boolean hasTerminal(BlockState state, Direction side) {
        return role(state) == Role.WIRE || side.getAxis() == front(state).getAxis();
    }

    /** Can a wire in direction {@code fromWire} reach a terminal of this neighbouring block? */
    static boolean wireConnects(BlockState neighbor, Direction fromWire) {
        return neighbor.getBlock() instanceof ElectricalBlock e && e.hasTerminal(neighbor, fromWire.getOpposite());
    }
}
