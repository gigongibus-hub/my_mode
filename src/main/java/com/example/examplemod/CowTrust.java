package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Довіра однієї корови до кожного гравця окремо.
 * Шкала від -100 до +100. Усі числа зібрані на початку файлу.
 */
public class CowTrust {

    // ================= ТЕСТОВІ ПЕРЕМИКАЧІ =================
    // Для звичайної гри: TEST_MULTIPLIER = 1 і ONE_FEED_PER_DAY = true
    public static final int TEST_MULTIPLIER = 25;        // множник змін довіри (годування, удар, вбивство)
    public static final boolean ONE_FEED_PER_DAY = false; // true = довіру дає лише перше годування за ігрову добу

    // ================= ШКАЛА =================
    public static final int MIN = -100;
    public static final int MAX = 100;
    public static final int FRIEND_FROM = 50;            // з цієї довіри корова не лякається гравця й підходить

    // ================= СКІЛЬКИ ДОДАЄТЬСЯ =================
    public static final int GAIN_SMALL = 1;              // скибка кавуна, яблуко, морква
    public static final int GAIN_BIG = 2;                // блок кавуна, гарбуз

    // ================= СКІЛЬКИ ВІДНІМАЄТЬСЯ =================
    public static final int HIT_PENALTY = 2;             // удар гравця
    public static final int SCARE_PENALTY = 1;           // корова почала тікати від гравця
    public static final int SCARE_COOLDOWN_TICKS = 200;  // не частіше раз на 10 секунд
    public static final int SCARE_MIN_TRUST = 1;         // «налякав» діє, лише якщо довіра не менша за це
    public static final int POISON_FEED_PENALTY = 5;     // отруйна їжа з руки
    public static final int WITNESS_PENALTY = 10;        // корова бачила, як гравець убив іншу
    public static final double WITNESS_RADIUS = 16.0;    // з якої відстані корови бачать убивство

    // ================= СТРАХ =================
    public static final int HIT_FEAR_NORMAL = 50;        // страх від удару (довіра нижче 50)
    public static final int HIT_FEAR_FRIEND_START = 25;  // страх від удару при довірі 50
    public static final int HIT_FEAR_FRIEND_END = 10;    // страх від удару при довірі 100
    public static final int HOSTILE_MIN_FEAR = 12;       // ворожа корова завжди насторожена (страх за секунду)

    // ================= ВОРОЖА КОРОВА (довіра < 0) =================
    public static final double CHARGE_RANGE = 8.0;       // з якої відстані кидається на гравця
    public static final int CHARGE_TIMEOUT_TICKS = 100;  // скільки максимум біжить за гравцем (5 сек)
    public static final double ATTACK_REACH = 2.0;       // з якої відстані б'є
    public static final int ATTACK_COOLDOWN_TICKS = 20;  // мінімальна пауза між ударами
    public static final float ATTACK_DAMAGE = 1.0F;      // пів серця
    public static final double ATTACK_KNOCKBACK = 0.8;   // відкидання (приблизно пара блоків)
    public static final int MAX_INTERVAL_TICKS = 400;    // пауза між кидками при довірі -1 (20 сек)
    public static final int MIN_INTERVAL_TICKS = 40;     // пауза між кидками при довірі -99 (2 сек)

    // ================= ІНШЕ =================
    private static final int MAX_PLAYERS = 50;           // скількох гравців пам'ятає одна корова

    // ---------------------------------------------------

    private static class Entry {
        int value = 0;                 // довіра
        long feedDay = -1;             // доба останнього годування, що дало довіру
        int lastScareTick = -100000;   // коли востаннє налякав (не зберігається)
    }

    private final Map<UUID, Entry> map = new HashMap<>();

    // ---------- читання та зміна ----------

    public int get(UUID id) {
        Entry e = map.get(id);
        return e == null ? 0 : e.value;
    }

    public int add(UUID id, int delta) {
        Entry e = map.computeIfAbsent(id, k -> new Entry());
        e.value = Math.max(MIN, Math.min(MAX, e.value + delta));
        int result = e.value;
        trim();
        return result;
    }

    // true, якщо сьогодні це перше годування, що дає довіру
    public boolean tryDailyFeed(UUID id, long day) {
        Entry e = map.computeIfAbsent(id, k -> new Entry());
        if (e.feedDay == day) return false;
        e.feedDay = day;
        return true;
    }

    // true, якщо корову можна вважати наляканою цим гравцем (не частіше за кулдаун)
    public boolean tryScare(UUID id, int nowTick) {
        Entry e = map.computeIfAbsent(id, k -> new Entry());
        if (nowTick - e.lastScareTick < SCARE_COOLDOWN_TICKS) return false;
        e.lastScareTick = nowTick;
        return true;
    }

    // забуває тих, до кого ставлення найнейтральніше, якщо їх забагато
    private void trim() {
        while (map.size() > MAX_PLAYERS) {
            UUID weakest = null;
            int weakestAbs = Integer.MAX_VALUE;
            for (Map.Entry<UUID, Entry> en : map.entrySet()) {
                int a = Math.abs(en.getValue().value);
                if (a < weakestAbs) {
                    weakestAbs = a;
                    weakest = en.getKey();
                }
            }
            if (weakest == null) return;
            map.remove(weakest);
        }
    }

    // ---------- правила ----------

    // Скільки довіри дає ласощі з руки (0 = не ласощі)
    public static int feedGain(Item item) {
        if (item == Items.MELON || item == Items.PUMPKIN) return GAIN_BIG;
        if (item == Items.MELON_SLICE || item == Items.APPLE || item == Items.CARROT) return GAIN_SMALL;
        return 0;
    }

    // Множник страху від руху гравця: 1 при довірі 0, 0 при довірі 50 і вище
    public static float fearMultiplier(int trust) {
        if (trust <= 0) return 1.0F;
        if (trust >= FRIEND_FROM) return 0.0F;
        return (FRIEND_FROM - trust) / (float) FRIEND_FROM;
    }

    // Скільки страху дає удар гравця
    public static int hitFear(int trust) {
        if (trust < FRIEND_FROM) return HIT_FEAR_NORMAL;
        return HIT_FEAR_FRIEND_START
                - (trust - FRIEND_FROM) * (HIT_FEAR_FRIEND_START - HIT_FEAR_FRIEND_END) / (MAX - FRIEND_FROM);
    }

    // Пауза між кидками ворожої корови: від 20 сек (-1) до 2 сек (-99); при -100 лише пауза між ударами
    public static int attackInterval(int trust) {
        int n = -trust;
        if (n >= 100) return ATTACK_COOLDOWN_TICKS;
        if (n < 1) n = 1;
        return (int) Math.round(MAX_INTERVAL_TICKS
                - (n - 1) * (MAX_INTERVAL_TICKS - MIN_INTERVAL_TICKS) / 98.0);
    }

    // ---------- збереження ----------

    public ListTag save() {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> en : map.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("Id", en.getKey());
            c.putInt("Value", en.getValue().value);
            c.putLong("FeedDay", en.getValue().feedDay);
            list.add(c);
        }
        return list;
    }

    public void load(ListTag list) {
        map.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            if (!c.hasUUID("Id")) continue;
            Entry e = new Entry();
            e.value = c.getInt("Value");
            e.feedDay = c.contains("FeedDay") ? c.getLong("FeedDay") : -1;
            map.put(c.getUUID("Id"), e);
        }
    }
}