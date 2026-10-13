package com.scientia.core;

import com.scientia.core.chem.AcidBaseSolver;
import com.scientia.core.chem.ChemicalFormula;
import com.scientia.core.chem.Indicator;
import com.scientia.core.chem.Mixture;
import com.scientia.core.chem.Reaction;
import com.scientia.core.chem.ReactionEngine;
import com.scientia.core.chem.ReactionValidator;
import com.scientia.core.chem.SubstanceDatabase;
import com.scientia.core.data.ScienceDatabase;
import com.scientia.core.math.Constants;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ChemistryTest {
    private final ScienceDatabase db = TestData.db();
    private final SubstanceDatabase subs = db.substances();

    @Test
    void formulaParsing() {
        assertEquals(Map.of("Ca", 1, "O", 2, "H", 2), ChemicalFormula.parse("Ca(OH)2").composition());
        assertEquals(Map.of("Ca", 1, "C", 4, "H", 6, "O", 4), ChemicalFormula.parse("Ca(CH3COO)2").composition());
        assertEquals(Map.of("Cu", 1, "S", 1, "O", 9, "H", 10), ChemicalFormula.parse("CuSO4·5H2O").composition());
        assertEquals(Map.of("Al", 2, "Si", 2, "O", 9, "H", 4), ChemicalFormula.parse("Al2Si2O5(OH)4").composition());
        assertEquals(Map.of("Na", 1), ChemicalFormula.parse("Na+").composition());
        assertThrows(IllegalArgumentException.class, () -> ChemicalFormula.parse("Ca(OH2"));
        assertThrows(IllegalArgumentException.class, () -> ChemicalFormula.parse("Xx2", db.elements()::isElement));
        assertThrows(IllegalArgumentException.class, () -> ChemicalFormula.parse("h2o"));
        assertEquals("Ca(OH)₂", ChemicalFormula.pretty("Ca(OH)2"));
    }

    @Test
    void molarMasses() {
        assertEquals(18.015, subs.molarMass("water"), 0.002);
        assertEquals(58.44, subs.molarMass("sodium_chloride"), 0.01);
        assertEquals(100.09, subs.molarMass("calcium_carbonate"), 0.01);
        assertEquals(44.01, subs.molarMass("carbon_dioxide"), 0.01);
        assertEquals(159.69, subs.molarMass("iron_iii_oxide"), 0.02);
    }

    @Test
    void elementsAndCompoundsAreDistinguished() {
        assertEquals("element", subs.get("oxygen").kind());
        assertEquals("ionic", subs.get("sodium_chloride").kind());
        // A mixture is not a substance: mixing salt and water must not create a new compound.
        Mixture m = new Mixture(Map.of("water", 5.0, "sodium_chloride", 0.1));
        ReactionEngine.StepResult r = db.reactionEngine().step(m, new ReactionEngine.Conditions(298, Set.of(), true, 0), 10);
        assertFalse(r.anyReaction());
        assertEquals(0.1, m.get("sodium_chloride"), 1e-12);
    }

    @Test
    void allReactionsAreBalanced() {
        for (Reaction r : db.reactions().values()) {
            assertEquals(List.of(), ReactionValidator.validate(r, subs), r.id());
            assertEquals(0, ReactionValidator.massBalance(r, subs), 1e-9, r.id());
        }
    }

    @Test
    void unbalancedReactionIsDetected() {
        Reaction good = db.reactions().get("water_electrolysis");
        Reaction bad = new Reaction("bad", "bad", "x", List.of(new Reaction.Term("water", 1, "l")),
                good.products(), good.conditions(), null, null, false, 4, 0, List.of(), good.explanation(), List.of("x"), null);
        List<String> errors = ReactionValidator.validate(bad, subs);
        assertTrue(errors.stream().anyMatch(s -> s.contains("unbalanced H")), errors.toString());
        assertTrue(errors.stream().anyMatch(s -> s.contains("unbalanced O")), errors.toString());
    }

    @Test
    void closedVesselConservesMass() {
        Mixture m = new Mixture(Map.of("water", 5.5, "calcium_carbonate", 0.1, "hydrogen_chloride", 0.1));
        double before = m.massGrams(subs);
        ReactionEngine.StepResult total = null;
        for (int i = 0; i < 200; i++) total = db.reactionEngine().step(m, new ReactionEngine.Conditions(298, Set.of(), true, 0), 1);
        assertEquals(before, m.massGrams(subs), 1e-9);
        assertEquals(0.05, m.get("carbon_dioxide"), 1e-6); // limited by HCl (2:1)
        assertEquals(0.05, m.get("calcium_carbonate"), 1e-6);
        assertEquals(0, total.escapedGasGrams(), 1e-12);
    }

    @Test
    void openVesselLosesExactlyTheEscapedGas() {
        Mixture m = new Mixture(Map.of("water", 5.5, "calcium_carbonate", 0.1, "hydrogen_chloride", 0.2));
        double before = m.massGrams(subs);
        double escaped = 0;
        for (int i = 0; i < 200; i++) escaped += db.reactionEngine().step(m, new ReactionEngine.Conditions(298, Set.of(), false, 0), 1).escapedGasGrams();
        double after = m.massGrams(subs);
        assertEquals(before, after + escaped, 1e-9);
        // CO2 above its solubility (0.033 mol/L × ~0.1 L) escapes
        double dissolvedCap = 0.033 * m.waterLitres(subs);
        assertEquals(0.1 * 44.0095 - dissolvedCap * 44.0095, escaped, 0.01);
        assertFalse(m.contains("calcium_carbonate"));
        assertTrue(m.contains("hydrogen_chloride") == false);
    }

    @Test
    void unknownCombinationProducesNoReactionAndKeepsReagents() {
        Mixture m = new Mixture(Map.of("gold", 0.5, "water", 3.0, "sucrose", 0.01));
        Map<String, Double> copy = Map.copyOf(m.amounts());
        assertTrue(db.reactionEngine().applicable(m, new ReactionEngine.Conditions(298, Set.of("heat"), true, 0)).isEmpty());
        db.reactionEngine().step(m, new ReactionEngine.Conditions(298, Set.of("heat"), true, 0), 5);
        assertEquals(copy, m.amounts());
    }

    @Test
    void temperatureThresholdsAreRespected() {
        Mixture m = new Mixture(Map.of("calcium_carbonate", 1.0));
        db.reactionEngine().step(m, new ReactionEngine.Conditions(800, Set.of("heat"), false, 0), 10);
        assertEquals(1.0, m.get("calcium_carbonate"), 1e-12);
        db.reactionEngine().step(m, new ReactionEngine.Conditions(1200, Set.of("heat"), false, 0), 10);
        assertTrue(m.get("calcium_carbonate") < 1.0);
        assertTrue(m.contains("calcium_oxide"));
    }

    @Test
    void electrolysisFollowsFaradaysLaw() {
        Mixture m = new Mixture(Map.of("water", 10.0));
        double q = 4 * Constants.FARADAY * 0.01; // charge for 0.01 mol of reaction (2 H2O -> 2 H2 + O2)
        db.reactionEngine().step(m, new ReactionEngine.Conditions(298, Set.of("electrolysis"), true, q), 1);
        assertEquals(0.02, m.get("hydrogen"), 1e-9);
        assertEquals(0.01, m.get("oxygen"), 1e-9);
        assertEquals(10.0 - 0.02, m.get("water"), 1e-9);
    }

    @Test
    void neutralisationReleasesHeat() {
        Mixture m = new Mixture(Map.of("water", 11.0, "hydrogen_chloride", 0.1, "sodium_hydroxide", 0.1));
        ReactionEngine.StepResult r = db.reactionEngine().step(m, new ReactionEngine.Conditions(298, Set.of(), true, 0), 5);
        double heat = r.events().stream().mapToDouble(ReactionEngine.Event::heatReleasedJ).sum();
        assertEquals(5580, heat, 60); // 0.1 mol × 55.8 kJ/mol, ~all reacted in 5 s
        assertEquals(7.0, AcidBaseSolver.pH(m, subs), 0.05);
    }

    @Test
    void pHCalculations() {
        double l = 1.0;
        double waterMol = 997.0 * l / subs.molarMass("water");
        assertEquals(7.0, AcidBaseSolver.pH(new Mixture(Map.of("water", waterMol)), subs), 0.01);
        assertEquals(2.0, AcidBaseSolver.pH(new Mixture(Map.of("water", waterMol, "hydrogen_chloride", 0.01)), subs), 0.01);
        assertEquals(12.0, AcidBaseSolver.pH(new Mixture(Map.of("water", waterMol, "sodium_hydroxide", 0.01)), subs), 0.01);
        assertEquals(2.88, AcidBaseSolver.pH(new Mixture(Map.of("water", waterMol, "acetic_acid", 0.1)), subs), 0.02);
        assertEquals(8.88, AcidBaseSolver.pH(new Mixture(Map.of("water", waterMol, "sodium_acetate", 0.1)), subs), 0.03);
        double lime = AcidBaseSolver.pH(new Mixture(Map.of("water", waterMol, "calcium_hydroxide", 1.0)), subs); // saturated
        assertTrue(lime > 12.3 && lime < 12.8, "limewater pH " + lime);
        // Ten-fold dilution of a strong acid raises the pH by one unit
        assertEquals(3.0, AcidBaseSolver.pH(new Mixture(Map.of("water", waterMol, "hydrogen_chloride", 0.001)), subs), 0.01);
        assertTrue(Double.isNaN(AcidBaseSolver.pH(new Mixture(Map.of("iron", 1.0)), subs)));
    }

    @Test
    void indicators() {
        assertEquals("colourless", Indicator.PHENOLPHTHALEIN.describe(7));
        assertEquals("pink", Indicator.PHENOLPHTHALEIN.describe(11));
        assertEquals("red", Indicator.LITMUS.describe(3));
        assertEquals("green", Indicator.UNIVERSAL.describe(7));
    }

    @Test
    void reactivitySeriesExplainsNonReactions() {
        Mixture cu = new Mixture(Map.of("water", 5.0, "hydrogen_chloride", 0.1, "copper", 0.5));
        assertTrue(db.reactionEngine().applicable(cu, new ReactionEngine.Conditions(298, Set.of(), true, 0)).isEmpty());
        assertTrue(com.scientia.core.chem.ReactivitySeries.explainNoReaction(cu).contains("below hydrogen"));
        Mixture fe = new Mixture(Map.of("water", 5.0, "hydrogen_chloride", 0.2, "iron", 0.5));
        double before = fe.massGrams(subs);
        for (int i = 0; i < 400; i++) db.reactionEngine().step(fe, new ReactionEngine.Conditions(298, Set.of(), true, 0), 1);
        assertTrue(fe.get("hydrogen") > 0.09, "iron + HCl should release hydrogen");
        assertEquals(before, fe.massGrams(subs), 1e-9);
    }
}
