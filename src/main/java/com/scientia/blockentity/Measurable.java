package com.scientia.blockentity;

import java.util.List;

/** A block entity that exposes live measurements to instruments and the Data Recorder. */
public interface Measurable {
    record Reading(String quantity, double value, double uncertainty, String unit) {
    }

    /** Current readings; the first is the primary one recorded by a Data Recorder. */
    List<Reading> readings();
}
