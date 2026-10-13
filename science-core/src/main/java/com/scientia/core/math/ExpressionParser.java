package com.scientia.core.math;

import com.scientia.core.units.Dimension;
import com.scientia.core.units.Quantity;
import com.scientia.core.units.Unit;
import com.scientia.core.units.UnitException;
import com.scientia.core.units.Units;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A safe recursive-descent parser for calculator expressions. It never executes code: the input is
 * tokenised and evaluated over a fixed grammar of numbers, units, constants, variables and a whitelist
 * of functions.
 *
 * <pre>
 * input      := additive (('in' | 'to' | '->') unitExpr)?
 * additive   := term (('+' | '-') term)*
 * term       := unary (('*' | '/') unary)*
 * unary      := ('-' | '+') unary | power
 * power      := postfix ('^' unary)?
 * postfix    := primary ('%' | '!')*
 * primary    := NUMBER unitExpr? | '(' additive ')' | IDENT '(' args ')' | IDENT
 * </pre>
 * Numbers may carry units ("12 g / 3 mL", "25 degC in K"); arithmetic is checked dimensionally.
 */
public final class ExpressionParser {

    public enum AngleMode {RADIANS, DEGREES}

    /** The evaluated result plus the unit chosen for display. */
    public record Result(Quantity quantity, String displayUnit, double displayValue, Fraction fraction) {
        public String format(int sigFigs) {
            String v = SigFigs.format(displayValue, sigFigs);
            String s = displayUnit.isEmpty() || displayUnit.equals("1") ? v : v + " " + displayUnit;
            if (fraction != null && fraction.denominator() != 1 && displayUnit.isEmpty()) s += "  (= " + fraction + ")";
            return s;
        }
    }

    private enum Kind {NUMBER, IDENT, OP, END}

    private record Token(Kind kind, String text, double number, int pos) {
    }

    private static final int MAX_LENGTH = 512;
    private static final int MAX_DEPTH = 64;

    private final Map<String, Quantity> variables;
    private final AngleMode angleMode;
    private List<Token> tokens;
    private int index;
    private int depth;

    public ExpressionParser() {
        this(Collections.emptyMap(), AngleMode.RADIANS);
    }

    public ExpressionParser(Map<String, Quantity> variables, AngleMode angleMode) {
        this.variables = variables;
        this.angleMode = angleMode;
    }

    /** Convenience: evaluates a dimensionless expression to a double. */
    public static double evaluateNumber(String input) {
        Result r = new ExpressionParser().evaluate(input);
        if (!r.quantity().dimension().isDimensionless()) {
            throw new MathException("Expected a pure number but the result has dimension " + r.quantity().dimension());
        }
        return r.quantity().si();
    }

    public Result evaluate(String input) {
        if (input == null || input.isBlank()) throw new MathException("Empty expression");
        if (input.length() > MAX_LENGTH) throw new MathException("Expression too long (max " + MAX_LENGTH + " characters)");
        tokens = tokenize(normalise(input));
        index = 0;
        depth = 0;
        Quantity q;
        try {
            q = additive();
            Unit target = null;
            String targetSymbol = null;
            if (peekIdent("in") || peekIdent("to") || peekOp("->")) {
                next();
                int start = peek().pos;
                UnitSpec spec = unitExpr(true);
                if (spec == null) throw new MathException("Expected a unit after conversion keyword", start);
                target = spec.toUnit();
                targetSymbol = spec.symbol;
            }
            if (peek().kind != Kind.END) {
                throw new MathException("Unexpected '" + peek().text + "'", peek().pos);
            }
            checkFinite(q.si());
            if (target != null) {
                if (!target.dimension().equals(q.dimension())) {
                    throw new MathException("Cannot convert " + describe(q.dimension()) + " to " + targetSymbol
                            + " (" + describe(target.dimension()) + ")");
                }
                return new Result(q, targetSymbol, target.fromSI(q.si()), null);
            }
            return present(q);
        } catch (UnitException e) {
            throw new MathException(e.getMessage());
        }
    }

    private static String describe(Dimension d) {
        return d.isDimensionless() ? "a pure number" : d.toString();
    }

    private Result present(Quantity q) {
        if (q.dimension().isDimensionless()) {
            Fraction f = Math.abs(q.si()) < 1e9 ? Fraction.approximate(q.si(), 1000, 1e-12) : null;
            return new Result(q, "", q.si(), f);
        }
        Unit u = Units.preferredFor(q.dimension());
        if (u != null) return new Result(q, u.symbol(), q.si(), null);
        return new Result(q, q.dimension().toString(), q.si(), null);
    }

    // ------------------------------------------------------------------ grammar

    private Quantity additive() {
        Quantity left = term();
        while (peekOp("+") || peekOp("-")) {
            Token op = next();
            Quantity right = term();
            try {
                left = op.text.equals("+") ? left.add(right) : left.subtract(right);
            } catch (UnitException e) {
                throw new MathException(e.getMessage(), op.pos);
            }
        }
        return left;
    }

    private Quantity term() {
        Quantity left = unary();
        while (peekOp("*") || peekOp("/")) {
            Token op = next();
            Quantity right = unary();
            if (op.text.equals("*")) {
                left = left.multiply(right);
            } else {
                if (right.si() == 0) throw new MathException("Division by zero", op.pos);
                left = left.divide(right);
            }
            checkFinite(left.si());
        }
        return left;
    }

    private Quantity unary() {
        if (peekOp("-")) {
            next();
            return unary().negate();
        }
        if (peekOp("+")) {
            next();
            return unary();
        }
        return power();
    }

    private Quantity power() {
        Quantity base = postfix();
        if (peekOp("^")) {
            Token op = next();
            Quantity exp = unary();
            if (!exp.dimension().isDimensionless()) throw new MathException("An exponent must be a pure number", op.pos);
            double e = exp.si();
            if (base.si() == 0 && e < 0) throw new MathException("Division by zero (0 raised to a negative power)", op.pos);
            if (base.si() < 0 && e != Math.rint(e)) throw new MathException("Negative base with a fractional exponent is not a real number", op.pos);
            try {
                base = base.pow(e);
            } catch (UnitException ex) {
                throw new MathException(ex.getMessage(), op.pos);
            }
            checkFinite(base.si());
        }
        return base;
    }

    private Quantity postfix() {
        Quantity q = primary();
        while (peekOp("%") || peekOp("!")) {
            Token op = next();
            if (op.text.equals("%")) {
                if (!q.dimension().isDimensionless()) throw new MathException("Percent applies to pure numbers", op.pos);
                q = Quantity.dimensionless(q.si() / 100.0);
            } else {
                q = Quantity.dimensionless(factorial(q, op.pos));
            }
        }
        return q;
    }

    private Quantity primary() {
        Token t = peek();
        if (t.kind == Kind.NUMBER) {
            next();
            UnitSpec spec = unitExpr(false);
            if (spec == null) return Quantity.dimensionless(t.number);
            return new Quantity(spec.toUnit().toSI(t.number), spec.dimension);
        }
        if (peekOp("(")) {
            next();
            if (++depth > MAX_DEPTH) throw new MathException("Expression nested too deeply");
            Quantity q = additive();
            depth--;
            expectOp(")");
            return q;
        }
        if (t.kind == Kind.IDENT) {
            next();
            String name = t.text;
            if (peekOp("(")) {
                next();
                List<Quantity> args = new ArrayList<>();
                if (!peekOp(")")) {
                    if (++depth > MAX_DEPTH) throw new MathException("Expression nested too deeply");
                    args.add(additive());
                    while (peekOp(",")) {
                        next();
                        args.add(additive());
                    }
                    depth--;
                }
                expectOp(")");
                return function(name, args, t.pos);
            }
            Quantity v = variables.get(name);
            if (v != null) return v;
            Constants.Constant c = Constants.get(name);
            if (c != null) return c.value();
            Unit u = unitAtom(name);
            if (u != null) {
                if (u.isAffine()) throw new MathException("A temperature scale such as " + name + " needs a number in front", t.pos);
                return new Quantity(u.factor(), u.dimension());
            }
            throw new MathException("Unknown name '" + name + "'", t.pos);
        }
        if (t.kind == Kind.END) throw new MathException("Unexpected end of expression", t.pos);
        throw new MathException("Unexpected '" + t.text + "'", t.pos);
    }

    // ------------------------------------------------------------------ units

    private static final class UnitSpec {
        String symbol = "";
        double factor = 1;
        double offset = 0;
        Dimension dimension = Dimension.NONE;
        int atoms = 0;

        Unit toUnit() {
            if (offset != 0 && atoms > 1) throw new MathException("Temperature scales with an offset (°C, °F) cannot be combined with other units");
            return new Unit(symbol, symbol, dimension, factor, offset);
        }
    }

    /**
     * Parses a unit expression such as {@code g/cm3}, {@code m/s^2}, {@code kg*m/s}. When not mandatory,
     * it only consumes identifiers that are known units and are not variables, constants or function calls.
     */
    private UnitSpec unitExpr(boolean mandatory) {
        UnitSpec spec = null;
        boolean first = true;
        while (true) {
            int save = index;
            String op = "*";
            if (!first) {
                if (peekOp("*") || peekOp("/")) {
                    op = next().text;
                } else {
                    break;
                }
            }
            Token t = peek();
            if (t.kind != Kind.IDENT || isFunctionCall() || (!mandatory && (variables.containsKey(t.text)))) {
                index = save;
                break;
            }
            Unit u = unitAtom(t.text);
            if (u == null) {
                index = save;
                break;
            }
            next();
            int exponent = 1;
            if (peekOp("^") && tokens.get(index + 1).kind == Kind.NUMBER) {
                double e = tokens.get(index + 1).number;
                if (e == Math.rint(e) && Math.abs(e) <= 9) {
                    next();
                    next();
                    exponent = (int) e;
                }
            } else if (peekOp("^") && tokens.get(index + 1).text.equals("-") && tokens.get(index + 2).kind == Kind.NUMBER) {
                double e = tokens.get(index + 2).number;
                if (e == Math.rint(e) && Math.abs(e) <= 9) {
                    next();
                    next();
                    next();
                    exponent = -(int) e;
                }
            }
            if (spec == null) spec = new UnitSpec();
            if (op.equals("/")) exponent = -exponent;
            if (u.isAffine() && exponent != 1) throw new MathException("Cannot raise " + u.symbol() + " to a power", t.pos);
            spec.factor *= Math.pow(u.factor(), exponent);
            spec.dimension = spec.dimension.multiply(u.dimension().pow(exponent));
            if (u.isAffine()) spec.offset = u.offset();
            spec.symbol += (spec.atoms == 0 ? "" : op) + u.symbol() + (Math.abs(exponent) != 1 ? "^" + Math.abs(exponent) : "");
            spec.atoms++;
            first = false;
        }
        if (spec == null && mandatory) return null;
        return spec;
    }

    /** Resolves a unit identifier, accepting a trailing single-digit power such as "s2" or "cm3". */
    private static Unit unitAtom(String name) {
        Unit u = Units.get(name);
        if (u != null && !u.symbol().contains("/") && !u.symbol().contains("*")) return u;
        if (name.length() >= 2 && Character.isDigit(name.charAt(name.length() - 1))) {
            Unit base = Units.get(name.substring(0, name.length() - 1));
            int p = name.charAt(name.length() - 1) - '0';
            if (base != null && !base.isAffine() && p >= 2) {
                return new Unit(name, name, base.dimension().pow(p), Math.pow(base.factor(), p));
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ functions

    private Quantity function(String name, List<Quantity> args, int pos) {
        String n = name.toLowerCase(Locale.ROOT);
        switch (n) {
            case "min", "max" -> {
                if (args.isEmpty()) throw new MathException(n + "() needs at least one argument", pos);
                Quantity best = args.get(0);
                for (Quantity a : args) {
                    if (!a.dimension().equals(best.dimension())) throw new MathException(n + "() arguments must share a unit", pos);
                    if (n.equals("min") ? a.si() < best.si() : a.si() > best.si()) best = a;
                }
                return best;
            }
            default -> {
            }
        }
        if (args.size() != 1) throw new MathException(name + "() takes exactly one argument", pos);
        Quantity a = args.get(0);
        switch (n) {
            case "sqrt" -> {
                if (a.si() < 0) throw new MathException("Square root of a negative number is not real", pos);
                try {
                    return a.pow(0.5);
                } catch (UnitException e) {
                    throw new MathException(e.getMessage(), pos);
                }
            }
            case "cbrt" -> {
                Dimension r = a.dimension().root(3);
                if (r == null) throw new MathException("Cube root of " + a.dimension() + " has no meaningful unit", pos);
                return new Quantity(Math.cbrt(a.si()), r);
            }
            case "abs" -> {
                return new Quantity(Math.abs(a.si()), a.dimension());
            }
            default -> {
            }
        }
        if (!a.dimension().isDimensionless()) {
            throw new MathException(name + "() needs a pure number, but got " + a.dimension(), pos);
        }
        double x = a.si();
        double angle = angleMode == AngleMode.DEGREES ? Math.toRadians(x) : x;
        double r = switch (n) {
            case "sin" -> Math.sin(angle);
            case "cos" -> Math.cos(angle);
            case "tan" -> {
                if (Math.abs(Math.cos(angle)) < 1e-12) throw new MathException("tan() is undefined at this angle", pos);
                yield Math.tan(angle);
            }
            case "asin", "acos" -> {
                if (x < -1 || x > 1) throw new MathException(n + "() is only defined for values between -1 and 1", pos);
                double v = n.equals("asin") ? Math.asin(x) : Math.acos(x);
                yield angleMode == AngleMode.DEGREES ? Math.toDegrees(v) : v;
            }
            case "atan" -> angleMode == AngleMode.DEGREES ? Math.toDegrees(Math.atan(x)) : Math.atan(x);
            case "ln" -> log(x, Math.E, pos);
            case "log", "log10" -> log(x, 10, pos);
            case "log2" -> log(x, 2, pos);
            case "exp" -> Math.exp(x);
            case "floor" -> Math.floor(x);
            case "ceil" -> Math.ceil(x);
            case "round" -> Math.rint(x);
            default -> throw new MathException("Unknown function '" + name + "'", pos);
        };
        checkFinite(r);
        return Quantity.dimensionless(r);
    }

    private static double log(double x, double base, int pos) {
        if (x <= 0) throw new MathException("Logarithm is only defined for positive numbers", pos);
        return base == Math.E ? Math.log(x) : base == 10 ? Math.log10(x) : Math.log(x) / Math.log(base);
    }

    private static double factorial(Quantity q, int pos) {
        double x = q.si();
        if (!q.dimension().isDimensionless() || x < 0 || x != Math.rint(x)) {
            throw new MathException("Factorial needs a non-negative whole number", pos);
        }
        if (x > 170) throw new MathException("Overflow: factorial too large", pos);
        double r = 1;
        for (int i = 2; i <= (int) x; i++) r *= i;
        return r;
    }

    private static void checkFinite(double v) {
        if (Double.isNaN(v)) throw new MathException("Result is undefined (not a real number)");
        if (Double.isInfinite(v)) throw new MathException("Overflow: result is too large");
    }

    // ------------------------------------------------------------------ tokens

    private static String normalise(String s) {
        return s.replace("°C", "degC").replace("°F", "degF").replace("kΩ", "kohm").replace("Ω", "ohm")
                .replace("µ", "u").replace("μ", "u").replace("²", "^2").replace("³", "^3")
                .replace("×", "*").replace("·", "*").replace("÷", "/").replace("−", "-").replace("π", "pi").replace("√", "sqrt");
    }

    private static List<Token> tokenize(String s) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            int start = i;
            if (Character.isDigit(c) || (c == '.' && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1)))) {
                while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
                if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                    int j = i + 1;
                    if (j < s.length() && (s.charAt(j) == '+' || s.charAt(j) == '-')) j++;
                    if (j < s.length() && Character.isDigit(s.charAt(j))) {
                        i = j;
                        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
                    }
                }
                String text = s.substring(start, i);
                double v;
                try {
                    v = Double.parseDouble(text);
                } catch (NumberFormatException e) {
                    throw new MathException("Malformed number '" + text + "'", start);
                }
                if (Double.isInfinite(v)) throw new MathException("Overflow: number too large", start);
                out.add(new Token(Kind.NUMBER, text, v, start));
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
                out.add(new Token(Kind.IDENT, s.substring(start, i), 0, start));
                continue;
            }
            if (c == '-' && i + 1 < s.length() && s.charAt(i + 1) == '>') {
                out.add(new Token(Kind.OP, "->", 0, start));
                i += 2;
                continue;
            }
            if ("+-*/^(),%!".indexOf(c) >= 0) {
                out.add(new Token(Kind.OP, String.valueOf(c), 0, start));
                i++;
                continue;
            }
            throw new MathException("Unexpected character '" + c + "'", start);
        }
        out.add(new Token(Kind.END, "", 0, s.length()));
        out.add(new Token(Kind.END, "", 0, s.length()));
        out.add(new Token(Kind.END, "", 0, s.length()));
        return out;
    }

    private Token peek() {
        return tokens.get(index);
    }

    private Token next() {
        Token t = tokens.get(index);
        if (t.kind != Kind.END) index++;
        return t;
    }

    private boolean peekOp(String op) {
        Token t = peek();
        return t.kind == Kind.OP && t.text.equals(op);
    }

    private boolean peekIdent(String name) {
        Token t = peek();
        return t.kind == Kind.IDENT && t.text.equals(name);
    }

    private boolean isFunctionCall() {
        Token n = tokens.get(index + 1);
        return n.kind == Kind.OP && n.text.equals("(");
    }

    private void expectOp(String op) {
        if (!peekOp(op)) throw new MathException("Expected '" + op + "'", peek().pos);
        next();
    }
}
