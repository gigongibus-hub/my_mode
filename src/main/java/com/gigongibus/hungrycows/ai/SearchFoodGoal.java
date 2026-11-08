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
    private static final int WANDER_TRIES = 16;          // скільки випадкових місць пробує (бере перше надворі)
    private static final double WANDER_MIN_CLOSE = 4;    // далеких немає (тісний загін) — годиться й ближче 4 бл.
    private static final int RETRY_TICKS = 20;           // не знайшла, куди йти, — наступна спроба за 1 с
    private static final double ARRIVED = 2.5;           // дійшла
    private static final int MAX_TICKS = 400;            // іде щонайдовше 20 с, потім вибирає інше місце
    private static final double SPEED = 1.0;
    private static final int SMELL_IGNORE_TICKS = 1200;  // на запах їжі, до якої немає дороги (чи дійшла, а їжі не видно), — 1 хв не йде
    private static final int LOOK_AROUND_TICKS = 40;     // їжі не видно — спершу 2 с озирається (розвертається на 180°: позаду сліпа зона)
    private static final int LOOK_AROUND_EVERY = 400;    // ... не частіше раз на 20 с
    private static final int SHORT_RUN = 20;             // ціль скінчилась швидше 1 с — пауза 1 с (щоб не смикатись і не спамити)
    private static final int RECENT_SPOTS = 6;           // пам'ятає 6 останніх місць, де вже шукала ...
    private static final double RECENT_NEAR = 6;         // ... і нове бере щонайменше за 6 бл. від них
    private static final int RECENT_TICKS = 2400;        // ... протягом 2 хв

    private final HungryCow cow;
    private BlockPos spot;
    private BlockPos smellFood = null;   // їжа, на запах якої йде (spot — кінець дороги до неї)
    private BlockPos toldFood = null;    // про цю їжу вже писала в чат
    private boolean fromMemory;
    private int ticks;
    private final Map<Long, Integer> skipped = new HashMap<>();
    private int retryAt = 0;
    private int lookUntil = 0, lastLook = -100000;
    private float lookFrom = 0;
    private final java.util.Deque<long[]> recent = new java.util.ArrayDeque<>();   // {позиція, тік} — де вже шукала   // клітинка пам'яті → до якого тіку не йти

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
        if (cow.tickCount < retryAt) return false;
        spot = choose();
        if (spot == null) retryAt = cow.tickCount + RETRY_TICKS;
        return spot != null;
    }

    private BlockPos choose() {
        skipped.values().removeIf(t -> t < cow.tickCount);
        cow.smellIgnore.values().removeIf(t -> t < cow.tickCount);
        smellFood = null;
        // нюх: чує запах їжі — іде на нього, але дорогою (у лабіринті їжа буває за стінкою поруч, а йти довго)
        BlockPos food = cow.smelledFood;
        if (food != null && cow.tickCount - cow.smelledFoodTick <= NOT_SEEN_FRESH && !cow.smellIgnore.containsKey(food)) {
            Path p = cow.getNavigation().createPath(food, 1);
            if (p != null && p.canReach()) {
                fromMemory = false;
                smellFood = food;
                if (!food.equals(toldFood)) {
                    toldFood = food;
                    cow.tell(cow.family.name + " не бачить їжі, але чує її запах — іде туди");
                    cow.status("чує запах їжі");
                }
                return p.getEndNode() != null ? p.getEndNode().asBlockPos() : p.getTarget();   // кінець дороги, а не сама ціль
            }
            cow.smellIgnore.put(food, cow.tickCount + SMELL_IGNORE_TICKS);
            cow.tell(cow.family.name + " чує запах їжі, але дороги до неї не знаходить");
        }
        Set<Long> skip = new HashSet<>(skipped.keySet());
        BlockPos mem = cow.memory.bestPlace(cow.blockPosition(), MEMORY_RANGE, MEMORY_MIN, skip);
        if (mem != null) {
            Path p = cow.getNavigation().createPath(mem, 2);
            if (p != null) {
                fromMemory = true;
                cow.tell(cow.family.name + " не бачить їжі — іде туди, де їла раніше");
                return p.getEndNode() != null ? p.getEndNode().asBlockPos() : p.getTarget();   // кінець дороги, а не сама ціль
            }
            skipped.put(CowMemory.cellKey(mem), cow.tickCount + MEMORY_SKIP_TICKS);
        }
        fromMemory = false;
        recent.removeIf(e -> cow.tickCount - e[1] > RECENT_TICKS);
        BlockPos any = null, close = null;
        for (int i = 0; i < WANDER_TRIES; i++) {
            Vec3 v = LandRandomPos.getPos(cow, WANDER_XZ, WANDER_Y);
            if (v == null || v.distanceToSqr(cow.position()) < WANDER_MIN_CLOSE * WANDER_MIN_CLOSE) continue;
            if (v.distanceToSqr(cow.position()) < WANDER_MIN * WANDER_MIN) {
                BlockPos c = BlockPos.containing(v);
                if (close == null && !searchedNear(c)) {
                    Path cp = cow.getNavigation().createPath(c, 1);
                    if (cp != null && cp.canReach()) close = c;
                }
                continue;
            }
            BlockPos b = BlockPos.containing(v);
            if (searchedNear(b)) continue;                         // тут уже шукала — далі
            Path path = cow.getNavigation().createPath(b, 1);
            if (path == null || !path.canReach()) continue;        // туди не дійти
            if (!CowHome.hasRoof(cow.level(), b)) {
                cow.tell(cow.family.name + " не бачить їжі — шукає");
                return b;
            }
            if (any == null) any = b;
        }
        if (any == null) any = close;
        if (any != null) {
            cow.tell(cow.family.name + " не бачить їжі — шукає");
            cow.status("шукає їжу");
        }
        else recent.clear();   // усе поруч обшукала — наступного разу шукатиме знову скрізь
        return any;
    }

    private boolean searchedNear(BlockPos b) {
        for (long[] e : recent) {
            if (BlockPos.of(e[0]).distSqr(b) < RECENT_NEAR * RECENT_NEAR) return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return spot != null && ticks < MAX_TICKS && cow.shouldSeekFood() && !cow.choking && !cow.heat.unconscious
                && cow.distanceToSqr(Vec3.atBottomCenterOf(spot)) > ARRIVED * ARRIVED;
    }

    @Override
    public void start() {
        ticks = 0;
        if (cow.tickCount - lastLook > LOOK_AROUND_EVERY) {   // спершу озирнутись
            lastLook = cow.tickCount;
            lookUntil = cow.tickCount + LOOK_AROUND_TICKS;
            lookFrom = cow.getYRot();
            cow.getNavigation().stop();
        }
    }

    @Override
    public void tick() {
        if (spot == null) return;
        if (cow.tickCount < lookUntil) {
            // розвертається на місці: тіло й голова, за 2 с на 180° (так побачить те, що було за спиною)
            float yaw = lookFrom + 180F * (1F - (lookUntil - cow.tickCount) / (float) LOOK_AROUND_TICKS);
            cow.setYRot(yaw);
            cow.yBodyRot = yaw;
            cow.setYHeadRot(yaw);
            return;
        }
        ticks++;
        if (ticks == 1 || ticks % 20 == 0) cow.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, SPEED);
    }

    @Override
    public void stop() {
        // дійшла дорогою до їжі, яку чула, а не бачить — 1 хв на цей запах не йде
        if (smellFood != null && spot != null && cow.distanceToSqr(Vec3.atBottomCenterOf(spot)) <= (ARRIVED + 1) * (ARRIVED + 1)) {
            cow.smellIgnore.put(smellFood, cow.tickCount + SMELL_IGNORE_TICKS);
        }
        if (ticks < SHORT_RUN) retryAt = cow.tickCount + RETRY_TICKS;
        // дійшла до пам'ятного місця, а їжі не видно — 2 хв туди не ходить
        if (spot != null && fromMemory && cow.distanceToSqr(Vec3.atBottomCenterOf(spot)) <= (ARRIVED + 1) * (ARRIVED + 1)) skipped.put(CowMemory.cellKey(spot), cow.tickCount + MEMORY_SKIP_TICKS);
        if (spot != null) {
            recent.addLast(new long[]{ spot.asLong(), cow.tickCount });
            while (recent.size() > RECENT_SPOTS) recent.pollFirst();
        }
        spot = null;
        cow.getNavigation().stop();
    }
}
