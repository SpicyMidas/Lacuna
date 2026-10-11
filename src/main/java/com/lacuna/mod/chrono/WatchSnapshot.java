package com.lacuna.mod.chrono;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** One Chronoshard Pocket Watch snapshot (no inventory, by design). */
public record WatchSnapshot(ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot,
                            float health, int food, float saturation, long gameTime) {
    public static final Codec<WatchSnapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(WatchSnapshot::dimension),
            Codec.DOUBLE.fieldOf("x").forGetter(WatchSnapshot::x),
            Codec.DOUBLE.fieldOf("y").forGetter(WatchSnapshot::y),
            Codec.DOUBLE.fieldOf("z").forGetter(WatchSnapshot::z),
            Codec.FLOAT.optionalFieldOf("y_rot", 0.0F).forGetter(WatchSnapshot::yRot),
            Codec.FLOAT.optionalFieldOf("x_rot", 0.0F).forGetter(WatchSnapshot::xRot),
            Codec.FLOAT.optionalFieldOf("health", 20.0F).forGetter(WatchSnapshot::health),
            Codec.INT.optionalFieldOf("food", 20).forGetter(WatchSnapshot::food),
            Codec.FLOAT.optionalFieldOf("saturation", 5.0F).forGetter(WatchSnapshot::saturation),
            Codec.LONG.fieldOf("game_time").forGetter(WatchSnapshot::gameTime)
    ).apply(i, WatchSnapshot::new));

    public static final Codec<List<WatchSnapshot>> LIST_CODEC = CODEC.listOf();

    /** Snapshots are not needed by the client except for their count; the full codec is reused over the network. */
    public static final StreamCodec<RegistryFriendlyByteBuf, List<WatchSnapshot>> LIST_STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(LIST_CODEC);
}
