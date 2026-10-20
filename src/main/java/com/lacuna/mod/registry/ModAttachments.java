package com.lacuna.mod.registry;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.chrono.HarmfulEffectLog;
import com.lacuna.mod.eclipse.ShadowWalkState;
import com.lacuna.mod.gear.UmbralSwordItem;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Serializable per-entity data (spec section 3, "Data"). Empty string / 0 means "absent". */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Lacuna.MODID);

    /** Banishment record id (UUID string) carried by a banished entity across dimension changes. */
    public static final Supplier<AttachmentType<String>> BANISHMENT_ID = ATTACHMENTS.register("banishment_id",
            () -> AttachmentType.builder(() -> "").serialize(Codec.STRING, s -> !s.isEmpty()).build());

    /** Game time until which a player who just arrived from the Forgotten Land ignores damage. */
    public static final Supplier<AttachmentType<Long>> ARRIVAL_PROTECTION = ATTACHMENTS.register("arrival_protection",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG, v -> v != 0L).build());

    /** Relic item entities: owner UUID string ... */
    public static final Supplier<AttachmentType<String>> RELIC_OWNER = ATTACHMENTS.register("relic_owner",
            () -> AttachmentType.builder(() -> "").serialize(Codec.STRING, s -> !s.isEmpty()).build());

    /** ... and the game time until which only the owner may pick the relic up. */
    public static final Supplier<AttachmentType<Long>> RELIC_OWNER_UNTIL = ATTACHMENTS.register("relic_owner_until",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG, v -> v != 0L).build());

    /** Shadow Walk: game mode to restore, end time, dash origin (survives logout/crash). */
    public static final Supplier<AttachmentType<ShadowWalkState>> SHADOW_WALK = ATTACHMENTS.register("shadow_walk",
            () -> AttachmentType.builder(() -> ShadowWalkState.NONE).serialize(ShadowWalkState.CODEC, ShadowWalkState::active).build());

    /** When harmful effects were applied (Pocket Watch clears those of the last 10 s). */
    public static final Supplier<AttachmentType<HarmfulEffectLog>> HARMFUL_EFFECTS = ATTACHMENTS.register("harmful_effects",
            () -> AttachmentType.builder(() -> HarmfulEffectLog.EMPTY).serialize(HarmfulEffectLog.CODEC, l -> !l.applied().isEmpty()).build());

    /** Marks arrows shot from the Heartwood Bow (they root what they hit). */
    public static final Supplier<AttachmentType<Boolean>> HEARTWOOD_ARROW = ATTACHMENTS.register("heartwood_arrow",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL, v -> v).build());

    /** Umbral Rend stacks on a target (who applied them, how many, until which game tick). Transient: a 4 s window. */
    public static final Supplier<AttachmentType<UmbralSwordItem.Rend>> UMBRAL_REND = ATTACHMENTS.register("umbral_rend",
            () -> AttachmentType.builder(() -> UmbralSwordItem.Rend.NONE).build());

    /** M10: ore totals at the player's last Last Merchant visit (stock reflects what was mined since). */
    public static final Supplier<AttachmentType<java.util.Map<String, Integer>>> MINED_SNAPSHOT = ATTACHMENTS.register("mined_snapshot",
            () -> AttachmentType.<java.util.Map<String, Integer>>builder(() -> java.util.Map.of())
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.INT), m -> !m.isEmpty()).copyOnDeath().build());

    /** M11d: the player's guild ("" = none, else smiths/gemcutters/scholars); survives death. */
    public static final Supplier<AttachmentType<String>> GUILD = ATTACHMENTS.register("guild",
            () -> AttachmentType.builder(() -> "").serialize(Codec.STRING, s -> !s.isEmpty()).copyOnDeath().build());

    /** M11d: a Guildmaster villager's guild, read once from its job-site board. */
    public static final Supplier<AttachmentType<String>> VILLAGER_GUILD = ATTACHMENTS.register("villager_guild",
            () -> AttachmentType.builder(() -> "").serialize(Codec.STRING, s -> !s.isEmpty()).build());

    /** Starlace Bandage wraps on a creature (mummy/Mummification); saved once it is a finished mummy, synced to clients. */
    public static final Supplier<AttachmentType<com.lacuna.mod.mummy.Mummy>> MUMMY = ATTACHMENTS.register("mummy",
            () -> AttachmentType.builder(() -> com.lacuna.mod.mummy.Mummy.NONE)
                    .serialize(com.lacuna.mod.mummy.Mummy.CODEC, com.lacuna.mod.mummy.Mummy::done)
                    .sync(com.lacuna.mod.mummy.Mummy.STREAM_CODEC).build());

    private ModAttachments() {}
}
