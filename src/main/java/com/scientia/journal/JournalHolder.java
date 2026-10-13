package com.scientia.journal;

import com.scientia.core.research.JournalData;
import net.minecraft.nbt.CompoundTag;

/** Capability payload attached to every player: their scientific journal. */
public final class JournalHolder {
    private JournalData data = new JournalData();

    public JournalData data() {
        return data;
    }

    public void set(JournalData d) {
        data = d;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("journal", data.toJson());
        return t;
    }

    public void load(CompoundTag t) {
        data = JournalData.fromJson(t.getString("journal"));
    }
}
