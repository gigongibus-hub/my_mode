package com.gigongibus.hungrycows;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

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
    public static final int BEST_SCORE = 13;            // найкращий можливий дім (дах + 4 стіни)
    public static final int COLUMN_UP = 6;              // у кожному місці перевіряє висоти від y+6 ...
    public static final int COLUMN_DOWN = 6;            // ... до y−6 (і дах, і підлогу всередині будівлі)
    public static final int ROOF_HEIGHT = 6;            // дах шукає не вище стількох блоків над головою
    public static final int MIN_WALLS = 2;              // скільки боків мають бути закриті
    public static final int WALL_DISTANCE = 3;          // стіна — не далі стількох блоків
    public static final int RECHECK_SECONDS = 30;       // як часто перевіряє, чи дім ще годиться
    public static final double MAX_HOME_DISTANCE = 64;  // дім далі цього — забуває його

    private final HungryCow cow;
    private BlockPos home = null;
    private BlockPos treeSpot = null;   // запасне місце для сну, якщо дому немає: під деревом чи будь-яким накриттям
    private int homeScore = 0;
    private int seconds = 0;
    private int sweep = 0;
    private int lastUnreachableTell = -100000;
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

    public BlockPos treeSpot() {
        return treeSpot;
    }

    // ---------- перевірки місць ----------

    // Чи може корова тут стояти: тверде під ногами, два блоки повітря (корова ~1,4 блока)
    public static boolean standable(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.isSolid() && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir();
    }

    // Тінь: над головою не видно неба
    public static boolean shaded(Level level, BlockPos pos) {
        return !level.canSeeSky(pos.above());
    }

    // Справжній будівельний блок (тверде, але не листя: листя дає тінь, але не захищає)
    private static boolean building(BlockState s) {
        return s.isSolid() && !s.is(BlockTags.LEAVES);
    }

    // Скільки боків закрито стінами (до WALL_DISTANCE блоків); листя не рахується
    public static int walls(Level level, BlockPos pos) {
        int n = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int i = 1; i <= WALL_DISTANCE; i++) {
                BlockPos p = pos.relative(d, i);
                if (building(level.getBlockState(p)) || building(level.getBlockState(p.above()))) {
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
            BlockState s = level.getBlockState(pos.above(i));
            if (s.isAir()) continue;
            return building(s);
        }
        return false;
    }

    // Наскільки добре місце як дім (0 = не годиться): дах +3, кожна стіна +2, усі 4 стіни ще +2 (до 13)
    public static int shelterScore(Level level, BlockPos pos) {
        if (!standable(level, pos) || !hasRoof(level, pos)) return 0;
        int w = walls(level, pos);
        if (w < MIN_WALLS) return 0;
        return 3 + 2 * w + (w == 4 ? 2 : 0);
    }

    // Чи може корова туди дійти (наприклад, у хлів із зачиненими дверима — ні)
    private boolean reachable(BlockPos p) {
        net.minecraft.world.level.pathfinder.Path path = cow.getNavigation().createPath(p, 1);
        return path != null && path.canReach();
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
            if (s > homeScore && !reachable(p)) {
                // для перевірки: бачить гарне укриття, але не може туди дійти (вхід закритий чи загороджений)
                if (cow.tickCount - lastUnreachableTell > UNREACHABLE_TELL_TICKS) {
                    lastUnreachableTell = cow.tickCount;
                    cow.tell(cow.family.name + " бачить дім " + p.getX() + " " + p.getY() + " " + p.getZ()
                            + " (бали " + s + "), але не може дійти — перевір вхід");
                }
                continue;
            }
            if (s > homeScore) {
                cow.tell(cow.family.name + (home == null ? " знайшла дім: " : " знайшла кращий дім: ")
                        + p.getX() + " " + p.getY() + " " + p.getZ() + " (бали " + s + ")");
                home = p.immutable();
                homeScore = s;
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
            } else {
                homeScore = s;
            }
        }
        // огляд околиць: шукає дім або кращий (закритіший) дім — навмання й по черзі
        BlockPos o = cow.blockPosition();
        int side = SEARCH_RADIUS * 2 + 1;
        // ідеальний дім уже є — не шукає; добрий є — шукає вчетверо рідше (щоб не навантажувати гру)
        if (homeScore >= BEST_SCORE) return;
        int checks = SAMPLES_PER_SECOND + SWEEP_PER_SECOND;
        if (home != null) checks /= 4;
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
        }
        if (treeSpot != null) tag.putLong("TreeSpot", treeSpot.asLong());
    }

    public void load(CompoundTag tag) {
        home = tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null;
        homeScore = tag.getInt("HomeScore");
        treeSpot = tag.contains("TreeSpot") ? BlockPos.of(tag.getLong("TreeSpot")) : null;
    }
}
