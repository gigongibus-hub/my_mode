package com.gigongibus.hungrycows;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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
    public static final int SAMPLES_PER_SECOND = 16;    // скільки випадкових місць оглядає щосекунди
    public static final int MIN_WALLS = 2;              // скільки боків мають бути закриті
    public static final int WALL_DISTANCE = 3;          // стіна — не далі стількох блоків
    public static final int RECHECK_SECONDS = 30;       // як часто перевіряє, чи дім ще годиться
    public static final double MAX_HOME_DISTANCE = 64;  // дім далі цього — забуває його

    private final HungryCow cow;
    private BlockPos home = null;
    private int homeScore = 0;
    private int seconds = 0;

    public CowHome(HungryCow cow) {
        this.cow = cow;
    }

    public BlockPos home() {
        return home;
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

    // Скільки боків закрито стінами (до WALL_DISTANCE блоків)
    public static int walls(Level level, BlockPos pos) {
        int n = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int i = 1; i <= WALL_DISTANCE; i++) {
                BlockPos p = pos.relative(d, i);
                if (level.getBlockState(p).isSolid() || level.getBlockState(p.above()).isSolid()) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    // Наскільки добре місце як дім (0 = не годиться)
    public static int shelterScore(Level level, BlockPos pos) {
        if (!standable(level, pos) || !shaded(level, pos)) return 0;
        int w = walls(level, pos);
        return w >= MIN_WALLS ? 1 + w : 0;   // 3..5
    }

    // Знайти місце, де можна стати на висоті поблизу y (від y+4 до y−6)
    private BlockPos groundNear(int x, int y, int z) {
        Level level = cow.level();
        for (int dy = 4; dy >= -6; dy--) {
            BlockPos p = new BlockPos(x, y + dy, z);
            if (standable(level, p)) return p;
        }
        return null;
    }

    // Випадкове місце в тіні поруч (для відпочинку в сонячний день)
    public BlockPos findShade(int radius, int tries) {
        BlockPos o = cow.blockPosition();
        for (int i = 0; i < tries; i++) {
            int x = o.getX() + cow.getRandom().nextInt(radius * 2 + 1) - radius;
            int z = o.getZ() + cow.getRandom().nextInt(radius * 2 + 1) - radius;
            BlockPos p = groundNear(x, o.getY(), z);
            if (p != null && shaded(cow.level(), p)) return p;
        }
        return null;
    }

    // ---------- раз на секунду ----------

    public void secondTick() {
        if (cow.isBaby()) return;   // теля спить біля матері
        seconds++;
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
        // огляд околиць: шукає дім або кращий (закритіший) дім
        BlockPos o = cow.blockPosition();
        for (int i = 0; i < SAMPLES_PER_SECOND; i++) {
            int x = o.getX() + cow.getRandom().nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
            int z = o.getZ() + cow.getRandom().nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
            BlockPos p = groundNear(x, o.getY(), z);
            if (p == null) continue;
            int s = shelterScore(cow.level(), p);
            if (s > homeScore) {
                if (home == null) cow.tell(cow.family.name + " знайшла дім: " + p.getX() + " " + p.getY() + " " + p.getZ());
                home = p.immutable();
                homeScore = s;
            }
        }
    }

    // ---------- збереження ----------

    public void save(CompoundTag tag) {
        if (home != null) {
            tag.putLong("Home", home.asLong());
            tag.putInt("HomeScore", homeScore);
        }
    }

    public void load(CompoundTag tag) {
        home = tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null;
        homeScore = tag.getInt("HomeScore");
    }
}
