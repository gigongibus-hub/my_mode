package com.gigongibus.hungrycows;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Таймер бруду сіна (Fij): скільки секунд корови простояли на кожному блоці сіна мода. Корова стоїть — таймер іде,
 * пішла — стоїть на місці (не скидається). Зберігається разом зі світом. Пороги — у таблиці `CowHayBlock`.
 */
public class HayWear extends SavedData {
    private static final String NAME = HungryCowsMod.MODID + "_hay_wear";

    private final Map<Long, Integer> seconds = new HashMap<>();

    public static HayWear get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(HayWear::load, HayWear::new, NAME);
    }

    public int get(BlockPos pos) {
        return seconds.getOrDefault(pos.asLong(), 0);
    }

    public void set(BlockPos pos, int value) {
        seconds.put(pos.asLong(), value);
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (seconds.remove(pos.asLong()) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Integer> e : seconds.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", e.getKey());
            c.putInt("Seconds", e.getValue());
            list.add(c);
        }
        tag.put("Entries", list);
        return tag;
    }

    public static HayWear load(CompoundTag tag) {
        HayWear data = new HayWear();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            data.seconds.put(c.getLong("Pos"), c.getInt("Seconds"));
        }
        return data;
    }
}
