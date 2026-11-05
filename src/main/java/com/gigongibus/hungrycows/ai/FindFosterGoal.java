package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// Теля-сирота шукає іншу самку з молоком. З шансом 50% вона його не приймає: б'є, і теля тікає шукати далі.
public class FindFosterGoal extends Goal {
    private static final double SEARCH_RANGE = 16.0;    // з якої відстані помічає самку
    private static final double REACH = 2.0;
    private static final float REJECT_CHANCE = 0.5F;    // шанс, що самка не прийме теля, яке просто забуло матір
    private static final float REJECT_CHANCE_DEAD_MOTHER = 0.7F; // шанс, що не прийме теля, чия мати померла
    private static final float REJECT_DAMAGE = 1.0F;    // удар самки (пів серця)
    private static final int FLEE_TICKS = 60;           // скільки теля тікає після удару
    private static final int MAX_RUN_TICKS = 400;
    private static final int RETRY_TICKS = 200;         // пауза перед наступним пошуком (10 сек)
    private final HungryCow cow;
    private final Set<UUID> rejectedBy = new HashSet<>();   // хто вже не прийняв (не зберігається)
    private HungryCow target;
    private int runTicks = 0;
    private int fleeTicks = 0;
    private int nextTry = 0;

    public FindFosterGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (!cow.isBaby() || !cow.family.isOrphan() || cow.tickCount < nextTry) return false;
        nextTry = cow.tickCount + 40;
        target = findFemale();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (fleeTicks > 0) return true;
        return target != null && target.isAlive() && cow.family.isOrphan() && runTicks < MAX_RUN_TICKS;
    }

    @Override
    public void start() {
        runTicks = 0;
        fleeTicks = 0;
    }

    @Override
    public void tick() {
        if (fleeTicks > 0) {
            fleeTicks--;
            return;
        }
        if (target == null) return;
        runTicks++;
        if (cow.distanceToSqr(target) > REACH * REACH) {
            if (runTicks == 1 || runTicks % 10 == 0) cow.getNavigation().moveTo(target, 1.0);
            return;
        }
        float reject = cow.family.lostMotherToDeath() ? REJECT_CHANCE_DEAD_MOTHER : REJECT_CHANCE;
        if (cow.getRandom().nextFloat() < reject) {
            // не прийняла: б'є, теля тікає
            rejectedBy.add(target.getUUID());
            cow.hurt(target.damageSources().mobAttack(target), REJECT_DAMAGE);
            target.playSound(SoundEvents.COW_HURT, 1.0F, 0.8F);
            cow.tell(target.family.name + " не прийняла теля " + cow.family.name);
            Vec3 away = DefaultRandomPos.getPosAway(cow, 8, 4, target.position());
            if (away != null) cow.getNavigation().moveTo(away.x, away.y, away.z, 1.5);
            fleeTicks = FLEE_TICKS;
            target = null;
        } else {
            cow.family.adoptBy(target);
            target = null;
        }
    }

    @Override
    public void stop() {
        target = null;
        runTicks = 0;
        fleeTicks = 0;
        nextTry = cow.tickCount + RETRY_TICKS;
    }

    private HungryCow findFemale() {
        HungryCow best = null;
        double bestDist = SEARCH_RANGE * SEARCH_RANGE;
        for (HungryCow c : cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(SEARCH_RANGE),
                c -> c != cow && c.isAlive() && !c.isBaby() && c.repro.isFemale() && c.milk.isLactating()
                        && !rejectedBy.contains(c.getUUID()))) {
            double d = cow.distanceToSqr(c);
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }
}
