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

import java.util.List;

/**
 * Купа шматків (Fij): кожен шматок — брусок з текстурами темного дуба: де була кора — кора, де розкололи вздовж волокон —
 * свіже дерево, торці — кільця (з того місця колоди, звідки шматок, тож візерунок «складається»).
 */
public class WoodPileRenderer implements BlockEntityRenderer<WoodPileBlockEntity> {
    /** Текстури за видом дерева шматка: кора, розкол (обтесана), торець */
    private static final java.util.Map<String, ResourceLocation[]> TEX = new java.util.HashMap<>();

    private static ResourceLocation[] tex(String wood) {
        return TEX.computeIfAbsent(wood, w -> new ResourceLocation[]{ new ResourceLocation("minecraft", "block/" + w + "_log"),
                new ResourceLocation("minecraft", "block/stripped_" + w + "_log"), new ResourceLocation("minecraft", "block/" + w + "_log_top") });
    }

    public static final float GAP = 0F;               // щілина між шматками (Fij, 18.11: «дивно, ніби повітря» — було 0,2; тепер чорна лінія)
    public static final double SEAM_OUT = 0.04;      // лінія стику трохи над поверхнею (пікс.), щоб не ховалась у грані
    public static final float SEAM_DARK = 0.03F;     // колір лінії стику (майже чорний)

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
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose p = pose.last();
        for (WoodPiece w : be.pieces()) {
            ResourceLocation[] rl = tex(w.wood);
            TextureAtlasSprite[] tex = { atlas.apply(rl[0]), atlas.apply(rl[1]), atlas.apply(rl[2]) };
            for (Direction d : Direction.values()) {
                byte kind = w.faces[d.get3DDataValue()];
                face(vc, p, d, w, tex[Math.max(0, Math.min(2, kind))], shade(d), light);
            }
        }
        if (be.strings() > 0) strings(be, vc, p, light);   // до ліній: інший буфер закінчує цей
        // стики (Fij): де шматок торкається іншого або це грань розрубу — чорна лінія по краю грані
        List<WoodPiece> all = be.pieces();
        VertexConsumer lines = buf.getBuffer(RenderType.lines());
        for (WoodPiece w : all)
            for (Direction d : Direction.values()) {
                boolean seam = (w.cut >> d.get3DDataValue() & 1) != 0;
                for (int i = 0; !seam && i < all.size(); i++) if (all.get(i) != w && touches(w, all.get(i), d)) seam = true;
                if (!seam) continue;
                int a = d.getAxis().ordinal();
                double[] mn = { w.x, w.y, w.z }, mx = { w.x + w.sx, w.y + w.sy, w.z + w.sz };
                mn[a] = mx[a] = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? mx[a] : mn[a];
                for (int k = 0; k < 3; k++) if (k != a) { mn[k] -= SEAM_OUT; mx[k] += SEAM_OUT; }
                net.minecraft.client.renderer.LevelRenderer.renderLineBox(pose, lines,
                        mn[0] / 16, mn[1] / 16, mn[2] / 16, mx[0] / 16, mx[1] / 16, mx[2] / 16, SEAM_DARK, SEAM_DARK, SEAM_DARK, 1F);
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

    private static final ResourceLocation STRING = new ResourceLocation("minecraft", "block/white_wool");
    public static final float STRING_WIDTH = 0.8F;   // ширина нитки (пікс.)
    public static final float STRING_OUT = 0.05F;    // нитка над поверхнею шматка (пікс.)

    /**
     * Нитки (Fij): кільця навколо купи впоперек волокон — перша на ¼ довжини, друга на ¾. Fij: «вилазить за текстуру» — тепер
     * нитка лягає на кожен шматок, який перетинає, і лише на ті його грані, що зовні (не торкаються іншого шматка), впритул.
     */
    private static void strings(WoodPileBlockEntity be, VertexConsumer vc, PoseStack.Pose p, int light) {
        List<WoodPiece> all = be.pieces();
        float[] mn = { 16, 16, 16 }, mx = { 0, 0, 0 };
        int[] grainVol = new int[3];
        for (WoodPiece w : all) {
            int[] a = { w.x, w.y, w.z }, s = { w.sx, w.sy, w.sz };
            for (int k = 0; k < 3; k++) { mn[k] = Math.min(mn[k], a[k]); mx[k] = Math.max(mx[k], a[k] + s[k]); }
            grainVol[w.grain.ordinal()] += w.volume();
        }
        int g = 0;
        for (int k = 1; k < 3; k++) if (grainVol[k] > grainVol[g]) g = k;
        TextureAtlasSprite tex = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(STRING);
        for (int i = 0; i < be.strings(); i++) {
            float at = Math.round(mn[g] + (mx[g] - mn[g]) * (i == 0 ? 0.25F : 0.75F)) + 0.5F;
            for (WoodPiece w : all) {
                int[] a = { w.x, w.y, w.z }, s = { w.sx, w.sy, w.sz };
                if (at - STRING_WIDTH / 2 < a[g] || at + STRING_WIDTH / 2 > a[g] + s[g]) continue;   // нитка не на цьому шматку
                for (Direction d : Direction.values()) {
                    if (d.getAxis().ordinal() == g) continue;
                    boolean covered = false;
                    for (WoodPiece o : all) if (o != w && touches(w, o, d)) { covered = true; break; }
                    if (covered) continue;
                    float[] lo = { a[0], a[1], a[2] }, hi = { a[0] + s[0], a[1] + s[1], a[2] + s[2] };
                    int na = d.getAxis().ordinal();
                    if (d.getAxisDirection() == Direction.AxisDirection.POSITIVE) hi[na] += STRING_OUT; else lo[na] -= STRING_OUT;
                    lo[g] = at - STRING_WIDTH / 2; hi[g] = at + STRING_WIDTH / 2;
                    stringFace(vc, p, d, lo, hi, tex, shade(d) * 0.92F, light);
                }
            }
        }
    }

    private static void stringFace(VertexConsumer vc, PoseStack.Pose p, Direction d, float[] lo, float[] hi, TextureAtlasSprite tex, float c, int light) {
        float x0 = lo[0], y0 = lo[1], z0 = lo[2], x1 = hi[0], y1 = hi[1], z1 = hi[2];
        float[][] q = switch (d) {
            case DOWN -> new float[][]{ { x0, y0, z0 }, { x1, y0, z0 }, { x1, y0, z1 }, { x0, y0, z1 } };
            case UP -> new float[][]{ { x0, y1, z0 }, { x0, y1, z1 }, { x1, y1, z1 }, { x1, y1, z0 } };
            case NORTH -> new float[][]{ { x0, y0, z0 }, { x0, y1, z0 }, { x1, y1, z0 }, { x1, y0, z0 } };
            case SOUTH -> new float[][]{ { x0, y0, z1 }, { x1, y0, z1 }, { x1, y1, z1 }, { x0, y1, z1 } };
            case WEST -> new float[][]{ { x0, y0, z0 }, { x0, y0, z1 }, { x0, y1, z1 }, { x0, y1, z0 } };
            default -> new float[][]{ { x1, y0, z0 }, { x1, y1, z0 }, { x1, y1, z1 }, { x1, y0, z1 } };
        };
        float[][] uv = { { 0, 0 }, { 0, 16 }, { 2, 16 }, { 2, 0 } };
        for (int i = 0; i < 4; i++) {
            float[] v = q[i];
            vc.vertex(p.pose(), v[0] / 16F, v[1] / 16F, v[2] / 16F).color(c, c, c * 0.92F, 1F)
                    .uv(tex.getU(uv[i][0]), tex.getV(uv[i][1])).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                    .normal(p.normal(), d.getStepX(), d.getStepY(), d.getStepZ()).endVertex();
        }
    }

    /** Грань d шматка w прилягає до шматка o (та сама площина, перекриваються) */
    private static boolean touches(WoodPiece w, WoodPiece o, Direction d) {
        int a = d.getAxis().ordinal();
        int[] wp = { w.x, w.y, w.z }, ws = { w.sx, w.sy, w.sz }, op = { o.x, o.y, o.z }, os = { o.sx, o.sy, o.sz };
        int plane = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? wp[a] + ws[a] : wp[a];
        int other = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? op[a] : op[a] + os[a];
        if (plane != other) return false;
        for (int k = 0; k < 3; k++) if (k != a && (wp[k] >= op[k] + os[k] || op[k] >= wp[k] + ws[k])) return false;
        return true;
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
