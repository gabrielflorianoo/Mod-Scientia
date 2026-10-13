package com.scientia.blockentity;

import com.scientia.core.experiment.HypothesisEvaluator;
import com.scientia.core.research.JournalData;
import com.scientia.core.stats.DataSeries;
import com.scientia.core.stats.LinearFit;
import com.scientia.electric.CircuitManager;
import com.scientia.electric.ElectricalBlock;
import com.scientia.journal.JournalService;
import com.scientia.registry.ModBlockEntities;
import com.scientia.util.Sci;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Logs a quantity from an adjacent instrument or circuit component at a fixed interval and plots it.
 * It can fit a straight line (least squares) and, next to a Laboratory Heater that is switched off,
 * estimate the cooling constant k = −(dT/dt)/(T − T_env) used in the insulation experiments.
 */
public class DataRecorderBlockEntity extends MachineBlockEntity {
    public static final int ACTION_TOGGLE = 0, ACTION_CLEAR = 1, ACTION_INTERVAL = 2, ACTION_FIT = 3, ACTION_COOLING = 4, ACTION_QUANTITY = 5;
    public static final int[] INTERVALS = {1, 2, 5, 10};
    public static final int CAPACITY = 120;

    private boolean recording;
    private int intervalIndex = 0;
    private int quantityIndex = 0;
    private long startTick;
    private String quantity = "", unit = "", sourceName = "";
    private DataSeries series = new DataSeries("time", "s", "", "", CAPACITY);

    public DataRecorderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DATA_RECORDER.get(), pos, state, 0);
    }

    public DataSeries series() {
        return series;
    }

    public boolean recording() {
        return recording;
    }

    public int intervalSeconds() {
        return INTERVALS[intervalIndex];
    }

    public String quantity() {
        return quantity;
    }

    public String unit() {
        return unit;
    }

    public String sourceName() {
        return sourceName;
    }

    /** Finds the readings of the first adjacent measurable block. */
    private List<Measurable.Reading> neighbourReadings(StringBuilder name) {
        for (Direction d : Direction.values()) {
            BlockPos p = worldPosition.relative(d);
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof Measurable m && !(be instanceof DataRecorderBlockEntity)) {
                name.append(level.getBlockState(p).getBlock().getDescriptionId());
                return m.readings();
            }
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof ElectricalBlock eb && eb.role(s) != ElectricalBlock.Role.WIRE && level instanceof ServerLevel sl) {
                CircuitManager.Reading r = CircuitManager.get(sl).reading(p);
                name.append(s.getBlock().getDescriptionId());
                double v = r == null ? 0 : r.voltage(), i = r == null ? 0 : r.current();
                return List.of(new Measurable.Reading("voltage", v, 0.01, "V"), new Measurable.Reading("current", i, 0.001, "A"),
                        new Measurable.Reading("power", r == null ? 0 : r.power(), 0.01, "W"));
            }
        }
        return List.of();
    }

    @Override
    public void serverTick() {
        super.serverTick();
        if (!recording || ticks % (20 * intervalSeconds()) != 0) return;
        StringBuilder name = new StringBuilder();
        List<Measurable.Reading> rs = neighbourReadings(name);
        if (rs.isEmpty()) return;
        Measurable.Reading r = rs.get(Math.min(quantityIndex, rs.size() - 1));
        if (!r.quantity().equals(quantity) || !r.unit().equals(unit)) {
            series = new DataSeries("time", "s", r.quantity(), r.unit(), CAPACITY);
            quantity = r.quantity();
            unit = r.unit();
            startTick = level.getGameTime();
        }
        sourceName = name.toString();
        double t = (level.getGameTime() - startTick) / 20.0;
        series.add(t, r.value(), r.uncertainty());
        requestSync();
    }

    @Override
    public void handleAction(ServerPlayer player, int action, int arg) {
        switch (action) {
            case ACTION_TOGGLE -> {
                recording = !recording;
                if (recording && series.size() == 0) startTick = level.getGameTime();
            }
            case ACTION_CLEAR -> {
                series = new DataSeries("time", "s", quantity, unit, CAPACITY);
                startTick = level.getGameTime();
            }
            case ACTION_INTERVAL -> intervalIndex = (intervalIndex + 1) % INTERVALS.length;
            case ACTION_QUANTITY -> {
                quantityIndex = (quantityIndex + 1) % 3;
                quantity = "";
            }
            case ACTION_FIT -> fit(player);
            case ACTION_COOLING -> cooling(player);
            default -> {
            }
        }
        requestSync();
    }

    private void fit(ServerPlayer player) {
        if (series.size() < 5) {
            player.displayClientMessage(Component.translatable("scientia.msg.need_points", 5).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        LinearFit f = series.fit();
        if (f == null) return;
        player.displayClientMessage(Component.literal(String.format(Locale.ROOT, "Best fit: %s = (%.4g ± %.2g) %s/s × t + %.4g %s,  R² = %.4f",
                quantity, f.slope(), f.slopeError(), unit, f.intercept(), unit, f.rSquared())).withStyle(ChatFormatting.AQUA), false);
        JournalService.record(player, "slope", quantity, f.slope(), f.slopeError(), unit + "/s", "data_recorder");
        JournalService.grant(player, "analyze:graph_fit");
    }

    private void cooling(ServerPlayer player) {
        LabHeaterBlockEntity heater = null;
        for (Direction d : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(d)) instanceof LabHeaterBlockEntity h) heater = h;
        }
        if (heater == null || !quantity.equals("temperature")) {
            player.displayClientMessage(Component.translatable("scientia.msg.cooling_needs_heater").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        if (heater.isHeating()) {
            player.displayClientMessage(Component.translatable("scientia.msg.cooling_heater_on").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        List<DataSeries.Point> pts = series.points();
        if (pts.size() < 10 || pts.get(pts.size() - 1).x() - pts.get(0).x() < 60) {
            player.displayClientMessage(Component.translatable("scientia.msg.cooling_more_data").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        LinearFit f = series.fit();
        double meanT = pts.stream().mapToDouble(DataSeries.Point::y).average().orElse(0);
        double excess = meanT - heater.ambientK();
        if (f == null || excess < 5 || f.slope() >= 0) {
            player.displayClientMessage(Component.translatable("scientia.msg.cooling_more_data").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        double k = -f.slope() / excess * 60;          // per minute
        double kErr = f.slopeError() / excess * 60;
        String jacket = heater.jacketId();
        boolean insulated = !jacket.equals("none");
        JournalService.record(player, "cooling_rate", jacket, -f.slope() * 60, f.slopeError() * 60, "K/min", "data_recorder");
        JournalService.record(player, "cooling_constant", jacket, k, kErr, "1/min", "data_recorder");
        player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                "Cooling: %.2f K/min at %.1f K above the room → k = %.4f /min (jacket: %s)", -f.slope() * 60, excess, k, jacket))
                .withStyle(ChatFormatting.AQUA), false);
        JournalService.grant(player, insulated ? "measure:cooling_rate_insulated" : "measure:cooling_rate_bare");
        JournalData j = JournalService.journal(player);
        Set<String> jackets = new HashSet<>();
        for (JournalData.MeasurementRecord m : j.measurements()) if (m.quantity().equals("cooling_constant")) jackets.add(m.subject());
        if (jackets.size() >= 2 && insulated) JournalService.grant(player, "analyze:heat_transfer_compare");
        JournalData.MeasurementRecord bare = j.latest("cooling_constant", "none");
        if (insulated && bare != null) {
            String observed = HypothesisEvaluator.classifyChange(k - bare.value(), Math.hypot(kErr, bare.uncertainty()));
            JournalService.resolveHypothesis(player, "thermal_insulation", observed, String.format(Locale.ROOT,
                    "Cooling constant without jacket %.4f /min, with %s %.4f /min.", bare.value(), jacket, k));
        }
    }

    @Override
    protected void saveState(CompoundTag tag) {
        tag.putBoolean("Recording", recording);
        tag.putInt("Interval", intervalIndex);
        tag.putInt("QuantityIndex", quantityIndex);
        tag.putLong("Start", startTick);
        tag.putString("Quantity", quantity);
        tag.putString("Unit", unit);
        tag.putString("Source", sourceName);
        List<DataSeries.Point> pts = series.points();
        double[] xs = new double[pts.size()], ys = new double[pts.size()], us = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            xs[i] = pts.get(i).x();
            ys[i] = pts.get(i).y();
            us[i] = pts.get(i).yUncertainty();
        }
        tag.putLongArray("X", toBits(xs));
        tag.putLongArray("Y", toBits(ys));
        tag.putLongArray("U", toBits(us));
    }

    @Override
    protected void loadState(CompoundTag tag) {
        recording = tag.getBoolean("Recording");
        intervalIndex = Math.max(0, Math.min(INTERVALS.length - 1, tag.getInt("Interval")));
        quantityIndex = tag.getInt("QuantityIndex");
        startTick = tag.getLong("Start");
        quantity = tag.getString("Quantity");
        unit = tag.getString("Unit");
        sourceName = tag.getString("Source");
        series = new DataSeries("time", "s", quantity, unit, CAPACITY);
        long[] xs = tag.getLongArray("X"), ys = tag.getLongArray("Y"), us = tag.getLongArray("U");
        for (int i = 0; i < Math.min(xs.length, ys.length); i++) {
            series.add(Double.longBitsToDouble(xs[i]), Double.longBitsToDouble(ys[i]), i < us.length ? Double.longBitsToDouble(us[i]) : 0);
        }
    }

    private static long[] toBits(double[] v) {
        long[] out = new long[v.length];
        for (int i = 0; i < v.length; i++) out[i] = Double.doubleToLongBits(v[i]);
        return out;
    }
}
