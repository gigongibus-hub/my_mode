package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

// Вночі корова йде додому (укриття) і спить: стоїть, відпочиває, гірше помічає дрібниці.
// Якщо дому немає — спить там, де є. Голод, спрага, страх і пологи важливіші (вищий пріоритет).
public class SleepGoal extends Goal {
    private static final double HOME_REACH = 2.0;       // «вдома» = ближче цього
    private static final int MAX_WALK_TICKS = 1200;     // іде додому щонайбільше 1 хв, потім спить, де є
    private static final int RECOVER_EVERY_TICKS = 20;  // втома −2 щосекунди сну
    private static final int RECOVER = 2;
    private final HungryCow cow;
    private int walkTicks = 0;
    private int ticks = 0;

    public SleepGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        return cow.isNightTime() && !cow.isBaby() && !cow.cycle.inEstrus();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        walkTicks = 0;
        ticks = 0;
    }

    @Override
    public void tick() {
        ticks++;
        BlockPos home = cow.home.home();
        if (home != null && walkTicks < MAX_WALK_TICKS
                && cow.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) > HOME_REACH * HOME_REACH) {
            walkTicks++;
            cow.sleeping = false;
            cow.resting = false;
            if (walkTicks == 1 || walkTicks % 20 == 0) {
                cow.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
            }
            return;
        }
        // спить
        cow.getNavigation().stop();
        cow.sleeping = true;
        cow.resting = true;
        if (ticks % RECOVER_EVERY_TICKS == 0) cow.fatigue = Math.max(0, cow.fatigue - RECOVER);
    }

    @Override
    public void stop() {
        cow.sleeping = false;
        cow.resting = false;
    }
}
