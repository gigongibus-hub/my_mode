package com.gigongibus.hungrycows.client;

import com.gigongibus.hungrycows.chop.WoodPiece;
import com.gigongibus.hungrycows.chop.WoodPileBlockEntity;
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
 * Купа шматків (Fij): кожен шматок — брусок з текстурами темного дуба: де була кора — кора, де розкололи вздовж волокон —
 * свіже дерево, торці — кільця (з того місця колоди, звідки шматок, тож візерунок «складається»).
 */
public class WoodPileRenderer implements BlockEntityRenderer<WoodPileBlockEntity> {
    private static final ResourceLocation BARK = new ResourceLocation("minecraft", "block/dark_oak_log");
    private static final ResourceLocation SPLIT = new ResourceLocation("minecraft", "block/stripped_dark_oak_log");
    private static final ResourceLocation END = new ResourceLocation("minecraft", "block/dark_oak_log_top");

    public static final float GAP = 0.2F;             // пікселя з кожного боку розрубу (щілина 0,4 пікс.)

    private static float gap(WoodPiece w, Direction d, float g) {
        return (w.cut >> d.get3DDataValue() & 1) != 0 ? g : 0;
    }

    public WoodPileRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    private static float shade(Direction d) {
        return switch (d) { case UP -> 1F; case DOWN -> 0.5F; case NORTH, SOUTH -> 0.8F; default -> 0.6F; };
    }

    @Override
    public void render(WoodPileBlockEntity be, float partial, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite[] tex = { atlas.apply(BARK), atlas.apply(SPLIT), atlas.apply(END) };
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose p = pose.last();
        for (WoodPiece w : be.pieces())
            for (Direction d : Direction.values()) {
                byte kind = w.faces[d.get3DDataValue()];
                face(vc, p, d, w, tex[Math.max(0, Math.min(2, kind))], shade(d), light);
            }
        // засічка: де сокира застрягла — темна риска навколо шматка
        for (WoodPiece w : be.pieces()) {
            if (w.notchAxis < 0) continue;
            double[] mn = { w.x, w.y, w.z }, mx = { w.x + w.sx, w.y + w.sy, w.z + w.sz };
            int a = w.notchAxis;
            mn[a] = mx[a] = new int[]{ w.x, w.y, w.z }[a] + w.notchAt;
            for (int i = 0; i < 3; i++) if (i != a) { mn[i] -= NOTCH_OUT; mx[i] += NOTCH_OUT; }
            net.minecraft.client.renderer.LevelRenderer.renderLineBox(pose, buf.getBuffer(RenderType.lines()),
                    mn[0] / 16, mn[1] / 16, mn[2] / 16, mx[0] / 16, mx[1] / 16, mx[2] / 16, 0.12F, 0.08F, 0.05F, 1F);
        }
    }

    public static final double NOTCH_OUT = 0.03;     // риска засічки трохи над поверхнею

    /** Грань бруска (у пікселях) — текстура вирізається з того місця, де брусок лежить */
    private static void face(VertexConsumer vc, PoseStack.Pose p, Direction d, WoodPiece w, TextureAtlasSprite tex, float c, int light) {
        // Fij: розруб має бути видно — грані розрубу трохи «всередину», між шматками тонка щілина
        float g = GAP;
        float x0 = w.x + gap(w, Direction.WEST, g), y0 = w.y + gap(w, Direction.DOWN, g), z0 = w.z + gap(w, Direction.NORTH, g);
        float x1 = w.x + w.sx - gap(w, Direction.EAST, g), y1 = w.y + w.sy - gap(w, Direction.UP, g), z1 = w.z + w.sz - gap(w, Direction.SOUTH, g);
        float[][] q = switch (d) {
            case DOWN -> new float[][]{ { x0, y0, z0 }, { x1, y0, z0 }, { x1, y0, z1 }, { x0, y0, z1 } };
            case UP -> new float[][]{ { x0, y1, z0 }, { x0, y1, z1 }, { x1, y1, z1 }, { x1, y1, z0 } };
            case NORTH -> new float[][]{ { x0, y0, z0 }, { x0, y1, z0 }, { x1, y1, z0 }, { x1, y0, z0 } };
            case SOUTH -> new float[][]{ { x0, y0, z1 }, { x1, y0, z1 }, { x1, y1, z1 }, { x0, y1, z1 } };
            case WEST -> new float[][]{ { x0, y0, z0 }, { x0, y0, z1 }, { x0, y1, z1 }, { x0, y1, z0 } };
            default -> new float[][]{ { x1, y0, z0 }, { x1, y1, z0 }, { x1, y1, z1 }, { x1, y0, z1 } };
        };
        // дві осі грані: «довжина» текстури (V) — уздовж волокон, якщо вони лежать у грані
        int na = d.getAxis().ordinal(), ga = w.grain.ordinal();
        int ua = -1, va = -1;
        for (int a = 0; a < 3; a++) if (a != na) { if (ua < 0) ua = a; else va = a; }
        if (ga != na) { va = ga; ua = 3 - na - ga; }                 // бік: волокна вздовж V
        for (float[] v : q) {
            float uu = v[ua], vv = 16 - v[va];
            vc.vertex(p.pose(), v[0] / 16F, v[1] / 16F, v[2] / 16F).color(c, c, c, 1F)
                    .uv(tex.getU(Math.max(0, Math.min(16, uu))), tex.getV(Math.max(0, Math.min(16, vv))))
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                    .normal(p.normal(), d.getStepX(), d.getStepY(), d.getStepZ()).endVertex();
        }
    }
}
