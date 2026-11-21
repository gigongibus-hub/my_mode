package com.gigongibus.hungrycows;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

public class CowFood {
    // Як корова ставиться до цієї їжі на землі
    public enum Ground {
        NONE,       // тільки з руки гравця
        SAFE,       // їсть сама як звичайну
        MISTAKE,    // група Б: може помилково взяти з шансом
        DESPERATE   // група В: лише коли їжі більше немає
    }

    public final int hunger;            // скільки голоду знімає
    public float heal = 0;              // скільки HP лікує (1 серце = 2 HP)
    public int regenSeconds = 0;        // регенерація щоразу (сек)
    public int firstRegenSeconds = 0;   // регенерація лише перший раз за добу (сек)
    public int poisonSeconds = 0;       // слабке отруєння щоразу (сек)
    public int limitFrom = 0;           // з якого разу за добу починається отруєння (0 = немає)
    public int limitPoisonSeconds = 0;  // тривалість отруєння після ліміту (сек)
    public float selfDamage = 0;        // миттєвий урон (шипи)
    public int toxinSeconds = 0;        // власний урон щосекунди: скільки секунд
    public float toxinDamage = 0;       // власний урон щосекунди: скільки HP
    public int slowFrom = 0;            // з якого разу за добу корова стає повільною
    public int slowSeconds = 0;         // на скільки секунд
    public int dyingFrom = 0;           // з якого разу за добу починається повільна смерть
    public int dyingSeconds = 0;        // скільки секунд триває (урон 1 HP кожні 5 сек)
    public float chokeChance = 0;       // шанс подавитися (0.01 = 1%)
    public String group = null;         // спільний лічильник для кількох предметів
    public Ground ground = Ground.NONE; // ставлення до їжі на землі

    private CowFood(int hunger) {
        this.hunger = hunger;
    }

    private CowFood healing(float v) { this.heal = v; return this; }
    private CowFood regen(int s) { this.regenSeconds = s; return this; }
    private CowFood firstRegen(int s) { this.firstRegenSeconds = s; return this; }
    private CowFood poison(int s) { this.poisonSeconds = s; return this; }
    private CowFood limit(int from, int seconds) { this.limitFrom = from; this.limitPoisonSeconds = seconds; return this; }
    private CowFood thorns(float d) { this.selfDamage = d; return this; }
    private CowFood toxin(int seconds, float damage) { this.toxinSeconds = seconds; this.toxinDamage = damage; return this; }
    private CowFood slow(int from, int seconds) { this.slowFrom = from; this.slowSeconds = seconds; return this; }
    private CowFood dying(int from, int seconds) { this.dyingFrom = from; this.dyingSeconds = seconds; return this; }
    private CowFood choke(float chance) { this.chokeChance = chance; return this; }
    private CowFood group(String g) { this.group = g; return this; }
    private CowFood ground(Ground g) { this.ground = g; return this; }

    // чи можна давати ситій здоровій корові без наслідків
    public boolean isSafe() {
        return limitFrom == 0 && poisonSeconds == 0 && toxinSeconds == 0
                && selfDamage == 0 && slowFrom == 0 && dyingFrom == 0 && chokeChance == 0;
    }

    private static final Map<Item, CowFood> TABLE = new HashMap<>();

    // Пучок свіжої трави (сіно мода) — предмет мода, тож не в статичній таблиці (вона заповнюється раніше за реєстрацію)
    public static final int BUNDLE_FOOD = 10;   // пучок свіжої трави знімає 10 голоду (Fij)
    private static CowFood bundle;

    public static CowFood get(Item item) {
        if (item == ModBlocks.HAY_BUNDLE.get()) {
            if (bundle == null) bundle = new CowFood(BUNDLE_FOOD);
            return bundle;
        }
        return TABLE.get(item);
    }

    private static CowFood food(Item item, int hunger) {
        CowFood f = new CowFood(hunger);
        TABLE.put(item, f);
        return f;
    }

    static {
        food(Items.WHEAT, 5);        // пшениця з рук — 5 (Fij)
        food(Items.HAY_BLOCK, 30);   // блок сіна (було 10): основний корм у хліві

        // квіти
        food(Items.DANDELION, 10).ground(Ground.SAFE);
        food(Items.POPPY, 10).limit(6, 5).ground(Ground.MISTAKE);
        food(Items.ALLIUM, 10).ground(Ground.SAFE);               // різанець
        food(Items.AZURE_BLUET, 10);                              // черемша (особлива поведінка в коді)
        food(Items.OXEYE_DAISY, 10).ground(Ground.SAFE);          // королиця
        food(Items.CORNFLOWER, 10).limit(15, 5).ground(Ground.MISTAKE);
        food(Items.LILY_OF_THE_VALLEY, 10).toxin(5, 3.0F).ground(Ground.DESPERATE);
        food(Items.RED_TULIP, 10).poison(10).ground(Ground.MISTAKE);
        food(Items.ORANGE_TULIP, 10).poison(10).ground(Ground.MISTAKE);
        food(Items.WHITE_TULIP, 10).poison(10).ground(Ground.MISTAKE);
        food(Items.PINK_TULIP, 10).poison(10).ground(Ground.MISTAKE);
        food(Items.SUNFLOWER, 10).ground(Ground.SAFE);
        food(Items.LILAC, 10).limit(6, 1).ground(Ground.MISTAKE);
        food(Items.ROSE_BUSH, 10).thorns(1.0F).ground(Ground.DESPERATE);
        food(Items.PEONY, 10).limit(2, 10).ground(Ground.MISTAKE);

        // рослини
        food(Items.GLOW_LICHEN, 5).ground(Ground.SAFE);
        food(Items.MOSS_CARPET, 5).ground(Ground.SAFE);
        food(Items.LILY_PAD, 5).limit(5, 5);
        food(Items.KELP, 5).firstRegen(5).limit(10, 5);
        food(Items.FERN, 5).group("fern").slow(3, 60).dying(5, 1200).ground(Ground.MISTAKE);
        food(Items.LARGE_FERN, 5).group("fern").slow(3, 60).dying(5, 1200).ground(Ground.MISTAKE);

        // овочі та фрукти
        food(Items.MELON_SLICE, 10).healing(1.0F);
        food(Items.MELON, 50).regen(10);
        food(Items.PUMPKIN, 50).regen(10);
        food(Items.APPLE, 10).choke(0.01F);
        food(Items.CARROT, 10).choke(0.01F);
        food(Items.POTATO, 5).choke(0.20F);
        food(Items.POISONOUS_POTATO, 1).poison(10);
        food(Items.BEETROOT, 10).limit(5, 5);
    }
}
