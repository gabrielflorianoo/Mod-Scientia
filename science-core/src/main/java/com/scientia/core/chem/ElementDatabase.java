package com.scientia.core.chem;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable lookup of all elements by atomic number and symbol. */
public final class ElementDatabase {
    private final List<Element> elements;
    private final Map<String, Element> bySymbol = new HashMap<>();
    private final Map<Integer, Element> byZ = new HashMap<>();

    public ElementDatabase(List<Element> elements) {
        this.elements = List.copyOf(elements);
        for (Element e : elements) {
            bySymbol.put(e.symbol(), e);
            byZ.put(e.z(), e);
        }
    }

    public Element bySymbol(String symbol) {
        return bySymbol.get(symbol);
    }

    public Element byNumber(int z) {
        return byZ.get(z);
    }

    public boolean isElement(String symbol) {
        return bySymbol.containsKey(symbol);
    }

    public List<Element> all() {
        return Collections.unmodifiableList(elements);
    }

    public int size() {
        return elements.size();
    }

    /**
     * Position of an element in the standard 18-column periodic table layout: {column 1..18, row 1..9}.
     * Lanthanides (57–71) and actinides (89–103) are placed in separate rows 8 and 9 below the main table.
     */
    public static int[] layoutPosition(Element e) {
        int z = e.z();
        if (z >= 57 && z <= 71) return new int[]{3 + (z - 57), 8};
        if (z >= 89 && z <= 103) return new int[]{3 + (z - 89), 9};
        int g = e.group() == null ? 3 : e.group();
        return new int[]{g, e.period()};
    }
}
