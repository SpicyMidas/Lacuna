package com.lacuna.mod.client.gear;

import com.lacuna.mod.client.fx.ClientFx;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.FxHandlers;
import com.lacuna.mod.client.fx.FxManager;
import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.client.fx.ScreenFx;
import com.lacuna.mod.gear.StarlaceSet;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.network.StarStepPayload;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.util.SafeRun;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Starlace client side: the Constellation Step input (the rising edge of the jump key in mid-air, reported to the
 * server, which decides) and the set's visuals:
 * <ul>
 *   <li>{@code starlace_step}: a ring of stars under the feet and a small constellation (5 stars joined by faint lines)
 *       left hanging in the air for ~0.6 s where the player kicked off.</li>
 *   <li>{@code starlace_starfall}: rings of pale gold and lavender light racing out over the ground to the shockwave
 *       radius, a flash of glints and rising motes.</li>
 * </ul>
 */
public final class StarlaceClient {
    private static final String FEATURE = "starlace_fx";
    private static final int GOLD = 0xFFE3A0;
    private static final int LAVENDER = 0xB9A2FF;
    private static final int STARLIGHT = 0xF2EEFF;

    private static boolean jumpWasDown;
    private static boolean stepped;
    private static int airTicks;

    private StarlaceClient() {}

    public static void register(IEventBus bus) {
        bus.addListener(StarlaceClient::onClientTick);
        StarlaceSet.setLocalPlayer(() -> Minecraft.getInstance().player);
    }

    /** Effect kinds (called from {@link FxHandlers#bootstrap}). */
    public static void registerFx() {
        FxHandlers.register(StarlaceSet.FX_STEP, StarlaceClient::step);
        FxHandlers.register(StarlaceSet.FX_STARFALL, StarlaceClient::starfall);
    }

    // ------------------------------------------------------------------ input

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            jumpWasDown = false;
            stepped = false;
            airTicks = 0;
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        SafeRun.run(FEATURE, () -> input(mc, player));
    }

    private static void input(Minecraft mc, LocalPlayer player) {
        boolean down = mc.options.keyJump.isDown();
        boolean edge = down && !jumpWasDown;
        jumpWasDown = down;
        if (StarlaceSet.grounded(player) || player.getAbilities().flying || player.isSpectator()) {
            airTicks = 0;
            stepped = false;
            return;
        }
        airTicks++;
        // airTicks >= 2: the press that made the ordinary jump (this tick or the last) is never taken for a second one
        if (!edge || stepped || airTicks < 2 || mc.screen != null || player.isFallFlying() || !StarlaceSet.fullSet(player)) {
            return;
        }
        stepped = true;
        Vec3 dir = inputDirection(player);
        PacketDistributor.sendToServer(new StarStepPayload((float) dir.x, (float) dir.z));
    }

    /** World-space horizontal direction of the movement keys (unit vector), or zero when none is held. */
    private static Vec3 inputDirection(LocalPlayer player) {
        float strafe = player.input.leftImpulse;
        float forward = player.input.forwardImpulse;
        if (Math.abs(strafe) + Math.abs(forward) < 1.0E-3F) {
            return Vec3.ZERO;
        }
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        float sin = Mth.sin(yaw);
        float cos = Mth.cos(yaw);
        return new Vec3(strafe * cos - forward * sin, 0.0, forward * cos + strafe * sin).normalize();
    }

    // ------------------------------------------------------------------ effects

    private static boolean isSelf(FxPayload p) {
        Player player = Minecraft.getInstance().player;
        return player != null && player.getId() == p.entityId();
    }

    private static void step(FxPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        Vec3 at = p.a();
        RandomSource r = RandomSource.create((long) p.p0());
        // a ring of stars bursting out under the feet
        int n = 16;
        for (int i = 0; i < n; i++) {
            double a = i * Mth.TWO_PI / n;
            double c = Math.cos(a);
            double s = Math.sin(a);
            level.addParticle(i % 2 == 0 ? ModParticles.LIGHT_MOTE.get() : ModParticles.SOLAR_GLINT.get(),
                    at.x + c * 0.35, at.y + 0.05, at.z + s * 0.35, c * 0.13, -0.01, s * 0.13);
        }
        for (int i = 0; i < 5; i++) {
            level.addParticle(ModParticles.LIGHT_MOTE.get(), at.x + r.nextGaussian() * 0.3, at.y - 0.1 + r.nextDouble() * 0.3,
                    at.z + r.nextGaussian() * 0.3, 0.0, -0.03 - r.nextDouble() * 0.03, 0.0);
        }
        FxManager.add(new Constellation(at, r, p.p1()));
        if (isSelf(p)) {
            ScreenFx.flash(LAVENDER, 0.05F, 5);
        }
    }

    private static void starfall(FxPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        Vec3 at = p.a();
        float radius = p.p0() > 0.0F ? p.p0() : 3.5F;
        float power = Mth.clamp((p.p1() - 5.0F) / 15.0F, 0.0F, 1.0F);
        RandomSource r = RandomSource.create((long) p.p2());
        int n = 28;
        for (int i = 0; i < n; i++) {
            double a = i * Mth.TWO_PI / n + r.nextDouble() * 0.1;
            double c = Math.cos(a);
            double s = Math.sin(a);
            double speed = 0.28 + r.nextDouble() * 0.12;
            level.addParticle(ModParticles.SOLAR_GLINT.get(), at.x + c * 0.4, at.y + 0.15, at.z + s * 0.4, c * speed, 0.02, s * speed);
        }
        for (int i = 0; i < 18; i++) {
            double a = r.nextDouble() * Mth.TWO_PI;
            double d = r.nextDouble() * radius * 0.6;
            level.addParticle(ModParticles.LIGHT_MOTE.get(), at.x + Math.cos(a) * d, at.y + 0.1, at.z + Math.sin(a) * d,
                    0.0, 0.05 + r.nextDouble() * 0.08, 0.0);
        }
        FxManager.add(new Starfall(at, radius));
        if (isSelf(p)) {
            ScreenFx.shake(1.2F + 1.3F * power, 10);
            ScreenFx.flash(GOLD, 0.1F + 0.06F * power, 7);
        }
    }

    // ------------------------------------------------------------------ the constellation left behind by a step

    private static final class Constellation implements ClientFx {
        private static final int LIFE = 12;
        private static final int TRACE = 4;
        private final List<Vec3> stars = new ArrayList<>();
        private final int[][] lines;
        private final float[] twinkle;
        private int age;

        Constellation(Vec3 feet, RandomSource r, float yaw) {
            // five stars scattered on a tilted disc around the feet, kept apart so the figure reads
            Vec3 centre = feet.add(0, 0.1, 0);
            double base = Float.isFinite(yaw) ? yaw : r.nextDouble() * Mth.TWO_PI;
            for (int i = 0; i < 5; i++) {
                Vec3 best = null;
                for (int attempt = 0; attempt < 8 && best == null; attempt++) {
                    double a = base + i * Mth.TWO_PI / 5 + (r.nextDouble() - 0.5) * 0.9;
                    double d = 0.45 + r.nextDouble() * 0.75;
                    Vec3 c = centre.add(Math.cos(a) * d, (r.nextDouble() - 0.5) * 0.35, Math.sin(a) * d);
                    boolean apart = true;
                    for (Vec3 s : stars) {
                        apart &= s.distanceToSqr(c) > 0.3 * 0.3;
                    }
                    if (apart || attempt == 7) {
                        best = c;
                    }
                }
                stars.add(best);
            }
            // a chain through the five and one branch back, like a small asterism
            lines = new int[][] {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {1, 3 + r.nextInt(2)}};
            twinkle = new float[stars.size()];
            for (int i = 0; i < twinkle.length; i++) {
                twinkle[i] = r.nextFloat() * Mth.TWO_PI;
            }
        }

        @Override
        public boolean tick() {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null && age % 4 == 0 && age < LIFE - 2) {
                Vec3 s = stars.get(level.random.nextInt(stars.size()));
                level.addParticle(ModParticles.LIGHT_MOTE.get(), s.x, s.y, s.z, 0.0, -0.01, 0.0);
            }
            return ++age < LIFE;
        }

        @Override
        public void render(FxRenderContext ctx) {
            float time = ctx.time(age);
            float fade = time < LIFE - 5 ? 1.0F : Mth.clamp((LIFE - time) / 5.0F, 0.0F, 1.0F);
            if (fade <= 0.0F) {
                return;
            }
            PoseStack.Pose pose = ctx.pose().last();
            // the lines trace out one after another during the first ticks
            VertexConsumer beam = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
            float traced = Mth.clamp(time / TRACE, 0.0F, 1.0F) * lines.length;
            for (int i = 0; i < lines.length; i++) {
                float part = Mth.clamp(traced - i, 0.0F, 1.0F);
                if (part <= 0.0F) {
                    break;
                }
                Vec3 a = ctx.rel(stars.get(lines[i][0]));
                Vec3 b = ctx.rel(stars.get(lines[i][1]));
                Vec3 end = a.add(b.subtract(a).scale(part));
                FxDraw.ribbon(pose, beam, a, end, 0.05F, FxDraw.argb(0.45F * fade, LAVENDER), 0.0F, 1.0F);
                FxDraw.ribbon(pose, beam, a, end, 0.02F, FxDraw.argb(0.5F * fade, STARLIGHT), 0.0F, 1.0F);
            }
            VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
            for (int i = 0; i < stars.size(); i++) {
                Vec3 s = ctx.rel(stars.get(i));
                float tw = 0.75F + 0.25F * Mth.sin(time * 1.3F + twinkle[i]);
                float pop = Mth.clamp(time / 2.0F, 0.0F, 1.0F);
                FxDraw.billboard(pose, glow, ctx.camera(), s, 0.22F * pop * tw, 0.0F, FxDraw.argb(0.35F * fade, LAVENDER));
                FxDraw.billboard(pose, glow, ctx.camera(), s, 0.09F * pop * tw, 0.0F, FxDraw.argb(0.95F * fade, i % 2 == 0 ? STARLIGHT : GOLD));
            }
        }
    }

    // ------------------------------------------------------------------ the Starfall shockwave

    private static final class Starfall implements ClientFx {
        private static final int LIFE = 16;
        private static final float GROW = 8.0F;
        private final Vec3 origin;
        private final float range;
        private int age;

        Starfall(Vec3 origin, float range) {
            this.origin = origin;
            this.range = range;
        }

        private float radius(float time) {
            float t = Mth.clamp(time / GROW, 0.0F, 1.0F);
            return 0.4F + (range - 0.4F) * (1.0F - (1.0F - t) * (1.0F - t));
        }

        @Override
        public boolean tick() {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null && age <= GROW) {
                // stars shed along the racing ring front
                RandomSource r = level.random;
                float rad = radius(age);
                for (int i = 0; i < 6; i++) {
                    double a = r.nextDouble() * Mth.TWO_PI;
                    level.addParticle(i % 3 == 0 ? ModParticles.SOLAR_GLINT.get() : ModParticles.LIGHT_MOTE.get(),
                            origin.x + Math.cos(a) * rad, origin.y + 0.1 + r.nextDouble() * 0.3, origin.z + Math.sin(a) * rad,
                            Math.cos(a) * 0.03, 0.03, Math.sin(a) * 0.03);
                }
            }
            return ++age < LIFE;
        }

        @Override
        public void render(FxRenderContext ctx) {
            float time = ctx.time(age);
            float fade = 1.0F - Mth.clamp((time - GROW * 0.6F) / (LIFE - GROW * 0.6F), 0.0F, 1.0F);
            if (fade <= 0.0F) {
                return;
            }
            PoseStack.Pose pose = ctx.pose().last();
            Vec3 base = ctx.rel(origin).add(0, 0.06, 0);
            VertexConsumer ring = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.RING));
            // pale gold front, a lavender ring trailing it and a faint echo further behind
            FxDraw.ground(pose, ring, base, radius(time), FxDraw.argb(0.85F * fade, GOLD));
            FxDraw.ground(pose, ring, base.add(0, 0.01, 0), radius(time - 1.2F) * 0.94F, FxDraw.argb(0.6F * fade, LAVENDER));
            FxDraw.ground(pose, ring, base.add(0, 0.02, 0), radius(time - 2.6F) * 0.85F, FxDraw.argb(0.3F * fade, STARLIGHT));
            // a soft pool of light where the player landed, brightest at the impact
            float flare = 1.0F - Mth.clamp(time / 6.0F, 0.0F, 1.0F);
            VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
            FxDraw.ground(pose, glow, base.add(0, 0.03, 0), 0.8F + range * 0.6F * (1.0F - flare), FxDraw.argb(0.3F * fade, LAVENDER));
            if (flare > 0.0F) {
                FxDraw.billboard(pose, glow, ctx.camera(), base.add(0, 0.4, 0), 0.9F + 0.6F * (1.0F - flare), 0.0F,
                        FxDraw.argb(0.55F * flare, GOLD));
            }
        }
    }
}
