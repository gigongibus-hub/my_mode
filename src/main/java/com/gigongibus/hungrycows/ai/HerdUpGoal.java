package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowBrain;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

// Самка в проеструсі й тічці тримається біля інших корів (у тічці «пристає»: то бігає, то йде повільно).
// У метеструсі те саме, але рідше: усе спадає.
public class HerdUpGoal extends Goal {
    private static final double SEE_RANGE = 16.0;
    private static final double STOP_DISTANCE = 2.0;
    private static final double LEADER_STOP = 4.0;      // до ватажка підходить на 4 блоки
    private static final int RUN_TICKS = 300;           // скільки триває один «підхід» (15 сек)
    private static final int PAUSE_PROESTRUS = 200;     // пауза між підходами: проеструс 10 с ...
    private static final int PAUSE_ESTRUS = 60;         // ... тічка 3 с ...
    private static final int PAUSE_METESTRUS = 600;     // ... метеструс 30 с
    private static final int SPEED_SWITCH_TICKS = 60;   // у тічку кожні 3 с міняє: біжить чи повільно йде
    private static final double SPEED_RUN = 1.4;
    private static final double SPEED_SLOW = 0.6;
    private static final double SPEED_NORMAL = 1.0;
    private final HungryCow cow;
    private HungryCow buddy;
    private int ticks = 0;
    private int pauseUntil = 0;

    public HerdUpGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean active() {
        if (CowBrain.enabled) return cow.brain.is(CowBrain.Action.HERD);   // вирішує мозок (будь-яка корова)
        return cow.repro.isFemale() && !cow.isBaby()
                && (cow.cycle.inProestrus() || cow.cycle.inEstrus() || cow.cycle.inMetestrus());
    }

    @Override
    public boolean canUse() {
        if (!active() || cow.tickCount < pauseUntil) return false;
        buddy = null;
        // до ватажка стада (якщо вона є і це не сама корова), інакше до найближчої корови
        HungryCow leader = cow.herd.leader();
        if (leader != null && leader != cow && cow.distanceToSqr(leader) < 32 * 32) {
            buddy = leader;
            return true;
        }
        double bestDist = SEE_RANGE * SEE_RANGE;
        for (HungryCow c : cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(SEE_RANGE),
                c -> c != cow && c.isAlive() && !c.isBaby())) {
            double d = cow.distanceToSqr(c);
            if (d < bestDist) {
                bestDist = d;
                buddy = c;
            }
        }
        if (buddy == null) pauseUntil = cow.tickCount + 100;
        return buddy != null;
    }

    @Override
    public boolean canContinueToUse() {
        return active() && buddy != null && buddy.isAlive() && ticks < RUN_TICKS
                && cow.distanceToSqr(buddy) < 32 * 32;
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void tick() {
        if (buddy == null) return;
        ticks++;
        double stop = buddy == cow.herd.leader() ? LEADER_STOP : STOP_DISTANCE;   // до ватажка — не впритул
        if (cow.distanceToSqr(buddy) <= stop * stop) {
            cow.getNavigation().stop();
            return;
        }
        if (ticks == 1 || ticks % 10 == 0) {
            double speed = SPEED_NORMAL;
            if (cow.cycle.inEstrus()) speed = (ticks / SPEED_SWITCH_TICKS) % 2 == 0 ? SPEED_RUN : SPEED_SLOW;
            cow.getNavigation().moveTo(buddy, speed);
        }
    }

    @Override
    public void stop() {
        buddy = null;
        cow.getNavigation().stop();
        int pause = cow.cycle.inEstrus() ? PAUSE_ESTRUS : cow.cycle.inMetestrus() ? PAUSE_METESTRUS : PAUSE_PROESTRUS;
        pauseUntil = cow.tickCount + pause;
    }
}
