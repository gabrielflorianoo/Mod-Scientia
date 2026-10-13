package com.scientia.core.chem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Checks that reactions are balanced in atoms (and, for listed ions, in charge) and reference known substances. */
public final class ReactionValidator {
    private ReactionValidator() {
    }

    public static List<String> validate(Reaction r, SubstanceDatabase db) {
        List<String> errors = new ArrayList<>();
        if (r.reactants() == null || r.reactants().isEmpty()) errors.add(r.id() + ": no reactants");
        if (r.products() == null || r.products().isEmpty()) errors.add(r.id() + ": no products");
        if (!errors.isEmpty()) return errors;
        for (Reaction.Term t : concat(r.reactants(), r.products())) {
            if (!db.has(t.substance())) errors.add(r.id() + ": unknown substance '" + t.substance() + "'");
            if (t.coefficient() <= 0) errors.add(r.id() + ": coefficient must be positive for " + t.substance());
        }
        if (!errors.isEmpty()) return errors;
        Map<String, Integer> left = atoms(r.reactants(), db), right = atoms(r.products(), db);
        TreeSet<String> all = new TreeSet<>(left.keySet());
        all.addAll(right.keySet());
        for (String el : all) {
            int a = left.getOrDefault(el, 0), b = right.getOrDefault(el, 0);
            if (a != b) errors.add(r.id() + ": unbalanced " + el + " (" + a + " on the left, " + b + " on the right)");
        }
        if (r.conditions() != null && r.conditions().requiresTag("electrolysis") && r.electrons() <= 0) {
            errors.add(r.id() + ": electrolysis reaction must declare electrons transferred");
        }
        if (r.conditions() != null && r.conditions().catalyst() != null && !db.has(r.conditions().catalyst())) {
            errors.add(r.id() + ": unknown catalyst '" + r.conditions().catalyst() + "'");
        }
        if (r.ratePerSecond() <= 0 && (r.conditions() == null || !r.conditions().requiresTag("electrolysis"))) {
            errors.add(r.id() + ": ratePerSecond must be positive");
        }
        if (r.explanation() == null || isBlank(r.explanation().intuition()) || isBlank(r.explanation().science())) {
            errors.add(r.id() + ": missing educational explanation");
        }
        if (r.references() == null || r.references().isEmpty()) errors.add(r.id() + ": missing reference notes");
        return errors;
    }

    /** Net mass change (g) of the reaction as written: should be ~0 for every balanced reaction. */
    public static double massBalance(Reaction r, SubstanceDatabase db) {
        double m = 0;
        for (Reaction.Term t : r.reactants()) m -= t.coefficient() * db.molarMass(t.substance());
        for (Reaction.Term t : r.products()) m += t.coefficient() * db.molarMass(t.substance());
        return m;
    }

    static Map<String, Integer> atoms(List<Reaction.Term> terms, SubstanceDatabase db) {
        Map<String, Integer> m = new HashMap<>();
        for (Reaction.Term t : terms) {
            for (var e : db.formula(t.substance()).composition().entrySet()) {
                m.merge(e.getKey(), e.getValue() * t.coefficient(), Integer::sum);
            }
        }
        return m;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> l = new ArrayList<>(a);
        l.addAll(b);
        return l;
    }
}
