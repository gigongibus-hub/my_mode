package com.gigongibus.hungrycows.client;

import com.gigongibus.hungrycows.HungryCow;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Дорослий бик — модель бика Fij (GeckoLib), корови й телята — звична модель корови з мастями */
public class HungryCowDispatchRenderer extends EntityRenderer<HungryCow> {
    private final HungryCowRenderer cow;
    private final BullRenderer bull;

    public HungryCowDispatchRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.cow = new HungryCowRenderer(context);
        this.bull = new BullRenderer(context);
        this.shadowRadius = 0.7F;
    }

    @Override
    public void render(HungryCow e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (e.bullModel()) bull.render(e, yaw, partialTick, pose, buffers, light);
        else cow.render(e, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HungryCow e) {
        return e.bullModel() ? bull.getTextureLocation(e) : cow.getTextureLocation(e);
    }

    // зірочник: корова (і бик) світиться в темряві
    @Override
    protected int getBlockLightLevel(HungryCow e, BlockPos pos) {
        return Math.max(super.getBlockLightLevel(e, pos), e.starGlowLevel());
    }
}
