package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowHerd;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

// Стадо: корова, що відійшла від ватажка далі свого порогу, повертається в стадо (до ватажка, але не впритул).
// Поріг і точка повернення за рангом (Fij): вища — ближче до ватажка (8 бл.), нижча — далі (22 бл.).
// Інстинкт поза мозком; важливіші справи (страх, їжа, вода, пологи, сон) мають вищий пріоритет.
// Теля з матір'ю тримається матері (FollowMotherGoal), ватажок нікуди не повертається.
public class HerdCohesionGoal extends Goal {
    private static final double SPEED = 1.1;
    private static final double BUMP = 1.3;             // інша корова ближче цього — зупиняється
    private static final int REPATH_TICKS = 20;
    private static final int MAX_TICKS = 600;           // щонайбільше 30 с за раз
    private final HungryCow cow;
    private Vec3 point;
    private int ticks = 0;
    private int cooldown = 0;

    public HerdCohesionGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean applies() {
        if (cow.herd.isLeader() || cow.herd.leader() == null) return false;
        return !cow.isBaby() || !cow.family.hasCowMother();   // теля з матір'ю — за матір'ю
    }

    @Override
    public boolean canUse() {
        if (!applies()) return false;
        if (cooldown > 0) { cooldown--; return false; }
        cooldown = 5;   // перевіряє раз на ~10 тіків (canUse викликається раз на 2 тіки, тож не через tickCount %)
        return cow.herd.distanceToLeader() > cow.herd.farFromLeader();
    }

    @Override
    public boolean canContinueToUse() {
        return applies() && ticks < MAX_TICKS && cow.herd.distanceToLeader() > cow.herd.backNear() && !bumping();
    }

    // уже в стаді й упирається в іншу корову — досить, не пхатися
    private boolean bumping() {
        return cow.herd.distanceToLeader() < cow.herd.farFromLeader()
                && !cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(BUMP),
                c -> c != cow && c.isAlive()).isEmpty();
    }

    @Override
    public void start() {
        ticks = 0;
        point = null;
    }

    @Override
    public void tick() {
        ticks++;
        if (point == null || ticks % REPATH_TICKS == 0) {
            point = cow.herd.pointNearLeader();
            if (point != null) cow.getNavigation().moveTo(point.x, point.y, point.z, SPEED);
        }
    }

    @Override
    public void stop() {
        point = null;
        cow.getNavigation().stop();
    }
}
