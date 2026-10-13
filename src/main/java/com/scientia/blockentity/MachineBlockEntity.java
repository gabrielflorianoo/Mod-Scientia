package com.scientia.blockentity;

import com.scientia.menu.MachineMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Base class for every Scientia machine: an item inventory exposed for automation, server-authoritative
 * state that is synchronised to clients through block-entity update packets, and an owner ("last user")
 * who receives research credit for what the machine observes.
 */
public abstract class MachineBlockEntity extends BlockEntity implements MenuProvider {
    /** A slot shown in the machine screen at (x, y). Output slots reject insertion. */
    public record SlotDef(int index, int x, int y, boolean output) {
    }

    protected final ItemStackHandler items;
    private final LazyOptional<IItemHandler> itemCap;
    protected UUID lastUser;
    private boolean syncPending;
    protected int ticks;

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots) {
        super(type, pos, state);
        this.items = new ItemStackHandler(slots) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                requestSync();
                onSlotChanged(slot);
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return MachineBlockEntity.this.isItemValid(slot, stack);
            }
        };
        this.itemCap = LazyOptional.of(() -> items);
    }

    public ItemStackHandler items() {
        return items;
    }

    public List<SlotDef> slots() {
        return List.of();
    }

    public boolean isItemValid(int slot, ItemStack stack) {
        for (SlotDef d : slots()) if (d.index() == slot && d.output()) return false;
        return true;
    }

    protected void onSlotChanged(int slot) {
    }

    /** Called on the server every tick. Subclasses call {@code super.serverTick()}. */
    public void serverTick() {
        ticks++;
        if (syncPending && ticks % 4 == 0 && level != null) {
            syncPending = false;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void clientTick() {
        ticks++;
    }

    /** Handles a validated button press from the machine screen. */
    public void handleAction(ServerPlayer player, int action, int arg) {
    }

    /** Right-click with an item, before the menu opens. Return PASS to open the menu. */
    public InteractionResult onUse(ServerPlayer player, InteractionHand hand, ItemStack held) {
        return InteractionResult.PASS;
    }

    public void markUser(Player player) {
        if (lastUser == null || !lastUser.equals(player.getUUID())) {
            lastUser = player.getUUID();
            setChanged();
        }
    }

    public UUID lastUser() {
        return lastUser;
    }

    public void requestSync() {
        syncPending = true;
        setChanged();
    }

    public ServerLevel serverLevel() {
        return level instanceof ServerLevel sl ? sl : null;
    }

    public void dropContents() {
        if (level == null) return;
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack s = items.getStackInSlot(i);
            if (!s.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, s.copy());
        }
    }

    /** Inserts a stack into an output slot, returning what did not fit. */
    protected ItemStack output(int slot, ItemStack stack) {
        ItemStack existing = items.getStackInSlot(slot);
        if (existing.isEmpty()) {
            items.setStackInSlot(slot, stack);
            return ItemStack.EMPTY;
        }
        if (ItemStack.isSameItemSameTags(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
            int move = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
            ItemStack grown = existing.copy();
            grown.grow(move);
            items.setStackInSlot(slot, grown);
            ItemStack rest = stack.copy();
            rest.shrink(move);
            return rest;
        }
        return stack;
    }

    // ------------------------------------------------------------------ persistence & sync

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        if (lastUser != null) tag.putUUID("LastUser", lastUser);
        saveState(tag);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) items.deserializeNBT(tag.getCompound("Items"));
        lastUser = tag.hasUUID("LastUser") ? tag.getUUID("LastUser") : null;
        loadState(tag);
    }

    protected abstract void saveState(CompoundTag tag);

    protected abstract void loadState(CompoundTag tag);

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && items.getSlots() > 0) return itemCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
    }

    // ------------------------------------------------------------------ menu

    @Override
    public @NotNull Component getDisplayName() {
        var key = ForgeRegistries.BLOCKS.getKey(getBlockState().getBlock());
        return Component.translatable("block." + (key == null ? "scientia.machine" : key.getNamespace() + "." + key.getPath()));
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player player) {
        return new MachineMenu(id, inv, this);
    }
}
