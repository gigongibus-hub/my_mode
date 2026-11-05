package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowMemory;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

// Пам'ять місць: корова сама йде з місця, де її били чи лякали (інстинкт, поза мозком)
public class AvoidBadPlaceGoal extends Goal {
    private static final int CHECK_EVERY = 40;   // перевіряє раз на 2 с
    private final HungryCow cow;
    private int cooldown = 0;

    public AvoidBadPlaceGoal(HungryCow cow) {
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
        cooldown = CHECK_EVERY / 2;   // canUse викликається раз на 2 тіки
        return cow.memory.placeValue(cow.blockPosition()) < CowMemory.BAD_PLACE;
    }

    @Override
    public boolean canContinueToUse() {
        return !cow.getNavigation().isDone();
    }

    @Override
    public void start() {
        BlockPos center = CowMemory.cellCenter(cow.blockPosition());
        Vec3 away = DefaultRandomPos.getPosAway(cow, 10, 3, Vec3.atCenterOf(center));
        if (away != null) cow.getNavigation().moveTo(away.x, away.y, away.z, 1.0);
    }

    @Override
    public void stop() {
        cow.getNavigation().stop();
    }
}
