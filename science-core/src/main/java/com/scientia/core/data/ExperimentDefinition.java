package com.scientia.core.data;

import java.util.List;

/** A scientific experiment: its design, procedure and the facts that mark it complete. */
public record ExperimentDefinition(
        String id,
        String title,
        String branch,
        String objective,
        String question,
        List<String> materials,
        List<String> apparatus,
        String independentVariable,
        String dependentVariable,
        List<String> controlledVariables,
        List<String> procedure,
        String expected,
        List<String> formulas,
        String interpretation,
        String limitations,
        List<String> completion,
        Hypothesis hypothesis
) {
    /** An optional prediction the player records before running the experiment. */
    public record Hypothesis(String prompt, List<Option> options, String evaluator) {
    }

    public record Option(String id, String text) {
    }
}
