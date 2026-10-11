package com.lacuna.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /lacuna} debug command tree (spec section 4), permission level 2. */
public final class LacunaCommands {
    private LacunaCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lacuna")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("selftest").executes(ctx -> SelfTest.run(ctx.getSource())))
                .then(Commands.literal("givekit").executes(LacunaCommands::notYet))
                .then(Commands.literal("banish").executes(LacunaCommands::notYet))
                .then(Commands.literal("return").executes(LacunaCommands::notYet))
                .then(Commands.literal("rep").executes(LacunaCommands::notYet))
                .then(Commands.literal("township").executes(LacunaCommands::notYet))
                .then(Commands.literal("scent").executes(LacunaCommands::notYet)));
    }

    private static int notYet(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendFailure(Component.literal("Not available in this build yet."));
        return 0;
    }
}
