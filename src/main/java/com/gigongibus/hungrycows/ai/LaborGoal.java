package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.EnumSet;

// Корова під час пологів стоїть на місці (перебиває навіть страх)
public class LaborGoal extends Goal {
    private final HungryCow cow;

    public LaborGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    // викликати tick() кожного тіку, а не раз на два
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        return cow.repro.inLabor();
    }

    @Override
    public boolean canContinueToUse() {
        return cow.repro.inLabor();
    }

    @Override
    public void start() {
        cow.getNavigation().stop();
    }

    @Override
    public void tick() {
        cow.repro.laborTick();
    }
}
