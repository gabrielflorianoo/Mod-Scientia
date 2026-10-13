package com.scientia.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: the player's full journal as JSON. */
public final class SyncJournalPacket {
    private static final int MAX = 1 << 19;
    private final String json;

    public SyncJournalPacket(String json) {
        this.json = json;
    }

    public SyncJournalPacket(FriendlyByteBuf buf) {
        this.json = buf.readUtf(MAX);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(json, MAX);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.scientia.client.ClientJournal.update(json));
        ctx.get().setPacketHandled(true);
    }
}
