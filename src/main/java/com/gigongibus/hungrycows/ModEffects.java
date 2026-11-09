package com.gigongibus.hungrycows;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, HungryCowsMod.MODID);

    // «Друг корови»: одне відро забирає 10 одиниць молока замість 25, корови бояться вдвічі менше
    public static final RegistryObject<MobEffect> COW_FRIEND = EFFECTS.register("cow_friend", CowFriendEffect::new);

    // «Грибна лють» (план Fij): корова з'їла сіно з вогняним грибом — 1 доба б'є всіх навколо місця, де з'їла
    public static final RegistryObject<MobEffect> MUSHROOM_RAGE = EFFECTS.register("mushroom_rage", MushroomRageEffect::new);

    public static class MushroomRageEffect extends MobEffect {
        public MushroomRageEffect() {
            super(MobEffectCategory.HARMFUL, 0xE0561B);
        }
    }

    public static class CowFriendEffect extends MobEffect {
        public CowFriendEffect() {
            super(MobEffectCategory.BENEFICIAL, 0xF2D7A6);
        }
    }
}
