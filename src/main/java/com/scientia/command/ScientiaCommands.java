package com.scientia.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.scientia.ScientiaMod;
import com.scientia.core.research.JournalData;
import com.scientia.core.validation.ScienceValidator;
import com.scientia.journal.JournalService;
import com.scientia.util.Sci;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** Operator commands for testing and server administration (permission level 2). */
@Mod.EventBusSubscriber(modid = ScientiaMod.MODID)
public final class ScientiaCommands {
    private ScientiaCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("scientia").requires(s -> s.hasPermission(2))
                .then(Commands.literal("validate").executes(ctx -> {
                    List<String> errors = ScienceValidator.validate(Sci.db());
                    ctx.getSource().sendSuccess(() -> Component.literal(errors.isEmpty() ? "Science data valid."
                            : errors.size() + " problem(s): " + String.join("; ", errors.subList(0, Math.min(5, errors.size())))), false);
                    return errors.isEmpty() ? 1 : 0;
                }))
                .then(Commands.literal("reset").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                    JournalService.journal(p).reset();
                    JournalService.sync(p);
                    ctx.getSource().sendSuccess(() -> Component.literal("Journal reset for " + p.getName().getString()), true);
                    return 1;
                })))
                .then(Commands.literal("grant").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("fact", StringArgumentType.greedyString()).executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                            String fact = StringArgumentType.getString(ctx, "fact");
                            JournalService.grant(p, fact);
                            ctx.getSource().sendSuccess(() -> Component.literal("Granted " + fact), true);
                            return 1;
                        }))))
                .then(Commands.literal("facts").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                    JournalData j = JournalService.journal(p);
                    ctx.getSource().sendSuccess(() -> Component.literal(j.facts().size() + " facts, " + j.research().size() + " research, "
                            + j.experiments().size() + " experiments: " + String.join(", ", j.facts())), false);
                    return 1;
                }))));
    }
}
