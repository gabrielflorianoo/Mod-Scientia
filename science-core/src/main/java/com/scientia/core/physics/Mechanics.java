package com.scientia.core.physics;

import com.scientia.core.math.Constants;
import com.scientia.core.stats.DataSeries;

/** Classical mechanics helpers used by experiments and machines. */
public final class Mechanics {
    private Mechanics() {
    }

    public static double kineticEnergy(double massKg, double speed) {
        return 0.5 * massKg * speed * speed;
    }

    /** Gravitational potential energy near Earth's surface (uniform field): E = m g h. */
    public static double potentialEnergy(double massKg, double heightM) {
        return massKg * Constants.STANDARD_GRAVITY * heightM;
    }

    public static double momentum(double massKg, double velocity) {
        return massKg * velocity;
    }

    /**
     * Result of a cart released from rest on an inclined track with kinetic friction.
     * a = g(sin θ − μ cos θ); the cart stays at rest if tan θ ≤ μ_s (static friction assumed equal to μ).
     */
    public record InclineRun(double acceleration, boolean moves, double timeToEnd, double finalSpeed,
                             double potentialEnergyLost, double kineticEnergyGained, double frictionWork,
                             DataSeries distanceVsTime, DataSeries speedVsTime) {
        /** Fraction of potential energy converted to kinetic energy. */
        public double efficiency() {
            return potentialEnergyLost <= 0 ? 0 : kineticEnergyGained / potentialEnergyLost;
        }
    }

    public static InclineRun incline(double massKg, double angleDeg, double mu, double lengthM, double sampleIntervalS) {
        double theta = Math.toRadians(angleDeg);
        double g = Constants.STANDARD_GRAVITY;
        double a = g * (Math.sin(theta) - mu * Math.cos(theta));
        DataSeries dist = new DataSeries("time", "s", "distance", "m", 512);
        DataSeries speed = new DataSeries("time", "s", "speed", "m/s", 512);
        double height = lengthM * Math.sin(theta);
        if (a <= 0 || Math.tan(theta) <= mu) {
            dist.add(0, 0, 0);
            speed.add(0, 0, 0);
            return new InclineRun(0, false, Double.POSITIVE_INFINITY, 0, 0, 0, 0, dist, speed);
        }
        double tEnd = Math.sqrt(2 * lengthM / a);
        double interval = Math.max(sampleIntervalS, tEnd / 400);
        for (double t = 0; t < tEnd; t += interval) {
            dist.add(t, 0.5 * a * t * t, 0);
            speed.add(t, a * t, 0);
        }
        dist.add(tEnd, lengthM, 0);
        speed.add(tEnd, a * tEnd, 0);
        double v = a * tEnd;
        double pe = massKg * g * height;
        double ke = kineticEnergy(massKg, v);
        double friction = mu * massKg * g * Math.cos(theta) * lengthM;
        return new InclineRun(a, true, tEnd, v, pe, ke, friction, dist, speed);
    }

    /** Efficiency of a simple machine: useful work out / work in. */
    public static double efficiency(double workOut, double workIn) {
        return workIn <= 0 ? 0 : workOut / workIn;
    }
}
