package com.scientia.core.chem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Parses chemical formulas such as {@code H2O}, {@code Ca(OH)2}, {@code Ca(CH3COO)2},
 * {@code Al2Si2O5(OH)4} and hydrates written {@code CuSO4·5H2O} (also {@code .} or {@code *}).
 * Trailing ionic charges ({@code Na+}, {@code SO4^2-}) are ignored for composition purposes.
 */
public final class ChemicalFormula {
    private final String text;
    private final Map<String, Integer> composition;

    private ChemicalFormula(String text, Map<String, Integer> composition) {
        this.text = text;
        this.composition = Collections.unmodifiableMap(composition);
    }

    public static ChemicalFormula parse(String formula) {
        return parse(formula, s -> true);
    }

    /** Parses and verifies every symbol with {@code isElement}; throws IllegalArgumentException on errors. */
    public static ChemicalFormula parse(String formula, Predicate<String> isElement) {
        if (formula == null || formula.isBlank()) throw new IllegalArgumentException("Empty formula");
        String f = stripCharge(formula.trim().replace("₀", "0").replace("₁", "1").replace("₂", "2").replace("₃", "3")
                .replace("₄", "4").replace("₅", "5").replace("₆", "6").replace("₇", "7").replace("₈", "8").replace("₉", "9"));
        Map<String, Integer> total = new LinkedHashMap<>();
        for (String part : f.split("[·.*]")) {
            if (part.isEmpty()) throw new IllegalArgumentException("Malformed hydrate in '" + formula + "'");
            int i = 0, mult = 1;
            while (i < part.length() && Character.isDigit(part.charAt(i))) i++;
            if (i > 0) mult = Integer.parseInt(part.substring(0, i));
            Parser p = new Parser(part.substring(i), formula, isElement);
            Map<String, Integer> m = p.group();
            if (p.pos != p.s.length()) throw new IllegalArgumentException("Unexpected '" + p.s.charAt(p.pos) + "' in '" + formula + "'");
            for (var e : m.entrySet()) total.merge(e.getKey(), e.getValue() * mult, Integer::sum);
        }
        if (total.isEmpty()) throw new IllegalArgumentException("No elements in '" + formula + "'");
        return new ChemicalFormula(formula, total);
    }

    private static String stripCharge(String f) {
        return f.replaceAll("\\^?\\d*[+-]$", "").replaceAll("\\(aq\\)|\\(s\\)|\\(l\\)|\\(g\\)", "");
    }

    private static final class Parser {
        final String s, original;
        final Predicate<String> isElement;
        int pos = 0;

        Parser(String s, String original, Predicate<String> isElement) {
            this.s = s;
            this.original = original;
            this.isElement = isElement;
        }

        Map<String, Integer> group() {
            Map<String, Integer> m = new LinkedHashMap<>();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '(' || c == '[') {
                    char close = c == '(' ? ')' : ']';
                    pos++;
                    Map<String, Integer> inner = group();
                    if (pos >= s.length() || s.charAt(pos) != close) throw new IllegalArgumentException("Unbalanced brackets in '" + original + "'");
                    pos++;
                    int n = number();
                    for (var e : inner.entrySet()) m.merge(e.getKey(), e.getValue() * n, Integer::sum);
                } else if (c == ')' || c == ']') {
                    return m;
                } else if (Character.isUpperCase(c)) {
                    int start = pos++;
                    while (pos < s.length() && Character.isLowerCase(s.charAt(pos))) pos++;
                    String sym = s.substring(start, pos);
                    if (!isElement.test(sym)) throw new IllegalArgumentException("Unknown element '" + sym + "' in '" + original + "'");
                    m.merge(sym, number(), Integer::sum);
                } else {
                    throw new IllegalArgumentException("Unexpected '" + c + "' in '" + original + "'");
                }
            }
            return m;
        }

        int number() {
            int start = pos;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            return start == pos ? 1 : Integer.parseInt(s.substring(start, pos));
        }
    }

    public Map<String, Integer> composition() {
        return composition;
    }

    public String text() {
        return text;
    }

    /** Molar mass in g/mol computed from the element table. */
    public double molarMass(ElementDatabase elements) {
        double m = 0;
        for (var e : composition.entrySet()) {
            Element el = elements.bySymbol(e.getKey());
            if (el == null || el.atomicMass() == null) throw new IllegalArgumentException("No atomic mass for " + e.getKey());
            m += el.atomicMass() * e.getValue();
        }
        return m;
    }

    /** Mass fraction of each element (0..1). */
    public Map<String, Double> massFractions(ElementDatabase elements) {
        double total = molarMass(elements);
        Map<String, Double> out = new LinkedHashMap<>();
        for (var e : composition.entrySet()) {
            out.put(e.getKey(), elements.bySymbol(e.getKey()).atomicMass() * e.getValue() / total);
        }
        return out;
    }

    /** Pretty form with Unicode subscripts, e.g. Ca(OH)₂. */
    public static String pretty(String formula) {
        StringBuilder sb = new StringBuilder();
        boolean afterDot = true;
        for (char c : formula.toCharArray()) {
            if (Character.isDigit(c) && !afterDot) sb.append((char) ('₀' + (c - '0')));
            else sb.append(c);
            if (c == '·' || c == '.' || c == '*') afterDot = true;
            else if (!Character.isDigit(c)) afterDot = false;
        }
        return sb.toString();
    }
}
