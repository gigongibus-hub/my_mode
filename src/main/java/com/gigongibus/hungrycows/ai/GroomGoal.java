package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowCharacter.Trait;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Дотик (Fij, етап 5): спокійна, сита корова інколи вилизує шию подрузі, теляті чи матері — дружба ↑, обом приємно.
 * Частіше — мати з телям і товариські корови; з ким дружба більша, ту й обирає.
 */
public class GroomGoal extends Goal {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final int CHECK_EVERY = 300;          // вирішує раз на 15 с
    private static final double CHANCE = 0.15;           // шанс × (0,5 + товариськість)
    private static final double MOTHER_CHANCE = 0.4;     // мати з телям поруч — 40%
    private static final double RANGE = 8;               // кого вилизувати — до 8 блоків
    private static final double REACH = 1.8;             // лиже ближче 1,8 бл.
    private static final int LICK_TICKS = 100;           // лиже 5 с
    private static final int MAX_WALK = 200;             // іде до неї щонайдовше 10 с
    private static final int CALM_FEAR = 10, CALM_HUNGER = 40;
    private static final double BOND_PICK = 0.02;        // дружба +50 → у 2 рази охочіше вибирає

    private final HungryCow cow;
    private HungryCow partner;
    private int nextCheck = 0, licking, walk;

    public GroomGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean calm(HungryCow c) {
        return c.isAlive() && c.fear < CALM_FEAR && !c.sleeping && !c.heat.unconscious && !c.choking;
    }

    @Override
    public boolean canUse() {
        if (cow.tickCount < nextCheck) return false;
        nextCheck = cow.tickCount + CHECK_EVERY + cow.getRandom().nextInt(100);
        if (!calm(cow) || cow.hunger >= CALM_HUNGER || cow.isNightTime()) return false;
        HungryCow best = null;
        double bestW = 0;
        boolean family = false;
        for (HungryCow o : cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(RANGE), o -> o != cow && calm(o))) {
            boolean kin = cow.isMotherOf(o) || o.isMotherOf(cow);
            double w = (kin ? 3 : 1) * (1 + BOND_PICK * Math.max(0, cow.touch.bond(o))) * (0.5 + cow.getRandom().nextDouble());
            if (w > bestW) { bestW = w; best = o; family = kin; }
        }
        if (best == null) return false;
        double chance = family ? MOTHER_CHANCE : CHANCE * (0.5 + cow.character.fraction(Trait.SOCIABILITY));
        if (cow.getRandom().nextDouble() >= chance) return false;
        partner = best;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return partner != null && calm(cow) && calm(partner) && licking < LICK_TICKS && walk < MAX_WALK
                && cow.distanceToSqr(partner) < RANGE * RANGE * 1.5;
    }

    @Override
    public void start() {
        licking = 0;
        walk = 0;
    }

    @Override
    public void tick() {
        if (partner == null) return;
        cow.getLookControl().setLookAt(partner.getX(), partner.getEyeY(), partner.getZ());
        if (cow.distanceTo(partner) > REACH) {
            walk++;
            if (walk == 1 || walk % 10 == 0) cow.getNavigation().moveTo(partner, 0.9);
            return;
        }
        cow.getNavigation().stop();
        if (++licking % 25 == 0 && cow.level() instanceof ServerLevel s) {
            s.sendParticles(ParticleTypes.SPLASH, partner.getX(), partner.getEyeY() - 0.2, partner.getZ(), 4, 0.2, 0.1, 0.2, 0);
        }
        if (licking >= LICK_TICKS) {
            cow.touch.groomed(partner);
            partner.touch.groomed(cow);
            cow.tell(cow.family.name + " вилизала " + partner.family.name);
        }
    }

    @Override
    public void stop() {
        partner = null;
        cow.getNavigation().stop();
    }
}
