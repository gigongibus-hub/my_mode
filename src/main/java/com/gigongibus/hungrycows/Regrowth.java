package com.gigongibus.hungrycows;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Відростання з'їденого: трав'яний блок, мох, низька трава, низька → висока.
 * Пам'ятає місця й час окремо для кожного світу (зберігається разом зі світом).
 * Якщо гравець зламав або поставив блок на цьому місці, відлік скасовується.
 * Усі числа зібрані на початку файлу.
 */
public class Regrowth extends SavedData {

    // ================= ТАБЛИЦЯ ЧИСЕЛ (1 хв = 1200 тіків) =================
    public static final int GRASS_MIN_TICKS = 4 * 1200;     // трав'яний блок відростає за 4–6 хв
    public static final int GRASS_MAX_TICKS = 6 * 1200;
    public static final int MOSS_MIN_TICKS = 8 * 1200;      // мох відростає за 8–12 хв
    public static final int MOSS_MAX_TICKS = 12 * 1200;
    public static final float PLANT_CHANCE = 0.30F;         // шанс, що на трав'яному блоці виросте низька трава
    public static final int PLANT_MIN_TICKS = 3 * 1200;     // ... через 3–5 хв
    public static final int PLANT_MAX_TICKS = 5 * 1200;
    public static final int TALL_TICKS = 5 * 1200;
    public static final int FLOWER_MIN_TICKS = 5 * 1200;    // з'їдена квітка відростає за 5–8 хв
    public static final int FLOWER_MAX_TICKS = 8 * 1200;          // низька трава стає високою через 5 хв
    public static final int CHECK_EVERY_TICKS = 20;         // як часто перевіряти, чи щось уже має відрости

    private static final String NAME = HungryCowsMod.MODID + "_regrowth";

    private enum Kind { GRASS, MOSS, SHORT_PLANT, TALL_PLANT, FLOWER }

    private static class Entry {
        final Kind kind;
        final long due;          // ігровий час, коли відросте
        final String expected;   // який блок має бути на місці (інакше гравець його змінив)
        final String payload;    // що виросте (для квітки — яка саме)

        Entry(Kind kind, long due, String expected, String payload) {
            this.kind = kind;
            this.due = due;
            this.expected = expected;
            this.payload = payload;
        }
    }

    private final Map<Long, Entry> entries = new HashMap<>();

    // ---------- доступ ----------

    public static Regrowth get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Regrowth::load, Regrowth::new, NAME);
    }

    // Корова з'їла трав'яний блок (тепер тут земля)
    public static void grassEaten(ServerLevel level, BlockPos pos) {
        get(level).add(level, pos, Kind.GRASS, Blocks.DIRT, GRASS_MIN_TICKS, GRASS_MAX_TICKS);
    }

    // Корова з'їла моховий блок (тепер тут soil)
    public static void mossEaten(ServerLevel level, BlockPos pos, Block soil) {
        get(level).add(level, pos, Kind.MOSS, soil, MOSS_MIN_TICKS, MOSS_MAX_TICKS);
    }

    // Корова з'їла рослину-траву: на трав'яному блоці знову може вирости низька трава
    public static void plantEaten(ServerLevel level, BlockPos plantPos) {
        if (level.getRandom().nextFloat() < PLANT_CHANCE) {
            get(level).add(level, plantPos, Kind.SHORT_PLANT, Blocks.AIR, PLANT_MIN_TICKS, PLANT_MAX_TICKS);
        }
    }

    // Корова з'їла квітку: через 5–8 хв на тому самому місці виросте така сама
    public static void flowerEaten(ServerLevel level, BlockPos pos, Block flower) {
        Regrowth data = get(level);
        RandomSource r = level.getRandom();
        long due = level.getGameTime() + FLOWER_MIN_TICKS + r.nextInt(FLOWER_MAX_TICKS - FLOWER_MIN_TICKS + 1);
        data.entries.put(pos.asLong(), new Entry(Kind.FLOWER, due, id(Blocks.AIR), id(flower)));
        data.setDirty();
    }

    // Гравець зламав або поставив блок: на цьому місці нічого не відросте
    public static void blockChanged(ServerLevel level, BlockPos pos) {
        Regrowth data = get(level);
        if (data.entries.remove(pos.asLong()) != null) data.setDirty();
        if (data.entries.remove(pos.above().asLong()) != null) data.setDirty();   // рослина над цим блоком
    }

    private void add(ServerLevel level, BlockPos pos, Kind kind, Block expected, int minTicks, int maxTicks) {
        RandomSource r = level.getRandom();
        long due = level.getGameTime() + minTicks + r.nextInt(Math.max(1, maxTicks - minTicks + 1));
        entries.put(pos.asLong(), new Entry(kind, due, id(expected), ""));
        setDirty();
    }

    // ---------- щосекунди ----------

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % CHECK_EVERY_TICKS != 0) return;
        Regrowth data = get(level);
        if (data.entries.isEmpty()) return;
        long now = level.getGameTime();
        List<Map.Entry<Long, Entry>> due = new ArrayList<>();
        for (Iterator<Map.Entry<Long, Entry>> it = data.entries.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Entry> e = it.next();
            if (e.getValue().due > now) continue;
            BlockPos pos = BlockPos.of(e.getKey());
            if (!level.isLoaded(pos)) continue;   // чанк не завантажений: відросте, коли гравець прийде
            it.remove();
            due.add(e);
        }
        if (due.isEmpty()) return;
        data.setDirty();
        for (Map.Entry<Long, Entry> e : due) {
            data.grow(level, BlockPos.of(e.getKey()), e.getValue());
        }
    }

    private void grow(ServerLevel level, BlockPos pos, Entry e) {
        BlockState state = level.getBlockState(pos);
        if (!id(state.getBlock()).equals(e.expected)) return;   // блок змінили: не відростає
        switch (e.kind) {
            case GRASS -> {
                if (!level.getBlockState(pos.above()).isAir()) return;   // щось накриває
                level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
                plantEaten(level, pos.above());   // з шансом 30% потім виросте низька трава
            }
            case MOSS -> {
                if (!level.getBlockState(pos.above()).isAir()) return;
                level.setBlockAndUpdate(pos, Blocks.MOSS_BLOCK.defaultBlockState());
            }
            case SHORT_PLANT -> {
                if (!level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)) return;
                level.setBlockAndUpdate(pos, Blocks.GRASS.defaultBlockState());
                add(level, pos, Kind.TALL_PLANT, Blocks.GRASS, TALL_TICKS, TALL_TICKS);
            }
            case TALL_PLANT -> {
                if (!level.getBlockState(pos.above()).isAir()) return;   // немає місця для верхньої половини
                DoublePlantBlock.placeAt(level, Blocks.TALL_GRASS.defaultBlockState(), pos, 3);
            }
            case FLOWER -> {
                Block flower = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(e.payload));
                if (flower == null || flower == Blocks.AIR) return;
                BlockState fs = flower.defaultBlockState();
                if (!fs.canSurvive(level, pos)) return;   // тут квітка вже не виросте (немає трави чи землі)
                if (flower instanceof DoublePlantBlock) {
                    if (!level.getBlockState(pos.above()).isAir()) return;
                    DoublePlantBlock.placeAt(level, fs, pos, 3);
                } else {
                    level.setBlockAndUpdate(pos, fs);
                }
            }
        }
    }

    // ---------- збереження ----------

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Entry> e : entries.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", e.getKey());
            c.putString("Kind", e.getValue().kind.name());
            c.putLong("Due", e.getValue().due);
            c.putString("Expected", e.getValue().expected);
            c.putString("Payload", e.getValue().payload);
            list.add(c);
        }
        tag.put("Entries", list);
        return tag;
    }

    public static Regrowth load(CompoundTag tag) {
        Regrowth data = new Regrowth();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            try {
                Kind kind = Kind.valueOf(c.getString("Kind"));
                data.entries.put(c.getLong("Pos"), new Entry(kind, c.getLong("Due"), c.getString("Expected"), c.getString("Payload")));
            } catch (IllegalArgumentException ignored) {
                // невідомий запис зі старої версії: пропускаємо
            }
        }
        return data;
    }

    private static String id(Block block) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
        return key == null ? "" : key.toString();
    }
}
