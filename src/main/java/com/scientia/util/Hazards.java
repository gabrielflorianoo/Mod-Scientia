package com.scientia.util;

import com.scientia.journal.JournalService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.List;

/**
 * Real laboratory hazards with in-game consequences. Each accident is also a lesson: affected players get
 * an explanation, a note in their journal and the "observe:lesson" fact (research "Learning from Accidents").
 * Nothing here is an explosive or weapon mechanic: these are the ordinary failure modes of school-lab work.
 */
public final class Hazards {
    private Hazards() {
    }

    public static List<ServerPlayer> playersNear(ServerLevel level, BlockPos pos, double radius) {
        List<ServerPlayer> out = new ArrayList<>();
        for (Player p : level.players()) {
            if (p instanceof ServerPlayer sp && p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= radius * radius) out.add(sp);
        }
        return out;
    }

    private static final java.util.Map<String, Long> LAST = new java.util.HashMap<>();

    /** True at most once per {@code ticks} for the same key (avoids repeating effects every tick). */
    public static boolean cooldown(String key, long now, long ticks) {
        Long last = LAST.get(key);
        if (last != null && now - last < ticks) return false;
        LAST.put(key, now);
        if (LAST.size() > 4096) LAST.clear();
        return true;
    }

    /** Explains what happened and records it in the journal (once per kind every 30 s per player). */
    public static void lesson(ServerPlayer p, String kind, String what, String why) {
        if (!cooldown(p.getUUID() + "|" + kind, p.level().getGameTime(), 600)) return;
        p.displayClientMessage(Component.literal("⚠ " + what).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
        p.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.GOLD), false);
        JournalService.journal(p).addNote("Lesson (" + kind.replace('_', ' ') + "): " + what + " — " + why);
        JournalService.grant(p, "observe:" + kind);
        JournalService.grant(p, "observe:lesson");
        JournalService.sync(p);
    }

    public static void lessonNearby(ServerLevel level, BlockPos pos, double radius, String kind, String what, String why) {
        for (ServerPlayer p : playersNear(level, pos, radius)) lesson(p, kind, what, why);
    }

    /** Toxic gas release (e.g. chlorine): poisons players nearby for a time that grows with the amount. */
    public static void toxicGas(ServerLevel level, BlockPos pos, String gasName, double moles) {
        if (!cooldown("gas|" + pos.asLong(), level.getGameTime(), 40)) return;
        int seconds = (int) Math.max(4, Math.min(30, moles * 1500));
        level.sendParticles(ParticleTypes.EFFECT, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 30, 0.6, 0.4, 0.6, 0.01);
        for (ServerPlayer p : playersNear(level, pos, 4.5)) {
            p.addEffect(new MobEffectInstance(MobEffects.POISON, seconds * 20, 0));
            p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 8 * 20, 0));
            lesson(p, "toxic_gas", gasName + " escaped into the air",
                    gasName + " is toxic: it attacks the moist lining of the lungs. Real labs handle it only in a fume hood or a sealed apparatus.");
        }
    }

    /** True when there is an ignition source (fire, lava, a burning heater) within two blocks. */
    public static boolean ignitionNear(ServerLevel level, BlockPos pos) {
        for (BlockPos q : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
            BlockState s = level.getBlockState(q);
            FluidState f = level.getFluidState(q);
            if (s.getBlock() instanceof BaseFireBlock || f.is(net.minecraft.tags.FluidTags.LAVA)) return true;
            if (s.hasProperty(com.scientia.block.MachineBlock.ACTIVE) && s.getValue(com.scientia.block.MachineBlock.ACTIVE)
                    && level.getBlockEntity(q) instanceof com.scientia.blockentity.LabHeaterBlockEntity) return true;
        }
        return false;
    }

    /** Hydrogen meeting a flame: a short flash fire (not an explosion) that singes nearby players. */
    public static void hydrogenFlash(ServerLevel level, BlockPos pos, double moles) {
        if (!cooldown("h2|" + pos.asLong(), level.getGameTime(), 40)) return;
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.8f, 1.6f);
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 25, 0.4, 0.4, 0.4, 0.04);
        BlockPos above = pos.above();
        if (level.getBlockState(above).isAir()) level.setBlockAndUpdate(above, BaseFireBlock.getState(level, above));
        for (ServerPlayer p : playersNear(level, pos, 3)) {
            p.hurt(level.damageSources().inFire(), 2f);
            lesson(p, "hydrogen_ignition", "Escaping hydrogen caught fire",
                    "Hydrogen is flammable in air from about 4 % to 75 % by volume and ignites easily. Keep hydrogen away from flames and hot plates.");
        }
    }

    /** Glassware bursting: the block breaks, contents are lost and flying glass hurts nearby players. */
    public static void shatter(ServerLevel level, BlockPos pos, String kind, String what, String why) {
        level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.2f, 0.8f);
        level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.05);
        for (ServerPlayer p : playersNear(level, pos, 3)) {
            p.hurt(level.damageSources().generic(), 4f);
            lesson(p, kind, what, why);
        }
        level.destroyBlock(pos, false);
    }

    /** An overheated conductor: the block is destroyed and may ignite something next to it. */
    public static void burnOut(ServerLevel level, BlockPos pos, boolean ignite) {
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.2f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.2, 0.2, 0.2, 0.02);
        level.destroyBlock(pos, false);
        if (ignite) {
            for (Direction d : Direction.values()) {
                BlockPos q = pos.relative(d);
                if (level.getBlockState(q).isAir() && BaseFireBlock.canBePlacedAt(level, q, d)) {
                    level.setBlockAndUpdate(q, BaseFireBlock.getState(level, q));
                    break;
                }
            }
        }
    }
}
