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

    public static class CowFriendEffect extends MobEffect {
        public CowFriendEffect() {
            super(MobEffectCategory.BENEFICIAL, 0xF2D7A6);
        }
    }
}
