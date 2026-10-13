package com.scientia.client;

import com.scientia.core.research.JournalData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The client's read-only copy of its player's journal, replaced whenever the server syncs it. */
@OnlyIn(Dist.CLIENT)
public final class ClientJournal {
    private static JournalData data = new JournalData();
    private static int revision;

    private ClientJournal() {
    }

    public static void update(String json) {
        data = JournalData.fromJson(json);
        revision++;
    }

    public static JournalData get() {
        return data;
    }

    public static int revision() {
        return revision;
    }
}
