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
            int l = LevelRenderer.getLightColor(e.level(), BlockPos.containing(origin.x + wc.x, origin.y + wc.y, origin.z + wc.z));
            pose.pushPose();
            pose.mulPose(q);
            if (p.kind == Part.PIECE) {
                // шматок малюється в пікселях свого місця в колоді (щоб текстура складалась)
                pose.translate(p.c.x - (p.piece.x + p.piece.sx / 2.0) / 16, p.c.y - (p.piece.y + p.piece.sy / 2.0) / 16,
                        p.c.z - (p.piece.z + p.piece.sz / 2.0) / 16);
                WoodPileRenderer.drawPiece(p.piece, buf.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)), pose.last(), l);
            } else if (p.state != null) {
                pose.translate(p.c.x - 0.5, p.c.y - 0.5, p.c.z - 0.5);
                dispatcher.renderSingleBlock(p.state, pose, buf, l, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            }
            pose.popPose();
        }
        super.render(e, yaw, partial, pose, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(WoodBodyEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
