package com.lacuna.mod.client.solar;

import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.solar.SunsFuryEntity;
import com.lacuna.mod.util.SafeRun;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the thrown Sun's Fury like a thrown trident: the 3D item model (full bright, enchantment glint) pointing
 * along its velocity in flight, and embedded at its landing angle (tip in the ground) during the focus. Its light
 * (trail, flare, focus cone and rings) is queued for {@link SolarVisuals}.
 */
public class SunsFuryRenderer extends EntityRenderer<SunsFuryEntity> {
    /** Model scale: the 16 px spear sprite becomes about two blocks long. */
    private static final float SCALE = 1.7F;
    /** Distance from the sprite centre to the spear tip along its axis (model units after the 45 degree turn). */
    private static final float TIP = 0.6F * SCALE;
    /** How deep the tip sinks into the ground once embedded. */
    private static final float EMBED = 0.35F;

    private final ItemRenderer items;
    private ItemStack stack = ItemStack.EMPTY;

    public SunsFuryRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0.0F;
    }

    /**
     * Like vanilla thrown items: during its first ticks the spear is still at the thrower's eyes, where the model and
     * its light would fill their screen.
     */
    static boolean tooCloseToShow(SunsFuryEntity spear, Vec3 camera) {
        return !spear.isFocusing() && spear.tickCount < 4 && spear.position().distanceToSqr(camera) < 3.5 * 3.5;
    }

    @Override
    public void render(SunsFuryEntity spear, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (tooCloseToShow(spear, entityRenderDispatcher.camera.getPosition())) {
            return;
        }
        SafeRun.run(SolarClient.FEATURE, () -> {
            if (stack.isEmpty()) {
                // no enchantment glint on the flying spear: its own gold should read, lit by its light
                stack = new ItemStack(ModItems.SUNS_FURY.get());
                stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
            }
            Vec3 d = spear.pointing(partialTick);
            pose.pushPose();
            if (spear.isFocusing()) {
                pose.translate(-d.x * (TIP - EMBED), -d.y * (TIP - EMBED), -d.z * (TIP - EMBED));
            }
            pose.mulPose(orientation(d));
            pose.mulPose(Axis.ZP.rotationDegrees(45.0F)); // the sprite's tip (top right) onto +Y
            pose.scale(SCALE, SCALE, SCALE);
            items.renderStatic(stack, ItemDisplayContext.NONE, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers,
                    spear.level(), spear.getId());
            pose.popPose();
            SolarVisuals.queue(spear);
        });
        super.render(spear, yaw, partialTick, pose, buffers, light);
    }

    /**
     * Rotation taking model +Y (the spear axis after the 45 degree turn) onto {@code dir}, with the flat side of the
     * sprite facing sideways (model +Z horizontal) so the spear reads well from beside its path.
     */
    static Quaternionf orientation(Vec3 dir) {
        Vector3f f = dir.toVector3f();
        if (f.lengthSquared() < 1.0E-6F) {
            f.set(0.0F, -1.0F, 0.0F);
        }
        f.normalize();
        Vector3f s = new Vector3f(f).cross(0.0F, 1.0F, 0.0F);
        if (s.lengthSquared() < 1.0E-4F) {
            s.set(1.0F, 0.0F, 0.0F);
        } else {
            s.normalize();
        }
        Vector3f x = new Vector3f(f).cross(s).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f(x, f, s));
    }

    @Override
    public ResourceLocation getTextureLocation(SunsFuryEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
