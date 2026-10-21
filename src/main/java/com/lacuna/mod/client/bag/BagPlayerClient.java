package com.lacuna.mod.client.bag;

import com.lacuna.mod.Balance;
import com.lacuna.mod.Lacuna;
import com.lacuna.mod.bag.BagPlayerNet;
import com.lacuna.mod.client.MuffledSound;
import com.lacuna.mod.util.SafeRun;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The view from inside the Comically Large Bag ({@code bag/BaggedPlayers}). Every client hides bagged players
 * (model, armour, held items, name) and shrinks them to the same speck as the server. The bagged player's own client:
 * <ul>
 *   <li>Watches through a client-only camera {@link Marker} hung at the bagger's right hip, turned with the bagger's
 *   view and swaying and bobbing with their stride; the bagger is not drawn for them (they are inside the sack).
 *   First person is forced while bagged and the previous camera mode comes back after.</li>
 *   <li>Sees the inside of a burlap sack over everything, drawn like the vanilla pumpkin blur: dark weave, the
 *   drawstring opening letting the light in at the top and a few holes. It rises around them when they are bagged,
 *   jiggles when they struggle and slides off when they get out.</li>
 *   <li>Has no hands, crosshair, block overlay or clicks; jump, attack and use presses are struggles sent to the
 *   server instead. World sounds come through muffled.</li>
 * </ul>
 */
public final class BagPlayerClient {
    public static final String FEATURE = "bag_player_view";
    private static final ResourceLocation INSIDE = Lacuna.id("textures/gui/bag_inside.png");
    private static final int TEX_W = 256;
    private static final int TEX_H = 144;
    private static final EntityDimensions SPECK = EntityDimensions.fixed(0.01F, 0.01F);
    private static final float MUFFLE = 0.55F;

    /** Bagged player → their bagger, as the server last said. */
    private static final Map<UUID, UUID> BAGGED = new ConcurrentHashMap<>();
    @Nullable
    private static Marker eye;
    @Nullable
    private static CameraType savedCamera;
    private static boolean inBag;
    private static float fade;
    private static float fadeO;
    private static float shake;
    private static float roll;
    private static float swayX;
    private static float swayY;
    private static float swayRoll;
    private static boolean jumpDown;
    private static boolean attackDown;
    private static boolean useDown;
    private static int struggleCooldown;

    private BagPlayerClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(BagPlayerClient::onRegisterLayers);
        NeoForge.EVENT_BUS.addListener(BagPlayerClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(BagPlayerClient::onRenderFrame);
        NeoForge.EVENT_BUS.addListener(BagPlayerClient::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(BagPlayerClient::onRenderPlayer);
        NeoForge.EVENT_BUS.addListener(BagPlayerClient::onRenderHand);
        NeoForge.EVENT_BUS.addListener(BagPlayerClient::onGuiLayer);
        NeoForge.EVENT_BUS.addListener(BagPlayerClient::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(BagPlayerClient::onPlaySound);
        NeoForge.EVENT_BUS.addListener(BagPlayerClient::onBlockOverlay);
        NeoForge.EVENT_BUS.addListener(BagPlayerClient::onSize);
        NeoForge.EVENT_BUS.addListener(BagPlayerClient::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(BagPlayerClient::onLogout);
    }

    // ================================================================================================ state

    public static void onState(BagPlayerNet.State state) {
        SafeRun.run(FEATURE, () -> {
            if (state.active()) {
                BAGGED.put(state.target(), state.bagger());
            } else {
                BAGGED.remove(state.target());
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            Player player = mc.level.getPlayerByUUID(state.target());
            if (player != null) {
                player.refreshDimensions();
            }
            if (mc.player != null && state.target().equals(mc.player.getUUID())) {
                shake = 1.0F;
                if (!state.active()) {
                    restoreCamera(mc);
                }
            }
        });
    }

    private static boolean localBagged(Minecraft mc) {
        return mc.player != null && BAGGED.containsKey(mc.player.getUUID());
    }

    @Nullable
    private static Player bagger(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return null;
        }
        if (mc.player.getVehicle() instanceof Player vehicle) {
            return vehicle;
        }
        UUID id = BAGGED.get(mc.player.getUUID());
        return id == null ? null : mc.level.getPlayerByUUID(id);
    }

    private static void restoreCamera(Minecraft mc) {
        if (eye != null && mc.getCameraEntity() == eye && mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
        eye = null;
        if (savedCamera != null) {
            mc.options.setCameraType(savedCamera);
            savedCamera = null;
        }
        inBag = false;
    }

    // ================================================================================================ tick: struggle

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean bagged = localBagged(mc);
        fadeO = fade;
        fade = bagged ? Math.min(1.0F, fade + 0.2F) : Math.max(0.0F, fade - 0.125F);
        shake *= 0.75F;
        if (struggleCooldown > 0) {
            struggleCooldown--;
        }
        if (!bagged) {
            if (inBag || eye != null) {
                restoreCamera(mc);
            }
            jumpDown = attackDown = useDown = false;
            return;
        }
        if (!inBag) {
            inBag = true;
            savedCamera = mc.options.getCameraType();
        }
        if (!mc.options.getCameraType().isFirstPerson()) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        boolean jump = mc.options.keyJump.isDown();
        boolean attack = mc.options.keyAttack.isDown();
        boolean use = mc.options.keyUse.isDown();
        if (mc.screen == null && !mc.isPaused() && (jump && !jumpDown || attack && !attackDown || use && !useDown)) {
            struggle(mc);
        }
        jumpDown = jump;
        attackDown = attack;
        useDown = use;
    }

    private static void struggle(Minecraft mc) {
        if (struggleCooldown > 0) {
            return;
        }
        struggleCooldown = Balance.BAG_STRUGGLE_INTERVAL;
        shake = 1.0F;
        PacketDistributor.sendToServer(BagPlayerNet.Struggle.INSTANCE);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOOL_STEP, 0.8F + mc.level.random.nextFloat() * 0.3F, 0.6F));
    }

    // ================================================================================================ camera

    private static void onRenderFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !localBagged(mc)) {
            return;
        }
        SafeRun.run(FEATURE, () -> {
            Player bagger = bagger(mc);
            if (bagger == null) {
                if (eye != null && mc.getCameraEntity() == eye) {
                    mc.setCameraEntity(mc.player);
                }
                return;
            }
            if (eye == null || eye.level() != mc.level) {
                eye = new Marker(EntityType.MARKER, mc.level);
            }
            hang(eye, bagger, event.getPartialTick().getGameTimeDeltaPartialTick(true), mc.level,
                    mc.options.screenEffectScale().get().floatValue());
            if (mc.getCameraEntity() != eye) {
                mc.setCameraEntity(eye);
            }
        }, () -> restoreCamera(mc));
    }

    /** Puts the camera at the bagger's right hip, looking where they look, swaying with their stride. */
    private static void hang(Marker camera, Player bagger, float partial, ClientLevel level, float distortion) {
        double x = Mth.lerp(partial, bagger.xo, bagger.getX());
        double y = Mth.lerp(partial, bagger.yo, bagger.getY());
        double z = Mth.lerp(partial, bagger.zo, bagger.getZ());
        float body = Mth.rotLerp(partial, bagger.yBodyRotO, bagger.yBodyRot) * Mth.DEG_TO_RAD;
        float walkPos = bagger.walkAnimation.position(partial);
        float walkSpeed = Math.min(1.0F, bagger.walkAnimation.speed(partial));
        float time = bagger.tickCount + partial;
        float stride = Mth.sin(walkPos * 0.6662F) * walkSpeed;
        float bob = Mth.abs(Mth.cos(walkPos * 0.6662F)) * walkSpeed;
        float sway = distortion;

        Vec3 center = new Vec3(x, y + bagger.getBbHeight() * 0.5 - bob * 0.07 * sway, z);
        Vec3 right = new Vec3(-Mth.cos(body), 0.0, -Mth.sin(body));
        Vec3 forward = new Vec3(-Mth.sin(body), 0.0, Mth.cos(body));
        Vec3 want = center.add(right.scale(0.42)).add(forward.scale(0.06 + stride * 0.05 * sway));
        // never through a wall the bagger is hugging
        BlockHitResult hit = level.clip(new ClipContext(center, want, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, bagger));
        Vec3 at = hit.getType() == HitResult.Type.MISS ? want : center.add(hit.getLocation().subtract(center).scale(0.6));
        camera.setPos(at.x, at.y, at.z);
        camera.xo = at.x;
        camera.yo = at.y;
        camera.zo = at.z;

        float yaw = bagger.getViewYRot(partial) + (stride * 2.5F + Mth.sin(time * 0.045F) * 1.2F) * sway;
        float pitch = Mth.clamp(bagger.getViewXRot(partial) * 0.6F + (bob * 3.0F + Mth.sin(time * 0.061F) * 0.8F) * sway, -70.0F, 80.0F);
        camera.setYRot(yaw);
        camera.yRotO = yaw;
        camera.setXRot(pitch);
        camera.xRotO = pitch;
        roll = (stride * 6.0F + Mth.sin(time * 0.037F) * 1.5F + shake * Mth.sin(time * 2.9F) * 3.0F) * sway;
        swayX = (stride * 6.0F + shake * Mth.sin(time * 3.7F) * 5.0F) * sway;
        swayY = (bob * 4.0F + shake * Mth.cos(time * 3.1F) * 4.0F) * sway;
        swayRoll = (-stride * 1.2F + shake * Mth.sin(time * 4.3F) * 0.8F) * sway;
    }

    private static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (eye != null && event.getCamera().getEntity() == eye) {
            event.setRoll(event.getRoll() + roll);
        }
    }

    // ================================================================================================ hidden things

    private static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (BAGGED.isEmpty()) {
            return;
        }
        UUID id = event.getEntity().getUUID();
        Minecraft mc = Minecraft.getInstance();
        if (BAGGED.containsKey(id) || mc.player != null && id.equals(BAGGED.get(mc.player.getUUID()))) {
            event.setCanceled(true); // the bagged are in the bag; from inside it, the bagger is all around you
        }
    }

    private static void onRenderHand(RenderHandEvent event) {
        if (localBagged(Minecraft.getInstance())) {
            event.setCanceled(true);
        }
    }

    private static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (VanillaGuiLayers.CROSSHAIR.equals(event.getName()) && localBagged(Minecraft.getInstance())) {
            event.setCanceled(true);
        }
    }

    private static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (localBagged(Minecraft.getInstance())) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private static void onBlockOverlay(RenderBlockScreenEffectEvent event) {
        if (localBagged(Minecraft.getInstance())) {
            event.setCanceled(true);
        }
    }

    private static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound.isRelative() || sound instanceof TickableSoundInstance || !localBagged(Minecraft.getInstance())) {
            return;
        }
        event.setSound(new MuffledSound(sound, MUFFLE));
    }

    private static void onSize(EntityEvent.Size event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player && !BAGGED.isEmpty() && entity.level() != null && entity.level().isClientSide()
                && BAGGED.containsKey(entity.getUUID())) {
            event.setNewSize(SPECK);
        }
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof Player player && BAGGED.containsKey(player.getUUID())) {
            player.refreshDimensions();
        }
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BAGGED.clear();
        eye = null;
        if (savedCamera != null) {
            Minecraft.getInstance().options.setCameraType(savedCamera);
            savedCamera = null;
        }
        inBag = false;
        fade = fadeO = shake = 0.0F;
    }

    // ================================================================================================ the inside of the sack

    private static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, Lacuna.id("bag_inside"), BagPlayerClient::renderInside);
    }

    private static void renderInside(GuiGraphics g, DeltaTracker delta) {
        float a = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), fadeO, fade);
        if (a <= 0.003F) {
            return;
        }
        SafeRun.run(FEATURE + "_overlay", () -> {
            float ease = a * a * (3.0F - 2.0F * a);
            int w = g.guiWidth();
            int h = g.guiHeight();
            PoseStack pose = g.pose();
            pose.pushPose();
            // the sack rises up around you as you go in, and slides off as you get out
            pose.translate(w / 2.0F + swayX * ease, h / 2.0F + swayY * ease + (1.0F - ease) * h * 0.55F, 0.0F);
            pose.mulPose(Axis.ZP.rotationDegrees(swayRoll * ease));
            float scale = 1.12F + (1.0F - ease) * 0.35F;
            pose.scale(scale, scale, 1.0F);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.setColor(1.0F, 1.0F, 1.0F, Math.min(1.0F, ease * 1.25F));
            g.blit(INSIDE, -w / 2, -h / 2, w, h, 0.0F, 0.0F, TEX_W, TEX_H, TEX_W, TEX_H);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            pose.popPose();
        });
    }
}
