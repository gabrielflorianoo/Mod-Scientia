package com.scientia.util;

import com.scientia.core.chem.Mixture;
import com.scientia.core.chem.Substance;
import com.scientia.core.chem.SubstanceDatabase;

import java.util.List;

/** Moving liquids between vessels: only the liquid phase (water and what is dissolved in it) pours. */
public final class Liquids {
    private Liquids() {
    }

    /** Amount (mol) of a substance that is in the liquid phase of the mixture. */
    public static double liquidPortion(Mixture m, String id, SubstanceDatabase subs) {
        double n = m.get(id);
        if (id.equals("water")) return n;
        if (id.equals("ice")) return 0;
        Substance s = subs.get(id);
        if (s == null) return 0;
        double litres = m.waterLitres(subs);
        if ("liquid".equals(s.phase())) return n;
        if (litres <= 1e-6) return 0;
        if (s.solubility() == null) return s.isGas() ? n : n; // very soluble
        return Math.min(n, s.solubility() * litres);
    }

    /** Removes up to {@code maxWaterGrams} of water together with the same fraction of everything dissolved. */
    public static Mixture takeLiquid(Mixture source, double maxWaterGrams) {
        SubstanceDatabase subs = Sci.db().substances();
        double water = Sci.waterGrams(source);
        Mixture out = new Mixture();
        if (water <= 1e-6) return out;
        double f = Math.min(1.0, maxWaterGrams / water);
        for (String id : List.copyOf(source.amounts().keySet())) {
            double take = liquidPortion(source, id, subs) * f;
            if (take > 0) out.add(id, source.remove(id, take));
        }
        return out;
    }

    /** Removes everything that is not in the liquid phase (insoluble or undissolved solids). */
    public static Mixture takeSolids(Mixture source) {
        SubstanceDatabase subs = Sci.db().substances();
        Mixture out = new Mixture();
        for (String id : List.copyOf(source.amounts().keySet())) {
            Substance s = subs.get(id);
            if (s == null || s.isGas() || id.equals("water") || id.equals("ice")) continue;
            double solid = source.get(id) - liquidPortion(source, id, subs);
            if (solid > 1e-9) out.add(id, source.remove(id, solid));
        }
        return out;
    }

    /** Water volume in mL (dilute solutions: solution volume ≈ water volume). */
    public static double volumeMl(Mixture m) {
        return Sci.waterGrams(m) / 0.997;
    }
}
