package com.lacuna.mod.client;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.mob.ClockworkHound;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Clockwork Hound: the vanilla wolf model with a brass-and-rust texture (angry variant while hunting). */
public class ClockworkHoundRenderer extends MobRenderer<ClockworkHound, WolfModel<ClockworkHound>> {
    private static final ResourceLocation TEXTURE = Lacuna.id("textures/entity/clockwork_hound.png");
    private static final ResourceLocation ANGRY = Lacuna.id("textures/entity/clockwork_hound_angry.png");

    public ClockworkHoundRenderer(EntityRendererProvider.Context context) {
        super(context, new WolfModel<>(context.bakeLayer(ModelLayers.WOLF)), 0.5F);
    }

    /** WolfModel reads the tail angle from the "bob" parameter, exactly like the vanilla WolfRenderer. */
    @Override
    protected float getBob(ClockworkHound entity, float partialTick) {
        return entity.getTailAngle();
    }

    @Override
    public ResourceLocation getTextureLocation(ClockworkHound entity) {
        return entity.isAngry() ? ANGRY : TEXTURE;
    }
}
