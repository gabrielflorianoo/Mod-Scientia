package com.scientia.core;

import com.scientia.core.math.Fraction;
import com.scientia.core.math.SigFigs;
import com.scientia.core.stats.DataSeries;
import com.scientia.core.stats.LinearFit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MathUtilTest {
    @Test
    void significantFigures() {
        assertEquals(3, SigFigs.count("123"));
        assertEquals(3, SigFigs.count("0.00123"));
        assertEquals(4, SigFigs.count("1.230"));
        assertEquals(2, SigFigs.count("1200"));
        assertEquals(4, SigFigs.count("1200."));
        assertEquals(3, SigFigs.count("6.02e23"));
        assertEquals(5, SigFigs.count("10.005"));
        assertEquals(1234.6, SigFigs.round(1234.56, 5), 1e-9);
        assertEquals(0.00123, SigFigs.round(0.0012345, 3), 1e-12);
        assertEquals("1.23e-5", SigFigs.format(0.0000123, 3));
        assertEquals(2, SigFigs.forProduct(3, 2, 4));
        assertEquals(1, SigFigs.decimalsForSum("12.1", "3.456"));
        assertThrows(RuntimeException.class, () -> SigFigs.count("abc"));
    }

    @Test
    void fractions() {
        assertEquals("1/3", Fraction.approximate(1.0 / 3, 1000, 1e-12).toString());
        assertEquals("-22/7", Fraction.approximate(-22.0 / 7, 1000, 1e-12).toString());
        assertNull(Fraction.approximate(Math.PI, 1000, 1e-12));
        assertEquals("5/6", new Fraction(1, 2).add(new Fraction(1, 3)).toString());
        assertEquals("1/6", new Fraction(1, 2).multiply(new Fraction(1, 3)).toString());
    }

    @Test
    void linearFitRecoversLine() {
        double[] x = {0, 1, 2, 3, 4};
        double[] y = {1, 3, 5, 7, 9};
        LinearFit f = LinearFit.of(x, y);
        assertEquals(2, f.slope(), 1e-12);
        assertEquals(1, f.intercept(), 1e-12);
        assertEquals(1, f.rSquared(), 1e-12);
        assertNull(LinearFit.of(new double[]{1, 1}, new double[]{2, 3}));
    }

    @Test
    void dataSeriesRingBufferAndBounds() {
        DataSeries s = new DataSeries("t", "s", "T", "K", 3);
        for (int i = 0; i < 5; i++) s.add(i, 10 * i, 0);
        assertEquals(3, s.size());
        assertEquals(2, s.points().get(0).x());
        double[] b = s.bounds();
        assertTrue(b[2] < 20 && b[3] > 40);
        assertEquals(10, s.fit().slope(), 1e-12);
        assertEquals(2, DataSeries.niceStep(10, 5), 1e-12);
        assertEquals(0.5, DataSeries.niceStep(2.3, 5), 1e-12);
    }
}
