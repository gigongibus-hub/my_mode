package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowFamily;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

// Теля тримається біля своєї матері (корови або гравця, що його вигодував), якщо вона ближче 25 блоків
public class FollowMotherGoal extends Goal {
    private static final double START_DISTANCE = 5.0;   // далі цього теля йде до матері
    private static final double STOP_DISTANCE = 3.0;    // ближче цього зупиняється
    private final HungryCow cow;
    private LivingEntity mother;

    public FollowMotherGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    private LivingEntity findMother() {
        HungryCow m = cow.family.findMother();
        if (m != null) return m;
        Player p = cow.family.findPlayerMother();
        return p != null && !p.isSpectator() ? p : null;
    }

    private boolean inRange(double dist) {
        double d = cow.distanceToSqr(mother);
        return d <= CowFamily.MOTHER_RANGE * CowFamily.MOTHER_RANGE && d > dist * dist;
    }

    @Override
    public boolean canUse() {
        if (!cow.isBaby()) return false;
        mother = findMother();
        return mother != null && inRange(START_DISTANCE);
    }

    @Override
    public boolean canContinueToUse() {
        return cow.isBaby() && mother != null && mother.isAlive() && inRange(STOP_DISTANCE);
    }

    @Override
    public void tick() {
        if (cow.tickCount % 10 == 0) cow.getNavigation().moveTo(mother, 1.1);
    }

    @Override
    public void stop() {
        mother = null;
        cow.getNavigation().stop();
    }
}
