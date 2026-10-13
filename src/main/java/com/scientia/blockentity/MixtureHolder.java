package com.scientia.blockentity;

import com.scientia.core.chem.Mixture;

/** A block entity containing a chemical mixture (the pH meter and analyser can inspect it). */
public interface MixtureHolder {
    Mixture mixture();

    double temperatureK();
}
