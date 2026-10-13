package com.scientia.core.physics;

import java.util.Map;

/** Geometric (ray) optics: thin lenses, mirrors, refraction and dispersion. */
public final class Optics {
    private Optics() {
    }

    /** Refractive indices at ~589 nm (sodium D line), 20 °C. */
    public static final Map<String, Double> REFRACTIVE_INDEX = Map.of(
            "vacuum", 1.0,
            "air", 1.000293,
            "water", 1.333,
            "ice", 1.31,
            "ethanol", 1.361,
            "crown_glass", 1.517,
            "quartz", 1.544,      // ordinary ray; quartz is birefringent
            "diamond", 2.417
    );

    /** Thin-lens equation 1/f = 1/d_o + 1/d_i (real-is-positive convention). Returns d_i, or ±∞ at the focal point. */
    public static double imageDistance(double focalLength, double objectDistance) {
        double inv = 1.0 / focalLength - 1.0 / objectDistance;
        if (Math.abs(inv) < 1e-12) return Double.POSITIVE_INFINITY;
        return 1.0 / inv;
    }

    /** Linear magnification m = −d_i / d_o (negative means inverted). */
    public static double magnification(double objectDistance, double imageDistance) {
        return -imageDistance / objectDistance;
    }

    /**
     * Snell's law n₁ sin θ₁ = n₂ sin θ₂. Angles in degrees from the normal.
     * Returns NaN for total internal reflection.
     */
    public static double refractionAngle(double n1, double n2, double incidenceDeg) {
        double s = n1 / n2 * Math.sin(Math.toRadians(incidenceDeg));
        if (Math.abs(s) > 1) return Double.NaN;
        return Math.toDegrees(Math.asin(s));
    }

    /** Critical angle for total internal reflection going from n1 into n2 (n1 > n2), or NaN. */
    public static double criticalAngle(double n1, double n2) {
        if (n1 <= n2) return Double.NaN;
        return Math.toDegrees(Math.asin(n2 / n1));
    }

    /** Law of reflection: angle of reflection equals angle of incidence. */
    public static double reflectionAngle(double incidenceDeg) {
        return incidenceDeg;
    }

    /**
     * Refractive index of a borosilicate crown glass (BK7-like) from the two-term Cauchy approximation
     * n(λ) = A + B/λ², λ in micrometres. Adequate across the visible range (≈ ±0.001).
     */
    public static double crownGlassIndex(double wavelengthNm) {
        double um = wavelengthNm / 1000.0;
        return 1.5046 + 0.00420 / (um * um);
    }

    /** Lensmaker's equation for a thin lens in air: 1/f = (n − 1)(1/R₁ − 1/R₂). */
    public static double lensmaker(double n, double r1, double r2) {
        return 1.0 / ((n - 1) * (1.0 / r1 - 1.0 / r2));
    }

    /** Approximate sRGB colour (0xRRGGBB) of monochromatic light, for spectrum displays (380–750 nm). */
    public static int wavelengthToRgb(double nm) {
        double r = 0, g = 0, b = 0;
        if (nm >= 380 && nm < 440) { r = -(nm - 440) / 60; b = 1; }
        else if (nm < 490 && nm >= 440) { g = (nm - 440) / 50; b = 1; }
        else if (nm < 510 && nm >= 490) { g = 1; b = -(nm - 510) / 20; }
        else if (nm < 580 && nm >= 510) { r = (nm - 510) / 70; g = 1; }
        else if (nm < 645 && nm >= 580) { r = 1; g = -(nm - 645) / 65; }
        else if (nm <= 750 && nm >= 645) { r = 1; }
        double f = 1;
        if (nm >= 380 && nm < 420) f = 0.3 + 0.7 * (nm - 380) / 40;
        else if (nm > 700 && nm <= 750) f = 0.3 + 0.7 * (750 - nm) / 50;
        else if (nm < 380 || nm > 750) f = 0;
        int ri = (int) Math.round(255 * Math.pow(r * f, 0.8));
        int gi = (int) Math.round(255 * Math.pow(g * f, 0.8));
        int bi = (int) Math.round(255 * Math.pow(b * f, 0.8));
        return (ri << 16) | (gi << 8) | bi;
    }
}
