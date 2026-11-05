package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

// Корова відходить від черемші (Azure Bluet)
public class AvoidBluetGoal extends Goal {
    private static final int RADIUS = 3;
    private final HungryCow cow;
    private BlockPos bluet;
    private int cooldown = 0;

    public AvoidBluetGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    // правило мода: кожна ціль оновлюється щотіку
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        cooldown = 20;
        bluet = findBluet();
        return bluet != null;
    }

    @Override
    public boolean canContinueToUse() {
        return bluet != null && !cow.getNavigation().isDone();
    }

    @Override
    public void start() {
        Vec3 away = DefaultRandomPos.getPosAway(cow, 8, 3, Vec3.atCenterOf(bluet));
        if (away != null) {
            cow.getNavigation().moveTo(away.x, away.y, away.z, 1.0);
        } else {
            bluet = null;
        }
    }

    @Override
    public void stop() {
        bluet = null;
    }

    private BlockPos findBluet() {
        BlockPos origin = cow.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(
                origin.offset(-RADIUS, -1, -RADIUS), origin.offset(RADIUS, 1, RADIUS))) {
            if (cow.level().getBlockState(p).is(Blocks.AZURE_BLUET)) {
                return p.immutable();
            }
        }
        return null;
    }
}
