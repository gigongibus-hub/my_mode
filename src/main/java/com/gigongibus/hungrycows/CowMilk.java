package com.gigongibus.hungrycows;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Map;

/**
 * Молоко самки: шкала 100 одиниць, що з'являється лише після пологів, і якість за раціоном.
 * Усі числа зібрані на початку файлу.
 */
public class CowMilk {

    // ================= ШКАЛА =================
    public static final int MAX = 100;                   // повна шкала (одразу після пологів)
    public static final int BUCKET_COST = 25;            // скільки забирає одне відро
    public static final int FRIEND_BUCKET_COST = 10;     // скільки забирає відро з ефектом «Друг корови»
    public static final int REGEN_SECONDS = 24;          // +1 одиниця кожні 24 с = 100 одиниць за 2 ігрові доби
    public static final int CALF_DRINK = 5;              // скільки випиває теля за один раз (≈50 за 2 доби)

    // ================= СКІЛЬКИ ТРИВАЄ =================
    public static final long DAY = 24000L;               // ігрова доба в тіках
    public static final int CHILDHOOD_DAYS = 1;          // скільки росте теля (ванільно 20 хв = 1 доба)
    public static final int DAYS_AFTER_GROWN = 3;        // молоко ще стільки діб після того, як теля виросло
    public static final int DAYS_AFTER_CALF_DEATH = 5;   // молоко стільки діб, якщо теля померло

    // ================= ЯКІСТЬ ЗА РАЦІОНОМ (з останніх пологів) =================
    public static final int BLUET_FOR_POISONED = 1;      // черемша: отруєне молоко вже з 1 квітки
    public static final int POPPY_FOR_S = 20;            // мак: якість С з 20 квіток
    public static final int BAD_FLOWERS_FOR_S = 10;      // волошка, тюльпани, півонія, бузок: С з 10 квіток
    public static final int ALLIUM_FOR_B = 10;           // різанець: Б з 10 квіток
    // A: їла і овочі, і фрукти, і траву. Інакше: В.

    private static final Map<Item, String> DIET_GROUP = Map.ofEntries(
            Map.entry(Items.CARROT, "veg"), Map.entry(Items.POTATO, "veg"),
            Map.entry(Items.BEETROOT, "veg"), Map.entry(Items.PUMPKIN, "veg"),
            Map.entry(Items.APPLE, "fruit"), Map.entry(Items.MELON_SLICE, "fruit"), Map.entry(Items.MELON, "fruit"),
            Map.entry(Items.GRASS, "grass"), Map.entry(Items.TALL_GRASS, "grass"),
            Map.entry(Items.GRASS_BLOCK, "grass"), Map.entry(Items.MOSS_BLOCK, "grass"),
            Map.entry(Items.MOSS_CARPET, "grass"), Map.entry(Items.GLOW_LICHEN, "grass"),
            Map.entry(Items.DANDELION, "grass"),
            Map.entry(Items.ALLIUM, "allium"),
            Map.entry(Items.POPPY, "poppy"),
            Map.entry(Items.CORNFLOWER, "bad"), Map.entry(Items.RED_TULIP, "bad"),
            Map.entry(Items.ORANGE_TULIP, "bad"), Map.entry(Items.WHITE_TULIP, "bad"),
            Map.entry(Items.PINK_TULIP, "bad"), Map.entry(Items.PEONY, "bad"), Map.entry(Items.LILAC, "bad"),
            Map.entry(Items.AZURE_BLUET, "bluet"));

    private final HungryCow cow;
    private int milk = 0;
    private long lactationEnd = -1;      // ігровий час, коли молоко закінчиться
    private int regenSeconds = 0;
    private MilkQuality quality = MilkQuality.V;
    private CompoundTag diet = new CompoundTag();   // що з'їла з останніх пологів

    public CowMilk(HungryCow cow) {
        this.cow = cow;
    }

    // ---------- стан ----------

    public boolean isLactating() {
        return cow.level().getGameTime() < lactationEnd;
    }

    public int amount() {
        return milk;
    }

    public MilkQuality quality() {
        return quality;
    }

    public boolean take(int units) {
        if (!isLactating() || milk < units) return false;
        milk -= units;
        return true;
    }

    // ---------- пологи ----------

    // Після пологів: повна шкала, нова якість за раціоном, раціон з нуля
    public void startLactation(boolean calfAlive) {
        quality = computeQuality();
        diet = new CompoundTag();
        milk = MAX;
        regenSeconds = 0;
        long days = calfAlive ? CHILDHOOD_DAYS + DAYS_AFTER_GROWN : DAYS_AFTER_CALF_DEATH;
        lactationEnd = cow.level().getGameTime() + days * DAY;
        cow.tell(cow.family.name + " має молоко: якість " + quality.letter);
    }

    // Теля померло: усе молоко гравцю, ще 5 діб
    public void onCalfDied() {
        if (!isLactating()) return;
        lactationEnd = cow.level().getGameTime() + DAYS_AFTER_CALF_DEATH * DAY;
        cow.tell(cow.family.name + " втратила теля: молоко ще " + DAYS_AFTER_CALF_DEATH + " діб");
    }

    // Раз на секунду
    public void secondTick() {
        if (isLactating()) {
            if (milk < MAX && ++regenSeconds >= REGEN_SECONDS) {
                regenSeconds = 0;
                milk++;
            }
        } else if (milk > 0) {
            milk = 0;
            cow.tell("У " + cow.family.name + " закінчилося молоко");
        }
    }

    // ---------- раціон і якість ----------

    public void recordDiet(Item item) {
        String group = DIET_GROUP.get(item);
        if (group != null) diet.putInt(group, diet.getInt(group) + 1);
    }

    private MilkQuality computeQuality() {
        if (diet.getInt("bluet") >= BLUET_FOR_POISONED) return MilkQuality.POISONED;
        if (diet.getInt("poppy") >= POPPY_FOR_S || diet.getInt("bad") >= BAD_FLOWERS_FOR_S) return MilkQuality.S;
        if (diet.getInt("allium") >= ALLIUM_FOR_B) return MilkQuality.B;
        if (diet.getInt("veg") > 0 && diet.getInt("fruit") > 0 && diet.getInt("grass") > 0) return MilkQuality.A;
        return MilkQuality.V;
    }

    public String dietText() {
        return "овочі " + diet.getInt("veg") + ", фрукти " + diet.getInt("fruit") + ", трава " + diet.getInt("grass")
                + ", різанець " + diet.getInt("allium") + ", мак " + diet.getInt("poppy")
                + ", погані квіти " + diet.getInt("bad") + ", черемша " + diet.getInt("bluet");
    }

    // Для напису над коровою (null, якщо молока немає)
    public String label() {
        return isLactating() ? "Молоко: " + milk + " (" + quality.letter + ")" : null;
    }

    // ---------- збереження ----------

    public void save(CompoundTag tag) {
        tag.putInt("Milk", milk);
        tag.putLong("LactationEnd", lactationEnd);
        tag.putInt("MilkRegen", regenSeconds);
        tag.putString("MilkQuality", quality.name());
        tag.put("Diet", diet);
    }

    public void load(CompoundTag tag) {
        milk = tag.getInt("Milk");
        lactationEnd = tag.contains("LactationEnd") ? tag.getLong("LactationEnd") : -1;
        regenSeconds = tag.getInt("MilkRegen");
        quality = tag.contains("MilkQuality") ? MilkQuality.byName(tag.getString("MilkQuality")) : MilkQuality.V;
        diet = tag.getCompound("Diet");
    }
}
