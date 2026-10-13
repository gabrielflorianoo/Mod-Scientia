package com.scientia.core.chem;

import com.scientia.core.math.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Computes the pH of an aqueous mixture at 25 °C by solving the charge balance
 * <pre>[H⁺] + Σ(strong cations) = [OH⁻] + Σ(strong anions) + Σ C_T(α₁ + 2α₂)</pre>
 * for [H⁺] by bisection, including water autoionisation (K_w = 1.0×10⁻¹⁴) and weak acid systems.
 * Concentrations stand in for activities (ideal dilute solution); this is the standard approximation
 * pH ≈ −log₁₀[H⁺], which becomes inaccurate for concentrated solutions.
 */
public final class AcidBaseSolver {
    /** pKa values at 25 °C for the weak acid systems used in the game. */
    private static final Map<String, double[]> SYSTEMS = Map.of(
            "acetate", new double[]{4.76},          // CH3COOH ⇌ H⁺ + CH3COO⁻
            "carbonate", new double[]{6.35, 10.33}  // H2CO3* ⇌ HCO3⁻ ⇌ CO3²⁻ (apparent pKa1 includes dissolved CO2)
    );

    private AcidBaseSolver() {
    }

    /** Returns the pH, or NaN when the mixture contains no liquid water. */
    public static double pH(Mixture m, SubstanceDatabase db) {
        double litres = m.waterLitres(db);
        if (litres < 1e-6) return Double.NaN;
        double strong = 0; // net strong cation minus strong anion, mol/L of charge
        Map<String, Double> systemTotals = new HashMap<>();
        for (var e : m.amounts().entrySet()) {
            Substance s = db.get(e.getKey());
            if (s == null || s.acidBase() == null) continue;
            double dissolved = e.getValue();
            if (s.solubility() != null) dissolved = Math.min(dissolved, s.solubility() * litres);
            double c = dissolved / litres;
            Substance.AcidBase ab = s.acidBase();
            strong += c * (ab.strongCation() - ab.strongAnion());
            if (ab.system() != null && SYSTEMS.containsKey(ab.system())) {
                systemTotals.merge(ab.system(), c * ab.systemMoles(), Double::sum);
            }
        }
        final double s = strong;
        double lo = -16, hi = 2; // log10 [H+]
        for (int i = 0; i < 200; i++) {
            double mid = (lo + hi) / 2;
            if (balance(Math.pow(10, mid), s, systemTotals) > 0) hi = mid;
            else lo = mid;
        }
        return -(lo + hi) / 2;
    }

    /** Charge balance residual; increases monotonically with [H⁺]. */
    private static double balance(double h, double strong, Map<String, Double> systems) {
        double r = h + strong - Constants.KW_25C / h;
        for (var e : systems.entrySet()) {
            double[] pka = SYSTEMS.get(e.getKey());
            r -= e.getValue() * negativeChargePerMole(h, pka);
        }
        return r;
    }

    /** Average negative charge per mole of a weak acid system at a given [H⁺] (α₁ + 2α₂ + ...). */
    static double negativeChargePerMole(double h, double[] pka) {
        int n = pka.length;
        double[] terms = new double[n + 1];
        terms[0] = Math.pow(h, n);
        double kprod = 1;
        for (int i = 1; i <= n; i++) {
            kprod *= Math.pow(10, -pka[i - 1]);
            terms[i] = Math.pow(h, n - i) * kprod;
        }
        double sum = 0, charge = 0;
        for (int i = 0; i <= n; i++) {
            sum += terms[i];
            charge += i * terms[i];
        }
        return charge / sum;
    }

    /** Describes acidity in words. pH 7 is neutral only at 25 °C. */
    public static String classify(double pH) {
        if (Double.isNaN(pH)) return "no aqueous solution";
        if (pH < 3) return "strongly acidic";
        if (pH < 6.5) return "acidic";
        if (pH <= 7.5) return "close to neutral";
        if (pH <= 11) return "basic (alkaline)";
        return "strongly basic";
    }
}
