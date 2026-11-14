package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.ModEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * «Грибна лють» (план Fij): корова з'їла сіно з вогняним грибом — добу б'є всіх навколо місця, де з'їла (8 блоків).
 * Рекомендації Claude, Fij ще НЕ підтвердив (числа й правила — у таблиці, легко змінити):
 * не добиває (лють не смертельна), одна ціль за раз, телят не чіпає, гравця бити можна (крім творчого режиму).
 */
public class MushroomRageGoal extends Goal {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final int HIT_EVERY = 20;             // б'є раз на 1 с
    private static final double REACH = 2.2;             // б'є, коли ближче 2,2 бл.
    private static final double SPEED = 1.3;
    private static final boolean SPARE_CALVES = true;    // телят не чіпає (Fij сказав «всіх» — уточнити)
    private static final boolean NON_LETHAL = true;      // не добиває: ціль з малим здоров'ям лишає
    private static final float SPARE_HEALTH = 1.0F;      // ... після удару лишається щонайменше пів серця

    private final HungryCow cow;
    private LivingEntity target;
    private int nextHit;

    public MushroomRageGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean raging() {
        return cow.raging() && !cow.heat.unconscious;
    }

    private boolean valid(LivingEntity e) {
        if (e == null || e == cow || !e.isAlive()) return false;
        if (SPARE_CALVES && e.isBaby()) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (NON_LETHAL && e.getHealth() - cow.ramDamage() < SPARE_HEALTH) return false;   // удар не має добити
        Vec3 c = Vec3.atCenterOf(cow.rageCenter);
        return e.distanceToSqr(c) <= HungryCow.RAGE_RADIUS * HungryCow.RAGE_RADIUS;
    }

    private LivingEntity pick() {
        Vec3 c = Vec3.atCenterOf(cow.rageCenter);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : cow.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(HungryCow.RAGE_RADIUS), this::valid)) {
            double d = cow.distanceToSqr(e);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    @Override
    public boolean canUse() {
        if (!raging()) return false;
        target = pick();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return raging() && valid(target);
    }

    @Override
    public void start() {
        nextHit = 0;
        cow.status("грибна лють!");
    }

    @Override
    public void tick() {
        if (target == null) return;
        cow.getLookControl().setLookAt(target, 30F, 30F);
        if (cow.distanceTo(target) > REACH) {
            if (cow.tickCount % 10 == 0 || cow.getNavigation().isDone()) cow.getNavigation().moveTo(target, SPEED);
            return;
        }
        if (cow.tickCount >= nextHit) {
            nextHit = cow.tickCount + HIT_EVERY;
            cow.ram(target);
        }
    }

    @Override
    public void stop() {
        target = null;
        cow.getNavigation().stop();
    }
}
