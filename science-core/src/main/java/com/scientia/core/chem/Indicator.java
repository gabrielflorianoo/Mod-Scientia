package com.scientia.core.chem;

/**
 * Acid–base indicators with their real transition ranges. Colours are RGB approximations of what is
 * seen in dilute solution; the {@link #describe} text is provided so colour is never the only cue.
 */
public enum Indicator {
    LITMUS("Litmus", 4.5, 8.3, 0xD03030, 0x3050D0, "red", "blue"),
    PHENOLPHTHALEIN("Phenolphthalein", 8.2, 10.0, 0xF4F4F4, 0xE0309A, "colourless", "pink"),
    BROMOTHYMOL_BLUE("Bromothymol blue", 6.0, 7.6, 0xE8D020, 0x2050C0, "yellow", "blue"),
    METHYL_ORANGE("Methyl orange", 3.1, 4.4, 0xD02020, 0xF0C020, "red", "yellow"),
    UNIVERSAL("Universal indicator", 0, 14, 0, 0, "", "");

    public final String displayName;
    public final double lowPH, highPH;
    private final int acidColor, baseColor;
    private final String acidName, baseName;

    Indicator(String displayName, double lowPH, double highPH, int acidColor, int baseColor, String acidName, String baseName) {
        this.displayName = displayName;
        this.lowPH = lowPH;
        this.highPH = highPH;
        this.acidColor = acidColor;
        this.baseColor = baseColor;
        this.acidName = acidName;
        this.baseName = baseName;
    }

    // Typical universal indicator colours by whole pH (formulations vary).
    private static final int[] UNIVERSAL_COLORS = {
            0xC81E1E, 0xDC2828, 0xE6461E, 0xF0781E, 0xF5A01E, 0xF5C81E, 0xD2DC28, 0x50B43C,
            0x28A064, 0x1E8C96, 0x1E64B4, 0x283CB4, 0x46289B, 0x5A1E87, 0x641E78};
    private static final String[] UNIVERSAL_NAMES = {
            "red", "red", "red-orange", "orange", "orange-yellow", "yellow", "yellow-green", "green",
            "blue-green", "teal", "blue", "indigo", "violet", "violet", "purple"};

    public int color(double pH) {
        if (Double.isNaN(pH)) return 0x808080;
        if (this == UNIVERSAL) {
            int i = (int) Math.max(0, Math.min(14, Math.round(pH)));
            return UNIVERSAL_COLORS[i];
        }
        double t = Math.max(0, Math.min(1, (pH - lowPH) / (highPH - lowPH)));
        return lerp(acidColor, baseColor, t);
    }

    public String describe(double pH) {
        if (Double.isNaN(pH)) return "no change (not an aqueous solution)";
        if (this == UNIVERSAL) return UNIVERSAL_NAMES[(int) Math.max(0, Math.min(14, Math.round(pH)))];
        if (pH <= lowPH) return acidName;
        if (pH >= highPH) return baseName;
        return "intermediate (" + acidName + " → " + baseName + ")";
    }

    private static int lerp(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
