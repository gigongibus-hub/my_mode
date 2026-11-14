package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Гра телят (етап 9): телята поруч (8 бл.), ситі й спокійні, удень — бігають і стрибають разом.
 */
public class PlayGoal extends Goal {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final double RANGE = 8;
    private static final int CHECK_EVERY = 200;     // раз на 10 с
    private static final double CHANCE = 0.4;       // 40%
    private static final int PLAY_TICKS = 200;      // грається 10 с
    private static final double SPEED = 1.4;        // бігає
    private static final double JUMP = 0.42;        // підстрибує
    private static final int CALM_FEAR = 10, FED = 30;

    private final HungryCow cow;
    private HungryCow mate;
    private int ticks, nextCheck = 0;

    public PlayGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean ready(HungryCow c) {
        return c.isBaby() && c.isAlive() && c.fear < CALM_FEAR && c.hunger < FED && !c.sleeping && !c.isNightTime();
    }

    @Override
    public boolean canUse() {
        if (!ready(cow) || cow.tickCount < nextCheck) return false;
        nextCheck = cow.tickCount + CHECK_EVERY;
        if (cow.getRandom().nextDouble() >= CHANCE) return false;
        mate = null;
        for (HungryCow c : cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(RANGE), c -> c != cow && ready(c))) {
            mate = c;
            break;
        }
        return mate != null;
    }

    @Override
    public boolean canContinueToUse() {
        return mate != null && ready(cow) && mate.isAlive() && ticks < PLAY_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
        cow.status("грається з " + mate.family.name);
        cow.psyche.stimulate(0.3);
    }

    @Override
    public void tick() {
        if (mate == null) return;
        ticks++;
        if (ticks % 30 == 1) {
            // то біжить до товариша, то відбігає (наздоганялки)
            Vec3 to = (ticks / 30) % 2 == 0 ? mate.position() : DefaultRandomPos.getPosAway(cow, 5, 2, mate.position());
            if (to != null) cow.getNavigation().moveTo(to.x, to.y, to.z, SPEED);
        }
        if (cow.onGround() && cow.getRandom().nextInt(25) == 0) cow.setDeltaMovement(cow.getDeltaMovement().add(0, JUMP, 0));
    }

    @Override
    public void stop() {
        mate = null;
        cow.getNavigation().stop();
    }
}
