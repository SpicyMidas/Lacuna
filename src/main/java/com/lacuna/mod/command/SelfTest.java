package com.lacuna.mod.command;

import com.lacuna.mod.Balance;
import com.lacuna.mod.Lacuna;
import com.lacuna.mod.logic.CoordMath;
import com.lacuna.mod.registry.ModRegistries;
import com.lacuna.mod.registry.ModTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Internal invariants checked by {@code /lacuna selftest} (spec section 4): registries complete, recipes
 * parse, coordinate math round-trips, tags non-empty. Logs a PASS/FAIL summary.
 */
public final class SelfTest {
    private SelfTest() {}

    public static int run(CommandSourceStack source) {
        List<String> failures = new ArrayList<>();
        int[] checks = {0};
        MinecraftServer server = source.getServer();

        // Registries complete: every deferred holder bound.
        ModRegistries.BLOCKS.getEntries().forEach(h -> {
            checks[0]++;
            if (!h.isBound()) failures.add("unbound block " + h.getId());
        });
        ModRegistries.ITEMS.getEntries().forEach(h -> {
            checks[0]++;
            if (!h.isBound()) failures.add("unbound item " + h.getId());
        });
        ExtraChecks.registries(failures, checks);

        // Recipes parse: every lacuna recipe file produced a recipe.
        Map<ResourceLocation, Resource> recipeFiles = server.getResourceManager().listResources("recipe",
                rl -> rl.getNamespace().equals(Lacuna.MODID) && rl.getPath().endsWith(".json"));
        for (ResourceLocation file : recipeFiles.keySet()) {
            checks[0]++;
            String path = file.getPath().substring("recipe/".length(), file.getPath().length() - ".json".length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Lacuna.MODID, path);
            if (server.getRecipeManager().byKey(id).isEmpty()) {
                failures.add("recipe did not load: " + id);
            }
        }

        // Coordinate math round trips (section 6).
        for (int x = -500; x <= 500; x += 7) {
            checks[0]++;
            int back = CoordMath.toOverworld(CoordMath.toForgotten(x, Balance.FL_SCALE), Balance.FL_SCALE);
            int error = x - back;
            if (error < 0 || error > (int) Balance.FL_SCALE - 1) {
                failures.add("coord round trip " + x + " -> " + back);
            }
        }

        // Tags non-empty.
        for (TagKey<Block> tag : List.of(ModTags.Blocks.DIVINE_SIGHT_TARGETS, ModTags.Blocks.DIVINE_GLASS)) {
            checks[0]++;
            boolean nonEmpty = BuiltInRegistries.BLOCK.getTag(tag).map(set -> set.size() > 0).orElse(false);
            if (!nonEmpty) {
                failures.add("empty block tag " + tag.location());
            }
        }
        ExtraChecks.tags(failures, checks);
        ExtraChecks.world(server, failures, checks);

        String summary = failures.isEmpty()
                ? "Lacuna selftest PASS (" + checks[0] + " checks)"
                : "Lacuna selftest FAIL (" + failures.size() + "/" + checks[0] + "): " + String.join("; ", failures);
        Lacuna.LOGGER.info(summary);
        if (failures.isEmpty()) {
            source.sendSuccess(() -> Component.literal(summary), true);
            return 1;
        }
        source.sendFailure(Component.literal(summary));
        return 0;
    }
}
