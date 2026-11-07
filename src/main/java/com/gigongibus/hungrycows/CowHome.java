package com.gigongibus.hungrycows;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Дім і тінь (ідея 9).
 * Дім = місце, де над коровою дах (не видно неба) і хоча б з двох боків стіни (до 3 блоків).
 * Корова сама потроху оглядає околиці (кілька випадкових місць щосекунди), знаходить найкраще укриття
 * і запам'ятовує його. Вночі йде туди спати. Тінь = будь-яке місце без неба над головою.
 * Усі числа зібрані на початку файлу.
 */
public class CowHome {

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int SEARCH_RADIUS = 32;         // як далеко шукає дім
    public static final int SAMPLES_PER_SECOND = 16;    // скільки випадкових місць оглядає щосекунди ...
    public static final int SWEEP_PER_SECOND = 80;      // ... і скільки ще по черзі (так менше ніж за хвилину огляне всю околицю)
    public static final double LOW_BLOCK = 0.5;         // неповний блок до пів блока заввишки — на ньому можна стояти
    public static final int NEAR_REACH = 3;             // шлях закінчився поруч усередині (до 3 блоків) — теж годиться
    public static final int BURST_CHECKS = 400;         // перед сном без місця: стільки місць за секунду (≈ вся околиця за 10 с)
    public static final int BEST_SCORE = 13;            // найкращий можливий дім (дах + 4 стіни)
    public static final int COLUMN_UP = 6;              // у кожному місці перевіряє висоти від y+6 ...
    public static final int COLUMN_DOWN = 6;            // ... до y−6 (і дах, і підлогу всередині будівлі)
    public static final int ROOF_HEIGHT = 16;           // дах шукає не вище стількох блоків над головою (великий хлів — стеля високо; було 6)
    public static final int MIN_WALLS = 2;              // скільки боків мають бути закриті
    public static final int EDGE_PENALTY = 3;           // поріг / прохід входу (поруч відкрите небо): −3 бали — спати можна, але краще глибше (Fij)
    public static final int WALL_DISTANCE = 6;          // стіна — не далі стількох блоків у кожен бік (великий хлів — стіни далеко; було 3, 12 — забагато: усе «дім»)
    public static final int RECHECK_SECONDS = 30;       // як часто перевіряє, чи дім ще годиться
    public static final double MAX_HOME_DISTANCE = 64;  // дім далі цього — забуває його
    public static final double ADOPT_RANGE = 32;        // свого дому немає — переймає дім корови зі стада ближче цього
    public static final int ADOPT_EVERY_SECONDS = 2;    // ... перевіряє раз на 2 с

    private final HungryCow cow;
    private BlockPos home = null;
    private BlockPos treeSpot = null;   // запасне місце для сну, якщо дому немає: під деревом чи будь-яким накриттям
    private int homeScore = 0;
    private BlockPos entrance = null;      // вхід у дім: остання точка шляху надворі перед дахом (туди спершу, потім усередину)
    private BlockPos lastEntrance = null;  // вхід, знайдений останнім перевіреним шляхом
    public static final double ENTRANCE_REACH = 2.0;   // «біля входу» = ближче 2 блоків
    private int seconds = 0;
    private int sweep = 0;
    private int lastUnreachableTell = -100000;
    public static final float PATH_SEARCH_MULTIPLIER = 4F; // шлях додому шукає вчетверо довше за звичайний (обхід будівлі до входу)
    public static final int PATHS_PER_SECOND = 6;           // скільки шляхів до кандидатів перевіряє за секунду (звичайно)
    public static final int PATHS_PER_SECOND_HARD = 15;     // ... і перед сном без місця
    public static final int RETRY_UNREACHABLE_TICKS = 600;  // недосяжне місце знову перевіряє лише через 30 с
    private final java.util.Map<BlockPos, Integer> unreachable = new java.util.HashMap<>();
    private int pathBudget = 0;
    private static final int UNREACHABLE_TELL_TICKS = 1200;   // підказку «не може дійти» — не частіше раз на хвилину   // по черзі оглядає околицю (номер клітинки)

    public CowHome(HungryCow cow) {
        this.cow = cow;
    }

    public BlockPos home() {
        return home;
    }

    // Куди йти спати: дім, а якщо його немає — під дерево
    public BlockPos sleepSpot() {
        return home != null ? home : treeSpot;
    }

    // Вхід у дім (null — невідомий: дім знайдено зсередини або спить під деревом)
    public BlockPos entrance() {
        return entrance;
    }

    public BlockPos treeSpot() {
        return treeSpot;
    }

    // ---------- перевірки місць ----------

    // Чи може корова тут стояти: тверде під ногами, два блоки повітря (корова ~1,4 блока)
    // Чи може корова тут стояти, як у звичайній грі: можна стати на неповний блок (килим, люк, нажимна плита,
    // плита-півблок), а трава й квіти під ногами не заважають; над головою має бути вільно
    public static boolean standable(Level level, BlockPos pos) {
        if (!level.getFluidState(pos).isEmpty()) return false;   // не у воді
        VoxelShape at = level.getBlockState(pos).getCollisionShape(level, pos);
        boolean low = at.isEmpty() || at.max(Direction.Axis.Y) <= LOW_BLOCK;
        if (!low) return false;
        boolean ground = !at.isEmpty() || !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
        boolean head = level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
        if (!ground || !head) return false;
        // не на котлі, паркані, огорожі чи хвіртці: мобам туди не можна ставати
        BlockState below = level.getBlockState(pos.below());
        if (below.is(BlockTags.CAULDRONS) || below.is(BlockTags.FENCES) || below.is(BlockTags.WALLS)
                || below.is(BlockTags.FENCE_GATES)) return false;
        // і головне — та сама перевірка, якою користується пошук шляху гри: чи може моб тут ходити
        return WalkNodeEvaluator.getBlockPathTypeStatic(level, pos.mutable()) == BlockPathTypes.WALKABLE;
    }

    // Тінь: над головою не видно неба
    public static boolean shaded(Level level, BlockPos pos) {
        return !level.canSeeSky(pos.above());
    }

    // Справжній будівельний блок (тверде, але не листя: листя дає тінь, але не захищає)
    // Дах: будь-який блок, у який не можна пройти (повний блок, півблок, сходинки, люк…), крім листя
    private static boolean roofBlock(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return !s.is(BlockTags.LEAVES) && !s.getCollisionShape(level, p).isEmpty();
    }

    // Стіна: те саме, але паркан, хвіртка й огорожа стіною не рахуються (крізь них дме вітер і видно)
    private static boolean wallBlock(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return roofBlock(level, p) && !s.is(BlockTags.FENCES) && !s.is(BlockTags.FENCE_GATES) && !s.is(BlockTags.WALLS);
    }

    // Скільки боків закрито стінами (до WALL_DISTANCE блоків); листя не рахується
    public static int walls(Level level, BlockPos pos) {
        int n = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int i = 1; i <= WALL_DISTANCE; i++) {
                BlockPos p = pos.relative(d, i);
                if (wallBlock(level, p) || wallBlock(level, p.above())) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    // Чи є над головою справжній дах (не листя), до ROOF_HEIGHT блоків
    public static boolean hasRoof(Level level, BlockPos pos) {
        for (int i = 2; i <= ROOF_HEIGHT; i++) {
            BlockPos p = pos.above(i);
            // повітря, ліхтар, смолоскип, ланцюг тощо — пропускає; дах = перший блок, у який не пройти (і півблок теж)
            if (roofBlock(level, p)) return true;
            if (level.getBlockState(p).is(BlockTags.LEAVES)) return false;   // листя: тінь, але не дах
        }
        return false;
    }

    // Наскільки добре місце як дім (0 = не годиться): дах +3, кожна стіна +2, усі 4 стіни ще +2 (до 13)
    public static int shelterScore(Level level, BlockPos pos) {
        if (!standable(level, pos) || !hasRoof(level, pos)) return 0;
        int w = walls(level, pos);
        if (w < MIN_WALLS) return 0;
        int score = 3 + 2 * w + (w == 4 ? 2 : 0);
        if (atEdge(level, pos)) score = Math.max(1, score - EDGE_PENALTY);
        return score;
    }

    // Край укриття: поруч (з будь-якого з 4 боків) клітинка під відкритим небом — поріг, прохід входу
    private static boolean atEdge(Level level, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(d);
            // поруч відкрите місце (не стіна й без даху) — за блоками, а не за світлом неба (воно буває ще не пораховане)
            if (!wallBlock(level, n) && !hasRoof(level, n)) return true;
        }
        return false;
    }

    // Чи може корова туди дійти (наприклад, у хлів із зачиненими дверима — ні)
    private BlockPos lastPathEnd = null;   // де обірвався останній шлях (для підказки)

    // Досяжне місце в укритті біля p: саме p, а якщо впритул не підійти — точка, де закінчився шлях,
    // якщо вона теж усередині (під дахом між стінами) і не далі NEAR_REACH від p. Інакше null.
    private BlockPos reachableNear(BlockPos p) {
        // довший пошук шляху: щоб обійти будівлю й знайти вхід з іншого боку
        cow.getNavigation().setMaxVisitedNodesMultiplier(PATH_SEARCH_MULTIPLIER);
        net.minecraft.world.level.pathfinder.Path path = cow.getNavigation().createPath(p, NEAR_REACH);
        cow.getNavigation().resetMaxVisitedNodesMultiplier();
        lastPathEnd = null;
        if (path == null) return null;
        net.minecraft.world.level.pathfinder.Node end = path.getEndNode();
        if (end == null) return null;
        BlockPos e = end.asBlockPos();
        lastPathEnd = e;
        // вхід: остання точка шляху ще надворі (без даху) перед тим, як шлях заходить під дах
        lastEntrance = null;
        for (int i = 1; i < path.getNodeCount(); i++) {
            if (hasRoof(cow.level(), path.getNodePos(i)) && !hasRoof(cow.level(), path.getNodePos(i - 1))) {
                lastEntrance = path.getNodePos(i - 1).immutable();
            }
        }
        if (e.distManhattan(p) <= 1) return p;
        if (e.distManhattan(p) <= NEAR_REACH && standable(cow.level(), e) && shelterScore(cow.level(), e) > 0) return e;
        return null;
    }

    // Знайти вхід до місця target (корова надворі, на землі): шлях туди, остання точка надворі перед дахом.
    // Якщо target — її дім і вхід ще невідомий, запам'ятовує. null — вхід не знайдено (або корова вже всередині).
    public BlockPos findEntrance(BlockPos target) {
        if (target == null || (!cow.onGround() && !cow.isInWater())) return null;
        if (shelterScore(cow.level(), cow.blockPosition()) > 0) return null;   // уже всередині
        if (reachableNear(target) == null || lastEntrance == null) return null;
        if (target.equals(home) && entrance == null) entrance = lastEntrance;
        return lastEntrance;
    }

    // Свого дому немає (нова корова): переймає дім найближчої дорослої корови поруч, у якої він є.
    // Потім, як звичайно, шукає кращий сама. true — перейняла.
    public boolean adoptFromHerd() {
        if (home != null || cow.isBaby()) return false;
        HungryCow best = null;
        double bestD = ADOPT_RANGE * ADOPT_RANGE;
        for (HungryCow c : cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(ADOPT_RANGE),
                c -> c != cow && c.isAlive() && !c.isBaby() && c.home.home != null)) {
            double d = cow.distanceToSqr(c);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        if (best == null) return false;
        home = best.home.home;
        homeScore = best.home.homeScore;
        entrance = best.home.entrance;
        cow.tell(cow.family.name + " переняла дім від " + best.family.name + ": " + home.getX() + " " + home.getY() + " "
                + home.getZ() + " (бали " + homeScore + ")");
        return true;
    }

    // Оглянути одне місце: усі висоти (дах будівлі й підлогу всередині), найкраще укриття й тінь
    private void inspectColumn(int x, int y, int z, BlockPos o) {
        Level level = cow.level();
        for (int dy = COLUMN_UP; dy >= -COLUMN_DOWN; dy--) {
            BlockPos p = new BlockPos(x, y + dy, z);
            if (!standable(level, p)) continue;
            int s = shelterScore(level, p);
            // не дім, але є тінь (дерево): запасне місце для сну — ближче краще
            if (s == 0 && shaded(level, p) && (treeSpot == null || p.distSqr(o) < treeSpot.distSqr(o))) {
                treeSpot = p.immutable();
            }
            if (s <= homeScore) continue;
            BlockPos key = p.immutable();
            Integer retry = unreachable.get(key);
            if (retry != null && cow.tickCount < retry) continue;   // нещодавно не дійшла сюди: спершу інші місця
            if (pathBudget <= 0) continue;                          // цієї секунди шляхів уже досить
            // корова в повітрі (сходинка, стрибок, поштовх): гра тоді взагалі не будує шлях — це не «недосяжно», перевірить пізніше
            if (!cow.onGround() && !cow.isInWater()) continue;
            pathBudget--;
            BlockPos r = reachableNear(p);
            if (r == null) {
                unreachable.put(key, cow.tickCount + RETRY_UNREACHABLE_TICKS);
                // для перевірки: бачить гарне укриття, але не може туди дійти (вхід закритий чи загороджений)
                if (cow.tickCount - lastUnreachableTell > UNREACHABLE_TELL_TICKS) {
                    lastUnreachableTell = cow.tickCount;
                    String end = lastPathEnd == null ? "шляху немає зовсім"
                            : "шлях обривається за " + lastPathEnd.distManhattan(p) + " бл. (" + lastPathEnd.getX() + " "
                            + lastPathEnd.getY() + " " + lastPathEnd.getZ() + ")";
                    cow.tell(cow.family.name + " бачить дім " + p.getX() + " " + p.getY() + " " + p.getZ()
                            + " (бали " + s + "), але не може дійти: " + end);
                }
                continue;
            }
            int rs = shelterScore(level, r);
            if (rs > homeScore) {
                cow.tell(cow.family.name + (home == null ? " знайшла дім: " : " знайшла кращий дім: ")
                        + r.getX() + " " + r.getY() + " " + r.getZ() + " (бали " + rs + ")");
                home = r.immutable();
                homeScore = rs;
                entrance = lastEntrance;
            }
        }
    }

    // Випадкове місце в тіні поруч (для відпочинку в сонячний день)
    public BlockPos findShade(int radius, int tries) {
        BlockPos o = cow.blockPosition();
        for (int i = 0; i < tries; i++) {
            int x = o.getX() + cow.getRandom().nextInt(radius * 2 + 1) - radius;
            int z = o.getZ() + cow.getRandom().nextInt(radius * 2 + 1) - radius;
            // усі висоти: тінь може бути й під дахом, а не лише нагорі
            for (int dy = COLUMN_UP; dy >= -COLUMN_DOWN; dy--) {
                BlockPos p = new BlockPos(x, o.getY() + dy, z);
                if (standable(cow.level(), p) && shaded(cow.level(), p)) return p;
            }
        }
        return null;
    }

    // ---------- раз на секунду ----------

    public void secondTick() {
        if (cow.isBaby()) return;   // теля спить біля матері
        seconds++;
        if (home == null && seconds % ADOPT_EVERY_SECONDS == 0) adoptFromHerd();
        // запасне місце під деревом: забути, якщо там більше немає тіні чи воно далеко
        if (treeSpot != null && seconds % RECHECK_SECONDS == 0
                && (!standable(cow.level(), treeSpot) || !shaded(cow.level(), treeSpot)
                || cow.distanceToSqr(treeSpot.getX() + 0.5, treeSpot.getY(), treeSpot.getZ() + 0.5)
                > MAX_HOME_DISTANCE * MAX_HOME_DISTANCE)) {
            treeSpot = null;
        }
        if (home != null && (seconds % RECHECK_SECONDS == 0)) {
            int s = shelterScore(cow.level(), home);
            if (s == 0 || cow.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5)
                    > MAX_HOME_DISTANCE * MAX_HOME_DISTANCE) {
                cow.tell(cow.family.name + ": дім більше не годиться, шукає новий");
                home = null;
                homeScore = 0;
                entrance = null;
            } else {
                homeScore = s;
                // вхід ще невідомий (дім знайдено зсередини чи зі старого збереження): шукає його, коли сама надворі
                if (entrance == null) findEntrance(home);
            }
        }
        // огляд околиць: шукає дім або кращий (закритіший) дім — навмання й по черзі
        BlockPos o = cow.blockPosition();
        int side = SEARCH_RADIUS * 2 + 1;
        // ідеальний дім уже є — не шукає; добрий є — шукає вчетверо рідше (щоб не навантажувати гру)
        if (homeScore >= BEST_SCORE) return;
        int checks = SAMPLES_PER_SECOND + SWEEP_PER_SECOND;
        if (home != null) checks /= 4;
        scan(checks);
    }

    // Перед сном, якщо ще не знає, де спати: оглядає околицю в кілька разів швидше
    public void searchHard() {
        if (homeScore >= BEST_SCORE) return;
        scan(BURST_CHECKS);
    }

    private void scan(int checks) {
        BlockPos o = cow.blockPosition();
        pathBudget = checks >= BURST_CHECKS ? PATHS_PER_SECOND_HARD : PATHS_PER_SECOND;
        if (unreachable.size() > 200) unreachable.entrySet().removeIf(e -> cow.tickCount >= e.getValue());
        int side = SEARCH_RADIUS * 2 + 1;
        for (int i = 0; i < checks; i++) {
            int x, z;
            if (i < checks * SAMPLES_PER_SECOND / (SAMPLES_PER_SECOND + SWEEP_PER_SECOND)) {
                x = o.getX() + cow.getRandom().nextInt(side) - SEARCH_RADIUS;
                z = o.getZ() + cow.getRandom().nextInt(side) - SEARCH_RADIUS;
            } else {
                sweep = (sweep + 1) % (side * side);
                x = o.getX() + sweep % side - SEARCH_RADIUS;
                z = o.getZ() + sweep / side - SEARCH_RADIUS;
            }
            inspectColumn(x, o.getY(), z, o);
        }
    }

    // Коротко для палиці налагодження
    public String debugText() {
        if (home != null) return "дім " + home.getX() + " " + home.getY() + " " + home.getZ() + " (" + homeScore + "/" + BEST_SCORE + ")";
        if (treeSpot != null) return "дому немає, спить під деревом";
        return "дому немає";
    }

    public int homeScore() {
        return homeScore;
    }

    // ---------- збереження ----------

    public void save(CompoundTag tag) {
        if (home != null) {
            tag.putLong("Home", home.asLong());
            tag.putInt("HomeScore", homeScore);
            if (entrance != null) tag.putLong("HomeEntrance", entrance.asLong());
        }
        if (treeSpot != null) tag.putLong("TreeSpot", treeSpot.asLong());
    }

    public void load(CompoundTag tag) {
        home = tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null;
        homeScore = tag.getInt("HomeScore");
        entrance = tag.contains("HomeEntrance") ? BlockPos.of(tag.getLong("HomeEntrance")) : null;
        treeSpot = tag.contains("TreeSpot") ? BlockPos.of(tag.getLong("TreeSpot")) : null;
    }
}
