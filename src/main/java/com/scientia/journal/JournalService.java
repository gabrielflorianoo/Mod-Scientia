package com.scientia.journal;

import com.scientia.core.data.ExperimentDefinition;
import com.scientia.core.data.MaterialAnalysis;
import com.scientia.core.data.MaterialEntry;
import com.scientia.core.data.ScienceDatabase;
import com.scientia.core.experiment.HypothesisEvaluator;
import com.scientia.core.research.JournalData;
import com.scientia.core.research.ResearchNode;
import com.scientia.core.units.Measurement;
import com.scientia.network.ScientiaNetwork;
import com.scientia.network.SyncJournalPacket;
import com.scientia.util.Sci;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Server-side journal operations. All research, measurements and hypothesis results are decided here
 * from server-side state; clients only ever receive a copy.
 */
public final class JournalService {
    private JournalService() {
    }

    public static JournalData journal(ServerPlayer p) {
        return JournalCapability.get(p).data();
    }

    public static void sync(ServerPlayer p) {
        ScientiaNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new SyncJournalPacket(journal(p).toJson()));
    }

    /** Adds a fact for a player and announces any research or experiments it completes. */
    public static void grant(ServerPlayer p, String fact) {
        ScienceDatabase db = Sci.db();
        JournalData.Progress progress = journal(p).addFact(fact, db);
        if (!progress.any()) return;
        if (fact.startsWith("element:")) {
            var el = db.elements().bySymbol(fact.substring(8));
            if (el != null) {
                p.displayClientMessage(Component.translatable("scientia.msg.element", el.name(), el.symbol(), el.z())
                        .withStyle(ChatFormatting.AQUA), false);
            }
        }
        for (String e : progress.newExperiments()) {
            ExperimentDefinition def = db.experiments().get(e);
            p.displayClientMessage(Component.translatable("scientia.msg.experiment", def == null ? e : def.title())
                    .withStyle(ChatFormatting.GREEN), false);
        }
        for (String r : progress.newResearch()) {
            ResearchNode n = db.research().get(r);
            p.displayClientMessage(Component.translatable("scientia.msg.research", n == null ? r : n.title())
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            if (n != null && n.unlocks() != null && !n.unlocks().isEmpty()) {
                p.displayClientMessage(Component.translatable("scientia.msg.unlocks", String.join(", ", n.unlocks()))
                        .withStyle(ChatFormatting.YELLOW), false);
            }
        }
        if (!progress.newResearch().isEmpty()) {
            p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, 1.0f);
        } else if (!progress.newExperiments().isEmpty()) {
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.4f);
        }
        sync(p);
    }

    public static void grantAll(ServerPlayer p, String... facts) {
        for (String f : facts) grant(p, f);
    }

    /** Grants a fact to the player with this UUID if they are online in the level's server. */
    public static void grant(ServerLevel level, UUID player, String fact) {
        if (player == null) return;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(player);
        if (p != null) grant(p, fact);
    }

    public static ServerPlayer online(ServerLevel level, UUID player) {
        return player == null ? null : level.getServer().getPlayerList().getPlayer(player);
    }

    public static void record(ServerPlayer p, String quantity, String subject, double value, double uncertainty, String unit, String instrument) {
        journal(p).addMeasurement(new JournalData.MeasurementRecord(quantity, subject, value, uncertainty, unit,
                p.level().getGameTime(), instrument));
        grant(p, "measure:" + quantity);
        sync(p);
    }

    /**
     * After a mass or volume measurement, derives density when both have been measured for the same
     * specimen (same sample seed and count), then tries to identify the material.
     */
    public static void tryDeriveDensity(ServerPlayer p, MaterialEntry material, String sampleKey) {
        JournalData j = journal(p);
        JournalData.MeasurementRecord m = j.latest("mass", material.id());
        JournalData.MeasurementRecord v = j.latest("volume", material.id());
        if (m == null || v == null || !m.instrument().endsWith(sampleKey) || !v.instrument().endsWith(sampleKey)) return;
        Measurement mass = new Measurement(m.value(), m.uncertainty(), "g");
        Measurement vol = new Measurement(v.value(), v.uncertainty(), "cm3");
        Measurement rho = mass.dividedBy(vol, "g/cm³");
        j.addMeasurement(new JournalData.MeasurementRecord("density", material.id(), rho.value(), rho.uncertainty(), "g/cm³",
                p.level().getGameTime(), "derived|" + sampleKey));
        p.displayClientMessage(Component.translatable("scientia.msg.density", material.name(), rho.format())
                .withStyle(ChatFormatting.AQUA), false);
        grant(p, "derived:density");
        ScienceDatabase db = Sci.db();
        List<MaterialEntry> candidates = MaterialAnalysis.identifyByDensity(rho, db);
        Double ref = MaterialAnalysis.referenceDensity(material, db);
        boolean match = candidates.stream().anyMatch(c -> c.id().equals(material.id()));
        if (match && ref != null) {
            StringBuilder others = new StringBuilder();
            for (MaterialEntry c : candidates) {
                if (c.id().equals(material.id())) continue;
                if (others.length() > 0) others.append(", ");
                others.append(c.name());
            }
            p.displayClientMessage(Component.translatable("scientia.msg.identified", material.name(),
                    String.format(Locale.ROOT, "%.3g", ref)).withStyle(ChatFormatting.GREEN), false);
            if (others.length() > 0) {
                p.displayClientMessage(Component.translatable("scientia.msg.ambiguous", others.toString()).withStyle(ChatFormatting.GRAY), false);
            }
            grant(p, "identify:" + material.id());
            grant(p, "identify:any");
            for (String el : MaterialAnalysis.elementalComposition(material, db).keySet()) grant(p, "element:" + el);
            evaluateDensityOrder(p);
        } else if (ref != null) {
            p.displayClientMessage(Component.translatable("scientia.msg.nomatch", String.format(Locale.ROOT, "%.3g", ref))
                    .withStyle(ChatFormatting.GRAY), false);
        }
        sync(p);
    }

    private static void evaluateDensityOrder(ServerPlayer p) {
        JournalData j = journal(p);
        JournalData.MeasurementRecord ingot = j.latest("density", "iron_ingot");
        JournalData.MeasurementRecord ore = j.latest("density", "raw_iron");
        if (ingot == null || ore == null) return;
        String observed = HypothesisEvaluator.classifyChange(ingot.value() - ore.value(), Math.hypot(ingot.uncertainty(), ore.uncertainty()));
        resolveHypothesis(p, "density", observed, String.format(Locale.ROOT,
                "Iron ingot: %.2f g/cm³, raw iron ore: %.2f g/cm³. Ore contains lighter oxygen and silicate rock.", ingot.value(), ore.value()));
    }

    /** Resolves a pending hypothesis (if the player recorded one) against an observed outcome. */
    public static void resolveHypothesis(ServerPlayer p, String experiment, String observed, String context) {
        JournalData j = journal(p);
        JournalData.HypothesisRecord h = j.hypotheses().get(experiment);
        if (h == null || h.observed() != null) return;
        HypothesisEvaluator.Outcome o = HypothesisEvaluator.evaluate(h.predicted(), observed, context);
        j.hypotheses().put(experiment, new JournalData.HypothesisRecord(experiment, h.predicted(), observed, o.supported(), o.explanation()));
        p.displayClientMessage(Component.translatable(o.supported() ? "scientia.msg.hypothesis_supported" : "scientia.msg.hypothesis_refuted")
                .withStyle(o.supported() ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        p.displayClientMessage(Component.literal(context).withStyle(ChatFormatting.GRAY), false);
        grant(p, "hypothesis:" + experiment);
        sync(p);
    }

    /** Grants a fact to every player within {@code radius} blocks (used for visual observations). */
    public static void grantNearby(ServerLevel level, net.minecraft.core.BlockPos pos, double radius, String fact) {
        for (Player pl : level.players()) {
            if (pl instanceof ServerPlayer sp && pl.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= radius * radius) {
                grant(sp, fact);
            }
        }
    }
}
