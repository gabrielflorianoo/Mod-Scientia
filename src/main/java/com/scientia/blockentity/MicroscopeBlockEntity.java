package com.scientia.blockentity;

import com.scientia.core.data.MicroscopeSample;
import com.scientia.journal.JournalService;
import com.scientia.registry.ModBlockEntities;
import com.scientia.util.Sci;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** A compound light microscope. Samples are matched against the microscope database by item id. */
public class MicroscopeBlockEntity extends MachineBlockEntity {
    public static final int ACTION_OBSERVE = 0;

    public MicroscopeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MICROSCOPE.get(), pos, state, 1);
    }

    @Override
    public List<SlotDef> slots() {
        return List.of(new SlotDef(0, 16, 60, false));
    }

    public static MicroscopeSample sampleFor(ItemStack stack) {
        if (stack.isEmpty()) return null;
        String id = Sci.itemId(stack.getItem());
        for (MicroscopeSample s : Sci.db().microscope().values()) if (id.equals(s.item())) return s;
        return null;
    }

    public MicroscopeSample sample() {
        return sampleFor(items.getStackInSlot(0));
    }

    @Override
    public void handleAction(ServerPlayer player, int action, int arg) {
        if (action != ACTION_OBSERVE) return;
        MicroscopeSample s = sample();
        if (s == null) {
            player.displayClientMessage(Component.translatable("scientia.msg.no_slide").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        JournalService.grant(player, "observe:microscope");
        JournalService.record(player, "observation", s.id(), s.magnification(), 0, "×", "microscope");
    }

    @Override
    protected void saveState(CompoundTag tag) {
    }

    @Override
    protected void loadState(CompoundTag tag) {
    }
}
