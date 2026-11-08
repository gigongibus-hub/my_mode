package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Смак (Fij, етап 4): корова хоче солі (CowTaste.salt ≥ 40) — іде до соляного лизунця, який бачить, чує носом або пам'ятає,
 * і 4 с лиже його: бажання солі −60, +1 серце, задоволення.
 */
public class LickSaltGoal extends Goal {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final int SEARCH_RADIUS = 16, SEARCH_HEIGHT = 3;   // шукає лизунець до 16 блоків
    private static final int SEARCH_EVERY = 100;                       // раз на 5 с
    private static final int LICK_TICKS = 80;                          // лиже 4 с
    private static final int MAX_WALK = 600;                           // іде щонайдовше 30 с
    private static final double REACH = 2.2;                           // лизати можна ближче 2,2 бл. до середини блока

    private final HungryCow cow;
    private BlockPos lick;            // до якого йде
    private BlockPos remembered;      // де бачила лизунець (не зберігається)
    private int nextSearch = 0;
    private int walk, licking;

    public LickSaltGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean isLick(BlockPos p) {
        return p != null && cow.level().getBlockState(p).is(ModBlocks.SALT_LICK.get());
    }

    @Override
    public boolean canUse() {
        if (cow.isBaby() || cow.sleeping || !cow.taste.wantsSalt() || !cow.onGround()) return false;
        if (cow.tickCount < nextSearch) return false;
        nextSearch = cow.tickCount + SEARCH_EVERY;
        lick = isLick(remembered) ? remembered : find();
        if (lick == null) return false;
        Path path = cow.getNavigation().createPath(lick, 1);
        if (path == null || !path.canReach()) return false;
        remembered = lick;
        return true;
    }

    // Найближчий лизунець, який видно або чути носом
    private BlockPos find() {
        BlockPos o = cow.blockPosition(), best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-SEARCH_RADIUS, -SEARCH_HEIGHT, -SEARCH_RADIUS), o.offset(SEARCH_RADIUS, SEARCH_HEIGHT, SEARCH_RADIUS))) {
            if (!cow.level().getBlockState(p).is(ModBlocks.SALT_LICK.get())) continue;
            double d = p.distSqr(o);
            if (d >= bd) continue;
            if (!cow.vision.canSeeBlock(p, Vec3.atCenterOf(p)) && !cow.smell.smellsBlock(p)) continue;
            bd = d;
            best = p.immutable();
        }
        return best;
    }

    @Override
    public boolean canContinueToUse() {
        return isLick(lick) && walk < MAX_WALK && licking < LICK_TICKS && !cow.sleeping;
    }

    @Override
    public void start() {
        walk = 0;
        licking = 0;
        cow.tell(cow.family.name + " хоче солі — іде до лизунця");
    }

    @Override
    public void tick() {
        if (lick == null) return;
        Vec3 c = Vec3.atCenterOf(lick);
        if (cow.distanceToSqr(c) > REACH * REACH) {
            walk++;
            if (walk == 1 || walk % 20 == 0) {
                Path path = cow.getNavigation().createPath(lick, 1);
                if (path != null) cow.getNavigation().moveTo(path, 1.0);
            }
            return;
        }
        cow.getNavigation().stop();
        cow.getLookControl().setLookAt(c.x, c.y, c.z);
        if (++licking % 20 == 0) cow.playSound(SoundEvents.GENERIC_EAT, 0.5F, 1.4F);
        if (licking >= LICK_TICKS) {
            cow.taste.onLick();
            cow.tell(cow.family.name + " полизала сіль");
        }
    }

    @Override
    public void stop() {
        lick = null;
        cow.getNavigation().stop();
    }
}
