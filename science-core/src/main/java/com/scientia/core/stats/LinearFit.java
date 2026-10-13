package com.scientia.core.stats;

/**
 * Ordinary least-squares straight-line fit y = slope·x + intercept, with standard errors and the
 * coefficient of determination R². Requires at least two points with distinct x values.
 */
public record LinearFit(double slope, double intercept, double rSquared, double slopeError, double interceptError, int n) {

    public static LinearFit of(double[] x, double[] y) {
        int n = x.length;
        if (n != y.length) throw new IllegalArgumentException("x and y must have equal length");
        if (n < 2) return null;
        double mx = 0, my = 0;
        for (int i = 0; i < n; i++) {
            mx += x[i];
            my += y[i];
        }
        mx /= n;
        my /= n;
        double sxx = 0, sxy = 0, syy = 0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - mx, dy = y[i] - my;
            sxx += dx * dx;
            sxy += dx * dy;
            syy += dy * dy;
        }
        if (sxx == 0) return null;
        double slope = sxy / sxx;
        double intercept = my - slope * mx;
        double ssRes = 0;
        for (int i = 0; i < n; i++) {
            double r = y[i] - (slope * x[i] + intercept);
            ssRes += r * r;
        }
        double r2 = syy == 0 ? 1.0 : 1.0 - ssRes / syy;
        double slopeErr = 0, interceptErr = 0;
        if (n > 2) {
            double s2 = ssRes / (n - 2);
            slopeErr = Math.sqrt(s2 / sxx);
            double sumX2 = 0;
            for (double xi : x) sumX2 += xi * xi;
            interceptErr = Math.sqrt(s2 * sumX2 / (n * sxx));
        }
        return new LinearFit(slope, intercept, r2, slopeErr, interceptErr, n);
    }

    public double predict(double x) {
        return slope * x + intercept;
    }
}
