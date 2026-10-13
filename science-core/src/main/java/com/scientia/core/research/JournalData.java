package com.scientia.core.research;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.scientia.core.data.ExperimentDefinition;
import com.scientia.core.data.ScienceDatabase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A player's scientific journal: known facts (accomplishments), completed research and experiments,
 * recorded measurements, hypotheses and personal notes. It is plain data so it can be persisted as JSON
 * and synchronised to the client; all mutation happens on the server.
 */
public final class JournalData {
    public static final int MAX_MEASUREMENTS = 256;
    public static final int MAX_NOTES = 64;
    public static final int MAX_NOTE_LENGTH = 256;
    private static final Gson GSON = new GsonBuilder().create();

    public record MeasurementRecord(String quantity, String subject, double value, double uncertainty, String unit,
                                    long gameTime, String instrument) {
    }

    public record HypothesisRecord(String experiment, String predicted, String observed, Boolean supported,
                                   String explanation) {
    }

    /** What changed after a fact was added. */
    public record Progress(List<String> newResearch, List<String> newExperiments, boolean newFact) {
        public boolean any() {
            return newFact || !newResearch.isEmpty() || !newExperiments.isEmpty();
        }
    }

    private int version = 1;
    private Set<String> facts = new LinkedHashSet<>();
    private Set<String> research = new LinkedHashSet<>();
    private Set<String> experiments = new LinkedHashSet<>();
    private Set<String> elements = new LinkedHashSet<>();
    private Set<String> favorites = new LinkedHashSet<>();
    private List<MeasurementRecord> measurements = new ArrayList<>();
    private Map<String, HypothesisRecord> hypotheses = new LinkedHashMap<>();
    private List<String> notes = new ArrayList<>();

    public Set<String> facts() {
        return facts;
    }

    public Set<String> research() {
        return research;
    }

    public Set<String> experiments() {
        return experiments;
    }

    public Set<String> elements() {
        return elements;
    }

    public Set<String> favorites() {
        return favorites;
    }

    public List<MeasurementRecord> measurements() {
        return measurements;
    }

    public Map<String, HypothesisRecord> hypotheses() {
        return hypotheses;
    }

    public List<String> notes() {
        return notes;
    }

    public boolean hasFact(String fact) {
        return facts.contains(fact);
    }

    /** Adds a fact and re-evaluates experiments and research until nothing more completes. */
    public Progress addFact(String fact, ScienceDatabase db) {
        boolean added = facts.add(fact);
        if (fact.startsWith("element:")) elements.add(fact.substring("element:".length()));
        if (!added) return new Progress(List.of(), List.of(), false);
        return reevaluate(db, true);
    }

    public Progress reevaluate(ScienceDatabase db, boolean newFact) {
        List<String> newResearch = new ArrayList<>();
        List<String> newExperiments = new ArrayList<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ExperimentDefinition e : db.experiments().values()) {
                if (experiments.contains(e.id())) continue;
                if (e.completion() != null && !e.completion().isEmpty() && facts.containsAll(e.completion())) {
                    experiments.add(e.id());
                    facts.add("experiment:" + e.id());
                    newExperiments.add(e.id());
                    changed = true;
                }
            }
            for (ResearchNode n : db.research().all()) {
                if (research.contains(n.id())) continue;
                if (research.containsAll(n.prerequisites()) && facts.containsAll(n.requires())) {
                    research.add(n.id());
                    facts.add("research:" + n.id());
                    newResearch.add(n.id());
                    changed = true;
                }
            }
        }
        return new Progress(newResearch, newExperiments, newFact);
    }

    /** True when the recipe/feature is not gated, or its research node is complete. */
    public boolean isUnlocked(String unlockId, ScienceDatabase db) {
        ResearchNode n = db.research().unlockerOf(unlockId);
        return n == null || research.contains(n.id());
    }

    public void addMeasurement(MeasurementRecord m) {
        measurements.add(m);
        while (measurements.size() > MAX_MEASUREMENTS) measurements.remove(0);
    }

    /** Most recent measurement of a quantity for a subject, or null. */
    public MeasurementRecord latest(String quantity, String subject) {
        for (int i = measurements.size() - 1; i >= 0; i--) {
            MeasurementRecord m = measurements.get(i);
            if (m.quantity().equals(quantity) && m.subject().equals(subject)) return m;
        }
        return null;
    }

    public boolean addNote(String note) {
        if (note == null) return false;
        String n = note.strip();
        if (n.isEmpty()) return false;
        if (n.length() > MAX_NOTE_LENGTH) n = n.substring(0, MAX_NOTE_LENGTH);
        notes.add(n);
        while (notes.size() > MAX_NOTES) notes.remove(0);
        return true;
    }

    public void reset() {
        facts.clear();
        research.clear();
        experiments.clear();
        elements.clear();
        favorites.clear();
        measurements.clear();
        hypotheses.clear();
        notes.clear();
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static JournalData fromJson(String json) {
        if (json == null || json.isBlank()) return new JournalData();
        try {
            JournalData d = GSON.fromJson(json, JournalData.class);
            if (d == null) return new JournalData();
            d.repair();
            return d;
        } catch (RuntimeException e) {
            return new JournalData();
        }
    }

    /** Gson leaves missing fields null; restore empty collections. */
    private void repair() {
        if (facts == null) facts = new LinkedHashSet<>();
        if (research == null) research = new LinkedHashSet<>();
        if (experiments == null) experiments = new LinkedHashSet<>();
        if (elements == null) elements = new LinkedHashSet<>();
        if (favorites == null) favorites = new LinkedHashSet<>();
        if (measurements == null) measurements = new ArrayList<>();
        if (hypotheses == null) hypotheses = new LinkedHashMap<>();
        if (notes == null) notes = new ArrayList<>();
    }

    public int version() {
        return version;
    }
}
