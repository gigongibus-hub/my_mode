package com.gigongibus.hungrycows;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Якість молока. Визначається після кожних пологів за раціоном корови (див. CowMilk).
 */
public enum MilkQuality {
    A("A", "Найкраще: знімає лише погані ефекти"),
    V("В", "Звичайне: знімає всі ефекти"),
    B("Б", "Погане: скорочує всі ефекти вдвічі"),
    S("С", "Найгірше: знімає лише хороші ефекти, може отруїти"),
    POISONED("отруєне", "Отруєне: завжди отруює");

    public static final float S_POISON_CHANCE = 0.20F;   // шанс отруїтися молоком С
    public static final int POISON_SECONDS = 5;          // скільки триває отруєння від молока

    public final String letter;
    public final String description;

    MilkQuality(String letter, String description) {
        this.letter = letter;
        this.description = description;
    }

    // Що відбувається з тим, хто випив молоко
    public void apply(LivingEntity drinker) {
        List<MobEffectInstance> active = new ArrayList<>(drinker.getActiveEffects());
        switch (this) {
            case A -> {
                for (MobEffectInstance e : active) {
                    if (e.getEffect().getCategory() == MobEffectCategory.HARMFUL) drinker.removeEffect(e.getEffect());
                }
            }
            case V -> drinker.removeAllEffects();
            case B -> {
                for (MobEffectInstance e : active) {
                    if (e.isInfiniteDuration()) continue;
                    drinker.removeEffect(e.getEffect());
                    drinker.addEffect(new MobEffectInstance(e.getEffect(), Math.max(1, e.getDuration() / 2),
                            e.getAmplifier(), e.isAmbient(), e.isVisible(), e.showIcon()));
                }
            }
            case S -> {
                for (MobEffectInstance e : active) {
                    if (e.getEffect().getCategory() == MobEffectCategory.BENEFICIAL) drinker.removeEffect(e.getEffect());
                }
                if (drinker.getRandom().nextFloat() < S_POISON_CHANCE) poison(drinker);
            }
            case POISONED -> poison(drinker);
        }
    }

    private static void poison(LivingEntity drinker) {
        drinker.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_SECONDS * 20));
    }

    public static MilkQuality byName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return V;
        }
    }
}
