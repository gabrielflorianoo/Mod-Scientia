package com.scientia.core.physics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DC circuit model solved by nodal analysis. Every component is a two-terminal element between nodes
 * {@code a} and {@code b}. Voltage sources are modelled with their internal resistance (a Thévenin
 * source converted to its exact Norton equivalent), so short circuits produce large but finite currents
 * rather than singular equations.
 */
public final class Circuit {

    public enum Kind {RESISTOR, SOURCE}

    /**
     * @param resistance ohms (for a source: the internal resistance)
     * @param emf        volts, sources only (positive terminal is {@code a})
     */
    public record Component(int id, Kind kind, int a, int b, double resistance, double emf, Object tag) {
    }

    /** Solution of the network: node potentials and per-component current, voltage and power. */
    public static final class Solution {
        private final double[] potentials;
        private final List<Component> components;

        Solution(double[] potentials, List<Component> components) {
            this.potentials = potentials;
            this.components = components;
        }

        public double potential(int node) {
            return potentials[node];
        }

        /** Voltage across the component, V(a) − V(b). */
        public double voltage(Component c) {
            return potentials[c.a()] - potentials[c.b()];
        }

        /**
         * Current through the component in amperes. For a resistor, positive means flowing from a to b.
         * For a source, positive means the source is delivering current out of its positive terminal (a).
         */
        public double current(Component c) {
            double v = voltage(c);
            if (c.kind() == Kind.RESISTOR) return v / c.resistance();
            return (c.emf() - v) / c.resistance();
        }

        /** Power in watts: dissipated by a resistor, or delivered by the EMF of a source (EMF × current). */
        public double power(Component c) {
            double i = current(c);
            if (c.kind() == Kind.RESISTOR) return i * i * c.resistance();
            return c.emf() * i;
        }

        public List<Component> components() {
            return components;
        }
    }

    /** Conductance tying every node weakly to ground so floating sub-networks stay solvable (1 nS). */
    public static final double GMIN = 1e-9;

    private final List<Component> components = new ArrayList<>();
    private int nodeCount;

    public int addNode() {
        return nodeCount++;
    }

    public int nodeCount() {
        return nodeCount;
    }

    public Component resistor(int a, int b, double ohms, Object tag) {
        if (!(ohms > 0)) throw new IllegalArgumentException("Resistance must be positive");
        return add(new Component(components.size(), Kind.RESISTOR, a, b, ohms, 0, tag));
    }

    public Component source(int positive, int negative, double emf, double internalOhms, Object tag) {
        if (!(internalOhms > 0)) throw new IllegalArgumentException("Internal resistance must be positive");
        return add(new Component(components.size(), Kind.SOURCE, positive, negative, internalOhms, emf, tag));
    }

    private Component add(Component c) {
        if (c.a() < 0 || c.b() < 0 || c.a() >= nodeCount || c.b() >= nodeCount) throw new IllegalArgumentException("Bad node index");
        components.add(c);
        return c;
    }

    public List<Component> components() {
        return Collections.unmodifiableList(components);
    }

    /** Solves the network with {@code ground} held at 0 V. */
    public Solution solve(int ground) {
        int n = nodeCount;
        double[][] g = new double[n][n];
        double[] rhs = new double[n];
        for (int i = 0; i < n; i++) g[i][i] += GMIN;
        for (Component c : components) {
            double cond = 1.0 / c.resistance();
            stamp(g, c.a(), c.b(), cond);
            if (c.kind() == Kind.SOURCE) {
                double in = c.emf() * cond; // Norton current injected into the positive node
                rhs[c.a()] += in;
                rhs[c.b()] -= in;
            }
        }
        // Replace the ground row with V(ground) = 0
        for (int j = 0; j < n; j++) g[ground][j] = 0;
        g[ground][ground] = 1;
        rhs[ground] = 0;
        double[] v = gauss(g, rhs);
        return new Solution(v, List.copyOf(components));
    }

    private static void stamp(double[][] g, int a, int b, double cond) {
        g[a][a] += cond;
        g[b][b] += cond;
        g[a][b] -= cond;
        g[b][a] -= cond;
    }

    /** Gaussian elimination with partial pivoting. */
    static double[] gauss(double[][] a, double[] b) {
        int n = b.length;
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int r = col + 1; r < n; r++) if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) pivot = r;
            double[] tmp = a[col];
            a[col] = a[pivot];
            a[pivot] = tmp;
            double t = b[col];
            b[col] = b[pivot];
            b[pivot] = t;
            double p = a[col][col];
            if (Math.abs(p) < 1e-300) continue;
            for (int r = col + 1; r < n; r++) {
                double f = a[r][col] / p;
                if (f == 0) continue;
                for (int k = col; k < n; k++) a[r][k] -= f * a[col][k];
                b[r] -= f * b[col];
            }
        }
        double[] x = new double[n];
        for (int r = n - 1; r >= 0; r--) {
            double s = b[r];
            for (int k = r + 1; k < n; k++) s -= a[r][k] * x[k];
            x[r] = Math.abs(a[r][r]) < 1e-300 ? 0 : s / a[r][r];
        }
        return x;
    }

    /** Equivalent resistance of resistors in series: R = R1 + R2 + ... */
    public static double series(double... r) {
        double s = 0;
        for (double x : r) s += x;
        return s;
    }

    /** Equivalent resistance of resistors in parallel: 1/R = 1/R1 + 1/R2 + ... */
    public static double parallel(double... r) {
        double s = 0;
        for (double x : r) s += 1.0 / x;
        return 1.0 / s;
    }

    /** Resistance of a uniform conductor: R = ρL/A (ρ in Ω·m, L in m, A in m²). */
    public static double wireResistance(double resistivity, double length, double area) {
        return resistivity * length / area;
    }
}
