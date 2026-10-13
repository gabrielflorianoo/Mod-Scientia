package com.scientia.core;

import com.scientia.core.physics.Circuit;
import com.scientia.core.physics.Gas;
import com.scientia.core.physics.Materials;
import com.scientia.core.physics.Mechanics;
import com.scientia.core.physics.Optics;
import com.scientia.core.physics.ThermalBody;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhysicsTest {
    @Test
    void ohmsLawSingleResistor() {
        Circuit c = new Circuit();
        int a = c.addNode(), b = c.addNode();
        Circuit.Component bat = c.source(a, b, 12, 1e-6, "bat");
        Circuit.Component r = c.resistor(a, b, 24, "lamp");
        Circuit.Solution s = c.solve(b);
        assertEquals(0.5, s.current(r), 1e-6);
        assertEquals(12, s.voltage(r), 1e-5);
        assertEquals(6, s.power(r), 1e-4);
        assertEquals(0.5, s.current(bat), 1e-6);
    }

    @Test
    void seriesAndParallel() {
        // Two 24 Ω lamps in series on a 12 V source with 0.1 Ω internal resistance
        Circuit c = new Circuit();
        int p = c.addNode(), m = c.addNode(), n = c.addNode();
        Circuit.Component bat = c.source(p, n, 12, 0.1, "bat");
        Circuit.Component l1 = c.resistor(p, m, 24, "l1");
        Circuit.Component l2 = c.resistor(m, n, 24, "l2");
        Circuit.Solution s = c.solve(n);
        double i = 12 / (48 + 0.1);
        assertEquals(i, s.current(l1), 1e-6);
        assertEquals(s.current(l1), s.current(l2), 1e-7); // GMIN leakage is ~1e-9 S
        assertEquals(i, s.current(bat), 1e-6);

        Circuit q = new Circuit();
        int p2 = q.addNode(), n2 = q.addNode();
        Circuit.Component bat2 = q.source(p2, n2, 12, 0.1, "bat");
        Circuit.Component a = q.resistor(p2, n2, 24, "a");
        Circuit.Component b = q.resistor(p2, n2, 24, "b");
        Circuit.Solution t = q.solve(n2);
        double itot = 12 / (12 + 0.1);
        assertEquals(itot, t.current(bat2), 1e-6);
        assertEquals(itot / 2, t.current(a), 1e-6);
        assertEquals(t.current(a), t.current(b), 1e-9);
        assertTrue(t.current(bat2) > s.current(bat), "parallel draws more current");
        assertEquals(12, Circuit.parallel(24, 24), 1e-12);
        assertEquals(48, Circuit.series(24, 24), 1e-12);
    }

    @Test
    void shortCircuitIsFiniteAndOpenCircuitCarriesNoCurrent() {
        Circuit c = new Circuit();
        int a = c.addNode(), b = c.addNode();
        Circuit.Component bat = c.source(a, b, 12, 0.05, "bat");
        c.resistor(a, b, Materials.copperWireBlockResistance(), "wire");
        Circuit.Solution s = c.solve(b);
        assertTrue(Double.isFinite(s.current(bat)));
        assertEquals(12 / (0.05 + 0.0168), s.current(bat), 0.5);

        Circuit o = new Circuit();
        int x = o.addNode(), y = o.addNode(), z = o.addNode();
        Circuit.Component bat2 = o.source(x, y, 12, 0.05, "bat");
        Circuit.Component lamp = o.resistor(x, z, 24, "lamp"); // z is floating: open circuit
        Circuit.Solution u = o.solve(y);
        assertEquals(0, u.current(lamp), 1e-6);
        assertEquals(0, u.current(bat2), 1e-6);
    }

    @Test
    void copperWireResistance() {
        assertEquals(0.0168, Materials.copperWireBlockResistance(), 1e-6);
    }

    @Test
    void heatingWaterAndBoilingPlateau() {
        ThermalBody body = new ThermalBody(293.15, 100, 0, 0);
        double t = 0;
        while (body.temperatureK() < 373.15 - 1e-9 && t < 10000) {
            body.step(100, 293.15, 0, ThermalBody.BOILING_POINT, 0.5);
            t += 0.5;
        }
        assertEquals(100 * 4.18 * 80 / 100, t, 1.0); // Q = m c ΔT at 100 W
        // Continued heating: temperature stays at the boiling point while water evaporates
        double vaporised = 0;
        for (int i = 0; i < 100; i++) vaporised += body.step(100, 293.15, 0, ThermalBody.BOILING_POINT, 1).vaporisedGrams();
        assertEquals(373.15, body.temperatureK(), 1e-9);
        assertEquals(100 * 100 / 2257.0, vaporised, 1e-6);
    }

    @Test
    void meltingIcePlateau() {
        ThermalBody body = new ThermalBody(263.15, 0, 50, 0);
        for (int i = 0; i < 2000; i++) body.step(10, 293.15, 0, ThermalBody.BOILING_POINT, 0.5);
        // energy 10 kJ: warm ice 10 K (1045 J), then melt 50 g (16700 J) needs more: still melting
        assertEquals(273.15, body.temperatureK(), 1e-9);
        assertEquals((10000 - 50 * 2.09 * 10) / 334.0, body.waterGrams(), 1e-6);
    }

    @Test
    void insulationSlowsCooling() {
        ThermalBody bare = new ThermalBody(343.15, 200, 0, 84);
        ThermalBody wool = new ThermalBody(343.15, 200, 0, 84);
        for (int i = 0; i < 600; i++) {
            bare.step(0, 293.15, 0.8, 373.15, 1);
            wool.step(0, 293.15, 0.2, 373.15, 1);
        }
        assertTrue(wool.temperatureK() > bare.temperatureK());
        // Exact exponential decay for the lumped model
        double c = 200 * 4.18 + 84;
        assertEquals(293.15 + 50 * Math.exp(-0.8 * 600 / c), bare.temperatureK(), 0.2);
    }

    @Test
    void boilingPointElevation() {
        // Seawater: ~0.6 mol NaCl per kg, i = 2
        assertEquals(373.15 + 0.512 * 1.2, ThermalBody.boilingPointOfSolution(1.2), 1e-9);
    }

    @Test
    void inclineMotion() {
        Mechanics.InclineRun run = Mechanics.incline(1.0, 30, 0, 2.0, 0.05);
        assertEquals(9.80665 * 0.5, run.acceleration(), 1e-9);
        assertEquals(1.0, run.efficiency(), 1e-9); // frictionless: all PE becomes KE
        Mechanics.InclineRun rough = Mechanics.incline(1.0, 30, 0.2, 2.0, 0.05);
        assertTrue(rough.efficiency() < 1);
        assertEquals(rough.potentialEnergyLost(), rough.kineticEnergyGained() + rough.frictionWork(), 1e-9);
        assertFalse(Mechanics.incline(1, 5, 0.3, 2, 0.05).moves());
    }

    @Test
    void optics() {
        assertEquals(0.15, Optics.imageDistance(0.10, 0.30), 1e-12);
        assertEquals(-0.5, Optics.magnification(0.30, 0.15), 1e-12);
        assertEquals(32.03, Optics.refractionAngle(1.0, 1.333, 45), 0.01);
        assertTrue(Double.isNaN(Optics.refractionAngle(1.517, 1.0, 60)));
        assertEquals(41.24, Optics.criticalAngle(1.517, 1.0), 0.05);
        assertTrue(Optics.crownGlassIndex(400) > Optics.crownGlassIndex(700)); // normal dispersion
        assertEquals(1.517, Optics.crownGlassIndex(589), 0.003);
    }

    @Test
    void idealGas() {
        assertEquals(0.022414, Gas.volume(1, 273.15, 101325), 1e-5);
        // Ideal-gas value for CO2 at STP; the measured value (1.977 g/L) is ~0.7 % higher because CO2 is not ideal.
        assertEquals(1.9635, Gas.densityGPerL(44.01, 101325, 273.15), 0.001);
    }
}
