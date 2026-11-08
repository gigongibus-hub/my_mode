package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Слух (Fij, етап 3): різкий гучний звук (вибух, постріл, удар, ламання блока поруч) лякає одразу, навіть коли джерела не видно, —
 * корова 3 с відбігає від звуку. Звук, до якого звикла, лякає менше (CowHearing.habit) і може не викликати втечі.
 */
public class StartleGoal extends Goal {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final int AWAY_XZ = 8, AWAY_Y = 4;   // відбігає до 8 блоків від звуку
    private static final double SPEED = 1.5;

    private final HungryCow cow;

    public StartleGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        return cow.hearing.startleFrom != null && cow.tickCount < cow.hearing.startleUntil && !cow.sleeping && !cow.heat.unconscious;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        cow.tell(cow.family.name + " злякалась звуку й відбігає");
        run();
    }

    private void run() {
        Vec3 from = cow.hearing.startleFrom;
        if (from == null) return;
        Vec3 away = DefaultRandomPos.getPosAway(cow, AWAY_XZ, AWAY_Y, from);
        if (away != null) cow.getNavigation().moveTo(away.x, away.y, away.z, cow.fleeSpeed(SPEED));
    }

    @Override
    public void tick() {
        if (cow.hearing.startleFrom == null) return;
        if (cow.getNavigation().isDone()) run();
    }

    @Override
    public void stop() {
        cow.hearing.startleFrom = null;
        cow.getNavigation().stop();
    }
}
