package com.gigongibus.hungrycows;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Котли з водою: повного котла (3 рівні) вистачає корові на SIPS_PER_CAULDRON разів.
 * Пам'ятає, скільки разів пили з кожного котла (зберігається разом зі світом).
 */
public class WaterSources extends SavedData {

    public static final int SIPS_PER_CAULDRON = 10;      // скільки разів можна попити з повного котла
    private static final String NAME = HungryCowsMod.MODID + "_water";

    private final Map<Long, Integer> sips = new HashMap<>();   // котел → скільки разів уже пили

    public static WaterSources get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(WaterSources::load, WaterSources::new, NAME);
    }

    // Скільки разів уже пили з котла за поточного рівня, якщо він повний (3 рівні = 10 разів)
    private static int sipsForLevel(int fill) {
        // рівень 3: 0–2 рази випито; рівень 2: 3–6; рівень 1: 7–9
        return switch (fill) {
            case 3 -> 0;
            case 2 -> 3;
            default -> 7;
        };
    }

    // Попити з котла. true — вдалося (і, можливо, рівень води знизився)
    public static boolean sip(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(LayeredCauldronBlock.LEVEL)) return false;
        int fill = state.getValue(LayeredCauldronBlock.LEVEL);
        WaterSources data = get(level);
        long key = pos.asLong();
        int taken = data.sips.getOrDefault(key, 0);
        // гравець долив воду: відлік за поточним рівнем
        if (taken < sipsForLevel(fill)) taken = sipsForLevel(fill);
        taken++;
        // рівень знижується після 3-го, 7-го і 10-го разу
        int shouldBe = taken >= SIPS_PER_CAULDRON ? 0 : taken >= 7 ? 1 : taken >= 3 ? 2 : 3;
        if (shouldBe < fill) LayeredCauldronBlock.lowerFillLevel(state, level, pos);
        if (taken >= SIPS_PER_CAULDRON) data.sips.remove(key);
        else data.sips.put(key, taken);
        data.setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        sips.forEach((k, v) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", k);
            c.putInt("Sips", v);
            list.add(c);
        });
        tag.put("Cauldrons", list);
        return tag;
    }

    public static WaterSources load(CompoundTag tag) {
        WaterSources data = new WaterSources();
        ListTag list = tag.getList("Cauldrons", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            data.sips.put(c.getLong("Pos"), c.getInt("Sips"));
        }
        return data;
    }
}
