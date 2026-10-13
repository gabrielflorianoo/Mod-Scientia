package com.scientia.core.physics;

import com.scientia.core.math.Constants;

/** Ideal gas law PV = nRT, valid for gases at low pressure and well above their condensation point. */
public final class Gas {
    private Gas() {
    }

    public static double pressure(double moles, double temperatureK, double volumeM3) {
        return moles * Constants.GAS_CONSTANT * temperatureK / volumeM3;
    }

    public static double volume(double moles, double temperatureK, double pressurePa) {
        return moles * Constants.GAS_CONSTANT * temperatureK / pressurePa;
    }

    public static double moles(double pressurePa, double volumeM3, double temperatureK) {
        return pressurePa * volumeM3 / (Constants.GAS_CONSTANT * temperatureK);
    }

    /** Density in g/L of an ideal gas with molar mass M (g/mol): ρ = PM/(RT). */
    public static double densityGPerL(double molarMass, double pressurePa, double temperatureK) {
        return pressurePa * molarMass / (Constants.GAS_CONSTANT * temperatureK) / 1000.0;
    }
}
