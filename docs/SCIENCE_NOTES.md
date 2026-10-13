# Scientific reference notes

This document lists every scientific model in Scientia, how it is implemented, how a player can
verify it, its limitations, and where the data come from. Each item is labelled:

- **REAL** — an established law or measured value used as such.
- **SIMPLIFIED** — real science with a stated approximation chosen for gameplay or performance.
- **GAME** — a convention needed because Minecraft has no equivalent (e.g. item masses).
- **FICTIONAL** — Minecraft content with no real counterpart (redstone); never presented as science.

## Data provenance

| Data | Source | Notes |
|---|---|---|
| 118 elements: atomic mass, density, melting/boiling points, electronegativity, shells, configuration | Compiled with `tools/build_elements.py` from the Bowserinator *Periodic-Table-JSON* dataset (values from Wikipedia, CC BY-SA), spot-checked against IUPAC standard atomic weights and CRC Handbook values | Elements without stable isotopes show the mass number of the longest-lived isotope, flagged `massIsMassNumber`. Z ≥ 100: bulk properties are unknown or only predicted, so they are **null** and flagged `propertiesPredicted`. At, Fr: bulk values are estimates (noted). Bk–Es: microgram-sample values (noted). Gas densities are g/L at 0 °C, 1 atm. |
| Common oxidation states | IUPAC / Greenwood & Earnshaw main states | Only the common states are listed. |
| Element descriptions | Written for this project | No text copied from references. |
| Substances (37): densities, melting/boiling points, specific heats, solubilities | CRC Handbook of Chemistry and Physics typical values; NIST Chemistry WebBook | Values near 25 °C; temperature dependence is not modelled. |
| Reaction enthalpies | Calculated from standard enthalpies of formation at 298 K (NIST/CRC) | Shown only when a reliable value exists; otherwise `null` (never invented). |
| Emission lines | Standard strong lines (NIST Atomic Spectra Database values, air wavelengths) | Relative intensities are approximate. |
| Refractive indices, resistivities, thermal conductivities | CRC Handbook typical values at ~20 °C, 589 nm | Typical values; real samples vary. |
| Constants | SI 2019 exact values; CODATA 2018 for G and ε₀ | |

## Measurement and uncertainty — REAL

- Balance: resolution and uncertainty ±0.01 g. Graduated cylinder: each reading ±0.2 mL, so a
  displaced volume (difference of two readings) has ±√2·0.2 ≈ 0.28 mL.
- Uncertainties of derived values are propagated in quadrature (independent random errors):
  relative uncertainties for products/quotients, absolute for sums/differences
  (`science-core/.../units/Measurement.java`).
- Hypotheses compare a change with a 2σ coverage interval (`HypothesisEvaluator`), so noise is
  reported as "no significant change".
- **GAME**: Minecraft items have no mass. One ore/ingot/mineral item is treated as a 100 g specimen,
  reagent items as 10 g portions, with ±4 % specimen-to-specimen variation fixed per item stack.

## Density and identification — REAL (with GAME specimen masses)

ρ = m/V. Volume by displacement assumes a non-porous sample. Floating samples (ρ < 0.997 g/cm³)
displace only their own weight of water — Archimedes' principle — and the cylinder reports exactly
that, warning the player. Mixture densities assume additive volumes: 1/ρ = Σ wᵢ/ρᵢ.
Identification accepts a reference value within 2σ + 1.5 %; materials with similar densities
(raw gold ore vs raw copper ore in the game model) are reported as ambiguous, motivating a spectrum.

**GAME** compositions of vanilla items are stated in `materials.json` (`abstraction` field), e.g.
raw iron = 70 % hematite + 30 % quartz gangue; coal = 85 % carbon + silicate ash with the density of
real bituminous coal; redstone is **FICTIONAL** and has no composition.

## Chemistry

### Elements, compounds, mixtures — REAL
Substances are pure elements or compounds with formulas; a `Mixture` is a list of amounts of
substances and never becomes a new compound unless a database reaction occurs. Dissolving salt or
sugar is a physical change.

### Reaction engine — REAL stoichiometry, SIMPLIFIED kinetics
- Every reaction is validated: atoms balance for each element, total molar mass is conserved, all
  substances exist, explanations and references are present (`ReactionValidator`, unit-tested).
- Extent per step is limited by the limiting reagent. **SIMPLIFIED**: rate = first-order gameplay
  constant (`ratePerSecond`); real rates depend on concentration, surface area and temperature.
- Temperature requirements are sharp thresholds (**SIMPLIFIED**): e.g. CaCO₃ decomposition at
  1170 K, the temperature where its equilibrium CO₂ pressure reaches 1 atm.
- Heat released = −ΔH × extent feeds the vessel's thermal model (neutralisation warms the solution).
- Unknown combinations produce **no reaction** ("No reaction under these conditions") — products are
  never invented.
- Gases: a gas stays dissolved up to its solubility (CO₂ 0.033 mol/L, O₂ 1.3 mmol/L, H₂ 0.78 mmol/L…;
  very soluble HCl stays while water is present). In an open vessel the excess escapes and the
  measured mass drops by exactly the escaped mass; in a sealed vessel mass is conserved and pressure
  follows PV = nRT (1.0 L headspace, including trapped air). Above 500 kPa the stopper pops
  (**SIMPLIFIED** pressure-rated flask).

| Reaction | Equation | ΔH° (kJ) | Conditions |
|---|---|---|---|
| Calcite + hydrochloric acid | CaCO₃ + 2 HCl → CaCl₂ + H₂O + CO₂ | −15.1 | aqueous |
| Calcite + vinegar | CaCO₃ + 2 CH₃COOH → Ca(CH₃COO)₂ + H₂O + CO₂ | — | aqueous |
| Neutralisation | HCl + NaOH → NaCl + H₂O | −55.8 | aqueous |
| Acetic acid neutralisation | CH₃COOH + NaOH → CH₃COONa + H₂O | — | aqueous |
| Limestone decomposition | CaCO₃ → CaO + CO₂ | +178.3 | ≥ 1170 K (kiln) |
| Brine electrolysis | 2 NaCl + 2 H₂O → 2 NaOH + H₂ + Cl₂ | +446.0 | electrolysis, z = 2 |
| Water electrolysis | 2 H₂O → 2 H₂ + O₂ | +571.6 | electrolysis, z = 4 |
| Hydrogen chloride synthesis | H₂ + Cl₂ → 2 HCl | −184.6 | sealed, aqueous |
| Carbon combustion | C + O₂ → CO₂ | −393.5 | ≥ 700 K |
| Copper oxidation | 2 Cu + O₂ → 2 CuO | −314.6 | ≥ 573 K |
| Slaking lime | CaO + H₂O → Ca(OH)₂ | −65.2 | aqueous |
| Limewater test | Ca(OH)₂ + CO₂ → CaCO₃ + H₂O | −113.1 | aqueous |
| Iron ore reduction | 2 Fe₂O₃ + 3 C → 4 Fe + 3 CO₂ | +467.9 | ≥ 1173 K (overall equation; the CO intermediate is folded in) |
| Hydrogen combustion | 2 H₂ + O₂ → 2 H₂O | −571.6 | ≥ 843 K, controlled |

### Acids, bases and pH — REAL (ideal solution)
pH is computed from the charge balance [H⁺] + Σ(strong cations) = [OH⁻] + Σ(strong anions) +
Σ C_T(α₁ + 2α₂), solved by bisection with K_w = 1.0×10⁻¹⁴ (25 °C) and pKa(acetic) = 4.76,
pKa₁,₂(carbonate) = 6.35, 10.33. Tested values: 0.01 M HCl → 2.00, 0.01 M NaOH → 12.00,
0.1 M acetic acid → 2.88, 0.1 M sodium acetate → 8.88, saturated Ca(OH)₂ ≈ 12.6.
**SIMPLIFIED**: concentrations stand in for activities (pH = −log₁₀ a(H⁺) ≈ −log₁₀[H⁺]), accurate only
for dilute solutions; temperature is fixed at 25 °C. Solubility limits apply (Ca(OH)₂ 0.023 mol/L).
Indicator ranges (litmus 4.5–8.3, phenolphthalein 8.2–10.0, bromothymol blue 6.0–7.6, methyl orange
3.1–4.4) are real; colours are approximate and always accompanied by text.

### Electrolysis — REAL Faraday's law, SIMPLIFIED cell
n = Q/(zF) exactly. The cell is a counter-EMF of 1.48 V (thermoneutral voltage, ΔH/zF) plus 2 Ω of
electrolyte; it conducts only when driven above that. Real cells need extra overpotential and an
electrolyte; current efficiency is taken as 100 %.

### Emission spectroscopy — REAL lines, SIMPLIFIED intensities
The analyzer shows reference lines for each element in the sample's modelled composition (Na D lines
589.0/589.6 nm, Ca 422.7 nm, Fe 438.4 nm, Cu 510.6–521.8 nm, H Balmer lines…). Elements whose strong
lines are ultraviolet (Au, Ag, C, Zn…) are shown as UV, and gold is noted as hard to detect in the
visible — a real limitation of visible spectroscopy.

## Thermal science

- Lumped capacitance model **SIMPLIFIED**: C dT/dt = P − UA(T − T_env); convection, conduction and
  radiation are folded into one overall coefficient UA (bare beaker 0.6 W/K).
- Latent heats **REAL**: fusion 334 J/g at 0 °C, vaporisation 2257 J/g (value at 100 °C); temperature
  plateaus during melting and boiling (unit-tested against Q = mcΔT).
- Boiling-point elevation **REAL** (ideal dilute solution): ΔT_b = i K_b b, K_b = 0.512 K·kg/mol,
  ideal van 't Hoff factors (NaCl i = 2; the real effective value is ≈1.8).
- Insulating jackets: UA = 1/(1/UA₀ + t/(kA)), t = 1 cm, A = 0.03 m², k from real conductivities
  (wool 0.04, brick 0.7, glass 1.0, iron 80 W/(m·K)).
- Fuel energy **REAL** enthalpy: carbon burns at 393.5 kJ/mol; **GAME**: a coal item is 85 g of carbon
  (≈2.8 MJ). Heater burner efficiency 50 % (**SIMPLIFIED**).
- Kiln **SIMPLIFIED**: 2 kJ/K chamber, 20 kW thermostated burner, 15 kW absorbed by the charge,
  120 kJ per smelted item; brick walls UA = 22 W/K, a wool layer brings it to ≈1.8 W/K. The physics of
  why insulated kilns need much less fuel is real; the absolute numbers are game-scaled.
- **GAME** ambient temperature: T(°C) = −15 + 35 t from the biome's dimensionless temperature t,
  clamped to −30…45 °C (Nether 45 °C); lava ≈1100 °C, flames ≈800 °C are typical values, labelled.

## Electricity

- Circuit solver **REAL**: Kirchhoff's laws by nodal analysis with Gaussian elimination. Every wire
  block is a node joined to its neighbours by its real resistance (1 m of 1 mm² copper:
  R = ρL/A = 0.0168 Ω). Sources are EMFs with internal resistance (Norton equivalent), so short
  circuits give large finite currents. A 1 nS conductance to ground keeps floating parts solvable.
- Battery **SIMPLIFIED** lead–acid: 12 V × 7 Ah = 302.4 kJ, open-circuit voltage 11.8–12.7 V linear in
  state of charge, r = 0.05 Ω, 85 % charging efficiency.
- Filament lamp **SIMPLIFIED**: constant 24 Ω (12 V, 6 W rating); real filaments are non-ohmic.
- Resistors: E-series values with the real 4-band colour code.
- Motor **SIMPLIFIED**: 12 Ω load, speed ∝ voltage, 70 % efficiency; comparator output ∝ mechanical power.
- Generator **SIMPLIFIED**: 25 % fuel-to-electric efficiency plus 50 W idle losses; energy accounting
  is exact (fuel energy used = electrical output / efficiency).
- Solar panel **SIMPLIFIED**: the non-linear PV curve is linearised (V_oc 18 V, I_sc 1 A × irradiance);
  irradiance from sky light × max(0, cos(sun angle)) × 0.3 when raining.
- Redstone is **FICTIONAL**; it only switches the (real) circuits through the switch block.
- Series/parallel detection uses solved values (equal currents vs equal voltages), and the parallel
  hypothesis is evaluated by re-solving the network without one lamp.

## Mechanics and optics — REAL laws, SIMPLIFIED apparatus

- Incline: a = g(sin θ − μ cos θ), static if tan θ ≤ μ (μ_s = μ_k assumed); energy balance
  E_p = E_k + friction work is unit-tested. Rolling resistance and wheel rotation are ignored.
- Ramp efficiency (push at constant speed): η = mgh / (F L), F = mg(sin θ + μ cos θ).
- Thin lens 1/f = 1/dₒ + 1/dᵢ, M = −dᵢ/dₒ (real-is-positive convention); Snell's law with total
  internal reflection and critical angle; prism dispersion with the Cauchy fit n(λ) = 1.5046 +
  0.00420/λ² (λ in µm, BK7-like crown glass) and the thin-prism deviation δ ≈ (n − 1)A.
  Ray optics only (no diffraction).
- Microscope images are **illustrations generated for the game**, not photographs.

## Mathematics

- Expression parser: recursive descent over a fixed grammar; no reflection, scripting or code
  execution. Limits: 512 characters, nesting depth 64; errors for syntax, domain (√ of a negative,
  log of non-positive, asin outside [−1, 1]), division by zero, overflow, and unit mismatch.
- Dimensional analysis: quantities carry SI dimension exponents; adding a length to a mass is an error.
  Every rearranged formula in `formulas.json` is checked to produce the dimension of the variable it
  computes.
- Significant figures follow the usual conventions (trailing zeros in an integer without a decimal
  point are treated as not significant). Fractions are shown via continued fractions.
- Least-squares fits report slope/intercept standard errors and R².

## Safety policy

The reaction catalogue is limited to standard school-laboratory chemistry. No explosive, toxic-agent,
drug or other hazardous production procedure is implemented. Chlorine appears only as a sealed product
of brine electrolysis (an industrial process described at textbook level) used to make dilute
hydrochloric acid; hazards are stated in the substance data. In-game safety never implies real-world
safety — see the `hazard` and `simplification` fields.

## Applied processes and hazards

- **Steel** — REAL composition (≈0.8 % C, density 7.85 g/cm³); carburisation simplified to a fixed
  carbon mass at ≥ 900 °C (austenite region).
- **Portland cement** — REAL clinker phases (C₃S, C₂S) from limestone + clay at ≈ 1450 °C; hydration
  and curing times are GAME.
- **New reactions** — Fe + 2HCl → FeCl₂ + H₂; Fe₂O₃ + 6HCl → 2FeCl₃ + 3H₂O; CuO + 2HCl → CuCl₂ + H₂O;
  2NaOH + CO₂ → Na₂CO₃ + H₂O. Non-reactions (e.g. Cu + HCl) are explained with the reactivity series.
- **Hazards** — burst pressure (4.5 bar) and thermal-shock threshold (ΔT 150 K) are representative of
  ordinary laboratory glassware; wire limits are SIMPLIFIED; damage values are GAME.
- **Oxygen breathing** — resting human consumption ≈ 0.25 L O₂/min ≈ 0.0112 mol/min (REAL order of magnitude).
- **Salt de-icing** — NaCl–water eutectic −21.1 °C (REAL).

## Known scientific limitations

- No activity coefficients, temperature-dependent equilibrium constants, or kinetics beyond a rate constant.
- Heat-loss coefficients and the kiln/generator efficiencies are representative, not measured.
- Only a subset of vanilla materials has compositions; superheavy elements have no bulk properties.
- Specimen masses are a game convention; densities and compositions of vanilla items are modelled.
