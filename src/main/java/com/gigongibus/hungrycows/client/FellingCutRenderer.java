package com.gigongibus.hungrycows.client;

import com.gigongibus.hungrycows.fell.FellingCutBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Колода стовбура з зарубкою (Fij): дуб, з того боку, звідки рубали, вирізано щілину на висоті розрубу — що глибша зарубка,
 * то глибше; усередині видно свіже дерево. Після падіння — лише пень (низ колоди до висоти розрубу, зверху — кільця).
 */
public class FellingCutRenderer implements BlockEntityRenderer<FellingCutBlockEntity> {
    private static final ResourceLocation BARK = new ResourceLocation("minecraft", "block/oak_log");
    private static final ResourceLocation INNER = new ResourceLocation("minecraft", "block/stripped_oak_log");
    private static final ResourceLocation TOP = new ResourceLocation("minecraft", "block/oak_log_top");
    public static final float GAP = 2.5F;     // висота щілини (пікс.): клин від сокири

    public FellingCutRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(FellingCutBlockEntity be, float partial, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite bark = atlas.apply(BARK), inner = atlas.apply(INNER), top = atlas.apply(TOP);
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose p = pose.last();
        float at = be.at();
        if (be.felled()) {                                         // пень
            box(vc, p, 0, 0, 0, 16, at, 16, bark, top, top, light);
            return;
        }
        float lo = Math.max(0, at - GAP / 2), hi = Math.min(16, at + GAP / 2), d = be.depth();
        box(vc, p, 0, 0, 0, 16, lo, 16, bark, top, inner, light);   // низ
        box(vc, p, 0, hi, 0, 16, 16, 16, bark, inner, top, light);  // верх
        // середина — без вирізаного з боку side на глибину d
        Direction s = be.side();
        float x0 = 0, x1 = 16, z0 = 0, z1 = 16;
        switch (s) {
            case NORTH -> z0 = d;
            case SOUTH -> z1 = 16 - d;
            case WEST -> x0 = d;
            default -> x1 = 16 - d;
        }
        if (x1 > x0 && z1 > z0) box(vc, p, x0, lo, z0, x1, hi, z1, bark, inner, inner, light, s);
    }

    private static void box(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
                            TextureAtlasSprite side, TextureAtlasSprite bottom, TextureAtlasSprite top, int light) {
        box(vc, p, x0, y0, z0, x1, y1, z1, side, bottom, top, light, null);
    }

    /** Брусок у пікселях; грань з боку cutSide (дно зарубки) — свіже дерево */
    private static void box(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
                            TextureAtlasSprite side, TextureAtlasSprite bottom, TextureAtlasSprite top, int light, Direction cutSide) {
        for (Direction d : Direction.values()) {
            TextureAtlasSprite t = d == Direction.UP ? top : d == Direction.DOWN ? bottom : d == cutSide ? bottom : side;
            float c = switch (d) { case UP -> 1F; case DOWN -> 0.5F; case NORTH, SOUTH -> 0.8F; default -> 0.6F; };
            float[][] q = switch (d) {
                case DOWN -> new float[][]{ { x0, y0, z0 }, { x1, y0, z0 }, { x1, y0, z1 }, { x0, y0, z1 } };
                case UP -> new float[][]{ { x0, y1, z0 }, { x0, y1, z1 }, { x1, y1, z1 }, { x1, y1, z0 } };
                case NORTH -> new float[][]{ { x0, y0, z0 }, { x0, y1, z0 }, { x1, y1, z0 }, { x1, y0, z0 } };
                case SOUTH -> new float[][]{ { x0, y0, z1 }, { x1, y0, z1 }, { x1, y1, z1 }, { x0, y1, z1 } };
                case WEST -> new float[][]{ { x0, y0, z0 }, { x0, y0, z1 }, { x0, y1, z1 }, { x0, y1, z0 } };
                default -> new float[][]{ { x1, y0, z0 }, { x1, y1, z0 }, { x1, y1, z1 }, { x1, y0, z1 } };
            };
            for (float[] v : q) {
                float u, w;
                if (d.getAxis() == Direction.Axis.Y) { u = v[0]; w = v[2]; }
                else { u = d.getAxis() == Direction.Axis.X ? v[2] : v[0]; w = 16 - v[1]; }
                vc.vertex(p.pose(), v[0] / 16F, v[1] / 16F, v[2] / 16F).color(c, c, c, 1F).uv(t.getU(u), t.getV(w))
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(p.normal(), d.getStepX(), d.getStepY(), d.getStepZ()).endVertex();
            }
        }
    }
}
