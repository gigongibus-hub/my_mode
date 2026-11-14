package com.gigongibus.hungrycows;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Гриби на сіні (Fij, 14.11): ПКМ по сіну з грибом — 1 гриб, щонайбільше 3; після третього гриб із сіна зникає (сіно гниле),
 * але місце «пам'ятає» гриб і він відростає, поки поруч волога. Пам'ять зберігається разом зі світом.
 */
public class FungusPatches extends SavedData {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int MAX_PICKS = 3;              // з одного сіна — щонайбільше 3 гриби, потім гриб зникає
    public static final int WATER_RADIUS = 3;           // вода — не далі 3 бл.
    public static final int FIRE_WATER_NEED = 1;        // вогнегриб: хоч 1 блок води поруч (або дощ) ...
    public static final int FIRE_DAYS = 3;              // ... відростає за 3 ігрові доби вологи
    public static final int STAR_WATER_NEED = 2;        // зірочник: хоч 2 блоки води (дощ рахується як один) ...
    public static final int STAR_DAYS = 4;              // ... відростає за 4 доби
    public static final int SECONDS_PER_DAY = 1200;

    private static final String NAME = HungryCowsMod.MODID + "_fungus_patches";

    private static class Patch {
        CowHayBlock.Cond kind;   // який гриб тут ріс
        int picks;               // скільки разів зібрали
        int grown;               // скільки секунд вологи вже минуло (коли гриб зник)
        Patch(CowHayBlock.Cond kind, int picks, int grown) { this.kind = kind; this.picks = picks; this.grown = grown; }
    }

    private final Map<Long, Patch> patches = new HashMap<>();

    public static FungusPatches get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FungusPatches::load, FungusPatches::new, NAME);
    }

    /** Гравець зібрав гриб із сіна. Повертає новий стан блока (гниле — коли зібрано третій) */
    public static BlockState pick(ServerLevel level, BlockPos pos, BlockState state) {
        CowHayBlock.Cond kind = state.getValue(CowHayBlock.COND);
        FungusPatches data = get(level);
        Patch p = data.patches.computeIfAbsent(pos.asLong(), k -> new Patch(kind, 0, 0));
        p.kind = kind;
        p.picks++;
        p.grown = 0;
        data.setDirty();
        return p.picks >= MAX_PICKS ? state.setValue(CowHayBlock.COND, CowHayBlock.Cond.ROTTEN) : state;
    }

    public static int picks(ServerLevel level, BlockPos pos) {
        Patch p = get(level).patches.get(pos.asLong());
        return p == null ? 0 : p.picks;
    }

    /** Раз на 5 с (з `CowHayBlock.check`): гриб відростає, поки волого. true — блок змінено */
    public static boolean tick(ServerLevel level, BlockPos pos, BlockState state, int seconds) {
        FungusPatches data = get(level);
        Patch p = data.patches.get(pos.asLong());
        if (p == null || p.picks < MAX_PICKS) return false;
        if (state.getValue(CowHayBlock.COND) != CowHayBlock.Cond.ROTTEN) {   // сіно вже інше — пам'ять стерти
            data.patches.remove(pos.asLong());
            data.setDirty();
            return false;
        }
        boolean star = p.kind == CowHayBlock.Cond.STAR_FUNGUS;
        if (moisture(level, pos) < (star ? STAR_WATER_NEED : FIRE_WATER_NEED)) return false;   // сухо — не росте
        p.grown += seconds;
        data.setDirty();
        if (p.grown < (star ? STAR_DAYS : FIRE_DAYS) * SECONDS_PER_DAY) return false;
        data.patches.remove(pos.asLong());   // відріс: знову можна зібрати 3
        level.setBlockAndUpdate(pos, state.setValue(CowHayBlock.COND, p.kind));
        return true;
    }

    /** Скільки «води» поруч: блоки води не далі 3 бл. + дощ на сіно (як один блок) */
    public static int moisture(ServerLevel level, BlockPos pos) {
        int n = CowHayBlock.rainedOn(level, pos) ? 1 : 0;
        for (BlockPos q : BlockPos.betweenClosed(pos.offset(-WATER_RADIUS, -WATER_RADIUS, -WATER_RADIUS), pos.offset(WATER_RADIUS, WATER_RADIUS, WATER_RADIUS))) {
            if (q.equals(pos)) continue;
            FluidState f = level.getFluidState(q);
            if (f.is(Fluids.WATER) || f.is(Fluids.FLOWING_WATER)) {
                if (++n >= STAR_WATER_NEED) return n;
            }
        }
        return n;
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        FungusPatches data = get(level);
        if (data.patches.remove(pos.asLong()) != null) data.setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Patch> e : patches.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", e.getKey());
            c.putString("Kind", e.getValue().kind.getSerializedName());
            c.putInt("Picks", e.getValue().picks);
            c.putInt("Grown", e.getValue().grown);
            list.add(c);
        }
        tag.put("Patches", list);
        return tag;
    }

    public static FungusPatches load(CompoundTag tag) {
        FungusPatches data = new FungusPatches();
        ListTag list = tag.getList("Patches", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            CowHayBlock.Cond kind = "star_fungus".equals(c.getString("Kind")) ? CowHayBlock.Cond.STAR_FUNGUS : CowHayBlock.Cond.FIRE_FUNGUS;
            data.patches.put(c.getLong("Pos"), new Patch(kind, c.getInt("Picks"), c.getInt("Grown")));
        }
        return data;
    }
}
