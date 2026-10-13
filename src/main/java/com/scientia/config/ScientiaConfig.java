package com.scientia.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-side simulation settings and client presentation settings. */
public final class ScientiaConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.IntValue CIRCUIT_INTERVAL;
    public static final ForgeConfigSpec.IntValue MAX_CIRCUIT_SIZE;
    public static final ForgeConfigSpec.IntValue THERMAL_INTERVAL;
    public static final ForgeConfigSpec.IntValue REACTION_INTERVAL;

    public static final ForgeConfigSpec.EnumValue<Difficulty> DIFFICULTY;
    public static final ForgeConfigSpec.BooleanValue TEMPERATURE_IN_KELVIN;
    public static final ForgeConfigSpec.BooleanValue SHOW_HUD;
    public static final ForgeConfigSpec.BooleanValue PARTICLES;
    public static final ForgeConfigSpec.BooleanValue HIGH_CONTRAST;

    /** Changes the depth of explanations shown, never the scientific facts themselves. */
    public enum Difficulty {
        EXPLORER, RESEARCHER, SCIENTIST
    }

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("simulation");
        CIRCUIT_INTERVAL = b.comment("Ticks between electrical network solves (lower = more responsive, more CPU).")
                .defineInRange("circuitIntervalTicks", 5, 1, 100);
        MAX_CIRCUIT_SIZE = b.comment("Maximum number of blocks in one electrical network.")
                .defineInRange("maxCircuitBlocks", 1024, 16, 8192);
        THERMAL_INTERVAL = b.comment("Ticks between thermal model updates of heaters, vessels and kilns.")
                .defineInRange("thermalIntervalTicks", 10, 1, 100);
        REACTION_INTERVAL = b.comment("Ticks between reaction engine steps in vessels.")
                .defineInRange("reactionIntervalTicks", 10, 1, 100);
        b.pop();
        COMMON_SPEC = b.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.push("presentation");
        DIFFICULTY = c.comment("EXPLORER: intuitive explanations. RESEARCHER: adds formulas and variables. SCIENTIST: full technical detail and fewer hints.")
                .defineEnum("difficulty", Difficulty.RESEARCHER);
        TEMPERATURE_IN_KELVIN = c.comment("Show temperatures in kelvin as well as degrees Celsius.")
                .define("temperatureInKelvin", true);
        SHOW_HUD = c.comment("Show the contextual measurement HUD while holding an instrument.").define("showMeasurementHud", true);
        PARTICLES = c.comment("Show bubbles, steam and sparks from working machines.").define("machineParticles", true);
        HIGH_CONTRAST = c.comment("Use a high-contrast colour scheme in Scientia screens.").define("highContrast", false);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    private ScientiaConfig() {
    }
}
