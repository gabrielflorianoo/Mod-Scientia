package com.scientia.core.chem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contents of a vessel: amounts (mol) of each pure substance. A mixture is a physical combination;
 * its components keep their identities until a chemical reaction changes them.
 */
public final class Mixture {
    public static final double EPSILON = 1e-9;
    private final Map<String, Double> moles = new LinkedHashMap<>();

    public Mixture() {
    }

    public Mixture(Map<String, Double> initial) {
        initial.forEach(this::add);
    }

    public Mixture copy() {
        return new Mixture(moles);
    }

    public void add(String substanceId, double mol) {
        if (mol == 0) return;
        double v = moles.getOrDefault(substanceId, 0.0) + mol;
        if (v <= EPSILON) moles.remove(substanceId);
        else moles.put(substanceId, v);
    }

    /** Removes up to {@code mol}; returns the amount actually removed. */
    public double remove(String substanceId, double mol) {
        double have = moles.getOrDefault(substanceId, 0.0);
        double taken = Math.min(have, mol);
        add(substanceId, -taken);
        return taken;
    }

    public double get(String substanceId) {
        return moles.getOrDefault(substanceId, 0.0);
    }

    public boolean contains(String substanceId) {
        return get(substanceId) > EPSILON;
    }

    public boolean isEmpty() {
        return moles.isEmpty();
    }

    public void clear() {
        moles.clear();
    }

    public Map<String, Double> amounts() {
        return Collections.unmodifiableMap(moles);
    }

    /** Total mass in grams. */
    public double massGrams(SubstanceDatabase db) {
        double m = 0;
        for (var e : moles.entrySet()) m += e.getValue() * db.molarMass(e.getKey());
        return m;
    }

    public double massOf(String substanceId, SubstanceDatabase db) {
        return get(substanceId) * db.molarMass(substanceId);
    }

    /** Volume of liquid water in litres (the solvent volume used for dilute-solution concentrations). */
    public double waterLitres(SubstanceDatabase db) {
        return massOf("water", db) / 997.0;
    }

    @Override
    public String toString() {
        return moles.toString();
    }
}
