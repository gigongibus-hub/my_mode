package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

// Без тями від спеки (перегрівання 100): лежить і не рухається, поки не охолоне чи не обіллють водою.
// Забирає рух, погляд і стрибок — усі інші цілі чекають.
public class UnconsciousGoal extends Goal {
    private final HungryCow cow;

    public UnconsciousGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        return cow.heat.unconscious;
    }

    @Override
    public boolean canContinueToUse() {
        return cow.heat.unconscious;
    }

    @Override
    public void start() {
        cow.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (cow.getNavigation() != null && !cow.getNavigation().isDone()) cow.getNavigation().stop();
    }
}
