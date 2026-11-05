package com.gigongibus.hungrycows;

import com.gigongibus.hungrycows.CowCharacter.Experience;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Події гри (не завантаження мода): зомбі нападають на корів, корови лякаються вибухів
@Mod.EventBusSubscriber(modid = HungryCowsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModForgeEvents {

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int ZOMBIE_TARGET_PRIORITY = 4;  // у зомбі: 2 гравець, 3 селянин і залізний голем, 4 корова, 5 черепаха

    // Зомбі (і кадавр, утоплений, зомбі-селянин) полюють на корів. Зомбований піглін — ні: він не ворожий сам по собі.
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof Zombie zombie && !(zombie instanceof ZombifiedPiglin)) {
            zombie.targetSelector.addGoal(ZOMBIE_TARGET_PRIORITY,
                    new NearestAttackableTargetGoal<>(zombie, HungryCow.class, true));
        }
    }

    // Вибух поруч: корови, яких він зачепив, отримують досвід «поруч вибух»
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;
        for (Entity e : event.getAffectedEntities()) {
            if (e instanceof HungryCow cow && cow.isAlive()) {
                cow.experience(Experience.EXPLOSION, HungryCow.EXPLOSION_EXPERIENCE_COOLDOWN);
            }
        }
    }
}
