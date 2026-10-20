package com.lacuna.mod.client.mummy;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.mummy.Mummification;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The starlace wraps on a mummy, for every living-entity renderer (added to all of them by {@link MummyClient}).
 * <p>
 * The creature's own model, already posed for this frame, is drawn a second time with a wrap texture instead of the
 * creature's skin, then a third time with the matching glow texture (additive, full-bright: the faint light of the
 * cloth and its gold star threads, gently pulsing). The geometry is identical to the creature's own pass, so the
 * wraps sit exactly on its skin like a second skin (equal depth passes the depth test), with no z-fighting.
 * <p>
 * The wrap textures are painted in model-UV space (tools/art/m16_starlace_bandage.py): horizontal strips in the
 * texture become bands wound round every box-UV cube, whatever the model. The variant is picked by the size of the
 * creature's own texture, so the strips land on its own pixel grid; humanoid models get the variant with an eye slit
 * and loose outer wraps on the hat/jacket layer.
 */
final class MummyLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private record Wrap(int w, int h, ResourceLocation wrap, ResourceLocation glow) {}

    private static final Wrap HUMANOID_64X64 = new Wrap(64, 64, Lacuna.id("textures/entity/mummy/wrap_humanoid_64x64.png"),
            Lacuna.id("textures/entity/mummy/glow_humanoid_64x64.png"));
    private static final Wrap HUMANOID_64X32 = new Wrap(64, 32, Lacuna.id("textures/entity/mummy/wrap_humanoid_64x32.png"),
            Lacuna.id("textures/entity/mummy/glow_humanoid_64x32.png"));
    private static final List<Wrap> GENERIC = List.of(
            new Wrap(64, 64, Lacuna.id("textures/entity/mummy/wrap_64x64.png"), Lacuna.id("textures/entity/mummy/glow_64x64.png")),
            new Wrap(64, 32, Lacuna.id("textures/entity/mummy/wrap_64x32.png"), Lacuna.id("textures/entity/mummy/glow_64x32.png")),
            new Wrap(32, 32, Lacuna.id("textures/entity/mummy/wrap_32x32.png"), Lacuna.id("textures/entity/mummy/glow_32x32.png")),
            new Wrap(128, 64, Lacuna.id("textures/entity/mummy/wrap_128x64.png"), Lacuna.id("textures/entity/mummy/glow_128x64.png")));

    /** Pixel size of each creature texture seen so far, read once from its PNG header (w << 16 | h). */
    private static final Map<ResourceLocation, Integer> SIZES = new HashMap<>();

    MummyLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    static void clearCache() {
        SIZES.clear();
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible() || !Mummification.sealed(entity)) {
            return;
        }
        Wrap wrap = pick(getTextureLocation(entity), getParentModel() instanceof HumanoidModel<?>);
        M model = getParentModel();
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(wrap.wrap())), light,
                LivingEntityRenderer.getOverlayCoords(entity, 0.0F));
        float pulse = 0.7F + 0.3F * Mth.sin((ageInTicks + entity.getId() * 7) * 0.07F);
        int c = (int) (pulse * 255.0F);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(wrap.glow())), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                FastColor.ARGB32.color(255, c, c, c));
    }

    /**
     * The wrap matching the creature's texture: same size if there is one, else the nearest shape and size (a 128x128
     * iron golem takes the 64x64 wraps: each wrap pixel covers 2x2 of its own, still on its pixel grid).
     */
    private static Wrap pick(ResourceLocation texture, boolean humanoid) {
        int size = SIZES.computeIfAbsent(texture, MummyLayer::readSize);
        int w = size >>> 16;
        int h = size & 0xFFFF;
        if (humanoid && (w == h || w == 2 * h)) {
            return w == h ? HUMANOID_64X64 : HUMANOID_64X32;
        }
        Wrap best = GENERIC.get(0);
        double bestScore = Double.MAX_VALUE;
        for (Wrap v : GENERIC) {
            double aspect = Math.abs(Math.log((v.w() / (double) v.h()) / (w / (double) h)));
            double scale = Math.abs(Math.log(v.w() / (double) w));
            double score = aspect * 10.0 + scale;
            if (score < bestScore) {
                bestScore = score;
                best = v;
            }
        }
        return best;
    }

    /** Width and height from the PNG header (IHDR), or 64x64 when the texture is not a resource (a downloaded skin). */
    private static int readSize(ResourceLocation texture) {
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(texture)) {
            byte[] head = in.readNBytes(24);
            if (head.length == 24) {
                int w = ((head[16] & 0xFF) << 24) | ((head[17] & 0xFF) << 16) | ((head[18] & 0xFF) << 8) | (head[19] & 0xFF);
                int h = ((head[20] & 0xFF) << 24) | ((head[21] & 0xFF) << 16) | ((head[22] & 0xFF) << 8) | (head[23] & 0xFF);
                if (w > 0 && h > 0 && w < 0x10000 && h < 0x10000) {
                    return (w << 16) | h;
                }
            }
        } catch (Exception ignored) {
            // not a resource: fall through
        }
        return (64 << 16) | 64;
    }
}
