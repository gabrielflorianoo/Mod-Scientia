package com.scientia.core.data;

import com.scientia.core.chem.Substance;
import com.scientia.core.units.Measurement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Derived properties of in-game materials: reference density, sample masses, elemental composition. */
public final class MaterialAnalysis {
    /** Samples of the same item vary by up to ±4 % in mass, like real hand specimens. */
    public static final double SAMPLE_VARIATION = 0.04;

    private MaterialAnalysis() {
    }

    /** Reference density (g/cm³): the declared value, or a mixture density assuming additive volumes. */
    public static Double referenceDensity(MaterialEntry m, ScienceDatabase db) {
        if (m.density() != null) return m.density();
        if (m.composition() == null || m.composition().isEmpty()) return null;
        double inv = 0, total = 0;
        for (MaterialEntry.Component c : m.composition()) {
            Substance s = db.substances().get(c.substance());
            if (s == null || s.density() == null) return null;
            inv += c.massFraction() / s.density();
            total += c.massFraction();
        }
        return inv <= 0 ? null : total / inv;
    }

    /** Deterministic pseudo-random mass of a specific sample (seed identifies the physical specimen). */
    public static double sampleMass(MaterialEntry m, long seed, int count) {
        long x = seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
        x ^= (x >>> 31);
        x *= 0xBF58476D1CE4E5B9L;
        x ^= (x >>> 29);
        double u = ((x >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53); // 0..1
        double factor = 1 + SAMPLE_VARIATION * (2 * u - 1);
        return m.sampleMassG() * factor * count;
    }

    /** Elemental mass fractions of a material, combining all its component substances. */
    public static Map<String, Double> elementalComposition(MaterialEntry m, ScienceDatabase db) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (m.composition() == null) return out;
        for (MaterialEntry.Component c : m.composition()) {
            if (!db.substances().has(c.substance())) continue;
            Map<String, Double> f = db.substances().formula(c.substance()).massFractions(db.elements());
            for (var e : f.entrySet()) out.merge(e.getKey(), e.getValue() * c.massFraction(), Double::sum);
        }
        return out;
    }

    /** Candidate materials whose reference density agrees with a measured density (within 2σ plus 1.5 %). */
    public static List<MaterialEntry> identifyByDensity(Measurement density, ScienceDatabase db) {
        List<MaterialEntry> out = new ArrayList<>();
        for (MaterialEntry m : db.materials().values()) {
            Double ref = referenceDensity(m, db);
            if (ref == null) continue;
            double tol = 2 * density.uncertainty() + 0.015 * ref;
            if (Math.abs(density.value() - ref) <= tol) out.add(m);
        }
        return out;
    }
}
