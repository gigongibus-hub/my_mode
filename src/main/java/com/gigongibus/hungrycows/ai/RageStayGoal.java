package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Грибна лють (Fij): поки діє, корова не пасеться як звичайна, а ходить біля місця, де з'їла вогняний гриб
 * (до 4 бл.), — і б'є кожного, хто підійде (MushroomRageGoal). Їжа, вода й сон важливіші (вища черга).
 */
public class RageStayGoal extends Goal {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final double SPEED = 0.9;           // ходить неспокійно, трохи швидше за прогулянку
    private static final int PAUSE_MIN = 40, PAUSE_MAX = 100;   // між кроками стоїть 2–5 с

    private final HungryCow cow;
    private int nextMove;

    public RageStayGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        return cow.raging() && !cow.isBaby();
    }

    @Override
    public boolean canContinueToUse() {
        return cow.raging();
    }

    @Override
    public void start() {
        nextMove = 0;
    }

    @Override
    public void tick() {
        BlockPos c = cow.rageCenter;
        if (c == null) return;
        Vec3 center = Vec3.atBottomCenterOf(c);
        boolean far = cow.position().distanceToSqr(center) > HungryCow.RAGE_STAY * HungryCow.RAGE_STAY;
        if (far) {   // відійшла — повертається
            if (cow.getNavigation().isDone() || cow.tickCount % 20 == 0) cow.getNavigation().moveTo(center.x, center.y, center.z, SPEED);
            return;
        }
        if (cow.tickCount < nextMove || !cow.getNavigation().isDone()) return;
        nextMove = cow.tickCount + PAUSE_MIN + cow.getRandom().nextInt(PAUSE_MAX - PAUSE_MIN + 1);
        double a = cow.getRandom().nextDouble() * Math.PI * 2, r = cow.getRandom().nextDouble() * HungryCow.RAGE_STAY;
        cow.getNavigation().moveTo(center.x + Math.cos(a) * r, center.y, center.z + Math.sin(a) * r, SPEED);
    }

    @Override
    public void stop() {
        cow.getNavigation().stop();
    }
}
