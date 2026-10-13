package com.scientia.core.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An ordered list of (x, y) data points with optional y-uncertainties and axis metadata. */
public final class DataSeries {
    public record Point(double x, double y, double yUncertainty) {
    }

    private final String xLabel, xUnit, yLabel, yUnit;
    private final int capacity;
    private final List<Point> points = new ArrayList<>();

    public DataSeries(String xLabel, String xUnit, String yLabel, String yUnit, int capacity) {
        this.xLabel = xLabel;
        this.xUnit = xUnit;
        this.yLabel = yLabel;
        this.yUnit = yUnit;
        this.capacity = capacity;
    }

    /** Adds a point; when full, the oldest point is discarded (ring-buffer behaviour). */
    public void add(double x, double y, double yUncertainty) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) return;
        if (points.size() >= capacity) points.remove(0);
        points.add(new Point(x, y, Math.abs(yUncertainty)));
    }

    public void clear() {
        points.clear();
    }

    public List<Point> points() {
        return Collections.unmodifiableList(points);
    }

    public int size() {
        return points.size();
    }

    public String xLabel() {
        return xLabel;
    }

    public String xUnit() {
        return xUnit;
    }

    public String yLabel() {
        return yLabel;
    }

    public String yUnit() {
        return yUnit;
    }

    public int capacity() {
        return capacity;
    }

    /** Returns {minX, maxX, minY, maxY}, padded so that a flat series still has a visible range. */
    public double[] bounds() {
        if (points.isEmpty()) return new double[]{0, 1, 0, 1};
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (Point p : points) {
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y() - p.yUncertainty());
            maxY = Math.max(maxY, p.y() + p.yUncertainty());
        }
        if (maxX - minX < 1e-9) {
            minX -= 0.5;
            maxX += 0.5;
        }
        double padY = Math.max((maxY - minY) * 0.08, Math.max(Math.abs(maxY) * 1e-3, 1e-6));
        return new double[]{minX, maxX, minY - padY, maxY + padY};
    }

    public LinearFit fit() {
        double[] xs = new double[points.size()];
        double[] ys = new double[points.size()];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = points.get(i).x();
            ys[i] = points.get(i).y();
        }
        return LinearFit.of(xs, ys);
    }

    /** "Nice" axis tick spacing (1, 2 or 5 × 10^n) for a range split into roughly {@code targetTicks} parts. */
    public static double niceStep(double range, int targetTicks) {
        if (range <= 0 || !Double.isFinite(range)) return 1;
        double raw = range / Math.max(1, targetTicks);
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double norm = raw / mag;
        double nice = norm < 1.5 ? 1 : norm < 3 ? 2 : norm < 7 ? 5 : 10;
        return nice * mag;
    }
}
