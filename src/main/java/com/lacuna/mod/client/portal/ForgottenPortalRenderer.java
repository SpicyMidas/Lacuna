package com.lacuna.mod.client.portal;

import com.lacuna.mod.banish.ForgottenPortalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * Draws a Forgotten Portal block like vanilla's {@code TheEndPortalRenderer}: the surface at 12/16 and the underside
 * at 6/16, with {@link ForgottenPortalRenderTypes#surface()}. The shader projects its layers from the screen, so the
 * faces of neighbouring blocks show one continuous field: the opening is a single seamless surface.
 */
public class ForgottenPortalRenderer implements BlockEntityRenderer<ForgottenPortalBlockEntity> {
    private static final float TOP = 12.0F / 16.0F;
    private static final float BOTTOM = 6.0F / 16.0F;

    public ForgottenPortalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ForgottenPortalBlockEntity portal, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer out = buffers.getBuffer(ForgottenPortalRenderTypes.surface());
        if (portal.shouldRenderFace(Direction.DOWN)) {
            out.addVertex(pose, 0.0F, BOTTOM, 0.0F);
            out.addVertex(pose, 1.0F, BOTTOM, 0.0F);
            out.addVertex(pose, 1.0F, BOTTOM, 1.0F);
            out.addVertex(pose, 0.0F, BOTTOM, 1.0F);
        }
        if (portal.shouldRenderFace(Direction.UP)) {
            out.addVertex(pose, 0.0F, TOP, 1.0F);
            out.addVertex(pose, 1.0F, TOP, 1.0F);
            out.addVertex(pose, 1.0F, TOP, 0.0F);
            out.addVertex(pose, 0.0F, TOP, 0.0F);
        }
    }
}
