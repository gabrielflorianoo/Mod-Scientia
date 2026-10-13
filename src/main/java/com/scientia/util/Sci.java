package com.scientia.util;

import com.scientia.core.chem.Mixture;
import com.scientia.core.chem.Substance;
import com.scientia.core.chem.SubstanceDatabase;
import com.scientia.core.data.MaterialAnalysis;
import com.scientia.core.data.MaterialEntry;
import com.scientia.core.data.ScienceDatabase;
import com.scientia.core.math.SigFigs;
import com.scientia.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Shared helpers that connect Minecraft objects to the science engine. */
public final class Sci {
    public static final String SAMPLE_TAG = "ScientiaSample";
    public static final String CONTENTS_TAG = "Contents";

    private Sci() {
    }

    public static ScienceDatabase db() {
        return ScienceDatabase.get();
    }

    public static String itemId(Item item) {
        var key = ForgeRegistries.ITEMS.getKey(item);
        return key == null ? "minecraft:air" : key.toString();
    }

    public static MaterialEntry material(ItemStack stack) {
        if (stack.isEmpty()) return null;
        return db().materialForItem(itemId(stack.getItem()));
    }

    /** Each physical specimen gets a random seed the first time it is measured, so repeated measurements agree. */
    public static long sampleSeed(ItemStack stack, Level level) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(SAMPLE_TAG)) tag.putLong(SAMPLE_TAG, level.random.nextLong());
        return tag.getLong(SAMPLE_TAG);
    }

    /** Mass in grams of an item stack treated as a sample, or NaN when the item has no scientific data. */
    public static double sampleMass(ItemStack stack, Level level) {
        MaterialEntry m = material(stack);
        if (m == null) return Double.NaN;
        return MaterialAnalysis.sampleMass(m, sampleSeed(stack, level), stack.getCount());
    }

    public static String sampleKey(ItemStack stack, Level level) {
        return Long.toHexString(sampleSeed(stack, level)) + "x" + stack.getCount();
    }

    // ------------------------------------------------------------------ mixtures

    public static CompoundTag writeMixture(Mixture m) {
        CompoundTag t = new CompoundTag();
        for (var e : m.amounts().entrySet()) t.putDouble(e.getKey(), e.getValue());
        return t;
    }

    public static Mixture readMixture(CompoundTag t) {
        Mixture m = new Mixture();
        SubstanceDatabase subs = db().substances();
        for (String k : t.getAllKeys()) if (subs.has(k)) m.add(k, t.getDouble(k));
        return m;
    }

    /** Contents of a beaker or canister item; empty mixture when the item holds nothing. */
    public static Mixture contentsOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(CONTENTS_TAG)) return new Mixture();
        return readMixture(tag.getCompound(CONTENTS_TAG));
    }

    public static void setContents(ItemStack stack, Mixture m) {
        if (m.isEmpty()) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                tag.remove(CONTENTS_TAG);
                if (tag.isEmpty()) stack.setTag(null);
            }
        } else {
            stack.getOrCreateTag().put(CONTENTS_TAG, writeMixture(m));
        }
    }

    /** Converts one item of a material into moles of its component substances (by mass fraction). */
    public static Map<String, Double> substancesForItem(ItemStack stack) {
        Map<String, Double> out = new LinkedHashMap<>();
        MaterialEntry m = material(stack);
        if (m == null || m.composition() == null) return out;
        SubstanceDatabase subs = db().substances();
        for (MaterialEntry.Component c : m.composition()) {
            String id = c.substance();
            out.merge(id, m.sampleMassG() * c.massFraction() / subs.molarMass(id), Double::sum);
        }
        return out;
    }

    /** Reagent items that a solid substance can be packaged back into (10 g per item). */
    public static final Map<String, java.util.function.Supplier<Item>> SOLID_ITEMS = Map.of(
            "sodium_chloride", () -> ModItems.SALT_CRYSTALS.get(),
            "calcium_carbonate", () -> ModItems.CALCITE_POWDER.get(),
            "calcium_oxide", () -> ModItems.QUICKLIME.get(),
            "calcium_hydroxide", () -> ModItems.SLAKED_LIME.get(),
            "copper_ii_oxide", () -> ModItems.COPPER_OXIDE_POWDER.get());
    public static final double REAGENT_GRAMS = 10.0;

    /** Water mass (g) in a mixture. */
    public static double waterGrams(Mixture m) {
        return m.massOf("water", db().substances());
    }

    /** Heat capacity (J/K) of everything except water. Substances without data use 1.0 J/(g·K). */
    public static double otherHeatCapacity(Mixture m) {
        SubstanceDatabase subs = db().substances();
        double c = 0;
        for (var e : m.amounts().entrySet()) {
            if (e.getKey().equals("water")) continue;
            Substance s = subs.get(e.getKey());
            double cp = s == null || s.specificHeat() == null ? 1.0 : s.specificHeat();
            c += e.getValue() * subs.molarMass(e.getKey()) * cp;
        }
        return c;
    }

    /** Ideal van 't Hoff factor used for colligative properties (dissolved particles per formula unit). */
    public static double vantHoff(String substance) {
        return switch (substance) {
            case "sodium_chloride", "sodium_hydroxide", "sodium_acetate", "hydrogen_chloride" -> 2;
            case "calcium_chloride", "calcium_acetate", "sodium_carbonate", "calcium_hydroxide" -> 3;
            default -> 1;
        };
    }

    /** Molality of dissolved particles (mol/kg of water) of non-volatile solutes. */
    public static double soluteParticleMolality(Mixture m) {
        SubstanceDatabase subs = db().substances();
        double kg = waterGrams(m) / 1000.0;
        if (kg <= 1e-6) return 0;
        double particles = 0;
        for (var e : m.amounts().entrySet()) {
            Substance s = subs.get(e.getKey());
            if (s == null || e.getKey().equals("water") || s.isGas() || "liquid".equals(s.phase())) continue;
            double dissolved = e.getValue();
            if (s.solubility() != null) dissolved = Math.min(dissolved, s.solubility() * kg);
            particles += dissolved * vantHoff(e.getKey());
        }
        return particles / kg;
    }

    // ------------------------------------------------------------------ environment

    /**
     * Ambient air temperature (K) for a position. Minecraft biomes only have a dimensionless "temperature"
     * value, so this is a documented game approximation: T(°C) = −15 + 35 × t, clamped to −30…45 °C,
     * with the Nether at 45 °C.
     */
    public static double ambientK(Level level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        if (biome.is(BiomeTags.IS_NETHER)) return 273.15 + 45;
        float t = biome.value().getBaseTemperature();
        double c = Math.max(-30, Math.min(45, -15 + 35 * t));
        return 273.15 + c;
    }

    public static boolean isOcean(Level level, BlockPos pos) {
        return level.getBiome(pos).is(BiomeTags.IS_OCEAN);
    }

    // ------------------------------------------------------------------ formatting

    public static String temperature(double kelvin, boolean showKelvin) {
        if (!Double.isFinite(kelvin)) return "—";
        String c = String.format(Locale.ROOT, "%.1f °C", kelvin - 273.15);
        return showKelvin ? c + String.format(Locale.ROOT, " (%.1f K)", kelvin) : c;
    }

    public static String fmt(double v, int sig) {
        return SigFigs.format(v, sig);
    }

    public static String fixed(double v, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }

    /** Engineering-style formatting for electrical values: 0.0123 A -> "12.3 mA". */
    public static String si(double v, String unit) {
        double a = Math.abs(v);
        if (a == 0) return "0 " + unit;
        if (a >= 1e6) return fixed(v / 1e6, 2) + " M" + unit;
        if (a >= 1e3) return fixed(v / 1e3, 2) + " k" + unit;
        if (a >= 1) return fixed(v, 2) + " " + unit;
        if (a >= 1e-3) return fixed(v * 1e3, 1) + " m" + unit;
        if (a >= 1e-6) return fixed(v * 1e6, 1) + " µ" + unit;
        return SigFigs.format(v, 2) + " " + unit;
    }
}
