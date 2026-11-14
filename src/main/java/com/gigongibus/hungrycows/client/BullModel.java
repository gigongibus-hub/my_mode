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
    // кістки моделі Fij: golowa = шия + голова разом (жодна анімація її не крутить), golowabez = лише голова (її крутять
    // анімації «спокій 1» і «підготовка»). Погляд — лише головою (Fij: «шия й голова повинні бути розділені»), пиття — усією шиєю
    private static final String NECK = "golowa";
    private static final String HEAD = "golowabez";
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final float DRINK_PITCH = 40F;   // п'є — шия з головою донизу на 40° (в анімації пиття голова не рухається)
    public static final float LOOK_PITCH_MAX = 30F; // головою вгору/вниз не більше 30°

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

    // Кути ставимо ЦІЛІ (від початкового положення), а не додаємо: раніше додавали щокадру — голова крутилась без кінця.
    // У GeckoLib кут нахилу навпаки: «донизу» = мінус (раніше бик, п'ючи, задирав голову — Fij)
    @Override
    public void setCustomAnimations(HungryCow cow, long instanceId, AnimationState<HungryCow> state) {
        super.setCustomAnimations(cow, instanceId, state);
        CoreGeoBone neck = getAnimationProcessor().getBone(NECK);
        CoreGeoBone head = getAnimationProcessor().getBone(HEAD);
        HungryCow.Anim a = cow.animNow();
        if (neck != null) {
            neck.setRotY(neck.getInitialSnapshot().getRotY());
            neck.setRotX(neck.getInitialSnapshot().getRotX() - (a == HungryCow.Anim.DRINK ? DRINK_PITCH * Mth.DEG_TO_RAD : 0));
        }
        // голову крутить анімація (спокій 1, підготовка) — не заважаємо; інакше — дивиться, куди бик
        boolean animatedHead = a == HungryCow.Anim.PREPARE || cow.tickCount < cow.idleAnimUntil;
        if (head == null || animatedHead) return;
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        float yaw = 0, pitch = 0;
        if (a == HungryCow.Anim.NONE && data != null) {
            yaw = data.netHeadYaw();
            pitch = Mth.clamp(data.headPitch(), -LOOK_PITCH_MAX, LOOK_PITCH_MAX);
        }
        head.setRotY(head.getInitialSnapshot().getRotY() + yaw * Mth.DEG_TO_RAD);
        head.setRotX(head.getInitialSnapshot().getRotX() + pitch * Mth.DEG_TO_RAD);
    }
}
