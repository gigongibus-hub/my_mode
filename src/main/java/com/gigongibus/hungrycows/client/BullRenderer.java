package com.gigongibus.hungrycows.client;

import com.gigongibus.hungrycows.HungryCow;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Рендерер дорослого бика: модель Fij, розмір за геном, без тями — лежить на боці */
public class BullRenderer extends GeoEntityRenderer<HungryCow> {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    // Модель Fij велика (1,25 × 2,1 × 3 бл.; ванільна корова ≈ 0,9 × 1,4 × 1,5). Множник розміру — спитати Fij
    public static final float MODEL_SCALE = 0.75F;

    public BullRenderer(EntityRendererProvider.Context context) {
        super(context, new BullModel());
        this.shadowRadius = 0.9F;
    }

    @Override
    public void render(HungryCow cow, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float s = MODEL_SCALE * cow.sizeScale();
        this.scaleWidth = s;
        this.scaleHeight = s;
        super.render(cow, yaw, partialTick, pose, buffers, light);
    }

    @Override
    protected void applyRotations(HungryCow cow, PoseStack pose, float ageInTicks, float rotationYaw, float partialTick) {
        super.applyRotations(cow, pose, ageInTicks, rotationYaw, partialTick);
        if (cow.isUnconsciousVisual()) pose.mulPose(Axis.ZP.rotationDegrees(90F));   // без тями від спеки — на боці
    }
}
