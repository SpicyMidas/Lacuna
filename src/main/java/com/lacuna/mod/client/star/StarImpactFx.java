package com.lacuna.mod.client.star;

import com.lacuna.mod.client.fx.ClientFx;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.FxManager;
import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.client.fx.GroundDecal;
import com.lacuna.mod.client.fx.ScreenFx;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.star.Starfall;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The falling star's impact ({@code starfall_impact}: point, entity = the star, p0 = blast radius, p2 = 1 in water):
 * a white flash with a short pillar of light, a shockwave ring running over the ground, a molten glow that cools,
 * a burst of sparks, flames, star glints, smoke and earth, camera shake for everyone near and a flash for the closest.
 */
final class StarImpactFx implements ClientFx {
    private static final int LIFE = 40;

    private final Vec3 at;
    private final float radius;
    /** The crater rim the shockwave and the molten glow lie on. */
    private final GroundDecal decal = new GroundDecal();
    private int age;

    private StarImpactFx(Vec3 at, float radius) {
        this.at = at;
        this.radius = radius;
    }

    static void handle(FxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Vec3 at = p.a();
        FallingStarFx star = StarClient.STARS.get(p.entityId());
        if (star != null) {
            star.land(at);
        }
        burst(level, at, p.p2() > 0.5F);
        FxManager.add(new StarImpactFx(at, Math.max(1.0F, p.p0())));
        double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(at);
        if (d < Starfall.SHAKE_RANGE) {
            float k = (float) (1.0 - d / Starfall.SHAKE_RANGE);
            ScreenFx.shake(0.4F + 4.0F * k * k, 10 + (int) (10 * k));
        }
        if (d < 28.0) {
            ScreenFx.flash(0xFFF1D0, 0.5F * (float) (1.0 - d / 28.0), 10);
        }
    }

    private static void burst(ClientLevel level, Vec3 at, boolean wet) {
        RandomSource r = level.random;
        level.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION, true, at.x, at.y + 0.3, at.z, 1.0, 0.0, 0.0);
        for (int i = 0; i < 28; i++) {
            Vec3 d = dir(r);
            double sp = 0.15 + r.nextDouble() * 0.25;
            level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, at.x, at.y + 0.2, at.z, d.x * sp, Math.abs(d.y) * sp + 0.05, d.z * sp);
        }
        for (int i = 0; i < 36; i++) {
            Vec3 d = dir(r);
            double sp = 0.3 + r.nextDouble() * 0.45;
            level.addAlwaysVisibleParticle(ParticleTypes.FIREWORK, true, at.x, at.y + 0.3, at.z, d.x * sp, Math.abs(d.y) * sp + 0.1, d.z * sp);
        }
        for (int i = 0; i < 16; i++) {
            Vec3 d = dir(r);
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, at.x, at.y + 0.3, at.z, d.x * 0.25, Math.abs(d.y) * 0.25, d.z * 0.25);
            level.addAlwaysVisibleParticle(ModParticles.SOLAR_GLINT.get(), true, at.x + d.x * 1.5, at.y + 0.4 + Math.abs(d.y), at.z + d.z * 1.5,
                    0.0, 0.0, 0.0);
        }
        for (int i = 0; i < 8; i++) {
            level.addParticle(ParticleTypes.LAVA, at.x + r.nextGaussian() * 0.5, at.y + 0.2, at.z + r.nextGaussian() * 0.5, 0.0, 0.0, 0.0);
        }
        for (int i = 0; i < 10; i++) {
            Vec3 d = dir(r);
            level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, at.x + d.x, at.y + 0.3, at.z + d.z, d.x * 0.08, 0.06 + r.nextDouble() * 0.06,
                    d.z * 0.08);
        }
        for (int i = 0; i < 3; i++) {
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, at.x + r.nextGaussian() * 0.4, at.y + 0.2,
                    at.z + r.nextGaussian() * 0.4, 0.0, 0.05, 0.0);
        }
        BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
        if (!ground.isAir() && ground.getFluidState().isEmpty()) {
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            for (int i = 0; i < 30; i++) {
                Vec3 d = dir(r);
                level.addParticle(dust, at.x + d.x * 0.6, at.y + 0.3, at.z + d.z * 0.6, d.x * 0.5, 0.3 + r.nextDouble() * 0.5, d.z * 0.5);
            }
        }
        if (wet) {
            for (int i = 0; i < 24; i++) {
                level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, at.x + r.nextGaussian(), at.y + 0.5, at.z + r.nextGaussian(),
                        0.0, 0.1 + r.nextDouble() * 0.1, 0.0);
                level.addParticle(ParticleTypes.BUBBLE, at.x + r.nextGaussian(), at.y, at.z + r.nextGaussian(), 0.0, 0.3, 0.0);
            }
        }
    }

    private static Vec3 dir(RandomSource r) {
        Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
        return v.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : v.normalize();
    }

    @Override
    public boolean tick() {
        age++;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && age < 24 && level.random.nextFloat() < 0.5F) {
            RandomSource r = level.random;
            level.addParticle(ParticleTypes.SMALL_FLAME, at.x + r.nextGaussian() * 0.8, at.y + 0.1, at.z + r.nextGaussian() * 0.8,
                    0.0, 0.03, 0.0);
        }
        return age < LIFE;
    }

    @Override
    public void render(FxRenderContext ctx) {
        float t = ctx.time(age);
        PoseStack.Pose pose = ctx.pose().last();
        Vec3 c = ctx.rel(at);
        // the flash and its pillar of light
        float flash = Mth.clamp(1.0F - t / 8.0F, 0.0F, 1.0F);
        if (flash > 0.0F) {
            VertexConsumer core = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
            FxDraw.billboard(pose, core, ctx.camera(), c.add(0.0, 0.6, 0.0), 3.0F + 5.0F * (1.0F - flash), t * 0.05F,
                    FxDraw.argb(flash, FallingStarFx.WHITE_HOT));
            VertexConsumer beam = ctx.buffers().getBuffer(FxRenderTypes.beam());
            FxDraw.ribbon(pose, beam, c, c.add(0.0, 7.0 * (0.4 + 0.6 * flash), 0.0), 1.4F * flash, 0.2F * flash,
                    FxDraw.argb(flash, FallingStarFx.WHITE_HOT), FxDraw.argb(0.0F, FallingStarFx.GOLD), 0.0F, 7.0F);
        }
        // the shockwave over the ground
        float wave = Mth.clamp(t / 10.0F, 0.0F, 1.0F);
        if (wave < 1.0F) {
            float e = 1.0F - (1.0F - wave) * (1.0F - wave);
            VertexConsumer ring = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.RING));
            GroundDecal g = decal.at(ctx.level(), at, 0.6 + radius * 2.4);
            g.square(ctx, ring, at.x, at.z, 0.6F + radius * 2.4F * e, 0.006F, FxDraw.argb(0.9F * (1.0F - wave), FallingStarFx.GOLD));
            g.square(ctx, ring, at.x, at.z, 0.4F + radius * 1.6F * e, 0.012F, FxDraw.argb(0.6F * (1.0F - wave), FallingStarFx.STARLIGHT));
        }
        // the molten glow, cooling through amber to ember red
        float cool = Mth.clamp(t / LIFE, 0.0F, 1.0F);
        float flicker = 0.85F + 0.15F * Mth.sin(t * 1.9F) * Mth.sin(t * 0.7F + 1.0F);
        VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
        decal.at(ctx.level(), at, 0.6 + radius * 2.4).square(ctx, glow, at.x, at.z, radius * 1.5F, 0.0F,
                FxDraw.argb(0.7F * (1.0F - cool) * flicker, FallingStarFx.mix(FallingStarFx.GOLD, FallingStarFx.EMBER, cool)));
        FxDraw.billboard(pose, glow, ctx.camera(), c.add(0.0, 0.5, 0.0), radius * (1.0F + cool), 0.0F,
                FxDraw.argb(0.35F * (1.0F - cool) * flicker, FallingStarFx.AMBER));
    }
}
