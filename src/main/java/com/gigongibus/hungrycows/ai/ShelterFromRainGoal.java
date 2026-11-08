package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Дощ (Fij): корова, що не любить дощу (ген «любов до дощу» нижче 40), ховається — у дім (хлів), якщо він недалеко,
 * інакше під дерево, інакше під будь-яке накриття — і стоїть там, поки дощ не скінчиться. Любителька дощу й
 * байдужа не ховаються. Голод, спрага й страх (важливіші цілі) її звідти виводять, як і раніше.
 */
public class ShelterFromRainGoal extends Goal {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final int HOME_RADIUS = 32;        // дім ближче 32 блоків — іде туди
    private static final int SHADE_RADIUS = 16;       // інакше шукає дерево / накриття в 16 блоках
    private static final int SHADE_TRIES = 40;        // скільки місць перевіряє за раз
    private static final int MAX_WALK_TICKS = 1200;   // іде до укриття щонайбільше 60 с
    private static final int MAX_STAY_TICKS = 6000;   // стоїть в укритті щонайдовше 5 хв, потім вирішує знову
    private static final int RETRY_TICKS = 200;       // укриття не знайшла — наступна спроба за 10 с
    private static final int STUCK_CHECK = 40;        // раз на 2 с: стоїть під дощем, а йти нікуди — шукає ближче укриття
    private static final int SHADE_ROUNDS = 3;        // тінь, на яку все ж падає дощ (тінь від світла неба), — шукає ще раз, до 3 разів

    private final HungryCow cow;
    private BlockPos shelter;
    private int walkTicks = 0;
    private int stayTicks = 0;
    private int nextTry = 0;

    public ShelterFromRainGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean wants() {
        return cow.level().isRaining() && cow.hatesRain() && !cow.isBaby() && !cow.isNightTime() && !cow.heat.unconscious;
    }

    @Override
    public boolean canUse() {
        if (cow.tickCount < nextTry || !wants() || !cow.rainedOn()) return false;
        nextTry = cow.tickCount + RETRY_TICKS;
        BlockPos home = cow.home.home();
        shelter = home != null && home.distSqr(cow.blockPosition()) < HOME_RADIUS * HOME_RADIUS ? home : null;
        if (shelter != null) {   // до дому мусить бути шлях, інакше — дерево чи навіс поруч
            net.minecraft.world.level.pathfinder.Path path = cow.getNavigation().createPath(shelter, 2);
            if (path == null || !path.canReach()) shelter = null;
        }
        for (int i = 0; i < SHADE_ROUNDS && dry(shelter) == null; i++) shelter = cow.home.findShade(SHADE_RADIUS, SHADE_TRIES, true);  // дерево
        for (int i = 0; i < SHADE_ROUNDS && dry(shelter) == null; i++) shelter = cow.home.findShade(SHADE_RADIUS, SHADE_TRIES, false); // будь-яке накриття
        shelter = dry(shelter);
        if (shelter != null) cow.tell(cow.family.name + ": не любить дощу — іде в укриття");
        return shelter != null;
    }

    // Місце, куди дощ не падає (інакше null)
    private BlockPos dry(BlockPos p) {
        return p != null && !HungryCow.rainReaches(cow.level(), p) ? p : null;
    }

    @Override
    public boolean canContinueToUse() {
        if (shelter == null || !wants()) return false;
        if (cow.rainedOn()) return walkTicks < MAX_WALK_TICKS;
        return stayTicks < MAX_STAY_TICKS;
    }

    @Override
    public void start() {
        walkTicks = 0;
        stayTicks = 0;
    }

    @Override
    public void tick() {
        if (shelter == null) return;
        if (cow.rainedOn()) {
            walkTicks++;
            // дорогою зупинилась (шлях скінчився, а дощ досі на неї) — шукає укриття ближче
            if (walkTicks % STUCK_CHECK == 0 && cow.getNavigation().isDone()) {
                BlockPos near = dry(cow.home.findShade(SHADE_RADIUS, SHADE_TRIES, false));
                if (near != null) shelter = near;
            }
            if (walkTicks == 1 || walkTicks % 20 == 0) {
                cow.getNavigation().moveTo(shelter.getX() + 0.5, shelter.getY(), shelter.getZ() + 0.5, 1.0);
            }
        } else {
            // уже під дахом: ще трохи вглиб до вибраного місця, далі стоїть
            stayTicks++;
            if (cow.blockPosition().distSqr(shelter) <= 4 && !cow.getNavigation().isDone()) cow.getNavigation().stop();
        }
    }

    @Override
    public void stop() {
        shelter = null;
        cow.getNavigation().stop();
    }
}
