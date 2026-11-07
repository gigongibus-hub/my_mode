package com.gigongibus.hungrycows;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Шкала сну і дорога додому, яку корова запам'ятовує (ідеї Fij).
 * Шкала: удень 0; увечері +1 кожні 2 с, уночі +2 кожні 2 с; сонна корова трохи повільніша.
 * Корова має встигнути дійти додому й лягти; шкала дійшла до 100 — засинає, де є.
 * Дорога: коли корова вперше дійшла додому, вона запам'ятовує точки дороги (кожні 5 блоків); наступного разу
 * йде від точки до точки (короткі шматки гра знаходить легко). Що частіше ходить — то довше пам'ятає.
 * Усі числа зібрані на початку файлу.
 */
public class CowSleep {

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int EVENING_FROM = 12000;        // вечір (захід сонця) ...
    public static final int NIGHT_FROM = 13000;          // ... ніч ...
    public static final int NIGHT_UNTIL = 23500;         // ... до світанку
    public static final float EVENING_GAIN = 1F / 2F;    // увечері +1 кожні 2 с (тобто +0,5 щосекунди)
    public static final float NIGHT_GAIN = 2F / 2F;      // уночі +2 кожні 2 с
    public static final float MAX = 100F;                // шкала повна — засинає, де є
    public static final double MAX_SLOWDOWN = 0.10;      // сонна — до 10% повільніша (пропорційно шкалі) ...
    public static final double FULL_SLOWDOWN = 0.50;     // ... а шкала повна (100) — удвічі повільніша (Fij; раніше засинала, де є)
    public static final int ROUTE_STEP = 5;              // точка дороги кожні 5 блоків
    public static final int ROUTE_MAX_POINTS = 40;       // щонайбільше стільки точок
    public static final double ROUTE_JOIN = 16;          // приєднується до дороги, якщо найближча точка не далі 16 блоків
    public static final double ROUTE_REACH = 2.0;        // точку пройдено = ближче 2 блоків
    public static final int ROUTE_MAX_STRENGTH = 10;     // що частіше ходить, то міцніше пам'ятає (до 10)
    public static final int ROUTE_FORGET_BASE_DAYS = 3;  // не ходила 3 доби + міцність — забуває дорогу

    private static final UUID SLEEPY_ID = UUID.fromString("5d6e7f80-91a2-4b3c-8d4e-5f60718293a4");

    private final HungryCow cow;
    public float sleepiness = 0;
    private double appliedSlow = 0;

    private final List<BlockPos> route = new ArrayList<>();
    private BlockPos routeBase = null;   // до якого дому ця дорога
    private int strength = 0;
    private long lastUsedDay = 0;

    public CowSleep(HungryCow cow) {
        this.cow = cow;
    }

    // ---------- шкала сну ----------

    public boolean full() {
        return sleepiness >= MAX;
    }

    public void secondTick() {
        if (cow.level().dimensionType().hasFixedTime() || !cow.level().dimensionType().hasSkyLight()) {
            sleepiness = 0;
        } else {
            long t = cow.level().getDayTime() % 24000L;
            if (t < EVENING_FROM || t >= NIGHT_UNTIL) {
                sleepiness = 0;                                   // день: спати не хоче
            } else if (!cow.sleeping) {
                sleepiness = Math.min(MAX, sleepiness + (t < NIGHT_FROM ? EVENING_GAIN : NIGHT_GAIN));
            }
        }
        // сонна — трохи повільніша (міняємо, лише коли різниця помітна)
        double slow = full() && !cow.sleeping ? -FULL_SLOWDOWN : -MAX_SLOWDOWN * sleepiness / MAX;
        if (Math.abs(slow - appliedSlow) >= 0.005 || (slow == 0 && appliedSlow != 0)) {
            AttributeInstance inst = cow.getAttribute(Attributes.MOVEMENT_SPEED);
            if (inst != null) {
                inst.removeModifier(SLEEPY_ID);
                if (slow != 0) inst.addTransientModifier(new AttributeModifier(SLEEPY_ID, "Sleepy", slow,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            appliedSlow = slow;
        }
        // давно не ходила дорогою — забуває
        if (!route.isEmpty() && day() - lastUsedDay > ROUTE_FORGET_BASE_DAYS + strength) forgetRoute();
    }

    // ---------- дорога додому ----------

    private long day() {
        long d = cow.level().getDayTime() / 24000L;
        if (d < lastUsedDay) lastUsedDay = d;   // час перевели назад командою
        return d;
    }

    // Почати йти дорогою до base: номер найближчої точки (не далі ROUTE_JOIN), або −1 — дороги немає
    public int joinRoute(BlockPos base) {
        if (route.isEmpty() || routeBase == null || !routeBase.equals(base)) return -1;
        int best = -1;
        double bestD = ROUTE_JOIN * ROUTE_JOIN;
        for (int i = 0; i < route.size(); i++) {
            BlockPos p = route.get(i);
            double d = cow.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    public BlockPos routePoint(int i) {
        return i >= 0 && i < route.size() ? route.get(i) : null;
    }

    public int routeSize() {
        return route.size();
    }

    // Дійшла додому своєю дорогою: пам'ятає міцніше
    public void reinforce() {
        strength = Math.min(ROUTE_MAX_STRENGTH, strength + 1);
        lastUsedDay = day();
    }

    // Дійшла додому вперше (або нова дорога): запам'ятати точки
    public void saveRoute(BlockPos base, List<BlockPos> points) {
        if (points.size() < 2) return;
        route.clear();
        int from = Math.max(0, points.size() - ROUTE_MAX_POINTS);   // задовга — лишає останні точки (ближчі до дому)
        route.addAll(points.subList(from, points.size()));
        routeBase = base.immutable();
        strength = 1;
        lastUsedDay = day();
        cow.tell(cow.family.name + " запам'ятала дорогу додому (" + route.size() + " точок)");
    }

    // Дорогу перегородили або дім змінився: забути
    public void forgetRoute() {
        if (!route.isEmpty()) cow.tell(cow.family.name + " забула дорогу додому");
        route.clear();
        routeBase = null;
        strength = 0;
    }

    public String debugText() {
        return "сон " + Math.round(sleepiness) + "/" + Math.round(MAX)
                + (route.isEmpty() ? "" : ", дорога " + route.size() + " т. (міцність " + strength + ")");
    }

    // ---------- збереження ----------

    public void save(CompoundTag tag) {
        tag.putFloat("Sleepiness", sleepiness);
        if (!route.isEmpty() && routeBase != null) {
            long[] pts = new long[route.size()];
            for (int i = 0; i < pts.length; i++) pts[i] = route.get(i).asLong();
            tag.putLongArray("RoutePoints", pts);
            tag.putLong("RouteBase", routeBase.asLong());
            tag.putInt("RouteStrength", strength);
            tag.putLong("RouteDay", lastUsedDay);
        }
    }

    public void load(CompoundTag tag) {
        sleepiness = tag.getFloat("Sleepiness");
        route.clear();
        routeBase = null;
        if (tag.contains("RoutePoints") && tag.contains("RouteBase")) {
            for (long l : tag.getLongArray("RoutePoints")) route.add(BlockPos.of(l));
            routeBase = BlockPos.of(tag.getLong("RouteBase"));
            strength = tag.getInt("RouteStrength");
            lastUsedDay = tag.getLong("RouteDay");
        }
    }
}
