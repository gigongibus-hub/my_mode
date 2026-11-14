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
    // Модель Fij: 1,25 × 2,1 × 3 бл. Fij: «трохи більший за гравця» (гравець 1,8) — 0,95 → ≈ 2 бл. заввишки (× ген розміру).
    // Хітбокс (для ходьби, дверей) лишається коров'ячим — інакше бик не пролазив би в проходи 1 бл.
    public static final float MODEL_SCALE = 0.95F;

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
