package com.scientia.core.units;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Registry of supported units. All factors are exact SI definitions unless noted. */
public final class Units {
    private static final Map<String, Unit> BY_SYMBOL = new LinkedHashMap<>();

    public static final Dimension AREA = Dimension.L.pow(2);
    public static final Dimension VOLUME = Dimension.L.pow(3);
    public static final Dimension VELOCITY = Dimension.L.divide(Dimension.T);
    public static final Dimension ACCELERATION = VELOCITY.divide(Dimension.T);
    public static final Dimension FORCE = Dimension.M.multiply(ACCELERATION);
    public static final Dimension ENERGY = FORCE.multiply(Dimension.L);
    public static final Dimension POWER = ENERGY.divide(Dimension.T);
    public static final Dimension PRESSURE = FORCE.divide(AREA);
    public static final Dimension CHARGE = Dimension.I.multiply(Dimension.T);
    public static final Dimension VOLTAGE = POWER.divide(Dimension.I);
    public static final Dimension RESISTANCE = VOLTAGE.divide(Dimension.I);
    public static final Dimension DENSITY = Dimension.M.divide(VOLUME);
    public static final Dimension CONCENTRATION = Dimension.N.divide(VOLUME);
    public static final Dimension MOLAR_MASS = Dimension.M.divide(Dimension.N);
    public static final Dimension FREQUENCY = Dimension.NONE.divide(Dimension.T);
    public static final Dimension MOMENTUM = Dimension.M.multiply(VELOCITY);
    public static final Dimension HEAT_CAPACITY = ENERGY.divide(Dimension.THETA);
    public static final Dimension SPECIFIC_HEAT = HEAT_CAPACITY.divide(Dimension.M);

    static {
        // Length
        reg("m", "metre", Dimension.L, 1);
        reg("km", "kilometre", Dimension.L, 1e3);
        reg("cm", "centimetre", Dimension.L, 1e-2);
        reg("mm", "millimetre", Dimension.L, 1e-3);
        reg("um", "micrometre", Dimension.L, 1e-6);
        reg("nm", "nanometre", Dimension.L, 1e-9);
        reg("in", "inch", Dimension.L, 0.0254);
        reg("ft", "foot", Dimension.L, 0.3048);
        // Mass
        reg("kg", "kilogram", Dimension.M, 1);
        reg("g", "gram", Dimension.M, 1e-3);
        reg("mg", "milligram", Dimension.M, 1e-6);
        reg("t", "tonne", Dimension.M, 1e3);
        // Time
        reg("s", "second", Dimension.T, 1);
        reg("ms", "millisecond", Dimension.T, 1e-3);
        reg("min", "minute", Dimension.T, 60);
        reg("h", "hour", Dimension.T, 3600);
        // Area / volume
        reg("m2", "square metre", AREA, 1);
        reg("cm2", "square centimetre", AREA, 1e-4);
        reg("mm2", "square millimetre", AREA, 1e-6);
        reg("m3", "cubic metre", VOLUME, 1);
        reg("cm3", "cubic centimetre", VOLUME, 1e-6);
        reg("L", "litre", VOLUME, 1e-3);
        reg("mL", "millilitre", VOLUME, 1e-6);
        // Temperature (affine scales)
        reg("K", "kelvin", Dimension.THETA, 1);
        reg("degC", "degree Celsius", Dimension.THETA, 1, 273.15);
        reg("degF", "degree Fahrenheit", Dimension.THETA, 5.0 / 9.0, 273.15 - 32 * 5.0 / 9.0);
        // Amount
        reg("mol", "mole", Dimension.N, 1);
        reg("mmol", "millimole", Dimension.N, 1e-3);
        // Electricity
        reg("A", "ampere", Dimension.I, 1);
        reg("mA", "milliampere", Dimension.I, 1e-3);
        reg("V", "volt", VOLTAGE, 1);
        reg("mV", "millivolt", VOLTAGE, 1e-3);
        reg("kV", "kilovolt", VOLTAGE, 1e3);
        reg("ohm", "ohm", RESISTANCE, 1);
        reg("kohm", "kilohm", RESISTANCE, 1e3);
        reg("C", "coulomb", CHARGE, 1);
        reg("Ah", "ampere-hour", CHARGE, 3600);
        reg("mAh", "milliampere-hour", CHARGE, 3.6);
        // Mechanics
        reg("N", "newton", FORCE, 1);
        reg("kN", "kilonewton", FORCE, 1e3);
        reg("J", "joule", ENERGY, 1);
        reg("kJ", "kilojoule", ENERGY, 1e3);
        reg("MJ", "megajoule", ENERGY, 1e6);
        reg("cal", "thermochemical calorie", ENERGY, 4.184);
        reg("kcal", "kilocalorie", ENERGY, 4184);
        reg("Wh", "watt-hour", ENERGY, 3600);
        reg("kWh", "kilowatt-hour", ENERGY, 3.6e6);
        reg("eV", "electronvolt", ENERGY, 1.602176634e-19);
        reg("W", "watt", POWER, 1);
        reg("kW", "kilowatt", POWER, 1e3);
        reg("MW", "megawatt", POWER, 1e6);
        reg("Pa", "pascal", PRESSURE, 1);
        reg("kPa", "kilopascal", PRESSURE, 1e3);
        reg("MPa", "megapascal", PRESSURE, 1e6);
        reg("bar", "bar", PRESSURE, 1e5);
        reg("atm", "standard atmosphere", PRESSURE, 101325);
        reg("mmHg", "millimetre of mercury", PRESSURE, 133.322387415);
        reg("Hz", "hertz", FREQUENCY, 1);
        reg("kHz", "kilohertz", FREQUENCY, 1e3);
        // Derived convenience units
        reg("m/s", "metre per second", VELOCITY, 1);
        reg("km/h", "kilometre per hour", VELOCITY, 1000.0 / 3600.0);
        reg("m/s2", "metre per second squared", ACCELERATION, 1);
        reg("g/cm3", "gram per cubic centimetre", DENSITY, 1000);
        reg("g/mL", "gram per millilitre", DENSITY, 1000);
        reg("kg/m3", "kilogram per cubic metre", DENSITY, 1);
        reg("g/L", "gram per litre", DENSITY, 1);
        reg("M", "mole per litre (molar)", CONCENTRATION, 1000);
        reg("mol/L", "mole per litre", CONCENTRATION, 1000);
        reg("g/mol", "gram per mole", MOLAR_MASS, 1e-3);
        reg("J/K", "joule per kelvin", HEAT_CAPACITY, 1);
        reg("J/g", "joule per gram", ENERGY.divide(Dimension.M), 1000);
        reg("kJ/mol", "kilojoule per mole", ENERGY.divide(Dimension.N), 1000);
        reg("J/(g*K)", "joule per gram kelvin", SPECIFIC_HEAT, 1000);
        reg("kg*m/s", "kilogram metre per second", MOMENTUM, 1);
        reg("ohm*m", "ohm metre", RESISTANCE.multiply(Dimension.L), 1);
        reg("W/K", "watt per kelvin", POWER.divide(Dimension.THETA), 1);
        reg("mol/kg", "mole per kilogram (molal)", Dimension.N.divide(Dimension.M), 1);
        reg("K*kg/mol", "kelvin kilogram per mole", Dimension.THETA.multiply(Dimension.M).divide(Dimension.N), 1);
        reg("1", "dimensionless", Dimension.NONE, 1);
        reg("%", "percent", Dimension.NONE, 0.01);
    }

    private Units() {
    }

    private static void reg(String symbol, String name, Dimension d, double factor) {
        reg(symbol, name, d, factor, 0);
    }

    private static void reg(String symbol, String name, Dimension d, double factor, double offset) {
        BY_SYMBOL.put(symbol, new Unit(symbol, name, d, factor, offset));
    }

    public static Unit get(String symbol) {
        Unit u = BY_SYMBOL.get(symbol);
        if (u == null) u = BY_SYMBOL.get(normalise(symbol));
        return u;
    }

    public static Unit require(String symbol) {
        Unit u = get(symbol);
        if (u == null) throw new UnitException("Unknown unit '" + symbol + "'");
        return u;
    }

    public static boolean exists(String symbol) {
        return get(symbol) != null;
    }

    public static Collection<Unit> all() {
        return Collections.unmodifiableCollection(BY_SYMBOL.values());
    }

    /** Accepts common typographic variants (°C, Ω, µm, m³...). */
    private static String normalise(String s) {
        return s.replace("°C", "degC").replace("°F", "degF").replace("Ω", "ohm").replace("kΩ", "kohm")
                .replace("µ", "u").replace("μ", "u").replace("³", "3").replace("²", "2").replace("·", "*");
    }

    /** Converts a value between two units of the same dimension. */
    public static double convert(double value, Unit from, Unit to) {
        if (!from.dimension().equals(to.dimension())) {
            throw new UnitException("Cannot convert " + from.symbol() + " (" + from.dimension() + ") to "
                    + to.symbol() + " (" + to.dimension() + "): different dimensions");
        }
        return to.fromSI(from.toSI(value));
    }

    public static double convert(double value, String from, String to) {
        return convert(value, require(from), require(to));
    }

    /** Finds the most natural registered unit symbol for an SI dimension, or null. */
    public static Unit preferredFor(Dimension d) {
        for (Unit u : BY_SYMBOL.values()) {
            if (u.dimension().equals(d) && u.factor() == 1.0 && u.offset() == 0.0) return u;
        }
        return null;
    }
}
