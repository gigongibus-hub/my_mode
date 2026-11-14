package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.WaterSources;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Спрага: корова йде до води (джерело води або котел з водою) і п'є 3 секунди
public class DrinkWaterGoal extends Goal {
    private static final int SEARCH_RADIUS = 16;        // як далеко шукає воду ...
    private static final int WIDE_RADIUS = 30;          // ... поруч нема, а спрага 45+ — шукає далі
    private static final int SEARCH_HEIGHT = 4;
    private static final int MAX_PATH_CHECKS = 4;       // скільки найближчих місць перевіряти, чи можна дійти
    private static final double REACH = 2.0;            // з якої відстані п'є
    private static final int DRINK_TICKS = 60;          // п'є 3 секунди
    private static final int MAX_RUN_TICKS = 600;       // скільки максимум іти до води (30 с)
    private static final int SEARCH_COOLDOWN = 40;      // нічого не знайшла — наступний пошук за ~4 с
    private final HungryCow cow;
    private BlockPos target;
    private boolean cauldron;
    private int drinkTicks = 0;
    private int runTicks = 0;
    private int cooldown = 0;
    private final Set<BlockPos> unreachable = new HashSet<>();
    private int lastClearTick = 0;

    public DrinkWaterGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private int radius = SEARCH_RADIUS;
    private boolean lastFoundNothing = false;

    @Override
    public boolean canUse() {
        if (cow.isBaby() || !cow.shouldSeekWater()) return false;   // теля п'є лише молоко
        if (cow.tickCount - lastClearTick > 1200) {
            unreachable.clear();
            lastClearTick = cow.tickCount;
        }
        if (cooldown > 0) { cooldown--; return false; }
        radius = (lastFoundNothing && cow.thirst >= HungryCow.NIGHT_DRINK_AT) ? WIDE_RADIUS : SEARCH_RADIUS;
        target = findWater();
        if (target == null) {
            target = cow.episodes.nearest(com.gigongibus.hungrycows.CowEpisodes.Place.WATER, this::isWater);   // пам'ятає, де пила (до 64 бл.)
            if (target != null) cauldron = cow.level().getBlockState(target).is(Blocks.WATER_CAULDRON);
        }
        lastFoundNothing = target == null && radius == SEARCH_RADIUS;
        cooldown = target != null ? 10 : SEARCH_COOLDOWN;
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && cow.thirst > 0 && runTicks < MAX_RUN_TICKS && isWater(target);
    }

    @Override
    public void start() {
        cow.sleep.wokeToEat();   // уночі встала попити — сон −50
        drinkTicks = 0;
        runTicks = 0;
    }

    @Override
    public void tick() {
        if (target == null) return;
        double x = target.getX() + 0.5, y = target.getY() + 0.5, z = target.getZ() + 0.5;
        cow.getLookControl().setLookAt(x, y, z);
        if (cow.distanceToSqr(x, y, z) > REACH * REACH + 1.0) {
            runTicks++;
            drinkTicks = 0;
            if (runTicks == 1 || runTicks % 10 == 0) cow.getNavigation().moveTo(x, y, z, 1.0);
            if (runTicks >= MAX_RUN_TICKS) unreachable.add(target);
            return;
        }
        cow.getNavigation().stop();
        if (++drinkTicks % 20 == 0) cow.playSound(SoundEvents.GENERIC_DRINK, 0.8F, 0.9F);
        if (drinkTicks >= DRINK_TICKS) {
            boolean ok = !cauldron || (cow.level() instanceof ServerLevel sl && WaterSources.sip(sl, target));
            if (ok) cow.drinkWater();
            target = null;
        }
    }

    @Override
    public void stop() {
        target = null;
        drinkTicks = 0;
        runTicks = 0;
        cow.getNavigation().stop();
    }

    private boolean isWater(BlockPos p) {
        if (cow.level().getBlockState(p).is(Blocks.WATER_CAULDRON)) return true;
        FluidState fs = cow.level().getFluidState(p);
        return fs.is(FluidTags.WATER) && fs.isSource();
    }

    private BlockPos findWater() {
        BlockPos o = cow.blockPosition();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-radius, -SEARCH_HEIGHT, -radius),
                o.offset(radius, SEARCH_HEIGHT, radius))) {
            if (unreachable.contains(p)) continue;
            // вода, над якою повітря (можна напитись з берега), або котел
            if (cow.level().getBlockState(p).is(Blocks.WATER_CAULDRON)
                    || (isWater(p) && cow.level().getBlockState(p.above()).isAir())) {
                found.add(p.immutable());
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(o)));
        int checks = 0;
        for (BlockPos p : found) {
            if (checks++ >= MAX_PATH_CHECKS) break;
            Path path = cow.getNavigation().createPath(p, 1);
            if (path != null && path.canReach()) {
                cauldron = cow.level().getBlockState(p).is(Blocks.WATER_CAULDRON);
                return p;
            }
            unreachable.add(p);
        }
        return null;
    }
}
