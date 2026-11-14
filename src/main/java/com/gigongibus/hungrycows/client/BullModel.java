package com.gigongibus.hungrycows.client;

import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.HungryCowsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/** Модель бика Fij (Blockbench → GeckoLib): файли в assets/hungrycows/geo, animations, textures/entity */
public class BullModel extends GeoModel<HungryCow> {
    private static final ResourceLocation MODEL = new ResourceLocation(HungryCowsMod.MODID, "geo/bull.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(HungryCowsMod.MODID, "textures/entity/bull.png");
    private static final ResourceLocation ANIMATIONS = new ResourceLocation(HungryCowsMod.MODID, "animations/bull.animation.json");
    private static final String HEAD = "golowa";   // кістка голови в моделі Fij

    @Override
    public ResourceLocation getModelResource(HungryCow cow) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(HungryCow cow) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(HungryCow cow) {
        return ANIMATIONS;
    }

    // Голова повертається туди, куди дивиться бик (крім анімацій, де голову веде сама анімація)
    @Override
    public void setCustomAnimations(HungryCow cow, long instanceId, AnimationState<HungryCow> state) {
        super.setCustomAnimations(cow, instanceId, state);
        if (cow.animNow() != HungryCow.Anim.NONE) return;
        CoreGeoBone head = getAnimationProcessor().getBone(HEAD);
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head == null || data == null) return;
        head.setRotY(head.getRotY() + data.netHeadYaw() * Mth.DEG_TO_RAD);
        head.setRotX(head.getRotX() + data.headPitch() * Mth.DEG_TO_RAD);
    }
}
