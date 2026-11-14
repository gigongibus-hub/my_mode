package com.gigongibus.hungrycows;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * Старість («Після плану» 3, бажання Fij: корова живе ~1000 ігрових діб). Вік рахується в добах з тим самим прискоренням,
 * що й розмноження (CowCycle.TIME_SPEED; теля росте 100 діб — корова живе в 10 разів довше). Довжина життя = 1000 діб × ген
 * «тривалість життя» (×0,7…×1,3). Нова корова (яйце, природа, стара збереженка) — 100–400 діб; теля народжується з 0.
 * З 85% життя — стара: на 15% повільніша, не вагітніє, молоко на 40% повільніше. Життя скінчилось — помирає вночі уві сні
 * (не заснула — щонайпізніше на 110% життя).
 * Усі числа зібрані на початку файлу.
 */
public class CowAging {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double LIFESPAN_DAYS = 1000;     // середня довжина життя (ген ×0,7…×1,3)
    public static final double START_MIN_DAYS = 100;     // нова доросла корова: 100 ...
    public static final double START_MAX_DAYS = 400;     // ... – 400 діб
    public static final double OLD_FRACTION = 0.85;      // з 85% життя — стара
    public static final double OLD_SLOWDOWN = 0.15;      // стара — на 15% повільніша
    public static final double OLD_MILK = 0.6;           // стара дає молоко на 40% повільніше
    public static final double LATE_DEATH = 1.1;         // помирає вночі уві сні; не заснула — щонайпізніше на 110% життя

    private static final UUID OLD_ID = UUID.fromString("8e7d6c5b-4a39-4281-9f0e-1d2c3b4a5968");

    private final HungryCow cow;
    public double ageSeconds;              // вік у секундах гри (уже з прискоренням часу)
    private boolean slowApplied = false;

    public CowAging(HungryCow cow) {
        this.cow = cow;
        double days = START_MIN_DAYS + cow.getRandom().nextDouble() * (START_MAX_DAYS - START_MIN_DAYS);
        ageSeconds = days * CowCycle.SECONDS_PER_DAY;
    }

    public void newborn() {
        ageSeconds = 0;
    }

    public double ageDays() {
        return ageSeconds / CowCycle.SECONDS_PER_DAY;
    }

    public double lifespanDays() {
        return LIFESPAN_DAYS * cow.genes.factor(CowGenes.Gene.LIFESPAN);
    }

    public boolean old() {
        return ageDays() >= lifespanDays() * OLD_FRACTION;
    }

    public void secondTick() {
        ageSeconds += CowCycle.TIME_SPEED;
        // життя скінчилось — помирає тихо вночі уві сні (Fij); якщо так і не заснула — щонайпізніше на 110% життя
        if (ageDays() >= lifespanDays() && (cow.sleeping || ageDays() >= lifespanDays() * LATE_DEATH)) {
            cow.tell(cow.family.name + " померла від старості" + (cow.sleeping ? " уві сні" : "") + " (" + Math.round(ageDays()) + " діб)");
            cow.kill();
            return;
        }
        boolean slow = old();
        if (slow != slowApplied) {
            AttributeInstance inst = cow.getAttribute(Attributes.MOVEMENT_SPEED);
            if (inst != null) {
                inst.removeModifier(OLD_ID);
                if (slow) inst.addTransientModifier(new AttributeModifier(OLD_ID, "Old age", -OLD_SLOWDOWN,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            slowApplied = slow;
            if (slow) cow.tell(cow.family.name + " постаріла");
        }
    }

    // Молоко на старість повільніше
    public double milkFactor() {
        return old() ? OLD_MILK : 1.0;
    }

    public String line() {
        return "Вік: " + Math.round(ageDays()) + " діб з ~" + Math.round(lifespanDays()) + (old() ? " — стара (повільніша, не вагітніє)" : "");
    }

    public void save(CompoundTag tag) {
        tag.putDouble("AgeSeconds", ageSeconds);
    }

    public void load(CompoundTag tag) {
        if (tag.contains("AgeSeconds")) ageSeconds = tag.getDouble("AgeSeconds");   // інакше (стара збереженка) — випадковий, як нова
    }
}
