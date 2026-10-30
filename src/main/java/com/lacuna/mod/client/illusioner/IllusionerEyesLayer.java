package com.lacuna.mod.client.illusioner;

import com.lacuna.mod.Lacuna;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.world.entity.monster.AbstractIllager;

/** Pale starlight eyes under the illusioner's hood, drawn full-bright (like a spider's or an enderman's eyes). */
public class IllusionerEyesLayer<T extends AbstractIllager> extends EyesLayer<T, IllagerModel<T>> {
    private static final RenderType EYES = RenderType.eyes(Lacuna.id("textures/entity/illusioner_eyes.png"));

    public IllusionerEyesLayer(RenderLayerParent<T, IllagerModel<T>> parent) {
        super(parent);
    }

    @Override
    public RenderType renderType() {
        return EYES;
    }
}
