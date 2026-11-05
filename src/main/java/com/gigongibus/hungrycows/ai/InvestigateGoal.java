package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowBrain;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

// Допитливість: корова обережно підходить до незнайомого гравця (не друга й не ворога), зупиняється за 4 блоки й роздивляється
public class InvestigateGoal extends Goal {
    private static final double STOP_DISTANCE = 4.0;
    private static final double SPEED = 0.7;            // повільно, обережно
    private static final int MAX_TICKS = 300;           // роздивляється до 15 с
    private final HungryCow cow;
    private Player target;
    private int ticks = 0;

    public InvestigateGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (!cow.brain.is(CowBrain.Action.CURIOUS)) return false;
        target = cow.brain.stranger();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return cow.brain.is(CowBrain.Action.CURIOUS) && target != null && target.isAlive()
                && !target.isSpectator() && ticks < MAX_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void tick() {
        if (target == null) return;
        ticks++;
        cow.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (cow.distanceToSqr(target) > STOP_DISTANCE * STOP_DISTANCE) {
            if (ticks == 1 || ticks % 10 == 0) cow.getNavigation().moveTo(target, SPEED);
        } else {
            cow.getNavigation().stop();
        }
    }

    @Override
    public void stop() {
        target = null;
        cow.getNavigation().stop();
    }
}
