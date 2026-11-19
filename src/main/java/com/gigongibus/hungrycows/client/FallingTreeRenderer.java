package com.gigongibus.hungrycows.client;

import com.gigongibus.hungrycows.fell.FallingTreeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Quaternionf;

/** Дерево, що падає: усі його блоки, повернуті довкола пня на кут падіння (плавно між тіками) */
public class FallingTreeRenderer extends EntityRenderer<FallingTreeEntity> {
    public FallingTreeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(FallingTreeEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buf, int light) {
        var mc = Minecraft.getInstance();
        var dispatcher = mc.getBlockRenderer();
        float a = Mth.lerp(partial, e.prevAngle, e.angle());
        Vec3 d = e.dir();
        // вісь обертання: «угору» хилиться в бік dir
        Quaternionf q = new Quaternionf().rotateAxis(a, (float) d.z, 0F, (float) -d.x);
        Vec3 origin = e.getPosition(partial);
        for (int i = 0; i < e.parts.size(); i++) {
            if (e.isBroken(i)) continue;
            FallingTreeEntity.Part p = e.parts.get(i);
            Vec3 w = origin.add(e.rotate(p.rel.add(0.5, 0.5, 0.5), a));
            int l = LevelRenderer.getLightColor(e.level(), BlockPos.containing(w));
            pose.pushPose();
            pose.mulPose(q);
            pose.translate(p.rel.x, p.rel.y, p.rel.z);
            dispatcher.renderSingleBlock(p.state, pose, buf, l, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            pose.popPose();
        }
        super.render(e, yaw, partial, pose, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(FallingTreeEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
