package com.gigongibus.hungrycows.client;

import com.gigongibus.hungrycows.HungryCow;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.CowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.animal.Cow;

// Звичайна модель корови, але збільшена чи зменшена за геном «розмір»; без тями від спеки — лежить на боці
public class HungryCowRenderer extends CowRenderer {

    public HungryCowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void scale(Cow cow, PoseStack poseStack, float partialTick) {
        if (cow instanceof HungryCow hungry) {
            float s = hungry.sizeScale();
            poseStack.scale(s, s, s);
        }
    }

    @Override
    protected void setupRotations(Cow cow, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        super.setupRotations(cow, poseStack, ageInTicks, rotationYaw, partialTick);
        if (cow instanceof HungryCow hungry && hungry.isUnconsciousVisual() && cow.deathTime == 0) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));   // на боці, як ванільна тварина, що падає
        }
    }
}
