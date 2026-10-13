package com.scientia.client.screen;

import com.scientia.core.data.FormulaEntry;
import com.scientia.core.math.Constants;
import com.scientia.core.math.ExpressionParser;
import com.scientia.core.math.MathException;
import com.scientia.core.math.SigFigs;
import com.scientia.core.units.Quantity;
import com.scientia.core.units.Unit;
import com.scientia.core.units.UnitException;
import com.scientia.core.units.Units;
import com.scientia.util.Sci;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scientific calculator with units, a unit converter and a formula solver. Expressions are evaluated
 * by the safe {@link ExpressionParser} — no player input is ever executed as code.
 */
@OnlyIn(Dist.CLIENT)
public class CalculatorScreen extends Screen {
    enum Mode {CALCULATOR, CONVERTER, SOLVER}

    private Mode mode = Mode.CALCULATOR;
    private int x0, y0, w, h;
    private EditBox input, convValue, convFrom, convTo;
    private ExpressionParser.AngleMode angle = ExpressionParser.AngleMode.RADIANS;
    private int sigFigs = 6;
    private final List<String[]> history = new ArrayList<>();
    private String result = "", error = "";
    private String formulaId;
    private String solveFor = "";
    private final Map<String, EditBox> varBoxes = new LinkedHashMap<>();
    private final List<SciButton> modeButtons = new ArrayList<>();

    public CalculatorScreen() {
        this(null);
    }

    public CalculatorScreen(String formulaId) {
        super(Component.translatable("item.scientia.calculator"));
        this.formulaId = formulaId;
        if (formulaId != null) mode = Mode.SOLVER;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        w = Math.min(width - 20, 340);
        h = Math.min(height - 20, 230);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        clearWidgets();
        varBoxes.clear();
        int bx = x0 + 6;
        for (Mode m : Mode.values()) {
            String label = switch (m) {
                case CALCULATOR -> "Calculator";
                case CONVERTER -> "Units";
                case SOLVER -> "Formula solver";
            };
            int bw = font.width(label) + 12;
            addRenderableWidget(new SciButton(bx, y0 + 4, bw, 14, () -> (mode == m ? "▸" : "") + label, () -> {
                mode = m;
                result = error = "";
                init();
            }));
            bx += bw + 2;
        }
        switch (mode) {
            case CALCULATOR -> initCalculator();
            case CONVERTER -> initConverter();
            case SOLVER -> initSolver();
        }
    }

    private void initCalculator() {
        input = new EditBox(font, x0 + 8, y0 + 26, w - 16, 14, Component.literal("Expression"));
        input.setMaxLength(256);
        input.setHint(Component.literal("e.g. 12 g / 3 mL in g/cm3   or   sqrt(2)*sin(pi/4)"));
        addRenderableWidget(input);
        setInitialFocus(input);
        addRenderableWidget(new SciButton(x0 + 8, y0 + 44, 60, 14, () -> "= Evaluate", this::evaluate).accent(UiKit.GREEN));
        addRenderableWidget(new SciButton(x0 + 72, y0 + 44, 52, 14, () -> angle == ExpressionParser.AngleMode.RADIANS ? "RAD" : "DEG",
                () -> angle = angle == ExpressionParser.AngleMode.RADIANS ? ExpressionParser.AngleMode.DEGREES : ExpressionParser.AngleMode.RADIANS));
        addRenderableWidget(new SciButton(x0 + 128, y0 + 44, 60, 14, () -> sigFigs + " sig. fig.", () -> sigFigs = sigFigs >= 10 ? 2 : sigFigs + 1));
        addRenderableWidget(new SciButton(x0 + 192, y0 + 44, 40, 14, () -> "Clear", () -> {
            history.clear();
            input.setValue("");
            result = error = "";
        }).accent(UiKit.RED));
    }

    private void evaluate() {
        String expr = input.getValue();
        try {
            ExpressionParser.Result r = new ExpressionParser(Map.of(), angle).evaluate(expr);
            result = r.format(sigFigs);
            error = "";
            history.add(0, new String[]{expr, result});
            while (history.size() > 8) history.remove(history.size() - 1);
        } catch (MathException e) {
            error = e.getMessage() + (e.position() >= 0 ? "  (at character " + (e.position() + 1) + ")" : "");
            result = "";
        }
    }

    private void initConverter() {
        convValue = box(x0 + 8, y0 + 30, 70, "1");
        convFrom = box(x0 + 84, y0 + 30, 70, "degC");
        convTo = box(x0 + 172, y0 + 30, 70, "K");
        addRenderableWidget(new SciButton(x0 + 248, y0 + 30, 70, 14, () -> "Convert", this::convert).accent(UiKit.GREEN));
    }

    private EditBox box(int x, int y, int bw, String value) {
        EditBox b = new EditBox(font, x, y, bw, 14, Component.literal(""));
        b.setMaxLength(64);
        b.setValue(value);
        addRenderableWidget(b);
        return b;
    }

    private void convert() {
        try {
            double v = ExpressionParser.evaluateNumber(convValue.getValue());
            Unit from = Units.require(convFrom.getValue().trim()), to = Units.require(convTo.getValue().trim());
            double out = Units.convert(v, from, to);
            result = SigFigs.format(v, sigFigs) + " " + from.symbol() + " = " + SigFigs.format(out, sigFigs) + " " + to.symbol();
            error = "";
        } catch (MathException | UnitException e) {
            error = e.getMessage();
            result = "";
        }
    }

    private void initSolver() {
        List<FormulaEntry> all = new ArrayList<>(Sci.db().formulas().values());
        if (formulaId == null || !Sci.db().formulas().containsKey(formulaId)) formulaId = all.get(0).id();
        FormulaEntry f = Sci.db().formulas().get(formulaId);
        addRenderableWidget(new SciButton(x0 + 8, y0 + 24, 150, 14, () -> "Formula: " + Sci.db().formulas().get(formulaId).name(), () -> {
            int i = 0;
            for (int k = 0; k < all.size(); k++) if (all.get(k).id().equals(formulaId)) i = k;
            formulaId = all.get((i + 1) % all.size()).id();
            solveFor = "";
            result = error = "";
            init();
        }));
        if (solveFor.isEmpty() || !f.forms().containsKey(solveFor)) solveFor = f.forms().keySet().iterator().next();
        List<String> targets = new ArrayList<>(f.forms().keySet());
        addRenderableWidget(new SciButton(x0 + 162, y0 + 24, 100, 14, () -> "Solve for: " + solveFor, () -> {
            solveFor = targets.get((targets.indexOf(solveFor) + 1) % targets.size());
            result = error = "";
            init();
        }));
        int y = y0 + 64;
        for (FormulaEntry.Variable v : f.variables()) {
            if (v.symbol().equals(solveFor)) continue;
            EditBox b = new EditBox(font, x0 + 120, y, 80, 12, Component.literal(v.symbol()));
            b.setMaxLength(32);
            addRenderableWidget(b);
            varBoxes.put(v.symbol(), b);
            y += 16;
        }
        addRenderableWidget(new SciButton(x0 + 210, y0 + 64, 60, 14, () -> "= Solve", this::solve).accent(UiKit.GREEN));
    }

    private void solve() {
        FormulaEntry f = Sci.db().formulas().get(formulaId);
        Map<String, Quantity> vars = new HashMap<>();
        try {
            for (FormulaEntry.Variable v : f.variables()) {
                EditBox b = varBoxes.get(v.symbol());
                if (b == null) continue;
                if (b.getValue().isBlank()) throw new MathException("Enter a value for " + v.symbol());
                double val = ExpressionParser.evaluateNumber(b.getValue());
                vars.put(v.symbol(), Quantity.of(val, Units.require(v.unit())));
            }
            Unit target = null;
            for (FormulaEntry.Variable v : f.variables()) if (v.symbol().equals(solveFor)) target = Units.require(v.unit());
            ExpressionParser.Result r = new ExpressionParser(vars, ExpressionParser.AngleMode.RADIANS).evaluate(f.forms().get(solveFor));
            double out = target == null ? r.quantity().si() : r.quantity().in(target);
            result = solveFor + " = " + SigFigs.format(out, sigFigs) + " " + (target == null ? "" : target.symbol());
            error = "";
        } catch (MathException | UnitException e) {
            error = e.getMessage();
            result = "";
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && mode == Mode.CALCULATOR && input != null && input.isFocused()) {
            evaluate();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        UiKit.frame(g, x0, y0, w, h);
        int y = y0 + 64;
        switch (mode) {
            case CALCULATOR -> {
                if (!result.isEmpty()) {
                    UiKit.inset(g, x0 + 8, y, w - 16, 22);
                    g.pose().pushPose();
                    g.pose().translate(x0 + 12, y + 5, 0);
                    g.pose().scale(1.5f, 1.5f, 1);
                    g.drawString(font, font.plainSubstrByWidth(result, (int) ((w - 24) / 1.5f)), 0, 0, 0xFF6CFFE8, false);
                    g.pose().popPose();
                }
                y += 28;
                for (String[] hst : history) {
                    if (y > y0 + h - 60) break;
                    g.drawString(font, font.plainSubstrByWidth(hst[0] + "  =  " + hst[1], w - 20), x0 + 10, y, UiKit.muted(), false);
                    y += 10;
                }
                int cy = y0 + h - 44;
                g.drawString(font, "Constants: pi e c g0 NA kB R qe F planck G eps0 atm0 sigma   ·   functions: sin cos tan asin acos atan", x0 + 8, cy, UiKit.muted(), false);
                g.drawString(font, "sqrt cbrt ln log log2 exp abs floor ceil round min max   ·   n!  x%   ·   units: 5 m + 20 cm, 25 degC in K", x0 + 8, cy + 10, UiKit.muted(), false);
                g.drawString(font, "Values: c=" + Constants.SPEED_OF_LIGHT + " m/s, R=" + SigFigs.format(Constants.GAS_CONSTANT, 10) + " J/(mol·K)", x0 + 8, cy + 20, UiKit.muted(), false);
            }
            case CONVERTER -> {
                g.drawString(font, "value", x0 + 8, y0 + 20, UiKit.muted(), false);
                g.drawString(font, "from unit", x0 + 84, y0 + 20, UiKit.muted(), false);
                g.drawString(font, "→", x0 + 160, y0 + 33, UiKit.text(), false);
                g.drawString(font, "to unit", x0 + 172, y0 + 20, UiKit.muted(), false);
                if (!result.isEmpty()) g.drawString(font, result, x0 + 8, y0 + 52, 0xFF6CFFE8, false);
                StringBuilder sb = new StringBuilder("Units: ");
                for (Unit u : Units.all()) sb.append(u.symbol()).append("  ");
                UiKit.wrap(g, font, sb.toString(), x0 + 8, y0 + 70, w - 16, UiKit.muted());
                UiKit.wrap(g, font, "Only quantities with the same dimension can be converted (e.g. J ↔ kWh, but never m ↔ kg).", x0 + 8, y0 + h - 30, w - 16, UiKit.AMBER);
            }
            case SOLVER -> {
                FormulaEntry f = Sci.db().formulas().get(formulaId);
                g.drawString(font, f.display(), x0 + 8, y0 + 44, UiKit.text(), false);
                int vy = y0 + 66;
                for (FormulaEntry.Variable v : f.variables()) {
                    if (v.symbol().equals(solveFor)) continue;
                    g.drawString(font, font.plainSubstrByWidth(v.symbol() + " (" + v.name() + ")", 100), x0 + 8, vy, UiKit.muted(), false);
                    g.drawString(font, v.unit(), x0 + 204, vy, UiKit.muted(), false);
                    vy += 16;
                }
                if (!result.isEmpty()) g.drawString(font, result, x0 + 210, y0 + 86, 0xFF6CFFE8, false);
                UiKit.wrap(g, font, "Assumptions: " + f.assumptions(), x0 + 8, Math.max(vy + 6, y0 + h - 50), w - 16, UiKit.AMBER);
            }
        }
        if (!error.isEmpty()) UiKit.wrap(g, font, "⚠ " + error, x0 + 8, y0 + h - 14 - (mode == Mode.SOLVER ? 0 : 0), w - 16, UiKit.RED);
        super.render(g, mouseX, mouseY, partial);
    }
}
