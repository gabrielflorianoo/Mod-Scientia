package com.scientia.core;

import com.scientia.core.units.Measurement;
import com.scientia.core.units.Quantity;
import com.scientia.core.units.UnitException;
import com.scientia.core.units.Units;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnitsTest {
    @Test
    void temperatureScales() {
        assertEquals(298.15, Units.convert(25, "degC", "K"), 1e-9);
        assertEquals(-40, Units.convert(-40, "degC", "degF"), 1e-9);
        assertEquals(212, Units.convert(100, "degC", "degF"), 1e-9);
        assertEquals(0, Units.convert(273.15, "K", "degC"), 1e-9);
    }

    @Test
    void commonConversions() {
        assertEquals(27.7777778, Units.convert(100, "km/h", "m/s"), 1e-6);
        assertEquals(3.6e6, Units.convert(1, "kWh", "J"), 1e-6);
        assertEquals(1000, Units.convert(1, "L", "mL"), 1e-9);
        assertEquals(1e-3, Units.convert(1, "L", "m3"), 1e-15);
        assertEquals(101.325, Units.convert(1, "atm", "kPa"), 1e-9);
        assertEquals(7874, Units.convert(7.874, "g/cm3", "kg/m3"), 1e-9);
        assertEquals(4.184, Units.convert(1, "cal", "J"), 1e-12);
        assertEquals(2.5, Units.convert(2500, "g", "kg"), 1e-12);
    }

    @Test
    void dimensionMismatchRejected() {
        assertThrows(UnitException.class, () -> Units.convert(1, "m", "kg"));
        assertThrows(UnitException.class, () -> Quantity.of(1, "m").add(Quantity.of(1, "kg")));
    }

    @Test
    void derivedDimensionsAreConsistent() {
        assertEquals(Units.ENERGY, Units.POWER.multiply(com.scientia.core.units.Dimension.T));
        assertEquals(Units.VOLTAGE, Units.RESISTANCE.multiply(com.scientia.core.units.Dimension.I));
        Quantity v = Quantity.of(12, "V"), i = Quantity.of(0.5, "A");
        assertEquals(Units.POWER, v.multiply(i).dimension());
        assertEquals(24, v.divide(i).in("ohm"), 1e-12);
    }

    @Test
    void uncertaintyPropagation() {
        Measurement m = new Measurement(100.00, 0.01, "g");
        Measurement v = new Measurement(12.7, 0.2, "mL");
        Measurement rho = m.dividedBy(v, "g/mL");
        assertEquals(7.874, rho.value(), 1e-3);
        assertEquals(7.874 * 0.2 / 12.7, rho.uncertainty(), 2e-3);
        assertTrue(rho.format().startsWith("7.9 ± 0.1"), rho.format());
        Measurement diff = new Measurement(10, 0.3, "g").minus(new Measurement(4, 0.4, "g"));
        assertEquals(0.5, diff.uncertainty(), 1e-12);
    }
}
