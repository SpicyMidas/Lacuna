package com.lacuna.mod.mirror;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** A projectile echo kept by the Mirror of Recursion: its type, damage-relevant snapshot and reuse count. */
public record StoredProjectile(ResourceLocation entityType, double baseDamage, double speed, int reuseCount) {
    public static final Codec<StoredProjectile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("entity_type").forGetter(StoredProjectile::entityType),
            Codec.DOUBLE.optionalFieldOf("base_damage", 2.0).forGetter(StoredProjectile::baseDamage),
            Codec.DOUBLE.optionalFieldOf("speed", 1.6).forGetter(StoredProjectile::speed),
            Codec.INT.optionalFieldOf("reuse", 0).forGetter(StoredProjectile::reuseCount)
    ).apply(i, StoredProjectile::new));

    public static final Codec<List<StoredProjectile>> LIST_CODEC = CODEC.listOf();

    public static final StreamCodec<ByteBuf, StoredProjectile> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, StoredProjectile::entityType,
            ByteBufCodecs.DOUBLE, StoredProjectile::baseDamage,
            ByteBufCodecs.DOUBLE, StoredProjectile::speed,
            ByteBufCodecs.VAR_INT, StoredProjectile::reuseCount,
            StoredProjectile::new);

    public static final StreamCodec<ByteBuf, List<StoredProjectile>> LIST_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs.list());

    public StoredProjectile reused() {
        return new StoredProjectile(entityType, baseDamage, speed, reuseCount + 1);
    }
}
