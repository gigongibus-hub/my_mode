package com.gigongibus.hungrycows;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Пам'ять корови (покращення навчання):
 * 1) відраза до їжі, після якої стало погано; 2) звикання до гравців, які поруч і нічого поганого не роблять;
 * 5) пам'ять місць (де було добре, а де вдарили чи налякали); 6) усе це повільно забувається.
 * (3 — страх передається стаду, і 4 — відкладені наслідки, у HungryCow і CowBrain.)
 * Усі числа зібрані на початку файлу.
 */
public class CowMemory {

    // ================= 1. ВІДРАЗА ДО ЇЖІ =================
    public static final int MEAL_WINDOW_TICKS = 3600;    // пов'язує нездужання з їжею за останні 3 хв
    public static final int MEALS_REMEMBERED = 10;       // скільки останніх страв пам'ятає
    public static final double AVERSION_GAIN = 0.5;      // наскільки сильна відраза до найсвіжішої страви за раз
    public static final int SICK_UNWELL = 30;            // «погано» = нездужання від 30, отрута, токсин чи подавилась
    public static final int SICK_REPEAT_SECONDS = 10;    // поки погано, підсилює відразу раз на 10 с
    // шкода прямо від їжі — відраза одразу саме до неї (не чекаючи нездужання)
    public static final double THORN_BLAME = 0.5;        // вкололась (трояндовий кущ)
    public static final double CHOKE_BLAME = 0.5;        // подавилась
    public static final double SLOW_BLAME = 0.3;         // стала повільною (папороть)
    public static final double DYING_BLAME = 0.6;        // «повільна смерть» (папороть)
    public static final double STAPLE_BLAME = 0.2;       // звична їжа (трава, мох, сіно) отримує лише 20% провини
    public static final double AVOID_AT = 0.7;           // з такої відрази не їсть цю їжу сама (хіба що голодує) і не бере з рук
    public static final double AVERSION_SCORE = 40;      // у пошуку їжі відраза 1.0 знімає 40 балів вигоди
    public static final double AVERSION_FORGET = 1.0 / 6000; // за секунду відраза слабшає (зникає приблизно за 5 діб)

    // ================= 2. ЗВИКАННЯ ДО ГРАВЦІВ =================
    public static final double HABITUATE_RANGE = 8;      // гравець ближче цього — корова звикає до нього
    public static final double HABITUATE_RATE = 0.002;   // за секунду спокійної присутності (повністю ≈ за 8 хв)
    public static final double HABITUATE_MAX = 0.7;      // повне звикання: страх від гравця на 70% менший
    public static final double HABIT_LOSS_HIT = 0.3;     // удар цього гравця: звикання −0.3
    public static final double HABIT_LOSS_SCARE = 0.01;  // за секунду, поки він бігає чи стрибає поруч
    public static final double HABIT_LOSS_ALARM = 0.05;  // інша корова втекла від нього на очах (вчиться від стада)
    public static final double HABIT_FORGET = 1.0 / 12000; // за секунду без гравця звикання слабшає (≈ 10 діб)
    public static final int PLAYERS_REMEMBERED = 20;

    // ================= 5. ПАМ'ЯТЬ МІСЦЬ =================
    public static final int CELL = 4;                    // місце = квадрат 4×4 блоки
    public static final int PLACES_REMEMBERED = 32;
    public static final double PLACE_HURT = -0.4;        // тут ударили чи напали
    public static final double PLACE_GOOD = 0.1;         // тут стало помітно краще (добра спроба мозку)
    public static final double PLACE_MEAL = 0.05;        // тут поїла
    public static final double BAD_PLACE = -0.3;         // гірше за це — корова сама звідси йде
    public static final double PLACE_SCORE = 30;         // у пошуку їжі місце ±1 дає ±30 балів
    public static final double PLACE_FORGET = 1.0 / 6000; // за секунду пам'ять місця слабшає (≈ 5 діб)

    private final HungryCow cow;

    private static class Meal {
        final String item;
        final int tick;
        final boolean staple;

        Meal(String item, int tick, boolean staple) {
            this.item = item;
            this.tick = tick;
            this.staple = staple;
        }
    }

    private final Deque<Meal> meals = new ArrayDeque<>();          // не зберігається (лише останні хвилини)
    private final Map<String, Double> aversion = new HashMap<>();  // предмет → 0..1
    private final Map<UUID, Double> familiarity = new HashMap<>(); // гравець → 0..1
    private final Map<Long, Double> places = new HashMap<>();      // клітинка → −1..1
    private boolean wasSick = false;
    private int sickSeconds = 0;

    public CowMemory(HungryCow cow) {
        this.cow = cow;
    }

    // ---------- 1. відраза ----------

    public void rememberMeal(Item item) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        if (key == null) return;
        boolean staple = "grass".equals(CowMilk.dietGroup(item)) || item == net.minecraft.world.item.Items.HAY_BLOCK;
        meals.addFirst(new Meal(key.toString(), cow.tickCount, staple));
        while (meals.size() > MEALS_REMEMBERED) meals.removeLast();
        addPlace(cow.blockPosition(), PLACE_MEAL);
        cow.taste.onEat(item);   // смак: звичка, набридання, задоволення
    }

    public double aversion(Item item) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        return key == null ? 0 : aversion.getOrDefault(key.toString(), 0.0);
    }

    // Від цієї їжі одразу стало погано (вкололась, подавилась тощо): відраза саме до неї
    public void blameDirect(Item item, double strength, String why) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        if (key == null) return;
        boolean staple = "grass".equals(CowMilk.dietGroup(item)) || item == net.minecraft.world.item.Items.HAY_BLOCK;
        double s = strength * (staple ? STAPLE_BLAME : 1.0);
        aversion.merge(key.toString(), s, (a, b) -> Math.min(1.0, a + b));
        cow.tell(cow.family.name + ": " + why + " — запам'ятала (" + key.getPath() + " "
                + Math.round(aversion.get(key.toString()) * 100) + "%)");
    }

    public boolean avoids(Item item) {
        return aversion(item) >= AVOID_AT;
    }

    private boolean isSick() {
        return cow.hasEffect(MobEffects.POISON) || cow.toxinActive() || cow.choking
                || cow.chem.feelUnwell() >= SICK_UNWELL;
    }

    // Стало погано: що з'їла нещодавно — до того відраза (до найсвіжішої страви найсильніша)
    private void blameMeals(double strength) {
        int n = 0;
        for (Meal m : meals) {
            if (cow.tickCount - m.tick > MEAL_WINDOW_TICKS) break;
            double share = strength / (1 + n) * (m.staple ? STAPLE_BLAME : 1.0);   // 1, 1/2, 1/3 …
            aversion.merge(m.item, share, (a, b) -> Math.min(1.0, a + b));
            n++;
        }
        if (n > 0) cow.tell(cow.family.name + ": після їжі стало погано — запам'ятала");
    }

    // ---------- 2. звикання ----------

    public double familiarity(Player p) {
        return familiarity.getOrDefault(p.getUUID(), 0.0);
    }

    // множник страху від цього гравця (1.0 … 0.3)
    public double habituationFactor(Player p) {
        return 1.0 - HABITUATE_MAX * familiarity(p);
    }

    public void familiarityHit(Player p, double loss) {
        familiarity.merge(p.getUUID(), -loss, (a, b) -> Math.max(0.0, a + b));
    }

    // ---------- 5. місця ----------

    private static long cellOf(BlockPos pos) {
        return BlockPos.asLong(Math.floorDiv(pos.getX(), CELL), 0, Math.floorDiv(pos.getZ(), CELL));
    }

    public double placeValue(BlockPos pos) {
        return places.getOrDefault(cellOf(pos), 0.0);
    }

    public void addPlace(BlockPos pos, double delta) {
        places.merge(cellOf(pos), delta, (a, b) -> Math.max(-1.0, Math.min(1.0, a + b)));
        if (places.size() > PLACES_REMEMBERED) {
            // забути найменш важливе місце
            Long weakest = null;
            double w = Double.MAX_VALUE;
            for (Map.Entry<Long, Double> e : places.entrySet()) {
                if (Math.abs(e.getValue()) < w) {
                    w = Math.abs(e.getValue());
                    weakest = e.getKey();
                }
            }
            if (weakest != null) places.remove(weakest);
        }
    }

    // Найкраще місце, де їла (значення від minValue), до range блоків, крім пропущених клітинок (skip) — куди йти шукати їжу
    public BlockPos bestPlace(BlockPos from, double range, double minValue, java.util.Set<Long> skip) {
        BlockPos best = null;
        double bestV = minValue;
        for (Map.Entry<Long, Double> e : places.entrySet()) {
            if (e.getValue() < bestV || skip.contains(e.getKey())) continue;
            BlockPos cell = BlockPos.of(e.getKey());
            BlockPos c = new BlockPos(cell.getX() * CELL + CELL / 2, from.getY(), cell.getZ() * CELL + CELL / 2);
            if (c.distSqr(from) > range * range || cellOf(c) == cellOf(from)) continue;
            bestV = e.getValue();
            best = c;
        }
        return best;
    }

    public static long cellKey(BlockPos pos) {
        return cellOf(pos);
    }

    // Центр клітинки (для «піти звідси»)
    public static BlockPos cellCenter(BlockPos pos) {
        int cx = Math.floorDiv(pos.getX(), CELL) * CELL + CELL / 2;
        int cz = Math.floorDiv(pos.getZ(), CELL) * CELL + CELL / 2;
        return new BlockPos(cx, pos.getY(), cz);
    }

    // ---------- раз на секунду ----------

    public void secondTick() {
        // 1. нездужання → відраза до нещодавньої їжі
        boolean sick = isSick();
        if (sick) {
            if (!wasSick || ++sickSeconds % SICK_REPEAT_SECONDS == 0) blameMeals(wasSick ? AVERSION_GAIN * 0.3 : AVERSION_GAIN);
        } else {
            sickSeconds = 0;
        }
        wasSick = sick;

        // 2. звикання до гравців поруч
        java.util.Set<UUID> near = new java.util.HashSet<>();
        for (Player p : cow.level().players()) {
            if (p.isSpectator() || cow.distanceToSqr(p) > HABITUATE_RANGE * HABITUATE_RANGE) continue;
            near.add(p.getUUID());
            boolean scary = p.isSprinting() || (!p.onGround() && !p.getAbilities().flying);
            double d = scary ? -HABIT_LOSS_SCARE : HABITUATE_RATE;
            familiarity.merge(p.getUUID(), d, (a, b) -> Math.max(0.0, Math.min(1.0, a + b)));
        }
        // 6. забування
        familiarity.replaceAll((id, v) -> near.contains(id) ? v : Math.max(0.0, v - HABIT_FORGET));
        familiarity.values().removeIf(v -> v <= 0);
        while (familiarity.size() > PLAYERS_REMEMBERED) {
            UUID weakest = null;
            double w = Double.MAX_VALUE;
            for (Map.Entry<UUID, Double> e : familiarity.entrySet()) {
                if (e.getValue() < w) {
                    w = e.getValue();
                    weakest = e.getKey();
                }
            }
            familiarity.remove(weakest);
        }
        aversion.replaceAll((k, v) -> Math.max(0.0, v - AVERSION_FORGET));
        aversion.values().removeIf(v -> v <= 0);
        places.replaceAll((k, v) -> v > 0 ? Math.max(0.0, v - PLACE_FORGET) : Math.min(0.0, v + PLACE_FORGET));
        places.values().removeIf(v -> v == 0);
    }

    // ---------- для книги ----------

    public List<String> lines(Player reader) {
        List<String> out = new ArrayList<>();
        out.add("#Пам'ять");
        out.add("Звикла до тебе: " + Math.round(familiarity(reader) * 100) + "% (страх від тебе ×"
                + String.format("%.2f", habituationFactor(reader)) + ")");
        if (aversion.isEmpty()) {
            out.add("Відрази до їжі немає");
        } else {
            out.add("Відраза до їжі:");
            aversion.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).limit(5)
                    .forEach(e -> out.add("   " + e.getKey().replace("minecraft:", "") + ": " + Math.round(e.getValue() * 100)
                            + "%" + (e.getValue() >= AVOID_AT ? " (не їсть)" : "")));
        }
        long good = places.values().stream().filter(v -> v > 0).count();
        long bad = places.values().stream().filter(v -> v < 0).count();
        out.add("Пам'ятає місць: добрих " + good + ", поганих " + bad);
        out.add("Це місце: " + String.format("%+.2f", placeValue(cow.blockPosition())));
        return out;
    }

    // ---------- збереження ----------

    public void save(CompoundTag tag) {
        CompoundTag c = new CompoundTag();
        CompoundTag av = new CompoundTag();
        aversion.forEach((k, v) -> av.putDouble(k, v));
        c.put("Aversion", av);
        ListTag fam = new ListTag();
        familiarity.forEach((id, v) -> {
            CompoundTag f = new CompoundTag();
            f.putUUID("Id", id);
            f.putDouble("V", v);
            fam.add(f);
        });
        c.put("Familiarity", fam);
        ListTag pl = new ListTag();
        places.forEach((k, v) -> {
            CompoundTag p = new CompoundTag();
            p.putLong("Cell", k);
            p.putDouble("V", v);
            pl.add(p);
        });
        c.put("Places", pl);
        tag.put("Memory", c);
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("Memory")) return;
        CompoundTag c = tag.getCompound("Memory");
        aversion.clear();
        CompoundTag av = c.getCompound("Aversion");
        for (String k : av.getAllKeys()) aversion.put(k, av.getDouble(k));
        familiarity.clear();
        ListTag fam = c.getList("Familiarity", Tag.TAG_COMPOUND);
        for (int i = 0; i < fam.size(); i++) {
            CompoundTag f = fam.getCompound(i);
            if (f.hasUUID("Id")) familiarity.put(f.getUUID("Id"), f.getDouble("V"));
        }
        places.clear();
        ListTag pl = c.getList("Places", Tag.TAG_COMPOUND);
        for (int i = 0; i < pl.size(); i++) {
            CompoundTag p = pl.getCompound(i);
            places.put(p.getLong("Cell"), p.getDouble("V"));
        }
    }
}
