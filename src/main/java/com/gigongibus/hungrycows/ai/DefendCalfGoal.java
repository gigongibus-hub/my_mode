package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

// Гравець ударив теля: корова кілька секунд б'є його, потім тікає
public class DefendCalfGoal extends Goal {
    private final HungryCow cow;

    public DefendCalfGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        return cow.defendTarget() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return cow.defendTarget() != null;
    }

    @Override
    public void tick() {
        Player target = cow.defendTarget();
        if (target != null) cow.defendTick(target);
    }

    @Override
    public void stop() {
        cow.stopDefending();
    }
}
