package com.gigongibus.hungrycows.client;

import com.gigongibus.hungrycows.CowGenes;
import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.HungryCowsMod;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.CowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.animal.Cow;

// Модель корови з текстурою за мастю й візерунком (гени), але збільшена чи зменшена за геном «розмір»; без тями від спеки — лежить на боці
public class HungryCowRenderer extends CowRenderer {

    public HungryCowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    // Текстура за мастю й візерунком (24 шт.: textures/entity/cow/c{масть}_p{візерунок}.png)
    private static final ResourceLocation[] COATS = new ResourceLocation[CowGenes.COAT_COLORS * CowGenes.COAT_PATTERNS];

    static {
        for (int c = 0; c < CowGenes.COAT_COLORS; c++) {
            for (int p = 0; p < CowGenes.COAT_PATTERNS; p++) {
                COATS[c * CowGenes.COAT_PATTERNS + p] = new ResourceLocation(HungryCowsMod.MODID, "textures/entity/cow/c" + c + "_p" + p + ".png");
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(Cow cow) {
        if (cow instanceof HungryCow hungry) {
            int i = hungry.coatIndex();
            if (i >= 0 && i < COATS.length) return COATS[i];
        }
        return super.getTextureLocation(cow);
    }

    // зірочник: корова світиться в темряві (сама, світу навколо не освітлює)
    @Override
    protected int getBlockLightLevel(Cow cow, net.minecraft.core.BlockPos pos) {
        int base = super.getBlockLightLevel(cow, pos);
        return cow instanceof HungryCow hungry ? Math.max(base, hungry.starGlowLevel()) : base;
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
