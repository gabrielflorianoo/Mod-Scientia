package com.scientia.electric;

/** A block entity that supplies electrical energy (battery, generator, solar panel). */
public interface ElectricalSource {
    /** Electromotive force in volts (0 when exhausted). */
    double emf();

    /** Internal resistance in ohms (> 0). */
    double internalResistance();

    /**
     * Called after each network solve.
     *
     * @param current current delivered out of the + terminal (A); negative means the source is being charged
     * @param dt      seconds represented by this solve
     */
    void onSolved(double current, double terminalVoltage, double dt);
}
