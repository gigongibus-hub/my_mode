package com.gigongibus.hungrycows.client;

import com.gigongibus.hungrycows.carve.WorkpieceBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import com.gigongibus.hungrycows.carve.VoxelCarve;
import com.gigongibus.hungrycows.carve.VoxelMask;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Заготовка на верстаку: креслення лежить пласко, на ньому — колода, що поклали (менша, уздовж) */
public class WorkpieceRenderer implements BlockEntityRenderer<WorkpieceBlockEntity> {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final float SIZE = 0.62F;      // колода — 62% блока
    public static final float PAPER_SIZE = 0.85F; // креслення на столі

    public WorkpieceRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(WorkpieceBlockEntity be, float partial, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        if (be.blueprint() != null) {   // креслення — пласка картинка предмета на столі
            pose.pushPose();
            pose.translate(0.5, 0.01, 0.5);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(PAPER_SIZE, PAPER_SIZE, PAPER_SIZE);
            Minecraft.getInstance().getItemRenderer().renderStatic(new net.minecraft.world.item.ItemStack(be.blueprint()),
                    net.minecraft.world.item.ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buf, be.getLevel(), (int) be.getBlockPos().asLong());
            pose.popPose();
        }
        if (be.hasLog() && be.voxels() != null) {
            renderVoxels(be, pose, buf, light);
            return;
        }
        if (!be.hasLog() || !(be.log() instanceof BlockItem bi)) return;
        BlockState s = bi.getBlock().defaultBlockState();
        if (s.hasProperty(RotatedPillarBlock.AXIS)) s = s.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        pose.pushPose();
        pose.translate(0.5, 0.02, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(15));
        pose.scale(SIZE, SIZE, SIZE);
        pose.translate(-0.5, 0, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(s, pose, buf, light, OverlayTexture.NO_OVERLAY,
                net.minecraftforge.client.model.data.ModelData.EMPTY, null);
        pose.popPose();
    }

    // ---------- справжня різьба (Fij, 17.11): кожен піксель колоди — кубик ----------
    private static final ResourceLocation SIDE = new ResourceLocation("minecraft", "block/stripped_spruce_log");
    private static final ResourceLocation END = new ResourceLocation("minecraft", "block/stripped_spruce_log_top");
    public static final float PENCIL_WIDTH = 0.12F;      // олівцева лінія, частка пікселя
    public static final int PENCIL = 0x3A3530;            // колір олівця
    // Fij (17.11): «деталь підсвічується чорним, коли до неї доходиш» — олівцевий обрис вимкнено (true — повернути)
    public static final boolean PENCIL_LINES = false;
    public static final float REVEALED = 0.13F;           // деталь, до якої дійшов, — майже чорна

    private static float shade(Direction d) {
        return switch (d) { case UP -> 1F; case DOWN -> 0.5F; case NORTH, SOUTH -> 0.8F; default -> 0.6F; };
    }

    /** Чи йде лінія з цієї клітинки вглиб (проти грані) крізь деталь креслення — так столяр переносить обрис на заготовку */
    private static boolean inside(VoxelMask keep, int x, int y, int z, Direction d) {
        for (int k = 0; k < VoxelMask.N; k++) {
            int xx = x - d.getStepX() * k, yy = y - d.getStepY() * k, zz = z - d.getStepZ() * k;
            if (!VoxelMask.in(xx, yy, zz)) return false;
            if (keep.get(xx, yy, zz)) return true;
        }
        return false;
    }

    private void renderVoxels(WorkpieceBlockEntity be, PoseStack pose, MultiBufferSource buf, int light) {
        VoxelMask m = be.voxels(), keep = be.keep3d();
        int sink = be.sink();
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite side = atlas.apply(SIDE), end = atlas.apply(END);
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose p = pose.last();
        int n = VoxelMask.N, o = VoxelCarve.OFFSET_XZ, oy = VoxelCarve.OFFSET_Y;
        for (int y = 0; y < n; y++)
            for (int z = 0; z < n; z++)
                for (int x = 0; x < n; x++) {
                    if (!m.get(x, y, z)) continue;
                    for (Direction d : Direction.values()) {
                        if (m.get(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ())) continue;
                        float c = shade(d);
                        if (keep != null && keep.get(x, y + sink, z)) c *= REVEALED;      // дійшов до деталі — чорна
                        face(vc, p, d, o + x, oy + y, o + z, d.getAxis() == Direction.Axis.X ? end : side, c, c, c, light, 0, 1, 0);
                        if (!PENCIL_LINES || keep == null || !inside(keep, x, y, z, d)) continue;
                        // олівець: край обрису на цій грані
                        for (Direction u : Direction.values()) {
                            if (u.getAxis() == d.getAxis()) continue;
                            if (inside(keep, x + u.getStepX(), y + u.getStepY(), z + u.getStepZ(), d)) continue;
                            float r = (PENCIL >> 16 & 255) / 255F, g = (PENCIL >> 8 & 255) / 255F, b = (PENCIL & 255) / 255F;
                            line(vc, p, d, u, o + x, oy + y, o + z, side, r * c, g * c, b * c, light);
                        }
                    }
                }
    }

    /** Грань клітинки (у пікселях) з текстурою: на боках волокна вздовж x, на торцях — кільця */
    private static void face(VertexConsumer vc, PoseStack.Pose p, Direction d, float x, float y, float z, TextureAtlasSprite tex,
                             float r, float g, float b, int light, float from, float to, float lift) {
        float[][] q = corners(d, x, y, z, from, to, lift * 0);
        for (float[] c : q) {
            float uu, vv;
            switch (d.getAxis()) {
                case X -> { uu = c[2]; vv = 16 - c[1]; }
                case Y -> { uu = c[2]; vv = c[0]; }
                default -> { uu = c[1]; vv = c[0]; }
            }
            vc.vertex(p.pose(), c[0] / 16F, c[1] / 16F, c[2] / 16F).color(r, g, b, 1F)
                    .uv(tex.getU(Math.max(0, Math.min(16, uu))), tex.getV(Math.max(0, Math.min(16, vv))))
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                    .normal(p.normal(), d.getStepX(), d.getStepY(), d.getStepZ()).endVertex();
        }
    }

    /** Кути грані клітинки (x, y, z) у пікселях; from/to — частина грані впоперек */
    private static float[][] corners(Direction d, float x, float y, float z, float from, float to, float lift) {
        float[][] out = new float[4][];
        float[][] pq = { { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 } };
        for (int i = 0; i < 4; i++) {
            float a = pq[i][0], b2 = pq[i][1];
            float px, py, pz;
            float off = 0.002F;
            switch (d.getAxis()) {
                case X -> { px = x + (d.getStepX() > 0 ? 1 + off : -off); py = y + a; pz = z + b2; }
                case Y -> { py = y + (d.getStepY() > 0 ? 1 + off : -off); px = x + a; pz = z + b2; }
                default -> { pz = z + (d.getStepZ() > 0 ? 1 + off : -off); px = x + a; py = y + b2; }
            }
            out[i] = new float[]{ px, py, pz };
        }
        return out;
    }

    /** Олівцева риска на грані d клітинки, уздовж її краю з боку u */
    private static void line(VertexConsumer vc, PoseStack.Pose p, Direction d, Direction u, float x, float y, float z,
                             TextureAtlasSprite tex, float r, float g, float b, int light) {
        float[][] q = corners(d, x, y, z, 0, 1, 0);
        // зсунути всі кути до краю u і стиснути впоперек до ширини риски
        float lift = 0.004F;
        for (float[] c : q) {
            float[] cell = { x, y, z };
            int ax = u.getAxis().ordinal();
            float edge = cell[ax] + (u.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
            float inner = edge - (u.getAxisDirection() == Direction.AxisDirection.POSITIVE ? PENCIL_WIDTH : -PENCIL_WIDTH);
            c[ax] = Math.abs(c[ax] - edge) < 0.5F ? edge : inner;
            int na = d.getAxis().ordinal();
            c[na] += d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? lift : -lift;
        }
        for (float[] c : q)
            vc.vertex(p.pose(), c[0] / 16F, c[1] / 16F, c[2] / 16F).color(r, g, b, 1F)
                    .uv(tex.getU(8), tex.getV(8)).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                    .normal(p.normal(), d.getStepX(), d.getStepY(), d.getStepZ()).endVertex();
    }
}
