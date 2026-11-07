package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowCharacter.Trait;
import com.gigongibus.hungrycows.CowHome;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumSet;

// Вихід на пасовище (ідея Fij). Удень корова в хліві (під дахом) час від часу вирішує, чи вийти надвір;
// надворі біля хліва — іде далі, коли «неспокій біля хліва» (HungryCow.barnUnrest) дійшов до межі.
// Шанс залежить від характеру: активна й допитлива виходить частіше, полохлива рідше, товариська майже напевно йде
// за ватажком, якщо та вже надворі. У дощ виходить утричі рідше. «Вийти» = через вхід на відкрите місце (бажано трава)
// за 8–24 блоки; далі корова поводиться як звичайно (пасеться, гуляє, п'є).
public class GoOutGoal extends Goal {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final int DECIDE_EVERY = 600;            // вирішує раз на 30 с
    private static final double BASE_CHANCE = 0.30;         // звичайний шанс вийти
    private static final double ACTIVITY_MIN = 0.5;         // лінива ×0,5 ... активна ×1,5
    private static final double CURIOSITY_BONUS = 0.20;     // допитлива до +20%
    private static final double FEAR_MALUS = 0.20;          // полохлива до −20%
    private static final double FOLLOW_LEADER = 0.50;       // ватажок надворі: товариська до +50%
    private static final double RAIN = 0.3;                 // дощ: ×0,3
    private static final int OUT_MIN = 12, OUT_MAX = 28;    // куди йде: 12–28 блоків від себе
    private static final int PASTURE_FROM_ROOF = 10;        // пасовище = до найближчого даху понад 10 блоків (не біля дверей)
    private static final int GRASS_AROUND = 2;              // траву рахує в квадраті 5×5 навколо місця
    private static final int SPOT_TRIES = 40;               // скільки місць пробує
    private static final double ARRIVED = 3;                // «вийшла» = дійшла до вибраного місця на пасовищі (ближче 3 блоків)
    private static final int MAX_WALK_TICKS = 1200;         // іде щонайбільше 1 хв
    private static final int MAX_FAILS = 5;                 // шляху немає / не наближається 5 разів — передумала
    private static final double SPEED = 1.0;
    private static final int DECISION_TICKS = 1200;          // вирішила вийти — пробує цю хвилину ...
    private static final int RETRY_TICKS = 100;              // ... кожні 5 с, доки не знайде, куди можна дійти
    private static final int PATH_CHECKS = 4;                // скільки місць надворі перевіряє шляхом за спробу

    private final HungryCow cow;
    private int cooldown = 0;
    private int decidedUntil = -1;   // поки tickCount < цього — вже вирішила вийти й шукає дорогу
    private int retryCooldown = 0;
    private BlockPos target;
    private BlockPos door;
    private boolean passedDoor;
    private BlockPos startPos;
    private int walkTicks;
    private int fails;
    private double lastDist;

    public GoOutGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    // «У хліві» = над головою дах. Не шелтер з балами: посеред великого хліва стіни далі 6 блоків, і там бали 0 —
    // корова там ніколи не вирішувала вийти
    private boolean inShelter(HungryCow c) {
        return CowHome.hasRoof(c.level(), c.blockPosition());
    }

    // Надворі = над головою немає даху (за блоками, як шукає дім; світло неба в щойно збудованих місцях буває ще не пораховане)
    private boolean underSky() {
        return !CowHome.hasRoof(cow.level(), cow.blockPosition());
    }

    // Шанс вийти за характером, ватажком і погодою
    public double chance() {
        double c = BASE_CHANCE * (ACTIVITY_MIN + cow.character.fraction(Trait.ACTIVITY));
        c += CURIOSITY_BONUS * cow.character.fraction(Trait.CURIOSITY);
        c -= FEAR_MALUS * cow.character.fraction(Trait.FEARFULNESS);
        HungryCow leader = cow.herd.leader();
        if (leader != null && leader != cow && !inShelter(leader)) {
            c += FOLLOW_LEADER * cow.character.fraction(Trait.SOCIABILITY);
        }
        if (cow.level().isRaining()) c *= RAIN;
        return Math.max(0, Math.min(1, c));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        cooldown = DECIDE_EVERY / 2;   // canUse викликається раз на 2 тіки
        if (!cow.isDayTime() || cow.isBaby() || cow.sleeping || cow.cycle.inEstrus()) return false;
        if (!inShelter(cow)) {
            // надворі біля хліва: неспокій дійшов до межі — іде на пасовище (без кидання шансу)
            if (cow.barnUnrest < HungryCow.UNREST_GO) return false;
            if (cow.tickCount >= decidedUntil) {
                decidedUntil = cow.tickCount + DECISION_TICKS;
                retryCooldown = 0;
            }
            return tryFindTarget();
        }
        if (cow.tickCount >= decidedUntil) {
            if (cow.getRandom().nextDouble() >= chance()) return false;
            decidedUntil = cow.tickCount + DECISION_TICKS;   // вирішила: шукає дорогу надвір цю хвилину
            retryCooldown = 0;
        }
        return tryFindTarget();
    }

    // Вирішила вийти: шукає місце надворі, до якого справді можна дійти (не знайшла — ще раз за 5 с)
    private boolean tryFindTarget() {
        if (retryCooldown > 0) { retryCooldown--; cooldown = 0; return false; }
        retryCooldown = RETRY_TICKS / 2;
        cooldown = 0;
        target = findOutside();
        if (target != null) decidedUntil = -1;
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (target == null || !cow.isDayTime() || walkTicks >= MAX_WALK_TICKS || fails >= MAX_FAILS) return false;
        // вийшла = дійшла до вибраного місця на пасовищі (а не просто ступила за двері)
        return !(underSky() && cow.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) <= ARRIVED * ARRIVED);
    }

    @Override
    public void start() {
        startPos = cow.blockPosition();
        door = cow.home.entrance();
        passedDoor = door == null;
        walkTicks = 0;
        fails = 0;
        lastDist = Double.MAX_VALUE;
        cow.tell(cow.family.name + " виходить на пасовище");
    }

    @Override
    public void tick() {
        if (target == null) return;
        walkTicks++;
        if (walkTicks != 1 && walkTicks % 20 != 0) return;
        // спершу до входу (якщо він відомий), потім надвір
        if (!passedDoor && door != null && cow.distanceToSqr(door.getX() + 0.5, door.getY(), door.getZ() + 0.5) <= 4) {
            passedDoor = true;
            lastDist = Double.MAX_VALUE;
        }
        BlockPos goal = passedDoor || door == null ? target : door;
        boolean grounded = cow.onGround() || cow.isInWater();
        cow.getNavigation().setMaxVisitedNodesMultiplier(CowHome.PATH_SEARCH_MULTIPLIER);   // обійти будівлю до входу
        boolean ok = cow.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, SPEED);
        cow.getNavigation().resetMaxVisitedNodesMultiplier();
        if (!ok && grounded) fails++;
        if (walkTicks % 60 == 0) {
            double d = cow.distanceToSqr(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5);
            if (d > lastDist - 0.5) fails++;
            lastDist = d;
        }
        if (fails >= MAX_FAILS && !passedDoor) {   // до входу не дійти — прямо надвір
            passedDoor = true;
            fails = 0;
        }
    }

    // Відкрите місце надворі (без даху), бажано на траві, до якого гра знаходить шлях: кандидати ближче до входу (якщо
    // відомий) — першими; перевіряє шляхом не більше PATH_CHECKS
    private BlockPos findOutside() {
        if (!cow.onGround() && !cow.isInWater()) return null;   // у повітрі шлях не будується — спробує пізніше
        BlockPos o = cow.blockPosition();
        java.util.List<BlockPos> spots = new java.util.ArrayList<>();
        for (int i = 0; i < SPOT_TRIES; i++) {
            double angle = cow.getRandom().nextDouble() * Math.PI * 2;
            int r = OUT_MIN + cow.getRandom().nextInt(OUT_MAX - OUT_MIN + 1);
            int x = o.getX() + (int) Math.round(Math.cos(angle) * r);
            int z = o.getZ() + (int) Math.round(Math.sin(angle) * r);
            for (int dy = 3; dy >= -3; dy--) {
                BlockPos p = new BlockPos(x, o.getY() + dy, z);
                if (!cow.level().isLoaded(p)) break;
                if (!CowHome.standable(cow.level(), p) || CowHome.hasRoof(cow.level(), p)) continue;
                spots.add(p.immutable());
                break;
            }
        }
        // справжнє пасовище: далеко від будівель (спершу ті, що понад PASTURE_FROM_ROOF від даху), де найбільше трави
        java.util.Map<BlockPos, Integer> roof = new java.util.HashMap<>();
        java.util.Map<BlockPos, Integer> grass = new java.util.HashMap<>();
        for (BlockPos p : spots) {
            roof.put(p, CowHome.roofDistance(cow.level(), p, PASTURE_FROM_ROOF));
            grass.put(p, grassAround(p));
        }
        spots.sort(java.util.Comparator.comparingInt((BlockPos p) -> roof.get(p) > PASTURE_FROM_ROOF ? 0 : 1)
                .thenComparing(p -> -grass.get(p))
                .thenComparing(p -> -roof.get(p)));
        int checks = 0;
        for (BlockPos p : spots) {
            if (checks++ >= PATH_CHECKS) break;
            cow.getNavigation().setMaxVisitedNodesMultiplier(CowHome.PATH_SEARCH_MULTIPLIER);
            net.minecraft.world.level.pathfinder.Path path = cow.getNavigation().createPath(p, 1);
            cow.getNavigation().resetMaxVisitedNodesMultiplier();
            if (path != null && path.getEndNode() != null && path.getEndNode().asBlockPos().distManhattan(p) <= 1) return p;
        }
        return null;
    }

    // Скільки трави (трав'яний блок під ногами) у квадраті навколо місця
    private int grassAround(BlockPos p) {
        int n = 0;
        for (int dx = -GRASS_AROUND; dx <= GRASS_AROUND; dx++) {
            for (int dz = -GRASS_AROUND; dz <= GRASS_AROUND; dz++) {
                if (cow.level().getBlockState(p.offset(dx, -1, dz)).is(Blocks.GRASS_BLOCK)) n++;
            }
        }
        return n;
    }

    @Override
    public void stop() {
        if (target != null && underSky() && cow.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) <= ARRIVED * ARRIVED) {
            cow.barnUnrest = 0;   // на пасовищі неспокій минув
        }
        if (target != null && underSky()) cow.tell(cow.family.name + " вийшла на пасовище");
        target = null;
        cow.getNavigation().stop();
    }
}
