package com.scientia.network;

import com.scientia.core.data.WorkbenchRecipe;
import com.scientia.journal.JournalService;
import com.scientia.registry.ModBlocks;
import com.scientia.util.Sci;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

/**
 * Client → server: craft a research-gated recipe at a Scientific Workbench. Everything is validated
 * on the server: proximity to a real workbench, research progress, and ingredients in the inventory.
 */
public final class WorkbenchCraftPacket {
    private final BlockPos pos;
    private final String recipe;

    public WorkbenchCraftPacket(BlockPos pos, String recipe) {
        this.pos = pos;
        this.recipe = recipe;
    }

    public WorkbenchCraftPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readUtf(128));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(recipe, 128);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (p == null) return;
        if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64
                || !p.level().getBlockState(pos).is(ModBlocks.SCIENTIFIC_WORKBENCH.get())) return;
        WorkbenchRecipe r = Sci.db().workbenchRecipes().get(recipe);
        if (r == null) return;
        if (r.research() != null && !JournalService.journal(p).research().contains(r.research())) {
            p.displayClientMessage(Component.translatable("scientia.msg.locked").withStyle(ChatFormatting.RED), true);
            return;
        }
        Inventory inv = p.getInventory();
        for (WorkbenchRecipe.Stack s : r.ingredients()) {
            Item item = item(s.item());
            if (item == null || count(inv, item) < s.count()) {
                p.displayClientMessage(Component.translatable("scientia.msg.missing").withStyle(ChatFormatting.RED), true);
                return;
            }
        }
        Item out = item(r.result().item());
        if (out == null) return;
        for (WorkbenchRecipe.Stack s : r.ingredients()) remove(inv, item(s.item()), s.count());
        ItemStack result = new ItemStack(out, r.result().count());
        if (!inv.add(result)) p.drop(result, false);
        p.containerMenu.broadcastChanges();
    }

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) return null;
        return ForgeRegistries.ITEMS.getValue(rl);
    }

    private static int count(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private static void remove(Inventory inv, Item item, int amount) {
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                int take = Math.min(amount, s.getCount());
                s.shrink(take);
                amount -= take;
            }
        }
    }
}
