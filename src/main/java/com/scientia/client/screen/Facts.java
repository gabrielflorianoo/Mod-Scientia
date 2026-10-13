package com.scientia.client.screen;

import com.scientia.core.data.ExperimentDefinition;
import com.scientia.core.data.MaterialEntry;
import com.scientia.core.research.ResearchNode;
import com.scientia.util.Sci;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Human-readable descriptions of accomplishment facts ("measure:mass" → "Record a mass"). */
@OnlyIn(Dist.CLIENT)
public final class Facts {
    private Facts() {
    }

    public static String describe(String fact) {
        int i = fact.indexOf(':');
        if (i < 0) return fact;
        String kind = fact.substring(0, i), arg = fact.substring(i + 1);
        return switch (kind) {
            case "measure" -> switch (arg) {
                case "cooling_rate_bare" -> "Measure a cooling rate without insulation (Data Recorder)";
                case "cooling_rate_insulated" -> "Measure a cooling rate with an insulating jacket";
                default -> "Record a " + arg.replace('_', ' ') + " measurement";
            };
            case "derived" -> "Calculate " + arg.replace('_', ' ') + " from your own measurements";
            case "identify" -> arg.equals("any") ? "Identify any material from its measured density" : "Identify " + materialName(arg);
            case "element" -> "Discover the element " + arg;
            case "reaction" -> switch (arg) {
                case "any" -> "Observe any chemical reaction";
                case "closed_mass_conserved" -> "Show mass is conserved in a sealed vessel";
                case "open_mass_loss" -> "Observe mass loss from an open vessel";
                default -> "Observe: " + (Sci.db().reactions().containsKey(arg) ? Sci.db().reactions().get(arg).name() : arg);
            };
            case "experiment" -> {
                ExperimentDefinition e = Sci.db().experiments().get(arg);
                yield "Complete the experiment \"" + (e == null ? arg : e.title()) + "\"";
            }
            case "research" -> {
                ResearchNode n = Sci.db().research().get(arg);
                yield "Research " + (n == null ? arg : n.title());
            }
            case "circuit" -> switch (arg) {
                case "closed" -> "Light a lamp with a complete circuit";
                case "series" -> "Run two lamps in series";
                case "parallel" -> "Run two lamps in parallel";
                default -> "Circuit: " + arg;
            };
            case "observe" -> "Observe " + arg.replace('_', ' ');
            case "analyze" -> switch (arg) {
                case "acid" -> "Measure an acidic solution (pH < 6.5)";
                case "base" -> "Measure a basic solution (pH > 7.5)";
                case "conductor" -> "Find a conductor with the conductivity probe";
                case "insulator" -> "Find an insulator with the conductivity probe";
                case "spectrum" -> "Record an emission spectrum";
                case "graph_fit" -> "Fit a straight line to recorded data";
                case "heat_transfer_compare" -> "Compare cooling with two different jacket materials";
                case "incline_efficiency" -> "Measure the efficiency of a ramp (push mode)";
                case "efficiency" -> "Measure energy efficiency of a rolling cart with friction";
                default -> "Analyse " + arg.replace('_', ' ');
            };
            case "phase" -> "Observe " + arg + " (phase change)";
            case "separate" -> "Separate a mixture by " + arg;
            case "hypothesis" -> "Test a hypothesis in " + arg.replace('_', ' ');
            default -> fact;
        };
    }

    private static String materialName(String id) {
        MaterialEntry m = Sci.db().materials().get(id);
        return m == null ? id : m.name();
    }
}
