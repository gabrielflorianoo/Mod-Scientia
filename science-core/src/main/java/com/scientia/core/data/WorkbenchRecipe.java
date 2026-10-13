package com.scientia.core.data;

import java.util.List;

/** A Scientific Workbench recipe gated behind a research node. Item ids are Minecraft resource locations. */
public record WorkbenchRecipe(String id, Stack result, List<Stack> ingredients, String research) {
    public record Stack(String item, int count) {
    }
}
