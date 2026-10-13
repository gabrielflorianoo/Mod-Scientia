package com.scientia.core.research;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The research tree with dependency validation (unknown prerequisites, cycles). */
public final class ResearchGraph {
    private final Map<String, ResearchNode> nodes = new LinkedHashMap<>();

    public ResearchGraph(Collection<ResearchNode> list) {
        for (ResearchNode n : list) nodes.put(n.id(), n);
    }

    public ResearchNode get(String id) {
        return nodes.get(id);
    }

    public Collection<ResearchNode> all() {
        return nodes.values();
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        for (ResearchNode n : nodes.values()) {
            for (String p : n.prerequisites()) {
                if (!nodes.containsKey(p)) errors.add("research " + n.id() + ": unknown prerequisite '" + p + "'");
            }
            if (n.requires() == null || n.requires().isEmpty()) {
                errors.add("research " + n.id() + ": must require at least one accomplishment (no free research points)");
            }
            if (n.summary() == null || n.summary().isBlank()) errors.add("research " + n.id() + ": missing summary");
        }
        Map<String, Integer> state = new HashMap<>();
        for (String id : nodes.keySet()) {
            if (cycle(id, state)) {
                errors.add("research graph contains a cycle through '" + id + "'");
                break;
            }
        }
        return errors;
    }

    private boolean cycle(String id, Map<String, Integer> state) {
        Integer s = state.get(id);
        if (s != null) return s == 1;
        state.put(id, 1);
        ResearchNode n = nodes.get(id);
        if (n != null) {
            for (String p : n.prerequisites()) if (cycle(p, state)) return true;
        }
        state.put(id, 2);
        return false;
    }

    /** Which node unlocks a given recipe or feature id, or null. */
    public ResearchNode unlockerOf(String unlockId) {
        for (ResearchNode n : nodes.values()) if (n.unlocks() != null && n.unlocks().contains(unlockId)) return n;
        return null;
    }
}
