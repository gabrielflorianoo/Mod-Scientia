package com.scientia.electric;

import com.scientia.ScientiaMod;
import com.scientia.block.FilamentLampBlock;
import com.scientia.config.ScientiaConfig;
import com.scientia.core.experiment.HypothesisEvaluator;
import com.scientia.core.physics.Circuit;
import com.scientia.core.physics.Materials;
import com.scientia.journal.JournalService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Solves every electrical network that contains a source, at a configurable interval. Networks are found
 * by flood fill over connected circuit blocks, converted to a {@link Circuit} netlist (each wire block is a
 * node joined to its neighbours by its real resistance) and solved by nodal analysis. Results drive lamps,
 * motors, electrolysis and battery charge, and are what the multimeter reads.
 */
@Mod.EventBusSubscriber(modid = ScientiaMod.MODID)
public final class CircuitManager {
    /** Last solved operating point of a block: for wires only {@code potential} is meaningful. */
    public record Reading(double voltage, double current, double power, double resistance, double potential, ElectricalBlock.Role role) {
    }

    private static final Map<ServerLevel, CircuitManager> MANAGERS = new WeakHashMap<>();
    private static final double SHORT_CIRCUIT_AMPS = 40;

    /** Wires above their ampacity overheat; sustained overload melts them. */
    public static final double WIRE_WARN_AMPS = 25, WIRE_FUSE_AMPS = 60;
    private final Set<BlockPos> sources = new HashSet<>();
    private final Map<BlockPos, Integer> overload = new HashMap<>();
    private Map<BlockPos, Reading> readings = new HashMap<>();

    public static CircuitManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, l -> new CircuitManager());
    }

    public void addSource(BlockPos pos) {
        sources.add(pos.immutable());
    }

    public void removeSource(BlockPos pos) {
        sources.remove(pos);
    }

    public Reading reading(BlockPos pos) {
        return readings.get(pos);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel sl)) return;
        CircuitManager m = MANAGERS.get(sl);
        if (m != null) m.tick(sl);
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel sl) MANAGERS.remove(sl);
    }

    private void tick(ServerLevel level) {
        int interval = ScientiaConfig.CIRCUIT_INTERVAL.get();
        if (level.getGameTime() % interval != 0) return;
        double dt = interval / 20.0;
        Map<BlockPos, Reading> next = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos src : List.copyOf(sources)) {
            if (!level.isLoaded(src)) continue;
            BlockState s = level.getBlockState(src);
            if (!(s.getBlock() instanceof ElectricalBlock eb) || eb.role(s) != ElectricalBlock.Role.SOURCE) {
                sources.remove(src);
                continue;
            }
            if (visited.contains(src)) continue;
            Network network = Network.discover(level, src, ScientiaConfig.MAX_CIRCUIT_SIZE.get());
            visited.addAll(network.members);
            network.solveAndApply(level, dt, next);
            checkOverloads(level, network, next, interval);
        }
        for (BlockPos p : readings.keySet()) {
            if (!next.containsKey(p) && level.isLoaded(p)) applyUnpowered(level, p);
        }
        readings = next;
    }

    /**
     * Joule heating (P = I²R): wires carrying more than ~25 A (1 mm² copper is rated ~10–16 A) overheat and melt
     * after a few seconds, faster above 60 A; lamps run far above their rating burn out their filament.
     */
    private void checkOverloads(ServerLevel level, Network network, Map<BlockPos, Reading> readings, int interval) {
        for (BlockPos p : network.members) {
            Reading r = readings.get(p);
            if (r == null) continue;
            BlockState s = level.getBlockState(p);
            if (r.role() == ElectricalBlock.Role.WIRE) {
                double amps = network.wireCurrents.getOrDefault(p, 0.0);
                if (amps > WIRE_WARN_AMPS) {
                    int t = overload.merge(p, interval, Integer::sum);
                    int limit = amps > WIRE_FUSE_AMPS ? 20 : 100;
                    if (t % 20 < interval) level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0.0);
                    if (t >= limit) {
                        overload.remove(p);
                        com.scientia.util.Hazards.burnOut(level, p, amps > WIRE_FUSE_AMPS);
                        com.scientia.util.Hazards.lessonNearby(level, p, 12, "wire_burnout",
                                String.format(Locale.ROOT, "A copper wire melted carrying %.0f A", amps),
                                "Power lost in a wire is P = I²R. A 1 mm² copper wire is rated for about 10–16 A; a short circuit drives far more "
                                        + "current through the battery's tiny internal resistance. Add a load (lamp, resistor) or a fuse.");
                    }
                } else {
                    overload.remove(p);
                }
            } else if (s.getBlock() instanceof FilamentLampBlock && !s.getValue(FilamentLampBlock.BROKEN)) {
                if (Math.abs(r.power()) > 2 * FilamentLampBlock.RATED_POWER) {
                    int t = overload.merge(p, interval, Integer::sum);
                    if (t >= 10) {
                        overload.remove(p);
                        level.setBlock(p, s.setValue(FilamentLampBlock.BROKEN, true).setValue(FilamentLampBlock.LEVEL, 0), 3);
                        level.playSound(null, p, net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 0.4f, 1.8f);
                        com.scientia.util.Hazards.lessonNearby(level, p, 12, "filament_burnout",
                                String.format(Locale.ROOT, "The lamp burnt out at %.1f W (rated 6 W)", Math.abs(r.power())),
                                "Power grows with the square of voltage (P = V²/R): doubling the voltage quadruples the power and the filament melts. "
                                        + "Match the lamp's voltage rating, or add resistance in series.");
                    }
                } else {
                    overload.remove(p);
                }
            }
        }
    }

    private static void applyUnpowered(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.getBlock() instanceof FilamentLampBlock && s.getValue(FilamentLampBlock.LEVEL) != 0) {
            level.setBlock(p, s.setValue(FilamentLampBlock.LEVEL, 0), 3);
        }
        BlockEntity be = level.getBlockEntity(p);
        if (be instanceof ElectricalLoad load) load.onSolved(0, 0, 0, 0);
    }

    // =====================================================================================

    /** One connected network of circuit blocks. */
    static final class Network {
        final List<BlockPos> members = new ArrayList<>();
        final Set<BlockPos> memberSet = new HashSet<>();
        /** Largest current (A) through any link of each wire block in the last solve. */
        final Map<BlockPos, Double> wireCurrents = new HashMap<>();
        final BlockPos start;
        boolean truncated;

        private Network(BlockPos start) {
            this.start = start;
        }

        static Network discover(ServerLevel level, BlockPos start, int maxSize) {
            Network n = new Network(start);
            Set<BlockPos> seen = new HashSet<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            seen.add(start);
            while (!queue.isEmpty()) {
                BlockPos p = queue.poll();
                n.members.add(p);
                n.memberSet.add(p);
                if (n.members.size() >= maxSize) {
                    n.truncated = true;
                    break;
                }
                BlockState s = level.getBlockState(p);
                if (!(s.getBlock() instanceof ElectricalBlock eb)) continue;
                for (Direction d : Direction.values()) {
                    if (!eb.hasTerminal(s, d)) continue;
                    BlockPos q = p.relative(d);
                    if (seen.contains(q) || !level.isLoaded(q)) continue;
                    BlockState t = level.getBlockState(q);
                    if (t.getBlock() instanceof ElectricalBlock other && other.hasTerminal(t, d.getOpposite())) {
                        seen.add(q);
                        queue.add(q);
                    }
                }
            }
            return n;
        }

        /** Netlist built from the members; {@code excluded} components are left out (open). */
        private final class Built {
            final Circuit circuit = new Circuit();
            final Map<BlockPos, Integer> wireNode = new HashMap<>();
            final Map<BlockPos, int[]> terminals = new HashMap<>();
            final Map<BlockPos, Circuit.Component> elements = new HashMap<>();
            final List<Object[]> wireLinks = new ArrayList<>();
            int ground = -1;

            Built(ServerLevel level, Set<BlockPos> excluded) {
                Map<String, Integer> junctions = new HashMap<>();
                double rWire = Materials.copperWireBlockResistance();
                for (BlockPos p : members) {
                    BlockState s = level.getBlockState(p);
                    if (s.getBlock() instanceof ElectricalBlock eb && eb.role(s) == ElectricalBlock.Role.WIRE) {
                        wireNode.put(p, circuit.addNode());
                    }
                }
                for (BlockPos p : wireNode.keySet()) {
                    for (Direction d : new Direction[]{Direction.EAST, Direction.SOUTH, Direction.UP}) {
                        Integer q = wireNode.get(p.relative(d));
                        if (q != null) wireLinks.add(new Object[]{p, p.relative(d), circuit.resistor(wireNode.get(p), q, rWire, p)});
                    }
                }
                for (BlockPos p : members) {
                    BlockState s = level.getBlockState(p);
                    if (!(s.getBlock() instanceof ElectricalBlock eb) || eb.role(s) == ElectricalBlock.Role.WIRE) continue;
                    Direction front = eb.front(s);
                    int a = terminalNode(level, p, front, junctions);
                    int b = terminalNode(level, p, front.getOpposite(), junctions);
                    terminals.put(p, new int[]{a, b});
                    if (excluded.contains(p)) continue;
                    BlockEntity be = level.getBlockEntity(p);
                    switch (eb.role(s)) {
                        case LOAD -> {
                            if (eb.conducts(s)) elements.put(p, circuit.resistor(a, b, eb.resistance(s, level, p), p));
                        }
                        case SWITCH -> {
                            if (eb.conducts(s)) elements.put(p, circuit.resistor(a, b, eb.resistance(s, level, p), p));
                        }
                        case SOURCE -> {
                            if (be instanceof ElectricalSource src) {
                                elements.put(p, circuit.source(a, b, src.emf(), src.internalResistance(), p));
                                if (p.equals(start)) ground = b;
                            }
                        }
                        case CELL -> {
                            if (be instanceof ElectricalLoad cell) {
                                elements.put(p, circuit.source(a, b, cell.counterEmf(), eb.resistance(s, level, p), p));
                            }
                        }
                        default -> {
                        }
                    }
                }
                if (ground < 0) ground = 0;
            }

            private int terminalNode(ServerLevel level, BlockPos p, Direction side, Map<String, Integer> junctions) {
                BlockPos q = p.relative(side);
                Integer w = wireNode.get(q);
                if (w != null) return w;
                BlockState t = level.getBlockState(q);
                if (memberSet.contains(q) && t.getBlock() instanceof ElectricalBlock other && other.hasTerminal(t, side.getOpposite())) {
                    String key = p.compareTo(q) < 0 ? p.asLong() + ":" + q.asLong() : q.asLong() + ":" + p.asLong();
                    return junctions.computeIfAbsent(key, k -> circuit.addNode());
                }
                return circuit.addNode(); // unconnected terminal
            }
        }

        void solveAndApply(ServerLevel level, double dt, Map<BlockPos, Reading> out) {
            if (members.isEmpty()) return;
            Set<BlockPos> excluded = new HashSet<>();
            Built built = null;
            Circuit.Solution sol = null;
            // Electrolysis cells conduct only when driven above their counter-EMF: drop cells that would act as sources.
            for (int iter = 0; iter < 4; iter++) {
                built = new Built(level, excluded);
                if (built.circuit.nodeCount() == 0) return;
                sol = built.circuit.solve(built.ground);
                boolean changed = false;
                for (var e : built.elements.entrySet()) {
                    BlockState s = level.getBlockState(e.getKey());
                    if (s.getBlock() instanceof ElectricalBlock eb && eb.role(s) == ElectricalBlock.Role.CELL && sol.current(e.getValue()) > -1e-6) {
                        excluded.add(e.getKey());
                        changed = true;
                    }
                }
                if (!changed) break;
            }
            wireCurrents.clear();
            for (Object[] link : built.wireLinks) {
                double amps = Math.abs(sol.current((Circuit.Component) link[2]));
                wireCurrents.merge((BlockPos) link[0], amps, Math::max);
                wireCurrents.merge((BlockPos) link[1], amps, Math::max);
            }
            List<BlockPos> litLamps = new ArrayList<>();
            double maxSourceCurrent = 0;
            for (BlockPos p : members) {
                BlockState s = level.getBlockState(p);
                if (!(s.getBlock() instanceof ElectricalBlock eb)) continue;
                ElectricalBlock.Role role = eb.role(s);
                if (role == ElectricalBlock.Role.WIRE) {
                    out.put(p, new Reading(0, Double.NaN, 0, Materials.copperWireBlockResistance(), sol.potential(built.wireNode.get(p)), role));
                    continue;
                }
                int[] t = built.terminals.get(p);
                Circuit.Component c = built.elements.get(p);
                double v = sol.potential(t[0]) - sol.potential(t[1]);
                double i = c == null ? 0 : sol.current(c);
                double power = c == null ? 0 : sol.power(c);
                double r = c == null ? Double.POSITIVE_INFINITY : c.resistance();
                BlockEntity be = level.getBlockEntity(p);
                switch (role) {
                    case SOURCE -> {
                        if (be instanceof ElectricalSource src) src.onSolved(i, v, dt);
                        maxSourceCurrent = Math.max(maxSourceCurrent, Math.abs(i));
                    }
                    case CELL -> {
                        double consumed = -i; // current entering the + terminal
                        if (be instanceof ElectricalLoad load) load.onSolved(v, Math.max(0, consumed), Math.max(0, v * consumed), dt);
                        i = Math.max(0, consumed);
                        power = v * i;
                    }
                    case LOAD -> {
                        if (be instanceof ElectricalLoad load) load.onSolved(v, i, power, dt);
                        if (s.getBlock() instanceof FilamentLampBlock) {
                            int lvl = FilamentLampBlock.levelFor(power);
                            if (s.getValue(FilamentLampBlock.LEVEL) != lvl) level.setBlock(p, s.setValue(FilamentLampBlock.LEVEL, lvl), 3);
                            if (lvl > 0) litLamps.add(p);
                        }
                    }
                    default -> {
                    }
                }
                out.put(p, new Reading(v, i, power, r, sol.potential(t[0]), role));
            }
            if (maxSourceCurrent > SHORT_CIRCUIT_AMPS && level.getGameTime() % 40 == 0) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, start.getX() + 0.5, start.getY() + 1.0, start.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.01);
            }
            if (!litLamps.isEmpty()) observeLamps(level, litLamps, out, built, excluded);
        }

        /** Grants circuit facts from the solved values: a lit lamp, and series/parallel arrangements. */
        private void observeLamps(ServerLevel level, List<BlockPos> lamps, Map<BlockPos, Reading> out, Built built, Set<BlockPos> excluded) {
            List<ServerPlayer> nearby = new ArrayList<>();
            BlockPos ref = lamps.get(0);
            for (Player pl : level.players()) {
                if (pl instanceof ServerPlayer sp && sp.blockPosition().distSqr(ref) <= 16 * 16) nearby.add(sp);
            }
            if (nearby.isEmpty()) return;
            for (ServerPlayer sp : nearby) JournalService.grant(sp, "circuit:closed");
            if (lamps.size() < 2) return;
            double emf = 0;
            for (BlockPos p : members) {
                if (level.getBlockEntity(p) instanceof ElectricalSource src) emf = Math.max(emf, src.emf());
            }
            for (int x = 0; x < lamps.size(); x++) {
                for (int y = x + 1; y < lamps.size(); y++) {
                    Reading a = out.get(lamps.get(x)), b = out.get(lamps.get(y));
                    boolean sameCurrent = Math.abs(a.current() - b.current()) <= 1e-3 * Math.max(Math.abs(a.current()), 1e-6);
                    boolean sameVoltage = Math.abs(a.voltage() - b.voltage()) <= 0.02 * Math.max(Math.abs(a.voltage()), 1e-6);
                    boolean series = sameCurrent && Math.abs(a.voltage()) < 0.75 * emf;
                    boolean parallel = sameVoltage && Math.abs(a.voltage()) >= 0.75 * emf;
                    if (series) for (ServerPlayer sp : nearby) JournalService.grant(sp, "circuit:series");
                    if (parallel) {
                        for (ServerPlayer sp : nearby) {
                            if (!JournalService.journal(sp).hasFact("circuit:parallel")) evaluateParallelHypothesis(level, sp, lamps.get(y), out);
                            JournalService.grant(sp, "circuit:parallel");
                        }
                    }
                }
            }
        }

        /** Compares the real source current with and without one of the parallel lamps. */
        private void evaluateParallelHypothesis(ServerLevel level, ServerPlayer sp, BlockPos lamp, Map<BlockPos, Reading> out) {
            Reading withLamp = out.get(start);
            if (withLamp == null) return;
            Built without = new Built(level, Set.of(lamp));
            Circuit.Solution s2 = without.circuit.solve(without.ground);
            Circuit.Component src = without.elements.get(start);
            if (src == null) return;
            double i1 = withLamp.current(), i0 = s2.current(src);
            String observed = HypothesisEvaluator.classifyChange(i1 - i0, 1e-3);
            JournalService.resolveHypothesis(sp, "series_parallel", observed, String.format(Locale.ROOT,
                    "Source current: %.3f A with both lamps, %.3f A with one. Each parallel branch draws its own current, so the total increases.", i1, i0));
        }
    }
}
