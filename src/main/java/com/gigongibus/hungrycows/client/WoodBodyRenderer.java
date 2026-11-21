package com.gigongibus.hungrycows.client;

import com.gigongibus.hungrycows.phys.Part;
import com.gigongibus.hungrycows.phys.WoodBodyEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Фізичне тіло (Fij, 20.11): кожен брусок — свій блок чи шматок, повернутий разом з тілом (плавно між тіками) */
public class WoodBodyRenderer extends EntityRenderer<WoodBodyEntity> {
    public WoodBodyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(WoodBodyEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buf, int light) {
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        Quaternionf q = new Quaternionf(e.prevQ).slerp(e.curQ, partial);
        var origin = e.getPosition(partial);
        for (Part p : e.body.parts) {
            if (p.gone) continue;
            Vector3f wc = q.transform(new Vector3f((float) p.c.x, (float) p.c.y, (float) p.c.z));
            int l = light(e, origin.x + wc.x, origin.y + wc.y, origin.z + wc.z);
            pose.pushPose();
            pose.mulPose(q);
            if (p.kind == Part.PIECE) {
                // шматок малюється в пікселях свого місця в колоді (щоб текстура складалась)
                pose.translate(p.c.x - (p.piece.x + p.piece.sx / 2.0) / 16, p.c.y - (p.piece.y + p.piece.sy / 2.0) / 16,
                        p.c.z - (p.piece.z + p.piece.sz / 2.0) / 16);
                WoodPileRenderer.drawPiece(p.piece, buf.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)), pose.last(), l, false);
                WoodPileRenderer.drawCuts(p.piece, pose, buf);
            } else if (p.state != null) {
                pose.translate(p.c.x - 0.5, p.c.y - 0.5, p.c.z - 0.5);
                dispatcher.renderSingleBlock(p.state, pose, buf, l, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            }
            pose.popPose();
        }
        rope(e, partial, pose, buf, q, origin);
        super.render(e, yaw, partial, pose, buf, light);
    }

    // мотузка до коня (Fij, 21.11): як ванільний повідець — два перехрещені смужки з провисанням
    private static final float ROPE_W = 0.03F;
    private static final int ROPE_SEGS = 24;

    private static void rope(WoodBodyEntity e, float partial, PoseStack pose, MultiBufferSource buf, Quaternionf q, net.minecraft.world.phys.Vec3 origin) {
        org.joml.Vector3d kl = new org.joml.Vector3d();
        int id = e.ropeHorseClient(kl);
        if (id < 0) return;
        net.minecraft.world.entity.Entity h = e.level().getEntity(id);
        if (h == null) return;
        Vector3f k = q.transform(new Vector3f((float) kl.x, (float) kl.y, (float) kl.z));
        double yaw = Math.toRadians(net.minecraft.util.Mth.rotLerp(partial, h.yRotO, h.getYRot()));
        net.minecraft.world.phys.Vec3 hp = h.getPosition(partial);
        float tx = (float) (hp.x + Math.sin(yaw) * h.getBbWidth() * 0.6 - origin.x), ty = (float) (hp.y + h.getBbHeight() * 0.7 - origin.y),
                tz = (float) (hp.z - Math.cos(yaw) * h.getBbWidth() * 0.6 - origin.z);
        float dx = tx - k.x, dy = ty - k.y, dz = tz - k.z;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float sag = len < com.gigongibus.hungrycows.phys.Rope.LENGTH ? (float) Math.sqrt(com.gigongibus.hungrycows.phys.Rope.LENGTH * com.gigongibus.hungrycows.phys.Rope.LENGTH - len * len) / 2 : 0.04F;
        float hl = (float) Math.sqrt(dx * dx + dz * dz);
        float sx = hl < 1e-4 ? ROPE_W : dz / hl * ROPE_W, sz = hl < 1e-4 ? 0 : -dx / hl * ROPE_W;
        int l = LevelRenderer.getLightColor(e.level(), BlockPos.containing(origin.x + k.x + dx / 2, origin.y + k.y + dy / 2, origin.z + k.z + dz / 2));
        var vc = buf.getBuffer(RenderType.leash());
        var m = pose.last().pose();
        for (int strip = 0; strip < 2; strip++) {
            float ox = strip == 0 ? sx : 0, oy = strip == 0 ? 0 : ROPE_W, oz = strip == 0 ? sz : 0;
            for (int i = 0; i <= ROPE_SEGS; i++) {
                float t = i / (float) ROPE_SEGS;
                float x = k.x + dx * t, y = k.y + dy * t - sag * (float) Math.sin(Math.PI * t), z = k.z + dz * t;
                float c = i % 2 == (strip == 0 ? 0 : 1) ? 0.7F : 1F;
                float r = 0.5F * c, g = 0.4F * c, bl = 0.3F * c;
                vc.vertex(m, x - ox, y - oy, z - oz).color(r, g, bl, 1F).uv2(l).endVertex();
                vc.vertex(m, x + ox, y + oy, z + oz).color(r, g, bl, 1F).uv2(l).endVertex();
            }
        }
    }

    /**
     * Світло для бруска — найсвітліше з його середини, над ним і з боків (Fij, 20.11: «обрубок, що падає з деревом, дуже темний» —
     * середина обрубка була всередині пня/землі, де світла немає).
     */
    private static int light(WoodBodyEntity e, double x, double y, double z) {
        BlockPos c = BlockPos.containing(x, y, z);
        int best = LevelRenderer.getLightColor(e.level(), c);
        for (BlockPos p : new BlockPos[]{ c.above(), c.north(), c.south(), c.east(), c.west(), BlockPos.containing(x, y + 0.6, z) }) {
            if (e.level().getBlockState(p).isSolidRender(e.level(), p)) continue;
            int l = LevelRenderer.getLightColor(e.level(), p);
            best = Math.max(best & 0xFFFF, l & 0xFFFF) | Math.max(best >>> 16, l >>> 16) << 16;
        }
        return best;
    }

    @Override
    public ResourceLocation getTextureLocation(WoodBodyEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
