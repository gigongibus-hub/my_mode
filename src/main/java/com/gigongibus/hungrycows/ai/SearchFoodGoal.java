package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowHome;
import com.gigongibus.hungrycows.CowMemory;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Пошук їжі (зір, Fij): голодна корова бачить лише ту їжу, що на очах. Не бачить жодної — іде шукати:
 * спершу до місця, де добре поїла раніше (пам'ять місць), інакше в нове місце 8–16 блоків від себе, переважно надворі.
 * Дорогою дивиться довкола; щойно побачить їжу, SeekGrassGoal (важливіша) веде до неї.
 */
public class SearchFoodGoal extends Goal {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final int NOT_SEEN_FRESH = 100;       // «не бачить їжі» — якщо так було за останні 5 с
    private static final double MEMORY_RANGE = 48;       // пам'ятне місце їжі — до 48 блоків
    private static final double MEMORY_MIN = 0.05;       // «добре місце» = там хоч раз поїла (CowMemory.PLACE_MEAL)
    private static final int MEMORY_SKIP_TICKS = 2400;   // місце, куди ходила й нічого не побачила, — 2 хв не йде знову
    private static final int WANDER_XZ = 16, WANDER_Y = 4;   // нове місце: до 16 блоків по горизонталі, ±4 по висоті
    private static final double WANDER_MIN = 8;          // ... але не ближче 8
    private static final int WANDER_TRIES = 8;           // скільки випадкових місць пробує (бере перше надворі)
    private static final double ARRIVED = 2.5;           // дійшла
    private static final int MAX_TICKS = 400;            // іде щонайдовше 20 с, потім вибирає інше місце
    private static final double SPEED = 1.0;

    private final HungryCow cow;
    private BlockPos spot;
    private boolean fromMemory;
    private int ticks;
    private final Map<Long, Integer> skipped = new HashMap<>();   // клітинка пам'яті → до якого тіку не йти

    public SearchFoodGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (cow.isBaby() || cow.choking || cow.heat.unconscious || !cow.onGround()) return false;
        if (cow.tickCount - cow.foodNotSeenTick > NOT_SEEN_FRESH || !cow.shouldSeekFood()) return false;
        spot = choose();
        return spot != null;
    }

    private BlockPos choose() {
        skipped.values().removeIf(t -> t < cow.tickCount);
        Set<Long> skip = new HashSet<>(skipped.keySet());
        BlockPos mem = cow.memory.bestPlace(cow.blockPosition(), MEMORY_RANGE, MEMORY_MIN, skip);
        if (mem != null) {
            Path p = cow.getNavigation().createPath(mem, 2);
            if (p != null) {
                fromMemory = true;
                cow.tell(cow.family.name + " не бачить їжі — іде туди, де їла раніше");
                return p.getTarget();
            }
            skipped.put(CowMemory.cellKey(mem), cow.tickCount + MEMORY_SKIP_TICKS);
        }
        fromMemory = false;
        BlockPos any = null;
        for (int i = 0; i < WANDER_TRIES; i++) {
            Vec3 v = LandRandomPos.getPos(cow, WANDER_XZ, WANDER_Y);
            if (v == null || v.distanceToSqr(cow.position()) < WANDER_MIN * WANDER_MIN) continue;
            BlockPos b = BlockPos.containing(v);
            if (!CowHome.hasRoof(cow.level(), b)) {
                cow.tell(cow.family.name + " не бачить їжі — шукає");
                return b;
            }
            if (any == null) any = b;
        }
        if (any != null) cow.tell(cow.family.name + " не бачить їжі — шукає");
        return any;
    }

    @Override
    public boolean canContinueToUse() {
        return spot != null && ticks < MAX_TICKS && cow.shouldSeekFood() && !cow.choking && !cow.heat.unconscious
                && cow.distanceToSqr(Vec3.atBottomCenterOf(spot)) > ARRIVED * ARRIVED;
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void tick() {
        if (spot == null) return;
        ticks++;
        if (ticks == 1 || ticks % 20 == 0) cow.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, SPEED);
    }

    @Override
    public void stop() {
        // дійшла до пам'ятного місця, а їжі не видно — 2 хв туди не ходить
        if (spot != null && fromMemory && cow.distanceToSqr(Vec3.atBottomCenterOf(spot)) <= (ARRIVED + 1) * (ARRIVED + 1)) skipped.put(CowMemory.cellKey(spot), cow.tickCount + MEMORY_SKIP_TICKS);
        spot = null;
        cow.getNavigation().stop();
    }
}
