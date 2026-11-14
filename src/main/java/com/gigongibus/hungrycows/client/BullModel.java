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
    private static final String HEAD = "golowa";   // кістка голови в моделі Fij (жодна анімація її не крутить)
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final float DRINK_PITCH = 40F;   // п'є — голова нахилена донизу на 40° (в анімації пиття голова не рухається)

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

    // Голова: дивиться туди, куди бик (коли нічим не зайнятий); п'є — нахилена; інакше — рівно.
    // Кут ставимо ЦІЛИЙ (від початкового положення), а не додаємо: раніше додавали щокадру — голова крутилась без кінця (Fij)
    @Override
    public void setCustomAnimations(HungryCow cow, long instanceId, AnimationState<HungryCow> state) {
        super.setCustomAnimations(cow, instanceId, state);
        CoreGeoBone head = getAnimationProcessor().getBone(HEAD);
        if (head == null) return;
        float baseX = head.getInitialSnapshot().getRotX(), baseY = head.getInitialSnapshot().getRotY();
        float yaw = 0, pitch = 0;
        HungryCow.Anim a = cow.animNow();
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (a == HungryCow.Anim.DRINK) {
            pitch = DRINK_PITCH;
        } else if (a == HungryCow.Anim.NONE && data != null) {
            yaw = data.netHeadYaw();
            pitch = data.headPitch();
        }
        head.setRotY(baseY + yaw * Mth.DEG_TO_RAD);
        head.setRotX(baseX + pitch * Mth.DEG_TO_RAD);
    }
}
