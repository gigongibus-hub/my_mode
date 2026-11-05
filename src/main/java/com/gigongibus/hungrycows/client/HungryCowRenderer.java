package com.gigongibus.hungrycows.client;

import com.gigongibus.hungrycows.HungryCow;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.CowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.animal.Cow;

// Звичайна модель корови, але збільшена чи зменшена за геном «розмір»
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
}
