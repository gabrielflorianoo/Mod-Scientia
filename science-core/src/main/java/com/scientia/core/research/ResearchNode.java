package com.scientia.core.research;

import java.util.List;

/**
 * A concept in the research tree. A node completes when all prerequisites are complete and all
 * required facts (genuine accomplishments such as "measure:mass" or "reaction:limewater_test") are known.
 */
public record ResearchNode(
        String id,
        String branch,
        String title,
        int x,
        int y,
        List<String> prerequisites,
        List<String> requires,
        List<String> unlocks,
        String icon,
        String summary
) {
}
