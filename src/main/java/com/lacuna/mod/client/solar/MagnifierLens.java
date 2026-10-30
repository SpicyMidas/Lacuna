package com.lacuna.mod.client.solar;

import com.lacuna.mod.util.SafeRun;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

/**
 * Where the lens of a Divine Magnifying Glass held by a player is drawn in third person (other players, and your own
 * F5 view), so the beam leaves exactly its centre. A render layer added to both player renderers records nothing on
 * screen: while the player channels a glass it replays {@code ItemInHandLayer#renderArmWithItem} on the pose of this
 * very frame (the model's animated arm: raised bow pose, swing, sneaking, riding, bobbing, the lerped body yaw and
 * whatever else animated the arm; then the hand offset and the item model's own held-item display transform) and
 * keeps the world position of the lens centre (sprite pixel {@link MagnifierBeams#LENS_X}, {@link MagnifierBeams#LENS_Y},
 * halfway through the item's thickness). Entities are drawn before the solar light pass of the same frame, which then
 * starts the beam there ({@link #drawn}); a player not drawn this frame falls back to the hand estimate.
 */
final class MagnifierLens extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** A recorded lens older than this (the player is no longer drawn) is not used. */
    private static final long FRESH_NANOS = 120_000_000L;
    private static final Map<Player, Capture> LENS = new WeakHashMap<>();

    private record Capture(Vec3 pos, long nanos) {}

    private MagnifierLens(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new MagnifierLens(renderer));
            }
        }
    }

    /** World position of the lens centre of {@code player}'s glass as drawn this frame, or null if not drawn lately. */
    @Nullable
    static Vec3 drawn(Player player) {
        Capture c = LENS.get(player);
        return c != null && Util.getNanos() - c.nanos() < FRESH_NANOS ? c.pos() : null;
    }

    static void clear() {
        LENS.clear();
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (MagnifierBeams.beaming(player) && !SafeRun.isDisabled(SolarClient.FEATURE)) {
            SafeRun.run(SolarClient.FEATURE, () -> capture(pose, player));
        }
    }

    private void capture(PoseStack pose, AbstractClientPlayer player) {
        InteractionHand hand = player.getUsedItemHand();
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return;
        }
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean left = arm == HumanoidArm.LEFT;
        ItemDisplayContext context = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        try {
            // ItemInHandLayer#renderArmWithItem
            getParentModel().translateToHand(arm, pose);
            pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.translate((left ? -1 : 1) / 16.0F, 0.125F, -0.625F);
            // ItemRenderer#render: the model's held-item display transform, then the -0.5 centring of the model
            BakedModel model = mc.getItemRenderer().getModel(stack, player.level(), player, player.getId() + context.ordinal());
            ClientHooks.handleCameraTransforms(pose, model, context, left);
            pose.translate(-0.5F, -0.5F, -0.5F);
            Vector4f p = pose.last().pose().transform(new Vector4f(MagnifierBeams.LENS_X / 16.0F, 1.0F - MagnifierBeams.LENS_Y / 16.0F,
                    0.5F, 1.0F));
            // the entity pass is camera-relative
            Vec3 cam = mc.getEntityRenderDispatcher().camera.getPosition();
            LENS.put(player, new Capture(new Vec3(p.x() + cam.x, p.y() + cam.y, p.z() + cam.z), Util.getNanos()));
        } finally {
            pose.popPose();
        }
    }
}
