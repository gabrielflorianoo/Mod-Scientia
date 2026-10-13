package com.scientia.core.data;

import java.util.List;
import java.util.Map;

/**
 * A formula library entry. {@code forms} maps each variable to an expression that computes it from the
 * others, so the formula solver never needs symbolic algebra at runtime.
 */
public record FormulaEntry(
        String id,
        String name,
        String branch,
        String display,
        Map<String, String> forms,
        List<Variable> variables,
        String assumptions,
        String example,
        String experiment,
        String mistakes,
        List<String> related
) {
    public record Variable(String symbol, String name, String unit) {
    }
}
