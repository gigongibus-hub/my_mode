package com.gigongibus.hungrycows;

import com.gigongibus.hungrycows.CowCharacter.Trait;
import com.gigongibus.hungrycows.CowGenes.Gene;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Речовини в крові корови (0–100, температура в °C) і відчуття, що з них виходять.
 * Етап 6а: глюкоза й аденозин поки беруться з голоду й утоми (поведінка та сама),
 * а гормони живуть власним життям від подій. Самотність і стрес уже впливають на корову.
 * Усі числа зібрані на початку файлу.
 */
public class CowChemistry {

    // ================= БАЗОВІ РІВНІ (ген «… базовий» змінює на ±30%) =================
    public static final double ADRENALINE_BASE = 5;
    public static final double CORTISOL_BASE = 15;
    public static final double OXYTOCIN_BASE = 30;
    public static final double DOPAMINE_BASE = 30;
    public static final double ESTROGEN_BASE = 20;      // цикл тічки додасться окремим кроком
    public static final double PROGESTERONE_BASE = 10;
    public static final double PROGESTERONE_PREGNANT = 80;
    public static final double PROLACTIN_BASE = 5;
    public static final double PROLACTIN_LACTATING = 80;
    public static final double TEMP_NORMAL = 38.8;      // °C, норма корови 38,5–39,5

    // ================= ЗА СЕКУНДУ =================
    public static final double ADRENALINE_DECAY = 4;    // на скільки спадає адреналін (ген «спад адреналіну» ±30%)
    public static final double CORTISOL_DECAY = 0.08;   // кортизол спадає повільно (ген «спад кортизолу» ±30%)
    public static final double CORTISOL_FROM_FEAR = 0.04;   // + за кожну одиницю адреналіну понад базу / 100
    public static final double CORTISOL_FROM_LONELY = 0.15; // + на секунду, поки корова самотня
    public static final double CORTISOL_FROM_STARVE = 0.2;  // + на секунду, поки голодує
    public static final double CORTISOL_FROM_PAIN = 0.02;   // + за кожну одиницю болю / 100
    public static final double OXYTOCIN_DECAY = 0.5;
    public static final double OXYTOCIN_PER_COW = 2;    // + на секунду за кожну корову поруч (до 3)
    public static final double DOPAMINE_DECAY = 1;
    public static final double PAIN_DECAY = 2;
    public static final double HORMONE_FOLLOW = 0.02;   // естроген/прогестерон/пролактин повільно йдуть до цілі (2% різниці)

    // ================= ПОДІЇ (ген «чутливість до гормонів» ±30%) =================
    public static final double PAIN_PER_DAMAGE = 12;    // біль за 1 HP урону
    public static final double ADRENALINE_ON_HIT = 40;
    public static final double DOPAMINE_ON_TREAT = 30;
    public static final double DOPAMINE_ON_EAT = 8;
    public static final double OXYTOCIN_ON_NURSING = 30;
    public static final double OXYTOCIN_ON_BIRTH = 60;

    // ================= САМОТНІСТЬ =================
    public static final double COMPANY_RADIUS = 16;     // інші корови ближче цього = не сама
    public static final int LONELY_AFTER_SECONDS = 300; // самотня після 5 хв без інших корів
    public static final int LONELY_FULL_SECONDS = 300;  // ще за 5 хв самотність доходить до 100
    public static final double LONELY_FEAR = 0.30;      // повна самотність: тікає раніше на 30%

    // ================= СТРЕС І МОЛОКО =================
    public static final double STRESS_MILK_SLOWDOWN = 0.5; // кортизол 100 → молоко відновлюється вдвічі повільніше

    private final HungryCow cow;
    private double adrenaline;
    private double cortisol;
    private double oxytocin;
    private double dopamine;
    private double pain = 0;
    private double estrogen;
    private double progesterone;
    private double prolactin;
    private double temperature = TEMP_NORMAL;
    private int aloneSeconds = 0;

    public CowChemistry(HungryCow cow) {
        this.cow = cow;
        this.adrenaline = ADRENALINE_BASE;
        this.cortisol = CORTISOL_BASE;
        this.oxytocin = OXYTOCIN_BASE;
        this.dopamine = DOPAMINE_BASE;
        this.estrogen = ESTROGEN_BASE;
        this.progesterone = PROGESTERONE_BASE;
        this.prolactin = PROLACTIN_BASE;
    }

    private double g(Gene gene) {
        return cow.gene(gene);
    }

    // ---------- раз на секунду ----------

    public void secondTick() {
        // адреналін: стрибає до рівня страху, потім спадає
        double adrBase = ADRENALINE_BASE * g(Gene.ADRENALINE_BASE);
        if (cow.fear > adrenaline) adrenaline = cow.fear;
        adrenaline = toward(adrenaline, adrBase, ADRENALINE_DECAY * g(Gene.ADRENALINE_DECAY));

        // компанія: чи є інші корови поруч
        int company = cow.level().getEntitiesOfClass(HungryCow.class,
                cow.getBoundingBox().inflate(COMPANY_RADIUS), c -> c != cow && c.isAlive()).size();
        aloneSeconds = company > 0 ? 0 : aloneSeconds + 1;

        // кортизол: повільно накопичується від страху, самотності, голодування, болю
        double stressK = (0.5 + cow.character.fraction(Trait.STRESS_SENSITIVITY)) * g(Gene.HORMONE_SENSITIVITY);
        double rise = Math.max(0, adrenaline - adrBase) / 100.0 * CORTISOL_FROM_FEAR * 100
                + (isLonely() ? CORTISOL_FROM_LONELY * loneliness() / 100.0 : 0)
                + (cow.hunger > HungryCow.STARVE_AT ? CORTISOL_FROM_STARVE : 0)
                + pain / 100.0 * CORTISOL_FROM_PAIN * 100;
        cortisol = clamp(cortisol + rise * stressK);
        cortisol = toward(cortisol, CORTISOL_BASE * g(Gene.CORTISOL_BASE), CORTISOL_DECAY * g(Gene.CORTISOL_DECAY));

        // окситоцин: інші корови поруч, гравець-друг поруч
        double oxyBase = OXYTOCIN_BASE * g(Gene.OXYTOCIN_BASE);
        double oxyRise = Math.min(3, company) * OXYTOCIN_PER_COW;
        for (Player p : cow.level().players()) {
            if (cow.distanceToSqr(p) < COMPANY_RADIUS * COMPANY_RADIUS && cow.trustOf(p) >= CowTrust.FRIEND_FROM) {
                oxyRise += OXYTOCIN_PER_COW;
                break;
            }
        }
        double oxyTarget = isLonely() ? oxyBase * (1.0 - loneliness() / 100.0) : oxyBase + oxyRise * 5;
        oxytocin = toward(clamp(oxytocin), Math.min(100, oxyTarget), OXYTOCIN_DECAY + oxyRise * 0.2);

        dopamine = toward(dopamine, DOPAMINE_BASE * g(Gene.DOPAMINE_BASE), DOPAMINE_DECAY);
        pain = toward(pain, 0, PAIN_DECAY);

        // статеві гормони й пролактин: повільно до цілі
        double progTarget = cow.repro.isPregnant() ? PROGESTERONE_PREGNANT : PROGESTERONE_BASE * g(Gene.PROGESTERONE_BASE);
        progesterone += (progTarget - progesterone) * HORMONE_FOLLOW;
        double estTarget = cow.repro.isFemale() ? ESTROGEN_BASE * g(Gene.ESTROGEN_BASE) : ESTROGEN_BASE * 0.3;
        estrogen += (estTarget - estrogen) * HORMONE_FOLLOW;
        double prolTarget = cow.milk.isLactating() ? PROLACTIN_LACTATING * g(Gene.PROLACTIN_BASE) : PROLACTIN_BASE;
        prolactin += (prolTarget - prolactin) * HORMONE_FOLLOW;

        // температура: отрута й біль піднімають
        double t = TEMP_NORMAL + pain * 0.01;
        if (cow.hasEffect(MobEffects.POISON) || cow.toxinActive()) t += 1.0;
        temperature += (t - temperature) * 0.1;
    }

    // ---------- події ----------

    public void onHurt(float damage) {
        double k = g(Gene.HORMONE_SENSITIVITY);
        pain = clamp(pain + damage * PAIN_PER_DAMAGE * (0.5 + cow.character.fraction(Trait.PAIN_SENSITIVITY)) * k);
        adrenaline = clamp(adrenaline + ADRENALINE_ON_HIT * k);
    }

    public void onTreat() {
        dopamine = clamp(dopamine + DOPAMINE_ON_TREAT * g(Gene.HORMONE_SENSITIVITY));
    }

    public void onEat() {
        dopamine = clamp(dopamine + DOPAMINE_ON_EAT * g(Gene.HORMONE_SENSITIVITY));
    }

    public void onNursing() {
        oxytocin = clamp(oxytocin + OXYTOCIN_ON_NURSING * g(Gene.HORMONE_SENSITIVITY));
    }

    public void onBirth() {
        oxytocin = clamp(oxytocin + OXYTOCIN_ON_BIRTH * g(Gene.HORMONE_SENSITIVITY));
    }

    public void addPain(double amount) {
        pain = clamp(pain + amount);
    }

    // ---------- відчуття (0–100) ----------

    public boolean isLonely() {
        return aloneSeconds >= LONELY_AFTER_SECONDS;
    }

    public int loneliness() {
        if (!isLonely()) return 0;
        return (int) Math.min(100, (aloneSeconds - LONELY_AFTER_SECONDS) * 100.0 / LONELY_FULL_SECONDS);
    }

    public double glucose() {   // етап 6а: з голоду
        return clamp(100.0 - cow.hunger * 100.0 / (HungryCow.STARVE_AT + 20));
    }

    public double adenosine() { // етап 6а: з утоми
        return clamp(cow.fatigue * 100.0 / 60.0);
    }

    public int feelHunger() { return (int) Math.round(100 - glucose()); }
    public int feelTired() { return (int) Math.round(adenosine()); }
    public int feelFear() { return (int) Math.round(clamp(adrenaline * 0.7 + cortisol * 0.3)); }
    public int feelStress() { return (int) Math.round(cortisol); }
    public int feelPain() { return (int) Math.round(pain); }
    public int feelMating() { return (int) Math.round(cow.repro.isFemale() ? clamp(estrogen * 1.5) : 0); }
    public int feelMotherhood() { return (int) Math.round(clamp((prolactin + oxytocin) / 2.0)); }
    public int feelUnwell() { return (int) Math.round(clamp((temperature - TEMP_NORMAL) * 40)); }

    public int feelComfort() {
        return (int) Math.round(clamp(50 + (dopamine - DOPAMINE_BASE) * 0.5 + (oxytocin - OXYTOCIN_BASE) * 0.5
                - (cortisol - CORTISOL_BASE) * 0.6 - pain * 0.5 - loneliness() * 0.3));
    }

    // ---------- вплив на корову ----------

    // самотня корова полохливіша: множник порогу страху (1.0 … 0.7)
    public double fearThresholdFactor() {
        return 1.0 - LONELY_FEAR * loneliness() / 100.0;
    }

    // стрес сповільнює молоко: множник швидкості (1.0 … 0.5)
    public double milkSpeedFactor() {
        return 1.0 - STRESS_MILK_SLOWDOWN * cortisol / 100.0;
    }

    // ---------- для книги ----------

    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("#Самопочуття");
        out.add("Голод: " + feelHunger());
        out.add("Втома: " + feelTired());
        out.add("Страх: " + feelFear());
        out.add("Стрес: " + feelStress());
        out.add("Самотність: " + loneliness() + (isLonely() ? "" : (aloneSeconds > 0 ? " (сама вже " + aloneSeconds + " с)" : "")));
        out.add("Біль: " + feelPain());
        out.add("Нездужання: " + feelUnwell());
        out.add("Комфорт: " + feelComfort());
        if (cow.repro.isFemale() && !cow.isBaby()) {
            out.add("Бажання пари: " + feelMating());
            out.add("Материнство: " + feelMotherhood());
        }
        out.add("#Речовини в крові");
        out.add("Глюкоза: " + Math.round(glucose()));
        out.add("Аденозин: " + Math.round(adenosine()));
        out.add("Адреналін: " + Math.round(adrenaline));
        out.add("Кортизол: " + Math.round(cortisol));
        out.add("Окситоцин: " + Math.round(oxytocin));
        out.add("Дофамін: " + Math.round(dopamine));
        out.add("Естроген: " + Math.round(estrogen));
        out.add("Прогестерон: " + Math.round(progesterone));
        out.add("Пролактин: " + Math.round(prolactin));
        out.add("Температура: " + String.format("%.1f", temperature) + " °C");
        if (cortisol > 50) out.add("Через стрес молоко відновлюється повільніше");
        return out;
    }

    // ---------- збереження ----------

    public void save(CompoundTag tag) {
        CompoundTag c = new CompoundTag();
        c.putDouble("Adrenaline", adrenaline);
        c.putDouble("Cortisol", cortisol);
        c.putDouble("Oxytocin", oxytocin);
        c.putDouble("Dopamine", dopamine);
        c.putDouble("Pain", pain);
        c.putDouble("Estrogen", estrogen);
        c.putDouble("Progesterone", progesterone);
        c.putDouble("Prolactin", prolactin);
        c.putDouble("Temperature", temperature);
        c.putInt("AloneSeconds", aloneSeconds);
        tag.put("Chemistry", c);
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("Chemistry")) return;
        CompoundTag c = tag.getCompound("Chemistry");
        adrenaline = c.getDouble("Adrenaline");
        cortisol = c.getDouble("Cortisol");
        oxytocin = c.getDouble("Oxytocin");
        dopamine = c.getDouble("Dopamine");
        pain = c.getDouble("Pain");
        estrogen = c.getDouble("Estrogen");
        progesterone = c.getDouble("Progesterone");
        prolactin = c.getDouble("Prolactin");
        temperature = c.contains("Temperature") ? c.getDouble("Temperature") : TEMP_NORMAL;
        aloneSeconds = c.getInt("AloneSeconds");
    }

    // ---------- допоміжне ----------

    private static double toward(double value, double target, double step) {
        if (value > target) return Math.max(target, value - step);
        return Math.min(target, value + step);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }
}
