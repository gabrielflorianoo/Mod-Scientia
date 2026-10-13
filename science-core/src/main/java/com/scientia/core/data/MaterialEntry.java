package com.scientia.core.data;

import java.util.List;

/**
 * Links a Minecraft item to scientific information. Minecraft items have no defined mass or exact
 * composition, so every entry states its {@code abstraction}: how the game interprets the item.
 *
 * @param classification raw_ore | mineral | refined | element | compound | mixture | alloy | manufactured | biological | fictional
 * @param sampleMassG    nominal mass of one item treated as a laboratory sample (a gameplay convention)
 * @param density        reference density (g/cm³); when null it is computed from the composition
 *                       assuming additive volumes, or the material has no meaningful density
 */
public record MaterialEntry(
        String id,
        String item,
        String name,
        String classification,
        List<Component> composition,
        double sampleMassG,
        Double density,
        String conductivity,
        String microscope,
        String abstraction,
        String description
) {
    public record Component(String substance, double massFraction) {
    }
}
