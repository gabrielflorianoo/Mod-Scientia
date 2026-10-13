package com.scientia.network;

import com.scientia.blockentity.MachineBlockEntity;
import com.scientia.menu.MachineMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: a button pressed in a machine screen. The server only accepts it when the player
 * actually has that machine's menu open, so actions cannot be triggered remotely.
 */
public final class MachineActionPacket {
    private final BlockPos pos;
    private final int action;
    private final int arg;

    public MachineActionPacket(BlockPos pos, int action, int arg) {
        this.pos = pos;
        this.action = action;
        this.arg = arg;
    }

    public MachineActionPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(action);
        buf.writeVarInt(arg);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.containerMenu instanceof MachineMenu menu && menu.pos().equals(pos)
                && menu.stillValid(player) && player.level().getBlockEntity(pos) instanceof MachineBlockEntity be) {
            be.markUser(player);
            be.handleAction(player, action, arg);
        }
        ctx.get().setPacketHandled(true);
    }
}
