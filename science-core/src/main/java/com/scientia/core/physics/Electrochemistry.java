package com.scientia.core.physics;

import com.scientia.core.math.Constants;

/** Faraday's laws of electrolysis. */
public final class Electrochemistry {
    private Electrochemistry() {
    }

    /** Moles of product formed by charge Q (C) when z electrons are needed per formula unit: n = Q / (zF). */
    public static double molesFromCharge(double chargeC, int electronsPerUnit) {
        return chargeC / (electronsPerUnit * Constants.FARADAY);
    }

    public static double chargeForMoles(double moles, int electronsPerUnit) {
        return moles * electronsPerUnit * Constants.FARADAY;
    }

    /**
     * Thermoneutral voltage for water electrolysis at 25 °C: ΔH/(zF) = 285.8 kJ/mol / (2 × 96485 C/mol) ≈ 1.48 V.
     * The reversible (minimum) voltage is ΔG/(zF) ≈ 1.23 V; real cells need more because of overpotentials.
     */
    public static final double WATER_REVERSIBLE_VOLTAGE = 1.23;
    public static final double WATER_THERMONEUTRAL_VOLTAGE = 1.48;
}
