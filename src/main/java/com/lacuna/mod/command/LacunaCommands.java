package com.lacuna.mod.command;

import com.lacuna.mod.banish.BanishmentManager;
import com.lacuna.mod.registry.ModDimensions;
import com.lacuna.mod.registry.ModRegistries;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
                .then(Commands.literal("givekit").executes(LacunaCommands::giveKit))
                .then(Commands.literal("banish")
                        .executes(ctx -> banish(ctx.getSource(), List.of(ctx.getSource().getEntityOrException())))
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> banish(ctx.getSource(), EntityArgument.getEntities(ctx, "targets")))))
                .then(Commands.literal("return")
                        .executes(ctx -> giveBack(ctx.getSource(), List.of(ctx.getSource().getEntityOrException())))
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> giveBack(ctx.getSource(), EntityArgument.getEntities(ctx, "targets")))))
                .then(Commands.literal("rep").executes(LacunaCommands::notYet))
                .then(Commands.literal("township").executes(LacunaCommands::notYet))
                .then(Commands.literal("scent").executes(LacunaCommands::notYet)));
    }

    private static int giveKit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int count = 0;
        for (var holder : ModRegistries.ITEMS.getEntries()) {
            ItemStack stack = new ItemStack(holder.get());
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            count++;
        }
        int given = count;
        ctx.getSource().sendSuccess(() -> Component.literal("Gave one of each of " + given + " Lacuna items"), true);
        return given;
    }

    private static int banish(CommandSourceStack source, Collection<? extends Entity> targets) {
        if (!BanishmentManager.enabled(source.getServer())) {
            source.sendFailure(Component.literal("Banishment is disabled or the Forgotten Land dimension is missing."));
            return 0;
        }
        int done = 0;
        for (Entity e : List.copyOf(targets)) {
            if (e.level().dimension() != Level.OVERWORLD) {
                continue;
            }
            if (BanishmentManager.banish(e) != null) {
                done++;
            }
        }
        int n = done;
        source.sendSuccess(() -> Component.literal("Banished " + n + " entities to the Forgotten Land"), true);
        return n;
    }

    private static int giveBack(CommandSourceStack source, Collection<? extends Entity> targets) {
        int done = 0;
        for (Entity e : List.copyOf(targets)) {
            if (!ModDimensions.isForgottenLand(e.level())) {
                continue;
            }
            if (BanishmentManager.returnEntity(e) != null) {
                done++;
            }
        }
        int n = done;
        source.sendSuccess(() -> Component.literal("Returned " + n + " entities from the Forgotten Land"), true);
        return n;
    }

    private static int notYet(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendFailure(Component.literal("Not available in this build yet."));
        return 0;
    }
}
