package com.scientia.menu;

import com.scientia.blockentity.MachineBlockEntity;
import com.scientia.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

/** Generic menu for all machines: the block entity's slots plus the player inventory. */
public final class MachineMenu extends AbstractContainerMenu {
    public static final int WIDTH = 256, HEIGHT = 230;
    public static final int INV_X = 48, INV_Y = 148;

    private final BlockPos pos;
    private final MachineBlockEntity machine;
    private final int machineSlots;

    /** Client constructor (from the network buffer). */
    public MachineMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, inv.player.level().getBlockEntity(pos) instanceof MachineBlockEntity be ? be : null, pos);
    }

    /** Server constructor. */
    public MachineMenu(int id, Inventory inv, MachineBlockEntity be) {
        this(id, inv, be, be.getBlockPos());
    }

    private MachineMenu(int id, Inventory inv, MachineBlockEntity be, BlockPos pos) {
        super(ModMenus.MACHINE.get(), id);
        this.pos = pos;
        this.machine = be;
        int count = 0;
        if (be != null) {
            ItemStackHandler h = be.items();
            for (MachineBlockEntity.SlotDef d : be.slots()) {
                final boolean out = d.output();
                addSlot(new SlotItemHandler(h, d.index(), d.x(), d.y()) {
                    @Override
                    public boolean mayPlace(@NotNull ItemStack stack) {
                        return !out && super.mayPlace(stack);
                    }
                });
                count++;
            }
        }
        machineSlots = count;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, INV_X + col * 18, INV_Y + 58));
    }

    public BlockPos pos() {
        return pos;
    }

    public MachineBlockEntity machine() {
        return machine;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(stack, 0, machineSlots, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return machine != null && !machine.isRemoved()
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
