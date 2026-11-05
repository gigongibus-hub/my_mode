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

    @Override
    public boolean canUse() {
        if (!cow.isBaby()) return false;
        place = cow.family.motherDeathPos();
        return place != null;
    }

    @Override
    public boolean canContinueToUse() {
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
