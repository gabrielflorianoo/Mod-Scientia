package com.scientia.core.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.scientia.core.chem.Element;
import com.scientia.core.chem.ElementDatabase;
import com.scientia.core.chem.Reaction;
import com.scientia.core.chem.ReactionEngine;
import com.scientia.core.chem.Substance;
import com.scientia.core.chem.SubstanceDatabase;
import com.scientia.core.research.ResearchGraph;
import com.scientia.core.research.ResearchNode;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All static scientific data, loaded once from JSON files in {@code data/scientia/science/}.
 * The database is immutable after loading and safe to share between threads.
 */
public final class ScienceDatabase {
    /** Opens a named data file (e.g. "elements.json"). */
    @FunctionalInterface
    public interface Source {
        Reader open(String file) throws IOException;
    }

    public static final String[] FILES = {"elements.json", "substances.json", "reactions.json", "materials.json",
            "formulas.json", "experiments.json", "research.json", "glossary.json", "spectra.json",
            "microscope.json", "workbench_recipes.json"};

    private static volatile ScienceDatabase instance;
    private static final Gson GSON = new GsonBuilder().create();

    private final ElementDatabase elements;
    private final SubstanceDatabase substances;
    private final Map<String, Reaction> reactions = new LinkedHashMap<>();
    private final ReactionEngine engine;
    private final Map<String, MaterialEntry> materials = new LinkedHashMap<>();
    private final Map<String, MaterialEntry> materialsByItem = new LinkedHashMap<>();
    private final Map<String, FormulaEntry> formulas = new LinkedHashMap<>();
    private final Map<String, ExperimentDefinition> experiments = new LinkedHashMap<>();
    private final ResearchGraph research;
    private final List<GlossaryEntry> glossary;
    private final Map<String, SpectrumEntry> spectra = new LinkedHashMap<>();
    private final Map<String, MicroscopeSample> microscope = new LinkedHashMap<>();
    private final Map<String, WorkbenchRecipe> workbench = new LinkedHashMap<>();

    private ScienceDatabase(Source src) throws IOException {
        List<Element> el = read(src, "elements.json", new TypeToken<List<Element>>() {}.getType());
        elements = new ElementDatabase(el);
        List<Substance> subs = read(src, "substances.json", new TypeToken<List<Substance>>() {}.getType());
        Map<String, Substance> seenS = new LinkedHashMap<>();
        for (Substance s : subs) putUnique(seenS, s.id(), s, "substance");
        Map<String, Element> seenE = new LinkedHashMap<>();
        for (Element e : el) putUnique(seenE, e.symbol(), e, "element");
        substances = new SubstanceDatabase(subs, elements);
        List<Reaction> rx = read(src, "reactions.json", new TypeToken<List<Reaction>>() {}.getType());
        for (Reaction r : rx) putUnique(reactions, r.id(), r, "reaction");
        engine = new ReactionEngine(substances, reactions.values());
        List<MaterialEntry> mats = read(src, "materials.json", new TypeToken<List<MaterialEntry>>() {}.getType());
        for (MaterialEntry m : mats) {
            putUnique(materials, m.id(), m, "material");
            putUnique(materialsByItem, m.item(), m, "material item");
        }
        List<FormulaEntry> f = read(src, "formulas.json", new TypeToken<List<FormulaEntry>>() {}.getType());
        for (FormulaEntry e : f) putUnique(formulas, e.id(), e, "formula");
        List<ExperimentDefinition> ex = read(src, "experiments.json", new TypeToken<List<ExperimentDefinition>>() {}.getType());
        for (ExperimentDefinition e : ex) putUnique(experiments, e.id(), e, "experiment");
        List<ResearchNode> rn = read(src, "research.json", new TypeToken<List<ResearchNode>>() {}.getType());
        Map<String, ResearchNode> seen = new LinkedHashMap<>();
        for (ResearchNode n : rn) putUnique(seen, n.id(), n, "research node");
        research = new ResearchGraph(rn);
        List<GlossaryEntry> gl = read(src, "glossary.json", new TypeToken<List<GlossaryEntry>>() {}.getType());
        glossary = List.copyOf(gl);
        List<SpectrumEntry> sp = read(src, "spectra.json", new TypeToken<List<SpectrumEntry>>() {}.getType());
        for (SpectrumEntry s : sp) putUnique(spectra, s.symbol(), s, "spectrum");
        List<MicroscopeSample> ms = read(src, "microscope.json", new TypeToken<List<MicroscopeSample>>() {}.getType());
        for (MicroscopeSample s : ms) putUnique(microscope, s.id(), s, "microscope sample");
        List<WorkbenchRecipe> wr = read(src, "workbench_recipes.json", new TypeToken<List<WorkbenchRecipe>>() {}.getType());
        for (WorkbenchRecipe w : wr) putUnique(workbench, w.id(), w, "workbench recipe");
    }

    private static <V> void putUnique(Map<String, V> map, String key, V value, String what) throws IOException {
        if (key == null || key.isBlank()) throw new IOException("A " + what + " has no id");
        if (map.putIfAbsent(key, value) != null) throw new IOException("Duplicate " + what + " id '" + key + "'");
    }

    private static <T> T read(Source src, String file, Type type) throws IOException {
        try (Reader r = src.open(file)) {
            if (r == null) throw new IOException("Missing science data file " + file);
            T v = GSON.fromJson(r, type);
            if (v == null) throw new IOException("Empty science data file " + file);
            return v;
        } catch (JsonParseException e) {
            throw new IOException("Malformed JSON in " + file + ": " + e.getMessage(), e);
        }
    }

    public static ScienceDatabase load(Source src) throws IOException {
        return new ScienceDatabase(src);
    }

    /** Loads from the classpath directory {@code /data/scientia/science/}. */
    public static ScienceDatabase loadFromClasspath(Class<?> anchor) throws IOException {
        return load(file -> {
            var in = anchor.getResourceAsStream("/data/scientia/science/" + file);
            return in == null ? null : new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8);
        });
    }

    public static void setInstance(ScienceDatabase db) {
        instance = db;
    }

    public static ScienceDatabase get() {
        ScienceDatabase db = instance;
        if (db == null) throw new IllegalStateException("Science database has not been loaded");
        return db;
    }

    public static boolean isLoaded() {
        return instance != null;
    }

    public ElementDatabase elements() {
        return elements;
    }

    public SubstanceDatabase substances() {
        return substances;
    }

    public Map<String, Reaction> reactions() {
        return Collections.unmodifiableMap(reactions);
    }

    public ReactionEngine reactionEngine() {
        return engine;
    }

    public Map<String, MaterialEntry> materials() {
        return Collections.unmodifiableMap(materials);
    }

    public MaterialEntry materialForItem(String itemId) {
        return materialsByItem.get(itemId);
    }

    public Map<String, FormulaEntry> formulas() {
        return Collections.unmodifiableMap(formulas);
    }

    public Map<String, ExperimentDefinition> experiments() {
        return Collections.unmodifiableMap(experiments);
    }

    public ResearchGraph research() {
        return research;
    }

    public List<GlossaryEntry> glossary() {
        return glossary;
    }

    public Map<String, SpectrumEntry> spectra() {
        return Collections.unmodifiableMap(spectra);
    }

    public Map<String, MicroscopeSample> microscope() {
        return Collections.unmodifiableMap(microscope);
    }

    public Map<String, WorkbenchRecipe> workbenchRecipes() {
        return Collections.unmodifiableMap(workbench);
    }
}
