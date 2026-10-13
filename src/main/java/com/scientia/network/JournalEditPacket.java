package com.scientia.network;

import com.scientia.core.data.ExperimentDefinition;
import com.scientia.core.research.JournalData;
import com.scientia.journal.JournalService;
import com.scientia.util.Sci;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: add a personal note, toggle a favourite, or record a hypothesis before an experiment. */
public final class JournalEditPacket {
    public enum Op {ADD_NOTE, TOGGLE_FAVORITE, HYPOTHESIS}

    private final Op op;
    private final String key;
    private final String value;

    public JournalEditPacket(Op op, String key, String value) {
        this.op = op;
        this.key = key;
        this.value = value;
    }

    public JournalEditPacket(FriendlyByteBuf buf) {
        this(buf.readEnum(Op.class), buf.readUtf(128), buf.readUtf(JournalData.MAX_NOTE_LENGTH + 16));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(op);
        buf.writeUtf(key, 128);
        buf.writeUtf(value.length() > JournalData.MAX_NOTE_LENGTH ? value.substring(0, JournalData.MAX_NOTE_LENGTH) : value,
                JournalData.MAX_NOTE_LENGTH + 16);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (p == null) return;
        JournalData j = JournalService.journal(p);
        switch (op) {
            case ADD_NOTE -> j.addNote(value);
            case TOGGLE_FAVORITE -> {
                if (key.length() > 96) return;
                if (!j.favorites().remove(key) && j.favorites().size() < 256) j.favorites().add(key);
            }
            case HYPOTHESIS -> {
                ExperimentDefinition e = Sci.db().experiments().get(key);
                if (e == null || e.hypothesis() == null || j.experiments().contains(key)) return;
                if (e.hypothesis().options().stream().noneMatch(o -> o.id().equals(value))) return;
                JournalData.HypothesisRecord existing = j.hypotheses().get(key);
                if (existing != null && existing.observed() != null) return; // already tested
                j.hypotheses().put(key, new JournalData.HypothesisRecord(key, value, null, null, null));
            }
        }
        JournalService.sync(p);
    }
}
