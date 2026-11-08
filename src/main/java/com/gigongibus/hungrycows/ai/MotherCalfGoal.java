package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowBrain;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

// Мати йде до свого теляти, коли мозок вибрав «до рідних» (для теляти це робить FollowMotherGoal)
public class MotherCalfGoal extends Goal {
    private static final double STOP_DISTANCE = 3.0;
    private static final double START_DISTANCE = 16.0;   // мати йде до теляти, лише коли воно далі 16 бл. (було з 3)
    private final HungryCow cow;
    private LivingEntity calf;

    public MotherCalfGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (cow.isBaby() || !cow.brain.is(CowBrain.Action.FAMILY)) return false;
        calf = cow.brain.family();
        // сама йде до теляти лише тоді, коли воно далеко (теля має ходити за матір'ю, а не навпаки — Fij)
        return calf != null && cow.distanceToSqr(calf) > START_DISTANCE * START_DISTANCE;
    }

    @Override
    public boolean canContinueToUse() {
        return cow.brain.is(CowBrain.Action.FAMILY) && calf != null && calf.isAlive()
                && cow.distanceToSqr(calf) > STOP_DISTANCE * STOP_DISTANCE;
    }

    @Override
    public void tick() {
        if (calf == null) return;
        if (cow.tickCount % 10 == 0) cow.getNavigation().moveTo(calf, 1.0);
    }

    @Override
    public void stop() {
        calf = null;
        cow.getNavigation().stop();
    }
}
