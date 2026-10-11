package com.lacuna.mod.eclipse;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.logic.ChargeMath;
import com.lacuna.mod.registry.ModDamageTypes;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.Teleporter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The Eclipser dash: consumes all charge, cuts everything along the path, then starts Shadow Walk. */
public final class EclipseDash {
    public static final String FEATURE = "eclipse_dash";
    private static final double STEP = 0.25;

    private EclipseDash() {}

    public static boolean perform(ServerPlayer player, ItemStack stack, InteractionHand hand, int charge) {
        return SafeRun.call(FEATURE, () -> dash(player, stack, hand, charge), false);
    }

    private static boolean dash(ServerPlayer player, ItemStack stack, InteractionHand hand, int charge) {
        ServerLevel level = player.serverLevel();
        boolean phase = ShadowWalk.enabled();
        double distance = ChargeMath.dashDistance(charge, LacunaConfig.get(LacunaConfig.ECLIPSE_DASH_MAX));
        Vec3 start = player.position();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = start;
        for (double t = STEP; t <= distance + 1e-6; t += STEP) {
            Vec3 p = start.add(look.scale(t));
            BlockPos bp = BlockPos.containing(p);
            if (!level.hasChunkAt(bp) || !level.getWorldBorder().isWithinBounds(bp)
                    || p.y < level.getMinBuildHeight() + 1 || p.y > level.getMaxBuildHeight() - 3) {
                break;
            }
            if (!phase && !level.noCollision(player, player.getBoundingBox().move(p.subtract(start)))) {
                break; // without Shadow Walk the dash stops before solid blocks
            }
            end = p;
        }

        float damage = ChargeMath.dashDamage(charge);
        double half = player.getBbHeight() / 2.0;
        Vec3 a = start.add(0, half, 0);
        Vec3 b = end.add(0, half, 0);
        AABB area = new AABB(a, b).inflate(Balance.ECLIPSE_DASH_RADIUS + 1.0);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != player && e.isAlive() && !e.isSpectator())) {
            Vec3 c = target.getBoundingBox().getCenter();
            double d = ChargeMath.segmentDistance(c.x, c.y, c.z, a.x, a.y, a.z, b.x, b.y, b.z);
            if (d <= Balance.ECLIPSE_DASH_RADIUS + target.getBbWidth() / 2.0) {
                target.hurt(ModDamageTypes.source(level, ModDamageTypes.ECLIPSE, player), damage);
            }
        }

        int steps = (int) Math.min(28, Math.ceil(start.distanceTo(end) / 0.5));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = steps == 0 ? a : a.add(b.subtract(a).scale(i / (double) steps));
            level.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 1, 0.1, 0.2, 0.1, 0.0);
        }
        level.sendParticles(ParticleTypes.SMOKE, end.x, end.y + 1.0, end.z, 12, 0.3, 0.5, 0.3, 0.02);
        level.playSound(null, start.x, start.y, start.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.5F);
        level.playSound(null, end.x, end.y, end.z, SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.3F, 1.6F);

        BlockPos origin = player.blockPosition();
        Teleporter.teleport(player, level, end, player.getYRot(), player.getXRot());
        EclipserItem.setState(stack, new EclipseCharge(0, EclipserItem.state(stack).lastHit()));
        stack.hurtAndBreak(ChargeMath.durabilityCost(charge), player, LivingEntity.getSlotForHand(hand));
        if (phase) {
            ShadowWalk.begin(player, ChargeMath.shadowWalkTicks(charge), origin);
        }
        EclipserItem.showCharge(player, 0);
        return true;
    }
}
