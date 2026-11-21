package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Слух (Fij, етап 3): корова йде на звук, який полюбила (дзвін перед годуванням → корови сходяться),
 * а мати — на клич свого теляти (до 32 бл.).
 */
public class ComeToSoundGoal extends Goal {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final double ARRIVED = 3;       // дійшла — ближче 3 блоків
    private static final double SPEED = 1.1;

    private final HungryCow cow;
    private Vec3 target;

    public ComeToSoundGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (cow.hearing.comeTo == null || cow.tickCount >= cow.hearing.comeUntil || cow.sleeping || cow.heat.unconscious) return false;
        if (cow.position().distanceToSqr(cow.hearing.comeTo) < ARRIVED * ARRIVED) {
            cow.hearing.comeTo = null;
            return false;
        }
        target = cow.hearing.comeTo;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && cow.tickCount < cow.hearing.comeUntil && cow.position().distanceToSqr(target) > ARRIVED * ARRIVED;
    }

    @Override
    public void start() {
        cow.tell(cow.family.name + " чує " + cow.hearing.comeWhy + " — іде туди");
        cow.status("чує " + cow.hearing.comeWhy);
    }

    @Override
    public void tick() {
        if (target == null) return;
        if (cow.hearing.comeTo != null) target = cow.hearing.comeTo;   // звук пролунав ще раз — туди
        if (cow.tickCount % 20 == 0 || cow.getNavigation().isDone()) cow.getNavigation().moveTo(target.x, target.y, target.z, SPEED);
    }

    @Override
    public void stop() {
        target = null;
        cow.hearing.comeTo = null;
        cow.getNavigation().stop();
    }
}
