package com.scientia.client.screen;

import com.scientia.client.ClientJournal;
import com.scientia.core.chem.ChemicalFormula;
import com.scientia.core.chem.Element;
import com.scientia.core.chem.ElementDatabase;
import com.scientia.core.chem.Substance;
import com.scientia.core.data.ExperimentDefinition;
import com.scientia.core.data.FormulaEntry;
import com.scientia.core.data.GlossaryEntry;
import com.scientia.core.data.MaterialAnalysis;
import com.scientia.core.data.MaterialEntry;
import com.scientia.core.research.JournalData;
import com.scientia.core.research.ResearchNode;
import com.scientia.core.stats.DataSeries;
import com.scientia.network.JournalEditPacket;
import com.scientia.network.ScientiaNetwork;
import com.scientia.util.Sci;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The scientific journal: an interactive encyclopaedia of the player's progress. All progress shown
 * here comes from the server-synchronised journal; the encyclopaedia content comes from the science data.
 */
@OnlyIn(Dist.CLIENT)
public class JournalScreen extends Screen {
    enum Tab {RESEARCH("Research"), ELEMENTS("Periodic table"), MATTER("Matter"), EXPERIMENTS("Experiments"),
        DATA("Data"), FORMULAS("Formulas"), GLOSSARY("Glossary"), NOTES("Notes");
        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    static final Map<String, Integer> CATEGORY_COLORS = new LinkedHashMap<>();

    static {
        CATEGORY_COLORS.put("alkali_metal", 0xFFE0705A);
        CATEGORY_COLORS.put("alkaline_earth_metal", 0xFFE8A85A);
        CATEGORY_COLORS.put("transition_metal", 0xFF6A9FD8);
        CATEGORY_COLORS.put("post_transition_metal", 0xFF7FB8A8);
        CATEGORY_COLORS.put("metalloid", 0xFFB8B060);
        CATEGORY_COLORS.put("reactive_nonmetal", 0xFF70C070);
        CATEGORY_COLORS.put("noble_gas", 0xFFB28CFF);
        CATEGORY_COLORS.put("lanthanide", 0xFFD88AB8);
        CATEGORY_COLORS.put("actinide", 0xFFC07890);
        CATEGORY_COLORS.put("unknown", 0xFF707880);
    }

    private static Tab lastTab = Tab.RESEARCH;
    private Tab tab = lastTab;
    private int x0, y0, w, h;
    private EditBox search;
    private EditBox noteBox;
    private String selected = "";
    private int listScroll, detailScroll;
    private double panX = 10, panY = 10;
    private String categoryFilter = "";
    private boolean showMaterials;
    private String plotQuantity = "";
    private final List<SciButton> dynamicButtons = new ArrayList<>();

    public JournalScreen() {
        super(Component.translatable("item.scientia.science_journal"));
    }

    @Override
    protected void init() {
        w = Math.min(width - 16, 440);
        h = Math.min(height - 16, 260);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        int tx = x0 + 6;
        for (Tab t : Tab.values()) {
            int tw = font.width(t.label) + 10;
            addRenderableWidget(new SciButton(tx, y0 + 4, tw, 14, () -> (tab == t ? "▸" : "") + t.label, () -> switchTab(t))
                    .accent(t == Tab.RESEARCH ? UiKit.AMBER : UiKit.CYAN));
            tx += tw + 2;
        }
        search = new EditBox(font, x0 + w - 96, y0 + 22, 90, 12, Component.literal("Search"));
        search.setMaxLength(48);
        search.setHint(Component.literal("search…"));
        search.setResponder(s -> listScroll = 0);
        addRenderableWidget(search);
        noteBox = new EditBox(font, x0 + 8, y0 + h - 20, w - 70, 12, Component.literal("Note"));
        noteBox.setMaxLength(JournalData.MAX_NOTE_LENGTH);
        noteBox.visible = tab == Tab.NOTES;
        addRenderableWidget(noteBox);
        rebuildDynamic();
    }

    private void switchTab(Tab t) {
        tab = t;
        lastTab = t;
        selected = "";
        listScroll = 0;
        detailScroll = 0;
        noteBox.visible = t == Tab.NOTES;
        rebuildDynamic();
    }

    /** Buttons that depend on tab and selection (filters, hypothesis choices, favourites, add note). */
    private void rebuildDynamic() {
        for (SciButton b : dynamicButtons) removeWidget(b);
        dynamicButtons.clear();
        switch (tab) {
            case ELEMENTS -> dyn(new SciButton(x0 + w - 200, y0 + 21, 100, 14,
                    () -> categoryFilter.isEmpty() ? "All categories" : categoryFilter.replace('_', ' '), this::cycleCategory));
            case MATTER -> dyn(new SciButton(x0 + w - 200, y0 + 21, 100, 14,
                    () -> showMaterials ? "Showing: materials" : "Showing: substances", () -> {
                showMaterials = !showMaterials;
                selected = "";
            }));
            case NOTES -> dyn(new SciButton(x0 + w - 58, y0 + h - 21, 50, 14, () -> "Add note", () -> {
                String v = noteBox.getValue().strip();
                if (!v.isEmpty()) {
                    ScientiaNetwork.CHANNEL.sendToServer(new JournalEditPacket(JournalEditPacket.Op.ADD_NOTE, "", v));
                    noteBox.setValue("");
                }
            }));
            case EXPERIMENTS -> {
                ExperimentDefinition e = Sci.db().experiments().get(selected);
                JournalData j = ClientJournal.get();
                if (e != null && e.hypothesis() != null && !j.experiments().contains(e.id())
                        && (j.hypotheses().get(e.id()) == null || j.hypotheses().get(e.id()).observed() == null)) {
                    int by = y0 + h - 22;
                    int bx = x0 + 150;
                    for (ExperimentDefinition.Option o : e.hypothesis().options()) {
                        String label = switch (o.id()) {
                            case "increase" -> "↑ increase";
                            case "decrease" -> "↓ decrease";
                            case "equal" -> "= no change";
                            default -> o.id();
                        };
                        String id = e.id(), opt = o.id();
                        dyn(new SciButton(bx, by, 80, 14, () -> label, () -> ScientiaNetwork.CHANNEL.sendToServer(
                                new JournalEditPacket(JournalEditPacket.Op.HYPOTHESIS, id, opt))).accent(UiKit.VIOLET));
                        bx += 84;
                    }
                }
            }
            case GLOSSARY, FORMULAS -> {
                if (!selected.isEmpty()) {
                    String key = tab.name().toLowerCase(Locale.ROOT) + ":" + selected;
                    dyn(new SciButton(x0 + w - 70, y0 + 40, 62, 14, () -> ClientJournal.get().favorites().contains(key) ? "★ Favourite" : "☆ Favourite",
                            () -> ScientiaNetwork.CHANNEL.sendToServer(new JournalEditPacket(JournalEditPacket.Op.TOGGLE_FAVORITE, key, ""))).accent(UiKit.AMBER));
                }
                if (tab == Tab.FORMULAS && !selected.isEmpty()) {
                    String f = selected;
                    dyn(new SciButton(x0 + w - 136, y0 + 40, 62, 14, () -> "Open solver", () -> Minecraft.getInstance().setScreen(new CalculatorScreen(f))));
                }
            }
            case DATA -> {
                List<String> qs = new ArrayList<>();
                for (JournalData.MeasurementRecord m : ClientJournal.get().measurements()) if (!qs.contains(m.quantity())) qs.add(m.quantity());
                if (!qs.isEmpty()) {
                    if (!qs.contains(plotQuantity)) plotQuantity = qs.get(0);
                    dyn(new SciButton(x0 + w - 200, y0 + 21, 100, 14, () -> "Plot: " + plotQuantity, () -> {
                        int i = qs.indexOf(plotQuantity);
                        plotQuantity = qs.get((i + 1) % qs.size());
                    }));
                }
            }
            default -> {
            }
        }
    }

    private void dyn(SciButton b) {
        dynamicButtons.add(addRenderableWidget(b));
    }

    private void cycleCategory() {
        List<String> cats = new ArrayList<>(CATEGORY_COLORS.keySet());
        int i = cats.indexOf(categoryFilter);
        categoryFilter = i + 1 >= cats.size() ? "" : cats.get(i + 1);
    }

    private String query() {
        return search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).strip();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ================================================================== rendering

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        UiKit.frame(g, x0, y0, w, h);
        JournalData j = ClientJournal.get();
        String stats = j.research().size() + "/" + Sci.db().research().all().size() + " research · " + j.experiments().size() + "/"
                + Sci.db().experiments().size() + " experiments · " + j.elements().size() + " elements";
        g.drawString(font, stats, x0 + 8, y0 + 24, UiKit.muted(), false);
        switch (tab) {
            case RESEARCH -> research(g, mouseX, mouseY);
            case ELEMENTS -> elements(g, mouseX, mouseY, partial);
            case MATTER -> matter(g, mouseX, mouseY);
            case EXPERIMENTS -> experiments(g, mouseX, mouseY);
            case DATA -> data(g, mouseX, mouseY);
            case FORMULAS -> formulas(g, mouseX, mouseY);
            case GLOSSARY -> glossary(g, mouseX, mouseY);
            case NOTES -> notes(g);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    private int contentTop() {
        return y0 + 38;
    }

    // ------------------------------------------------------------------ research tree

    private static final int NODE_W = 64, NODE_H = 22, GRID_X = 76, GRID_Y = 30;

    private int[] nodePos(ResearchNode n) {
        return new int[]{(int) (x0 + 8 + panX + n.x() * GRID_X), (int) (contentTop() + panY + n.y() * GRID_Y)};
    }

    private void research(GuiGraphics g, int mx, int my) {
        int top = contentTop(), bottom = y0 + h - 70;
        UiKit.inset(g, x0 + 6, top, w - 12, bottom - top);
        g.enableScissor(x0 + 7, top + 1, x0 + w - 7, bottom - 1);
        JournalData j = ClientJournal.get();
        for (ResearchNode n : Sci.db().research().all()) {
            int[] p = nodePos(n);
            for (String pre : n.prerequisites()) {
                ResearchNode q = Sci.db().research().get(pre);
                if (q == null) continue;
                int[] pp = nodePos(q);
                int color = j.research().contains(pre) ? 0xFF6BD47A : 0xFF4A5560;
                GraphRenderer.line(g, pp[0] + NODE_W, pp[1] + NODE_H / 2, p[0], p[1] + NODE_H / 2, color);
            }
        }
        for (ResearchNode n : Sci.db().research().all()) {
            int[] p = nodePos(n);
            boolean done = j.research().contains(n.id());
            boolean available = !done && j.research().containsAll(n.prerequisites());
            int border = done ? UiKit.GREEN : available ? UiKit.AMBER : 0xFF4A5560;
            boolean hover = mx >= p[0] && mx < p[0] + NODE_W && my >= p[1] && my < p[1] + NODE_H;
            boolean match = !query().isEmpty() && n.title().toLowerCase(Locale.ROOT).contains(query());
            g.fill(p[0], p[1], p[0] + NODE_W, p[1] + NODE_H, hover || n.id().equals(selected) ? 0xFF2E3A46 : UiKit.PANEL);
            UiKit.outline(g, p[0], p[1], NODE_W, NODE_H, match ? 0xFFFFFFFF : border);
            g.fill(p[0], p[1], p[0] + 3, p[1] + NODE_H, branchColor(n.branch()));
            String mark = done ? "✔" : available ? "○" : "×";
            g.drawString(font, mark, p[0] + NODE_W - 9, p[1] + 2, border, false);
            g.pose().pushPose();
            g.pose().translate(p[0] + 6, p[1] + 4, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            int ly = 0;
            for (var line : font.split(Component.literal(n.title()), (int) ((NODE_W - 16) / 0.75f))) {
                if (ly > 14) break;
                g.drawString(font, line, 0, ly, done ? UiKit.text() : UiKit.muted(), false);
                ly += 10;
            }
            g.pose().popPose();
        }
        g.disableScissor();
        g.drawString(font, "Drag to pan · click a node", x0 + 10, bottom + 2, UiKit.muted(), false);
        ResearchNode sel = Sci.db().research().get(selected);
        int dy = bottom + 12;
        if (sel == null) {
            g.drawString(font, "✔ complete   ○ available   × locked (complete its prerequisites first)", x0 + 10, dy, UiKit.muted(), false);
            return;
        }
        g.drawString(font, sel.title() + "  —  " + sel.branch(), x0 + 10, dy, branchColor(sel.branch()), false);
        int yy = UiKit.wrap(g, font, sel.summary(), x0 + 10, dy + 10, w / 2 - 14, UiKit.text());
        int rx = x0 + w / 2 + 4, ry = dy;
        g.drawString(font, "Requires:", rx, ry, UiKit.muted(), false);
        ry += 10;
        for (String f : sel.requires()) {
            boolean ok = j.hasFact(f);
            g.drawString(font, (ok ? "✔ " : "• ") + Facts.describe(f), rx, ry, ok ? UiKit.GREEN : UiKit.text(), false);
            ry += 9;
        }
        if (sel.unlocks() != null && !sel.unlocks().isEmpty()) {
            g.drawString(font, "Unlocks: " + String.join(", ", sel.unlocks()).replace("feature:", ""), rx, ry + 1, UiKit.AMBER, false);
        }
    }

    private static int branchColor(String b) {
        return switch (b) {
            case "observation" -> 0xFF4FD8E8;
            case "chemistry" -> 0xFF6BD47A;
            case "electricity" -> 0xFFF0B040;
            case "thermal" -> 0xFFFF8A5B;
            case "optics" -> 0xFFB28CFF;
            case "physics" -> 0xFF6A9FD8;
            case "earth" -> 0xFFC8A070;
            case "biology" -> 0xFF8FD86A;
            case "mathematics" -> 0xFFE8E070;
            case "engineering" -> 0xFF9AA8B8;
            default -> 0xFF8B98A5;
        };
    }

    // ------------------------------------------------------------------ periodic table

    private int cell() {
        return Math.max(12, Math.min(22, (w - 16) / 18));
    }

    private int cellH() {
        return Math.max(11, Math.min(15, (h - 150) / 9));
    }

    private void elements(GuiGraphics g, int mx, int my, float partial) {
        int c = cell(), ch = cellH();
        int tx = x0 + (w - 18 * c) / 2, ty = contentTop();
        JournalData j = ClientJournal.get();
        String q = query();
        Element hovered = null;
        for (Element e : Sci.db().elements().all()) {
            int[] pos = ElementDatabase.layoutPosition(e);
            int ex = tx + (pos[0] - 1) * c, ey = ty + (pos[1] - 1) * ch + (pos[1] >= 8 ? 4 : 0);
            boolean dim = (!categoryFilter.isEmpty() && !e.category().equals(categoryFilter))
                    || (!q.isEmpty() && !(e.name().toLowerCase(Locale.ROOT).contains(q) || e.symbol().toLowerCase(Locale.ROOT).equals(q) || String.valueOf(e.z()).equals(q)));
            int color = CATEGORY_COLORS.getOrDefault(e.category(), 0xFF707880);
            if (dim) color = (color & 0x00FFFFFF) | 0x30000000;
            g.fill(ex, ey, ex + c - 1, ey + ch - 1, color);
            boolean hover = mx >= ex && mx < ex + c - 1 && my >= ey && my < ey + ch - 1;
            if (hover) hovered = e;
            if (e.symbol().equals(selected) || hover) UiKit.outline(g, ex - 1, ey - 1, c + 1, ch + 1, 0xFFFFFFFF);
            g.pose().pushPose();
            g.pose().translate(ex + (c - 1) / 2f, ey + (ch - 7) / 2f, 0);
            float s = c < 18 ? 0.7f : 0.85f;
            g.pose().scale(s, s, 1);
            g.drawString(font, e.symbol(), -font.width(e.symbol()) / 2, 0, dim ? 0x60FFFFFF : 0xFF101418, false);
            g.pose().popPose();
            if (j.elements().contains(e.symbol())) g.fill(ex + c - 4, ey + 1, ex + c - 2, ey + 3, 0xFFFFFFFF);
        }
        // legend (text + colour)
        int ly = ty + 9 * ch + 8;
        Element e = Sci.db().elements().bySymbol(selected);
        if (e == null) e = hovered;
        if (e == null) {
            int lx = x0 + 10;
            for (var en : CATEGORY_COLORS.entrySet()) {
                String name = en.getKey().replace('_', ' ');
                if (lx + font.width(name) + 12 > x0 + w) {
                    lx = x0 + 10;
                    ly += 10;
                }
                g.fill(lx, ly + 1, lx + 7, ly + 8, en.getValue());
                g.drawString(font, name, lx + 9, ly, UiKit.muted(), false);
                lx += font.width(name) + 16;
            }
            g.drawString(font, "White dot = discovered by you. Click an element for details.", x0 + 10, ly + 14, UiKit.muted(), false);
            return;
        }
        elementDetail(g, e, x0 + 8, ly, partial, j.elements().contains(e.symbol()));
    }

    private void elementDetail(GuiGraphics g, Element e, int x, int y, float partial, boolean discovered) {
        int col = CATEGORY_COLORS.getOrDefault(e.category(), 0xFF707880);
        g.fill(x, y, x + 34, y + 34, col);
        g.drawString(font, String.valueOf(e.z()), x + 2, y + 2, 0xFF101418, false);
        g.pose().pushPose();
        g.pose().translate(x + 17, y + 12, 0);
        g.pose().scale(1.6f, 1.6f, 1);
        g.drawString(font, e.symbol(), -font.width(e.symbol()) / 2, 0, 0xFF101418, false);
        g.pose().popPose();
        int tx = x + 40;
        g.drawString(font, e.name() + (discovered ? "  ✦ discovered" : ""), tx, y, UiKit.text(), false);
        String mass = e.atomicMass() == null ? "—" : (e.massIsMassNumber() ? "[" + e.atomicMass().intValue() + "]" : Sci.fmt(e.atomicMass(), 6)) + " u";
        g.drawString(font, e.category().replace('_', ' ') + " · " + (e.group() == null ? "f-block" : "group " + e.group()) + ", period " + e.period()
                + ", " + e.block() + "-block", tx, y + 10, UiKit.muted(), false);
        g.drawString(font, "Atomic mass " + mass + " · " + e.electronConfiguration(), tx, y + 20, UiKit.muted(), false);
        int colW = (w - 140) / 2;
        int ry = y + 36;
        List<String[]> props = new ArrayList<>();
        props.add(new String[]{"Phase (25 °C)", e.phase()});
        props.add(new String[]{"Density", e.density() == null ? "unknown" : Sci.fmt(e.density(), 4) + ("gas".equals(e.phase()) ? " g/L" : " g/cm³")});
        props.add(new String[]{"Melting point", e.meltingPointK() == null ? "unknown" : Sci.fmt(e.meltingPointK(), 5) + " K (" + Sci.fixed(e.meltingPointK() - 273.15, 1) + " °C)"});
        props.add(new String[]{"Boiling point", e.boilingPointK() == null ? "unknown" : Sci.fmt(e.boilingPointK(), 5) + " K (" + Sci.fixed(e.boilingPointK() - 273.15, 1) + " °C)"});
        props.add(new String[]{"Electronegativity", e.electronegativity() == null ? "—" : Sci.fixed(e.electronegativity(), 2) + " (Pauling)"});
        props.add(new String[]{"Oxidation states", e.oxidationStates() == null || e.oxidationStates().isEmpty() ? "—" : e.oxidationStates().toString()});
        props.add(new String[]{"Electrical", e.electricalConductivity()});
        props.add(new String[]{"Occurrence", e.occurrence().replace('_', ' ')});
        for (String[] p : props) {
            UiKit.kv(g, font, x, ry, colW, p[0], p[1], UiKit.text());
            ry += 10;
        }
        int dx = x + colW + 8;
        int maxY = y0 + h - 6;
        int dy = UiKit.wrap(g, font, e.description(), dx, y + 36, colW, UiKit.text());
        if (e.dataNote() != null && dy < maxY) UiKit.wrap(g, font, "Note: " + e.dataNote(), dx, dy + 2, colW, UiKit.AMBER);
        int ax = x0 + w - 56, ay = y + 60;
        float time = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + partial;
        AtomView.draw(g, font, ax, ay, 44, e, time * 0.08f, (float) (tilt));
        g.drawString(font, "Bohr model (simplified)", ax - 50, ay + 50, UiKit.muted(), false);
        g.drawString(font, "p⁺ " + e.z() + "  n⁰ ≈" + e.approximateNeutrons() + "  e⁻ " + e.z(), ax - 46, ay - 56, UiKit.muted(), false);
    }

    private double tilt = 0.6;

    // ------------------------------------------------------------------ matter (substances & materials)

    private void matter(GuiGraphics g, int mx, int my) {
        List<String[]> rows = new ArrayList<>();
        String q = query();
        if (!showMaterials) {
            for (Substance s : Sci.db().substances().all()) {
                if (!q.isEmpty() && !(s.name().toLowerCase(Locale.ROOT).contains(q) || s.formula().toLowerCase(Locale.ROOT).contains(q))) continue;
                rows.add(new String[]{s.id(), ChemicalFormula.pretty(s.formula()) + "  " + s.name()});
            }
        } else {
            for (MaterialEntry m : Sci.db().materials().values()) {
                if (!q.isEmpty() && !m.name().toLowerCase(Locale.ROOT).contains(q)) continue;
                rows.add(new String[]{m.id(), m.name()});
            }
        }
        list(g, rows, mx, my);
        int dx = x0 + 150, dy = contentTop() - detailScroll, dw = w - 158;
        g.enableScissor(dx, contentTop(), x0 + w - 4, y0 + h - 4);
        if (!showMaterials) {
            Substance s = Sci.db().substances().get(selected);
            if (s != null) {
                var subs = Sci.db().substances();
                dy = heading(g, ChemicalFormula.pretty(s.formula()) + " — " + s.name(), dx, dy);
                dy = line(g, s.kind().replace('_', ' ') + " · " + s.phase() + " at 25 °C · M = " + Sci.fixed(subs.molarMass(s.id()), 3) + " g/mol", dx, dy, dw, UiKit.muted());
                StringBuilder comp = new StringBuilder("Composition by mass: ");
                for (var en : subs.formula(s.id()).massFractions(Sci.db().elements()).entrySet()) {
                    comp.append(en.getKey()).append(' ').append(Sci.fixed(en.getValue() * 100, 1)).append("%  ");
                }
                dy = line(g, comp.toString(), dx, dy, dw, UiKit.text());
                if (s.density() != null) dy = line(g, "Density " + s.density() + " g/cm³", dx, dy, dw, UiKit.text());
                if (s.meltingPointK() != null) dy = line(g, "Melting point " + Sci.fixed(s.meltingPointK(), 1) + " K", dx, dy, dw, UiKit.text());
                if (s.boilingPointK() != null) dy = line(g, "Boiling point " + Sci.fixed(s.boilingPointK(), 1) + " K", dx, dy, dw, UiKit.text());
                if (s.specificHeat() != null) dy = line(g, "Specific heat " + s.specificHeat() + " J/(g·K)", dx, dy, dw, UiKit.text());
                dy = line(g, s.description().intuition(), dx, dy + 3, dw, UiKit.text());
                dy = line(g, s.description().science(), dx, dy + 2, dw, UiKit.text());
                dy = line(g, s.description().technical(), dx, dy + 2, dw, UiKit.muted());
                if (s.hazard() != null) dy = line(g, "⚠ " + s.hazard(), dx, dy + 2, dw, UiKit.RED);
                if (s.notes() != null) dy = line(g, s.notes(), dx, dy + 2, dw, UiKit.AMBER);
                line(g, "Sources: " + String.join("; ", s.sources()), dx, dy + 2, dw, UiKit.muted());
            }
        } else {
            MaterialEntry m = Sci.db().materials().get(selected);
            if (m != null) {
                dy = heading(g, m.name(), dx, dy);
                dy = line(g, "Classification: " + m.classification().replace('_', ' ') + " · item " + m.item(), dx, dy, dw, UiKit.muted());
                Double rho = MaterialAnalysis.referenceDensity(m, Sci.db());
                dy = line(g, "Reference density: " + (rho == null ? "not defined" : Sci.fmt(rho, 3) + " g/cm³"), dx, dy, dw, UiKit.text());
                if (m.composition() != null) {
                    StringBuilder sb = new StringBuilder("Modelled composition: ");
                    for (MaterialEntry.Component c : m.composition()) {
                        Substance s = Sci.db().substances().get(c.substance());
                        sb.append(s == null ? c.substance() : ChemicalFormula.pretty(s.formula())).append(' ').append(Sci.fixed(c.massFraction() * 100, 0)).append("%  ");
                    }
                    dy = line(g, sb.toString(), dx, dy, dw, UiKit.text());
                }
                dy = line(g, m.description(), dx, dy + 3, dw, UiKit.text());
                line(g, "Game abstraction: " + m.abstraction(), dx, dy + 3, dw, UiKit.AMBER);
            }
        }
        g.disableScissor();
    }

    private int heading(GuiGraphics g, String text, int x, int y) {
        g.drawString(font, text, x, y, UiKit.CYAN, false);
        return y + 12;
    }

    private int line(GuiGraphics g, String text, int x, int y, int w, int color) {
        if (text == null || text.isEmpty()) return y;
        return UiKit.wrap(g, font, text, x, y, w, color);
    }

    /** Left-hand selectable list. rows: {id, label}. */
    private void list(GuiGraphics g, List<String[]> rows, int mx, int my) {
        int lx = x0 + 6, ly = contentTop(), lw = 138, lh = y0 + h - 6 - ly;
        UiKit.inset(g, lx, ly, lw, lh);
        int rowsVisible = lh / 11;
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, rows.size() - rowsVisible)));
        for (int i = 0; i < rowsVisible && i + listScroll < rows.size(); i++) {
            String[] r = rows.get(i + listScroll);
            int ry = ly + 2 + i * 11;
            boolean hover = mx >= lx && mx < lx + lw && my >= ry && my < ry + 11;
            if (r[0].equals(selected)) g.fill(lx + 1, ry - 1, lx + lw - 1, ry + 10, 0xFF2E3A46);
            else if (hover) g.fill(lx + 1, ry - 1, lx + lw - 1, ry + 10, 0xFF222B34);
            String label = font.plainSubstrByWidth(r[1], lw - 6);
            g.drawString(font, label, lx + 3, ry, r[0].equals(selected) ? UiKit.CYAN : UiKit.text(), false);
        }
        listRows = rows;
    }

    private List<String[]> listRows = List.of();

    // ------------------------------------------------------------------ experiments

    private void experiments(GuiGraphics g, int mx, int my) {
        JournalData j = ClientJournal.get();
        List<String[]> rows = new ArrayList<>();
        String q = query();
        for (ExperimentDefinition e : Sci.db().experiments().values()) {
            if (!q.isEmpty() && !e.title().toLowerCase(Locale.ROOT).contains(q)) continue;
            rows.add(new String[]{e.id(), (j.experiments().contains(e.id()) ? "✔ " : "· ") + e.title()});
        }
        list(g, rows, mx, my);
        ExperimentDefinition e = Sci.db().experiments().get(selected);
        if (e == null) {
            UiKit.wrap(g, font, "Experiments are completed by doing them: the journal checks your real measurements and observations. "
                    + "Before some experiments you can record a hypothesis and compare it with your data afterwards.", x0 + 150, contentTop(), w - 158, UiKit.muted());
            return;
        }
        int dx = x0 + 150, dw = w - 158, top = contentTop();
        int bottomLimit = e.hypothesis() != null ? y0 + h - 40 : y0 + h - 4;
        g.enableScissor(dx, top, x0 + w - 4, bottomLimit);
        int dy = top - detailScroll;
        boolean done = j.experiments().contains(e.id());
        dy = heading(g, e.title() + (done ? "  ✔" : ""), dx, dy);
        dy = line(g, "Question: " + e.question(), dx, dy, dw, UiKit.text());
        dy = line(g, "Objective: " + e.objective(), dx, dy, dw, UiKit.text());
        dy = line(g, "Apparatus: " + String.join(", ", e.apparatus()), dx, dy, dw, UiKit.muted());
        dy = line(g, "Independent variable: " + e.independentVariable() + " · Dependent: " + e.dependentVariable(), dx, dy, dw, UiKit.muted());
        if (e.controlledVariables() != null && !e.controlledVariables().isEmpty()) dy = line(g, "Controlled: " + String.join(", ", e.controlledVariables()), dx, dy, dw, UiKit.muted());
        int n = 1;
        for (String step : e.procedure()) dy = line(g, (n++) + ". " + step, dx, dy, dw, UiKit.text());
        g.drawString(font, "Completion:", dx, dy + 2, UiKit.muted(), false);
        dy += 12;
        for (String f : e.completion()) {
            boolean ok = j.hasFact(f);
            dy = line(g, (ok ? "✔ " : "• ") + Facts.describe(f), dx, dy, dw, ok ? UiKit.GREEN : UiKit.text());
        }
        boolean scientist = isScientist();
        if (done || !scientist) dy = line(g, "Expected: " + e.expected(), dx, dy + 2, dw, UiKit.text());
        if (done) dy = line(g, "Interpretation: " + e.interpretation(), dx, dy + 2, dw, UiKit.GREEN);
        dy = line(g, "Limitations: " + e.limitations(), dx, dy + 2, dw, UiKit.AMBER);
        if (e.formulas() != null && !e.formulas().isEmpty()) line(g, "Formulas: " + String.join(", ", e.formulas()), dx, dy + 2, dw, UiKit.muted());
        g.disableScissor();
        if (e.hypothesis() != null) {
            JournalData.HypothesisRecord hr = j.hypotheses().get(e.id());
            int hy = y0 + h - 38;
            g.drawString(font, "Hypothesis: " + e.hypothesis().prompt(), dx, hy, UiKit.VIOLET, false);
            if (hr == null) {
                if (!done) g.drawString(font, "Choose →", x0 + 110, y0 + h - 19, UiKit.muted(), false);
                else g.drawString(font, "(no prediction was recorded)", dx, hy + 12, UiKit.muted(), false);
            } else if (hr.observed() == null) {
                g.drawString(font, "Your prediction: " + hr.predicted() + " — run the experiment to test it.", dx, hy + 10, UiKit.text(), false);
            } else {
                g.drawString(font, (Boolean.TRUE.equals(hr.supported()) ? "✔ Supported" : "✗ Not supported") + ": predicted " + hr.predicted()
                        + ", observed " + hr.observed(), dx, hy + 10, Boolean.TRUE.equals(hr.supported()) ? UiKit.GREEN : UiKit.AMBER, false);
            }
        }
    }

    private static boolean isScientist() {
        try {
            return com.scientia.config.ScientiaConfig.DIFFICULTY.get() == com.scientia.config.ScientiaConfig.Difficulty.SCIENTIST;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ data

    private void data(GuiGraphics g, int mx, int my) {
        JournalData j = ClientJournal.get();
        int top = contentTop();
        int gw = w / 2 - 10;
        DataSeries s = new DataSeries("game time", "min", plotQuantity, "", 64);
        String unit = "";
        long t0 = -1;
        for (JournalData.MeasurementRecord m : j.measurements()) {
            if (!m.quantity().equals(plotQuantity)) continue;
            if (t0 < 0) t0 = m.gameTime();
            s.add((m.gameTime() - t0) / 1200.0, m.value(), m.uncertainty());
            unit = m.unit();
        }
        DataSeries plotted = new DataSeries("game time", "min", plotQuantity, unit, 64);
        for (DataSeries.Point p : s.points()) plotted.add(p.x(), p.y(), p.yUncertainty());
        GraphRenderer.draw(g, font, x0 + 6, top, gw, h - 50, plotted, false, UiKit.CYAN);
        int lx = x0 + gw + 12, ly = top;
        g.drawString(font, "Measurement history (newest first)", lx, ly, UiKit.muted(), false);
        ly += 11;
        List<JournalData.MeasurementRecord> ms = j.measurements();
        int rows = (h - 60) / 9;
        int start = Math.max(0, Math.min(listScroll, Math.max(0, ms.size() - rows)));
        for (int i = 0; i < rows && ms.size() - 1 - i - start >= 0; i++) {
            JournalData.MeasurementRecord m = ms.get(ms.size() - 1 - i - start);
            String val = Sci.fmt(m.value(), 5) + (m.uncertainty() > 0 ? " ± " + Sci.fmt(m.uncertainty(), 2) : "") + " " + m.unit();
            String text = m.quantity() + " · " + m.subject() + ": " + val;
            g.drawString(font, font.plainSubstrByWidth(text, w - gw - 20), lx, ly, UiKit.text(), false);
            ly += 9;
        }
        if (ms.isEmpty()) g.drawString(font, "No measurements yet — use the balance, cylinder or instruments.", lx, ly, UiKit.muted(), false);
    }

    // ------------------------------------------------------------------ formulas & glossary & notes

    private void formulas(GuiGraphics g, int mx, int my) {
        List<String[]> rows = new ArrayList<>();
        String q = query();
        JournalData j = ClientJournal.get();
        for (FormulaEntry f : Sci.db().formulas().values()) {
            if (!q.isEmpty() && !(f.name().toLowerCase(Locale.ROOT).contains(q) || f.display().toLowerCase(Locale.ROOT).contains(q))) continue;
            rows.add(new String[]{f.id(), (j.favorites().contains("formulas:" + f.id()) ? "★ " : "") + f.name()});
        }
        list(g, rows, mx, my);
        FormulaEntry f = Sci.db().formulas().get(selected);
        if (f == null) return;
        int dx = x0 + 150, dw = w - 158;
        g.enableScissor(dx, contentTop(), x0 + w - 4, y0 + h - 4);
        int dy = contentTop() - detailScroll;
        dy = heading(g, f.name(), dx, dy);
        g.pose().pushPose();
        g.pose().translate(dx, dy + 2, 0);
        g.pose().scale(1.4f, 1.4f, 1);
        g.drawString(font, f.display(), 0, 0, UiKit.text(), false);
        g.pose().popPose();
        dy += 20;
        for (FormulaEntry.Variable v : f.variables()) dy = line(g, v.symbol() + " — " + v.name() + " [" + v.unit() + "]", dx, dy, dw, UiKit.muted());
        dy = line(g, "Assumptions: " + f.assumptions(), dx, dy + 3, dw, UiKit.text());
        dy = line(g, "Worked example: " + f.example(), dx, dy + 3, dw, UiKit.text());
        dy = line(g, "Common mistakes: " + f.mistakes(), dx, dy + 3, dw, UiKit.AMBER);
        if (f.experiment() != null) dy = line(g, "Try it: experiment \"" + f.experiment() + "\"", dx, dy + 3, dw, UiKit.GREEN);
        if (f.related() != null && !f.related().isEmpty()) line(g, "Related: " + String.join(", ", f.related()), dx, dy + 3, dw, UiKit.muted());
        g.disableScissor();
    }

    private void glossary(GuiGraphics g, int mx, int my) {
        List<String[]> rows = new ArrayList<>();
        String q = query();
        JournalData j = ClientJournal.get();
        for (GlossaryEntry e : Sci.db().glossary()) {
            if (!q.isEmpty() && !(e.term().toLowerCase(Locale.ROOT).contains(q) || e.definition().toLowerCase(Locale.ROOT).contains(q))) continue;
            rows.add(new String[]{e.term(), (j.favorites().contains("glossary:" + e.term()) ? "★ " : "") + e.term()});
        }
        rows.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
        list(g, rows, mx, my);
        for (GlossaryEntry e : Sci.db().glossary()) {
            if (!e.term().equals(selected)) continue;
            int dy = heading(g, e.term(), x0 + 150, contentTop());
            dy = line(g, "(" + e.category() + ")", x0 + 150, dy, w - 158, UiKit.muted());
            line(g, e.definition(), x0 + 150, dy + 4, w - 158, UiKit.text());
        }
    }

    private void notes(GuiGraphics g) {
        JournalData j = ClientJournal.get();
        int dy = contentTop();
        g.drawString(font, "Your observations (" + j.notes().size() + "/" + JournalData.MAX_NOTES + ")", x0 + 8, dy, UiKit.muted(), false);
        dy += 12;
        for (int i = j.notes().size() - 1; i >= 0 && dy < y0 + h - 34; i--) {
            dy = UiKit.wrap(g, font, "• " + j.notes().get(i), x0 + 8, dy, w - 16, UiKit.text()) + 2;
        }
    }

    // ================================================================== input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (tab == Tab.RESEARCH) {
            for (ResearchNode n : Sci.db().research().all()) {
                int[] p = nodePos(n);
                if (mx >= p[0] && mx < p[0] + NODE_W && my >= p[1] && my < p[1] + NODE_H && my < y0 + h - 70) {
                    selected = n.id();
                    return true;
                }
            }
            return false;
        }
        if (tab == Tab.ELEMENTS) {
            int c = cell(), ch = cellH();
            int tx = x0 + (w - 18 * c) / 2, ty = contentTop();
            for (Element e : Sci.db().elements().all()) {
                int[] pos = ElementDatabase.layoutPosition(e);
                int ex = tx + (pos[0] - 1) * c, ey = ty + (pos[1] - 1) * ch + (pos[1] >= 8 ? 4 : 0);
                if (mx >= ex && mx < ex + c - 1 && my >= ey && my < ey + ch - 1) {
                    selected = e.symbol().equals(selected) ? "" : e.symbol();
                    return true;
                }
            }
            return false;
        }
        int lx = x0 + 6, ly = contentTop();
        if (mx >= lx && mx < lx + 138 && my >= ly) {
            int idx = (int) ((my - ly - 2) / 11) + listScroll;
            if (idx >= 0 && idx < listRows.size()) {
                selected = listRows.get(idx)[0];
                detailScroll = 0;
                rebuildDynamic();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (tab == Tab.RESEARCH && my > contentTop() && my < y0 + h - 70) {
            panX += dx;
            panY += dy;
            return true;
        }
        if (tab == Tab.ELEMENTS) {
            tilt = Math.max(0, Math.min(1.4, tilt + dy * 0.02));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < x0 + 146 || tab == Tab.DATA) listScroll = Math.max(0, listScroll - (int) Math.signum(delta) * 3);
        else detailScroll = Math.max(0, detailScroll - (int) Math.signum(delta) * 12);
        return true;
    }

    @Override
    public void tick() {
        search.tick();
        noteBox.tick();
        if (tab == Tab.EXPERIMENTS && ClientJournal.revision() != lastRevision) {
            lastRevision = ClientJournal.revision();
            rebuildDynamic();
        }
    }

    private int lastRevision = -1;
}
