package com.scientia.core.math;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;

/** Significant-figure utilities following the conventions taught in introductory science courses. */
public final class SigFigs {
    private SigFigs() {
    }

    /**
     * Counts significant figures in a written number. Trailing zeros in an integer without a decimal
     * point (e.g. "1200") are ambiguous; they are treated as not significant, which is the usual convention.
     */
    public static int count(String written) {
        String s = written.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("-") || s.startsWith("+")) s = s.substring(1);
        int e = s.indexOf('e');
        if (e >= 0) s = s.substring(0, e);
        if (s.isEmpty() || !s.matches("[0-9]*\\.?[0-9]*") || s.equals(".")) {
            throw new MathException("Not a number: " + written);
        }
        boolean hasPoint = s.contains(".");
        String digits = s.replace(".", "");
        int first = 0;
        while (first < digits.length() && digits.charAt(first) == '0') first++;
        if (first == digits.length()) {
            // The number is zero: count zeros after the decimal point, e.g. "0.00" -> 2 (convention: precision digits)
            if (!hasPoint) return 1;
            int dp = s.indexOf('.');
            return Math.max(1, s.length() - dp - 1);
        }
        String significant = digits.substring(first);
        if (!hasPoint) {
            int end = significant.length();
            while (end > 1 && significant.charAt(end - 1) == '0') end--;
            return end;
        }
        return significant.length();
    }

    public static double round(double value, int sigFigs) {
        if (value == 0 || !Double.isFinite(value)) return value;
        if (sigFigs < 1) throw new MathException("Significant figures must be at least 1");
        return new BigDecimal(value).round(new MathContext(sigFigs, RoundingMode.HALF_UP)).doubleValue();
    }

    public static double roundToDecimals(double value, int decimals) {
        if (!Double.isFinite(value)) return value;
        return new BigDecimal(value).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }

    /** Number of decimal places that rounds an uncertainty to one significant figure (may be negative). */
    public static int decimalsForUncertainty(double uncertainty) {
        if (uncertainty <= 0 || !Double.isFinite(uncertainty)) return 3;
        return (int) -Math.floor(Math.log10(uncertainty));
    }

    /** Formats a value to the given number of significant figures, using scientific notation for very large/small values. */
    public static String format(double value, int sigFigs) {
        if (Double.isNaN(value)) return "NaN";
        if (Double.isInfinite(value)) return value > 0 ? "∞" : "-∞";
        if (value == 0) return "0";
        double abs = Math.abs(value);
        if (abs >= 1e6 || abs < 1e-3) {
            BigDecimal bd = new BigDecimal(value).round(new MathContext(sigFigs, RoundingMode.HALF_UP));
            String sci = String.format(Locale.ROOT, "%." + (sigFigs - 1) + "e", bd.doubleValue());
            return sci.replace("e+0", "e").replace("e-0", "e-").replace("e+", "e");
        }
        BigDecimal bd = new BigDecimal(value).round(new MathContext(sigFigs, RoundingMode.HALF_UP));
        String plain = bd.toPlainString();
        // Keep significant trailing zeros (BigDecimal already does) but avoid absurd lengths
        return plain;
    }

    /** Sig figs for a product or quotient: the smallest count among the operands. */
    public static int forProduct(int... counts) {
        int min = Integer.MAX_VALUE;
        for (int c : counts) min = Math.min(min, c);
        return min;
    }

    /** Decimal places for a sum or difference: the fewest decimal places among the operands. */
    public static int decimalsForSum(String... written) {
        int min = Integer.MAX_VALUE;
        for (String w : written) {
            String s = w.trim();
            int dp = s.indexOf('.');
            int decimals = dp < 0 ? 0 : s.length() - dp - 1;
            min = Math.min(min, decimals);
        }
        return min;
    }
}
