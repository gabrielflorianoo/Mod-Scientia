package com.scientia.journal;

import com.scientia.ScientiaMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Attaches, persists, copies and synchronises the journal capability. */
public final class JournalEvents {
    private JournalEvents() {
    }

    @Mod.EventBusSubscriber(modid = ScientiaMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void registerCaps(RegisterCapabilitiesEvent event) {
            event.register(JournalHolder.class);
        }
    }

    @Mod.EventBusSubscriber(modid = ScientiaMod.MODID)
    public static final class ForgeBus {
        @SubscribeEvent
        public static void attach(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Player) {
                event.addCapability(JournalCapability.KEY, new JournalCapability.Provider());
            }
        }

        /** Keeps the journal after death and when returning from the End. */
        @SubscribeEvent
        public static void clone(PlayerEvent.Clone event) {
            Player original = event.getOriginal();
            original.reviveCaps();
            JournalHolder old = JournalCapability.get(original);
            JournalHolder now = JournalCapability.get(event.getEntity());
            now.load(old.save());
            original.invalidateCaps();
        }

        @SubscribeEvent
        public static void login(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer sp) {
                JournalCapability.get(sp).data().reevaluate(com.scientia.util.Sci.db(), false);
                JournalService.sync(sp);
            }
        }

        @SubscribeEvent
        public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer sp) JournalService.sync(sp);
        }

        @SubscribeEvent
        public static void changeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer sp) JournalService.sync(sp);
        }
    }
}
