package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowGenes;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

// Самка в тічці сама йде до бика і стоїть біля нього 15 секунд (тоді спарювання, шанс завагітніти 60%)
public class MateGoal extends Goal {
    private static final double BULL_RANGE = 32.0;      // з якої відстані шукає бика
    private static final double REACH = 2.5;            // «разом» = ближче цього
    private static final int MAX_RUN_TICKS = 1200;      // скільки максимум іти до одного бика (1 хв)
    private final HungryCow cow;
    private HungryCow bull;
    private int together = 0;
    private int runTicks = 0;
    private int cooldown = 0;

    public MateGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean wants() {
        return cow.repro.isFemale() && !cow.isBaby() && cow.cycle.inEstrus()
                && !cow.cycle.matedThisEstrus() && !cow.repro.isPregnant();
    }

    @Override
    public boolean canUse() {
        if (!wants()) return false;
        if (cooldown > 0) { cooldown--; return false; }
        cooldown = 20;
        bull = findBull();
        return bull != null;
    }

    @Override
    public boolean canContinueToUse() {
        return wants() && bull != null && bull.isAlive() && bull.getAge() == 0 && runTicks < MAX_RUN_TICKS
                && cow.distanceToSqr(bull) < range() * range();
    }

    @Override
    public void start() {
        together = 0;
        runTicks = 0;
    }

    @Override
    public void tick() {
        if (bull == null) return;
        cow.getLookControl().setLookAt(bull, 30.0F, 30.0F);
        if (cow.distanceToSqr(bull) > REACH * REACH) {
            together = 0;
            runTicks++;
            if (runTicks == 1 || runTicks % 10 == 0) cow.getNavigation().moveTo(bull, 1.2);
            return;
        }
        cow.getNavigation().stop();
        bull.nurseUntil = bull.tickCount + 10;   // бик теж стоїть (NurseGoal: стояти на місці)
        if (++together >= cow.repro.matingTicksNeeded()) {
            cow.repro.mateWith(bull);
        }
    }

    @Override
    public void stop() {
        bull = null;
        together = 0;
        runTicks = 0;
        cow.getNavigation().stop();
    }

    // ген «тяга до розмноження»: шукає бика ближче чи далі (32 ±30%)
    private double range() {
        return BULL_RANGE * cow.gene(CowGenes.Gene.BREED_DRIVE);
    }

    private HungryCow findBull() {
        HungryCow best = null;
        double bestDist = BULL_RANGE * BULL_RANGE;
        double range = range();
        bestDist = range * range;
        for (HungryCow c : cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(range),
                c -> c != cow && c.isAlive() && !c.isBaby() && !c.repro.isFemale() && c.getAge() == 0)) {
            double d = cow.distanceToSqr(c);
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }
}
