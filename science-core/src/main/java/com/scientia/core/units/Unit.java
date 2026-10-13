package com.scientia.core.units;

/**
 * A unit of measurement. A value {@code x} in this unit corresponds to the SI value
 * {@code x * factor + offset}. Only temperature scales such as degrees Celsius use a non-zero offset;
 * such "affine" units may be converted but cannot be multiplied or divided meaningfully.
 */
public record Unit(String symbol, String name, Dimension dimension, double factor, double offset) {

    public Unit(String symbol, String name, Dimension dimension, double factor) {
        this(symbol, name, dimension, factor, 0.0);
    }

    public boolean isAffine() {
        return offset != 0.0;
    }

    public double toSI(double value) {
        return value * factor + offset;
    }

    public double fromSI(double si) {
        return (si - offset) / factor;
    }
}
