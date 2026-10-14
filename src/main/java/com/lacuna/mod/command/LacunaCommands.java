package com.lacuna.mod.command;

import com.lacuna.mod.banish.BanishmentManager;
import com.lacuna.mod.mob.ScentTracker;
import com.lacuna.mod.village.VillageCommand;
import com.lacuna.mod.village.VillageRecord;
import com.lacuna.mod.village.VillageService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
                .then(Commands.literal("rep")
                        .then(Commands.literal("get").then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> repGet(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(-100, 100))
                                        .executes(ctx -> repSet(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "value")))))))
                .then(VillageCommand.build())
                .then(Commands.literal("scent").executes(ctx -> scent(ctx.getSource()))));
    }

    private static int giveKit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int count = 0;
        for (var holder : ModRegistries.ITEMS.getEntries()) {
            ItemStack stack = new ItemStack(holder.get());
            com.lacuna.mod.util.Give.toPlayer(player, stack);
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

    /** Lists every tracked scent trail and marks the caller's own points with particles. */
    private static int scent(CommandSourceStack source) {
        var trails = ScentTracker.all();
        if (trails.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No scent trails recorded (scentTracking may be disabled)."), false);
            return 0;
        }
        ServerPlayer self = source.getPlayer();
        trails.forEach((uuid, trail) -> {
            ServerPlayer p = source.getServer().getPlayerList().getPlayer(uuid);
            String name = p == null ? uuid.toString() : p.getGameProfile().getName();
            var points = trail.points();
            String newest = points.isEmpty() ? "-" : String.format("%.0f %.0f %.0f", points.get(points.size() - 1).x(),
                    points.get(points.size() - 1).y(), points.get(points.size() - 1).z());
            source.sendSuccess(() -> Component.literal(name + ": " + points.size() + " points, newest at " + newest), false);
            if (self != null && self.getUUID().equals(uuid)) {
                for (var pt : points) {
                    self.serverLevel().sendParticles(self, ParticleTypes.HAPPY_VILLAGER, true, pt.x(), pt.y() + 0.5, pt.z(),
                            6, 0.2, 0.3, 0.2, 0.0);
                }
            }
        });
        return trails.size();
    }

    private static java.util.Optional<VillageRecord> villageHere(CommandSourceStack source) {
        return VillageService.at(source.getLevel(), BlockPos.containing(source.getPosition()));
    }

    private static int repGet(CommandSourceStack source, ServerPlayer player) {
        var t = VillageService.at(source.getLevel(), player.blockPosition()).or(() -> villageHere(source));
        if (t.isEmpty()) {
            source.sendFailure(Component.literal("No village here."));
            return 0;
        }
        int rep = t.get().reputation(player.getUUID());
        source.sendSuccess(() -> Component.literal(player.getGameProfile().getName() + ": " + rep + " ("
                + com.lacuna.mod.logic.ReputationMath.tier(rep) + ") in village " + t.get().shortId()), false);
        return rep;
    }

    private static int repSet(CommandSourceStack source, ServerPlayer player, int value) {
        var t = VillageService.at(source.getLevel(), player.blockPosition()).or(() -> villageHere(source));
        if (t.isEmpty()) {
            source.sendFailure(Component.literal("No village here."));
            return 0;
        }
        VillageService.set(source.getLevel(), t.get(), player.getUUID(), value);
        source.sendSuccess(() -> Component.literal("Reputation of " + player.getGameProfile().getName() + " set to " + value), true);
        return 1;
    }
}
