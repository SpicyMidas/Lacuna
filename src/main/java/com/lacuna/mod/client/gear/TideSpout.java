package com.lacuna.mod.client.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.fx.ClientFx;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.client.fx.ScreenFx;
import com.lacuna.mod.gear.TideCurrents;
import com.lacuna.mod.network.FxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * The Tidecaller's Maelstrom and its closing geyser, drawn the way Minecraft draws water.
 * <p>
 * Everything is built from flat, blocky faces textured with block-atlas sprites at vanilla's pixel density: the
 * animated vanilla {@code water_still} (whirlpool floor) and {@code water_flow} (spout walls, flipped so it streams
 * upward), tinted with the biome's water colour and dimmed by the light level like real water, plus Lacuna's pixel-art
 * {@code tide_foam} and {@code tide_spray} sprites. One translucent render type (nearest filtering, no glow, no
 * gradients) holds all of it, so the faces are depth-sorted together.
 * <ul>
 *   <li>{@code tide_maelstrom}: a whirlpool of stepped water terraces turning faster towards a dark eye, foam spiral
 *       arms and a spray curtain at the rim; from the eye rises a waterspout - stacked octagonal rings, each spinning at
 *       its own speed, widening upward and swaying like a twister, wrapped in foam bands and crowned with spray.
 *       Vanilla particles: splashes flung off the spout, bubbles whirling up it, water dripping from the crown, wakes
 *       circling the pool.</li>
 *   <li>{@code tide_geyser}: the spout collapses into a column of water that shoots up, bursts into a spray crown
 *       with a falling curtain and a foam ring racing out over the ground.</li>
 * </ul>
 */
final class TideSpout {
    private static final ResourceLocation WATER_STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation WATER_FLOW = ResourceLocation.withDefaultNamespace("block/water_flow");
    private static final ResourceLocation FOAM = Lacuna.id("block/tide_foam");
    private static final ResourceLocation SPRAY = Lacuna.id("block/tide_spray");
    /** Sprite fraction per block: still water and Lacuna's sprites are 16 px a block, the flow sprite covers 2x2 blocks. */
    private static final float STILL_DENSITY = 1.0F;
    private static final float FLOW_DENSITY = 0.5F;
    private static final int WHITE = 0xFFFFFF;

    private TideSpout() {}

    // ------------------------------------------------------------------ shared drawing

    /** The sprites of one frame (looked up once per frame: the atlas can be rebuilt by a resource reload). */
    private record Sprites(TextureAtlasSprite still, TextureAtlasSprite flow, TextureAtlasSprite foam, TextureAtlasSprite spray) {
        static Sprites get() {
            Function<ResourceLocation, TextureAtlasSprite> atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
            return new Sprites(atlas.apply(WATER_STILL), atlas.apply(WATER_FLOW), atlas.apply(FOAM), atlas.apply(SPRAY));
        }
    }

    private static RenderType type() {
        return FxRenderTypes.shade(InventoryMenu.BLOCK_ATLAS);
    }

    /** Water colour and brightness at a spot, refreshed every few ticks (vanilla water is biome-tinted and lit). */
    private static final class Look {
        int water = 0x3F76E4;
        float light = 1.0F;

        void update(ClientLevel level, Vec3 at) {
            BlockPos pos = BlockPos.containing(at.x, at.y + 0.5, at.z);
            water = BiomeColors.getAverageWaterColor(level, pos);
            float sky = level.getBrightness(LightLayer.SKY, pos) / 15.0F * level.getSkyDarken(1.0F);
            float block = level.getBrightness(LightLayer.BLOCK, pos) / 15.0F;
            light = 0.3F + 0.7F * Math.max(sky, block);
        }

        /** Water tint darkened by {@code k} (deep water) and the light level, at alpha {@code a}. */
        int tint(float k, float a) {
            return FxDraw.argb(a, scale(water, k * light));
        }

        /** Foam / spray (white sprites) at the light level. */
        int white(float a) {
            return FxDraw.argb(a, scale(WHITE, light));
        }
    }

    private static int scale(int rgb, float k) {
        int r = Mth.clamp((int) (((rgb >> 16) & 0xFF) * k), 0, 255);
        int g = Mth.clamp((int) (((rgb >> 8) & 0xFF) * k), 0, 255);
        int b = Mth.clamp((int) ((rgb & 0xFF) * k), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    private static void vtx(VertexConsumer vc, PoseStack.Pose pose, double x, double y, double z, float u, float v, int argb) {
        vc.addVertex(pose, (float) x, (float) y, (float) z).setUv(u, v).setColor(argb);
    }

    private static boolean skip(int i, int every, int phase) {
        return every > 1 && Math.floorMod(i + phase, every) != 0;
    }

    /**
     * A ring of {@code sides} flat faces from centre {@code c0} (height {@code y0}, radius {@code r0}) up to {@code c1}
     * ({@code y1}, {@code r1}), turned by {@code turn} radians: a straight band (equal radii) or a flaring one. Each face
     * shows the sprite at {@code density} sprite-widths per block; {@code flip} turns it upside down (water_flow then
     * streams upward). With {@code every > 1} only every n-th face is drawn (foam bands, spray gaps).
     */
    private static void band(VertexConsumer vc, PoseStack.Pose pose, Vec3 c0, double y0, double r0, Vec3 c1, double y1, double r1,
                             int sides, double turn, TextureAtlasSprite sprite, float density, boolean flip, int argb, int every, int phase) {
        if ((argb >>> 24) == 0) {
            return;
        }
        double width = 2.0 * Math.max(r0, r1) * Math.sin(Math.PI / sides);
        double length = Math.sqrt((y1 - y0) * (y1 - y0) + (r1 - r0) * (r1 - r0));
        float us = Mth.clamp((float) (width * density), 0.0625F, 1.0F);
        float vs = Mth.clamp((float) (length * density), 0.0625F, 1.0F);
        float top = flip ? vs : 0.0F;
        float bottom = flip ? 0.0F : vs;
        for (int i = 0; i < sides; i++) {
            if (skip(i, every, phase)) {
                continue;
            }
            double a0 = turn + Mth.TWO_PI * i / sides;
            double a1 = turn + Mth.TWO_PI * (i + 1) / sides;
            double cos0 = Math.cos(a0);
            double sin0 = Math.sin(a0);
            double cos1 = Math.cos(a1);
            double sin1 = Math.sin(a1);
            // neighbouring faces take different columns of the sprite so the pattern does not repeat face to face
            float u0 = (1.0F - us) * (Math.floorMod(i * 5 + phase * 3, 4) / 3.0F);
            float su0 = sprite.getU(u0);
            float su1 = sprite.getU(u0 + us);
            float sTop = sprite.getV(top);
            float sBottom = sprite.getV(bottom);
            vtx(vc, pose, c0.x + cos0 * r0, c0.y + y0, c0.z + sin0 * r0, su0, sBottom, argb);
            vtx(vc, pose, c0.x + cos1 * r0, c0.y + y0, c0.z + sin1 * r0, su1, sBottom, argb);
            vtx(vc, pose, c1.x + cos1 * r1, c1.y + y1, c1.z + sin1 * r1, su1, sTop, argb);
            vtx(vc, pose, c1.x + cos0 * r1, c1.y + y1, c1.z + sin0 * r1, su0, sTop, argb);
        }
    }

    private static void band(VertexConsumer vc, PoseStack.Pose pose, Vec3 c, double y0, double r0, double y1, double r1, int sides,
                             double turn, TextureAtlasSprite sprite, float density, boolean flip, int argb, int every, int phase) {
        band(vc, pose, c, y0, r0, c, y1, r1, sides, turn, sprite, density, flip, argb, every, phase);
    }

    /** A flat ring of {@code segments} faces at height {@code y} above {@code c}, from radius rIn to rOut. */
    private static void annulus(VertexConsumer vc, PoseStack.Pose pose, Vec3 c, double y, double rIn, double rOut, int segments,
                                double turn, TextureAtlasSprite sprite, float density, int argb, int every, int phase) {
        if ((argb >>> 24) == 0 || rOut <= rIn) {
            return;
        }
        float us = Mth.clamp((float) (rOut * Mth.TWO_PI / segments * density), 0.0625F, 1.0F);
        float vs = Mth.clamp((float) ((rOut - rIn) * density), 0.0625F, 1.0F);
        for (int i = 0; i < segments; i++) {
            if (skip(i, every, phase)) {
                continue;
            }
            double a0 = turn + Mth.TWO_PI * i / segments;
            double a1 = turn + Mth.TWO_PI * (i + 1) / segments;
            double cos0 = Math.cos(a0);
            double sin0 = Math.sin(a0);
            double cos1 = Math.cos(a1);
            double sin1 = Math.sin(a1);
            float u0 = (1.0F - us) * (Math.floorMod(i * 3 + phase, 4) / 3.0F);
            float su0 = sprite.getU(u0);
            float su1 = sprite.getU(u0 + us);
            float sv0 = sprite.getV(0.0F);
            float sv1 = sprite.getV(vs);
            vtx(vc, pose, c.x + cos0 * rIn, c.y + y, c.z + sin0 * rIn, su0, sv0, argb);
            vtx(vc, pose, c.x + cos0 * rOut, c.y + y, c.z + sin0 * rOut, su0, sv1, argb);
            vtx(vc, pose, c.x + cos1 * rOut, c.y + y, c.z + sin1 * rOut, su1, sv1, argb);
            vtx(vc, pose, c.x + cos1 * rIn, c.y + y, c.z + sin1 * rIn, su1, sv0, argb);
        }
    }

    /** Faces around a circle of radius r: about one block of arc each, a multiple of four. */
    private static int segmentsFor(double r) {
        return Math.max(8, (int) Math.ceil(Mth.TWO_PI * r / 4.0) * 4);
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static boolean wet(ClientLevel level, double x, double y, double z) {
        return level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER);
    }

    /** Rising bubbles: a real bubble column particle under water, a popping bubble in the open air. */
    private static ParticleOptions rising(ClientLevel level, double x, double y, double z) {
        return wet(level, x, y, z) ? ParticleTypes.BUBBLE_COLUMN_UP : ParticleTypes.BUBBLE_POP;
    }

    private static boolean isSelf(FxPayload p) {
        Player player = Minecraft.getInstance().player;
        return player != null && player.getId() == p.entityId();
    }

    // ------------------------------------------------------------------ the maelstrom

    static final class Maelstrom implements ClientFx {
        private static final int SEGMENTS = 9;
        private static final int SIDES = 8;
        private static final double TOP_HEIGHT = 7.0;
        private final Vec3 center;
        private final float radius;
        private final int duration;
        private final int rings;
        private final Look look = new Look();
        private int age;

        Maelstrom(FxPayload p) {
            this.center = p.a();
            this.radius = p.p0() > 0.0F ? p.p0() : (float) Balance.SICKLE_MAELSTROM_RADIUS;
            this.duration = p.p1() > 0.0F ? (int) p.p1() : Balance.SICKLE_MAELSTROM_TICKS;
            this.rings = Math.max(3, Mth.ceil(radius));
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                look.update(level, center);
            }
            if (isSelf(p)) {
                ScreenFx.shake(1.2F, 10);
            }
        }

        private float envelope(float time) {
            return smooth(time / 14.0F) * (1.0F - Mth.clamp((time - duration) / 6.0F, 0.0F, 1.0F));
        }

        /** Height of terrace i above the centre: the pool steps down towards the eye. */
        private double terrace(int i, float env) {
            return 0.03 + 0.125 * i * env;
        }

        private double spoutHeight(float time, float env) {
            return (1.5 + (TOP_HEIGHT - 1.5) * smooth(time / 30.0F)) * env;
        }

        /** Spout radius at fraction f of its height: a narrow neck widening upward like a waterspout. */
        private static double spoutRadius(double f) {
            return 0.42 + 1.9 * Math.pow(f, 1.6);
        }

        /** The spout's centre line at fraction f (relative to the eye), swaying like a twister. */
        private static Vec3 sway(double f, float time) {
            return new Vec3(Math.sin(time * 0.11 + f * 2.6) * 0.45 * f, 0.0, Math.cos(time * 0.09 + f * 2.1) * 0.45 * f);
        }

        @Override
        public boolean tick() {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level != null && age < duration) {
                if (age % 10 == 0) {
                    look.update(level, center);
                }
                particles(level, level.random);
                // anyone caught inside sees the water close around them
                Player self = mc.player;
                if (self != null && self.distanceToSqr(center.x, self.getY(), center.z) < radius * radius
                        && Math.abs(self.getY() - center.y) < 6.0) {
                    ScreenFx.hold("tide_maelstrom", 0x0F4F73, 0.45F * envelope(age), true);
                }
            }
            return ++age < duration + 6;
        }

        private void particles(ClientLevel level, RandomSource r) {
            float env = envelope(age);
            double eye = center.y + terrace(0, env);
            double h = spoutHeight(age, env);
            // bubbles whirling up the spout on two helices, splashes flung off its wall
            for (int j = 0; j < 2; j++) {
                double f = (age * 0.045 + j * 0.5) % 1.0;
                double a = age * 0.55 + j * Math.PI;
                Vec3 s = sway(f, age);
                double rad = spoutRadius(f) * 0.85;
                double x = center.x + s.x + Math.cos(a) * rad;
                double y = eye + f * h;
                double z = center.z + s.z + Math.sin(a) * rad;
                level.addParticle(rising(level, x, y, z), x, y, z, 0.0, 0.04, 0.0);
                double ta = a + r.nextDouble() * Mth.TWO_PI;
                double tf = r.nextDouble();
                Vec3 ts = sway(tf, age);
                double tr = spoutRadius(tf);
                level.addParticle(ParticleTypes.SPLASH, center.x + ts.x + Math.cos(ta) * tr, eye + tf * h, center.z + ts.z + Math.sin(ta) * tr,
                        -Math.sin(ta) * 0.3, 0.0, Math.cos(ta) * 0.3);
            }
            // water raining off the crown
            for (int i = 0; i < 2; i++) {
                double a = r.nextDouble() * Mth.TWO_PI;
                Vec3 s = sway(1.0, age);
                double rad = spoutRadius(1.0) + 0.3 + r.nextDouble() * 0.6;
                level.addParticle(ParticleTypes.FALLING_WATER, center.x + s.x + Math.cos(a) * rad, eye + h,
                        center.z + s.z + Math.sin(a) * rad, 0.0, 0.0, 0.0);
            }
            // the pool: splashes and wakes circling inward (counter-clockwise from above, like the server pull)
            for (int i = 0; i < 4; i++) {
                double a = r.nextDouble() * Mth.TWO_PI;
                double rad = radius * (0.2 + 0.8 * r.nextDouble());
                int ring = Mth.clamp((int) (rad / radius * rings), 0, rings - 1);
                double x = center.x + Math.cos(a) * rad;
                double z = center.z + Math.sin(a) * rad;
                double y = center.y + terrace(ring, env) + 0.05;
                double spin = 0.16 + 0.2 * (1.0 - rad / radius);
                double vx = -Math.sin(a) * spin - Math.cos(a) * 0.06;
                double vz = Math.cos(a) * spin - Math.sin(a) * 0.06;
                level.addParticle(i < 2 ? ParticleTypes.SPLASH : ParticleTypes.FISHING, x, y, z, vx, i < 2 ? 0.0 : 0.01, vz);
            }
            if ((age & 1) == 0) {
                double a = r.nextDouble() * Mth.TWO_PI;
                double rad = r.nextDouble() * 1.2;
                double x = center.x + Math.cos(a) * rad;
                double z = center.z + Math.sin(a) * rad;
                level.addParticle(rising(level, x, center.y - 0.3, z), x, center.y + 0.1, z, 0.0, 0.06, 0.0);
            }
            if (age % 3 == 0) {
                Vec3 s = sway(1.0, age);
                level.addParticle(ParticleTypes.CLOUD, center.x + s.x + (r.nextDouble() - 0.5) * 2.0, eye + h + 0.2,
                        center.z + s.z + (r.nextDouble() - 0.5) * 2.0, 0.0, 0.02, 0.0);
            }
        }

        @Override
        public void render(FxRenderContext ctx) {
            float time = ctx.time(age);
            float env = envelope(time);
            if (env <= 0.0F) {
                return;
            }
            Sprites sp = Sprites.get();
            PoseStack.Pose pose = ctx.pose().last();
            VertexConsumer vc = ctx.buffers().getBuffer(type());
            Vec3 c = ctx.rel(center);
            pool(vc, pose, sp, c, time, env);
            spout(vc, pose, sp, c, time, env);
        }

        /** The whirlpool: stepped rings of still water, darker and faster towards the eye, foam arms and a rim curtain. */
        private void pool(VertexConsumer vc, PoseStack.Pose pose, Sprites sp, Vec3 c, float time, float env) {
            double w = radius / rings;
            for (int i = 0; i < rings; i++) {
                double rIn = i * w;
                double rOut = (i + 1) * w;
                int segments = segmentsFor(rOut);
                double turn = time * (0.02 + 0.1 * (1.0 - (double) i / rings)) + i * 0.7;
                double y = terrace(i, env);
                float depth = 0.5F + 0.5F * i / (rings - 1.0F);
                float a = (0.85F - 0.05F * i) * env * (i == rings - 1 ? 0.6F : 1.0F);
                annulus(vc, pose, c, y, rIn, rOut, segments, turn, sp.still(), STILL_DENSITY, look.tint(depth, a), 1, 0);
                // foam arms: every third face, one face further round on each ring, so the arms wind into the eye
                annulus(vc, pose, c, y + 0.01, rIn + w * 0.15, rOut - w * 0.25, segments, turn, sp.foam(), STILL_DENSITY,
                        look.white(0.9F * env), 3, i);
                // a foam lip along the edge of every terrace (the pool is water over water: the lips make the steps read)
                annulus(vc, pose, c, y + 0.012, rOut - w * 0.2, rOut, segments, turn, sp.foam(), STILL_DENSITY,
                        look.white(0.85F * env), 1, 0);
                if (i < rings - 1) {
                    // the step down to the next ring: water spilling inward over the edge
                    band(vc, pose, c, y, rOut, terrace(i + 1, env), rOut, segments, turn, sp.flow(), FLOW_DENSITY, false,
                            look.tint(depth * 0.8F, a), 1, 0);
                }
            }
            // spray thrown up all round the rim
            double rim = radius;
            double top = terrace(rings - 1, env);
            band(vc, pose, c, top, rim, top + 0.7 * env, rim * 1.04, segmentsFor(rim), time * 0.03, sp.spray(), STILL_DENSITY, true,
                    look.white(0.8F * env), 2, 0);
        }

        /** The waterspout: stacked rings of upward-streaming water, each spinning on its own, wrapped in foam. */
        private void spout(VertexConsumer vc, PoseStack.Pose pose, Sprites sp, Vec3 c, float time, float env) {
            double h = spoutHeight(time, env);
            if (h < 0.1) {
                return;
            }
            Vec3 base = c.add(0.0, terrace(0, env), 0.0);
            // a foam skirt where the spout sucks up the pool
            band(vc, pose, base, 0.0, 1.35, 0.45, 0.5, SIDES, -time * 0.4, sp.foam(), STILL_DENSITY, true, look.white(0.95F * env), 1, 0);
            for (int k = 0; k < SEGMENTS; k++) {
                double f0 = (double) k / SEGMENTS;
                double f1 = (double) (k + 1) / SEGMENTS;
                Vec3 c0 = base.add(sway(f0, time));
                Vec3 c1 = base.add(sway(f1, time));
                double y0 = f0 * h;
                double y1 = f1 * h;
                double r0 = spoutRadius(f0) * (0.6 + 0.4 * env);
                double r1 = spoutRadius(f1) * (0.6 + 0.4 * env);
                double turn = time * (0.32 - 0.016 * k) + k * 0.45;
                // a darker core seen through the outer water, the outer wall, then a band of foam spinning faster
                band(vc, pose, c0, y0, r0 * 0.55, c1, y1, r1 * 0.55, SIDES, -turn * 0.8, sp.flow(), FLOW_DENSITY, true,
                        look.tint(0.55F, 0.85F * env), 1, 0);
                band(vc, pose, c0, y0, r0, c1, y1, r1, SIDES, turn, sp.flow(), FLOW_DENSITY, true, look.tint(1.0F, 0.6F * env), 1, 0);
                band(vc, pose, c0, y0, r0 * 1.05 + 0.03, c1, y1, r1 * 1.05 + 0.03, SIDES, turn * 1.35, sp.foam(), STILL_DENSITY, true,
                        look.white(0.95F * env), 2, k);
            }
            // the crown: spray bursting from the top ring, foam lying on its lip
            Vec3 top = base.add(sway(1.0, time));
            double rTop = spoutRadius(1.0) * (0.6 + 0.4 * env);
            band(vc, pose, top, h, rTop, top, h + 0.6, rTop + 0.7, SIDES * 2, time * 0.25, sp.spray(), STILL_DENSITY, true,
                    look.white(0.85F * env), 1, 0);
            annulus(vc, pose, top, h + 0.02, rTop * 0.55, rTop + 0.25, SIDES, time * 0.3, sp.foam(), STILL_DENSITY, look.white(0.9F * env),
                    1, 0);
        }
    }

    // ------------------------------------------------------------------ the geyser

    static final class Geyser implements ClientFx {
        private static final int LIFE = 32;
        private static final int RISE = 5;
        private static final int HOLD = 14;
        private static final int SEGMENTS = 10;
        private static final int SIDES = 8;
        private final Vec3 center;
        private final float radius;
        private final float height;
        private final Look look = new Look();
        private int age;

        Geyser(FxPayload p) {
            this.center = p.a();
            this.radius = p.p0() > 0.0F ? p.p0() : (float) Balance.SICKLE_MAELSTROM_RADIUS;
            this.height = p.p1() > 0.0F ? p.p1() : TideCurrents.GEYSER_HEIGHT;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                double dist = mc.player.position().distanceTo(center);
                if (dist < 18.0) {
                    ScreenFx.shake((float) (3.0 * (1.0 - dist / 18.0)) + 0.4F, 14);
                }
                if (dist < radius) {
                    ScreenFx.flash(0xCFF5FF, 0.25F, 8);
                }
            }
            ClientLevel level = mc.level;
            if (level != null) {
                look.update(level, center);
                RandomSource r = level.random;
                for (int i = 0; i < 30; i++) {
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double rad = 0.5 + r.nextDouble() * 2.5;
                    double x = center.x + Math.cos(a) * rad;
                    double z = center.z + Math.sin(a) * rad;
                    level.addParticle(ParticleTypes.SPLASH, x, center.y + 0.2, z, Math.cos(a) * 0.35, 0.0, Math.sin(a) * 0.35);
                    if (i % 3 == 0) {
                        level.addParticle(ParticleTypes.CLOUD, x, center.y + 0.3, z, Math.cos(a) * 0.12, 0.05, Math.sin(a) * 0.12);
                        level.addParticle(rising(level, x, center.y - 0.2, z), x, center.y + 0.3, z, 0.0, 0.15, 0.0);
                    }
                }
            }
        }

        private float columnHeight(float time) {
            if (time < RISE) {
                float t = time / RISE;
                return height * (1.0F - (1.0F - t) * (1.0F - t));
            }
            if (time < HOLD) {
                return height * (1.0F + 0.04F * Mth.sin(time * 1.7F));
            }
            float t = Mth.clamp((time - HOLD) / (LIFE - HOLD), 0.0F, 1.0F);
            return height * (1.0F - t * t);
        }

        @Override
        public boolean tick() {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null && age < HOLD + 6) {
                RandomSource r = level.random;
                float h = columnHeight(age);
                for (int i = 0; i < 8; i++) {
                    // spray peeling off the column at every height
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double y = center.y + r.nextDouble() * h;
                    double rad = 1.0 + 0.4 * r.nextDouble();
                    level.addParticle(ParticleTypes.SPLASH, center.x + Math.cos(a) * rad, y, center.z + Math.sin(a) * rad,
                            Math.cos(a) * 0.25, 0.0, Math.sin(a) * 0.25);
                }
                for (int i = 0; i < 6; i++) {
                    // the crown rains back down
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double rad = 0.5 + r.nextDouble() * 2.6;
                    level.addParticle(ParticleTypes.FALLING_WATER, center.x + Math.cos(a) * rad, center.y + h * (0.8 + 0.2 * r.nextDouble()),
                            center.z + Math.sin(a) * rad, 0.0, 0.0, 0.0);
                }
                double a = r.nextDouble() * Mth.TWO_PI;
                level.addParticle(ParticleTypes.CLOUD, center.x + Math.cos(a) * 0.8, center.y + h, center.z + Math.sin(a) * 0.8,
                        Math.cos(a) * 0.1, 0.02, Math.sin(a) * 0.1);
                double bx = center.x + (r.nextDouble() - 0.5);
                double bz = center.z + (r.nextDouble() - 0.5);
                level.addParticle(rising(level, bx, center.y + 0.5, bz), bx, center.y + 0.5 + r.nextDouble() * h * 0.5, bz, 0.0, 0.1, 0.0);
            }
            return ++age < LIFE;
        }

        @Override
        public void render(FxRenderContext ctx) {
            float time = ctx.time(age);
            float h = columnHeight(time);
            float fade = 1.0F - Mth.clamp((time - HOLD) / (LIFE - HOLD), 0.0F, 1.0F);
            if (fade <= 0.0F || h <= 0.05F) {
                return;
            }
            Sprites sp = Sprites.get();
            PoseStack.Pose pose = ctx.pose().last();
            VertexConsumer vc = ctx.buffers().getBuffer(type());
            Vec3 c = ctx.rel(center).add(0.0, 0.03, 0.0);
            float open = Mth.clamp((time - RISE + 1.0F) / 6.0F, 0.0F, 1.0F);
            // the column: stacked spinning rings of water shooting up, foam bands round them
            for (int k = 0; k < SEGMENTS; k++) {
                double f0 = (double) k / SEGMENTS;
                double f1 = (double) (k + 1) / SEGMENTS;
                double y0 = f0 * h;
                double y1 = f1 * h;
                double r0 = 1.2 - 0.25 * f0 + 0.06 * Mth.sin(time * 1.3F + k);
                double r1 = 1.2 - 0.25 * f1 + 0.06 * Mth.sin(time * 1.3F + k + 1);
                double turn = time * (0.5 - 0.02 * k) + k * 0.6;
                band(vc, pose, c, y0, r0 * 0.55, y1, r1 * 0.55, SIDES, -turn, sp.flow(), FLOW_DENSITY, true, look.tint(0.6F, 0.85F * fade), 1, 0);
                band(vc, pose, c, y0, r0, y1, r1, SIDES, turn, sp.flow(), FLOW_DENSITY, true, look.tint(1.0F, 0.65F * fade), 1, 0);
                band(vc, pose, c, y0, r0 * 1.05 + 0.03, y1, r1 * 1.05 + 0.03, SIDES, turn * 1.4, sp.foam(), STILL_DENSITY, true,
                        look.white(0.95F * fade), 2, k);
            }
            // the crown bursts open at the top and a curtain of water falls back around the column
            double rTop = 0.95;
            band(vc, pose, c, h, rTop, h + 0.9 * open, rTop + 1.8 * open, SIDES * 2, time * 0.2, sp.spray(), STILL_DENSITY, true,
                    look.white(0.9F * fade), 1, 0);
            annulus(vc, pose, c, h + 0.02, 0.0, rTop + 0.2, SIDES, time * 0.4, sp.foam(), STILL_DENSITY, look.white(0.95F * fade), 1, 0);
            if (open > 0.0F) {
                double curtain = rTop + 1.8 * open;
                band(vc, pose, c, h * 0.2, curtain, h * 0.95, curtain, SIDES * 2, -time * 0.1, sp.spray(), STILL_DENSITY, false,
                        look.white(0.7F * fade * open), 2, 0);
            }
            // a skirt of water bursting out at the foot and a foam ring racing out over the ground
            float burst = Mth.clamp(time / 8.0F, 0.0F, 1.0F);
            band(vc, pose, c, 0.0, 1.3, 0.3 + 0.9 * (1.0F - burst), 1.6 + 2.4 * burst, SIDES * 2, -time * 0.3, sp.flow(), FLOW_DENSITY, true,
                    look.tint(1.0F, 0.6F * fade * (1.0F - 0.5F * burst)), 1, 0);
            float ring = Mth.clamp(time / 12.0F, 0.0F, 1.0F);
            double rIn = 1.3 + (radius - 0.5) * ring;
            annulus(vc, pose, c, 0.02, rIn, rIn + 0.9, segmentsFor(rIn + 0.9), time * 0.05, sp.foam(), STILL_DENSITY,
                    look.white(0.9F * (1.0F - ring)), 1, 0);
        }
    }
}
