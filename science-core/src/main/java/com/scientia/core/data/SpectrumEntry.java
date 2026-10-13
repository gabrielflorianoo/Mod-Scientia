package com.scientia.core.data;

import java.util.List;

/** Characteristic atomic emission lines of an element (air wavelengths, nm). */
public record SpectrumEntry(String symbol, List<Line> lines, String note) {
    public record Line(double nm, double intensity) {
    }
}
