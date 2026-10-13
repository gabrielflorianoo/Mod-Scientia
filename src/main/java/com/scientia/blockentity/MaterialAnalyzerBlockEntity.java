package com.scientia.blockentity;

import com.scientia.core.data.MaterialAnalysis;
import com.scientia.core.data.MaterialEntry;
import com.scientia.core.data.SpectrumEntry;
import com.scientia.core.physics.Materials;
import com.scientia.journal.JournalService;
import com.scientia.registry.ModBlockEntities;
import com.scientia.util.Sci;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Material Analyzer with two modes:
 * <ul>
 * <li>Emission spectrometer: the sample is atomised and excited; each element present emits its
 * characteristic lines (database values), which are matched to identify the elements.</li>
 * <li>Conductivity probe: the resistance of a 1 cm × 1 cm × 1 cm specimen, R = ρL/A = ρ / 0.01 m.</li>
 * </ul>
 */
public class MaterialAnalyzerBlockEntity extends MachineBlockEntity {
    public static final int ACTION_ANALYZE = 0, ACTION_MODE = 1;
    public static final int MODE_SPECTRUM = 0, MODE_CONDUCTIVITY = 1;
    private static final int ANALYSIS_TICKS = 60;

    private int mode;
    private int progress = -1;
    private final List<String> detected = new ArrayList<>();
    private final List<String> undetectable = new ArrayList<>();
    private String analysedItem = "";
    private double resistanceOhm = Double.NaN;

    public MaterialAnalyzerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MATERIAL_ANALYZER.get(), pos, state, 1);
    }

    @Override
    public List<SlotDef> slots() {
        return List.of(new SlotDef(0, 16, 60, false));
    }

    @Override
    protected void onSlotChanged(int slot) {
        progress = -1;
        detected.clear();
        undetectable.clear();
        resistanceOhm = Double.NaN;
        analysedItem = "";
    }

    public int mode() {
        return mode;
    }

    public float progress() {
        return progress < 0 ? 0 : progress / (float) ANALYSIS_TICKS;
    }

    public boolean analysing() {
        return progress >= 0 && progress < ANALYSIS_TICKS;
    }

    public List<String> detected() {
        return detected;
    }

    public List<String> undetectable() {
        return undetectable;
    }

    public String analysedItem() {
        return analysedItem;
    }

    public double resistanceOhm() {
        return resistanceOhm;
    }

    @Override
    public void handleAction(ServerPlayer player, int action, int arg) {
        if (action == ACTION_MODE) {
            mode = 1 - mode;
            onSlotChanged(0);
        } else if (action == ACTION_ANALYZE && !items.getStackInSlot(0).isEmpty() && !analysing()) {
            progress = 0;
            markUser(player);
        }
        requestSync();
    }

    @Override
    public void serverTick() {
        super.serverTick();
        if (!analysing()) return;
        progress++;
        com.scientia.block.MachineBlock.setActive(level, worldPosition, progress < ANALYSIS_TICKS);
        if (progress == ANALYSIS_TICKS) complete();
        if (progress % 10 == 0) requestSync();
    }

    private void complete() {
        ItemStack s = items.getStackInSlot(0);
        MaterialEntry m = Sci.material(s);
        ServerPlayer p = JournalService.online(serverLevel(), lastUser);
        analysedItem = Sci.itemId(s.getItem());
        if (m == null) {
            if (p != null) p.displayClientMessage(Component.translatable("scientia.msg.no_sample_data").withStyle(ChatFormatting.GRAY), false);
            requestSync();
            return;
        }
        if (mode == MODE_SPECTRUM) {
            Map<String, Double> elements = MaterialAnalysis.elementalComposition(m, Sci.db());
            for (String el : elements.keySet()) {
                SpectrumEntry sp = Sci.db().spectra().get(el);
                if (sp != null && !sp.lines().isEmpty()) detected.add(el);
                else undetectable.add(el);
            }
            if (p != null) {
                JournalService.grant(p, "analyze:spectrum");
                for (String el : detected) JournalService.grant(p, "element:" + el);
                p.displayClientMessage(Component.translatable("scientia.msg.spectrum", m.name(),
                        detected.isEmpty() ? "—" : String.join(", ", detected)).withStyle(ChatFormatting.AQUA), false);
            }
        } else {
            resistanceOhm = resistivity(m) / 0.01;
            if (p != null) {
                JournalService.record(p, "resistance", m.id(), resistanceOhm, resistanceOhm * 0.05, "Ω", "material_analyzer");
                if (resistanceOhm < 1) JournalService.grant(p, "analyze:conductor");
                if (resistanceOhm > 1e6) JournalService.grant(p, "analyze:insulator");
                p.displayClientMessage(Component.literal(String.format(Locale.ROOT, "%s: R = %s for a 1 cm cube (%s)", m.name(),
                        Sci.si(resistanceOhm, "Ω"), resistanceOhm < 1 ? "conductor" : resistanceOhm > 1e6 ? "insulator" : "poor conductor / semiconductor"))
                        .withStyle(ChatFormatting.AQUA), false);
            }
        }
        requestSync();
    }

    /** Typical resistivity (Ω·m) for a material: real values for metals, orders of magnitude otherwise. */
    public static double resistivity(MaterialEntry m) {
        if (m.composition() != null && m.composition().size() == 1) {
            String s = m.composition().get(0).substance();
            Double r = switch (s) {
                case "copper" -> Materials.RESISTIVITY.get("copper");
                case "iron" -> Materials.RESISTIVITY.get("iron");
                case "gold" -> Materials.RESISTIVITY.get("gold");
                case "carbon_graphite" -> Materials.RESISTIVITY.get("graphite");
                default -> null;
            };
            if (r != null) return r;
        }
        String c = m.conductivity() == null ? "" : m.conductivity();
        if (c.startsWith("excellent conductor") || c.startsWith("conductor (fictional)")) return 2e-8;
        if (c.startsWith("conductor")) return 1e-7;
        if (c.startsWith("semiconductor")) return 1.0;
        if (c.startsWith("poor conductor")) return 100.0;
        return 1e12;
    }

    @Override
    protected void saveState(CompoundTag tag) {
        tag.putInt("Mode", mode);
        tag.putInt("Progress", progress);
        ListTag d = new ListTag();
        for (String s : detected) d.add(StringTag.valueOf(s));
        tag.put("Detected", d);
        ListTag u = new ListTag();
        for (String s : undetectable) u.add(StringTag.valueOf(s));
        tag.put("Undetectable", u);
        tag.putString("Analysed", analysedItem);
        tag.putDouble("R", resistanceOhm);
    }

    @Override
    protected void loadState(CompoundTag tag) {
        mode = tag.getInt("Mode");
        progress = tag.contains("Progress") ? tag.getInt("Progress") : -1;
        detected.clear();
        ListTag d = tag.getList("Detected", 8);
        for (int i = 0; i < d.size(); i++) detected.add(d.getString(i));
        undetectable.clear();
        ListTag u = tag.getList("Undetectable", 8);
        for (int i = 0; i < u.size(); i++) undetectable.add(u.getString(i));
        analysedItem = tag.getString("Analysed");
        resistanceOhm = tag.contains("R") ? tag.getDouble("R") : Double.NaN;
    }
}
