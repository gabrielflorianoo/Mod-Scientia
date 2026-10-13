package com.scientia.client.screen;

import com.scientia.config.ScientiaConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;

/** Three-level explanations (intuition / science / technical) shown in machine screens by difficulty. */
@OnlyIn(Dist.CLIENT)
public final class Explanations {
    private Explanations() {
    }

    private static final Map<String, String[]> TEXT = Map.ofEntries(
            Map.entry("precision_balance", new String[]{
                    "The balance tells you how much matter a sample contains.",
                    "Mass is measured in grams. This balance reads to 0.01 g, so every reading is ±0.01 g.",
                    "The pan sinks as the load cell deflects. Combine mass with the displaced volume to get density ρ = m/V; the volume dominates the uncertainty."}),
            Map.entry("graduated_cylinder", new String[]{
                    "A solid pushes water out of the way: the water level rises by the solid's volume.",
                    "Displacement volume = final reading − initial reading (1 mL = 1 cm³).",
                    "Each reading is ±0.2 mL, so a difference is ±0.28 mL (√2 × 0.2). Floating samples displace only their own weight of water (Archimedes' principle)."}),
            Map.entry("lab_heater", new String[]{
                    "Heat makes things warmer — until they start to melt or boil.",
                    "Q = m c ΔT while the temperature changes; during boiling Q = m L and the temperature stays constant.",
                    "Lumped model: C dT/dt = P − UA(T − T_env). A jacket lowers UA = 1/(1/UA₀ + t/(kA)). Dissolved salt raises the boiling point: ΔT_b = i K_b b."}),
            Map.entry("condenser", new String[]{
                    "Cooling steam turns it back into liquid water.",
                    "Condensation releases the latent heat that boiling absorbed. Salt stays behind because it does not evaporate at 100 °C.",
                    "Distillation separates by volatility; here vapour is assumed to be pure water and recovery is 100 %."}),
            Map.entry("reaction_vessel", new String[]{
                    "Chemicals can turn into new substances. Watch what happens to the mass!",
                    "Reactions rearrange atoms: a balanced equation has the same atoms on both sides, so mass is conserved in a sealed vessel.",
                    "Only reactions in the database occur. Gas above its solubility escapes when open. Headspace pressure P = nRT/V (ideal gas, 1.0 L). Heat released = −ΔH × extent."}),
            Map.entry("material_analyzer", new String[]{
                    "Every element glows with its own set of colours.",
                    "Excited electrons drop to lower energy levels and emit light at characteristic wavelengths (λ = hc/ΔE).",
                    "Lines are taken from reference data; intensities are approximate. The conductivity probe computes R = ρL/A for a 1 cm cube."}),
            Map.entry("microscope", new String[]{
                    "Lenses make tiny things look big.",
                    "Total magnification = objective × eyepiece. The scale bar shows real size.",
                    "Resolution of a light microscope is limited by diffraction to roughly 0.2 µm."}),
            Map.entry("data_recorder", new String[]{
                    "Measuring again and again shows how something changes.",
                    "A graph of a quantity against time; the slope of a straight-line fit is the rate of change.",
                    "Least-squares fit with standard errors. For cooling, k = −(dT/dt)/(T − T_env) estimates UA/C."}),
            Map.entry("insulated_kiln", new String[]{
                    "Insulation keeps heat in, so the kiln needs less fuel.",
                    "Heat loss is proportional to the temperature difference: P = UA (T − T_env).",
                    "C dT/dt = P_burner − UA ΔT − P_charge. Smelting needs ≥ 1100 K; carbon burns at 393.5 kJ/mol."}),
            Map.entry("battery", new String[]{
                    "A battery pushes electric charge around a circuit.",
                    "EMF is the energy given to each coulomb; the terminal voltage drops by I·r under load.",
                    "V_terminal = EMF − I r, r = 0.05 Ω. Capacity 7 Ah × 12 V = 302 kJ."}),
            Map.entry("thermal_generator", new String[]{
                    "Burning fuel can make electricity.",
                    "Chemical energy → heat → motion → electrical energy. Most of the energy ends up as waste heat.",
                    "Overall efficiency 25 %: fuel power = P_electric / 0.25 + 50 W idle losses."}),
            Map.entry("solar_panel", new String[]{
                    "Sunlight can be turned straight into electricity.",
                    "Photons free electrons in silicon; the panel acts like a source whose current depends on light.",
                    "Linearised I–V curve: V_oc = 18 V, I_sc = 1 A × irradiance, maximum power at R_load = V_oc/I_sc."}),
            Map.entry("electric_motor", new String[]{
                    "A motor turns electricity into motion.",
                    "A current in a magnetic field feels a force; the coil turns.",
                    "Speed ∝ voltage; mechanical power = 70 % of electrical power (simplified constant efficiency)."}),
            Map.entry("electrolysis_station", new String[]{
                    "Electricity can split water into hydrogen and oxygen.",
                    "At the electrodes, electrons are added to or removed from the substances in the solution.",
                    "Faraday: n = Q/(zF). Counter-EMF 1.48 V (thermoneutral), 2 Ω electrolyte; brine gives NaOH, H₂ and Cl₂."})
    );

    public static String get(String machine) {
        String[] t = TEXT.get(machine);
        if (t == null) return "";
        ScientiaConfig.Difficulty d;
        try {
            d = ScientiaConfig.DIFFICULTY.get();
        } catch (IllegalStateException e) {
            d = ScientiaConfig.Difficulty.RESEARCHER;
        }
        return switch (d) {
            case EXPLORER -> t[0];
            case RESEARCHER -> t[0] + " " + t[1];
            case SCIENTIST -> t[1] + " " + t[2];
        };
    }
}
