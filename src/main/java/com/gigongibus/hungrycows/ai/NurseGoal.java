package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

// Мати стоїть і відпочиває, поки теля п'є молоко (теля ставить cow.nurseUntil)
public class NurseGoal extends Goal {
    private static final int RECOVER_PER_SECOND = 5;   // як у RestGoal
    private final HungryCow cow;
    private int ticks = 0;

    public NurseGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        return cow.tickCount < cow.nurseUntil;
    }

    @Override
    public void start() {
        ticks = 0;
        cow.getNavigation().stop();
    }

    @Override
    public void tick() {
        cow.getNavigation().stop();
        if (++ticks % 20 == 0) {
            cow.fatigue = Math.max(0, cow.fatigue - RECOVER_PER_SECOND);
        }
    }
}
