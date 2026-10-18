package com.lacuna.mod.client.chrono;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.chrono.ChronoHourglassBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;

/**
 * The sand of a Chrono Hourglass, seen through the glass of its block models (opaque, so it is drawn before the
 * translucent glass and the glass blends over it). The bulbs are stacks of square layers matching
 * {@code tools/content/m15_chrono_hourglass.py}; the same volume of sand is split between them by
 * {@link ChronoHourglassBlockEntity#sandTop}: the top bulb empties from its surface down, the bottom one fills up, and a
 * thin animated stream joins them through the neck while the sand runs. Idle, all the sand rests in the bottom bulb.
 * Coordinates are in pixels from the lower half's corner: y 0-32 covers both blocks, the neck is at y 14-18.
 */
public class ChronoHourglassRenderer implements BlockEntityRenderer<ChronoHourglassBlockEntity> {
    private static final ResourceLocation SAND = Lacuna.id("block/chrono_hourglass_sand");
    private static final ResourceLocation STREAM = Lacuna.id("block/chrono_hourglass_stream");
    /** Sand sits this far inside the glass (pixels), so the two never z-fight. */
    private static final float INSET = 0.35F;
    /** Glass layers {radius, y0, y1}, bottom bulb from its floor up, top bulb from its neck up. */
    private static final float[][] BOTTOM = {{3, 2, 3}, {4, 3, 11}, {3, 11, 13}, {2, 13, 14}};
    private static final float[][] TOP = {{2, 18, 19}, {3, 19, 21}, {4, 21, 29}, {3, 29, 30}};
    /** All the sand fills the bottom bulb up to this height. */
    private static final float FULL_HEIGHT = 8.5F;
    private static final float NECK_TOP = 18.0F;
    private static final float SAND_VOLUME = volumeBelow(BOTTOM, FULL_HEIGHT);

    public ChronoHourglassRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public AABB getRenderBoundingBox(ChronoHourglassBlockEntity hourglass) {
        return hourglass.renderBounds();
    }

    @Override
    public void render(ChronoHourglassBlockEntity hourglass, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
                       int overlay) {
        TextureAtlasSprite sand = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(SAND);
        TextureAtlasSprite stream = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(STREAM);
        VertexConsumer vc = buffers.getBuffer(Sheets.solidBlockSheet());
        int lightUpper = hourglass.getLevel() != null ? LevelRenderer.getLightColor(hourglass.getLevel(), hourglass.getBlockPos().above()) : light;
        boolean running = hourglass.running();
        if (running) {
            // the chronal sand glows a little while it runs
            light = glow(light);
            lightUpper = glow(lightUpper);
        }
        float top = hourglass.sandTop(partialTick);
        float topVolume = top * SAND_VOLUME;
        float bottomLevel = heightFor(BOTTOM, SAND_VOLUME - topVolume);
        float topLevel = heightFor(TOP, topVolume);

        PoseStack.Pose p = pose.last();
        Box box = new Box(vc, p, sand);
        for (float[] layer : BOTTOM) {
            if (bottomLevel <= layer[1]) {
                break;
            }
            float r = layer[0] - INSET;
            box.draw(8 - r, layer[1], 8 - r, 8 + r, Math.min(layer[2], bottomLevel), 8 + r, light, layer[1] == BOTTOM[0][1]);
        }
        if (topVolume > 0.01F) {
            for (float[] layer : TOP) {
                if (topLevel <= layer[1]) {
                    break;
                }
                float r = layer[0] - INSET;
                box.draw(8 - r, layer[1], 8 - r, 8 + r, Math.min(layer[2], topLevel), 8 + r, lightUpper, true);
            }
        }
        if (running && top > 0.002F) {
            // the falling stream (its grains slide down a 4-texel repeating pattern) and a little heap where it lands
            float scroll = ((hourglass.clientAge() + partialTick) * 0.6F) % 4.0F;
            Box falling = new Box(vc, p, stream);
            float w = 0.45F;
            float y = bottomLevel;
            while (y < NECK_TOP - 0.01F) {
                float next = Math.min(NECK_TOP, Math.min(y + 4.0F, y < 16.0F ? 16.0F : NECK_TOP));
                float vTop = (NECK_TOP - next + 4.0F - scroll) % 4.0F;
                falling.draw(8 - w, y, 8 - w, 8 + w, next, 8 + w, vTop, next <= 16.0F ? light : lightUpper, false, false);
                y = next;
            }
            if (bottomLevel < 12.5F) {
                float heap = 1.3F;
                box.draw(8 - heap, bottomLevel, 8 - heap, 8 + heap, bottomLevel + 0.6F, 8 + heap, light, false);
            }
        }
    }

    private static int glow(int packed) {
        return LightTexture.pack(Math.max(LightTexture.block(packed), 9), LightTexture.sky(packed));
    }

    private static float area(float[] layer) {
        float side = 2 * (layer[0] - INSET);
        return side * side;
    }

    private static float volumeBelow(float[][] layers, float height) {
        float v = 0;
        for (float[] layer : layers) {
            if (height <= layer[1]) {
                break;
            }
            v += area(layer) * (Math.min(layer[2], height) - layer[1]);
        }
        return v;
    }

    /** The sand surface height (pixels) when {@code volume} of sand lies in {@code layers}. */
    private static float heightFor(float[][] layers, float volume) {
        float left = Math.max(0.0F, volume);
        for (float[] layer : layers) {
            float a = area(layer);
            float v = a * (layer[2] - layer[1]);
            if (left <= v) {
                return layer[1] + left / a;
            }
            left -= v;
        }
        return layers[layers.length - 1][2];
    }

    /** Axis-aligned box drawer in pixel units, sprite texels mapped 1:1 like a block model face. */
    private static final class Box {
        private final VertexConsumer vc;
        private final PoseStack.Pose pose;
        private final TextureAtlasSprite sprite;

        Box(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sprite) {
            this.vc = vc;
            this.pose = pose;
            this.sprite = sprite;
        }

        /** Side texture rows as on a block model face: row 0 at the top of the block the box sits in. */
        void draw(float x0, float y0, float z0, float x1, float y1, float z1, int light, boolean bottomFace) {
            draw(x0, y0, z0, x1, y1, z1, y1 <= 16.0F ? 16.0F - y1 : 32.0F - y1, light, true, bottomFace);
        }

        void draw(float x0, float y0, float z0, float x1, float y1, float z1, float vt, int light, boolean topFace, boolean bottomFace) {
            if (y1 - y0 < 0.01F) {
                return;
            }
            float a0 = x0 / 16, b0 = y0 / 16, c0 = z0 / 16, a1 = x1 / 16, b1 = y1 / 16, c1 = z1 / 16;
            float vb = Math.min(16.0F, vt + (y1 - y0));
            if (topFace) {
                quad(a0, b1, c0, a0, b1, c1, a1, b1, c1, a1, b1, c0, x0, z0, x1, z1, 0, 1, 0, light);
            }
            if (bottomFace) {
                quad(a0, b0, c1, a0, b0, c0, a1, b0, c0, a1, b0, c1, x0, z0, x1, z1, 0, -1, 0, light);
            }
            // north (-z) and south (+z)
            quad(a1, b1, c0, a1, b0, c0, a0, b0, c0, a0, b1, c0, 16 - x1, vt, 16 - x0, vb, 0, 0, -1, light);
            quad(a0, b1, c1, a0, b0, c1, a1, b0, c1, a1, b1, c1, x0, vt, x1, vb, 0, 0, 1, light);
            // west (-x) and east (+x)
            quad(a0, b1, c0, a0, b0, c0, a0, b0, c1, a0, b1, c1, z0, vt, z1, vb, -1, 0, 0, light);
            quad(a1, b1, c1, a1, b0, c1, a1, b0, c0, a1, b1, c0, 16 - z1, vt, 16 - z0, vb, 1, 0, 0, light);
        }

        /** Corners in order top-left, bottom-left, bottom-right, top-right of the texture; uv in texels. */
        private void quad(float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4,
                          float z4, float u0, float v0, float u1, float v1, float nx, float ny, float nz, int light) {
            float su0 = sprite.getU(u0 / 16), su1 = sprite.getU(u1 / 16), sv0 = sprite.getV(v0 / 16), sv1 = sprite.getV(v1 / 16);
            vertex(x1, y1, z1, su0, sv0, nx, ny, nz, light);
            vertex(x2, y2, z2, su0, sv1, nx, ny, nz, light);
            vertex(x3, y3, z3, su1, sv1, nx, ny, nz, light);
            vertex(x4, y4, z4, su1, sv0, nx, ny, nz, light);
        }

        private void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz, int light) {
            vc.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                    .setNormal(pose, nx, ny, nz);
        }
    }
}
