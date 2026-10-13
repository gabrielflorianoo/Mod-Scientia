package com.scientia.core.experiment;

/**
 * Compares a measured change against its uncertainty. A change smaller than the coverage interval
 * (k·σ, k = 2 by default, ≈95 % for normally distributed errors) is reported as "no significant change", so
 * a reasonable hypothesis is never marked wrong because of measurement noise.
 */
public final class HypothesisEvaluator {
    public static final double COVERAGE = 2.0;

    private HypothesisEvaluator() {
    }

    /** Returns "increase", "equal" or "decrease". */
    public static String classifyChange(double delta, double uncertainty) {
        double tol = COVERAGE * Math.max(uncertainty, 1e-12);
        if (delta > tol) return "increase";
        if (delta < -tol) return "decrease";
        return "equal";
    }

    public record Outcome(String predicted, String observed, boolean supported, String explanation) {
    }

    public static Outcome evaluate(String predicted, String observed, String context) {
        boolean ok = predicted.equals(observed);
        String text = ok
                ? "Your prediction is supported by the data. " + context
                : "The data do not support this prediction — that is a normal and useful scientific result. " + context;
        return new Outcome(predicted, observed, ok, text);
    }
}
