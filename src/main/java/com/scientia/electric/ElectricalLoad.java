package com.scientia.electric;

/** A block entity that consumes electrical energy and wants to know its operating point. */
public interface ElectricalLoad {
    void onSolved(double voltage, double current, double power, double dt);

    /** Counter-EMF in volts for electrochemical cells (0 for resistive loads). */
    default double counterEmf() {
        return 0;
    }
}
