package com.scientia.core.physics;

import java.util.Map;

/** Reference material properties near 20 °C used by the electrical and thermal models. */
public final class Materials {
    private Materials() {
    }

    /** Electrical resistivity in Ω·m at 20 °C. */
    public static final Map<String, Double> RESISTIVITY = Map.of(
            "silver", 1.59e-8,
            "copper", 1.68e-8,
            "gold", 2.44e-8,
            "aluminium", 2.65e-8,
            "iron", 9.71e-8,
            "graphite", 1.0e-5,     // varies greatly with direction and form; order of magnitude only
            "glass", 1.0e11,        // insulator; typical order of magnitude
            "rubber", 1.0e13        // insulator; typical order of magnitude
    );

    /** Thermal conductivity in W/(m·K) near room temperature. */
    public static final Map<String, Double> THERMAL_CONDUCTIVITY = Map.of(
            "copper", 401.0,
            "iron", 80.4,
            "glass", 1.0,
            "brick", 0.7,           // fired clay brick, typical
            "stone", 2.0,           // typical granite-like rock, varies
            "wood", 0.15,           // typical, varies with species and grain direction
            "wool", 0.04,           // loose wool insulation, typical
            "air", 0.026,
            "water", 0.6
    );

    /** Specific heat capacity in J/(g·K). */
    public static final Map<String, Double> SPECIFIC_HEAT = Map.of(
            "water", 4.18,
            "ice", 2.09,
            "glass", 0.84,
            "iron", 0.449,
            "copper", 0.385,
            "brick", 0.84,
            "stone", 0.79
    );

    /** Copper wire used by the in-game wire block: 1 m long, 1 mm² cross-section. */
    public static double copperWireBlockResistance() {
        return Circuit.wireResistance(RESISTIVITY.get("copper"), 1.0, 1e-6);
    }
}
