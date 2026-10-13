package com.scientia.core.units;

/**
 * An SI value with a physical dimension. Arithmetic enforces dimensional consistency:
 * quantities may only be added, subtracted or compared when they share a dimension.
 */
public record Quantity(double si, Dimension dimension) {

    public static Quantity of(double value, Unit unit) {
        return new Quantity(unit.toSI(value), unit.dimension());
    }

    public static Quantity of(double value, String unitSymbol) {
        return of(value, Units.require(unitSymbol));
    }

    public static Quantity dimensionless(double value) {
        return new Quantity(value, Dimension.NONE);
    }

    public Quantity add(Quantity o) {
        requireSame(o, "add");
        return new Quantity(si + o.si, dimension);
    }

    public Quantity subtract(Quantity o) {
        requireSame(o, "subtract");
        return new Quantity(si - o.si, dimension);
    }

    public Quantity multiply(Quantity o) {
        return new Quantity(si * o.si, dimension.multiply(o.dimension));
    }

    public Quantity divide(Quantity o) {
        return new Quantity(si / o.si, dimension.divide(o.dimension));
    }

    public Quantity negate() {
        return new Quantity(-si, dimension);
    }

    public Quantity pow(double exponent) {
        if (dimension.isDimensionless()) return new Quantity(Math.pow(si, exponent), dimension);
        if (exponent == Math.rint(exponent)) {
            return new Quantity(Math.pow(si, exponent), dimension.pow((int) exponent));
        }
        // Allow roots that yield integral dimensions, e.g. sqrt(m^2) -> m
        for (int n = 2; n <= 4; n++) {
            double k = exponent * n;
            if (Math.abs(k - Math.rint(k)) < 1e-12) {
                Dimension r = dimension.root(n);
                if (r != null) return new Quantity(Math.pow(si, exponent), r.pow((int) Math.rint(k)));
            }
        }
        throw new UnitException("Cannot raise a quantity with dimension " + dimension + " to the power " + exponent);
    }

    public double in(Unit unit) {
        if (!unit.dimension().equals(dimension)) {
            throw new UnitException("Cannot express " + dimension + " in " + unit.symbol() + " (" + unit.dimension() + ")");
        }
        return unit.fromSI(si);
    }

    public double in(String unitSymbol) {
        return in(Units.require(unitSymbol));
    }

    private void requireSame(Quantity o, String op) {
        if (!dimension.equals(o.dimension)) {
            throw new UnitException("Cannot " + op + " quantities with different dimensions: "
                    + dimension + " and " + o.dimension);
        }
    }
}
