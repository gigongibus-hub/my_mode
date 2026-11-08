package com.gigongibus.hungrycows;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Смак (план Fij, етап 4).
 * - П'ять смаків: солодке, солоне, гірке, кисле, ситне. У кожної їжі свій набір (таблиця PROFILE нижче).
 * - Скільки корові до смаку кожен смак — від генів (TASTE_*) і вродженого (гірке не люблять, ситне й солодке люблять).
 * - Смачність їжі = смаки × уподобання + звичка до цієї їжі − набридання. Вона змінює вибір їжі на землі
 *   (SeekGrassGoal), задоволення після їди (дофамін) і чи бере корова з рук.
 * - Незнайому гірку їжу корова лише пробує (маленький шматок: менше насичує, отрута слабша). Стало погано — CowMemory
 *   дає відразу, і більше не їсть (це вже було).
 * - Улюблена їжа: та, що найсмачніша серед з'їдених (гени + звичка: кожна добра трапеза +0,02).
 * - Набридання: одне й те саме за останні 10 хв — менш смачне (до −0,5), корова шукає різноманіття.
 * - Сіль: бажання солі росте (+1 за 30 с); соляний лизунець (новий блок) і ламінарія його гасять. Лизати — здоров'я й задоволення.
 */
public class CowTaste {
    public enum Taste {
        SWEET("солодке", 0.2), SALTY("солоне", 0.1), BITTER("гірке", -0.4), SOUR("кисле", -0.1), SAVORY("ситне", 0.2);

        public final String title;
        public final double innate;   // вроджене ставлення (−1…+1), до нього додається ген

        Taste(String title, double innate) {
            this.title = title;
            this.innate = innate;
        }
    }

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double GENE_RANGE = 0.3;          // ген смаку: 0 → −0,3 … 100 → +0,3 до вродженого
    public static final double HABIT_GAIN = 0.02;         // добра трапеза: звичка до цієї їжі +0,02 (до 0,3)
    public static final double HABIT_MAX = 0.3;
    public static final double HABIT_FORGET = 1.0 / 24000; // звичка забувається (≈ 20 діб)
    public static final int BORED_WINDOW = 12000;         // набридання рахує трапези за останні 10 хв (тіки)
    public static final double BORED_PER_MEAL = 0.06;     // кожна така сама трапеза −0,06 смачності ...
    public static final double BORED_MAX = 0.5;           // ... до −0,5
    public static final int MEALS_KEPT = 40;
    public static final double BITTER_TRY = 0.5;          // «гірка» їжа — гіркота від 0,5
    public static final double BITE = 0.3;                // незнайому гірку лише пробує: насичує на 30%, отрута вдвічі коротша
    public static final double SCORE = 25;                // у виборі їжі смачність ±1 дає ±25 балів
    public static final double PLEASURE = 6;              // смачна (1) їжа — дофамін +6 зверху
    public static final double REFUSE_BELOW = -0.45;      // з рук не бере зовсім несмачне, якщо не дуже голодна ...
    public static final int REFUSE_HUNGER = 40;           // ... (голод менше 40)
    // сіль
    public static final int SALT_EVERY = 30;              // бажання солі +1 раз на 30 с
    public static final int SALT_WANT = 40;               // з 40 іде до лизунця
    public static final int SALT_LICK = 60;               // лизнула — бажання −60
    public static final float SALT_HEAL = 2.0F;           // і +1 серце
    public static final double SALT_PLEASURE = 8;         // і дофамін +8
    public static final int KELP_SALT = 30;               // ламінарія гасить бажання солі на 30

    // ---------- що яка їжа на смак (0…1 кожного смаку) ----------
    private static final Map<Item, double[]> PROFILE = new HashMap<>();

    private static void p(Item item, double sweet, double salty, double bitter, double sour, double savory) {
        PROFILE.put(item, new double[]{ sweet, salty, bitter, sour, savory });
    }

    static {
        p(Items.GRASS_BLOCK, 0.1, 0, 0.05, 0, 0.8);
        p(Items.GRASS, 0.15, 0, 0.05, 0, 0.8);
        p(Items.TALL_GRASS, 0.2, 0, 0.05, 0, 0.85);
        p(Items.MOSS_BLOCK, 0, 0, 0.2, 0.1, 0.5);
        p(Items.HAY_BLOCK, 0.2, 0, 0, 0, 0.9);
        p(Items.MELON, 0.9, 0, 0, 0, 0.3);
        p(Items.MELON_SLICE, 0.9, 0, 0, 0, 0.2);
        p(Items.PUMPKIN, 0.5, 0, 0.1, 0, 0.5);
        p(Items.APPLE, 0.7, 0, 0, 0.4, 0.2);
        p(Items.CARROT, 0.6, 0, 0.1, 0, 0.4);
        p(Items.POTATO, 0.1, 0, 0.1, 0, 0.7);
        p(Items.POISONOUS_POTATO, 0, 0, 0.8, 0.2, 0.3);
        p(Items.BEETROOT, 0.6, 0, 0.3, 0, 0.3);
        p(Items.KELP, 0, 0.9, 0.2, 0, 0.3);
        p(Items.LILY_PAD, 0, 0, 0.5, 0, 0.2);
        p(Items.GLOW_LICHEN, 0, 0, 0.6, 0.2, 0.1);
        p(Items.MOSS_CARPET, 0, 0, 0.2, 0.1, 0.3);
        p(Items.FERN, 0, 0, 0.8, 0, 0.2);
        p(Items.LARGE_FERN, 0, 0, 0.8, 0, 0.2);
        p(Items.DANDELION, 0.2, 0, 0.4, 0, 0.2);
        p(Items.POPPY, 0.1, 0, 0.5, 0, 0.1);
        p(Items.ALLIUM, 0.1, 0, 0.4, 0.3, 0.1);
        p(Items.AZURE_BLUET, 0.1, 0, 0.4, 0.2, 0.1);
        p(Items.OXEYE_DAISY, 0.2, 0, 0.4, 0, 0.1);
        p(Items.CORNFLOWER, 0.2, 0, 0.4, 0, 0.1);
        p(Items.LILY_OF_THE_VALLEY, 0.3, 0, 0.9, 0, 0);
        p(Items.RED_TULIP, 0.3, 0, 0.6, 0, 0.1);
        p(Items.ORANGE_TULIP, 0.3, 0, 0.6, 0, 0.1);
        p(Items.WHITE_TULIP, 0.3, 0, 0.6, 0, 0.1);
        p(Items.PINK_TULIP, 0.3, 0, 0.6, 0, 0.1);
        p(Items.SUNFLOWER, 0.3, 0, 0.2, 0, 0.4);
        p(Items.LILAC, 0.4, 0, 0.3, 0, 0.1);
        p(Items.ROSE_BUSH, 0.3, 0, 0.5, 0.2, 0.1);
        p(Items.PEONY, 0.4, 0, 0.3, 0, 0.1);
    }

    public static double[] profile(Item item) {
        return PROFILE.get(item);
    }

    // трава всіх видів набридає разом (для корови це одна страва)
    private static String dish(Item item) {
        if (item == Items.GRASS_BLOCK || item == Items.GRASS || item == Items.TALL_GRASS) return "трава";
        return ForgeRegistries.ITEMS.getKey(item).toString();
    }

    private static final CowGenes.Gene[] GENES = { CowGenes.Gene.TASTE_SWEET, CowGenes.Gene.TASTE_SALTY,
            CowGenes.Gene.TASTE_BITTER, CowGenes.Gene.TASTE_SOUR, CowGenes.Gene.TASTE_SAVORY };

    // ---------- корова ----------

    private final HungryCow cow;
    private final Map<String, Double> habit = new HashMap<>();   // їжа → звичка 0…0,3 (зберігається)
    private final Map<String, Integer> eaten = new HashMap<>();  // їжа → скільки разів їла (знайома; зберігається)
    private final Deque<long[]> meals = new ArrayDeque<>();      // {хеш страви, тік} — для набридання
    private final Map<Integer, String> mealNames = new HashMap<>();
    public double salt = 20;                                     // бажання солі 0…100 (зберігається)
    private int seconds = 0;

    public CowTaste(HungryCow cow) {
        this.cow = cow;
    }

    // Наскільки корові до смаку цей смак (−1…+1)
    public double liking(Taste t) {
        double gene = (cow.genes.value(GENES[t.ordinal()]) - 50) / 50.0 * GENE_RANGE;
        return Mth.clamp(t.innate + gene, -1, 1);
    }

    // Смачність їжі (≈ −1…+1); null-профіль (невідома гра їжа) — 0
    public double tastiness(Item item) {
        double[] pr = PROFILE.get(item);
        if (pr == null) return 0;
        double s = 0;
        for (Taste t : Taste.values()) s += pr[t.ordinal()] * liking(t);
        if (pr[Taste.SALTY.ordinal()] > 0) s += pr[Taste.SALTY.ordinal()] * salt / 100.0;   // хоче солі — солоне смачніше
        s += habit.getOrDefault(dish(item), 0.0);
        s -= boredom(item);
        return Mth.clamp(s, -1, 1);
    }

    public double boredom(Item item) {
        int h = dish(item).hashCode();
        int n = 0;
        for (long[] m : meals) if (m[0] == h && cow.tickCount - m[1] < BORED_WINDOW) n++;
        return Math.min(BORED_MAX, n * BORED_PER_MEAL);
    }

    public boolean familiar(Item item) {
        return eaten.getOrDefault(dish(item), 0) > 0;
    }

    // Незнайома гірка — лише пробує
    public boolean onlyTries(Item item) {
        double[] pr = PROFILE.get(item);
        return pr != null && pr[Taste.BITTER.ordinal()] >= BITTER_TRY && !familiar(item);
    }

    // Чи бере з рук (зовсім несмачне — ні, якщо не дуже голодна)
    public boolean refuses(Item item) {
        return tastiness(item) < REFUSE_BELOW && cow.hunger < REFUSE_HUNGER && !onlyTries(item);
    }

    // З'їла (кличе CowMemory.rememberMeal — і з землі, і з рук)
    public void onEat(Item item) {
        String d = dish(item);
        double t = tastiness(item);
        eaten.merge(d, 1, Integer::sum);
        meals.addLast(new long[]{ d.hashCode(), cow.tickCount });
        mealNames.put(d.hashCode(), d);
        while (meals.size() > MEALS_KEPT) meals.pollFirst();
        if (t > 0) cow.chem.onTasty(PLEASURE * t);   // смачно — задоволення
        if (cow.memory.aversion(item) < 0.2) habit.merge(d, HABIT_GAIN, (a, b) -> Math.min(HABIT_MAX, a + b));
        if (item == Items.KELP) salt = Math.max(0, salt - KELP_SALT);
    }

    public boolean wantsSalt() {
        return salt >= SALT_WANT;
    }

    public void onLick() {
        salt = Math.max(0, salt - SALT_LICK);
        cow.heal(SALT_HEAL);
        cow.chem.onTasty(SALT_PLEASURE);
        cow.chem.onTreat();
    }

    public void secondTick() {
        if (++seconds % SALT_EVERY == 0 && !cow.isBaby()) salt = Math.min(100, salt + 1);
        habit.replaceAll((k, v) -> Math.max(0, v - HABIT_FORGET));
    }

    // Улюблена їжа — найсмачніша серед тих, що їла
    public String favorite() {
        String best = null;
        double bv = -2;
        for (Map.Entry<Item, double[]> e : PROFILE.entrySet()) {
            if (!familiar(e.getKey())) continue;
            double t = tastiness(e.getKey()) + boredom(e.getKey());   // без набридання
            if (t > bv) { bv = t; best = e.getKey().getDescription().getString(); }
        }
        return best;
    }

    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("#Смак");
        StringBuilder sb = new StringBuilder("Любить: ");
        StringBuilder no = new StringBuilder("Не любить: ");
        for (Taste t : Taste.values()) {
            double l = liking(t);
            if (l > 0.05) sb.append(t.title).append(' ');
            else if (l < -0.05) no.append(t.title).append(' ');
        }
        out.add(sb.toString().trim());
        out.add(no.toString().trim());
        String fav = favorite();
        out.add("Улюблена їжа: " + (fav != null ? fav : "ще не знає"));
        double b = boredom(Items.GRASS);
        if (b > 0.1) out.add("Трава трохи набридла — хочеться чогось іншого");
        out.add("Хоче солі: " + Math.round(salt) + (wantsSalt() ? " (шукає лизунець)" : ""));
        return out;
    }

    public void save(CompoundTag tag) {
        CompoundTag t = new CompoundTag();
        t.putDouble("Salt", salt);
        ListTag list = new ListTag();
        for (Map.Entry<String, Integer> e : eaten.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("K", e.getKey());
            c.putInt("N", e.getValue());
            c.putDouble("H", habit.getOrDefault(e.getKey(), 0.0));
            list.add(c);
        }
        t.put("Foods", list);
        tag.put("Taste", t);
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("Taste")) return;
        CompoundTag t = tag.getCompound("Taste");
        salt = t.getDouble("Salt");
        eaten.clear();
        habit.clear();
        for (Tag x : t.getList("Foods", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            eaten.put(c.getString("K"), c.getInt("N"));
            if (c.getDouble("H") > 0) habit.put(c.getString("K"), c.getDouble("H"));
        }
    }
}
