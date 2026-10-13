package com.scientia.core.math;

/** An exact rational number, used to show results such as 0.75 as 3/4. */
public record Fraction(long numerator, long denominator) {

    public Fraction {
        if (denominator == 0) throw new MathException("Division by zero in fraction");
        if (denominator < 0) {
            numerator = -numerator;
            denominator = -denominator;
        }
        long g = gcd(Math.abs(numerator), denominator);
        if (g > 1) {
            numerator /= g;
            denominator /= g;
        }
    }

    static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a == 0 ? 1 : a;
    }

    public double value() {
        return (double) numerator / denominator;
    }

    public Fraction add(Fraction o) {
        return new Fraction(Math.addExact(Math.multiplyExact(numerator, o.denominator), Math.multiplyExact(o.numerator, denominator)),
                Math.multiplyExact(denominator, o.denominator));
    }

    public Fraction multiply(Fraction o) {
        return new Fraction(Math.multiplyExact(numerator, o.numerator), Math.multiplyExact(denominator, o.denominator));
    }

    /**
     * Best rational approximation via continued fractions. Returns null when no fraction with
     * denominator up to {@code maxDenominator} matches within {@code tolerance}.
     */
    public static Fraction approximate(double x, long maxDenominator, double tolerance) {
        if (!Double.isFinite(x)) return null;
        long sign = x < 0 ? -1 : 1;
        double v = Math.abs(x);
        long h0 = 0, h1 = 1, k0 = 1, k1 = 0;
        double r = v;
        for (int i = 0; i < 64; i++) {
            long a = (long) Math.floor(r);
            long h2 = a * h1 + h0, k2 = a * k1 + k0;
            if (k2 > maxDenominator) break;
            h0 = h1; h1 = h2; k0 = k1; k1 = k2;
            if (Math.abs(v - (double) h1 / k1) <= tolerance * Math.max(1, v)) {
                return new Fraction(sign * h1, k1);
            }
            double frac = r - a;
            if (frac < 1e-15) break;
            r = 1 / frac;
        }
        if (k1 != 0 && Math.abs(v - (double) h1 / k1) <= tolerance * Math.max(1, v)) return new Fraction(sign * h1, k1);
        return null;
    }

    @Override
    public String toString() {
        return denominator == 1 ? Long.toString(numerator) : numerator + "/" + denominator;
    }
}
