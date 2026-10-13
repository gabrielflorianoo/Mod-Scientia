# Playing Scientia

## The first discovery loop (≈ 20 minutes)

1. **Gather** raw iron, raw copper, an iron ingot, a copper ingot, some sand and calcite.
2. **Craft** (vanilla crafting table) a *Science Journal*, a *Precision Balance*, a *Scientific
   Workbench* and a couple of *Beakers*.
3. **Measure mass.** Put an iron ingot on the balance and press *Record mass*. The balance reads to
   0.01 g; every specimen weighs a little differently (±4 %), like real hand samples.
   → research **Scientific Observation** completes and unlocks the *Graduated Cylinder* and the
   *Thermometer* at the Scientific Workbench.
4. **Measure volume.** Build the cylinder, fill it with a water bucket (50.0 mL), put the *same*
   ingot in and press *Record volume*. The water rises by the ingot's volume (±0.28 mL).
5. **Calculate density.** The journal combines your two measurements: ρ = m/V ≈ 7.87 ± 0.2 g/cm³,
   compares it with reference data and identifies the sample as iron. You discover the element Fe.
   → **Density** and **Identifying Materials** complete; *Reaction Vessel* and *Material Analyzer* unlock.
   Try raw iron ore: its density is far lower (oxygen and silicate rock), a nice hypothesis to test.
6. **Analyse.** In the Material Analyzer's conductivity mode a copper ingot reads ≈1.7 µΩ for a 1 cm
   cube → **Conductors and Insulators** unlocks wire, battery, lamp, switch and multimeter.
7. **Build a circuit.** Battery → copper wire → lamp → copper wire → back to the battery. The lamp's
   brightness comes from the solved current (12.7 V across 24 Ω ≈ 0.53 A, ≈ 6.6 W). Use the multimeter
   on the lamp. → **Electric Circuits**.
8. **Apply it.** Two lamps in parallel light your base at full brightness; a switch (or a redstone
   signal) controls them. A *Thermal Generator* burns coal to recharge the battery; an *Insulated Kiln*
   smelts ore with far less fuel than an uninsulated one.

## Other experiments

| Experiment | Apparatus | What you discover |
|---|---|---|
| Phase change | Heater + Data Recorder | Temperature plateau at the boiling point |
| Distillation | Heater + Condenser + seawater | Pure water and salt crystals; seawater boils ≈0.6 K above 100 °C |
| Thermal insulation | Heater + Data Recorder + wool jacket | Cooling constant k drops with insulation |
| Conservation of mass | Reaction Vessel (sealed vs open) | Mass constant when sealed, drops when CO₂ escapes |
| Acidity & pH | pH meter, vinegar, limewater, NaOH | Logarithmic pH scale, neutralisation |
| Ohm's law | Resistor + multimeter | V/I equals the colour-coded resistance |
| Series & parallel | Two lamps | Currents and brightness in each arrangement |
| Electrolysis | Electrolysis Station | n(H₂) = Q/(2F); brine gives NaOH, H₂, Cl₂ |
| Spectroscopy | Material Analyzer | Element lines (Na 589 nm, Ca 422.7 nm, Fe, Cu…) |
| Reflection, refraction, lenses | Optical Bench | Snell's law, critical angle, thin lens images |
| Motion and energy | Motion Track | a = g(sin θ − μ cos θ), energy lost to friction |
| Simple machines | Motion Track (push mode) | Ramp efficiency < 100 % |
| Microscopy | Microscope | Crystals, cells, fibres with a scale bar |
| Gas laws | Sealed vessel on a heater | Pressure rises with absolute temperature |

## Science that pays off

Lab work feeds straight back into survival progression — always through the real process:

| You learn | Use in the world |
|---|---|
| Carbon content of iron (Insulated Kiln, iron charge + ~10.8 g carbon additive at ≥ 1173 K) | **Steel ingots** → steel pickaxe, axe, shovel, sword and hoe (more durable and faster than iron) |
| Calcining limestone with clay at ≈ 1720 K (Kiln, clay additive) | **Portland cement** → 8 concrete powder (cement + sand + gravel), then **reinforced concrete** (concrete + steel rebar, blast resistance 1200) |
| Electrolysis of water, gas collection | **Oxygen canister** → drink for Water Breathing (duration from the moles stored, 0.0112 mol/min) |
| CO₂ from acid + carbonate | **CO₂ canister** → put yourself out, or use on a block to smother nearby fire |
| Freezing-point depression | **Salt crystals** melt ice and snow (works only above −21 °C, the NaCl–water eutectic) |
| Acids dissolve metal oxides | A beaker of acid (pH < 4) cleans one oxidation stage off weathered copper |

The kiln's GUI tells you what temperature and additive it still needs, and the journal records each new
recipe when you first make it.

## Mistakes have consequences

Nothing stops you from trying something unusual. If it goes wrong, the result is physically real and the
journal writes a **Lesson** explaining what happened and why:

- **Over-pressure** — a sealed Reaction Vessel above 4.5 bar bursts (gas-producing reactions, or heating a sealed vessel).
- **Thermal shock** — pouring water into glassware more than 150 K hotter shatters it.
- **Toxic gas** — chlorine escaping from an open vessel or an overfilled electrolysis store poisons nearby players.
- **Hydrogen** — released near fire, lava or an active heater it flashes.
- **Overloaded wires** — above ~25 A a wire warns you, above 60 A it burns out; a lamp driven past twice its rating blows its filament.
- **Hot surfaces and liquids** — standing on an active heater, kiln or generator, or grabbing liquid above 60 °C, burns.
- **Drinking reagents** — sneak-use a beaker: strong acids/bases burn, metal salts and chlorine poison, brine makes you thirsty.

Every lesson also counts toward the *Lab Safety* research.

## Hypotheses

Several experiments let you record a prediction in the journal *before* running them. Afterwards the
server compares it with your real measured data, using the measurement uncertainty, so a correct
prediction is never marked wrong because of noise. Being wrong is treated as a useful result.

## Difficulty

`config/scientia-client.toml` → `difficulty`: **EXPLORER** shows intuitive explanations,
**RESEARCHER** adds the governing equations, **SCIENTIST** shows full technical detail and hides the
expected results of experiments you have not completed. The science itself never changes.

## Commands (operators)

`/scientia validate`, `/scientia facts <player>`, `/scientia grant <player> <fact>`, `/scientia reset <player>`.
