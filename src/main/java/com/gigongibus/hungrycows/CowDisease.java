package com.gigongibus.hungrycows;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * Хвороба «кашель» (ідея Fij 14, перша для проби; ліки й столи — коли Fij зробить блоки).
 * Хвора корова кашляє (звук і частинки), має жар (температура тіла +1 °C → нездужання → менше їсть, мозок відчуває),
 * на 15% повільніша. Заражає корів ближче 3 блоків: раз на 10 с шанс 8% × гени «загальний імунітет» і «стійкість
 * до зараження від корів» (сильні гени — до ×0,7, слабкі — до ×1,3). Хворіє 2–4 доби × ген «швидкість одужання»
 * (час розмноження теж прискорений TIME_SPEED). Наприкінці 3% (× імунітет) — помирає, інакше одужує й 10 діб не хворіє.
 * Команда /hungrycows sick — заражена корова. Усі числа зібрані на початку файлу.
 */
public class CowDisease {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double SPREAD_RANGE = 3;          // заражає корів ближче 3 блоків ...
    public static final int SPREAD_EVERY = 10;            // ... раз на 10 с ...
    public static final double SPREAD_CHANCE = 0.08;      // ... з шансом 8% (× гени)
    public static final double MIN_DAYS = 2, MAX_DAYS = 4; // хворіє 2–4 доби (× ген одужання)
    public static final double DEATH_CHANCE = 0.03;       // наприкінці 3% (× імунітет) — помирає
    public static final double IMMUNE_DAYS = 10;          // одужала — 10 діб не хворіє
    public static final double FEVER = 1.0;               // жар +1 °C
    public static final double SLOWDOWN = 0.15;           // на 15% повільніша
    public static final int COUGH_MIN = 10, COUGH_MAX = 20; // кашляє раз на 10–20 с

    private static final UUID SICK_ID = UUID.fromString("1b2c3d4e-5f60-4718-9a2b-3c4d5e6f7081");

    private final HungryCow cow;
    private double sickSeconds = 0;    // скільки ще хворіти (секунди гри з прискоренням)
    private double immuneSeconds = 0;  // скільки ще не хворіти після одужання
    private int nextCough = 0;
    private boolean slowApplied = false;

    public CowDisease(HungryCow cow) {
        this.cow = cow;
    }

    public boolean sick() {
        return sickSeconds > 0;
    }

    // Заразитись (якщо не хворіє й не має імунітету); true — заразилась
    public boolean infect() {
        if (sick() || immuneSeconds > 0 || !cow.isAlive()) return false;
        double days = MIN_DAYS + cow.getRandom().nextDouble() * (MAX_DAYS - MIN_DAYS);
        sickSeconds = days * cow.genes.inverse(CowGenes.Gene.RECOVERY_SPEED) * CowCycle.SECONDS_PER_DAY;
        nextCough = 2;
        cow.tell(cow.family.name + " захворіла (кашель)");
        return true;
    }

    public double fever() {
        return sick() ? FEVER : 0;
    }

    public void secondTick() {
        if (immuneSeconds > 0) immuneSeconds = Math.max(0, immuneSeconds - CowCycle.TIME_SPEED);
        boolean wasSick = sick();
        if (wasSick) {
            sickSeconds -= CowCycle.TIME_SPEED;
            if (--nextCough <= 0) {
                nextCough = COUGH_MIN + cow.getRandom().nextInt(COUGH_MAX - COUGH_MIN + 1);
                cow.playSound(SoundEvents.COW_HURT, 0.7F, 1.6F);
                if (cow.level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.SNEEZE, cow.getX(), cow.getEyeY(), cow.getZ(), 6, 0.2, 0.1, 0.2, 0.02);
                }
            }
            if (cow.tickCount % (SPREAD_EVERY * 20) < 20) spread();
            if (!sick()) {   // хвороба скінчилась
                double death = DEATH_CHANCE * cow.genes.inverse(CowGenes.Gene.IMMUNITY);
                if (cow.getRandom().nextDouble() < death) {
                    cow.tell(cow.family.name + " померла від хвороби");
                    cow.kill();
                    return;
                }
                immuneSeconds = IMMUNE_DAYS * CowCycle.SECONDS_PER_DAY;
                cow.tell(cow.family.name + " одужала");
            }
        }
        boolean slow = sick();
        if (slow != slowApplied) {
            AttributeInstance inst = cow.getAttribute(Attributes.MOVEMENT_SPEED);
            if (inst != null) {
                inst.removeModifier(SICK_ID);
                if (slow) inst.addTransientModifier(new AttributeModifier(SICK_ID, "Sick", -SLOWDOWN, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            slowApplied = slow;
        }
    }

    private void spread() {
        for (HungryCow o : cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(SPREAD_RANGE),
                o -> o != cow && o.isAlive() && !o.disease.sick())) {
            double chance = SPREAD_CHANCE * o.genes.inverse(CowGenes.Gene.IMMUNITY) * o.genes.inverse(CowGenes.Gene.CONTACT_INFECTION_RESIST);
            if (o.getRandom().nextDouble() < chance) o.disease.infect();
        }
    }

    public String line() {
        if (sick()) return "Хвороба: кашель (ще ~" + Math.max(1, Math.round(sickSeconds / CowCycle.SECONDS_PER_DAY)) + " діб)";
        if (immuneSeconds > 0) return "Здорова (після кашлю не хворіє ще ~" + Math.round(immuneSeconds / CowCycle.SECONDS_PER_DAY) + " діб)";
        return "Здорова";
    }

    public void save(CompoundTag tag) {
        tag.putDouble("SickSeconds", sickSeconds);
        tag.putDouble("ImmuneSeconds", immuneSeconds);
    }

    public void load(CompoundTag tag) {
        sickSeconds = tag.getDouble("SickSeconds");
        immuneSeconds = tag.getDouble("ImmuneSeconds");
    }
}
