package com.scientia.client.screen;

import com.scientia.client.ClientJournal;
import com.scientia.core.data.WorkbenchRecipe;
import com.scientia.core.research.ResearchNode;
import com.scientia.network.ScientiaNetwork;
import com.scientia.network.WorkbenchCraftPacket;
import com.scientia.util.Sci;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Scientific Workbench: builds apparatus that your research has unlocked. The list shows what each
 * recipe needs and which research unlocks it; crafting is validated by the server.
 */
@OnlyIn(Dist.CLIENT)
public class WorkbenchScreen extends Screen {
    private final BlockPos pos;
    private int x0, y0, w, h, scroll;
    private String selected;

    public WorkbenchScreen(BlockPos pos) {
        super(Component.translatable("block.scientia.scientific_workbench"));
        this.pos = pos;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<WorkbenchRecipe> recipes() {
        return new ArrayList<>(Sci.db().workbenchRecipes().values());
    }

    @Override
    protected void init() {
        w = Math.min(width - 20, 330);
        h = Math.min(height - 20, 220);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        addRenderableWidget(new SciButton(x0 + w - 86, y0 + h - 22, 80, 16, () -> "Build", () -> {
            if (selected != null) ScientiaNetwork.CHANNEL.sendToServer(new WorkbenchCraftPacket(pos, selected));
        }).accent(UiKit.GREEN));
    }

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
    }

    private static int count(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
        return n;
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        UiKit.frame(g, x0, y0, w, h);
        UiKit.header(g, font, title.getString(), x0 + 6, y0 + 4, UiKit.AMBER);
        var journal = ClientJournal.get();
        List<WorkbenchRecipe> list = recipes();
        int lx = x0 + 6, ly = y0 + 18, rowH = 20, rows = (h - 24) / rowH;
        UiKit.inset(g, lx, ly, 150, rows * rowH + 2);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows)));
        for (int i = 0; i < rows && i + scroll < list.size(); i++) {
            WorkbenchRecipe r = list.get(i + scroll);
            int ry = ly + 1 + i * rowH;
            boolean unlocked = r.research() == null || journal.research().contains(r.research());
            if (r.id().equals(selected)) g.fill(lx + 1, ry, lx + 149, ry + rowH, 0xFF2E3A46);
            Item it = item(r.result().item());
            if (it != null) g.renderItem(new ItemStack(it), lx + 3, ry + 2);
            String name = it == null ? r.id() : new ItemStack(it).getHoverName().getString();
            g.drawString(font, font.plainSubstrByWidth(name, 110), lx + 22, ry + 2, unlocked ? UiKit.text() : UiKit.muted(), false);
            g.drawString(font, unlocked ? "unlocked" : "locked", lx + 22, ry + 11, unlocked ? UiKit.GREEN : UiKit.RED, false);
        }
        WorkbenchRecipe r = selected == null ? null : Sci.db().workbenchRecipes().get(selected);
        int dx = x0 + 164, dy = y0 + 20;
        if (r == null) {
            UiKit.wrap(g, font, "Select an apparatus. Each one is unlocked by a research concept, which you complete through real measurements and experiments.", dx, dy, w - 172, UiKit.muted());
            return;
        }
        Inventory inv = Minecraft.getInstance().player.getInventory();
        Item out = item(r.result().item());
        if (out != null) {
            g.renderItem(new ItemStack(out, r.result().count()), dx, dy);
            g.renderItemDecorations(font, new ItemStack(out, r.result().count()), dx, dy);
            g.drawString(font, new ItemStack(out).getHoverName().getString(), dx + 20, dy + 4, UiKit.text(), false);
        }
        dy += 24;
        ResearchNode node = r.research() == null ? null : Sci.db().research().get(r.research());
        if (node != null) {
            boolean ok = journal.research().contains(node.id());
            dy = UiKit.wrap(g, font, (ok ? "✔ Research: " : "✗ Requires research: ") + node.title(), dx, dy, w - 172, ok ? UiKit.GREEN : UiKit.RED) + 4;
        }
        g.drawString(font, "Ingredients:", dx, dy, UiKit.muted(), false);
        dy += 11;
        for (WorkbenchRecipe.Stack s : r.ingredients()) {
            Item it = item(s.item());
            if (it == null) continue;
            int have = count(inv, it);
            g.renderItem(new ItemStack(it), dx, dy);
            g.drawString(font, s.count() + "× " + new ItemStack(it).getHoverName().getString() + "  (" + have + ")", dx + 20, dy + 4,
                    have >= s.count() ? UiKit.text() : UiKit.RED, false);
            dy += 18;
        }
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int lx = x0 + 6, ly = y0 + 18;
        if (mx >= lx && mx < lx + 150 && my >= ly) {
            int idx = (int) ((my - ly - 1) / 20) + scroll;
            List<WorkbenchRecipe> list = recipes();
            if (idx >= 0 && idx < list.size()) {
                selected = list.get(idx).id();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, scroll - (int) Math.signum(delta));
        return true;
    }
}
