package com.scientia.core.chem;

import java.util.List;

/**
 * The metal reactivity series, used to explain why a metal does or does not react with a dilute acid.
 * Metals above hydrogen displace it from acids (releasing H₂); metals below it do not.
 */
public final class ReactivitySeries {
    /** Most to least reactive; "H" marks hydrogen's position. */
    public static final List<String> ORDER = List.of("K", "Na", "Ca", "Mg", "Al", "Zn", "Fe", "Sn", "Pb", "H", "Cu", "Ag", "Au");
    private static final List<String> ACIDS = List.of("hydrogen_chloride", "acetic_acid");
    private static final List<String[]> METALS = List.of(new String[]{"copper", "Cu", "Copper"}, new String[]{"gold", "Au", "Gold"},
            new String[]{"iron", "Fe", "Iron"});

    private ReactivitySeries() {
    }

    public static boolean aboveHydrogen(String symbol) {
        int i = ORDER.indexOf(symbol);
        return i >= 0 && i < ORDER.indexOf("H");
    }

    /**
     * Explains a metal–acid mixture in which no database reaction is running, or returns null if the
     * mixture contains no such pair.
     */
    public static String explainNoReaction(Mixture m) {
        if (!m.contains("water")) return null;
        String acid = null;
        for (String a : ACIDS) if (m.contains(a)) acid = a;
        if (acid == null) return null;
        for (String[] metal : METALS) {
            if (!m.contains(metal[0])) continue;
            if (!aboveHydrogen(metal[1])) {
                return metal[2] + " is below hydrogen in the reactivity series, so it cannot release hydrogen from a dilute acid: no reaction.";
            }
            if (acid.equals("acetic_acid")) {
                return metal[2] + " is above hydrogen, but acetic acid is weak: the reaction is extremely slow and is not modelled.";
            }
        }
        return null;
    }
}
