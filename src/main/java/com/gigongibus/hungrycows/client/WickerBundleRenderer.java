package com.gigongibus.hungrycows.client;

import com.gigongibus.hungrycows.salt.Salt;
import com.gigongibus.hungrycows.salt.WickerBundleBlock;
import com.gigongibus.hungrycows.salt.WickerBundleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Пучки лози на землі: кожен — пласка картинка предмета (своя стадія) у своєму місці й повороті (Fij: «розкидані рандомно») */
public class WickerBundleRenderer implements BlockEntityRenderer<WickerBundleBlockEntity> {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final float SIZE = 0.5F;        // пучок 0,5 бл. (8 пікс.) — удвічі менший за блок (Fij)
    public static final float LIFT = 0.01F;       // над землею, щоб не мерехтів
    public static final float STACK_STEP = 0.004F; // кожен наступний трохи вище — накладені не мерехтять

    public WickerBundleRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(WickerBundleBlockEntity be, float partial, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        var list = be.pieces();
        if (list.isEmpty()) {   // ще не прийшли дані з сервера — один посередині
            draw(be, be.getBlockState().getValue(WickerBundleBlock.STAGE), 0.5F, 0.5F, 0, 0, pose, buf, light);
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            var p = list.get(i);
            draw(be, p.stage, p.x, p.z, p.rot, i, pose, buf, light);
        }
    }

    private void draw(WickerBundleBlockEntity be, WickerBundleBlock.Stage stage, float x, float z, float rot, int i,
                      PoseStack pose, MultiBufferSource buf, int light) {
        pose.pushPose();
        pose.translate(x, LIFT + i * STACK_STEP, z);
        pose.mulPose(Axis.YP.rotationDegrees(rot));
        pose.mulPose(Axis.XP.rotationDegrees(90));   // лягає на землю
        pose.scale(SIZE, SIZE, SIZE);
        Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(Salt.bundleItem(stage)), ItemDisplayContext.FIXED,
                light, OverlayTexture.NO_OVERLAY, pose, buf, be.getLevel(), (int) be.getBlockPos().asLong() + i);
        pose.popPose();
    }
}
