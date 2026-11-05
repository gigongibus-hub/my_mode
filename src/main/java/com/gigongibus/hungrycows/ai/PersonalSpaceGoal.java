package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

// Особистий простір: якщо корова стоїть майже впритул до іншої, слабша (нижчий ранг) трохи відходить
public class PersonalSpaceGoal extends Goal {
    private static final double TOO_CLOSE = 1.0;        // ближче цього — затісно
    private static final int CHECK_EVERY = 40;          // перевіряє раз на 2 с
    private final HungryCow cow;
    private int cooldown = 0;
    private HungryCow other;

    public PersonalSpaceGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        cooldown = CHECK_EVERY / 2;
        if (!cow.getNavigation().isDone() || cow.sleeping) return false;
        List<HungryCow> near = cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(TOO_CLOSE),
                c -> c != cow && c.isAlive() && c.distanceToSqr(cow) < TOO_CLOSE * TOO_CLOSE);
        other = null;
        for (HungryCow c : near) {
            // відходить слабша; теля від своєї матері не відходить
            if (cow.isBaby() && c.getUUID().equals(cow.family.motherIdOrNull())) continue;
            if (com.gigongibus.hungrycows.CowHerd.dominance(c) >= com.gigongibus.hungrycows.CowHerd.dominance(cow)) {
                other = c;
                break;
            }
        }
        return other != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !cow.getNavigation().isDone();
    }

    @Override
    public void start() {
        if (other == null) return;
        Vec3 away = DefaultRandomPos.getPosAway(cow, 3, 1, other.position());
        if (away != null) cow.getNavigation().moveTo(away.x, away.y, away.z, 0.8);
    }

    @Override
    public void stop() {
        other = null;
    }
}
