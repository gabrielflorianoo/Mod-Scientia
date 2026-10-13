package com.scientia.blockentity;

import com.scientia.config.ScientiaConfig;
import com.scientia.core.chem.AcidBaseSolver;
import com.scientia.core.chem.Mixture;
import com.scientia.core.chem.ReactionEngine;
import com.scientia.core.chem.Substance;
import com.scientia.core.chem.SubstanceDatabase;
import com.scientia.core.experiment.HypothesisEvaluator;
import com.scientia.core.math.Constants;
import com.scientia.core.physics.ThermalBody;
import com.scientia.item.BeakerItem;
import com.scientia.item.GasCanisterItem;
import com.scientia.journal.JournalService;
import com.scientia.registry.ModBlockEntities;
import com.scientia.util.Hazards;
import com.scientia.util.Liquids;
import com.scientia.util.Sci;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Glass reaction vessel with a stopper, a built-in balance (±0.01 g), a thermometer and a pressure gauge.
 * Reactions come only from the reaction database. With the stopper in, gases stay inside (mass is
 * conserved and pressure rises); without it, gas above its solubility escapes and the mass decreases.
 */
public class ReactionVesselBlockEntity extends MachineBlockEntity implements Measurable, MixtureHolder {
    public static final int SLOT_IN = 0, SLOT_OUT = 1;
    public static final int ACTION_STOPPER = 0, ACTION_FILTER = 1, ACTION_RECORD_MASS = 2, ACTION_EMPTY = 3, ACTION_RECORD_PRESSURE = 4;
    public static final double HEADSPACE_L = 1.0; // a sturdy, pressure-rated flask (game simplification)
    public static final double MAX_CONTENTS_G = 1200;
    public static final double BALANCE_UNCERTAINTY = 0.01;
    /** A thick-walled flask bursts above about 4.5 atm; there is no pressure relief valve. */
    public static final double BURST_PRESSURE_PA = 450_000;
    /** Glass cracks when liquid more than ~150 K colder hits it while it is above ~450 K (thermal shock). */
    public static final double SHOCK_DELTA_K = 150, SHOCK_MIN_K = 450;
    public static final double LOSS_COEFFICIENT = 0.5;

    private final Mixture contents = new Mixture();
    private double temperatureK = Double.NaN;
    private boolean closed;
    private double airMoles;
    private double sealTemperatureK;
    private double pendingHeatJ;
    private int heatedTicks;
    // Experiment run tracking
    private boolean runActive, runClosed;
    private double runMassBefore, runPhBefore;
    private int idleTicks;
    private final Set<String> runReactions = new HashSet<>();
    private final List<String> recentReactions = new ArrayList<>();
    private int reactingTicks;
    private boolean shockPending;

    public ReactionVesselBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REACTION_VESSEL.get(), pos, state, 2);
    }

    @Override
    public List<SlotDef> slots() {
        return List.of(new SlotDef(SLOT_IN, 16, 60, false, "Input"), new SlotDef(SLOT_OUT, 16, 104, true, "Output"));
    }

    private SubstanceDatabase subs() {
        return Sci.db().substances();
    }

    public double massGrams() {
        return contents.massGrams(subs());
    }

    /** Balance reading rounded to 0.01 g. */
    public double massReading() {
        return Math.round(massGrams() * 100) / 100.0;
    }

    public boolean closed() {
        return closed;
    }

    public boolean reacting() {
        return reactingTicks > 0;
    }

    public List<String> recentReactions() {
        return recentReactions;
    }

    /** Why nothing happens (reactivity series), shown when no reaction runs; null if not applicable. */
    public String noReactionNote() {
        return com.scientia.core.chem.ReactivitySeries.explainNoReaction(contents);
    }

    public double pH() {
        return AcidBaseSolver.pH(contents, subs());
    }

    public void addExternalHeat(double joules) {
        pendingHeatJ += joules;
        heatedTicks = 40;
    }

    private double ambient() {
        return level == null ? 293.15 : Sci.ambientK(level, worldPosition);
    }

    @Override
    public double temperatureK() {
        return Double.isNaN(temperatureK) ? ambient() : temperatureK;
    }

    @Override
    public Mixture mixture() {
        return contents;
    }

    /** Moles of undissolved gas in the headspace (excluding air). */
    public double gasMoles() {
        double litres = contents.waterLitres(subs());
        double n = 0;
        for (var e : contents.amounts().entrySet()) {
            Substance s = subs().get(e.getKey());
            if (s == null || e.getKey().equals("water") || !ReactionEngine.isGasAt(s, temperatureK())) continue;
            double capacity = litres <= 1e-6 ? 0 : s.solubility() == null ? Double.POSITIVE_INFINITY : s.solubility() * litres;
            n += Math.max(0, e.getValue() - capacity);
        }
        return n;
    }

    /** Headspace pressure (Pa) from the ideal gas law; 1 atm when open. */
    public double pressurePa() {
        if (!closed) return Constants.STANDARD_PRESSURE;
        return (airMoles + gasMoles()) * Constants.GAS_CONSTANT * temperatureK() / (HEADSPACE_L / 1000.0);
    }

    // ------------------------------------------------------------------ input processing

    @Override
    public void serverTick() {
        super.serverTick();
        if (ticks % 5 == 0) processInput();
        if (heatedTicks > 0) heatedTicks--;
        if (reactingTicks > 0 && --reactingTicks == 0) requestSync();
        if (ticks % ScientiaConfig.THERMAL_INTERVAL.get() == 0) stepThermal(ScientiaConfig.THERMAL_INTERVAL.get() / 20.0);
        if (ticks % ScientiaConfig.REACTION_INTERVAL.get() == 0) stepReactions(ScientiaConfig.REACTION_INTERVAL.get() / 20.0);
    }

    private void processInput() {
        ItemStack in = items.getStackInSlot(SLOT_IN);
        if (in.isEmpty()) return;
        ItemStack out = items.getStackInSlot(SLOT_OUT);
        if (in.getItem() instanceof BeakerItem) {
            if (!out.isEmpty()) return;
            if (!BeakerItem.isEmpty(in)) {
                Mixture add = Sci.contentsOf(in);
                if (massGrams() + add.massGrams(subs()) > MAX_CONTENTS_G) return;
                addContents(add, ambient());
                items.setStackInSlot(SLOT_OUT, new ItemStack(in.getItem()));
            } else {
                Mixture taken = Liquids.takeLiquid(contents, BeakerItem.CAPACITY_ML * 0.997);
                if (taken.isEmpty()) return;
                items.setStackInSlot(SLOT_OUT, BeakerItem.filled(in.getItem(), taken));
            }
            in.shrink(1);
            items.setStackInSlot(SLOT_IN, in);
        } else if (in.getItem() instanceof GasCanisterItem && Sci.contentsOf(in).isEmpty()) {
            // An empty canister collects the main undissolved gas from a sealed vessel's headspace.
            if (!closed || !out.isEmpty()) return;
            String best = null;
            double bestN = 0;
            double litres = contents.waterLitres(subs());
            for (var e : contents.amounts().entrySet()) {
                Substance s = subs().get(e.getKey());
                if (s == null || e.getKey().equals("water") || !ReactionEngine.isGasAt(s, temperatureK())) continue;
                double cap = litres <= 1e-6 ? 0 : s.solubility() == null ? Double.POSITIVE_INFINITY : s.solubility() * litres;
                double free = e.getValue() - cap;
                if (free > bestN) {
                    bestN = free;
                    best = e.getKey();
                }
            }
            if (best == null || bestN < 0.002) return;
            double take = Math.min(bestN, ElectrolysisStationBlockEntity.CANISTER_MOL);
            contents.remove(best, take);
            items.setStackInSlot(SLOT_OUT, GasCanisterItem.filled(in.getItem(), best, take));
            in.shrink(1);
            items.setStackInSlot(SLOT_IN, in);
        } else if (in.getItem() instanceof GasCanisterItem) {
            Mixture gas = Sci.contentsOf(in);
            if (gas.isEmpty() || !out.isEmpty()) return;
            addContents(gas, ambient());
            items.setStackInSlot(SLOT_OUT, new ItemStack(in.getItem()));
            in.shrink(1);
            items.setStackInSlot(SLOT_IN, in);
        } else {
            var add = Sci.substancesForItem(in);
            if (add.isEmpty()) return;
            Mixture m = new Mixture(add);
            if (massGrams() + m.massGrams(subs()) > MAX_CONTENTS_G) return;
            addContents(m, ambient());
            in.shrink(1);
            items.setStackInSlot(SLOT_IN, in);
        }
        level.playSound(null, worldPosition, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.5f, 1.2f);
        requestSync();
    }

    private void addContents(Mixture add, double addTemp) {
        if (!contents.isEmpty() && temperatureK() > SHOCK_MIN_K && temperatureK() - addTemp > SHOCK_DELTA_K && add.contains("water")
                && serverLevel() != null) {
            shockPending = true;
        }
        if (contents.isEmpty() || Double.isNaN(temperatureK)) {
            temperatureK = addTemp;
        } else {
            double c1 = heatCapacity(contents), c2 = heatCapacity(add);
            if (c1 + c2 > 0) temperatureK = (c1 * temperatureK + c2 * addTemp) / (c1 + c2);
        }
        add.amounts().forEach((id, n) -> contents.add(id.equals("ice") ? "water" : id, n));
    }

    private static double heatCapacity(Mixture m) {
        return Sci.waterGrams(m) * ThermalBody.WATER_C + Sci.otherHeatCapacity(m) + 1e-6;
    }

    // ------------------------------------------------------------------ simulation

    /** Destroys the vessel; any toxic or flammable gas inside is released at the same time. */
    private void burst(String kind, String what, String why) {
        ServerLevel sl = serverLevel();
        if (sl == null) return;
        releaseGasHazards(sl, contents.get("chlorine"), contents.get("hydrogen"));
        contents.clear();
        Hazards.shatter(sl, worldPosition, kind, what, why);
    }

    private void releaseGasHazards(ServerLevel sl, double chlorine, double hydrogen) {
        if (chlorine > 0.0005) Hazards.toxicGas(sl, worldPosition, "Chlorine", chlorine);
        if (hydrogen > 0.002 && Hazards.ignitionNear(sl, worldPosition)) Hazards.hydrogenFlash(sl, worldPosition, hydrogen);
    }

    private void stepThermal(double dt) {
        if (shockPending) {
            shockPending = false;
            burst("thermal_shock", "The hot vessel cracked when cold liquid was added",
                    "The inner glass surface cooled and contracted suddenly while the rest stayed hot: thermal shock. Let hot glassware cool before adding cold liquids.");
            return;
        }
        if (contents.isEmpty()) {
            temperatureK = Double.NaN;
            pendingHeatJ = 0;
            return;
        }
        double c = heatCapacity(contents) + 60; // + glass vessel
        double t = temperatureK();
        t += (pendingHeatJ - LOSS_COEFFICIENT * (t - ambient()) * dt) / c;
        pendingHeatJ = 0;
        temperatureK = Math.min(t, 1500);
        if (closed) {
            if (Math.abs(temperatureK - sealTemperatureK) >= 20 && gasMoles() + airMoles > 0) {
                JournalService.grant(serverLevel(), lastUser, "observe:pressure_temperature");
            }
            if (pressurePa() > BURST_PRESSURE_PA) {
                burst("overpressure", String.format(Locale.ROOT, "The sealed vessel burst at %.0f kPa", pressurePa() / 1000),
                        "Gas made or heated in a sealed container raises the pressure (PV = nRT) until the glass fails. "
                                + "Never seal a reaction that produces gas, and never heat a closed vessel.");
                return;
            }
        }
        requestSync();
    }

    private void stepReactions(double dt) {
        if (contents.isEmpty()) return;
        Set<String> tags = heatedTicks > 0 ? Set.of("heat") : Set.of();
        double massBefore = massGrams();
        double phBefore = pH();
        ReactionEngine.StepResult r = Sci.db().reactionEngine().step(contents,
                new ReactionEngine.Conditions(temperatureK(), tags, closed, 0), dt);
        ServerLevel sl = serverLevel();
        if (sl != null && !r.escapedMoles().isEmpty()) {
            releaseGasHazards(sl, r.escapedMoles().getOrDefault("chlorine", 0.0), r.escapedMoles().getOrDefault("hydrogen", 0.0));
        }
        if (r.anyReaction()) {
            if (!runActive) {
                runActive = true;
                runClosed = closed;
                runMassBefore = massBefore;
                runPhBefore = phBefore;
                runReactions.clear();
            }
            if (!closed) runClosed = false;
            idleTicks = 0;
            reactingTicks = 30;
            double heat = 0;
            for (ReactionEngine.Event e : r.events()) {
                heat += e.heatReleasedJ();
                String id = e.reaction().id();
                runReactions.add(id);
                recentReactions.remove(id);
                recentReactions.add(0, id);
                while (recentReactions.size() > 4) recentReactions.remove(recentReactions.size() - 1);
                if (sl != null) {
                    JournalService.grant(sl, lastUser, "reaction:" + id);
                    JournalService.grant(sl, lastUser, "reaction:any");
                }
            }
            pendingHeatJ += heat;
            requestSync();
        } else if (runActive) {
            idleTicks += (int) Math.round(dt * 20);
            if (r.escapedGasGrams() == 0 && idleTicks >= 60) finishRun(sl);
        }
    }

    /** A reaction run has finished: compare mass (and pH) before and after. */
    private void finishRun(ServerLevel sl) {
        runActive = false;
        if (sl == null) return;
        ServerPlayer p = JournalService.online(sl, lastUser);
        if (p == null) return;
        double after = massGrams();
        double delta = after - runMassBefore;
        double u = Math.sqrt(2) * BALANCE_UNCERTAINTY;
        String observed = HypothesisEvaluator.classifyChange(delta, u);
        p.displayClientMessage(Component.translatable("scientia.msg.run_mass", Sci.fixed(runMassBefore, 2), Sci.fixed(after, 2),
                String.format(Locale.ROOT, "%+.2f", delta)).withStyle(ChatFormatting.AQUA), false);
        if (runClosed && observed.equals("equal")) {
            JournalService.grant(p, "reaction:closed_mass_conserved");
            JournalService.resolveHypothesis(p, "conservation_of_mass", observed, String.format(Locale.ROOT,
                    "Sealed vessel: %.2f g before, %.2f g after (change %+.2f g, within ±%.2f g). Atoms were rearranged, none were lost.",
                    runMassBefore, after, delta, 2 * u));
        } else if (!runClosed && observed.equals("decrease")) {
            JournalService.grant(p, "reaction:open_mass_loss");
            p.displayClientMessage(Component.translatable("scientia.msg.gas_escaped").withStyle(ChatFormatting.GRAY), false);
        }
        if (runReactions.stream().anyMatch(id -> id.startsWith("neutralization"))) {
            double phAfter = pH();
            if (!Double.isNaN(phAfter) && !Double.isNaN(runPhBefore)) {
                JournalService.resolveHypothesis(p, "acidity_ph", HypothesisEvaluator.classifyChange(phAfter - runPhBefore, 0.05),
                        String.format(Locale.ROOT, "pH changed from %.2f to %.2f as hydroxide ions combined with hydrogen ions to form water.", runPhBefore, phAfter));
            }
        }
    }

    // ------------------------------------------------------------------ actions

    @Override
    public void handleAction(ServerPlayer player, int action, int arg) {
        switch (action) {
            case ACTION_STOPPER -> {
                closed = !closed;
                if (closed) {
                    sealTemperatureK = temperatureK();
                    airMoles = Constants.STANDARD_PRESSURE * (HEADSPACE_L / 1000.0) / (Constants.GAS_CONSTANT * sealTemperatureK);
                } else {
                    // Opening releases undissolved gas immediately.
                    double cl = contents.get("chlorine"), h2 = contents.get("hydrogen");
                    Sci.db().reactionEngine().step(contents, new ReactionEngine.Conditions(temperatureK(), Set.of(), false, 0), 0);
                    runClosed = false;
                    ServerLevel sl = serverLevel();
                    if (sl != null) releaseGasHazards(sl, cl - contents.get("chlorine"), h2 - contents.get("hydrogen"));
                }
                level.playSound(null, worldPosition, closed ? SoundEvents.WOOD_PLACE : SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.5f, 1.6f);
            }
            case ACTION_FILTER -> filter(player);
            case ACTION_RECORD_MASS -> {
                if (contents.isEmpty()) return;
                JournalService.record(player, "mass", "vessel_contents", massReading(), BALANCE_UNCERTAINTY, "g", "reaction_vessel|" + worldPosition.asLong());
            }
            case ACTION_RECORD_PRESSURE -> {
                if (!closed) {
                    player.displayClientMessage(Component.translatable("scientia.msg.open_pressure").withStyle(ChatFormatting.GRAY), false);
                    return;
                }
                JournalService.record(player, "pressure", "vessel_headspace", pressurePa() / 1000.0, 0.5, "kPa", "reaction_vessel");
                JournalService.record(player, "temperature", "vessel_contents", temperatureK(), 0.5, "K", "reaction_vessel");
            }
            case ACTION_EMPTY -> {
                contents.clear();
                temperatureK = Double.NaN;
                runActive = false;
                recentReactions.clear();
            }
            default -> {
            }
        }
        requestSync();
    }

    /** Filtration: undissolved solids are caught; whole 10 g portions are packaged as reagent items. */
    private void filter(ServerPlayer player) {
        if (!items.getStackInSlot(SLOT_OUT).isEmpty()) {
            player.displayClientMessage(Component.translatable("scientia.msg.output_full").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Mixture solids = Liquids.takeSolids(contents);
        if (solids.isEmpty()) {
            player.displayClientMessage(Component.translatable("scientia.msg.nothing_to_filter").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        boolean outputDone = false;
        for (var e : new ArrayList<>(solids.amounts().entrySet())) {
            String id = e.getKey();
            var item = Sci.SOLID_ITEMS.get(id);
            int n = item == null ? 0 : (int) Math.floor(e.getValue() * subs().molarMass(id) / Sci.REAGENT_GRAMS + 1e-9);
            if (n > 0 && !outputDone) {
                items.setStackInSlot(SLOT_OUT, new ItemStack(item.get(), Math.min(n, 64)));
                solids.remove(id, Math.min(n, 64) * Sci.REAGENT_GRAMS / subs().molarMass(id));
                outputDone = true;
            }
        }
        // Residue that cannot be packaged stays on the filter paper and is discarded with it.
        JournalService.grant(player, "separate:filtration");
    }

    @Override
    public List<Reading> readings() {
        return List.of(new Reading("mass", massReading(), BALANCE_UNCERTAINTY, "g"),
                new Reading("temperature", temperatureK(), 0.5, "K"),
                new Reading("pressure", pressurePa() / 1000.0, 0.5, "kPa"));
    }

    @Override
    public void clientTick() {
        super.clientTick();
        if (level == null || !reacting()) return;
        if (level.random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.BUBBLE_POP, worldPosition.getX() + 0.3 + level.random.nextDouble() * 0.4,
                    worldPosition.getY() + 0.5 + level.random.nextDouble() * 0.3, worldPosition.getZ() + 0.3 + level.random.nextDouble() * 0.4, 0, 0.03, 0);
        }
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveState(CompoundTag tag) {
        tag.put("Contents", Sci.writeMixture(contents));
        tag.putDouble("Temp", temperatureK);
        tag.putBoolean("Closed", closed);
        tag.putDouble("Air", airMoles);
        tag.putDouble("SealT", sealTemperatureK);
        tag.putBoolean("Run", runActive);
        tag.putBoolean("RunClosed", runClosed);
        tag.putDouble("RunMass", runMassBefore);
        tag.putDouble("RunPh", runPhBefore);
        tag.putInt("Reacting", reactingTicks);
        ListTag recent = new ListTag();
        for (String s : recentReactions) recent.add(StringTag.valueOf(s));
        tag.put("Recent", recent);
    }

    @Override
    protected void loadState(CompoundTag tag) {
        contents.clear();
        Sci.readMixture(tag.getCompound("Contents")).amounts().forEach(contents::add);
        temperatureK = tag.contains("Temp") ? tag.getDouble("Temp") : Double.NaN;
        closed = tag.getBoolean("Closed");
        airMoles = tag.getDouble("Air");
        sealTemperatureK = tag.getDouble("SealT");
        runActive = tag.getBoolean("Run");
        runClosed = tag.getBoolean("RunClosed");
        runMassBefore = tag.getDouble("RunMass");
        runPhBefore = tag.getDouble("RunPh");
        reactingTicks = tag.getInt("Reacting");
        recentReactions.clear();
        ListTag recent = tag.getList("Recent", 8);
        for (int i = 0; i < recent.size(); i++) recentReactions.add(recent.getString(i));
    }
}
