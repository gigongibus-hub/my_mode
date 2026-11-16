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

/** Колода на верстаку: та сама колода, що поклали, лежить уздовж (менша, на столі) */
public class WorkpieceRenderer implements BlockEntityRenderer<WorkpieceBlockEntity> {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final float SIZE = 0.62F;      // колода — 62% блока

    public WorkpieceRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(WorkpieceBlockEntity be, float partial, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        if (!(be.log() instanceof BlockItem bi)) return;
        BlockState s = bi.getBlock().defaultBlockState();
        if (s.hasProperty(RotatedPillarBlock.AXIS)) s = s.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(15));
        pose.scale(SIZE, SIZE, SIZE);
        pose.translate(-0.5, 0, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(s, pose, buf, light, OverlayTexture.NO_OVERLAY,
                net.minecraftforge.client.model.data.ModelData.EMPTY, null);
        pose.popPose();
    }
}
