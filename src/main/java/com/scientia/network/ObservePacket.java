package com.scientia.network;

import com.scientia.core.physics.Mechanics;
import com.scientia.core.physics.Optics;
import com.scientia.journal.JournalService;
import com.scientia.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Client → server: the player ran a virtual apparatus (Optical Bench, Motion Track) with given settings.
 * The server re-runs the calculation itself from the validated parameters; client-side results are never trusted.
 */
public final class ObservePacket {
    public enum Kind {REFLECTION, REFRACTION, LENS, TOTAL_INTERNAL_REFLECTION, DISPERSION, INCLINE_RUN, INCLINE_PUSH}

    private final BlockPos pos;
    private final Kind kind;
    private final double a, b, c, d;

    public ObservePacket(BlockPos pos, Kind kind, double a, double b, double c, double d) {
        this.pos = pos;
        this.kind = kind;
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
    }

    public ObservePacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readEnum(Kind.class), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(kind);
        buf.writeDouble(a);
        buf.writeDouble(b);
        buf.writeDouble(c);
        buf.writeDouble(d);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (p == null || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 100) return;
        if (!Double.isFinite(a) || !Double.isFinite(b) || !Double.isFinite(c) || !Double.isFinite(d)) return;
        var state = p.level().getBlockState(pos);
        switch (kind) {
            case REFLECTION, REFRACTION, LENS, TOTAL_INTERNAL_REFLECTION, DISPERSION -> {
                if (!state.is(ModBlocks.OPTICAL_BENCH.get())) return;
                handleOptics(p);
            }
            case INCLINE_RUN, INCLINE_PUSH -> {
                if (!state.is(ModBlocks.MOTION_TRACK.get())) return;
                handleIncline(p);
            }
        }
    }

    private void handleOptics(ServerPlayer p) {
        switch (kind) {
            case REFLECTION -> {
                if (a < 1 || a > 89) return;
                JournalService.grant(p, "observe:reflection");
            }
            case REFRACTION -> {
                // a = incidence angle, b = n1, c = n2
                if (a < 1 || a > 89 || b < 1 || b > 3 || c < 1 || c > 3) return;
                double t2 = Optics.refractionAngle(b, c, a);
                if (Double.isNaN(t2)) return;
                JournalService.record(p, "refraction_angle", String.format(Locale.ROOT, "n%.3f->n%.3f@%.0f", b, c, a), t2, 0.5, "°", "optical_bench");
                JournalService.grant(p, "observe:refraction");
            }
            case TOTAL_INTERNAL_REFLECTION -> {
                if (b <= c || Double.isNaN(Optics.refractionAngle(b, c, a)) == false) return;
                JournalService.grant(p, "observe:total_internal_reflection");
            }
            case LENS -> {
                // a = focal length (m), b = object distance (m)
                if (a <= 0.01 || a > 2 || b <= a || b > 5) return;
                double di = Optics.imageDistance(a, b);
                JournalService.record(p, "image_distance", String.format(Locale.ROOT, "f%.2f_do%.2f", a, b), di, 0.005, "m", "optical_bench");
                JournalService.grant(p, "observe:lens_image");
            }
            case DISPERSION -> JournalService.grant(p, "observe:dispersion");
            default -> {
            }
        }
    }

    private void handleIncline(ServerPlayer p) {
        // a = mass kg, b = angle deg, c = friction coefficient, d = track length m
        if (a < 0.1 || a > 10 || b < 1 || b > 60 || c < 0 || c > 1 || d < 0.5 || d > 5) return;
        if (kind == Kind.INCLINE_RUN) {
            Mechanics.InclineRun run = Mechanics.incline(a, b, c, d, 0.02);
            if (!run.moves()) {
                p.displayClientMessage(Component.translatable("scientia.msg.cart_static").withStyle(ChatFormatting.GRAY), false);
                return;
            }
            String subject = String.format(Locale.ROOT, "m%.1f_θ%.0f_μ%.2f", a, b, c);
            JournalService.record(p, "acceleration", subject, run.acceleration(), 0.01, "m/s²", "motion_track");
            JournalService.record(p, "kinetic_energy", subject, run.kineticEnergyGained(), 0.01, "J", "motion_track");
            JournalService.grant(p, "derived:kinetic_energy");
            if (c > 0) {
                JournalService.record(p, "efficiency", subject, run.efficiency() * 100, 0.5, "%", "motion_track");
                JournalService.grant(p, "analyze:efficiency");
            }
        } else {
            double theta = Math.toRadians(b);
            double g = 9.80665;
            double force = a * g * (Math.sin(theta) + c * Math.cos(theta));
            double work = force * d;
            double pe = a * g * d * Math.sin(theta);
            JournalService.record(p, "incline_efficiency", String.format(Locale.ROOT, "θ%.0f_μ%.2f", b, c), pe / work * 100, 0.5, "%", "motion_track");
            JournalService.grant(p, "analyze:incline_efficiency");
        }
    }
}
