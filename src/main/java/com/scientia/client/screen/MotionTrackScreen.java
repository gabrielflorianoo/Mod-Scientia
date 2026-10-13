package com.scientia.client.screen;

import com.scientia.core.physics.Mechanics;
import com.scientia.network.ObservePacket;
import com.scientia.network.ScientiaNetwork;
import com.scientia.util.Sci;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Motion Track: a cart on an adjustable ramp with light gates. Release mode measures acceleration,
 * speed and energy; push mode measures the efficiency of the ramp as a simple machine. The animation
 * replays the computed motion a = g(sin θ − μ cos θ) in real time.
 */
@OnlyIn(Dist.CLIENT)
public class MotionTrackScreen extends Screen {
    private final BlockPos pos;
    private int x0, y0, w, h;
    private double mass = 1.0, angle = 20, mu = 0.10, length = 2.0;
    private Mechanics.InclineRun run;
    private long startMs;
    private boolean push;

    public MotionTrackScreen(BlockPos pos) {
        super(Component.translatable("block.scientia.motion_track"));
        this.pos = pos;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        w = Math.min(width - 20, 380);
        h = Math.min(height - 20, 240);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        int by = y0 + h - 20;
        addRenderableWidget(new SciButton(x0 + 6, by, 44, 14, () -> "θ − 5°", () -> angle = Math.max(5, angle - 5)));
        addRenderableWidget(new SciButton(x0 + 52, by, 44, 14, () -> "θ + 5°", () -> angle = Math.min(60, angle + 5)));
        addRenderableWidget(new SciButton(x0 + 100, by, 46, 14, () -> "μ " + Sci.fixed(mu, 2), () -> mu = mu >= 0.5 ? 0 : Math.round((mu + 0.05) * 100) / 100.0));
        addRenderableWidget(new SciButton(x0 + 150, by, 46, 14, () -> "m " + Sci.fixed(mass, 1) + " kg", () -> mass = mass >= 5 ? 0.5 : mass + 0.5));
        addRenderableWidget(new SciButton(x0 + 200, by, 56, 14, () -> push ? "Push mode" : "Release", () -> push = !push));
        addRenderableWidget(new SciButton(x0 + w - 110, by, 104, 14, () -> push ? "Push up & record" : "Release & record", this::go).accent(UiKit.GREEN));
    }

    private void go() {
        run = Mechanics.incline(mass, angle, mu, length, 0.02);
        startMs = System.currentTimeMillis();
        ScientiaNetwork.CHANNEL.sendToServer(new ObservePacket(pos, push ? ObservePacket.Kind.INCLINE_PUSH : ObservePacket.Kind.INCLINE_RUN, mass, angle, mu, length));
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        UiKit.frame(g, x0, y0, w, h);
        UiKit.header(g, font, title.getString(), x0 + 6, y0 + 4, UiKit.CYAN);
        // ramp drawing
        int rx = x0 + 10, ry = y0 + 110, rw = 150;
        double a = Math.toRadians(angle);
        int topX = rx, topY = ry - (int) (Math.sin(a) * rw), botX = rx + (int) (Math.cos(a) * rw);
        GraphRenderer.line(g, topX, topY, botX, ry, 0xFFB8C2CC);
        g.fill(rx, ry, botX + 4, ry + 1, 0xFF6A7480);
        double frac = 0;
        if (run != null && !push && run.moves()) {
            double t = (System.currentTimeMillis() - startMs) / 1000.0;
            double d = Math.min(length, 0.5 * run.acceleration() * t * t);
            frac = d / length;
        }
        int cxp = (int) (topX + (botX - topX) * frac), cyp = (int) (topY + (ry - topY) * frac);
        g.fill(cxp - 4, cyp - 7, cxp + 4, cyp - 1, UiKit.AMBER);
        g.drawString(font, "θ = " + (int) angle + "°, μ = " + Sci.fixed(mu, 2) + ", m = " + Sci.fixed(mass, 1) + " kg, L = " + length + " m", x0 + 10, y0 + 120, UiKit.muted(), false);
        double g0 = 9.80665;
        double predicted = g0 * (Math.sin(a) - mu * Math.cos(a));
        int ty = y0 + 134;
        UiKit.kv(g, font, x0 + 10, ty, 160, "Predicted a", predicted > 0 && Math.tan(a) > mu ? Sci.fixed(predicted, 3) + " m/s²" : "cart stays still", UiKit.text());
        if (push) {
            double force = mass * g0 * (Math.sin(a) + mu * Math.cos(a));
            double work = force * length, pe = mass * g0 * length * Math.sin(a);
            UiKit.kv(g, font, x0 + 10, ty + 10, 160, "Push force", Sci.fixed(force, 2) + " N", UiKit.text());
            UiKit.kv(g, font, x0 + 10, ty + 20, 160, "Work in (F·L)", Sci.fixed(work, 2) + " J", UiKit.text());
            UiKit.kv(g, font, x0 + 10, ty + 30, 160, "Eₚ gained (mgh)", Sci.fixed(pe, 2) + " J", UiKit.text());
            UiKit.kv(g, font, x0 + 10, ty + 40, 160, "Efficiency", Sci.fixed(pe / work * 100, 1) + " %", UiKit.GREEN);
        } else if (run != null && run.moves()) {
            UiKit.kv(g, font, x0 + 10, ty + 10, 160, "Time to bottom", Sci.fixed(run.timeToEnd(), 3) + " s", UiKit.text());
            UiKit.kv(g, font, x0 + 10, ty + 20, 160, "Final speed", Sci.fixed(run.finalSpeed(), 3) + " m/s", UiKit.text());
            UiKit.kv(g, font, x0 + 10, ty + 30, 160, "Eₚ lost / Eₖ gained", Sci.fixed(run.potentialEnergyLost(), 2) + " / " + Sci.fixed(run.kineticEnergyGained(), 2) + " J", UiKit.text());
            UiKit.kv(g, font, x0 + 10, ty + 40, 160, "Friction → heat", Sci.fixed(run.frictionWork(), 2) + " J", UiKit.AMBER);
            UiKit.kv(g, font, x0 + 10, ty + 50, 160, "Efficiency (Eₖ/Eₚ)", Sci.fixed(run.efficiency() * 100, 1) + " %", UiKit.GREEN);
        }
        if (run != null && run.moves() && !push) {
            int gx = x0 + 180, gw = w - 186;
            GraphRenderer.draw(g, font, gx, y0 + 20, gw, (h - 50) / 2, run.distanceVsTime(), false, UiKit.CYAN);
            GraphRenderer.draw(g, font, gx, y0 + 22 + (h - 50) / 2, gw, (h - 50) / 2, List.of(run.speedVsTime()), true, new int[]{UiKit.GREEN});
        } else {
            UiKit.wrap(g, font, "Set the ramp angle, friction coefficient and mass, then release the cart. The light gates record distance and speed against time; the speed–time graph's slope is the acceleration.",
                    x0 + 180, y0 + 24, w - 190, UiKit.muted());
        }
        super.render(g, mx, my, partial);
    }
}
