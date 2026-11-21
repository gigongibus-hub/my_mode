package com.gigongibus.hungrycows.phys;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/**
 * Колоди поваленого дерева, що вже лягли блоками (Fij, 20.11: «через час стає простим блоком, але якщо його вдарити — стане
 * фізичним»). Світ пам'ятає, які колоди — з поваленого дерева: такий блок від удару (ЛКМ без сокири) чи коли з-під нього
 * прибрали опору знову стає фізичним тілом разом із сусідніми колодами й листям.
 */
public class FallenLogs extends SavedData {
    private static final String NAME = "hungrycows_fallen_logs";
    private final Set<Long> logs = new HashSet<>();

    public static FallenLogs get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FallenLogs::load, FallenLogs::new, NAME);
    }

    public void mark(BlockPos pos) {
        if (logs.add(pos.asLong())) setDirty();
    }

    public void unmark(BlockPos pos) {
        if (logs.remove(pos.asLong())) setDirty();
    }

    /** Колода з поваленого дерева (і досі колода — інакше забути) */
    public boolean isFallen(ServerLevel level, BlockPos pos) {
        if (!logs.contains(pos.asLong())) return false;
        if (level.getBlockState(pos).is(BlockTags.LOGS)) return true;
        unmark(pos);
        return false;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray("Logs", logs.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    private static FallenLogs load(CompoundTag tag) {
        FallenLogs f = new FallenLogs();
        for (long l : tag.getLongArray("Logs")) f.logs.add(l);
        return f;
    }
}
