package com.gigongibus.hungrycows;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * Стать, спарювання, вагітність і пологи корови.
 * Усі числа зібрані на початку файлу.
 */
public class CowReproduction {

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int MATING_TICKS = 300;           // скільки пара має провести разом перед зачаттям (15 сек)
    public static final int PREGNANCY_SECONDS = 300;      // вагітність (5 хв); для швидкого тесту постав 20
    public static final int LABOR_TICKS = 200;            // пологи (10 сек)
    public static final float LABOR_DAMAGE = 2.0F;        // скільки HP мати втрачає за пологи (не смертельно)
    public static final float CALF_DEATH_CHANCE = 0.5F;   // шанс, що теля народиться мертвим (50% для тестів, у готовому моді 0.02F)
    public static final int BREED_COOLDOWN_TICKS = 6000;  // пауза перед наступним розмноженням (5 хв)

    // ================= СКІЛЬКИ ТЕЛЯТ =================
    public static final int BIRTHS_MIN = 5;               // звичайна самка: від 5 до 7 пологів за життя
    public static final int BIRTHS_MAX = 7;
    public static final float BIG_FAMILY_CHANCE = 0.05F;  // 5% самок: від 10 до 12 пологів
    public static final int BIG_BIRTHS_MIN = 10;
    public static final int BIG_BIRTHS_MAX = 12;
    public static final double EXHAUSTED_SLOWDOWN = 0.30; // після останніх пологів назавжди повільніша на 30%
    private static final UUID EXHAUSTED_MODIFIER_ID = UUID.fromString("7d3f5f0e-3c1b-4b8e-9a51-6b1f2c9e4a10");

    private final HungryCow cow;
    private int sex;                       // 1 = жіноча, 2 = чоловіча
    private boolean pregnant = false;
    private int pregnantSeconds = 0;
    private int laborTicksLeft = 0;
    private int laborTotalTicks = LABOR_TICKS; // скільки тривають ці пологи (залежить від гена)
    private int births = 0;                // скільки разів народжувала (мертві телята теж рахуються)
    private int birthLimit;                // скільки пологів можливо за життя
    private boolean exhausted = false;     // більше не може народжувати і давати молоко після останньої лактації
    private CompoundTag fatherNode = null; // батько поточної вагітності для родинного дерева
    private UUID fatherId = null;
    private CompoundTag fatherGenes = null; // обидві копії генів батька поточної вагітності (для теляти)

    // зустріч пари (не зберігається)
    private int matingSince = 0;
    private int matingLastCall = -100000;

    public CowReproduction(HungryCow cow) {
        this.cow = cow;
        this.sex = cow.getRandom().nextBoolean() ? 1 : 2;
        // birthLimit виставляє rollBirthLimit(), коли вже відомі гени
    }

    // Скільки телят за життя: 5–7 (5% самок 10–12), гени BIRTH_LIMIT і BIG_FAMILY змінюють це на ±30%
    public void rollBirthLimit() {
        boolean big = cow.getRandom().nextFloat() < BIG_FAMILY_CHANCE * cow.genes.factor(CowGenes.Gene.BIG_FAMILY);
        int base = big
                ? BIG_BIRTHS_MIN + cow.getRandom().nextInt(BIG_BIRTHS_MAX - BIG_BIRTHS_MIN + 1)
                : BIRTHS_MIN + cow.getRandom().nextInt(BIRTHS_MAX - BIRTHS_MIN + 1);
        birthLimit = Math.max(1, (int) Math.round(base * cow.genes.factor(CowGenes.Gene.BIRTH_LIMIT)));
    }

    // ---------- числа з урахуванням генів ----------

    private int matingTicks() {
        return (int) Math.round(MATING_TICKS * cow.genes.factor(CowGenes.Gene.MATING_TIME));
    }

    private int pregnancySeconds() {
        return (int) Math.round(PREGNANCY_SECONDS * cow.genes.factor(CowGenes.Gene.PREGNANCY_LENGTH));
    }

    private int laborTicks() {
        return (int) Math.round(LABOR_TICKS * cow.genes.factor(CowGenes.Gene.LABOR_LENGTH));
    }

    private float laborDamage() {
        return (float) (LABOR_DAMAGE * cow.genes.factor(CowGenes.Gene.LABOR_PAIN));
    }

    private float calfDeathChance() {
        return (float) (CALF_DEATH_CHANCE * cow.genes.factor(CowGenes.Gene.STILLBIRTH_RISK));
    }

    private int breedPause() {
        return (int) Math.round(BREED_COOLDOWN_TICKS * cow.genes.factor(CowGenes.Gene.BREED_PAUSE));
    }

    // ---------- стан ----------

    public boolean isFemale() {
        return sex == 1;
    }

    public boolean isPregnant() {
        return pregnant;
    }

    public boolean inLabor() {
        return laborTicksLeft > 0;
    }

    public int pregnantSecondsLeft() {
        return pregnantSeconds;
    }

    public int laborSecondsLeft() {
        return laborTicksLeft / 20 + (laborTicksLeft > 0 ? 1 : 0);
    }

    public int births() {
        return births;
    }

    public int birthLimit() {
        return birthLimit;
    }

    public boolean isExhausted() {
        return exhausted;
    }

    // Можуть розмножуватися: різні статі, ніхто не вагітний
    public boolean canMateWith(HungryCow other) {
        return isFemale() != other.repro.isFemale() && !pregnant && !other.repro.pregnant
                && !exhausted && !other.repro.exhausted;
    }

    private void say(String text) {
        if (!HungryCow.DEBUG_MESSAGES) return;
        Component msg = Component.literal(text);
        cow.level().players().forEach(p -> p.sendSystemMessage(msg));
    }

    // ---------- спарювання ----------

    // Викликається щоразу, коли ванільна ціль розмноження вважає, що пара «зустрілася» (раз на ~3 сек)
    public void onBreeding(HungryCow other) {
        HungryCow mother = isFemale() ? cow : other;
        HungryCow father = isFemale() ? other : cow;
        if (mother == father || !mother.repro.isFemale() || father.repro.isFemale()) return;

        CowReproduction m = mother.repro;
        if (m.pregnant) return;

        int now = mother.tickCount;
        if (now - m.matingLastCall > 200) {
            m.matingSince = now;               // нова зустріч: відлік з початку
        }
        m.matingLastCall = now;
        if (now - m.matingSince < m.matingTicks()) return;   // ще не 15 секунд разом

        m.conceive(father);
    }

    private void conceive(HungryCow father) {
        pregnant = true;
        pregnantSeconds = pregnancySeconds();
        fatherId = father.getUUID();
        fatherNode = father.family.asNode();
        fatherGenes = father.genes.saveTag();
        cow.resetLove();
        father.resetLove();
        cow.setAge(breedPause());
        father.setAge(father.repro.breedPause());
        cow.level().broadcastEntityEvent(cow, (byte) 18);   // сердечка
        say("Корова завагітніла!");
    }

    // ---------- вагітність і пологи ----------

    // Раз на секунду (викликається з aiStep корови)
    public void secondTick() {
        if (pregnant && laborTicksLeft <= 0) {
            if (pregnantSeconds > 0) pregnantSeconds--;
            if (pregnantSeconds <= 0) {
                laborTicksLeft = laborTicks();
                laborTotalTicks = laborTicksLeft;
                say("Починаються пологи!");
            }
        }
    }

    // Щотіку під час пологів (викликається з LaborGoal)
    public void laborTick() {
        if (laborTicksLeft <= 0) return;
        laborTicksLeft--;
        cow.getNavigation().stop();

        if (laborTicksLeft > 0 && laborTicksLeft % 20 == 0) {
            float pain = laborDamage() / (Math.max(20, laborTotalTicks) / 20.0F);
            cow.setHealth(Math.max(1.0F, cow.getHealth() - pain));   // не смертельно
            if ((laborTicksLeft / 20) % 3 == 0) {
                cow.playSound(SoundEvents.COW_HURT, 0.8F, 0.8F);
            }
        }
        if (laborTicksLeft == 0) {
            giveBirth();
        }
    }

    private void giveBirth() {
        pregnant = false;
        pregnantSeconds = 0;
        laborTicksLeft = 0;
        births++;

        HungryCow baby = null;
        if (cow.getRandom().nextFloat() < calfDeathChance()) {
            cow.playSound(SoundEvents.COW_DEATH, 0.8F, 1.3F);
            say("Теля народилося мертвим...");
        } else {
            baby = ModEntities.HUNGRY_COW.get().create(cow.level());
        }
        if (baby != null) {
            baby.setBaby(true);
            baby.moveTo(cow.getX(), cow.getY(), cow.getZ(), cow.getYRot(), 0.0F);
            baby.family.setupNewborn(cow, fatherNode);
            baby.genes.inherit(cow.genes.saveTag(), fatherGenes, cow.getRandom());
            baby.character.newborn(cow.getRandom());
            baby.onGenesChanged();
            baby.repro.rollBirthLimit();   // ліміт телят — уже за успадкованими генами
            baby.setHealth(baby.getMaxHealth());
            cow.family.addChild(baby.family.name);
            HungryCow father = findFather();
            if (father != null) father.family.addChild(baby.family.name);
            cow.level().addFreshEntity(baby);
            cow.level().broadcastEntityEvent(cow, (byte) 18);
            say("Народилося теля " + baby.family.name + "!");
        }
        // молоко з'являється після пологів; якщо теля мертве, воно все дістанеться гравцю
        cow.milk.startLactation(baby != null);
        fatherNode = null;
        fatherId = null;
        fatherGenes = null;

        if (births >= birthLimit) {
            exhausted = true;
            applyExhaustion();
            say(cow.family.name + " більше не зможе народжувати: це були останні пологи (" + births + ")");
        }
    }

    private HungryCow findFather() {
        if (fatherId == null || !(cow.level() instanceof ServerLevel level)) return null;
        Entity e = level.getEntity(fatherId);
        return e instanceof HungryCow father ? father : null;
    }

    // Виснажена корова назавжди повільніша (модифікатор зберігається разом з атрибутами)
    public void applyExhaustion() {
        AttributeInstance speed = cow.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(EXHAUSTED_MODIFIER_ID) == null) {
            speed.addPermanentModifier(new AttributeModifier(EXHAUSTED_MODIFIER_ID, "Exhausted cow",
                    -EXHAUSTED_SLOWDOWN, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    // ---------- напис над коровою ----------

    public String decorate(String name) {
        String result = "[" + (isFemale() ? "Ж" : "Ч") + "] " + cow.family.name + " | " + name;
        if (cow.isBaby()) {
            result += " | " + cow.family.calfLabel();
        } else if (isFemale()) {
            result += " | Пологи: " + births + "/" + birthLimit + (exhausted ? " (все)" : "");
            String milk = cow.milk.label();
            if (milk != null) result += " | " + milk;
        }
        if (laborTicksLeft > 0) {
            result += " | ПОЛОГИ ще " + (laborTicksLeft / 20 + 1) + " с";
        } else if (pregnant) {
            result += " | Вагітна: " + (pregnantSeconds / 60) + ":" + String.format("%02d", pregnantSeconds % 60);
        }
        return result;
    }

    // ---------- збереження ----------

    public void save(CompoundTag tag) {
        tag.putInt("Sex", sex);
        tag.putBoolean("Pregnant", pregnant);
        tag.putInt("PregnantSeconds", pregnantSeconds);
        tag.putInt("LaborTicks", laborTicksLeft);
        tag.putInt("Births", births);
        tag.putInt("BirthLimit", birthLimit);
        tag.putBoolean("Exhausted", exhausted);
        if (fatherNode != null) tag.put("FatherNode", fatherNode);
        if (fatherId != null) tag.putUUID("FatherId", fatherId);
        if (fatherGenes != null) tag.put("FatherGenome", fatherGenes);
        tag.putInt("LaborTotal", laborTotalTicks);
    }

    public void load(CompoundTag tag) {
        if (tag.contains("Sex")) {
            sex = tag.getInt("Sex");
        }
        pregnant = tag.getBoolean("Pregnant");
        pregnantSeconds = tag.getInt("PregnantSeconds");
        laborTicksLeft = tag.getInt("LaborTicks");
        births = tag.getInt("Births");
        if (tag.contains("BirthLimit")) birthLimit = tag.getInt("BirthLimit");
        exhausted = tag.getBoolean("Exhausted");
        fatherNode = tag.contains("FatherNode") ? tag.getCompound("FatherNode") : null;
        fatherId = tag.hasUUID("FatherId") ? tag.getUUID("FatherId") : null;
        fatherGenes = tag.contains("FatherGenome") ? tag.getCompound("FatherGenome") : null;
        laborTotalTicks = tag.contains("LaborTotal") ? tag.getInt("LaborTotal") : LABOR_TICKS;
        if (exhausted) applyExhaustion();
    }
}
