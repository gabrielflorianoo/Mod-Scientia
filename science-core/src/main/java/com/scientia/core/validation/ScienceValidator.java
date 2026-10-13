package com.scientia.core.validation;

import com.scientia.core.chem.Element;
import com.scientia.core.chem.ElementDatabase;
import com.scientia.core.chem.Reaction;
import com.scientia.core.chem.ReactionValidator;
import com.scientia.core.chem.Substance;
import com.scientia.core.data.ExperimentDefinition;
import com.scientia.core.data.FormulaEntry;
import com.scientia.core.data.GlossaryEntry;
import com.scientia.core.data.MaterialEntry;
import com.scientia.core.data.MicroscopeSample;
import com.scientia.core.data.ScienceDatabase;
import com.scientia.core.data.SpectrumEntry;
import com.scientia.core.data.WorkbenchRecipe;
import com.scientia.core.math.ExpressionParser;
import com.scientia.core.math.MathException;
import com.scientia.core.research.ResearchNode;
import com.scientia.core.units.Quantity;
import com.scientia.core.units.Unit;
import com.scientia.core.units.Units;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Central validation of every science data file. Returns human-readable error messages (empty = valid). */
public final class ScienceValidator {
    public static final Set<String> FACT_PREFIXES = Set.of("measure", "derived", "identify", "element", "reaction",
            "experiment", "research", "circuit", "observe", "craft", "analyze", "hypothesis", "phase", "separate");
    public static final Set<String> HYPOTHESIS_EVALUATORS = Set.of("mass_change", "boiling_temperature",
            "ph_change", "current_change", "insulation_loss", "density_order");
    public static final Set<String> CLASSIFICATIONS = Set.of("raw_ore", "mineral", "refined", "element", "compound",
            "mixture", "alloy", "manufactured", "biological", "fictional");

    private ScienceValidator() {
    }

    public static List<String> validate(ScienceDatabase db) {
        List<String> e = new ArrayList<>();
        elements(db.elements(), e);
        substances(db, e);
        for (Reaction r : db.reactions().values()) {
            e.addAll(ReactionValidator.validate(r, db.substances()));
            if (ReactionValidator.validate(r, db.substances()).isEmpty()) {
                double dm = ReactionValidator.massBalance(r, db.substances());
                if (Math.abs(dm) > 1e-6) e.add("reaction " + r.id() + ": mass not conserved (" + dm + " g/mol)");
            }
        }
        materials(db, e);
        formulas(db, e);
        experiments(db, e);
        research(db, e);
        for (SpectrumEntry s : db.spectra().values()) {
            if (!db.elements().isElement(s.symbol())) e.add("spectrum: unknown element " + s.symbol());
            for (SpectrumEntry.Line l : s.lines()) {
                if (l.nm() < 100 || l.nm() > 1100) e.add("spectrum " + s.symbol() + ": implausible line " + l.nm() + " nm");
            }
        }
        for (MicroscopeSample m : db.microscope().values()) {
            if (blank(m.description()) || blank(m.texture())) e.add("microscope " + m.id() + ": missing description or texture");
            if (m.magnification() <= 0 || m.fieldOfViewUm() <= 0) e.add("microscope " + m.id() + ": invalid optics values");
        }
        Set<String> terms = new HashSet<>();
        for (GlossaryEntry g : db.glossary()) {
            if (blank(g.term()) || blank(g.definition())) e.add("glossary: entry with missing term or definition");
            else if (!terms.add(g.term().toLowerCase())) e.add("glossary: duplicate term " + g.term());
        }
        return e;
    }

    private static void elements(ElementDatabase db, List<String> e) {
        if (db.size() != 118) e.add("elements: expected 118 elements, found " + db.size());
        Set<String> positions = new HashSet<>();
        for (int z = 1; z <= 118; z++) {
            Element el = db.byNumber(z);
            if (el == null) {
                e.add("elements: missing Z=" + z);
                continue;
            }
            if (blank(el.symbol()) || blank(el.name())) e.add("element " + z + ": missing symbol or name");
            if (blank(el.description())) e.add("element " + el.symbol() + ": missing description");
            if (el.atomicMass() != null && el.atomicMass() < z) e.add("element " + el.symbol() + ": atomic mass below Z");
            if (el.meltingPointK() != null && el.boilingPointK() != null && el.boilingPointK() < el.meltingPointK()
                    && !"Sublimes".equals(el.dataNote())) {
                e.add("element " + el.symbol() + ": boiling point below melting point");
            }
            if (el.shells() != null) {
                int sum = el.shells().stream().mapToInt(Integer::intValue).sum();
                if (sum != z) e.add("element " + el.symbol() + ": electron shells sum to " + sum + " not " + z);
            }
            int[] p = ElementDatabase.layoutPosition(el);
            if (!positions.add(p[0] + "," + p[1])) e.add("element " + el.symbol() + ": duplicate table position");
        }
    }

    private static void substances(ScienceDatabase db, List<String> e) {
        for (Substance s : db.substances().all()) {
            if (s.description() == null || blank(s.description().intuition()) || blank(s.description().science())) {
                e.add("substance " + s.id() + ": missing educational description");
            }
            if (!Set.of("solid", "liquid", "gas").contains(s.phase())) e.add("substance " + s.id() + ": invalid phase " + s.phase());
            if (s.sources() == null || s.sources().isEmpty()) e.add("substance " + s.id() + ": missing source notes");
            if (s.density() != null && s.density() <= 0) e.add("substance " + s.id() + ": non-positive density");
        }
    }

    private static void materials(ScienceDatabase db, List<String> e) {
        Set<String> micro = db.microscope().keySet();
        for (MaterialEntry m : db.materials().values()) {
            if (m.item() == null || !m.item().matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) e.add("material " + m.id() + ": invalid item id " + m.item());
            if (!CLASSIFICATIONS.contains(m.classification())) e.add("material " + m.id() + ": unknown classification " + m.classification());
            if (blank(m.abstraction())) e.add("material " + m.id() + ": missing gameplay abstraction note");
            if (m.sampleMassG() <= 0) e.add("material " + m.id() + ": sample mass must be positive");
            if (m.microscope() != null && !micro.contains(m.microscope())) e.add("material " + m.id() + ": unknown microscope sample " + m.microscope());
            if (!"fictional".equals(m.classification())) {
                if (m.composition() == null || m.composition().isEmpty()) {
                    e.add("material " + m.id() + ": non-fictional material needs a composition");
                } else {
                    double sum = 0;
                    for (MaterialEntry.Component c : m.composition()) {
                        if (!db.substances().has(c.substance())) e.add("material " + m.id() + ": unknown substance " + c.substance());
                        sum += c.massFraction();
                    }
                    if (Math.abs(sum - 1) > 1e-3) e.add("material " + m.id() + ": mass fractions sum to " + sum);
                }
            }
        }
    }

    /** Checks that every rearranged form is dimensionally consistent with the variable it computes. */
    private static void formulas(ScienceDatabase db, List<String> e) {
        for (FormulaEntry f : db.formulas().values()) {
            Map<String, Quantity> vars = new HashMap<>();
            Map<String, Unit> units = new HashMap<>();
            int k = 0;
            for (FormulaEntry.Variable v : f.variables()) {
                k++;
                Unit u = Units.get(v.unit());
                if (u == null) {
                    e.add("formula " + f.id() + ": unknown unit '" + v.unit() + "' for " + v.symbol());
                    continue;
                }
                units.put(v.symbol(), u);
                // Distinct test values avoid accidental cancellations (e.g. 1/f - 1/do = 0)
                vars.put(v.symbol(), new Quantity((u.isAffine() ? 300 : 0.3 + 0.41 * k) * u.factor(), u.dimension()));
            }
            if (blank(f.assumptions()) || blank(f.example()) || blank(f.mistakes())) e.add("formula " + f.id() + ": missing assumptions/example/mistakes");
            if (f.forms() == null || f.forms().isEmpty()) {
                e.add("formula " + f.id() + ": no forms");
                continue;
            }
            for (var form : f.forms().entrySet()) {
                Unit target = units.get(form.getKey());
                if (target == null) {
                    e.add("formula " + f.id() + ": form for undeclared variable " + form.getKey());
                    continue;
                }
                try {
                    Quantity q = new ExpressionParser(vars, ExpressionParser.AngleMode.RADIANS).evaluate(form.getValue()).quantity();
                    if (!q.dimension().equals(target.dimension())) {
                        e.add("formula " + f.id() + ": form for " + form.getKey() + " has dimension " + q.dimension()
                                + " but " + target.symbol() + " is " + target.dimension());
                    }
                } catch (MathException ex) {
                    e.add("formula " + f.id() + ": form for " + form.getKey() + " failed: " + ex.getMessage());
                }
            }
        }
    }

    private static void experiments(ScienceDatabase db, List<String> e) {
        for (ExperimentDefinition x : db.experiments().values()) {
            if (blank(x.objective()) || blank(x.question()) || blank(x.interpretation()) || blank(x.limitations())) {
                e.add("experiment " + x.id() + ": missing objective/question/interpretation/limitations");
            }
            if (x.procedure() == null || x.procedure().isEmpty()) e.add("experiment " + x.id() + ": missing procedure");
            if (x.completion() == null || x.completion().isEmpty()) e.add("experiment " + x.id() + ": missing completion conditions");
            else for (String fact : x.completion()) checkFact("experiment " + x.id(), fact, e);
            if (x.formulas() != null) for (String f : x.formulas()) {
                if (!db.formulas().containsKey(f)) e.add("experiment " + x.id() + ": unknown formula " + f);
            }
            if (x.hypothesis() != null && !HYPOTHESIS_EVALUATORS.contains(x.hypothesis().evaluator())) {
                e.add("experiment " + x.id() + ": unknown hypothesis evaluator " + x.hypothesis().evaluator());
            }
        }
    }

    private static void research(ScienceDatabase db, List<String> e) {
        e.addAll(db.research().validate());
        Set<String> recipeIds = db.workbenchRecipes().keySet();
        for (ResearchNode n : db.research().all()) {
            for (String fact : n.requires()) checkFact("research " + n.id(), fact, e);
            if (n.unlocks() != null) for (String u : n.unlocks()) {
                if (!u.startsWith("feature:") && !recipeIds.contains(u)) e.add("research " + n.id() + ": unlocks unknown recipe " + u);
            }
        }
        for (WorkbenchRecipe w : db.workbenchRecipes().values()) {
            if (w.research() != null && db.research().get(w.research()) == null) e.add("workbench recipe " + w.id() + ": unknown research " + w.research());
            if (w.ingredients() == null || w.ingredients().isEmpty()) e.add("workbench recipe " + w.id() + ": no ingredients");
            if (w.result() == null || w.result().count() <= 0) e.add("workbench recipe " + w.id() + ": invalid result");
        }
    }

    private static void checkFact(String owner, String fact, List<String> e) {
        int i = fact.indexOf(':');
        if (i <= 0 || !FACT_PREFIXES.contains(fact.substring(0, i))) e.add(owner + ": malformed fact '" + fact + "'");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
