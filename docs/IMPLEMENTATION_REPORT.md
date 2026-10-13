# Implementation report

## Environment

| Item | Value |
|---|---|
| Minecraft | Java Edition 1.20.1 |
| Mod loader | Forge 47.3.0, ForgeGradle `[6.0.24,6.2)`, official Mojang mappings |
| Java | 17 toolchain (engine compiled with `--release 17`) |
| Build | Gradle 8.8 wrapper committed (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) |
| Asset tool | Blender 5.2.2 (`bpy` module from PyPI) with Cycles CPU rendering |

The repository was empty at the start; the build, the engine, the data, the Forge layer and the asset
pipeline were all created in this work.

## Verification status — read this first

| What | Status |
|---|---|
| Science engine (`science-core`) | **Compiled and tested here**: 46 JUnit tests pass (`./gradlew -p science-core test`), including validation of every shipped data file. |
| Science data | **Validated here** by `ScienceValidator` (balanced reactions, mass conservation, dimensional consistency of all formula rearrangements, research graph acyclicity, references). |
| Blender asset pipeline | **Run here**: 32 OBJ models exported, 28 textures written, 30 Cycles previews rendered and inspected (`assets_generated/previews`). |
| Resources | **Validated here** by `tools/validate_assets.py` (21 blocks, 13 items, 74 models, OBJ/MTL integrity, textures, lang keys, loot tables, recipes). |
| Forge layer (`src/main/java`) | **Not compiled against real Minecraft/Forge.** This environment's network policy blocks `maven.minecraftforge.net` and Mojang's servers, so ForgeGradle cannot set up Minecraft. The code was compiled (0 errors) against hand-written stubs of the 1.20.1 API it uses, which verifies internal consistency (types, generics, overrides, my own APIs) but **not** that every Minecraft/Forge signature matches the real one. Expect possibly a few signature fixes on first real build. |
| Runtime behaviour in Minecraft | **Not verified.** The game could not be launched here. GameTests (`ScientiaGameTests`) are provided for the circuit, open circuit, balance and sealed-vessel loops; run them with `./gradlew runGameTestServer`. |

To complete verification, allow these hosts in the environment's network settings and run
`./gradlew build runGameTestServer`: `maven.minecraftforge.net`, `piston-meta.mojang.com`,
`piston-data.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net`.

## Implemented systems

### Foundation
- Gradle/ForgeGradle build, mods.toml, pack.mcmeta, wrapper; the engine sources are compiled into the
  mod and its tests run in the mod's `test` task; `check` also runs the asset validator.
- Deferred registers for 21 blocks, 34 items (21 block items + 13), 14 block entity types, one menu
  type, one creative tab; COMMON (simulation intervals, circuit size limit) and CLIENT (difficulty,
  units, HUD, particles, high contrast) configs.
- Static science database loaded from the jar on both sides and validated at start-up.

### Journal, research, persistence, networking
- Player capability holding the journal (facts, research, experiments, discovered elements, 256
  measurements, hypotheses, 64 notes, favourites), persisted as JSON in player NBT, copied on death and
  End return, synchronised on login/respawn/dimension change.
- Research completes only from facts produced by real actions (26 nodes across observation,
  chemistry, electricity, thermal, optics, physics, earth science, biology, mathematics, engineering);
  21 workbench recipes are gated by research and validated on the server.
- SimpleChannel packets: journal sync (S→C); machine actions, workbench crafting, virtual-apparatus
  observations, journal edits (C→S). Every C→S packet is validated: open menu for that block,
  distance, research state, parameter ranges, server-side recomputation of results.

### Chemistry and materials
- 118-element periodic table; 37 substances; 29 vanilla/mod item materials with stated abstractions;
  14 reactions; 28 element emission spectra; 8 microscope specimens.
- Reaction Vessel: inputs by slot (beakers, gas canisters, reagent items), stopper, built-in balance,
  thermometer and pressure gauge, filtration, mass-accounting runs, hypothesis evaluation (mass, pH).
- Laboratory Heater + Condenser: heating/cooling with latent heat, ice melting, boiling-point
  elevation, evaporation and distillation, salt recovery, jackets with real conductivities.
- Electrolysis Station: counter-EMF cell in the circuit, Faraday's law, brine vs water, gas canisters.
- Insulated Kiln: energy-balance smelting of vanilla recipes and limestone → quicklime with mass accounting.
- Material Analyzer: emission spectrum and conductivity probe. Microscope with focus and scale bar.
- Instruments: thermometer, multimeter (V, I, P, R, V/I check), pH meter with indicator descriptions.

### Physics and mathematics
- DC circuits: wire, battery, resistor, switch (redstone control), lamp, motor, generator, solar panel,
  electrolysis cell; nodal analysis; series/parallel detection; short-circuit smoke; battery charging.
- Data Recorder: logs any adjacent instrument/component, plots with uncertainty bars, least-squares
  fit, cooling-constant analysis for insulation experiments.
- Optical Bench (reflection, refraction/TIR, thin lens with ray diagram, prism dispersion) and Motion
  Track (incline kinematics, energy, ramp efficiency) as interactive simulations.
- Calculator (units, constants, sig figs, fractions, RAD/DEG), unit converter, formula solver over 27
  formulas.

### Visual identity and UI
- One design system (`UiKit`): graphite panels, steel borders, cyan/amber/green/violet accents, every
  coloured state also given as text or a symbol; optional high-contrast mode.
- Journal tabs: research tree (pan, prerequisites, requirement check-list), periodic table (18-column
  layout, lanthanide/actinide rows, category filter, search, discovered markers, animated Bohr model
  with tilt), matter database, experiments (+ hypotheses), data (history + plot), formulas, glossary, notes.
- Machine screens show only live synchronised state, with explanations at three depths.
- Contextual HUD while holding an instrument.

### Blender-generated assets
Generated by `assets_source/blender/generate_all.py` (procedural geometry in Minecraft pixel space,
outward-normal enforcement, atlas UV projection, custom OBJ/MTL writer for `forge:obj`):

| Model | Notes |
|---|---|
| scientific_workbench, precision_balance (+ `_pan` part), graduated_cylinder | lathe glassware, draft shield |
| lab_heater (+ `_coil` emissive part), condenser, reaction_vessel (+ `_stopper` part) | swept tubing, revolved flasks |
| electrolysis_station, material_analyzer, microscope, data_recorder, optical_bench | |
| insulated_kiln, insulated_kiln_active, motion_track | active state shows a glowing spy window |
| battery, electric_motor (+ `_rotor` part), thermal_generator (+ `_rotor` part), solar_panel | rotating parts animated by renderers |
| resistor_10/47/100/470/1000 | real colour-code bands |
| circuit_switch_open/closed, filament_lamp_off/on | |

Textures: 256 px block atlas with 47 procedural 16 px tiles (metals, glass, ceramics, screens with
graph/spectrum/digits, dial, vents, hazard stripes, resistor bands, solar cells, PCB, labels),
15 item icons (with filled variants), 8 microscope specimen images, liquid and wire textures.
Copper wire uses JSON multipart models.

## Tests performed

| Test | Result |
|---|---|
| `./gradlew -p science-core test` (46 tests: units, parser, sig figs, fractions, fits, formulas, molar masses, balance validation, closed/open vessel mass accounting, unknown combinations, thresholds, Faraday, neutralisation heat, pH values, indicators, circuits, short/open circuits, heating time vs Q = mcΔT, boiling and melting plateaus, insulation, incline energy balance, optics, ideal gas, journal unlocking and persistence, full data validation) | pass |
| `python3 tools/validate_assets.py` | pass |
| Blender pipeline with previews | pass (30 previews inspected; fixed a mirrored-label UV bug found this way) |
| Forge layer compiled against API stubs | 0 errors |
| Real Forge compile / game launch / GameTests | **not run** (network policy) |

## Development checklist

- [x] Phase 1 — repository audit, build, registration architecture, data framework, config, persistence, GUI infrastructure
- [x] Phase 2 — journal, periodic table, element/compound database, material identification, workbench, reaction engine, instruments
- [x] Phase 3 — calculator, unit conversion, measurements, graphs, motion and energy experiments, thermal behaviour, circuits
- [x] Phase 4 — chemistry apparatus, heating/cooling, filtration, distillation, electrolysis, microscope, spectrometer, generator, motor
- [x] Phase 5 — research branches, 23 experiments, explanations, hypotheses, journal history
- [x] Phase 6 — procedural modelling toolkit, machine models, textures, animated parts, export, previews, asset validation
- [~] Phase 7 — world integration, UI, feedback, performance limits done; accessibility partial (see below)
- [~] Phase 8 — automated tests and docs done; real build and runtime QA blocked by network access

## Not implemented / known technical limitations

- Magnetism (field visualisation, electromagnets, induction), telescopes, centrifuge, pump, automated
  laboratory, programmable controller, research computer and molecule visualisation are not implemented.
- Liquids move as beaker items rather than through Forge fluid capabilities; item automation works via
  `IItemHandler` on every machine.
- The atom view is a 2-D animated Bohr diagram with adjustable tilt, not a free 3-D rotation.
- UI scale follows Minecraft's GUI scale (no separate setting); sounds are vanilla sound events.
- Circuit networks are re-solved every 5 ticks from each loaded source (bounded by `maxCircuitBlocks`);
  very large networks cost O(n³) per solve.
- Text in screens is English and partly hard-coded (block/item names and chat messages are localised).
- Data files are loaded from the jar, not from datapacks.

## Launching

```bash
./gradlew runClient            # dev client with the mod
./gradlew build                # jar in build/libs/
./gradlew runGameTestServer    # in-world tests
```

## Regenerating assets

```bash
pip install bpy==5.2.2         # or use a Blender install: blender -b -P assets_source/blender/generate_all.py
python assets_source/blender/generate_all.py
python3 tools/gen_resources.py
python3 tools/validate_assets.py
```
