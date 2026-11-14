package com.gigongibus.hungrycows;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Утоптані стежки (ідея Fij): де корови часто ходять додому, трав'яний блок стає стежкою.
 * Рахуються лише кроки на дорозі додому (не звичайне пасіння), щоб не витоптати все пасовище.
 * Давно не ходили цим блоком — лічильник забувається. Зберігається разом зі світом.
 * Усі числа зібрані на початку файлу.
 */
public class Trails extends SavedData {

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int STEPS_TO_TRAIL = 36;             // стільки кроків корів на блоці — і він стає стежкою (Fij: «довше», було 12)
    public static final long FORGET_TICKS = 3 * 24000L;      // 3 доби ніхто не ступав — лічильник з нуля
    public static final int CLEANUP_OVER = 5000;             // записів більше — прибрати старі
    public static final long REGROW_AFTER_TICKS = 3 * 24000L; // Fij (варіант А): стежкою 3 доби не ходили — може зарости травою

    private static final String NAME = HungryCowsMod.MODID + "_trails";

    private static class Entry {
        int steps;
        long last;

        Entry(int steps, long last) {
            this.steps = steps;
            this.last = last;
        }
    }

    private final Map<Long, Entry> entries = new HashMap<>();
    private final Map<Long, Long> paths = new HashMap<>();   // стежки, витоптані коровами: коли по них ходили востаннє

    public static Trails get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Trails::load, Trails::new, NAME);
    }

    // Корова ступила на блок ground (дорогою додому)
    public static void step(ServerLevel level, BlockPos ground) {
        Trails data = get(level);
        long now = level.getGameTime();
        if (level.getBlockState(ground).is(Blocks.DIRT_PATH)) {   // ходять стежкою — вона не заростає
            if (data.paths.containsKey(ground.asLong())) { data.paths.put(ground.asLong(), now); data.setDirty(); }
            return;
        }
        if (!level.getBlockState(ground).is(Blocks.GRASS_BLOCK)) return;
        Entry e = data.entries.get(ground.asLong());
        if (e == null || now - e.last > FORGET_TICKS) {
            e = new Entry(0, now);
            data.entries.put(ground.asLong(), e);
        }
        e.steps++;
        e.last = now;
        data.setDirty();
        if (e.steps >= STEPS_TO_TRAIL) {
            data.entries.remove(ground.asLong());
            BlockPos above = ground.above();
            BlockState up = level.getBlockState(above);
            if (!up.isAir()) {
                if (!up.canBeReplaced()) return;          // щось тверде зверху: стежки не буде
                level.destroyBlock(above, false);         // трава чи квітка витоптана
            }
            level.setBlockAndUpdate(ground, Blocks.DIRT_PATH.defaultBlockState());
            data.paths.put(ground.asLong(), now);
        }
        if (data.entries.size() > CLEANUP_OVER) {
            data.entries.values().removeIf(x -> now - x.last > FORGET_TICKS);
        }
    }

    /** Стежку корів давно не топтали — може зарости (стежки гравця не заростають) */
    public static boolean canRegrow(ServerLevel level, BlockPos path) {
        Long last = get(level).paths.get(path.asLong());
        return last != null && level.getGameTime() - last > REGROW_AFTER_TICKS;
    }

    public static void regrown(ServerLevel level, BlockPos path) {
        Trails data = get(level);
        if (data.paths.remove(path.asLong()) != null) data.setDirty();
    }

    /** Для тестів: стежка корів, останній крок — when */
    public static void markPath(ServerLevel level, BlockPos path, long when) {
        Trails data = get(level);
        data.paths.put(path.asLong(), when);
        data.setDirty();
    }

    // ---------- збереження ----------

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Entry> e : entries.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", e.getKey());
            c.putInt("Steps", e.getValue().steps);
            c.putLong("Last", e.getValue().last);
            list.add(c);
        }
        tag.put("Entries", list);
        ListTag pl = new ListTag();
        for (Map.Entry<Long, Long> e : paths.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", e.getKey());
            c.putLong("Last", e.getValue());
            pl.add(c);
        }
        tag.put("Paths", pl);
        return tag;
    }

    public static Trails load(CompoundTag tag) {
        Trails data = new Trails();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            data.entries.put(c.getLong("Pos"), new Entry(c.getInt("Steps"), c.getLong("Last")));
        }
        ListTag pl = tag.getList("Paths", Tag.TAG_COMPOUND);
        for (int i = 0; i < pl.size(); i++) {
            CompoundTag c = pl.getCompound(i);
            data.paths.put(c.getLong("Pos"), c.getLong("Last"));
        }
        return data;
    }
}
