package com.scientia.core.chem;

import java.util.List;

/**
 * A pure chemical substance (an element or a compound). Mixtures are intentionally not substances;
 * see {@link Mixture}.
 *
 * @param kind            element | molecular | ionic | metallic | network_covalent | mineral
 * @param phase           physical state at 25 °C and 101.325 kPa: solid | liquid | gas
 * @param density         g/cm³ for solids/liquids near 25 °C; null for gases (computed from the ideal gas law)
 * @param specificHeat    specific heat capacity in J/(g·K) near 25 °C, or null when not in this database
 * @param solubility      approximate solubility in water near 20–25 °C in mol/L, or null when "very soluble" is
 *                        not limited in the game model; 0 for practically insoluble substances
 * @param acidBase        how the substance contributes to the acid–base balance of an aqueous solution
 */
public record Substance(
        String id,
        String name,
        String formula,
        String kind,
        String phase,
        Double density,
        Double meltingPointK,
        Double boilingPointK,
        Double specificHeat,
        Double solubility,
        AcidBase acidBase,
        String conductivity,
        Description description,
        String hazard,
        List<String> sources,
        String notes
) {
    /**
     * Acid–base contributions per mole of substance dissolved: strong cations / anions in charge
     * equivalents (e.g. Ca²⁺ counts 2), and an optional weak acid system (acetate, carbonate) with the
     * number of moles of that system contributed.
     */
    public record AcidBase(double strongCation, double strongAnion, String system, double systemMoles) {
    }

    /** Three levels of explanation: intuition, scientific explanation, technical detail. */
    public record Description(String intuition, String science, String technical) {
    }

    public boolean isGas() {
        return "gas".equals(phase);
    }
}
