package com.lacuna.mod.client.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lacuna.mod.Lacuna;
import com.lacuna.mod.registry.ModItems;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Almanac's table of contents, read from {@code assets/lacuna/almanac/entries.json} (written by
 * {@code tools/content/m20_almanac.py}, so a resource pack can reorder, add or retitle entries). Chapters keep their file
 * order; entries keep their order inside their chapter. Every text is a lang key: titles, the body text and the
 * optional "where to find it" line.
 */
final class AlmanacBook {
    static final ResourceLocation FILE = Lacuna.id("almanac/entries.json");

    record Chapter(String id, ItemStack icon, String titleKey, List<Entry> entries) {}

    record Entry(String id, Chapter chapter, ItemStack icon, List<ItemStack> extras, String titleKey, String textKey,
                 String whereKey) {}

    final List<Chapter> chapters;

    private AlmanacBook(List<Chapter> chapters) {
        this.chapters = chapters;
    }

    static AlmanacBook empty() {
        return new AlmanacBook(List.of());
    }

    /** Reads the entry file through the client resource manager (resource packs apply); a broken file reads as empty. */
    static AlmanacBook load() {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(FILE);
        if (resource.isEmpty()) {
            Lacuna.LOGGER.warn("[Lacuna] {} is missing: the Almanac has no pages", FILE);
            return empty();
        }
        try (Reader reader = resource.get().openAsReader()) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            Lacuna.LOGGER.warn("[Lacuna] cannot read {}: the Almanac has no pages", FILE, e);
            return empty();
        }
    }

    private static AlmanacBook parse(JsonObject root) {
        Map<String, Chapter> byId = new LinkedHashMap<>();
        for (JsonElement el : root.getAsJsonArray("chapters")) {
            JsonObject o = el.getAsJsonObject();
            String id = o.get("id").getAsString();
            String title = o.has("title") ? o.get("title").getAsString() : "almanac.lacuna.chapter." + id;
            byId.put(id, new Chapter(id, orAlmanac(stack(o.get("icon"))), title, new ArrayList<>()));
        }
        for (JsonElement el : root.getAsJsonArray("entries")) {
            JsonObject o = el.getAsJsonObject();
            Chapter chapter = byId.get(o.get("chapter").getAsString());
            if (chapter == null) {
                continue;
            }
            String id = o.get("id").getAsString();
            List<ItemStack> extras = new ArrayList<>();
            if (o.has("icons")) {
                JsonArray icons = o.getAsJsonArray("icons");
                for (JsonElement icon : icons) {
                    ItemStack s = stack(icon);
                    if (!s.isEmpty()) {
                        extras.add(s);
                    }
                }
            }
            chapter.entries().add(new Entry(id, chapter, orAlmanac(stack(o.get("icon"))), List.copyOf(extras),
                    string(o, "title", "almanac.lacuna." + id), string(o, "text", "almanac.lacuna." + id + ".text"),
                    string(o, "where", "")));
        }
        List<Chapter> chapters = new ArrayList<>();
        for (Chapter c : byId.values()) {
            if (!c.entries().isEmpty()) {
                chapters.add(c);
            }
        }
        return new AlmanacBook(List.copyOf(chapters));
    }

    private static String string(JsonObject o, String key, String fallback) {
        return o.has(key) ? o.get(key).getAsString() : fallback;
    }

    /** An item id; a missing or unknown one is an empty stack. */
    private static ItemStack stack(JsonElement id) {
        if (id == null || !id.isJsonPrimitive()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id.getAsString());
        Item item = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** Titles always show an icon: the Almanac itself stands in for a missing one. */
    private static ItemStack orAlmanac(ItemStack stack) {
        return stack.isEmpty() ? new ItemStack(ModItems.ALMANAC.get()) : stack;
    }
}
