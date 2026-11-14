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
    public static final int PREGNANCY_MIN_DAYS = 90;      // вагітність: від 90 до 110 ігрових діб (ген ±30%)
    public static final int PREGNANCY_MAX_DAYS = 110;     // час ділиться на CowCycle.TIME_SPEED
    public static final float CONCEPTION_CHANCE = 0.6F;   // шанс завагітніти, якщо в тічку побула з биком 15 с
    public static final float INFERTILE_CHANCE = 0.03F;   // 3% корів народжуються безплідними (ген «плідність» ±30%)
    public static final float FREEMARTIN_CHANCE = 0.9F;   // двійнята бик + телиця: телиця безплідна з шансом 90%
    public static final int LABOR_TICKS = 200;            // пологи (10 сек)
    public static final float LABOR_DAMAGE = 2.0F;        // скільки HP мати втрачає за пологи (не смертельно)
    public static final float CALF_DEATH_CHANCE = 0.02F;  // шанс, що теля народиться мертвим 2% (для тестів було 50%)
    public static final int BREED_COOLDOWN_TICKS = 6000;  // пауза перед наступним розмноженням (5 хв)

    // ================= СКІЛЬКИ ТЕЛЯТ =================
    public static final int BIRTHS_MIN = 5;               // звичайна самка: від 5 до 7 пологів за життя
    public static final int BIRTHS_MAX = 7;
    public static final float BIG_FAMILY_CHANCE = 0.05F;  // 5% самок: від 10 до 12 пологів
    public static final int BIG_BIRTHS_MIN = 10;
    public static final int BIG_BIRTHS_MAX = 12;
    // ================= ДВІЙНЯТА =================
    public static final float TWIN_CHANCE = 0.03F;        // шанс двійні при гені 50 (у справжніх корів 1–4%)
    public static final float TWIN_CHANCE_MIN = 0.01F;    // при гені 0
    public static final float TWIN_CHANCE_MAX = 0.06F;    // при гені 100
    public static final float SECOND_CALF_DEATH_MULTIPLIER = 2.0F; // друге теля гине вдвічі частіше ...
    public static final float SECOND_CALF_DEATH_MAX = 0.9F;        // ... але не частіше за 90%

    // ================= ДОПОМОГА ПРИ ПОЛОГАХ =================
    public static final int ASSIST_HOLD_TICKS = 60;       // скільки тримати ПКМ (3 сек)
    public static final int ASSIST_GAP_TICKS = 10;        // перерва довша за це = відпустив, відлік заново
    public static final float ASSIST_DEATH_MULTIPLIER = 0.5F; // шанс мертвого теляти вдвічі менший
    public static final float ASSIST_PAIN_MULTIPLIER = 0.5F;  // мати втрачає вдвічі менше здоров'я
    public static final int ASSIST_TRUST = 5;             // довіра до того, хто допоміг
    public static final double ASSIST_REACH = 3.0;        // з якої відстані можна допомогти
    public static final double ASSIST_BEHIND_DOT = -0.5;  // «ззаду»: кут від голови корови більший за 120°

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
    private boolean infertile = false;     // безплідна: має цикл, але не вагітніє
    private boolean freemartin = false;    // безплідна через фримартинізм (сестра бика-двійняти)
    private boolean exhausted = false;     // більше не може народжувати і давати молоко після останньої лактації
    private CompoundTag fatherNode = null; // батько поточної вагітності для родинного дерева
    private UUID fatherId = null;
    private int forcedCalves = 0;          // 0 = випадково; 1 або 2 = задано командою для тестів
    private boolean assisted = false;      // гравець допоміг з цими пологами
    private UUID assistPlayer = null;      // хто зараз допомагає (не зберігається)
    private int assistStart = 0;
    private int assistLast = -1000;
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
        double days = PREGNANCY_MIN_DAYS + cow.getRandom().nextDouble() * (PREGNANCY_MAX_DAYS - PREGNANCY_MIN_DAYS);
        days *= cow.genes.factor(CowGenes.Gene.PREGNANCY_LENGTH);
        return Math.max(1, (int) Math.round(days * CowCycle.SECONDS_PER_DAY / CowCycle.TIME_SPEED));
    }

    public boolean isInfertile() {
        return infertile || freemartin || cow.inbreeding.sterile() || cow.aging.old();
    }

    public boolean isFreemartin() {
        return freemartin;
    }

    // Для теляти: чи народилася безплідною (лише самки)
    public void rollInfertility() {
        infertile = isFemale()
                && cow.getRandom().nextFloat() < INFERTILE_CHANCE * cow.genes.inverse(CowGenes.Gene.FERTILITY);
    }

    // Самка в тічці побула з биком досить довго (викликається з MateGoal)
    public void mateWith(HungryCow bull) {
        if (!isFemale() || bull.repro.isFemale() || pregnant) return;
        cow.cycle.markMated();
        cow.level().broadcastEntityEvent(cow, (byte) 18);   // сердечка
        bull.setAge(bull.repro.breedPause());               // бик відпочиває 5 хв
        if (exhausted || isInfertile()) {
            say(cow.family.name + " спарувалася з " + bull.family.name + ", але не завагітніє");
            return;
        }
        if (cow.getRandom().nextFloat() < CONCEPTION_CHANCE) {
            conceive(bull);
        } else {
            say(cow.family.name + " спарувалася з " + bull.family.name + " (чи завагітніла, стане видно в діеструсі)");
        }
    }

    public int matingTicksNeeded() {
        return matingTicks();
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

    // «ще 12 діб» або «ще 3:20», якщо менше доби
    public String pregnancyText() {
        long realSeconds = pregnantSeconds;
        double days = realSeconds * (double) CowCycle.TIME_SPEED / CowCycle.SECONDS_PER_DAY;
        if (days >= 1) return (int) Math.ceil(days) + " діб";
        return (realSeconds / 60) + ":" + String.format("%02d", realSeconds % 60);
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
        say(cow.family.name + " спарувалася з " + father.family.name + " (чи завагітніла, стане видно в діеструсі)");
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
            if (assisted) pain *= ASSIST_PAIN_MULTIPLIER;
            cow.chem.addPain(pain * CowChemistry.PAIN_PER_DAMAGE);
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

        // одне теля чи двійнята (команда для тестів може задати це наперед)
        int calves = forcedCalves > 0 ? forcedCalves : (cow.getRandom().nextFloat() < twinChance() ? 2 : 1);
        float firstDeath = calfDeathChance();
        float secondDeath = Math.min(SECOND_CALF_DEATH_MAX, firstDeath * SECOND_CALF_DEATH_MULTIPLIER);
        if (assisted) {
            firstDeath *= ASSIST_DEATH_MULTIPLIER;
            secondDeath *= ASSIST_DEATH_MULTIPLIER;
        }
        if (calves == 2) say(cow.family.name + " народжує двійнят!");

        // близькоспоріднене схрещування: більше мертвих телят (CowInbreeding)
        double kin = CowInbreeding.coefficient(cow.family.asNode(), fatherNode);
        if (kin > 0) {
            firstDeath = Math.min((float) CowInbreeding.DEATH_MAX, firstDeath + CowInbreeding.stillbirthBonus(kin));
            secondDeath = Math.min((float) CowInbreeding.DEATH_MAX, secondDeath + CowInbreeding.stillbirthBonus(kin));
            say("Батьки теляти — родичі (спорідненість " + Math.round(kin * 1000) / 10.0 + "%)");
        }
        int alive = 0;
        java.util.List<HungryCow> born = new java.util.ArrayList<>();
        for (int k = 0; k < calves; k++) {
            births++;   // ліміт рахує телят, мертві теж
            float death = k == 0 ? firstDeath : secondDeath;
            if (cow.getRandom().nextFloat() < death) {
                cow.playSound(SoundEvents.COW_DEATH, 0.8F, 1.3F);
                say((k == 0 ? "Теля" : "Друге теля") + " народилося мертвим...");
                continue;
            }
            HungryCow baby = ModEntities.HUNGRY_COW.get().create(cow.level());
            if (baby == null) continue;
            baby.setBaby(true);
            baby.moveTo(cow.getX(), cow.getY(), cow.getZ(), cow.getYRot(), 0.0F);
            baby.family.setupNewborn(cow, fatherNode);
            baby.genes.inherit(cow.genes.saveTag(), fatherGenes, cow.getRandom());
            baby.inbreeding.onBorn(kin);
            baby.aging.newborn();
            if (baby.inbreeding.anomaly != CowInbreeding.Anomaly.NONE) say("У теляти вада: " + baby.inbreeding.anomaly.title);
            baby.character.newborn(cow.getRandom());
            baby.onGenesChanged();
            baby.repro.rollBirthLimit();   // ліміт телят — уже за успадкованими генами
            baby.setHealth(baby.getMaxHealth());
            baby.repro.rollInfertility();
            born.add(baby);
            cow.family.addChild(baby.family.name);
            HungryCow father = findFather();
            if (father != null) father.family.addChild(baby.family.name);
            cow.level().addFreshEntity(baby);
            cow.level().broadcastEntityEvent(cow, (byte) 18);
            say("Народилося теля " + baby.family.name + "!");
            alive++;
        }
        // фримартинізм: двійнята різної статі → телиця майже завжди безплідна
        if (born.size() == 2 && born.get(0).repro.isFemale() != born.get(1).repro.isFemale()) {
            HungryCow heifer = born.get(0).repro.isFemale() ? born.get(0) : born.get(1);
            if (cow.getRandom().nextFloat() < FREEMARTIN_CHANCE) {
                heifer.repro.freemartin = true;
                say("Двійнята бик і телиця: " + heifer.family.name + " буде безплідною (фримартинізм)");
            }
        }
        cow.chem.onBirth();
        cow.cycle.onBirth();
        // молоко з'являється після пологів; якщо всі телята мертві, воно все дістанеться гравцю
        cow.milk.startLactation(alive > 0);
        fatherNode = null;
        fatherId = null;
        fatherGenes = null;
        forcedCalves = 0;
        assisted = false;
        assistPlayer = null;

        if (births >= birthLimit) {
            exhausted = true;
            applyExhaustion();
            say(cow.family.name + " більше не зможе народжувати: народила телят " + births);
        }
    }

    // Шанс двійні за геном: 0 → 1%, 50 → 3%, 100 → 6%
    private float twinChance() {
        int g = cow.genes.value(CowGenes.Gene.TWINS);
        return g <= 50
                ? TWIN_CHANCE_MIN + (TWIN_CHANCE - TWIN_CHANCE_MIN) * g / 50.0F
                : TWIN_CHANCE + (TWIN_CHANCE_MAX - TWIN_CHANCE) * (g - 50) / 50.0F;
    }

    // ---------- допомога при пологах ----------

    // Гравець ззаду, присівши, тримає ПКМ (викликається з HungryCow кожні ~4 тіки, поки тримає)
    public void assistTick(net.minecraft.world.entity.player.Player player) {
        if (!inLabor() || assisted) return;
        int now = cow.tickCount;
        if (assistPlayer == null || !assistPlayer.equals(player.getUUID()) || now - assistLast > ASSIST_GAP_TICKS) {
            assistPlayer = player.getUUID();
            assistStart = now;   // відпустив або новий гравець: відлік заново
            say(player.getName().getString() + " допомагає " + cow.family.name + " з пологами...");
        }
        assistLast = now;
        if (now - assistStart >= ASSIST_HOLD_TICKS) {
            assisted = true;
            cow.addTrust(player, ASSIST_TRUST, "допомога з пологами", false);
            say("Допомога вдалася: телята мають більше шансів вижити, " + cow.family.name + " болить менше");
        }
    }

    public boolean isAssisted() {
        return assisted;
    }

    // ---------- для тестів (команда /hungrycows birth) ----------

    // Самка одразу вагітна, пологи за seconds секунд; calves = 1 або 2
    public void startTestPregnancy(int seconds, int calves) {
        sex = 1;
        pregnant = true;
        pregnantSeconds = seconds;
        cow.cycle.forcePregnant();
        laborTicksLeft = 0;
        forcedCalves = calves;
        fatherNode = null;
        fatherId = null;
        fatherGenes = null;
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
            result += " | Телят: " + births + "/" + birthLimit + (exhausted ? " (все)" : "");
            String milk = cow.milk.label();
            if (milk != null) result += " | " + milk;
        }
        if (laborTicksLeft > 0) {
            result += " | ПОЛОГИ ще " + (laborTicksLeft / 20 + 1) + " с" + (assisted ? " (допомогли)" : "");
        } else if (cow.cycle.pregnancyVisible()) {
            result += " | Вагітна: ще " + pregnancyText();
        } else if (isFemale() && !cow.isBaby() && cow.cycle.phase() != CowCycle.Phase.NONE) {
            result += " | " + cow.cycle.phase().title;
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
        tag.putBoolean("Infertile", infertile);
        tag.putBoolean("Freemartin", freemartin);
        if (fatherNode != null) tag.put("FatherNode", fatherNode);
        if (fatherId != null) tag.putUUID("FatherId", fatherId);
        if (fatherGenes != null) tag.put("FatherGenome", fatherGenes);
        tag.putInt("LaborTotal", laborTotalTicks);
        tag.putInt("ForcedCalves", forcedCalves);
        tag.putBoolean("Assisted", assisted);
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
        infertile = tag.getBoolean("Infertile");
        freemartin = tag.getBoolean("Freemartin");
        fatherNode = tag.contains("FatherNode") ? tag.getCompound("FatherNode") : null;
        fatherId = tag.hasUUID("FatherId") ? tag.getUUID("FatherId") : null;
        fatherGenes = tag.contains("FatherGenome") ? tag.getCompound("FatherGenome") : null;
        laborTotalTicks = tag.contains("LaborTotal") ? tag.getInt("LaborTotal") : LABOR_TICKS;
        forcedCalves = tag.getInt("ForcedCalves");
        assisted = tag.getBoolean("Assisted");
        if (exhausted) applyExhaustion();
    }
}
