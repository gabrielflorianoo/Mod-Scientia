package com.scientia.core.physics;

/**
 * Lumped-capacitance thermal model of a sample that may contain water, ice and other materials.
 * Each step applies the energy balance
 * <pre>Q = (P_in − UA·(T − T_env)) · Δt</pre>
 * and, when the temperature reaches 0 °C or the boiling point, routes energy into the latent heat of
 * fusion (334 J/g) or vaporisation (2257 J/g at 100 °C) instead of changing temperature. Convection,
 * conduction and radiation losses are folded into one overall heat-loss coefficient UA (W/K), which is a
 * standard engineering simplification valid for small temperature differences.
 */
public final class ThermalBody {
    public static final double WATER_C = 4.18, ICE_C = 2.09;   // J/(g·K)
    public static final double LATENT_FUSION = 334.0;          // J/g
    public static final double LATENT_VAPORISATION = 2257.0;   // J/g at 100 °C
    public static final double MELTING_POINT = 273.15;         // K at 1 atm
    public static final double BOILING_POINT = 373.15;         // K at 1 atm (pure water)
    public static final double EBULLIOSCOPIC_WATER = 0.512;    // K·kg/mol

    private double temperatureK;
    private double waterGrams;
    private double iceGrams;
    private double otherHeatCapacity; // J/K of non-water contents and the container

    public ThermalBody(double temperatureK, double waterGrams, double iceGrams, double otherHeatCapacity) {
        this.temperatureK = temperatureK;
        this.waterGrams = waterGrams;
        this.iceGrams = iceGrams;
        this.otherHeatCapacity = otherHeatCapacity;
    }

    public record StepResult(double vaporisedGrams, double meltedGrams, double frozenGrams) {
    }

    public double heatCapacity() {
        return waterGrams * WATER_C + iceGrams * ICE_C + otherHeatCapacity;
    }

    /**
     * Advances the model by {@code dt} seconds.
     *
     * @param powerW        heat supplied (W), e.g. by a heater; may be 0
     * @param ambientK      surrounding temperature
     * @param lossWPerK     overall heat-loss coefficient UA in W/K (smaller = better insulated)
     * @param boilingPointK boiling point of the liquid (373.15 K for pure water at 1 atm)
     */
    public StepResult step(double powerW, double ambientK, double lossWPerK, double boilingPointK, double dt) {
        double q = (powerW - lossWPerK * (temperatureK - ambientK)) * dt;
        double vaporised = 0, melted = 0, frozen = 0;
        // Melting / freezing plateau at 0 °C
        if (iceGrams > 0 && q > 0 && temperatureK >= MELTING_POINT - 1e-9) {
            double m = Math.min(iceGrams, q / LATENT_FUSION);
            iceGrams -= m;
            waterGrams += m;
            melted = m;
            q -= m * LATENT_FUSION;
            temperatureK = MELTING_POINT;
        } else if (waterGrams > 0 && q < 0 && temperatureK <= MELTING_POINT + 1e-9) {
            double m = Math.min(waterGrams, -q / LATENT_FUSION);
            waterGrams -= m;
            iceGrams += m;
            frozen = m;
            q += m * LATENT_FUSION;
            temperatureK = MELTING_POINT;
        }
        // Boiling plateau
        if (waterGrams > 0 && q > 0 && temperatureK >= boilingPointK - 1e-9) {
            double m = Math.min(waterGrams, q / LATENT_VAPORISATION);
            waterGrams -= m;
            vaporised = m;
            q -= m * LATENT_VAPORISATION;
            temperatureK = boilingPointK;
        }
        double c = heatCapacity();
        if (c > 0 && q != 0) {
            double newT = temperatureK + q / c;
            // Stop at phase-change temperatures so the next step handles the latent heat.
            if (temperatureK < MELTING_POINT && newT > MELTING_POINT && iceGrams > 0) newT = MELTING_POINT;
            if (temperatureK > MELTING_POINT && newT < MELTING_POINT && waterGrams > 0) newT = MELTING_POINT;
            if (temperatureK < boilingPointK && newT > boilingPointK && waterGrams > 0) newT = boilingPointK;
            temperatureK = newT;
        } else if (c <= 0) {
            temperatureK = ambientK;
        }
        return new StepResult(vaporised, melted, frozen);
    }

    /** Boiling point of an aqueous solution: T_b = 373.15 K + i·K_b·b (ideal dilute-solution approximation). */
    public static double boilingPointOfSolution(double soluteParticlesMolPerKg) {
        return BOILING_POINT + EBULLIOSCOPIC_WATER * soluteParticlesMolPerKg;
    }

    public double temperatureK() {
        return temperatureK;
    }

    public void setTemperatureK(double t) {
        temperatureK = t;
    }

    public double waterGrams() {
        return waterGrams;
    }

    public double iceGrams() {
        return iceGrams;
    }

    public void setWaterGrams(double g) {
        waterGrams = Math.max(0, g);
    }

    public void setIceGrams(double g) {
        iceGrams = Math.max(0, g);
    }

    public double otherHeatCapacity() {
        return otherHeatCapacity;
    }

    public void setOtherHeatCapacity(double c) {
        otherHeatCapacity = Math.max(0, c);
    }

    /** Adds heat directly (e.g. released by an exothermic reaction), in joules. */
    public void addHeat(double joules) {
        double c = heatCapacity();
        if (c > 0) temperatureK += joules / c;
    }
}
