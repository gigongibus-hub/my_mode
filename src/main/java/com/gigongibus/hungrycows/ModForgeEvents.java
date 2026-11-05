package com.gigongibus.hungrycows;

import com.gigongibus.hungrycows.CowCharacter.Experience;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

// Події гри (не завантаження мода): зомбі нападають на корів, корови лякаються вибухів
@Mod.EventBusSubscriber(modid = HungryCowsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModForgeEvents {

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int ZOMBIE_TARGET_PRIORITY = 4;  // у зомбі: 2 гравець, 3 селянин і залізний голем, 4 корова, 5 черепаха

    // Список «на кого нападати» (Mob.targetSelector) закритий, тому беремо його через рефлексію.
    // "f_21346_" — внутрішня назва поля targetSelector у Forge 1.20.1. Якщо поле не знайдеться,
    // гра не впаде: зомбі просто не нападатимуть на корів, а в лозі буде попередження.
    private static Field targetSelectorField = null;
    private static boolean targetSelectorFailed = false;

    private static GoalSelector targetSelector(Mob mob) {
        if (targetSelectorFailed) return null;
        try {
            if (targetSelectorField == null) {
                targetSelectorField = ObfuscationReflectionHelper.findField(Mob.class, "f_21346_");
            }
            return (GoalSelector) targetSelectorField.get(mob);
        } catch (Exception e) {
            targetSelectorFailed = true;
            LogUtils.getLogger().warn("Hungry Cows: не вдалося дати зомбі ціль «корова»", e);
            return null;
        }
    }

    // Зомбі (і кадавр, утоплений, зомбі-селянин) полюють на корів. Зомбований піглін — ні: він не ворожий сам по собі.
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof Zombie zombie && !(zombie instanceof ZombifiedPiglin)) {
            GoalSelector targets = targetSelector(zombie);
            if (targets != null) {
                targets.addGoal(ZOMBIE_TARGET_PRIORITY, new NearestAttackableTargetGoal<>(zombie, HungryCow.class, true));
            }
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
