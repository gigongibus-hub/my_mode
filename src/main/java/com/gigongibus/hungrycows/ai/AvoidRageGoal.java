package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Fij: корови не підходять до корови в грибній люті, а тікають від неї. Бачить (або вона ближче 4 бл.) корову в люті
 * ближче 10 бл. — відбігає подалі; важливіше за їжу, стадо й прогулянки (черга 4).
 */
public class AvoidRageGoal extends Goal {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final double NOTICE = 10;       // помічає корову в люті ближче 10 бл. ...
    private static final double NOTICE_BLIND = 4;  // ... а ближче 4 — навіть не бачачи (чує)
    private static final double SAFE = 13;         // відбігає, поки не буде далі 13 бл.
    private static final double SPEED = 1.25;
    private static final int CHECK_EVERY = 10;     // перевіряє раз на 0,5 с

    private final HungryCow cow;
    private HungryCow rager;
    private int cooldown = 0;

    public AvoidRageGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private HungryCow find() {
        HungryCow best = null;
        double bestD = NOTICE * NOTICE;
        for (HungryCow o : cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(NOTICE),
                o -> o != cow && o.isAlive() && o.raging())) {
            double d = cow.distanceToSqr(o);
            if (d < bestD && (d < NOTICE_BLIND * NOTICE_BLIND || cow.vision.canSee(o))) { bestD = d; best = o; }
        }
        return best;
    }

    @Override
    public boolean canUse() {
        if (cow.raging() || cow.heat.unconscious) return false;
        if (cooldown > 0) { cooldown--; return false; }
        cooldown = CHECK_EVERY / 2;   // canUse — раз на 2 тіки
        rager = find();
        return rager != null;
    }

    @Override
    public boolean canContinueToUse() {
        return rager != null && rager.isAlive() && rager.raging() && cow.distanceToSqr(rager) < SAFE * SAFE;
    }

    @Override
    public void start() {
        flee();
    }

    private void flee() {
        if (rager == null) return;
        Vec3 away = DefaultRandomPos.getPosAway(cow, 16, 4, rager.position());
        if (away != null) cow.getNavigation().moveTo(away.x, away.y, away.z, SPEED);
    }

    @Override
    public void tick() {
        if (rager == null) return;
        if (cow.getNavigation().isDone() || cow.tickCount % 20 == 0 && cow.distanceToSqr(rager) < 25) flee();
    }

    @Override
    public void stop() {
        rager = null;
        cow.getNavigation().stop();
    }
}
