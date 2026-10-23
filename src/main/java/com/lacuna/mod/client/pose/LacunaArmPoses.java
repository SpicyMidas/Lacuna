package com.lacuna.mod.client.pose;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/**
 * Two-handed arm poses added to {@link HumanoidModel.ArmPose} through META-INF/enumextensions.json (NeoForge 1.21.1
 * extends enums at load time; {@code ArmPose.create} no longer exists). Each transformer runs last for the holding arm
 * and sets BOTH arms, so the free arm joins in. Angles are written for an item held in the right hand and mirrored
 * (yRot/zRot negated, arms swapped) when it is in the left one. The item models' third-person display transforms
 * (tools/content/m15_bag.py, m15_starcaller.py) are solved against exactly these angles: change both together.
 */
public final class LacunaArmPoses {
    /** Empty Comically Large Bag: both arms raised forward, holding the open sack out, ready to catch something. */
    public static final EnumProxy<HumanoidModel.ArmPose> BAG_OPEN = new EnumProxy<>(HumanoidModel.ArmPose.class, true,
            (IArmPoseTransformer) LacunaArmPoses::bagOpen);
    /** Full Comically Large Bag: slung over the holding shoulder, both hands on its neck, like a cartoon thief. */
    public static final EnumProxy<HumanoidModel.ArmPose> BAG_CARRY = new EnumProxy<>(HumanoidModel.ArmPose.class, true,
            (IArmPoseTransformer) LacunaArmPoses::bagCarry);
    /** Starcaller Trumpet while blown: raised to the lips and held level with both hands. */
    public static final EnumProxy<HumanoidModel.ArmPose> TRUMPET_BLOW = new EnumProxy<>(HumanoidModel.ArmPose.class, true,
            (IArmPoseTransformer) LacunaArmPoses::trumpetBlow);

    // right-hand hold, radians (xRot, yRot, zRot); see the class comment
    static final float[] OPEN = {-1.85F, 0.0F, 0.0F};
    static final float[] CARRY_HOLDING = {-2.68F, 0.01F, 0.0F};
    static final float[] CARRY_FREE = {-1.75F, 0.89F, 0.0F};
    static final float[] BLOW_RIGHT = {-1.73F, -0.5F, 0.0F};

    private LacunaArmPoses() {}

    private static void bagOpen(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        // the sack follows the eyes up and down a little, so it is held out toward what is looked at
        float pitch = Mth.clamp(model.head.xRot, -0.7F, 0.6F) * 0.8F;
        set(model.rightArm, OPEN[0] + pitch, -OPEN[1], -OPEN[2]);
        set(model.leftArm, OPEN[0] + pitch, OPEN[1], OPEN[2]);
    }

    private static void bagCarry(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        boolean right = arm == HumanoidArm.RIGHT;
        float m = right ? 1.0F : -1.0F;
        ModelPart holding = right ? model.rightArm : model.leftArm;
        ModelPart free = right ? model.leftArm : model.rightArm;
        set(holding, CARRY_HOLDING[0], m * CARRY_HOLDING[1], m * CARRY_HOLDING[2]);
        set(free, CARRY_FREE[0], m * CARRY_FREE[1], m * CARRY_FREE[2]);
    }

    private static void trumpetBlow(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        float pitch = Mth.clamp(model.head.xRot, -1.0F, 1.0F);
        float yaw = Mth.clamp(model.head.yRot, -0.8F, 0.8F);
        set(model.rightArm, BLOW_RIGHT[0] + pitch, BLOW_RIGHT[1] + yaw, BLOW_RIGHT[2]);
        set(model.leftArm, BLOW_RIGHT[0] + pitch, -BLOW_RIGHT[1] + yaw, -BLOW_RIGHT[2]);
    }

    private static void set(ModelPart part, float x, float y, float z) {
        part.xRot = x;
        part.yRot = y;
        part.zRot = z;
    }
}
