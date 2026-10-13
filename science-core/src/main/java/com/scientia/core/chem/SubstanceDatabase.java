package com.scientia.core.chem;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure substances with their parsed formulas and molar masses computed from the element table. */
public final class SubstanceDatabase {
    private final Map<String, Substance> byId = new LinkedHashMap<>();
    private final Map<String, ChemicalFormula> formulas = new HashMap<>();
    private final Map<String, Double> molarMass = new HashMap<>();
    private final ElementDatabase elements;

    public SubstanceDatabase(List<Substance> substances, ElementDatabase elements) {
        this.elements = elements;
        for (Substance s : substances) {
            byId.put(s.id(), s);
            ChemicalFormula f = ChemicalFormula.parse(s.formula(), elements::isElement);
            formulas.put(s.id(), f);
            molarMass.put(s.id(), f.molarMass(elements));
        }
    }

    public Substance get(String id) {
        return byId.get(id);
    }

    public boolean has(String id) {
        return byId.containsKey(id);
    }

    public Collection<Substance> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public ChemicalFormula formula(String id) {
        ChemicalFormula f = formulas.get(id);
        if (f == null) throw new IllegalArgumentException("Unknown substance '" + id + "'");
        return f;
    }

    /** Molar mass in g/mol. */
    public double molarMass(String id) {
        Double m = molarMass.get(id);
        if (m == null) throw new IllegalArgumentException("Unknown substance '" + id + "'");
        return m;
    }

    public ElementDatabase elements() {
        return elements;
    }
}
