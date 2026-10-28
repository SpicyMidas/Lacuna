package com.lacuna.mod.structure;

import com.lacuna.mod.Balance;
import com.lacuna.mod.logic.PathTrail;
import com.lacuna.mod.registry.ModDamageTypes;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Reflection (spec M10): a dark-silver double of the player who woke it. It does not path-find: it replays its
 * player's own path 60 ticks late (positions sampled every 5 ticks into a volatile {@link PathTrail}), passing
 * through mirrors like a reflection would. Touching the player deals 6. 20 HP, never saved; it vanishes if its
 * player leaves 48 blocks, logs out or changes dimension. Beaten by a player it leaves a Lacunar Quintessence (its
 * loot table, {@code killed_by_player}); it carries nothing else and gives no experience.
 */
public class ReflectionEntity extends PathfinderMob {
    @Nullable
    private UUID target;
    @Nullable
    private BlockPos trigger;
    private final PathTrail history = new PathTrail(5, Balance.REFLECTION_DELAY * 2L, 40);
    private int hitCooldown;
    private boolean reported;

    public ReflectionEntity(EntityType<? extends ReflectionEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.noPhysics = true;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 6.0).add(Attributes.FOLLOW_RANGE, Balance.REFLECTION_LEASH)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    void bind(UUID player, BlockPos triggerPos) {
        this.target = player;
        this.trigger = triggerPos.immutable();
    }

    @Override
    public void tick() {
        noPhysics = true;
        super.tick();
        if (level() instanceof ServerLevel server && isAlive()) {
            serverTick(server);
        }
    }

    private void serverTick(ServerLevel server) {
        ServerPlayer player = target == null ? null : server.getServer().getPlayerList().getPlayer(target);
        if (player == null || player.level() != server || !player.isAlive() || player.distanceTo(this) > Balance.REFLECTION_LEASH) {
            vanish(server);
            return;
        }
        long now = server.getGameTime();
        history.record(player.getX(), player.getY(), player.getZ(), now);
        history.prune(now);
        history.positionAt(now - Balance.REFLECTION_DELAY).ifPresent(p -> {
            double dx = p[0] - getX();
            double dz = p[2] - getZ();
            if (dx * dx + dz * dz > 1.0E-4) {
                float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
                setYRot(yaw);
                setYHeadRot(yaw);
                yBodyRot = yaw;
            }
            setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); // scripted: travel() must not move it again
            setPos(p[0], p[1], p[2]);
        });
        if (hitCooldown > 0) {
            hitCooldown--;
        } else if (getBoundingBox().intersects(player.getBoundingBox().inflate(0.1))) {
            player.hurt(ModDamageTypes.source(server, ModDamageTypes.REFLECTION_TOUCH, this, this),
                    (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
            hitCooldown = 10;
        }
    }

    private void vanish(ServerLevel server) {
        report(server, false);
        discard();
    }

    private void report(ServerLevel server, boolean defeated) {
        if (!reported && trigger != null) {
            reported = true;
            LabyrinthHandler.onReflectionGone(server, trigger, defeated);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            report(server, source.getEntity() instanceof Player);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel server && reason != RemovalReason.KILLED) {
            report(server, false);
        }
        super.remove(reason);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
