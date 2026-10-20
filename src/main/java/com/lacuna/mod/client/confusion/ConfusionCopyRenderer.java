package com.lacuna.mod.client.confusion;

import com.lacuna.mod.confusion.ConfusionCopy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * Draws a {@link ConfusionCopy} exactly like the vanilla player renderer would draw its owner: the owner's skin on the
 * wide or slim player model (picked per frame from the skin), their skin layers, armour, held items, stuck arrows, cape,
 * elytra and head item, sneaking offset, swimming tilt, name tag (with the below-name score) and shadow.
 */
public class ConfusionCopyRenderer extends EntityRenderer<ConfusionCopy> {
    private final Body wide;
    private final Body slim;

    public ConfusionCopyRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
        this.wide = new Body(context, false);
        this.slim = new Body(context, true);
    }

    private Body body(ConfusionCopy copy) {
        return ConfusionClient.skin(copy).model() == PlayerSkin.Model.SLIM ? slim : wide;
    }

    @Override
    public void render(ConfusionCopy copy, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light) {
        body(copy).render(copy, yaw, partialTick, poseStack, buffer, light);
    }

    @Override
    public Vec3 getRenderOffset(ConfusionCopy copy, float partialTick) {
        return copy.isCrouching() ? new Vec3(0.0, copy.getScale() * -2.0F / 16.0, 0.0) : super.getRenderOffset(copy, partialTick);
    }

    @Override
    public ResourceLocation getTextureLocation(ConfusionCopy copy) {
        return ConfusionClient.skin(copy).texture();
    }

    /** The player renderer, for a copy: same model, layers, poses and name tag. */
    static final class Body extends LivingEntityRenderer<ConfusionCopy, PlayerModel<ConfusionCopy>> {
        Body(EntityRendererProvider.Context context, boolean slim) {
            super(context, new PlayerModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim), 0.5F);
            addLayer(new HumanoidArmorLayer<>(this,
                    new HumanoidArmorModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR)),
                    new HumanoidArmorModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR)),
                    context.getModelManager()));
            addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
            addLayer(new ArrowLayer<>(context, this));
            addLayer(new Cape(this));
            addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
            addLayer(new Elytra(this, context.getModelSet()));
        }

        @Override
        public void render(ConfusionCopy copy, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light) {
            setModelProperties(copy);
            super.render(copy, yaw, partialTick, poseStack, buffer, light);
        }

        private void setModelProperties(ConfusionCopy copy) {
            PlayerModel<ConfusionCopy> model = getModel();
            model.setAllVisible(true);
            model.hat.visible = copy.isPartShown(PlayerModelPart.HAT);
            model.jacket.visible = copy.isPartShown(PlayerModelPart.JACKET);
            model.leftPants.visible = copy.isPartShown(PlayerModelPart.LEFT_PANTS_LEG);
            model.rightPants.visible = copy.isPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
            model.leftSleeve.visible = copy.isPartShown(PlayerModelPart.LEFT_SLEEVE);
            model.rightSleeve.visible = copy.isPartShown(PlayerModelPart.RIGHT_SLEEVE);
            model.crouching = copy.isCrouching();
            HumanoidModel.ArmPose main = armPose(copy, InteractionHand.MAIN_HAND);
            HumanoidModel.ArmPose off = armPose(copy, InteractionHand.OFF_HAND);
            if (main.isTwoHanded()) {
                off = copy.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
            }
            if (copy.getMainArm() == HumanoidArm.RIGHT) {
                model.rightArmPose = main;
                model.leftArmPose = off;
            } else {
                model.rightArmPose = off;
                model.leftArmPose = main;
            }
        }

        private static HumanoidModel.ArmPose armPose(ConfusionCopy copy, InteractionHand hand) {
            ItemStack stack = copy.getItemInHand(hand);
            if (stack.isEmpty()) {
                return HumanoidModel.ArmPose.EMPTY;
            }
            if (!copy.swinging && stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
                return HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }
            HumanoidModel.ArmPose custom = IClientItemExtensions.of(stack).getArmPose(copy, hand, stack);
            return custom != null ? custom : HumanoidModel.ArmPose.ITEM;
        }

        @Override
        public ResourceLocation getTextureLocation(ConfusionCopy copy) {
            return ConfusionClient.skin(copy).texture();
        }

        @Override
        protected void scale(ConfusionCopy copy, PoseStack poseStack, float partialTick) {
            poseStack.scale(0.9375F, 0.9375F, 0.9375F);
        }

        @Override
        protected void setupRotations(ConfusionCopy copy, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
            super.setupRotations(copy, poseStack, bob, yBodyRot, partialTick, scale);
            float swim = copy.getSwimAmount(partialTick);
            if (swim > 0.0F) {
                float target = copy.isInWater() || copy.isInFluidType((fluidType, height) -> copy.canSwimInFluidType(fluidType))
                        ? -90.0F - copy.getXRot() : -90.0F;
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(swim, 0.0F, target)));
                if (copy.isVisuallySwimming()) {
                    poseStack.translate(0.0F, -1.0F, 0.3F);
                }
            }
        }

        @Override
        protected void renderNameTag(ConfusionCopy copy, Component name, PoseStack poseStack, MultiBufferSource buffer, int light,
                                     float partialTick) {
            poseStack.pushPose();
            if (entityRenderDispatcher.distanceToSqr(copy) < 100.0 && !copy.ownerName().isEmpty()) {
                Scoreboard scoreboard = copy.level().getScoreboard();
                Objective objective = scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME);
                if (objective != null) {
                    ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(copy.ownerName()), objective);
                    Component score = ReadOnlyScoreInfo.safeFormatValue(info, objective.numberFormatOrDefault(StyledFormat.NO_STYLE));
                    super.renderNameTag(copy, Component.empty().append(score).append(CommonComponents.SPACE).append(objective.getDisplayName()),
                            poseStack, buffer, light, partialTick);
                    poseStack.translate(0.0F, 9.0F * 1.15F * 0.025F, 0.0F);
                }
            }
            super.renderNameTag(copy, name, poseStack, buffer, light, partialTick);
            poseStack.popPose();
        }
    }

    /** The vanilla cape layer, fed by the copy's own cape physics. */
    static final class Cape extends RenderLayer<ConfusionCopy, PlayerModel<ConfusionCopy>> {
        Cape(Body parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int light, ConfusionCopy copy, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (copy.isInvisible() || !copy.isPartShown(PlayerModelPart.CAPE)) {
                return;
            }
            PlayerSkin skin = ConfusionClient.skin(copy);
            if (skin.capeTexture() == null || copy.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                return;
            }
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, 0.125F);
            double dx = Mth.lerp(partialTick, copy.xCloakO, copy.xCloak) - Mth.lerp(partialTick, copy.xo, copy.getX());
            double dy = Mth.lerp(partialTick, copy.yCloakO, copy.yCloak) - Mth.lerp(partialTick, copy.yo, copy.getY());
            double dz = Mth.lerp(partialTick, copy.zCloakO, copy.zCloak) - Mth.lerp(partialTick, copy.zo, copy.getZ());
            float body = Mth.rotLerp(partialTick, copy.yBodyRotO, copy.yBodyRot);
            double sin = Mth.sin(body * Mth.DEG_TO_RAD);
            double cos = -Mth.cos(body * Mth.DEG_TO_RAD);
            float lift = Mth.clamp((float) dy * 10.0F, -6.0F, 32.0F);
            float swing = Mth.clamp((float) (dx * sin + dz * cos) * 100.0F, 0.0F, 150.0F);
            float side = Mth.clamp((float) (dx * cos - dz * sin) * 100.0F, -20.0F, 20.0F);
            float bob = Mth.lerp(partialTick, copy.oBob, copy.bob);
            lift += Mth.sin(Mth.lerp(partialTick, copy.capeWalkO, copy.capeWalk) * 6.0F) * 32.0F * bob;
            if (copy.isCrouching()) {
                lift += 25.0F;
            }
            poseStack.mulPose(Axis.XP.rotationDegrees(6.0F + swing / 2.0F + lift));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side / 2.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - side / 2.0F));
            VertexConsumer consumer = buffer.getBuffer(RenderType.entitySolid(skin.capeTexture()));
            getParentModel().renderCloak(poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
    }

    /** Elytra in the owner's skin elytra or cape texture, as players wear it. */
    static final class Elytra extends ElytraLayer<ConfusionCopy, PlayerModel<ConfusionCopy>> {
        Elytra(Body parent, EntityModelSet models) {
            super(parent, models);
        }

        @Override
        public ResourceLocation getElytraTexture(ItemStack stack, ConfusionCopy copy) {
            PlayerSkin skin = ConfusionClient.skin(copy);
            if (skin.elytraTexture() != null) {
                return skin.elytraTexture();
            }
            if (skin.capeTexture() != null && copy.isPartShown(PlayerModelPart.CAPE)) {
                return skin.capeTexture();
            }
            return super.getElytraTexture(stack, copy);
        }
    }
}
