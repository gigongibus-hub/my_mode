package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowBrain;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

// Бик помічає самку в тічці й іде до неї
public class BullCourtGoal extends Goal {
    private static final double SEE_RANGE = 16.0;       // з якої відстані бик помічає самку в тічці
    private static final double STOP_DISTANCE = 2.5;
    private final HungryCow cow;
    private HungryCow female;
    private int cooldown = 0;

    public BullCourtGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean ready() {
        return cow.brain.allows(CowBrain.Action.MATE) && !cow.repro.isFemale() && !cow.isBaby() && cow.getAge() == 0;
    }

    private boolean stillWanted() {
        return female != null && female.isAlive() && female.cycle.inEstrus() && !female.cycle.matedThisEstrus()
                && cow.distanceToSqr(female) < SEE_RANGE * SEE_RANGE;
    }

    @Override
    public boolean canUse() {
        if (!ready()) return false;
        if (cooldown > 0) { cooldown--; return false; }
        cooldown = 20;
        female = null;
        double bestDist = SEE_RANGE * SEE_RANGE;
        for (HungryCow c : cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(SEE_RANGE),
                c -> c != cow && c.isAlive() && !c.isBaby() && c.repro.isFemale()
                        && c.cycle.inEstrus() && !c.cycle.matedThisEstrus())) {
            double d = cow.distanceToSqr(c);
            if (d < bestDist) {
                bestDist = d;
                female = c;
            }
        }
        return female != null;
    }

    @Override
    public boolean canContinueToUse() {
        return ready() && stillWanted();
    }

    @Override
    public void tick() {
        if (female == null) return;
        cow.getLookControl().setLookAt(female, 30.0F, 30.0F);
        if (cow.distanceToSqr(female) > STOP_DISTANCE * STOP_DISTANCE) {
            if (cow.tickCount % 10 == 0) cow.getNavigation().moveTo(female, 1.0);
        } else {
            cow.getNavigation().stop();
        }
    }

    @Override
    public void stop() {
        female = null;
        cow.getNavigation().stop();
    }
}
