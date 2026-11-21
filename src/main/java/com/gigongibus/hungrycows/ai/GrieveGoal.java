package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

// Теля, чия мати померла, ходить біля місця її смерті, поки не забуде її (CowFamily.FORGET_SECONDS, 5 хв)
public class GrieveGoal extends Goal {
    private static final int STAY_RADIUS = 6;          // на скільки блоків від місця смерті може відійти
    private static final int WANDER_EVERY_TICKS = 100; // як часто переходить на інше місце поруч (5 сек)
    private final HungryCow cow;
    private BlockPos place;
    private int ticks = 0;

    public GrieveGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    // етап 9: доросла тужить (загинула подруга чи теля) — інколи ходить на те місце й стоїть там
    private static final double ADULT_GRIEF = 0.3;        // туга від 0,3
    private static final int ADULT_VISIT_EVERY = 1200;    // іде туди не частіше раз на хвилину
    private static final int ADULT_VISIT_TICKS = 400;     // і стоїть 20 с
    private int nextAdultVisit = 0;
    private boolean adult;

    @Override
    public boolean canUse() {
        if (cow.isBaby()) {
            place = cow.family.motherDeathPos();
            adult = false;
            return place != null;
        }
        if (cow.psyche.grief < ADULT_GRIEF || cow.psyche.griefPlace == null || cow.tickCount < nextAdultVisit) return false;
        nextAdultVisit = cow.tickCount + ADULT_VISIT_EVERY + cow.getRandom().nextInt(ADULT_VISIT_EVERY);
        place = cow.psyche.griefPlace;
        adult = true;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (adult) return ticks < ADULT_VISIT_TICKS && cow.psyche.grief >= ADULT_GRIEF;
        return cow.isBaby() && cow.family.motherDeathPos() != null;
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void tick() {
        if (place == null) return;
        ticks++;
        Vec3 center = Vec3.atBottomCenterOf(place);
        if (cow.distanceToSqr(center) > STAY_RADIUS * STAY_RADIUS) {
            if (ticks % 10 == 0) cow.getNavigation().moveTo(center.x, center.y, center.z, 1.0);
        } else if (ticks % WANDER_EVERY_TICKS == 0) {
            Vec3 near = DefaultRandomPos.getPosTowards(cow, STAY_RADIUS, 2, center, Math.PI / 2);
            if (near != null) cow.getNavigation().moveTo(near.x, near.y, near.z, 0.8);
        }
    }

    @Override
    public void stop() {
        place = null;
        cow.getNavigation().stop();
    }
}
