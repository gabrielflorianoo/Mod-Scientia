package com.scientia.core;

import com.scientia.core.experiment.HypothesisEvaluator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HypothesisTest {
    @Test
    void noiseIsNotMistakenForChange() {
        assertEquals("equal", HypothesisEvaluator.classifyChange(0.015, 0.01));
        assertEquals("increase", HypothesisEvaluator.classifyChange(0.5, 0.01));
        assertEquals("decrease", HypothesisEvaluator.classifyChange(-2.2, 0.01));
        assertTrue(HypothesisEvaluator.evaluate("equal", "equal", "").supported());
        assertFalse(HypothesisEvaluator.evaluate("increase", "equal", "").supported());
    }
}
