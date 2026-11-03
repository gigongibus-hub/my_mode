package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

public class FearGoal extends Goal {
    private static final int SCARED_AT = 10;
    private static final int CALM_AT = 5;
    private final HungryCow cow;
    private LivingEntity scary;

    public FearGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    // викликати tick() кожного тіку, а не раз на два
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (cow.fear < SCARED_AT) return false;
        scary = cow.findScary(10.0);
        return scary != null;
    }

    // Шукати заново кожні 10 тіків, а не щотіку: пошук істот поруч дорогий
    @Override
    public boolean canContinueToUse() {
        if (cow.fear < CALM_AT) return false;
        if (scary == null || !scary.isAlive() || cow.tickCount % 10 == 0) {
            scary = cow.findScary(14.0);
        }
        return scary != null;
    }

    @Override
    public void start() {
        if (scary instanceof Player player) {
            cow.onScaredBy(player);
        }
    }

    @Override
    public void tick() {
        if (scary == null) return;
        // ворожа корова (довіра менша за 0) інколи атакує замість того, щоб тікати
        if (scary instanceof Player player && cow.trustOf(player) < 0 && cow.hostileBehavior(player)) {
            return;
        }
        if (cow.tickCount % 10 == 0 || cow.getNavigation().isDone()) {
            double speed = 1.0 + cow.fear / 100.0 * 1.2;
            Vec3 away = DefaultRandomPos.getPosAway(cow, 10, 4, scary.position());
            if (away != null) {
                cow.getNavigation().moveTo(away.x, away.y, away.z, speed);
            }
        }
    }

    @Override
    public void stop() {
        scary = null;
        cow.cancelCharge();
        cow.getNavigation().stop();
    }
}
