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
}
