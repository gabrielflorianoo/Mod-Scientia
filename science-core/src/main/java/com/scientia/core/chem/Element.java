package com.scientia.core.chem;

import java.util.List;

/**
 * One chemical element. Temperatures are in kelvin, density in g/cm³ (solids and liquids near room
 * temperature; for gases the value is g/L at 0 °C and 101.325 kPa). Any property may be null when it is
 * unknown or has never been measured (common for the superheavy elements).
 *
 * @param atomicMass standard atomic weight (abridged) or, for elements without stable isotopes, the mass
 *                   number of the longest-lived known isotope (flagged by {@code massIsMassNumber}).
 */
public record Element(
        int z,
        String symbol,
        String name,
        Double atomicMass,
        boolean massIsMassNumber,
        String category,
        Integer group,
        int period,
        String block,
        String electronConfiguration,
        List<Integer> shells,
        List<Integer> oxidationStates,
        Double density,
        Double meltingPointK,
        Double boilingPointK,
        String phase,
        String electricalConductivity,
        String thermalConductivity,
        Double electronegativity,
        String occurrence,
        String discovered,
        String description,
        String dataNote,
        boolean propertiesPredicted
) {
    /** Most common isotope's neutron count estimated from the rounded atomic mass (educational approximation). */
    public int approximateNeutrons() {
        if (atomicMass == null) return 0;
        return Math.max(0, (int) Math.round(atomicMass) - z);
    }

    public boolean isSynthetic() {
        return "synthetic".equals(occurrence);
    }
}
