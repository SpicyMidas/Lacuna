package com.lacuna.mod.client.fx;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.compat.ShaderPackCompat;
import com.lacuna.mod.util.SafeRun;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Volumetric rays for every effect: beams, bolts, pillars and trails of light as real round 3D volumes instead of
 * flat camera-facing ribbons (the technique of the solar shafts, {@code client/solar/SolarTubes}, made generic).
 * <p>
 * <b>Use</b>: during rendering (an effect's {@link ClientFx#render}, an entity renderer drawing into the level's
 * buffers) call {@link #tube} / {@link #beam} for a straight ray or {@link #chain()} for a trail through recent
 * positions, with the same camera-relative points as the ribbon. Each returns false when the ray cannot be drawn in
 * 3D: the shader did not load, a shader pack is in use ({@link ShaderPackCompat}: the pack owns the buffers and the
 * depth), the frame's cap of {@value #MAX} tubes is reached, or the renderer failed and {@link SafeRun} switched it
 * off; the caller then draws its old ribbon ({@link #beamOrRibbon} does both).
 * <p>
 * <b>Look</b> ({@code lacuna:fx_tube}): per tube a start and an end, a radius at each (tapering linearly), a rim
 * colour (body and wide halo; alpha = intensity) and a core colour (the hot centre line; alpha = its strength) at
 * each end, a softness (0 crisp rod .. 1 soft haze), v (blocks along, drives the flow) and flags: {@link #FLOW}
 * (turbulence and ripple rings travelling along, the {@link FxRenderTypes#beam()} look), {@link #FAR} (no distance
 * dimming), {@link #NEAR_FADE}. Seen from the side a tube has exactly the old ribbon's profile and brightness; seen
 * along its length it glows brighter (soft saturation); the eye inside it sees it all around.
 * <p>
 * <b>Depth</b>: the queue is drawn after the level ({@code AFTER_LEVEL}: clouds, weather and the Fabulous composite
 * done, the hand not yet) into the main target, as exposed light screened over the scene (no white bloom over a bright
 * sky), without a depth test; the shader reads the scene depth
 * ({@link FxDepth}) and integrates only the part of each ray in front of it: what stands in front hides the ray, what
 * stands behind never does, and a ray melts into the ground or wall it enters instead of being cut by a hard line.
 * <p>
 * <b>Batching</b>: tubes are queued into a float array (9 RGBA texels each, camera-relative), uploaded once per frame
 * into a small RGBA32F data texture and drawn {@value #BATCH} at a time from one static mesh of {@value #BATCH} unit
 * tubes, each placed by the vertex shader from its row of the texture: a handful of draw calls for hundreds of rays,
 * no allocation per frame. Only back faces are drawn, so every pixel of a tube's footprint is shaded exactly once.
 * <p>
 * <b>Fabulous</b>: {@link FxManager} draws its effects twice (colour, then a depth-only pass); during the second pass
 * ({@link #replay}) every call repeats the colour pass's answer without queueing anything, so a ray drawn in 3D gets no
 * ribbon in the depth pass and a ray that fell back gets its ribbon's depth.
 */
public final class FxTubes {
    public static final String FEATURE = "fx_tubes";
    /** Flag: turbulence and ripple rings flowing along the ray (the energy-beam look of {@link FxRenderTypes#beam()}). */
    public static final int FLOW = 1;
    /** Flag: not dimmed with distance (a meteor seen from far away); thick fogs still swallow it. */
    public static final int FAR = 2;
    /** Flag: fades out where it passes within about a block of the eye (a ray ending at the viewer never fills the screen). */
    public static final int NEAR_FADE = 4;
    /** Tubes per frame; further requests are refused (the caller draws its ribbon). */
    public static final int MAX = 256;
    /** Most points of one {@link Chain}. */
    public static final int MAX_CHAIN = 64;

    private static final int BATCH = 32;
    private static final int SIDES = 16;
    private static final int TEXELS = 9;
    private static final int STRIDE = TEXELS * 4;
    private static final float[] DATA = new float[MAX * STRIDE];
    /** Rows written this frame. */
    private static int count;
    /** Tubes promised this frame (a chain reserves all its segments at once); never more than {@link #MAX}. */
    private static int used;
    /** Fabulous depth pass: the promises made so far in it ({@link #replay}), or -1 outside it. */
    private static int replayUsed = -1;
    private static float fogStart = Float.MAX_VALUE;
    private static float fogEnd = 0.0F;

    @Nullable
    private static ShaderInstance shader;
    @Nullable
    private static VertexBuffer mesh;
    private static int dataTexture = -1;
    @Nullable
    private static FloatBuffer upload;
    private static final Vector3f P = new Vector3f();
    private static final Vector3f Q = new Vector3f();
    private static final Chain CHAIN = new Chain();

    private FxTubes() {}

    /** Client wiring: the shader (mod bus), the frame's passes and logout (game bus). */
    public static void register(IEventBus modBus, IEventBus forgeBus) {
        modBus.addListener(FxTubes::onRegisterShaders);
        forgeBus.addListener(FxTubes::onRenderLevel);
        forgeBus.addListener(FxTubes::onLogout);
    }

    private static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Lacuna.id("fx_tube"), DefaultVertexFormat.POSITION),
                    s -> shader = s);
        } catch (IOException | RuntimeException e) {
            shader = null;
            Lacuna.LOGGER.warn("Lacuna fx_tube shader unavailable, beams and trails stay flat ribbons: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------ queueing

    /** True when rays can be drawn in 3D now (shader loaded, no shader pack, not switched off). */
    public static boolean available() {
        return ShaderPackCompat.usable(shader) && !SafeRun.isDisabled(FEATURE);
    }

    /** Promises {@code n} tubes this frame (or in the depth pass, repeats the promise); false when they do not fit. */
    private static boolean reserve(int n) {
        if (!available()) {
            return false;
        }
        if (replayUsed >= 0) {
            if (replayUsed + n > MAX) {
                return false;
            }
            replayUsed += n;
            return true;
        }
        if (used + n > MAX) {
            return false;
        }
        used += n;
        return true;
    }

    /** The colour pass's position in the frame's promises, for {@link #replay} (called by {@link FxManager}). */
    static int mark() {
        return used;
    }

    /**
     * Starts ({@code from >= 0}: the {@link #mark} taken before the colour pass) or ends ({@code -1}) the Fabulous
     * depth pass, during which requests are answered as in the colour pass and nothing is queued.
     */
    static void replay(int from) {
        replayUsed = from;
    }

    /**
     * A straight ray from {@code a} to {@code b} (pose space, camera-relative after the pose), radius {@code radiusA}
     * at {@code a} tapering to {@code radiusB} at {@code b}; rim (body and halo) colours ARGB with alpha = intensity,
     * core colours ARGB with alpha = strength of the hot centre line (0: none); v in blocks along (the flow);
     * softness 0 (crisp) .. 1 (soft haze); flags {@link #FLOW}, {@link #FAR}, {@link #NEAR_FADE}. False when it cannot
     * be drawn in 3D: draw the ribbon instead.
     */
    public static boolean tube(PoseStack.Pose pose, Vec3 a, Vec3 b, float radiusA, float radiusB, int rimA, int rimB, int coreA,
                               int coreB, float vA, float vB, float softness, int flags) {
        if (!reserve(1)) {
            return false;
        }
        if (replayUsed >= 0) {
            return true;
        }
        Matrix4f m = pose.pose();
        m.transformPosition((float) a.x, (float) a.y, (float) a.z, P);
        m.transformPosition((float) b.x, (float) b.y, (float) b.z, Q);
        float dx = Q.x - P.x;
        float dy = Q.y - P.y;
        float dz = Q.z - P.z;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1.0E-4F) {
            float scale = scale(m);
            dx /= len;
            dy /= len;
            dz /= len;
            put(P.x, P.y, P.z, radiusA * scale, Q.x, Q.y, Q.z, radiusB * scale, rimA, rimB, coreA, coreB, dx, dy, dz, dx, dy, dz, vA, vB,
                    softness, flags);
        }
        return true;
    }

    /**
     * An energy beam in the {@link FxRenderTypes#beam()} style, taking the ribbon's arguments: full widths at each
     * end, tint ARGB (alpha = intensity), v from {@code vA} to {@code vB}. Its core is the tint run hot toward white.
     */
    public static boolean beam(PoseStack.Pose pose, Vec3 a, Vec3 b, float widthA, float widthB, int argbA, int argbB, float vA, float vB,
                               int flags) {
        return tube(pose, a, b, widthA * 0.5F, widthB * 0.5F, argbA, argbB, hot(argbA), hot(argbB), vA, vB, 0.2F, flags | FLOW);
    }

    /**
     * {@link #beam} when it can be drawn in 3D, else the old flat ribbon ({@link FxDraw#ribbon}) into {@code fallback}
     * (the render type the effect used to draw it with).
     */
    public static void beamOrRibbon(PoseStack.Pose pose, MultiBufferSource buffers, RenderType fallback, Vec3 a, Vec3 b, float widthA,
                                    float widthB, int argbA, int argbB, float vA, float vB, int flags) {
        if (!beam(pose, a, b, widthA, widthB, argbA, argbB, vA, vB, flags)) {
            FxDraw.ribbon(pose, buffers.getBuffer(fallback), a, b, widthA, widthB, argbA, argbB, vA, vB);
        }
    }

    /** The beam core of a tint: run hot toward white (as {@code beam.fsh}), full strength. */
    public static int hot(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r += (int) ((255 - r) * 0.75F);
        g += (int) ((255 - g) * 0.75F);
        b += (int) ((255 - b) * 0.75F);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** The shared trail builder (render thread only); see {@link Chain}. */
    public static Chain chain() {
        return CHAIN;
    }

    /** Uniform scale of a pose (entity renderers may scale; effects never do). */
    private static float scale(Matrix4f m) {
        float sx = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01() + m.m02() * m.m02());
        return sx > 1.0E-6F ? sx : 1.0F;
    }

    private static void put(float ax, float ay, float az, float ra, float bx, float by, float bz, float rb, int rimA, int rimB, int coreA,
                            int coreB, float nax, float nay, float naz, float nbx, float nby, float nbz, float vA, float vB, float softness,
                            int flags) {
        if (count >= MAX || Math.max(rimA >>> 24, rimB >>> 24) == 0 || Math.max(ra, rb) <= 1.0E-4F) {
            return;
        }
        int o = count * STRIDE;
        float[] d = DATA;
        d[o] = ax;
        d[o + 1] = ay;
        d[o + 2] = az;
        d[o + 3] = Math.max(ra, 1.0E-3F);
        d[o + 4] = bx;
        d[o + 5] = by;
        d[o + 6] = bz;
        d[o + 7] = Math.max(rb, 1.0E-3F);
        colour(o + 8, rimA);
        colour(o + 12, rimB);
        colour(o + 16, coreA);
        colour(o + 20, coreB);
        d[o + 24] = nax;
        d[o + 25] = nay;
        d[o + 26] = naz;
        d[o + 27] = vA;
        d[o + 28] = nbx;
        d[o + 29] = nby;
        d[o + 30] = nbz;
        d[o + 31] = vB;
        d[o + 32] = Math.max(0.0F, Math.min(1.0F, softness));
        d[o + 33] = flags;
        d[o + 34] = 1.0F;
        d[o + 35] = 0.0F;
        count++;
    }

    private static void colour(int o, int argb) {
        DATA[o] = ((argb >> 16) & 0xFF) / 255.0F;
        DATA[o + 1] = ((argb >> 8) & 0xFF) / 255.0F;
        DATA[o + 2] = (argb & 0xFF) / 255.0F;
        DATA[o + 3] = (argb >>> 24) / 255.0F;
    }

    /**
     * A trail of light through a list of points (newest first or last, either way), one tube per segment joined by
     * cuts along the bisecting planes, so the trail bends without seams, gaps or overlaps. Usage:
     * {@code if (chain.begin(pose, n, softness, flags)) { chain.point(...) x n; chain.end(); } else { ribbons }}. The
     * whole trail is accepted or refused at {@link #begin}. Fixed arrays, no allocation.
     */
    public static final class Chain {
        private final float[] x = new float[MAX_CHAIN];
        private final float[] y = new float[MAX_CHAIN];
        private final float[] z = new float[MAX_CHAIN];
        private final float[] radius = new float[MAX_CHAIN];
        private final float[] v = new float[MAX_CHAIN];
        private final int[] rim = new int[MAX_CHAIN];
        private final int[] core = new int[MAX_CHAIN];
        @Nullable
        private PoseStack.Pose pose;
        private int limit;
        private int n;
        private float softness;
        private int flags;
        private boolean open;

        private Chain() {}

        /**
         * Starts a trail of at most {@code points} points; false when it cannot be drawn in 3D (draw the ribbons). The
         * look: softness 0 (crisp) .. 1 (soft haze), flags {@link #FLOW}, {@link #FAR}, {@link #NEAR_FADE}.
         */
        public boolean begin(PoseStack.Pose pose, int points, float softness, int flags) {
            open = false;
            n = 0;
            if (points < 2 || points > MAX_CHAIN || !reserve(points - 1)) {
                return false;
            }
            open = replayUsed < 0;
            this.pose = pose;
            this.limit = points;
            this.softness = softness;
            this.flags = flags;
            return true;
        }

        /** The next point: radius there, rim colour ARGB (alpha = intensity), core ARGB (alpha = strength), v in blocks. */
        public void point(Vec3 p, float radius, int rim, int core, float v) {
            if (!open || n >= limit || pose == null) {
                return;
            }
            Matrix4f m = pose.pose();
            m.transformPosition((float) p.x, (float) p.y, (float) p.z, P);
            if (n > 0) {
                float dx = P.x - x[n - 1];
                float dy = P.y - y[n - 1];
                float dz = P.z - z[n - 1];
                if (dx * dx + dy * dy + dz * dz < 1.0E-8F) {
                    return; // a repeated point adds no segment
                }
            }
            float s = scale(m);
            x[n] = P.x;
            y[n] = P.y;
            z[n] = P.z;
            this.radius[n] = radius * s;
            this.rim[n] = rim;
            this.core[n] = core;
            this.v[n] = v;
            n++;
        }

        /** Queues the segments. */
        public void end() {
            if (!open) {
                return;
            }
            open = false;
            pose = null;
            for (int i = 0; i + 1 < n; i++) {
                float dx = x[i + 1] - x[i];
                float dy = y[i + 1] - y[i];
                float dz = z[i + 1] - z[i];
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= len;
                dy /= len;
                dz /= len;
                // the cut at each end: the bisecting plane with the neighbouring segment (square across a sharp turn)
                float[] na = cut(i - 1, i, dx, dy, dz);
                float nax = na[0];
                float nay = na[1];
                float naz = na[2];
                float[] nb = cut(i + 1, i, dx, dy, dz);
                put(x[i], y[i], z[i], radius[i], x[i + 1], y[i + 1], z[i + 1], radius[i + 1], rim[i], rim[i + 1], core[i], core[i + 1],
                        nax, nay, naz, nb[0], nb[1], nb[2], v[i], v[i + 1], softness, flags);
            }
            n = 0;
        }

        private final float[] cutA = new float[3];
        private final float[] cutB = new float[3];

        /**
         * The normal of the cut between segment {@code seg} (direction d) and its neighbour {@code other} (the segment
         * before or after it): the bisector of both directions, or d itself at the trail's ends and across turns
         * sharper than about 100 degrees.
         */
        private float[] cut(int other, int seg, float dx, float dy, float dz) {
            float[] out = other < seg ? cutA : cutB;
            out[0] = dx;
            out[1] = dy;
            out[2] = dz;
            if (other < 0 || other + 1 >= n) {
                return out;
            }
            float ox = x[other + 1] - x[other];
            float oy = y[other + 1] - y[other];
            float oz = z[other + 1] - z[other];
            float ol = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
            if (ol < 1.0E-5F) {
                return out;
            }
            ox /= ol;
            oy /= ol;
            oz /= ol;
            if (ox * dx + oy * dy + oz * dz < -0.2F) {
                return out;
            }
            float sx = ox + dx;
            float sy = oy + dy;
            float sz = oz + dz;
            float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl > 1.0E-4F) {
                out[0] = sx / sl;
                out[1] = sy / sl;
                out[2] = sz / sl;
            }
            return out;
        }
    }

    // ------------------------------------------------------------------ the frame

    private static void onRenderLevel(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            // a new frame (before entities and effects queue): drop whatever an unfinished frame left behind
            clear();
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            // the terrain fog (vanilla resets it before AFTER_LEVEL)
            fogStart = RenderSystem.getShaderFogStart();
            fogEnd = RenderSystem.getShaderFogEnd();
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            if (count > 0) {
                SafeRun.run(FEATURE, () -> draw(event), FxTubes::clear);
            }
            clear();
        }
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        count = 0;
        used = 0;
        replayUsed = -1;
        CHAIN.open = false;
        CHAIN.pose = null;
    }

    /** {@code AFTER_LEVEL}: draws the queued tubes against the finished scene. */
    private static void draw(RenderLevelStageEvent event) {
        ShaderInstance s = shader;
        Minecraft mc = Minecraft.getInstance();
        if (s == null || !ShaderPackCompat.usable(s) || mc.level == null || !FxDepth.prepare(mc)) {
            return;
        }
        int texture = dataTexture();
        VertexBuffer tubes = mesh();
        FxDepth.bind(s);
        s.setSampler("TubeData", texture);
        Matrix4f modelView = event.getModelViewMatrix();
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        float savedStart = RenderSystem.getShaderFogStart();
        float savedEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        mc.getMainRenderTarget().bindWrite(false);
        RenderSystem.enableBlend();
        // screened light (fx_tube.fsh): scene x (1 - light) + light; the target's alpha is kept
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.enableCull();
        GL11.glCullFace(GL11.GL_FRONT);
        tubes.bind();
        try {
            for (int base = 0; base < count; base += BATCH) {
                s.safeGetUniform("TubeBase").set(base);
                s.safeGetUniform("TubeCount").set(Math.min(BATCH, count - base));
                tubes.drawWithShader(modelView, projection, s);
            }
        } finally {
            VertexBuffer.unbind();
            GL11.glCullFace(GL11.GL_BACK);
            RenderSystem.setShaderFogStart(savedStart);
            RenderSystem.setShaderFogEnd(savedEnd);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    /** The data texture (9 RGBA32F texels per tube, one row each), with this frame's rows uploaded. */
    private static int dataTexture() {
        if (dataTexture < 0) {
            dataTexture = TextureUtil.generateTextureId();
            GlStateManager._bindTexture(dataTexture);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAX_LEVEL, 0);
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, TEXELS, MAX, 0, GL11.GL_RGBA, GL11.GL_FLOAT, null);
            upload = BufferUtils.createFloatBuffer(MAX * STRIDE);
        }
        FloatBuffer buffer = upload;
        buffer.clear();
        buffer.put(DATA, 0, count * STRIDE);
        buffer.flip();
        GlStateManager._bindTexture(dataTexture);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, TEXELS, count, GL11.GL_RGBA, GL11.GL_FLOAT, buffer);
        return dataTexture;
    }

    /**
     * {@value #BATCH} closed unit tubes, built once: x, y on the unit circle (0, 0 at a cap's centre), z = slot * 8 +
     * ring (0..3); outward faces wind counter-clockwise (side quads: around the axis then along it; caps facing out of
     * each end).
     */
    private static VertexBuffer mesh() {
        if (mesh != null && !mesh.isInvalid()) {
            return mesh;
        }
        float[] cos = new float[SIDES + 1];
        float[] sin = new float[SIDES + 1];
        for (int i = 0; i <= SIDES; i++) {
            double a = Math.PI * 2.0 * (i % SIDES) / SIDES;
            cos[i] = (float) Math.cos(a);
            sin[i] = (float) Math.sin(a);
        }
        int perTube = SIDES * 3 * 6 + SIDES * 6;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(BATCH * perTube * 12)) {
            BufferBuilder b = new BufferBuilder(bytes, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
            for (int slot = 0; slot < BATCH; slot++) {
                float base = slot * 8.0F;
                for (int ring = 0; ring < 3; ring++) {
                    float z0 = base + ring;
                    float z1 = base + ring + 1;
                    for (int i = 0; i < SIDES; i++) {
                        b.addVertex(cos[i], sin[i], z0);
                        b.addVertex(cos[i + 1], sin[i + 1], z0);
                        b.addVertex(cos[i + 1], sin[i + 1], z1);
                        b.addVertex(cos[i], sin[i], z0);
                        b.addVertex(cos[i + 1], sin[i + 1], z1);
                        b.addVertex(cos[i], sin[i], z1);
                    }
                }
                float end = base + 3.0F;
                for (int i = 0; i < SIDES; i++) {
                    b.addVertex(0.0F, 0.0F, end);
                    b.addVertex(cos[i], sin[i], end);
                    b.addVertex(cos[i + 1], sin[i + 1], end);
                    b.addVertex(0.0F, 0.0F, base);
                    b.addVertex(cos[i + 1], sin[i + 1], base);
                    b.addVertex(cos[i], sin[i], base);
                }
            }
            MeshData data = b.buildOrThrow();
            VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vbo.bind();
            vbo.upload(data);
            VertexBuffer.unbind();
            mesh = vbo;
        }
        return mesh;
    }
}
