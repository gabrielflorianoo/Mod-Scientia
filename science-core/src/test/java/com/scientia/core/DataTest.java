package com.scientia.core;

import com.scientia.core.chem.Element;
import com.scientia.core.chem.ElementDatabase;
import com.scientia.core.data.MaterialAnalysis;
import com.scientia.core.data.MaterialEntry;
import com.scientia.core.data.ScienceDatabase;
import com.scientia.core.research.JournalData;
import com.scientia.core.units.Measurement;
import com.scientia.core.validation.ScienceValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataTest {
    private final ScienceDatabase db = TestData.db();

    @Test
    void allDataValidates() {
        List<String> errors = ScienceValidator.validate(db);
        assertEquals(List.of(), errors, String.join("\n", errors));
    }

    @Test
    void periodicTableIsComplete() {
        assertEquals(118, db.elements().size());
        Element fe = db.elements().bySymbol("Fe");
        assertEquals(26, fe.z());
        assertEquals(55.845, fe.atomicMass(), 0.001);
        assertArrayEquals(new int[]{8, 4}, ElementDatabase.layoutPosition(fe));
        assertArrayEquals(new int[]{3, 8}, ElementDatabase.layoutPosition(db.elements().bySymbol("La")));
        assertArrayEquals(new int[]{18, 7}, ElementDatabase.layoutPosition(db.elements().bySymbol("Og")));
        assertTrue(db.elements().bySymbol("Og").propertiesPredicted());
        assertNull(db.elements().bySymbol("Og").density());
        assertEquals("synthetic", db.elements().bySymbol("Fm").occurrence());
        assertEquals("primordial", db.elements().bySymbol("U").occurrence());
        assertEquals(35, db.elements().bySymbol("Zn").approximateNeutrons()); // Zn-65 (rounded mean mass) has 35 neutrons
    }

    @Test
    void materialDensitiesAndIdentification() {
        MaterialEntry ironOre = db.materials().get("raw_iron");
        double rho = MaterialAnalysis.referenceDensity(ironOre, db);
        assertEquals(1 / (0.7 / 5.25 + 0.3 / 2.65), rho, 1e-9);
        assertEquals(7.874, MaterialAnalysis.referenceDensity(db.materials().get("iron_ingot"), db), 1e-9);
        double m1 = MaterialAnalysis.sampleMass(ironOre, 42L, 1);
        assertEquals(m1, MaterialAnalysis.sampleMass(ironOre, 42L, 1));
        assertTrue(Math.abs(m1 - 100) <= 4.0001);
        List<MaterialEntry> c = MaterialAnalysis.identifyByDensity(new Measurement(7.87, 0.12, "g/cm3"), db);
        assertTrue(c.stream().anyMatch(m -> m.id().equals("iron_ingot")));
        assertFalse(c.stream().anyMatch(m -> m.id().equals("gold_ingot")));
        double fe = MaterialAnalysis.elementalComposition(ironOre, db).get("Fe");
        assertEquals(0.7 * 2 * 55.845 / 159.69, fe, 1e-3);
    }

    @Test
    void journalUnlocksResearchOnlyFromAccomplishments() {
        JournalData j = new JournalData();
        assertFalse(j.isUnlocked("graduated_cylinder", db));
        assertTrue(j.isUnlocked("not_gated_anything", db));
        JournalData.Progress p = j.addFact("measure:mass", db);
        assertTrue(p.newResearch().contains("observation_basics"));
        assertTrue(j.isUnlocked("graduated_cylinder", db));
        assertFalse(j.addFact("measure:mass", db).any());
        p = j.addFact("measure:volume", db);
        assertTrue(p.newResearch().isEmpty());
        p = j.addFact("derived:density", db);
        assertTrue(p.newExperiments().contains("density"));
        assertTrue(p.newResearch().contains("measurement_density"));
        p = j.addFact("identify:any", db);
        assertTrue(p.newExperiments().contains("material_identification"));
        assertTrue(j.research().contains("material_identification"));
        j.addFact("element:Fe", db);
        assertTrue(j.elements().contains("Fe"));
    }

    @Test
    void journalSerialisationRoundTrip() {
        JournalData j = new JournalData();
        j.addFact("measure:mass", db);
        j.addMeasurement(new JournalData.MeasurementRecord("mass", "iron_ingot", 100.12, 0.01, "g", 1234L, "balance"));
        j.addNote("Iron ore is lighter than an iron ingot of the same size.");
        j.hypotheses().put("conservation_of_mass", new JournalData.HypothesisRecord("conservation_of_mass", "equal", null, null, null));
        JournalData k = JournalData.fromJson(j.toJson());
        assertEquals(j.facts(), k.facts());
        assertEquals(j.research(), k.research());
        assertEquals(100.12, k.latest("mass", "iron_ingot").value(), 1e-12);
        assertEquals("equal", k.hypotheses().get("conservation_of_mass").predicted());
        assertEquals(1, k.notes().size());
        assertNotNull(JournalData.fromJson("{not json"));
        assertNotNull(JournalData.fromJson("{}").facts());
    }

    @Test
    void measurementHistoryIsBounded() {
        JournalData j = new JournalData();
        for (int i = 0; i < 1000; i++) j.addMeasurement(new JournalData.MeasurementRecord("mass", "x", i, 0, "g", i, "b"));
        assertEquals(JournalData.MAX_MEASUREMENTS, j.measurements().size());
        assertFalse(j.addNote("   "));
    }
}
