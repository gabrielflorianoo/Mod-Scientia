package com.scientia.core.math;

import com.scientia.core.units.Dimension;
import com.scientia.core.units.Quantity;
import com.scientia.core.units.Units;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Physical constants. Values marked "exact" are exact by definition of the SI (2019 redefinition);
 * the others are CODATA 2018 recommended values.
 */
public final class Constants {
    public static final double PI = Math.PI;
    public static final double SPEED_OF_LIGHT = 299_792_458.0;          // m/s, exact
    public static final double STANDARD_GRAVITY = 9.80665;             // m/s^2, exact (conventional)
    public static final double AVOGADRO = 6.02214076e23;               // 1/mol, exact
    public static final double BOLTZMANN = 1.380649e-23;               // J/K, exact
    public static final double ELEMENTARY_CHARGE = 1.602176634e-19;    // C, exact
    public static final double PLANCK = 6.62607015e-34;                // J·s, exact
    public static final double GAS_CONSTANT = AVOGADRO * BOLTZMANN;    // 8.314462618... J/(mol·K), exact
    public static final double FARADAY = AVOGADRO * ELEMENTARY_CHARGE; // 96485.33212... C/mol, exact
    public static final double GRAVITATIONAL = 6.67430e-11;            // m^3/(kg·s^2), CODATA 2018
    public static final double VACUUM_PERMITTIVITY = 8.8541878128e-12; // F/m, CODATA 2018
    public static final double STANDARD_PRESSURE = 101_325.0;          // Pa (1 atm), exact
    public static final double STEFAN_BOLTZMANN = 5.670374419e-8;      // W/(m^2·K^4), derived from exact constants
    /** Ion product of water at 25 °C (approximately 1.0e-14; varies strongly with temperature). */
    public static final double KW_25C = 1.0e-14;

    public record Constant(String symbol, String name, Quantity value, String note) {
    }

    private static final Map<String, Constant> TABLE = new LinkedHashMap<>();

    static {
        put("pi", "π", Quantity.dimensionless(PI), "mathematical constant");
        put("e", "Euler's number", Quantity.dimensionless(Math.E), "mathematical constant");
        put("c", "speed of light in vacuum", new Quantity(SPEED_OF_LIGHT, Units.VELOCITY), "exact");
        put("g0", "standard acceleration of gravity", new Quantity(STANDARD_GRAVITY, Units.ACCELERATION), "exact by convention");
        put("NA", "Avogadro constant", new Quantity(AVOGADRO, Dimension.NONE.divide(Dimension.N)), "exact");
        put("kB", "Boltzmann constant", new Quantity(BOLTZMANN, Units.HEAT_CAPACITY), "exact");
        put("R", "molar gas constant", new Quantity(GAS_CONSTANT, Units.HEAT_CAPACITY.divide(Dimension.N)), "exact");
        put("qe", "elementary charge", new Quantity(ELEMENTARY_CHARGE, Units.CHARGE), "exact");
        put("F", "Faraday constant", new Quantity(FARADAY, Units.CHARGE.divide(Dimension.N)), "exact");
        put("planck", "Planck constant", new Quantity(PLANCK, Units.ENERGY.multiply(Dimension.T)), "exact");
        put("G", "Newtonian constant of gravitation", new Quantity(GRAVITATIONAL, Units.FORCE.multiply(Units.AREA).divide(Dimension.M.pow(2))), "CODATA 2018");
        put("eps0", "vacuum electric permittivity", new Quantity(VACUUM_PERMITTIVITY, Units.CHARGE.pow(2).divide(Units.FORCE.multiply(Units.AREA))), "CODATA 2018");
        put("atm0", "standard atmosphere", new Quantity(STANDARD_PRESSURE, Units.PRESSURE), "exact");
        put("sigma", "Stefan–Boltzmann constant", new Quantity(STEFAN_BOLTZMANN, Units.POWER.divide(Units.AREA).divide(Dimension.THETA.pow(4))), "exact (derived)");
    }

    private Constants() {
    }

    private static void put(String symbol, String name, Quantity q, String note) {
        TABLE.put(symbol, new Constant(symbol, name, q, note));
    }

    public static Constant get(String symbol) {
        return TABLE.get(symbol);
    }

    public static Map<String, Constant> all() {
        return java.util.Collections.unmodifiableMap(TABLE);
    }
}
