package com.gigongibus.hungrycows;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Годинник «світу мода» (Fij, 14.11): усе, що росте й псується само (трава, мох, квіти, стежки, сіно), іде за цим годинником.
 * Його можна прискорити (`/hungrycows speed N`) — корів, інших мобів і час доби це НЕ прискорює.
 * Ріст трави можна зупинити й знову ввімкнути (`/hungrycows grass off|on`): трава тоді має свій годинник, що стоїть.
 * Зберігається разом зі світом (окремо для кожного виміру).
 */
public class WorldClock extends SavedData {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int SPEED_MAX = 1000;   // найбільше прискорення

    private static final String NAME = HungryCowsMod.MODID + "_clock";

    private long clock = -1;        // «тіки світу мода» (звичайно = ігровий час)
    private long grassClock = -1;   // годинник трави (стоїть, коли ріст вимкнено)
    private int speed = 1;
    private boolean grassGrowth = true;

    public static WorldClock get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(WorldClock::load, WorldClock::new, NAME);
    }

    // щотіку з ModForgeEvents.onLevelTick
    public static void tick(ServerLevel level) {
        WorldClock c = get(level);
        c.init(level);
        c.clock += c.speed;
        if (c.grassGrowth) c.grassClock += c.speed;
        if (level.getGameTime() % 200 == 0) c.setDirty();   // зберігати не щотіку
    }

    private void init(ServerLevel level) {
        if (clock < 0) clock = level.getGameTime();          // старий світ: починаємо з ігрового часу (старі записи не зсуваються)
        if (grassClock < 0) grassClock = level.getGameTime();
    }

    public static long now(ServerLevel level) { WorldClock c = get(level); c.init(level); return c.clock; }
    public static long grassNow(ServerLevel level) { WorldClock c = get(level); c.init(level); return c.grassClock; }
    public static int speed(ServerLevel level) { return get(level).speed; }
    public static boolean grassGrowth(ServerLevel level) { return get(level).grassGrowth; }

    public static void setSpeed(ServerLevel level, int speed) {
        WorldClock c = get(level);
        c.speed = Math.max(1, Math.min(SPEED_MAX, speed));
        c.setDirty();
    }

    public static void setGrassGrowth(ServerLevel level, boolean on) {
        WorldClock c = get(level);
        c.grassGrowth = on;
        c.setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("Clock", clock);
        tag.putLong("GrassClock", grassClock);
        tag.putInt("Speed", speed);
        tag.putBoolean("GrassGrowth", grassGrowth);
        return tag;
    }

    public static WorldClock load(CompoundTag tag) {
        WorldClock c = new WorldClock();
        c.clock = tag.contains("Clock") ? tag.getLong("Clock") : -1;
        c.grassClock = tag.contains("GrassClock") ? tag.getLong("GrassClock") : -1;
        c.speed = Math.max(1, tag.getInt("Speed"));
        c.grassGrowth = !tag.contains("GrassGrowth") || tag.getBoolean("GrassGrowth");
        return c;
    }
}
