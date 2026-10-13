package com.scientia.core.chem;

import java.util.List;

/**
 * A chemical reaction definition loaded from data.
 *
 * @param deltaHkJ          standard enthalpy change for the reaction as written, in kJ, or null when this
 *                          database has no reliable value (no value is ever invented)
 * @param electrons         electrons transferred per reaction as written (electrolysis only, used with
 *                          Faraday's law)
 * @param ratePerSecond     gameplay rate constant: the fraction of the remaining possible extent that
 *                          proceeds per second (a simplified first-order approach, not real kinetics)
 */
public record Reaction(
        String id,
        String name,
        String type,
        List<Term> reactants,
        List<Term> products,
        Conditions conditions,
        Double deltaHkJ,
        String deltaHSource,
        boolean reversible,
        int electrons,
        double ratePerSecond,
        List<String> observations,
        Substance.Description explanation,
        List<String> references,
        String simplification
) {
    public record Term(String substance, int coefficient, String state) {
    }

    /**
     * @param minTemperatureK lowest temperature (K) at which the game model lets the reaction proceed
     * @param requires        apparatus/condition tags: "heat", "electrolysis", "aqueous"
     * @param catalyst        substance id that must be present but is not consumed, or null
     */
    public record Conditions(Double minTemperatureK, Double maxTemperatureK, List<String> requires, String catalyst) {
        public boolean requiresTag(String tag) {
            return requires != null && requires.contains(tag);
        }
    }

    /** Equation in plain text, e.g. "CaCO3 + 2 HCl → CaCl2 + H2O + CO2". */
    public String equation(SubstanceDatabase db) {
        return side(reactants, db) + (reversible ? " ⇌ " : " → ") + side(products, db);
    }

    private static String side(List<Term> terms, SubstanceDatabase db) {
        StringBuilder sb = new StringBuilder();
        for (Term t : terms) {
            if (sb.length() > 0) sb.append(" + ");
            if (t.coefficient() != 1) sb.append(t.coefficient()).append(' ');
            Substance s = db.get(t.substance());
            sb.append(ChemicalFormula.pretty(s == null ? t.substance() : s.formula()));
            if (t.state() != null && !t.state().isEmpty()) sb.append('(').append(t.state()).append(')');
        }
        return sb.toString();
    }
}
