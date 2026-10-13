package com.scientia.client.screen;

import com.scientia.blockentity.BatteryBlockEntity;
import com.scientia.blockentity.CondenserBlockEntity;
import com.scientia.blockentity.DataRecorderBlockEntity;
import com.scientia.blockentity.ElectricMotorBlockEntity;
import com.scientia.blockentity.ElectrolysisStationBlockEntity;
import com.scientia.blockentity.GraduatedCylinderBlockEntity;
import com.scientia.blockentity.InsulatedKilnBlockEntity;
import com.scientia.blockentity.LabHeaterBlockEntity;
import com.scientia.blockentity.MachineBlockEntity;
import com.scientia.blockentity.MaterialAnalyzerBlockEntity;
import com.scientia.blockentity.MicroscopeBlockEntity;
import com.scientia.blockentity.PrecisionBalanceBlockEntity;
import com.scientia.blockentity.ReactionVesselBlockEntity;
import com.scientia.blockentity.SolarPanelBlockEntity;
import com.scientia.blockentity.ThermalGeneratorBlockEntity;
import com.scientia.client.ClientJournal;
import com.scientia.config.ScientiaConfig;
import com.scientia.core.chem.ChemicalFormula;
import com.scientia.core.chem.Indicator;
import com.scientia.core.chem.Mixture;
import com.scientia.core.chem.Reaction;
import com.scientia.core.chem.SubstanceDatabase;
import com.scientia.core.data.MaterialAnalysis;
import com.scientia.core.data.MaterialEntry;
import com.scientia.core.data.MicroscopeSample;
import com.scientia.core.data.SpectrumEntry;
import com.scientia.core.physics.Optics;
import com.scientia.menu.MachineMenu;
import com.scientia.network.MachineActionPacket;
import com.scientia.network.ScientiaNetwork;
import com.scientia.util.Liquids;
import com.scientia.util.Sci;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * One screen class for every machine. The panel shows the machine's actual synchronised state — nothing
 * on it is decorative — plus an explanation whose depth follows the difficulty setting.
 */
@OnlyIn(Dist.CLIENT)
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int PX = 40, PW = 176; // content column
    private int focus = 50;

    public MachineScreen(MachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = MachineMenu.WIDTH;
        imageHeight = MachineMenu.HEIGHT;
        inventoryLabelY = MachineMenu.INV_Y - 11;
        inventoryLabelX = MachineMenu.INV_X;
    }

    private MachineBlockEntity be() {
        return menu.machine();
    }

    private void send(int action, int arg) {
        ScientiaNetwork.CHANNEL.sendToServer(new MachineActionPacket(menu.pos(), action, arg));
    }

    private SciButton button(int rx, int ry, int w, String label, int action) {
        return addRenderableWidget(new SciButton(leftPos + rx, topPos + ry, w, 14, () -> label, () -> send(action, 0)));
    }

    private SciButton button(int rx, int ry, int w, java.util.function.Supplier<String> label, int action) {
        return addRenderableWidget(new SciButton(leftPos + rx, topPos + ry, w, 14, label, () -> send(action, 0)));
    }

    private String machineId() {
        var key = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(be().getType());
        return key == null ? "" : key.getPath();
    }

    @Override
    protected void init() {
        super.init();
        MachineBlockEntity be = be();
        if (be == null) return;
        int by = 122;
        if (be instanceof PrecisionBalanceBlockEntity) {
            button(PX, by, 84, "Record mass", PrecisionBalanceBlockEntity.ACTION_RECORD);
            button(PX + 92, by, 84, "Tare (zero)", PrecisionBalanceBlockEntity.ACTION_TARE);
        } else if (be instanceof GraduatedCylinderBlockEntity) {
            button(PX, by, 84, "Record volume", GraduatedCylinderBlockEntity.ACTION_RECORD);
            button(PX + 92, by, 84, "Pour out water", GraduatedCylinderBlockEntity.ACTION_EMPTY);
        } else if (be instanceof LabHeaterBlockEntity h) {
            button(PX, by, 42, () -> h.isOn() ? "ON" : "OFF", LabHeaterBlockEntity.ACTION_TOGGLE).accent(UiKit.AMBER);
            button(PX + 45, by, 42, () -> (int) h.powerW() + " W", LabHeaterBlockEntity.ACTION_POWER);
            button(PX + 90, by, 44, "Collect", LabHeaterBlockEntity.ACTION_COLLECT);
            button(PX + 137, by, 39, "Empty", LabHeaterBlockEntity.ACTION_EMPTY).accent(UiKit.RED);
        } else if (be instanceof ReactionVesselBlockEntity v) {
            button(PX, by, 50, () -> v.closed() ? "Unseal" : "Seal", ReactionVesselBlockEntity.ACTION_STOPPER).accent(UiKit.AMBER);
            button(PX + 53, by, 36, "Filter", ReactionVesselBlockEntity.ACTION_FILTER);
            button(PX + 92, by, 30, "m→J", ReactionVesselBlockEntity.ACTION_RECORD_MASS);
            button(PX + 125, by, 26, "P→J", ReactionVesselBlockEntity.ACTION_RECORD_PRESSURE);
            button(PX + 154, by, 22, "✕", ReactionVesselBlockEntity.ACTION_EMPTY).accent(UiKit.RED);
        } else if (be instanceof MaterialAnalyzerBlockEntity a) {
            button(PX, by, 84, () -> a.mode() == 0 ? "Mode: spectrum" : "Mode: conductivity", MaterialAnalyzerBlockEntity.ACTION_MODE);
            button(PX + 92, by, 84, "Analyse", MaterialAnalyzerBlockEntity.ACTION_ANALYZE).accent(UiKit.GREEN);
        } else if (be instanceof MicroscopeBlockEntity) {
            addRenderableWidget(new SciButton(leftPos + PX, topPos + by, 24, 14, () -> "−", () -> focus = Math.max(0, focus - 5)));
            addRenderableWidget(new SciButton(leftPos + PX + 28, topPos + by, 24, 14, () -> "+", () -> focus = Math.min(100, focus + 5)));
            SciButton observe = button(PX + 92, by, 84, "Record observation", MicroscopeBlockEntity.ACTION_OBSERVE);
            observe.accent(UiKit.GREEN);
        } else if (be instanceof DataRecorderBlockEntity r) {
            button(PX - 24, by, 40, () -> r.recording() ? "Stop" : "Start", DataRecorderBlockEntity.ACTION_TOGGLE).accent(UiKit.GREEN);
            button(PX + 19, by, 32, () -> r.intervalSeconds() + " s", DataRecorderBlockEntity.ACTION_INTERVAL);
            button(PX + 54, by, 36, "Next Q", DataRecorderBlockEntity.ACTION_QUANTITY);
            button(PX + 93, by, 30, "Fit", DataRecorderBlockEntity.ACTION_FIT).accent(UiKit.AMBER);
            button(PX + 126, by, 46, "Cooling k", DataRecorderBlockEntity.ACTION_COOLING);
            button(PX + 175, by, 37, "Clear", DataRecorderBlockEntity.ACTION_CLEAR).accent(UiKit.RED);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics g, float partial, int mouseX, int mouseY) {
        UiKit.frame(g, leftPos, topPos, imageWidth, imageHeight);
        UiKit.panel(g, leftPos + 4, topPos + 16, imageWidth - 8, 124);
        MachineBlockEntity be = be();
        if (be != null) for (MachineBlockEntity.SlotDef d : be.slots()) UiKit.slot(g, leftPos + d.x(), topPos + d.y(), d.output());
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) UiKit.slot(g, leftPos + MachineMenu.INV_X + col * 18, topPos + MachineMenu.INV_Y + row * 18, false);
        for (int col = 0; col < 9; col++) UiKit.slot(g, leftPos + MachineMenu.INV_X + col * 18, topPos + MachineMenu.INV_Y + 58, false);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        UiKit.header(g, font, title.getString(), 6, 4, UiKit.CYAN);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, UiKit.muted(), false);
        MachineBlockEntity be = be();
        if (be == null) return;
        if (be instanceof PrecisionBalanceBlockEntity b) balance(g, b);
        else if (be instanceof GraduatedCylinderBlockEntity c) cylinder(g, c);
        else if (be instanceof LabHeaterBlockEntity h) heater(g, h);
        else if (be instanceof CondenserBlockEntity c) condenser(g, c);
        else if (be instanceof ReactionVesselBlockEntity v) vessel(g, v);
        else if (be instanceof MaterialAnalyzerBlockEntity a) analyzer(g, a);
        else if (be instanceof MicroscopeBlockEntity m) microscope(g, m);
        else if (be instanceof DataRecorderBlockEntity r) recorder(g, r);
        else if (be instanceof InsulatedKilnBlockEntity k) kiln(g, k);
        else if (be instanceof BatteryBlockEntity b) battery(g, b);
        else if (be instanceof ThermalGeneratorBlockEntity t) generator(g, t);
        else if (be instanceof SolarPanelBlockEntity s) solar(g, s);
        else if (be instanceof ElectricMotorBlockEntity m) motor(g, m);
        else if (be instanceof ElectrolysisStationBlockEntity e) electrolysis(g, e);
    }

    private boolean kelvin() {
        try {
            return ScientiaConfig.TEMPERATURE_IN_KELVIN.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private void explanation(GuiGraphics g, int y, int maxY) {
        String text = Explanations.get(machineId());
        if (text.isEmpty()) return;
        var lines = font.split(net.minecraft.network.chat.FormattedText.of(text), PW);
        for (var l : lines) {
            if (y > maxY) break;
            g.drawString(font, l, PX, y, UiKit.muted(), false);
            y += 10;
        }
    }

    private void bigReadout(GuiGraphics g, int x, int y, int w, String value, String sub) {
        UiKit.inset(g, x, y, w, 30);
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, y + 5, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, value, -font.width(value) / 2, 0, 0xFF6CFFE8, false);
        g.pose().popPose();
        if (sub != null) g.drawString(font, sub, x + w - font.width(sub) - 3, y + 21, UiKit.muted(), false);
    }

    // ------------------------------------------------------------------ panels

    private void balance(GuiGraphics g, PrecisionBalanceBlockEntity b) {
        double r = b.reading();
        bigReadout(g, PX, 20, PW, Double.isNaN(r) ? "- - - - -" : Sci.fixed(r, 2) + " g", "± 0.01 g");
        MaterialEntry m = Sci.material(b.items().getStackInSlot(0));
        int y = 56;
        if (m != null) {
            UiKit.kv(g, font, PX, y, 70, "Sample", "", UiKit.text());
            g.drawString(font, m.name(), PX + 40, y, UiKit.text(), false);
            g.drawString(font, "Class: " + m.classification().replace('_', ' '), PX, y + 11, UiKit.muted(), false);
        } else if (!b.items().getStackInSlot(0).isEmpty() && Double.isNaN(r)) {
            UiKit.wrap(g, font, "This item has no defined mass in the science database.", PX, y, 74, UiKit.AMBER);
        } else {
            g.drawString(font, "Place a sample on the pan →", PX, y, UiKit.muted(), false);
        }
        if (b.tare() != 0) g.drawString(font, "Tared: −" + Sci.fixed(b.tare(), 2) + " g", PX, y + 22, UiKit.AMBER, false);
        explanation(g, 92, 112);
    }

    private void cylinder(GuiGraphics g, GraduatedCylinderBlockEntity c) {
        // Cylinder drawing with a scale 0–100 mL
        int cx = PX + 4, top = 22, bottom = 112;
        UiKit.inset(g, cx, top, 18, bottom - top);
        double frac = c.reading() / GraduatedCylinderBlockEntity.CAPACITY_ML;
        int lvl = (int) (bottom - 1 - (bottom - top - 2) * frac);
        if (c.waterMl() > 0) g.fill(cx + 1, lvl, cx + 17, bottom - 1, 0xC03F76E4);
        for (int ml = 0; ml <= 100; ml += 10) {
            int y = (int) (bottom - 1 - (bottom - top - 2) * ml / 100.0);
            g.fill(cx + 18, y, cx + (ml % 50 == 0 ? 26 : 22), y + 1, UiKit.border());
            if (ml % 50 == 0) g.drawString(font, String.valueOf(ml), cx + 28, y - 4, UiKit.muted(), false);
        }
        int x = PX + 52, w = PW - 52;
        if (c.waterMl() <= 0) {
            UiKit.wrap(g, font, "Empty. Right-click with a water bucket or a beaker of water to fill to 50.0 mL.", x, 24, 74, UiKit.AMBER);
        } else {
            UiKit.kv(g, font, x, 24, w, "Reading", Sci.fixed(c.reading(), 1) + " mL", UiKit.text());
            UiKit.kv(g, font, x, 36, w, "Water only", Sci.fixed(c.waterMl(), 1) + " mL", UiKit.muted());
            UiKit.kv(g, font, x, 48, w, "Displaced", Sci.fixed(c.displacedReading(), 1) + " ± 0.3 mL", UiKit.CYAN);
            if (c.floating()) UiKit.wrap(g, font, "⚠ The sample floats: it displaces only its own weight of water.", x, 62, 74, UiKit.AMBER);
            if (c.overflow()) UiKit.wrap(g, font, "⚠ Overflow — the sample is too large for this cylinder.", x, 62, 74, UiKit.RED);
            if (c.unknown()) UiKit.wrap(g, font, "No reference density for this item.", x, 62, 74, UiKit.AMBER);
        }
        explanation(g, 86, 112);
    }

    private void heater(GuiGraphics g, LabHeaterBlockEntity h) {
        Mixture m = h.mixture();
        double t = h.temperatureK();
        bigReadout(g, PX, 20, PW, m.isEmpty() ? "no sample" : Sci.fixed(t - 273.15, 1) + " °C", m.isEmpty() ? null : (kelvin() ? Sci.fixed(t, 1) + " K" : null));
        int y = 54;
        UiKit.status(g, font, PX, y, h.isHeating(), "heating " + (int) h.powerW() + " W", h.isOn() ? "no fuel" : "off");
        if (h.boiling()) g.drawString(font, "BOILING", PX + PW - font.width("BOILING"), y, UiKit.AMBER, false);
        y += 11;
        UiKit.kv(g, font, PX, y, PW, "Boiling point", Sci.fixed(h.boilingPointK() - 273.15, 2) + " °C", UiKit.text());
        y += 10;
        UiKit.kv(g, font, PX, y, PW, "Heat loss UA", Sci.fixed(h.lossCoefficient(), 3) + " W/K (" + h.jacketId().replace("minecraft:", "") + ")", UiKit.text());
        y += 10;
        SubstanceDatabase subs = Sci.db().substances();
        UiKit.kv(g, font, PX, y, PW, "Contents", m.isEmpty() ? "—" : Sci.fixed(m.massGrams(subs), 1) + " g, " + Sci.fixed(Liquids.volumeMl(m), 0) + " mL", UiKit.text());
        y += 10;
        y = contentsList(g, m, PX, y, 3);
        UiKit.gauge(g, font, PX, 104, 80, h.fuelFraction(), UiKit.AMBER, "Fuel", (int) (h.fuelFraction() * 100) + " %");
        g.drawString(font, "Jacket", PX - 23, 66, UiKit.muted(), false);
    }

    private int contentsList(GuiGraphics g, Mixture m, int x, int y, int max) {
        SubstanceDatabase subs = Sci.db().substances();
        int shown = 0;
        for (var e : m.amounts().entrySet()) {
            if (shown++ >= max) {
                g.drawString(font, "…", x, y, UiKit.muted(), false);
                return y + 9;
            }
            var s = subs.get(e.getKey());
            if (s == null) continue;
            String line = String.format(Locale.ROOT, "%s %.2f g", ChemicalFormula.pretty(s.formula()), e.getValue() * subs.molarMass(e.getKey()));
            g.drawString(font, line, x + 4, y, 0xFF9FD8E0, false);
            y += 9;
        }
        return y;
    }

    private void condenser(GuiGraphics g, CondenserBlockEntity c) {
        bigReadout(g, PX, 20, PW, Sci.fixed(c.distillateMl(), 1) + " mL", "distillate");
        UiKit.status(g, font, PX, 56, c.flowing(), "condensing", "idle");
        g.drawString(font, "Empty beaker in ↑, filled beaker out ↓", PX, 68, UiKit.muted(), false);
        explanation(g, 82, 112);
    }

    private void vessel(GuiGraphics g, ReactionVesselBlockEntity v) {
        Mixture m = v.mixture();
        SubstanceDatabase subs = Sci.db().substances();
        UiKit.inset(g, PX, 20, PW, 22);
        g.drawString(font, "m = " + Sci.fixed(v.massReading(), 2) + " g", PX + 4, 23, 0xFF6CFFE8, false);
        g.drawString(font, "T = " + Sci.fixed(v.temperatureK() - 273.15, 1) + " °C", PX + 92, 23, 0xFF6CFFE8, false);
        String p = v.closed() ? "P = " + Sci.fixed(v.pressurePa() / 1000, 1) + " kPa" : "open to air";
        g.drawString(font, p, PX + 4, 32, v.closed() ? UiKit.AMBER : UiKit.muted(), false);
        double ph = v.pH();
        boolean indicators = ClientJournal.get().research().contains("acids_bases");
        if (!Double.isNaN(ph) && indicators) {
            int col = 0xFF000000 | Indicator.UNIVERSAL.color(ph);
            g.fill(PX + 92, 32, PX + 99, 39, col);
            g.drawString(font, "pH " + Sci.fixed(ph, 1) + " (" + Indicator.UNIVERSAL.describe(ph) + ")", PX + 102, 32, UiKit.text(), false);
        }
        int y = contentsList(g, m, PX, 46, 4);
        if (m.isEmpty()) g.drawString(font, "Add reagents, beakers or gas canisters ←", PX, 46, UiKit.muted(), false);
        y = Math.max(y, 84);
        if (!v.recentReactions().isEmpty()) {
            Reaction r = Sci.db().reactions().get(v.recentReactions().get(0));
            if (r != null) {
                g.drawString(font, (v.reacting() ? "⚗ " : "✓ ") + r.name(), PX, y, v.reacting() ? UiKit.GREEN : UiKit.muted(), false);
                String eq = r.equation(subs);
                if (font.width(eq) > PW) {
                    g.pose().pushPose();
                    g.pose().translate(PX, y + 10, 0);
                    float s = PW / (float) font.width(eq);
                    g.pose().scale(s, s, 1);
                    g.drawString(font, eq, 0, 0, UiKit.text(), false);
                    g.pose().popPose();
                } else {
                    g.drawString(font, eq, PX, y + 10, UiKit.text(), false);
                }
                if (r.deltaHkJ() != null) g.drawString(font, "ΔH° = " + Sci.fixed(r.deltaHkJ(), 1) + " kJ", PX, y + 20, UiKit.muted(), false);
            }
        } else if (!m.isEmpty()) {
            g.drawString(font, "No reaction under these conditions.", PX, y, UiKit.muted(), false);
        }
    }

    private void analyzer(GuiGraphics g, MaterialAnalyzerBlockEntity a) {
        MaterialEntry m = Sci.material(a.items().getStackInSlot(0));
        g.drawString(font, m == null ? "Insert a sample ←" : m.name(), PX, 20, m == null ? UiKit.muted() : UiKit.text(), false);
        if (a.analysing()) UiKit.gauge(g, font, PX, 30, PW, a.progress(), UiKit.GREEN, "Analysing…", (int) (a.progress() * 100) + " %");
        if (a.mode() == MaterialAnalyzerBlockEntity.MODE_SPECTRUM) {
            int x = PX, y = 52, w = PW, h = 34;
            UiKit.inset(g, x, y, w, h);
            double lo = 200, hi = 900;
            // visible band background
            for (int i = 1; i < w - 1; i++) {
                double nm = lo + (hi - lo) * i / w;
                int c = Optics.wavelengthToRgb(nm);
                if (c != 0) g.fill(x + i, y + h - 4, x + i + 1, y + h - 1, 0xFF000000 | c);
            }
            for (String el : a.detected()) {
                SpectrumEntry sp = Sci.db().spectra().get(el);
                if (sp == null) continue;
                for (SpectrumEntry.Line l : sp.lines()) {
                    if (l.nm() < lo || l.nm() > hi) continue;
                    int px = x + (int) ((l.nm() - lo) / (hi - lo) * w);
                    int c = Optics.wavelengthToRgb(l.nm());
                    int col = c == 0 ? 0xFFB28CFF : 0xFF000000 | c;
                    int lh = (int) (4 + (h - 9) * l.intensity());
                    g.fill(px, y + h - 4 - lh, px + 1, y + h - 4, col);
                }
            }
            g.drawString(font, "200", x, y + h + 2, UiKit.muted(), false);
            g.drawString(font, "400", x + (int) ((400 - lo) / (hi - lo) * w) - 8, y + h + 2, UiKit.muted(), false);
            g.drawString(font, "700 nm", x + (int) ((700 - lo) / (hi - lo) * w) - 8, y + h + 2, UiKit.muted(), false);
            int ty = y + h + 13;
            if (!a.detected().isEmpty()) g.drawString(font, "Detected: " + String.join(", ", a.detected()), x, ty, UiKit.GREEN, false);
            if (!a.undetectable().isEmpty()) g.drawString(font, "No reference lines: " + String.join(", ", a.undetectable()), x, ty + 10, UiKit.muted(), false);
            g.drawString(font, "UV < 380 nm (violet bars)", x + 94, ty + 10 + (a.undetectable().isEmpty() ? -10 : 10) , UiKit.VIOLET, false);
        } else {
            double r = a.resistanceOhm();
            bigReadout(g, PX, 50, PW, Double.isNaN(r) ? "—" : Sci.si(r, "Ω"), "1 cm cube");
            if (m != null) {
                Double rho = MaterialAnalysis.referenceDensity(m, Sci.db());
                g.drawString(font, "Conductivity class: " + m.conductivity(), PX, 86, UiKit.muted(), false);
                if (rho != null) g.drawString(font, "Reference density " + Sci.fmt(rho, 3) + " g/cm³", PX, 96, UiKit.muted(), false);
            }
        }
    }

    private void microscope(GuiGraphics g, MicroscopeBlockEntity mb) {
        MicroscopeSample s = mb.sample();
        int x = PX, y = 20, size = 96;
        UiKit.inset(g, x, y, size, size);
        if (s == null) {
            UiKit.wrap(g, font, "Place a specimen (sand, salt, sugar, leaves, kelp, feather, iron ingot, bone meal) on the stage.", x + 102, y, 74, UiKit.muted());
            return;
        }
        ResourceLocation tex = ResourceLocation.tryParse(s.texture());
        int target = Math.abs(s.id().hashCode()) % 60 + 20;
        int blur = Math.min(6, Math.abs(focus - target) / 4);
        if (tex != null) {
            if (blur == 0) {
                g.blit(tex, x + 1, y + 1, 0, 0, size - 2, size - 2, size - 2, size - 2);
            } else {
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                g.setColor(1f, 1f, 1f, 0.35f);
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                    g.blit(tex, x + 1 + dx * blur, y + 1 + dy * blur, 0, 0, size - 2, size - 2, size - 2, size - 2);
                }
                g.setColor(1f, 1f, 1f, 1f);
            }
        }
        // scale bar: 1/4 of the field of view
        double barUm = s.fieldOfViewUm() / 4;
        g.fill(x + 6, y + size - 8, x + 6 + (size - 2) / 4, y + size - 6, 0xFFFFFFFF);
        g.drawString(font, Sci.fmt(barUm, 2) + " µm", x + 6, y + size - 18, 0xFFFFFFFF, true);
        int tx = x + 102;
        g.drawString(font, s.title(), tx, y, UiKit.text(), false);
        g.drawString(font, s.magnification() + "×   focus " + focus + (blur == 0 ? " ✓" : ""), tx, y + 11, blur == 0 ? UiKit.GREEN : UiKit.AMBER, false);
        UiKit.wrap(g, font, s.description(), tx, y + 24, 74, UiKit.muted());
    }

    private void recorder(GuiGraphics g, DataRecorderBlockEntity r) {
        String src = r.sourceName().isEmpty() ? "no instrument attached" : Component.translatable(r.sourceName()).getString();
        g.drawString(font, (r.recording() ? "● REC  " : "○ ") + src + "  (" + r.series().size() + " pts)", 10, 18, r.recording() ? UiKit.RED : UiKit.muted(), false);
        GraphRenderer.draw(g, font, 10, 28, 236, 92, r.series(), ClientJournal.get().research().contains("graphing") || r.series().size() >= 5, UiKit.CYAN);
    }

    private void kiln(GuiGraphics g, InsulatedKilnBlockEntity k) {
        double t = k.temperatureK();
        bigReadout(g, PX, 20, PW, Sci.fixed(t, 0) + " K", Sci.fixed(t - 273.15, 0) + " °C");
        int y = 54;
        UiKit.gauge(g, font, PX, y, PW, t / 1600, t >= InsulatedKilnBlockEntity.PROCESS_K ? UiKit.AMBER : UiKit.CYAN, "Chamber (≥1100 K to process)",
                t >= InsulatedKilnBlockEntity.PROCESS_K ? "hot enough" : "heating");
        int mark = PX + 1 + (int) ((PW - 2) * InsulatedKilnBlockEntity.PROCESS_K / 1600);
        g.fill(mark, y + 9, mark + 1, y + 17, 0xFFFFFFFF);
        y += 22;
        UiKit.gauge(g, font, PX, y, 84, k.progress(), UiKit.GREEN, "Charge", (int) (k.progress() * 100) + " %");
        UiKit.gauge(g, font, PX + 92, y, 84, k.fuelFraction(), UiKit.AMBER, "Fuel", (int) (k.fuelFraction() * 100) + " %");
        y += 22;
        UiKit.kv(g, font, PX, y, PW, "UA", Sci.fixed(k.lossCoefficient(), 2) + " W/K   loss " + Sci.si(k.heatLossW(), "W"), UiKit.text());
        double fpi = k.fuelPerItem();
        UiKit.kv(g, font, PX, y + 10, PW, "Fuel per item", Double.isNaN(fpi) ? "—" : Sci.fixed(fpi / 1000, 0) + " kJ", UiKit.text());
        g.drawString(font, "Insulation", 186, 20, UiKit.muted(), false);
    }

    private void electrical(GuiGraphics g, double v, double i, int y) {
        UiKit.inset(g, PX, y, PW, 24);
        g.drawString(font, "V " + Sci.si(v, "V"), PX + 4, y + 3, 0xFF6CFFE8, false);
        g.drawString(font, "I " + Sci.si(i, "A"), PX + 64, y + 3, 0xFF6CFFE8, false);
        g.drawString(font, "P " + Sci.si(v * i, "W"), PX + 124, y + 3, 0xFF6CFFE8, false);
        g.drawString(font, "(at the terminals)", PX + 4, y + 13, UiKit.muted(), false);
    }

    private void battery(GuiGraphics g, BatteryBlockEntity b) {
        electrical(g, b.lastVoltage(), b.lastCurrent(), 20);
        UiKit.gauge(g, font, PX, 50, PW, b.stateOfCharge(), b.stateOfCharge() > 0.2 ? UiKit.GREEN : UiKit.RED, "State of charge",
                Sci.fixed(b.stateOfCharge() * 100, 1) + " %");
        UiKit.kv(g, font, PX, 72, PW, "EMF", Sci.fixed(b.emf(), 2) + " V   r = 0.05 Ω", UiKit.text());
        if (b.lastCurrent() < -1e-4) g.drawString(font, "Charging", PX, 82, UiKit.GREEN, false);
        explanation(g, 94, 136);
    }

    private void generator(GuiGraphics g, ThermalGeneratorBlockEntity t) {
        electrical(g, t.lastVoltage(), t.lastCurrent(), 20);
        UiKit.gauge(g, font, PX, 50, PW, t.fuelFraction(), UiKit.AMBER, "Fuel", (int) (t.fuelFraction() * 100) + " %");
        UiKit.kv(g, font, PX, 72, PW, "Rotor", t.rotorSpeed() > 0 ? (int) (t.rotorSpeed() * 3000) + " rpm" : "stopped", UiKit.text());
        UiKit.kv(g, font, PX, 82, PW, "Fuel power", Sci.si(Math.max(0, t.lastVoltage() * t.lastCurrent()) / 0.25 + (t.rotorSpeed() > 0 ? 50 : 0), "W"), UiKit.text());
        explanation(g, 96, 136);
    }

    private void solar(GuiGraphics g, SolarPanelBlockEntity s) {
        electrical(g, s.lastVoltage(), s.lastCurrent(), 20);
        UiKit.gauge(g, font, PX, 50, PW, s.irradiance(), UiKit.AMBER, "Irradiance (of full sun)", (int) (s.irradiance() * 100) + " %");
        UiKit.kv(g, font, PX, 72, PW, "Max power now", Sci.si(s.emf() * s.emf() / (4 * s.internalResistance()), "W"), UiKit.text());
        explanation(g, 86, 136);
    }

    private void motor(GuiGraphics g, ElectricMotorBlockEntity m) {
        var r = m.readings();
        electrical(g, r.get(1).value(), r.get(2).value(), 20);
        UiKit.kv(g, font, PX, 50, PW, "Mechanical power", Sci.si(m.mechanicalPower(), "W"), UiKit.text());
        UiKit.kv(g, font, PX, 60, PW, "Speed", (int) m.rpm() + " rpm", UiKit.text());
        UiKit.kv(g, font, PX, 70, PW, "Comparator signal", String.valueOf(m.comparatorLevel()), UiKit.text());
        explanation(g, 86, 136);
    }

    private void electrolysis(GuiGraphics g, ElectrolysisStationBlockEntity e) {
        electrical(g, e.voltage(), e.current(), 20);
        int y = contentsList(g, e.mixture(), PX, 48, 3);
        y = Math.max(y, 78);
        UiKit.kv(g, font, PX, y, PW, "Charge passed", Sci.fixed(e.chargeC(), 1) + " C", UiKit.text());
        UiKit.kv(g, font, PX, y + 10, PW, "Q/(2F) → H₂", Sci.fmt(e.faradayHydrogen(), 3) + " mol", UiKit.muted());
        StringBuilder gas = new StringBuilder();
        for (String id : ElectrolysisStationBlockEntity.GASES) {
            double n = e.gas().get(id);
            if (n > 0) gas.append(ChemicalFormula.pretty(Sci.db().substances().get(id).formula())).append(' ').append(Sci.fmt(n, 3)).append(" mol  ");
        }
        g.drawString(font, gas.length() == 0 ? "No gas collected" : gas.toString(), PX, y + 22, UiKit.GREEN, false);
        if (e.current() < 1e-6) g.drawString(font, "Needs > " + ElectrolysisStationBlockEntity.COUNTER_EMF + " V across (+ at front)", PX, y + 32, UiKit.muted(), false);
    }
}
