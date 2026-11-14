package com.gigongibus.hungrycows;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Рідкісні корови (план Fij, етап 10): корови з'являються ЛИШЕ в селах жителів — 1–6 на село, коли село створюється (новий
 * чанк із початком села). У полях самі не з'являються; ванільних корів немає зовсім (ModForgeEvents.onEntityJoin).
 * Села, що чекають корів, зберігаються в світі (SavedData), щоб перезапуск не загубив їх.
 */
public class VillageCows extends SavedData {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int MIN_COWS = 1, MAX_COWS = 6;   // скільки корів на село
    public static final int SPREAD = 8;                   // з'являються до 8 бл. від середини села
    public static final int PROCESS_EVERY = 40;           // раз на 2 с перевіряє, чи вже завантажено місце

    private static final String NAME = "hungrycows_village_cows";
    private final List<Long> pending = new ArrayList<>();   // середини сіл, що ще чекають корів

    public static VillageCows get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(VillageCows::load, VillageCows::new, NAME);
    }

    private static VillageCows load(CompoundTag tag) {
        VillageCows v = new VillageCows();
        for (Tag t : tag.getList("Pending", Tag.TAG_LONG)) v.pending.add(((LongTag) t).getAsLong());
        return v;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag l = new ListTag();
        for (long p : pending) l.add(LongTag.valueOf(p));
        tag.put("Pending", l);
        return tag;
    }

    // Новий чанк: якщо в ньому починається село — запам'ятати його середину
    public static void onNewChunk(ServerLevel level, ChunkAccess chunk) {
        for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
            StructureStart start = e.getValue();
            if (start == null || !start.isValid()) continue;
            var key = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).wrapAsHolder(e.getKey());
            if (!key.is(StructureTags.VILLAGE)) continue;
            if (!start.getChunkPos().equals(chunk.getPos())) continue;
            BlockPos c = start.getBoundingBox().getCenter();
            VillageCows v = get(level);
            v.pending.add(c.asLong());
            v.setDirty();
        }
    }

    // Раз на 2 с: де місце села вже завантажене — поставити корів
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % PROCESS_EVERY != 0) return;
        VillageCows v = get(level);
        if (v.pending.isEmpty()) return;
        boolean changed = v.pending.removeIf(p -> {
            BlockPos c = BlockPos.of(p);
            if (!level.isLoaded(c) || !level.hasChunk(c.getX() >> 4, c.getZ() >> 4)) return false;
            spawnCows(level, c);
            return true;
        });
        if (changed) v.setDirty();
    }

    public static List<HungryCow> spawnCows(ServerLevel level, BlockPos center) {
        int n = MIN_COWS + level.getRandom().nextInt(MAX_COWS - MIN_COWS + 1);
        int done = 0;
        List<HungryCow> made = new ArrayList<>();
        for (int i = 0; i < n * 4 && done < n; i++) {
            int x = center.getX() + level.getRandom().nextInt(SPREAD * 2 + 1) - SPREAD;
            int z = center.getZ() + level.getRandom().nextInt(SPREAD * 2 + 1) - SPREAD;
            if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
            // місце, де можна стати: від карти висот, а якщо там не можна — біля висоти середини села (±6)
            BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!CowHome.standable(level, at)) {
                at = null;
                for (int dy = 6; dy >= -6 && at == null; dy--) {
                    BlockPos p = new BlockPos(x, center.getY() + dy, z);
                    if (CowHome.standable(level, p)) at = p;
                }
            }
            if (at == null || level.getFluidState(at.below()).isSource()) continue;
            int y = at.getY();
            HungryCow cow = ModEntities.HUNGRY_COW.get().create(level);
            if (cow == null) continue;
            cow.moveTo(x + 0.5, y, z + 0.5, level.getRandom().nextFloat() * 360F, 0);
            cow.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null, null);
            if (level.addFreshEntity(cow)) made.add(cow);
            done++;
        }
        return made;
    }
}
