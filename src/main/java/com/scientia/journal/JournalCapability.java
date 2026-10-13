package com.scientia.journal;

import com.scientia.ScientiaMod;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JournalCapability {
    public static final Capability<JournalHolder> JOURNAL = CapabilityManager.get(new CapabilityToken<>() {
    });
    public static final ResourceLocation KEY = ScientiaMod.id("journal");

    private JournalCapability() {
    }

    /** Returns the player's journal holder (a detached empty one if the capability is missing). */
    public static JournalHolder get(Player player) {
        return player.getCapability(JOURNAL).orElseGet(JournalHolder::new);
    }

    public static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final JournalHolder holder = new JournalHolder();
        private final LazyOptional<JournalHolder> optional = LazyOptional.of(() -> holder);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == JOURNAL ? optional.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return holder.save();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            holder.load(nbt);
        }
    }
}
