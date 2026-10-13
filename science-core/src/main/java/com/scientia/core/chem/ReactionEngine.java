package com.scientia.core.chem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Discrete-step reaction processor. Each call to {@link #step} advances every applicable reaction by a
 * bounded extent and returns what happened, including heat released and gas that escaped from an open
 * vessel. Only reactions present in the database can happen: an unknown combination of substances
 * simply produces no reaction.
 */
public final class ReactionEngine {
    private static final double WATER_FOR_AQUEOUS_MOL = 0.05; // ~1 mL of water

    private final SubstanceDatabase substances;
    private final List<Reaction> reactions;

    public ReactionEngine(SubstanceDatabase substances, Collection<Reaction> reactions) {
        this.substances = substances;
        this.reactions = List.copyOf(reactions);
    }

    /**
     * @param temperatureK current temperature of the vessel contents
     * @param tags         conditions the apparatus provides: "heat", "electrolysis" (aqueous is detected automatically)
     * @param closed       whether the vessel is sealed (gases are retained) or open (gaseous products escape)
     * @param chargeC      electric charge passed through the contents during this step (electrolysis), in coulombs
     */
    public record Conditions(double temperatureK, Set<String> tags, boolean closed, double chargeC) {
    }

    public record Event(Reaction reaction, double extentMol, double heatReleasedJ) {
    }

    /** @param escapedMoles moles of each gas that escaped from an open vessel during the step */
    public record StepResult(List<Event> events, double escapedGasGrams, List<String> escapedGases, java.util.Map<String, Double> escapedMoles) {
        public boolean anyReaction() {
            return !events.isEmpty();
        }
    }

    public List<Reaction> applicable(Mixture m, Conditions c) {
        List<Reaction> out = new ArrayList<>();
        for (Reaction r : reactions) if (canProceed(r, m, c)) out.add(r);
        return out;
    }

    public boolean canProceed(Reaction r, Mixture m, Conditions c) {
        for (Reaction.Term t : r.reactants()) if (!m.contains(t.substance())) return false;
        Reaction.Conditions rc = r.conditions();
        if (rc != null) {
            if (rc.minTemperatureK() != null && c.temperatureK() < rc.minTemperatureK()) return false;
            if (rc.maxTemperatureK() != null && c.temperatureK() > rc.maxTemperatureK()) return false;
            if (rc.requiresTag("heat") && !c.tags().contains("heat")) return false;
            if (rc.requiresTag("electrolysis") && (!c.tags().contains("electrolysis") || c.chargeC() <= 0)) return false;
            if (rc.requiresTag("aqueous") && m.get("water") < WATER_FOR_AQUEOUS_MOL) return false;
            if (rc.catalyst() != null && !m.contains(rc.catalyst())) return false;
        }
        return true;
    }

    /** Maximum extent (mol of "reaction as written") permitted by the limiting reactant. */
    public static double maxExtent(Reaction r, Mixture m) {
        double xi = Double.POSITIVE_INFINITY;
        for (Reaction.Term t : r.reactants()) xi = Math.min(xi, m.get(t.substance()) / t.coefficient());
        return xi;
    }

    public StepResult step(Mixture m, Conditions c, double dtSeconds) {
        List<Event> events = new ArrayList<>();
        double charge = c.chargeC();
        for (Reaction r : reactions) {
            if (!canProceed(r, m, new Conditions(c.temperatureK(), c.tags(), c.closed(), charge))) continue;
            double limit = maxExtent(r, m);
            double xi;
            if (r.conditions() != null && r.conditions().requiresTag("electrolysis")) {
                // Faraday's law: extent = Q / (z F)
                xi = Math.min(limit, charge / (r.electrons() * com.scientia.core.math.Constants.FARADAY));
                charge -= xi * r.electrons() * com.scientia.core.math.Constants.FARADAY;
            } else {
                double frac = 1 - Math.exp(-r.ratePerSecond() * dtSeconds);
                xi = limit * frac;
                if (limit < 1e-5) xi = limit; // finish off tiny remainders
            }
            if (xi <= 0) continue;
            for (Reaction.Term t : r.reactants()) m.add(t.substance(), -xi * t.coefficient());
            for (Reaction.Term t : r.products()) m.add(t.substance(), xi * t.coefficient());
            double heat = r.deltaHkJ() == null ? 0 : -r.deltaHkJ() * 1000 * xi;
            events.add(new Event(r, xi, heat));
        }
        double escaped = 0;
        List<String> gases = new ArrayList<>();
        java.util.Map<String, Double> escapedMoles = new java.util.LinkedHashMap<>();
        if (!c.closed()) {
            double litres = m.waterLitres(substances);
            for (String id : List.copyOf(m.amounts().keySet())) {
                Substance s = substances.get(id);
                if (s == null || id.equals("water") || !isGasAt(s, c.temperatureK())) continue;
                // A gas stays dissolved up to its solubility; "very soluble" gases (null) stay while water is present.
                double capacity = litres <= 1e-6 ? 0 : s.solubility() == null ? Double.POSITIVE_INFINITY : s.solubility() * litres;
                double excess = m.get(id) - capacity;
                if (excess > Mixture.EPSILON) {
                    escaped += excess * substances.molarMass(id);
                    m.remove(id, excess);
                    gases.add(id);
                    escapedMoles.put(id, excess);
                }
            }
        }
        return new StepResult(events, escaped, gases, escapedMoles);
    }

    /** A substance is treated as gaseous above its boiling point (or if it is a gas at 25 °C with no boiling data). */
    public static boolean isGasAt(Substance s, double temperatureK) {
        if (s.boilingPointK() != null) return temperatureK >= s.boilingPointK();
        return s.isGas();
    }

    public List<Reaction> reactions() {
        return reactions;
    }
}
