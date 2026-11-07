package com.gigongibus.hungrycows;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.biome.Biomes;

import java.util.UUID;

/**
 * Перегрівання (ідея Fij). Шкала 0–100.
 * На сонці росте: +1 кожні 20 с, у пустелі +4, в інших теплих біомах +2; бігла — ×1,5; ген «витривалість до спеки».
 * У тіні (і вночі, і в дощ) спадає −1 кожні 5 с, у воді −5 кожні 5 с.
 * 30+ — шукає тінь (до 30 тінь не шукає, спокійно ремигає на сонці).
 * 50+ — тепловий удар: «погано хімічно» (температура тіла й кортизол ростуть, нездужання, апетит менший), п'є вдвічі
 * частіше, ходить повільніше.
 * 100 — втрачає свідомість: лежить, не рухається. Без допомоги через 2 хв починає втрачати здоров'я й може померти.
 * Рятує відро води (ПКМ, −50) або прохолода: ніч, тінь, вода — шкала спадає до 80, і корова приходить до тями.
 * Усі числа зібрані на початку файлу.
 */
public class CowHeat {

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double MAX = 100;
    public static final double SUN_GAIN = 1;             // на сонці: +1 ...
    public static final double WARM_GAIN = 2;            // ... в теплому біомі +2 ...
    public static final double DESERT_GAIN = 4;          // ... у пустелі +4 ...
    public static final int GAIN_EVERY_SECONDS = 20;     // ... кожні 20 с
    public static final float WARM_BIOME = 1.0F;         // «теплий біом» = температура біому вища за це (савана, безплідні землі)
    public static final double RUN_GAIN = 1.5;           // бігла — набирає в 1,5 раза швидше
    public static final double SHADE_COOL = 1;           // у тіні (вночі, у дощ): −1 ...
    public static final double WATER_COOL = 5;           // ... у воді −5 ...
    public static final int COOL_EVERY_SECONDS = 5;      // ... кожні 5 с
    public static final double SEEK_SHADE_AT = 30;       // з 30 шукає тінь
    public static final double STROKE_AT = 50;           // з 50 тепловий удар: корові погано
    public static final double STROKE_FEVER = 1.5;       // температура тіла до +1,5 °C при 100 (→ нездужання)
    public static final double STROKE_CORTISOL = 0.5;    // кортизол +0,5 щосекунди при 100 (менше — пропорційно)
    public static final double STROKE_THIRST = 2.0;      // тепловий удар: спрага вдвічі швидша
    public static final double STROKE_SLOWDOWN = 0.15;   // тепловий удар: на 15% повільніша
    public static final double WAKE_AT = 80;             // без тями: охолола до 80 — приходить до тями
    public static final int DAMAGE_AFTER_SECONDS = 120;  // без тями 2 хв — починає втрачати здоров'я ...
    public static final int DAMAGE_EVERY_SECONDS = 15;   // ... по 1 кожні 15 с
    public static final float DAMAGE = 1.0F;
    public static final double BUCKET_COOL = 50;         // відро води: −50
    public static final int RESCUE_TRUST = 10;           // облив водою корову без тями — довіра +10

    private static final UUID STROKE_ID = UUID.fromString("7a1c2e3f-4b5d-4e6f-8a9b-0c1d2e3f4a5b");

    private final HungryCow cow;
    public double heat = 0;
    public boolean unconscious = false;
    private int outSeconds = 0;       // скільки секунд без тями
    private boolean slowApplied = false;

    public CowHeat(HungryCow cow) {
        this.cow = cow;
    }

    public boolean wantsShade() {
        return heat >= SEEK_SHADE_AT;
    }

    public boolean stroke() {
        return heat >= STROKE_AT;
    }

    // 0 при 50 і менше, 1 при 100
    public double strokeLevel() {
        return Math.max(0, (heat - STROKE_AT) / (MAX - STROKE_AT));
    }

    private double sunGainPer20s() {
        var biome = cow.level().getBiome(cow.blockPosition());
        if (biome.is(Biomes.DESERT)) return DESERT_GAIN;
        if (biome.value().getBaseTemperature() > WARM_BIOME) return WARM_GAIN;
        return SUN_GAIN;
    }

    public void secondTick() {
        // спершу перевірка «дійшла до 100» (шкала могла дійти до краю за минулу секунду), потім зміна за секунду
        if (!unconscious && heat >= MAX) faint();
        if (cow.isInWater()) {
            heat -= WATER_COOL / COOL_EVERY_SECONDS;
        } else if (cow.inSun()) {
            double g = sunGainPer20s() / GAIN_EVERY_SECONDS * cow.genes.inverse(CowGenes.Gene.HEAT_TOLERANCE);
            if (cow.isRunning()) g *= RUN_GAIN;
            heat += g;
        } else {
            heat -= SHADE_COOL / COOL_EVERY_SECONDS;
        }
        heat = Math.max(0, Math.min(MAX, heat));

        if (!unconscious && heat >= MAX) faint();
        else if (unconscious && heat <= WAKE_AT) wake();
        if (unconscious) {
            outSeconds++;
            if (outSeconds > DAMAGE_AFTER_SECONDS && outSeconds % DAMAGE_EVERY_SECONDS == 0) {
                cow.hurt(cow.damageSources().generic(), DAMAGE);
            }
            if (cow.level() instanceof ServerLevel server && outSeconds % 3 == 0) {
                server.sendParticles(ParticleTypes.SMOKE, cow.getX(), cow.getY() + cow.getBbHeight(), cow.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
            }
        }
        cow.setUnconsciousVisual(unconscious);

        // тепловий удар — повільніша
        boolean slow = stroke();
        if (slow != slowApplied) {
            AttributeInstance inst = cow.getAttribute(Attributes.MOVEMENT_SPEED);
            if (inst != null) {
                inst.removeModifier(STROKE_ID);
                if (slow) inst.addTransientModifier(new AttributeModifier(STROKE_ID, "Heat stroke", -STROKE_SLOWDOWN,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            slowApplied = slow;
        }
    }

    private void faint() {
        unconscious = true;
        outSeconds = 0;
        cow.getNavigation().stop();
        cow.tell(cow.family.name + " перегрілась і знепритомніла! Облий її водою (відро)");
    }

    private void wake() {
        unconscious = false;
        outSeconds = 0;
        cow.tell(cow.family.name + " прийшла до тями (перегрівання " + Math.round(heat) + ")");
    }

    // Облили з відра: true, якщо корова була без тями
    public boolean pourWater() {
        boolean was = unconscious;
        heat = Math.max(0, heat - BUCKET_COOL);
        if (unconscious && heat <= WAKE_AT) wake();
        cow.setUnconsciousVisual(unconscious);
        return was;
    }

    public String line() {
        return "Перегрівання: " + Math.round(heat) + " (шукає тінь з " + (int) SEEK_SHADE_AT + ", погано з " + (int) STROKE_AT
                + ", без тями при " + (int) MAX + ")" + (unconscious ? " — БЕЗ ТЯМИ! Облий водою" : stroke() ? " — тепловий удар" : "");
    }

    public void save(CompoundTag tag) {
        tag.putDouble("Heat", heat);
        tag.putBoolean("HeatOut", unconscious);
        tag.putInt("HeatOutSeconds", outSeconds);
    }

    public void load(CompoundTag tag) {
        heat = tag.getDouble("Heat");
        unconscious = tag.getBoolean("HeatOut");
        outSeconds = tag.getInt("HeatOutSeconds");
    }
}
