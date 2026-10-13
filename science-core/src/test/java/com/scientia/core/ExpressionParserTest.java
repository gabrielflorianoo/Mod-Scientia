package com.scientia.core;

import com.scientia.core.math.ExpressionParser;
import com.scientia.core.math.MathException;
import com.scientia.core.units.Quantity;
import com.scientia.core.units.Units;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionParserTest {
    private static double eval(String s) {
        return ExpressionParser.evaluateNumber(s);
    }

    @Test
    void arithmeticAndPrecedence() {
        assertEquals(14, eval("2 + 3 * 4"), 1e-12);
        assertEquals(20, eval("(2 + 3) * 4"), 1e-12);
        assertEquals(512, eval("2 ^ 3 ^ 2"), 1e-9);  // right associative
        assertEquals(-9, eval("-3^2"), 1e-12);         // unary minus binds looser than ^
        assertEquals(0.5, eval("1/2"), 1e-12);
        assertEquals(1.5e-3, eval("1.5e-3"), 1e-18);
        assertEquals(3000, eval("3E3"), 1e-9);
        assertEquals(0.25, eval("25%"), 1e-12);
        assertEquals(120, eval("5!"), 1e-12);
    }

    @Test
    void functionsAndConstants() {
        assertEquals(3, eval("sqrt(9)"), 1e-12);
        assertEquals(-2, eval("cbrt(-8)"), 1e-12);
        assertEquals(2, eval("log10(100)"), 1e-12);
        assertEquals(1, eval("ln(e)"), 1e-12);
        assertEquals(0, eval("sin(pi)"), 1e-12);
        assertEquals(5, eval("max(2, 5, 3)"), 1e-12);
        ExpressionParser deg = new ExpressionParser(Map.of(), ExpressionParser.AngleMode.DEGREES);
        assertEquals(0.5, deg.evaluate("sin(30)").quantity().si(), 1e-12);
        assertEquals(45, deg.evaluate("atan(1)").quantity().si(), 1e-12);
    }

    @Test
    void fractionsAreShown() {
        ExpressionParser.Result r = new ExpressionParser().evaluate("3/4");
        assertEquals("3/4", r.fraction().toString());
        assertTrue(r.format(6).contains("3/4"));
    }

    @Test
    void unitsAndConversions() {
        ExpressionParser p = new ExpressionParser();
        assertEquals(4.0, p.evaluate("12 g / 3 mL in g/mL").displayValue(), 1e-12);
        assertEquals(298.15, p.evaluate("25 degC in K").displayValue(), 1e-9);
        assertEquals(27.77777, p.evaluate("100 km/h -> m/s").displayValue(), 1e-4);
        assertEquals(5.2, p.evaluate("5 m + 20 cm").displayValue(), 1e-12);
        assertEquals("m", p.evaluate("5 m + 20 cm").displayUnit());
        assertEquals(58.86, p.evaluate("6 kg * 9.81 m/s2 in N").displayValue(), 1e-9);
        assertEquals(6, p.evaluate("12 V * 0.5 A in W").displayValue(), 1e-12);
        assertEquals(Units.ENERGY, p.evaluate("2 kW * 3 h").quantity().dimension());
        assertEquals(21.6, p.evaluate("2 kW * 3 h in MJ").displayValue(), 1e-9);
        assertEquals(10, p.evaluate("sqrt(100 m^2) in m").displayValue(), 1e-12);
    }

    @Test
    void variables() {
        ExpressionParser p = new ExpressionParser(Map.of("m", Quantity.of(100, "g"), "V", Quantity.of(12.7, "cm3")),
                ExpressionParser.AngleMode.RADIANS);
        assertEquals(7.874, p.evaluate("m / V in g/cm3").displayValue(), 1e-3);
    }

    @Test
    void errorsAreReportedGracefully() {
        assertThrows(MathException.class, () -> eval("1/0"));
        assertThrows(MathException.class, () -> eval("sqrt(-1)"));
        assertThrows(MathException.class, () -> eval("ln(0)"));
        assertThrows(MathException.class, () -> eval("asin(2)"));
        assertThrows(MathException.class, () -> eval("10^400"));
        assertThrows(MathException.class, () -> eval("2 +"));
        assertThrows(MathException.class, () -> eval("(2 + 3"));
        assertThrows(MathException.class, () -> eval("foo(3)"));
        assertThrows(MathException.class, () -> eval("2 $ 3"));
        assertThrows(MathException.class, () -> eval("171!"));
        assertThrows(MathException.class, () -> eval(""));
        assertThrows(MathException.class, () -> new ExpressionParser().evaluate("5 m + 2 kg"));
        assertThrows(MathException.class, () -> new ExpressionParser().evaluate("5 m in kg"));
        assertThrows(MathException.class, () -> new ExpressionParser().evaluate("sin(3 m)"));
        assertThrows(MathException.class, () -> eval("(".repeat(100) + "1" + ")".repeat(100)));
        assertThrows(MathException.class, () -> eval("1".repeat(600)));
        MathException e = assertThrows(MathException.class, () -> eval("2 * * 3"));
        assertTrue(e.position() >= 0);
    }
}
