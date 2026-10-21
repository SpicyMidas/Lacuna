package com.lacuna.mod.client.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.Lacuna;
import com.lacuna.mod.gear.AnimatedItemEntity;
import com.lacuna.mod.gear.PyrrhicScepterItem;
import com.lacuna.mod.gear.QuickeningWandItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.Util;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FoodOnAStickItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Animated Item: the item's own model standing on two little twig legs (thigh, shin, knee bud and foot, with a
 * glowing green vein). Flat items face the camera Paper-style and flip smoothly toward the way they walk; block
 * items turn with their head. The walk cycle comes from the vanilla walk animation: legs swing opposite with the
 * swinging knee lifting, the body rides on the planted leg (so it bobs), rocks side to side and leans in. Leaps
 * crouch, stretch and land with a squash; attacks chop forward (bows recoil); TNT swells and flashes; before a special
 * power (gear/AnimatedAbilities) the item rears up on its legs and glows. The legs sprout over the first ticks of life
 * and shrink away over the last.
 */
public class AnimatedItemRenderer extends EntityRenderer<AnimatedItemEntity> {
    public static final ModelLayerLocation LEGS = new ModelLayerLocation(Lacuna.id("animated_item"), "legs");
    private static final ResourceLocation TEXTURE = Lacuna.id("textures/entity/animated_item_legs.png");
    private static final ResourceLocation GLOW = Lacuna.id("textures/entity/animated_item_legs_glow.png");
    /** Leg segment lengths in model pixels. */
    private static final float THIGH = 3.0F;
    private static final float SHIN = 3.0F;
    private static final float FOOT = 1.0F;
    private static final float LEG_SCALE = 0.9F;
    private static final float FLAT_SCALE = 0.75F;
    private static final float UPRIGHT_SCALE = 0.62F;
    private static final float BLOCK_SCALE = 1.1F;
    private static final float CUSTOM_SCALE = 0.9F;
    /** Bottom-centre of the item in FIXED display space (a flat sprite spans one block, a block model half). */
    private static final float[] BLOCK_ANCHOR = {0.0F, -0.25F};
    private static final float[] CUSTOM_ANCHOR = {0.0F, -0.3F};
    private static final float[] FLAT_ANCHOR = {0.0F, -0.45F};

    private final ItemRenderer itemRenderer;
    private final ModelPart legs;
    private final ModelPart leftThigh;
    private final ModelPart leftShin;
    private final ModelPart leftFoot;
    private final ModelPart rightThigh;
    private final ModelPart rightShin;
    private final ModelPart rightFoot;
    private final Map<TextureAtlasSprite, float[]> anchors = new WeakHashMap<>();
    private final Map<TextureAtlasSprite, float[]> uprightAnchors = new WeakHashMap<>();

    public AnimatedItemRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.3F;
        this.itemRenderer = context.getItemRenderer();
        this.legs = context.bakeLayer(LEGS);
        this.leftThigh = legs.getChild("left_thigh");
        this.leftShin = leftThigh.getChild("shin");
        this.leftFoot = leftShin.getChild("foot");
        this.rightThigh = legs.getChild("right_thigh");
        this.rightShin = rightThigh.getChild("shin");
        this.rightFoot = rightShin.getChild("foot");
    }

    /** Mod bus (client). */
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LEGS, AnimatedItemRenderer::createLegs);
    }

    /**
     * Two legs built y-up around the hip (the root): +Z is forward, +X the creature's left. Each is a thigh, a shin
     * with a mossy knee bud, and a little foot with its toes forward. UV nets match tools/art/m14_quickening.py.
     */
    public static LayerDefinition createLegs() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        for (String side : new String[]{"left", "right"}) {
            float x = side.equals("left") ? 1.5F : -1.5F;
            PartDefinition thigh = root.addOrReplaceChild(side + "_thigh",
                    CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -THIGH, -0.5F, 1.0F, THIGH, 1.0F), PartPose.offset(x, 0.0F, 0.0F));
            PartDefinition shin = thigh.addOrReplaceChild("shin",
                    CubeListBuilder.create().texOffs(4, 0).addBox(-0.5F, -SHIN, -0.5F, 1.0F, SHIN, 1.0F), PartPose.offset(0.0F, -THIGH, 0.0F));
            shin.addOrReplaceChild("knee", CubeListBuilder.create().texOffs(8, 0)
                    .addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.2F)), PartPose.ZERO);
            shin.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(0, 4).addBox(-1.0F, -FOOT, -1.0F, 2.0F, FOOT, 3.0F),
                    PartPose.offset(0.0F, -SHIN, 0.0F));
        }
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public ResourceLocation getTextureLocation(AnimatedItemEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(AnimatedItemEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        ItemStack stack = e.getMainHandItem();
        float age = e.tickCount + pt;

        // life: the legs sprout with a little overshoot when it wakes, and shrink away before it sleeps
        float remaining = Math.max(0.0F, e.clientLife - pt);
        float wake = Mth.clamp((Balance.ANIMATED_LIFETIME - remaining) / Balance.ANIMATED_WAKE_TICKS, 0.0F, 1.0F);
        float sleep = Mth.clamp(remaining / Balance.ANIMATED_SLEEP_TICKS, 0.0F, 1.0F);
        float grow = Math.min(easeOutBack(wake), smooth(sleep));
        float itemGrow = 0.6F + 0.4F * Mth.clamp(grow, 0.0F, 1.1F);

        // gait
        float walkSpeed = Math.min(1.0F, e.walkAnimation.speed(pt));
        float air = Mth.clamp(Mth.lerp(pt, e.clientAirO, e.clientAir), 0.0F, 1.0F);
        float fuse = e.clientFuseTicks > 0 ? Math.min(1.0F, (e.clientFuseTicks + pt) / Balance.ANIMATED_TNT_FUSE) : 0.0F;
        float phase = e.walkAnimation.position(pt) * 0.9F;
        float amp = walkSpeed * (1.0F - air);
        if (fuse > 0.0F) {
            // a nervous tap dance while the fuse hisses
            phase = age * 1.4F;
            amp = Math.max(amp, 0.5F);
        }
        float idle = (1.0F - walkSpeed) * (1.0F - air) * (fuse > 0.0F ? 0.0F : 1.0F);
        float breathe = Mth.sin(age * 0.16F);
        float land = e.clientLandTicks > 0 ? Mth.clamp((e.clientLandTicks - pt) / 6.0F, 0.0F, 1.0F) : 0.0F;
        float crouch = e.clientCrouchTicks > 0
                ? 1.0F - Mth.clamp((e.clientCrouchTicks - pt) / (Balance.ANIMATED_LEAP_WINDUP + 1.0F), 0.0F, 1.0F) : 0.0F;
        float bend = Math.max(land * land, crouch);

        // legs swing opposite, the swinging knee lifts its foot, both tuck up in the air and bend to land or leap
        float swing = Mth.sin(phase) * 0.8F * amp;
        float idleKnee = (0.5F - 0.5F * breathe) * 0.18F * idle;
        float hipBend = -0.55F * bend - 0.6F * air - 0.5F * idleKnee;
        float kneeBend = 1.1F * bend + 1.0F * air + idleKnee;
        float lh = swing + hipBend;
        float lk = Math.max(0.0F, -Mth.cos(phase)) * 1.1F * amp + kneeBend;
        float rh = -swing + hipBend;
        float rk = Math.max(0.0F, Mth.cos(phase)) * 1.1F * amp + kneeBend;
        float standing = THIGH + SHIN + FOOT;
        float planted = Math.max(THIGH * Mth.cos(lh) + SHIN * Mth.cos(lh + lk), THIGH * Mth.cos(rh) + SHIN * Mth.cos(rh + rk)) + FOOT;
        float legScale = LEG_SCALE * Math.max(0.0F, grow);
        // the body rides on the planted leg (a natural bob); in the air it keeps its height with the legs tucked
        float hip = Mth.lerp(air, planted, standing) / 16.0F * legScale;

        // body motion
        float waddle = Mth.sin(phase) * 7.0F * amp;
        float lean = 7.0F * amp;
        float attack = e.getAttackAnim(pt);
        float strike = attack > 0.0F ? Mth.sin(Mth.sqrt(attack) * Mth.PI) : 0.0F;
        float lunge = Mth.sin(attack * Mth.PI) * 0.12F;
        float chop = strike * swingAngle(e.kind());
        float vy = Mth.lerp(pt, e.clientVyO, e.clientVy);
        // stretched by speed through the air, squashed on landing and while crouching to leap, breathing when idle
        float sy = 1.0F + Mth.clamp(Math.abs(vy) * 1.4F, 0.0F, 0.2F) * air - 0.25F * land * land - 0.18F * crouch + 0.025F * breathe * idle;
        float sxz = 1.0F / Mth.sqrt(Math.max(0.4F, sy));
        float swell = 1.0F + fuse * 0.15F + Mth.sin(age * 2.2F) * 0.03F * fuse;
        float white = fuse > 0.0F && (((int) (age / 2.5F)) & 1) == 0 ? 0.3F + 0.7F * fuse : 0.0F;
        // gathering a special power: it rears up (lifted, leaning back) and glows with a soft pulse
        float rear = smooth(Mth.clamp(Mth.lerp(pt, e.clientPowerO, e.clientPower), 0.0F, 1.0F));
        if (rear > 0.0F) {
            white = Math.max(white, rear * (0.22F + 0.14F * Mth.sin(age * 1.3F)));
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(e, white);

        float bodyYaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        float headYaw = Mth.rotLerp(pt, e.yHeadRotO, e.yHeadRot);

        if (legScale > 0.01F) {
            leftThigh.xRot = lh;
            leftThigh.zRot = 0.06F;
            leftShin.xRot = lk;
            leftFoot.xRot = -(lh + lk);
            rightThigh.xRot = rh;
            rightThigh.zRot = -0.06F;
            rightShin.xRot = rk;
            rightFoot.xRot = -(rh + rk);
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
            ps.translate(0.0F, hip, 0.0F);
            ps.mulPose(Axis.ZP.rotationDegrees(waddle * 0.4F));
            ps.scale(legScale, legScale, legScale);
            legs.render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, overlay);
            int g = (int) (Mth.clamp(grow, 0.0F, 1.0F) * (165.0F + 90.0F * (0.5F + 0.5F * Mth.sin(age * 0.2F))));
            legs.render(ps, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    FastColor.ARGB32.color(255, g, g, g));
            ps.popPose();
        }

        if (!stack.isEmpty()) {
            BakedModel base = itemRenderer.getItemModelShaper().getItemModel(stack);
            boolean flat = !base.isGui3d();
            boolean upright = flat && upright(stack, e);
            float[] anchor = flat ? anchor(base, upright) : base.isCustomRenderer() ? CUSTOM_ANCHOR : BLOCK_ANCHOR;
            float scale = (flat ? (upright ? UPRIGHT_SCALE : FLAT_SCALE) : base.isCustomRenderer() ? CUSTOM_SCALE : BLOCK_SCALE)
                    * itemGrow * swell;
            BakedModel model = itemRenderer.getModel(stack, e.level(), e, e.getId());
            float rearUp = rear * (0.1F + 0.015F * Mth.sin(age * 0.9F));
            float rearBack = -30.0F * rear;
            int itemLight = rear > 0.4F ? LightTexture.FULL_BRIGHT : light;
            ps.pushPose();
            ps.translate(0.0F, hip + rearUp, 0.0F);
            if (flat) {
                // a paper-thin item faces the camera and turns (with a smooth flip) toward where it is going;
                // its front is -X here, so a positive roll tips it forward
                float camYaw = entityRenderDispatcher.camera.getYRot();
                ps.mulPose(Axis.YP.rotationDegrees(-camYaw + flip(e, headYaw, camYaw)));
                ps.translate(-lunge, 0.0F, 0.0F);
                ps.mulPose(Axis.ZP.rotationDegrees(lean + waddle + chop + rearBack));
            } else {
                // a solid item turns with its head; its front is -Z here
                ps.mulPose(Axis.YP.rotationDegrees(180.0F - headYaw));
                ps.translate(0.0F, 0.0F, -lunge);
                ps.mulPose(Axis.XP.rotationDegrees(-(lean + chop + rearBack)));
                ps.mulPose(Axis.ZP.rotationDegrees(waddle));
            }
            ps.scale(sxz, sy, sxz);
            ps.scale(scale, scale, scale);
            if (upright) {
                // blades, tools and rods stand on their handle
                ps.mulPose(Axis.ZP.rotationDegrees(-45.0F));
            }
            ps.translate(-anchor[0], -anchor[1], 0.0F);
            itemRenderer.render(stack, ItemDisplayContext.FIXED, false, ps, buffers, itemLight, overlay, model);
            ps.popPose();
        }
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    private static float swingAngle(AnimatedItemEntity.Kind kind) {
        return switch (kind) {
            case ARCHER -> -26.0F;
            case TRIDENT, THROWER, HEALER, FOOD -> 48.0F;
            case MELEE -> 80.0F;
            default -> 60.0F;
        };
    }

    private static boolean upright(ItemStack stack, AnimatedItemEntity e) {
        Item item = stack.getItem();
        return e.kind() == AnimatedItemEntity.Kind.MELEE || item instanceof TieredItem || item instanceof TridentItem
                || item instanceof MaceItem || item instanceof FishingRodItem || item instanceof FoodOnAStickItem<?>
                || item instanceof ArrowItem || item instanceof SpyglassItem || item instanceof BrushItem
                || item instanceof QuickeningWandItem || item instanceof PyrrhicScepterItem
                || stack.is(Items.STICK) || stack.is(Items.BLAZE_ROD) || stack.is(Items.BREEZE_ROD) || stack.is(Items.BONE);
    }

    /** Smoothly turns a flat item toward the side of the screen it is walking to (renderer-owned state). */
    private static float flip(AnimatedItemEntity e, float facingYaw, float camYaw) {
        float side = Mth.sin((facingYaw - camYaw) * Mth.DEG_TO_RAD);
        if (side > 0.2F) {
            e.renderFacingRight = true;
        } else if (side < -0.2F) {
            e.renderFacingRight = false;
        }
        long now = Util.getNanos();
        float dt = e.renderFlipNanos == 0L ? 1.0F : Math.min(0.1F, (now - e.renderFlipNanos) / 1.0E9F);
        e.renderFlipNanos = now;
        float target = e.renderFacingRight ? 0.0F : 180.0F;
        float step = 900.0F * dt;
        e.renderFlip = e.renderFlip < target ? Math.min(target, e.renderFlip + step) : Math.max(target, e.renderFlip - step);
        return e.renderFlip;
    }

    private float[] anchor(BakedModel model, boolean upright) {
        TextureAtlasSprite sprite = model.getParticleIcon(ModelData.EMPTY);
        Map<TextureAtlasSprite, float[]> cache = upright ? uprightAnchors : anchors;
        return cache.computeIfAbsent(sprite, s -> computeAnchor(s, upright));
    }

    /**
     * Where the legs attach: the bottom-centre of the sprite's lowest opaque pixels (after the upright tilt, if
     * any), in FIXED display units. Sprites with empty bottom rows (an apple, a potion) still stand on their feet.
     */
    private static float[] computeAnchor(TextureAtlasSprite sprite, boolean upright) {
        try {
            SpriteContents contents = sprite.contents();
            int w = contents.width();
            int h = contents.height();
            float[] xs = new float[w * h];
            float[] ys = new float[w * h];
            float[] keys = new float[w * h];
            int n = 0;
            float lowest = Float.MAX_VALUE;
            for (int v = 0; v < h; v++) {
                for (int u = 0; u < w; u++) {
                    if (contents.isTransparent(0, u, v)) {
                        continue;
                    }
                    float x = 0.5F - (u + 0.5F) / w;
                    float y = 0.5F - (v + 1.0F) / h;
                    float key = upright ? (y - x) * 0.70710677F : y;
                    xs[n] = x;
                    ys[n] = y;
                    keys[n] = key;
                    lowest = Math.min(lowest, key);
                    n++;
                }
            }
            if (n == 0) {
                return FLAT_ANCHOR;
            }
            float band = 1.5F / h;
            float sx = 0.0F;
            float sy = 0.0F;
            int count = 0;
            for (int i = 0; i < n; i++) {
                if (keys[i] <= lowest + band) {
                    sx += xs[i];
                    sy += ys[i];
                    count++;
                }
            }
            return new float[]{sx / count, sy / count};
        } catch (RuntimeException ex) {
            return FLAT_ANCHOR;
        }
    }

    private static float easeOutBack(float t) {
        float u = t - 1.0F;
        return 1.0F + 2.70158F * u * u * u + 1.70158F * u * u;
    }

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }
}
