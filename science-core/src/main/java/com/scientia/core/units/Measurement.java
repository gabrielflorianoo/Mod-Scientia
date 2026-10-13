package com.scientia.core.units;

import com.scientia.core.math.SigFigs;

/**
 * A measured value with an absolute standard uncertainty, both expressed in {@link #unit()}.
 * Propagation assumes independent random uncertainties (added in quadrature), the standard
 * first-order treatment used in introductory laboratory courses.
 */
public record Measurement(double value, double uncertainty, String unit) {

    public Measurement {
        uncertainty = Math.abs(uncertainty);
    }

    public double relativeUncertainty() {
        return value == 0 ? Double.POSITIVE_INFINITY : uncertainty / Math.abs(value);
    }

    public Measurement plus(Measurement o) {
        requireUnit(o);
        return new Measurement(value + o.value, Math.hypot(uncertainty, o.uncertainty), unit);
    }

    public Measurement minus(Measurement o) {
        requireUnit(o);
        return new Measurement(value - o.value, Math.hypot(uncertainty, o.uncertainty), unit);
    }

    /** Quotient with relative uncertainties combined in quadrature; the caller names the resulting unit. */
    public Measurement dividedBy(Measurement o, String resultUnit) {
        double q = value / o.value;
        double rel = Math.hypot(relativeUncertainty(), o.relativeUncertainty());
        return new Measurement(q, Math.abs(q) * rel, resultUnit);
    }

    public Measurement times(Measurement o, String resultUnit) {
        double p = value * o.value;
        double rel = Math.hypot(relativeUncertainty(), o.relativeUncertainty());
        return new Measurement(p, Math.abs(p) * rel, resultUnit);
    }

    /** True when the two values agree within the combined uncertainty multiplied by {@code k} (coverage factor). */
    public boolean agreesWith(double reference, double k) {
        return Math.abs(value - reference) <= k * Math.max(uncertainty, 1e-12);
    }

    /** Formats as e.g. {@code 7.87 ± 0.05 g/cm3}, rounding the uncertainty to 1 significant figure. */
    public String format() {
        if (uncertainty <= 0 || !Double.isFinite(uncertainty)) {
            return SigFigs.format(value, 4) + " " + unit;
        }
        int decimals = SigFigs.decimalsForUncertainty(uncertainty);
        double u = SigFigs.roundToDecimals(uncertainty, decimals);
        double v = SigFigs.roundToDecimals(value, decimals);
        String fmt = "%." + Math.max(decimals, 0) + "f";
        return String.format(java.util.Locale.ROOT, fmt + " ± " + fmt + " %s", v, u, unit).trim();
    }

    private void requireUnit(Measurement o) {
        if (!unit.equals(o.unit)) throw new UnitException("Unit mismatch: " + unit + " vs " + o.unit);
    }
}
