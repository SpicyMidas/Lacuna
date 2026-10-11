package com.lacuna.mod.registry;

import com.lacuna.mod.Lacuna;
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

    private ModAttachments() {}
}
