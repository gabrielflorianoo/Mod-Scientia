package com.scientia.core.units;

import java.util.Arrays;

/**
 * A physical dimension expressed as integer exponents of the seven SI base quantities:
 * length (L), mass (M), time (T), electric current (I), thermodynamic temperature (Θ),
 * amount of substance (N) and luminous intensity (J).
 */
public final class Dimension {
    public static final int LENGTH = 0, MASS = 1, TIME = 2, CURRENT = 3, TEMPERATURE = 4, AMOUNT = 5, LUMINOUS = 6;
    private static final String[] SYMBOLS = {"m", "kg", "s", "A", "K", "mol", "cd"};

    public static final Dimension NONE = new Dimension(new int[7]);
    public static final Dimension L = base(LENGTH);
    public static final Dimension M = base(MASS);
    public static final Dimension T = base(TIME);
    public static final Dimension I = base(CURRENT);
    public static final Dimension THETA = base(TEMPERATURE);
    public static final Dimension N = base(AMOUNT);

    private final int[] exponents;

    private Dimension(int[] exponents) {
        this.exponents = exponents;
    }

    public static Dimension of(int length, int mass, int time, int current, int temperature, int amount, int luminous) {
        return new Dimension(new int[]{length, mass, time, current, temperature, amount, luminous});
    }

    private static Dimension base(int index) {
        int[] e = new int[7];
        e[index] = 1;
        return new Dimension(e);
    }

    public Dimension multiply(Dimension other) {
        int[] e = new int[7];
        for (int i = 0; i < 7; i++) e[i] = exponents[i] + other.exponents[i];
        return new Dimension(e);
    }

    public Dimension divide(Dimension other) {
        int[] e = new int[7];
        for (int i = 0; i < 7; i++) e[i] = exponents[i] - other.exponents[i];
        return new Dimension(e);
    }

    public Dimension pow(int power) {
        int[] e = new int[7];
        for (int i = 0; i < 7; i++) e[i] = exponents[i] * power;
        return new Dimension(e);
    }

    /** Returns the n-th root, or null when the exponents are not all divisible by n. */
    public Dimension root(int n) {
        int[] e = new int[7];
        for (int i = 0; i < 7; i++) {
            if (exponents[i] % n != 0) return null;
            e[i] = exponents[i] / n;
        }
        return new Dimension(e);
    }

    public boolean isDimensionless() {
        for (int x : exponents) if (x != 0) return false;
        return true;
    }

    public int exponent(int index) {
        return exponents[index];
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Dimension d && Arrays.equals(exponents, d.exponents);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(exponents);
    }

    /** SI base-unit representation, e.g. {@code kg·m^2·s^-2}. */
    @Override
    public String toString() {
        if (isDimensionless()) return "1";
        StringBuilder sb = new StringBuilder();
        int[] order = {MASS, LENGTH, TIME, CURRENT, TEMPERATURE, AMOUNT, LUMINOUS};
        for (int idx : order) {
            int e = exponents[idx];
            if (e == 0) continue;
            if (sb.length() > 0) sb.append('·');
            sb.append(SYMBOLS[idx]);
            if (e != 1) sb.append('^').append(e);
        }
        return sb.toString();
    }
}
