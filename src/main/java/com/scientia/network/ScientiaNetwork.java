package com.scientia.network;

import com.scientia.ScientiaMod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ScientiaNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(ScientiaMod.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private ScientiaNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SyncJournalPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncJournalPacket::encode).decoder(SyncJournalPacket::new).consumerMainThread(SyncJournalPacket::handle).add();
        CHANNEL.messageBuilder(MachineActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MachineActionPacket::encode).decoder(MachineActionPacket::new).consumerMainThread(MachineActionPacket::handle).add();
        CHANNEL.messageBuilder(WorkbenchCraftPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WorkbenchCraftPacket::encode).decoder(WorkbenchCraftPacket::new).consumerMainThread(WorkbenchCraftPacket::handle).add();
        CHANNEL.messageBuilder(ObservePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ObservePacket::encode).decoder(ObservePacket::new).consumerMainThread(ObservePacket::handle).add();
        CHANNEL.messageBuilder(JournalEditPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(JournalEditPacket::encode).decoder(JournalEditPacket::new).consumerMainThread(JournalEditPacket::handle).add();
    }
}
