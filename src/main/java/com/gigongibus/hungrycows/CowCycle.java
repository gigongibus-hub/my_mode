package com.gigongibus.hungrycows;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;

/**
 * Статевий цикл самки замість ванільного розмноження (кавуна й гарбуза).
 * Проеструс → еструс (тічка) → метеструс → діеструс → знову проеструс.
 * Якщо в тічку корова завагітніла, після діеструсу цикл зупиняється до пологів, потім пауза.
 * Усі числа зібрані на початку файлу. 1 ігрова доба = 1200 секунд.
 */
public class CowCycle {

    // ================= ТЕСТОВИЙ ПЕРЕМИКАЧ =================
    // У скільки разів швидше йде час розмноження: цикл, пауза, вагітність, ріст теляти, молоко.
    // 16 = цикл ≈ пів години (для тестів). Для звичайної гри: 1.
    public static final int TIME_SPEED = 16;

    // ================= ФАЗИ (у добах) =================
    public static final int PROESTRUS_MIN_DAYS = 7;     // підготовка: активніша, тримається біля інших корів
    public static final int PROESTRUS_MAX_DAYS = 10;
    public static final double ESTRUS_DAYS = 1.0;       // тічка: пристає до корів, мукає, менше їсть, шукає бика
    public static final int METESTRUS_MIN_DAYS = 3;     // після тічки: усе спадає
    public static final int METESTRUS_MAX_DAYS = 5;
    public static final int DIESTRUS_MIN_DAYS = 5;      // спокійна фаза: тепер видно, вагітна чи ні
    public static final int DIESTRUS_MAX_DAYS = 17;
    public static final int POSTPARTUM_DAYS = 15;       // пауза після пологів до нового циклу

    // ================= ПОВЕДІНКА =================
    public static final int MOO_MIN_SECONDS = 5;        // у тічку мукає кожні 5–12 секунд
    public static final int MOO_MAX_SECONDS = 12;
    // у тічку їсть менше: див. CowChemistry.ESTRUS_APPETITE

    public static final int SECONDS_PER_DAY = 1200;

    public enum Phase {
        NONE("—"),
        PROESTRUS("проеструс (підготовка)"),
        ESTRUS("еструс (тічка)"),
        METESTRUS("метеструс (після тічки)"),
        DIESTRUS("діеструс (спокій)"),
        PREGNANT("вагітна"),
        POSTPARTUM("пауза після пологів");

        public final String title;

        Phase(String title) {
            this.title = title;
        }
    }

    private final HungryCow cow;
    private Phase phase = Phase.NONE;
    private int secondsLeft = 0;      // скільки ще триває фаза
    private int phaseLength = 1;      // скільки фаза тривала від початку (для плавних гормонів)
    private boolean matedThisEstrus = false;
    private int nextMoo = 0;

    public CowCycle(HungryCow cow) {
        this.cow = cow;
    }

    // ---------- стан ----------

    public Phase phase() {
        return phase;
    }

    public boolean inEstrus() {
        return phase == Phase.ESTRUS;
    }

    public boolean inProestrus() {
        return phase == Phase.PROESTRUS;
    }

    public boolean inMetestrus() {
        return phase == Phase.METESTRUS;
    }

    public boolean matedThisEstrus() {
        return matedThisEstrus;
    }

    public void markMated() {
        matedThisEstrus = true;
    }

    // 0 на початку фази … 1 в кінці
    public double progress() {
        return 1.0 - secondsLeft / (double) Math.max(1, phaseLength);
    }

    public int daysLeft() {
        return (int) Math.ceil(secondsLeft * (double) TIME_SPEED / SECONDS_PER_DAY);
    }

    // Чи вже видно вагітність (лише з діеструсу)
    public boolean pregnancyVisible() {
        return cow.repro.isPregnant() && (phase == Phase.DIESTRUS || phase == Phase.PREGNANT || cow.repro.inLabor());
    }

    // ---------- раз на секунду ----------

    public void secondTick() {
        boolean canCycle = cow.repro.isFemale() && !cow.isBaby() && !cow.repro.isExhausted();
        if (!canCycle) {
            if (phase != Phase.PREGNANT || !cow.repro.isPregnant()) phase = Phase.NONE;
            return;
        }
        if (phase == Phase.NONE) {
            // молода корова виросла (або стара з попередньої версії): цикл починається
            start(cow.repro.isPregnant() ? Phase.PREGNANT : Phase.PROESTRUS);
            return;
        }
        if (phase == Phase.ESTRUS && --nextMoo <= 0) {
            cow.playSound(SoundEvents.COW_AMBIENT, 1.2F, 0.9F + cow.getRandom().nextFloat() * 0.3F);
            nextMoo = MOO_MIN_SECONDS + cow.getRandom().nextInt(MOO_MAX_SECONDS - MOO_MIN_SECONDS + 1);
        }
        if (phase == Phase.PREGNANT) return;   // чекає пологів
        if (--secondsLeft > 0) return;
        switch (phase) {
            case PROESTRUS -> {
                start(Phase.ESTRUS);
                cow.tell(cow.family.name + ": тічка!");
            }
            case ESTRUS -> start(Phase.METESTRUS);
            case METESTRUS -> start(Phase.DIESTRUS);
            case DIESTRUS -> {
                if (cow.repro.isPregnant()) {
                    start(Phase.PREGNANT);
                } else {
                    start(Phase.PROESTRUS);
                }
            }
            case POSTPARTUM -> start(Phase.PROESTRUS);
            default -> {
            }
        }
    }

    // Після пологів: пауза
    public void onBirth() {
        if (cow.repro.isExhausted()) {
            phase = Phase.NONE;
        } else {
            start(Phase.POSTPARTUM);
        }
    }

    // Для тестової команди: одразу видно вагітність
    public void forcePregnant() {
        phase = Phase.PREGNANT;
        secondsLeft = 0;
    }

    private void start(Phase p) {
        phase = p;
        double days = switch (p) {
            case PROESTRUS -> randomDays(PROESTRUS_MIN_DAYS, PROESTRUS_MAX_DAYS);
            case ESTRUS -> ESTRUS_DAYS;
            case METESTRUS -> randomDays(METESTRUS_MIN_DAYS, METESTRUS_MAX_DAYS);
            case DIESTRUS -> randomDays(DIESTRUS_MIN_DAYS, DIESTRUS_MAX_DAYS);
            case POSTPARTUM -> POSTPARTUM_DAYS;
            default -> 0;
        };
        secondsLeft = Math.max(1, (int) Math.round(days * SECONDS_PER_DAY / TIME_SPEED));
        phaseLength = secondsLeft;
        if (p == Phase.ESTRUS) {
            matedThisEstrus = false;
            nextMoo = 0;
        }
    }

    private double randomDays(int min, int max) {
        return min + cow.getRandom().nextDouble() * (max - min);
    }

    // ---------- гормони за фазою (для CowChemistry) ----------

    public double estrogenTarget() {
        return switch (phase) {
            case PROESTRUS -> 40 + 40 * progress();   // росте до тічки
            case ESTRUS -> 100;
            case METESTRUS -> 60 - 40 * progress();   // спадає
            case DIESTRUS, PREGNANT -> 15;
            case POSTPARTUM -> 10;
            default -> -1;   // -1 = без циклу, звичайний рівень
        };
    }

    public double progesteroneTarget() {
        return switch (phase) {
            case DIESTRUS -> 60;
            case PREGNANT -> 80;
            default -> -1;
        };
    }

    // ---------- збереження ----------

    public void save(CompoundTag tag) {
        CompoundTag c = new CompoundTag();
        c.putString("Phase", phase.name());
        c.putInt("SecondsLeft", secondsLeft);
        c.putInt("PhaseLength", phaseLength);
        c.putBoolean("Mated", matedThisEstrus);
        tag.put("Cycle", c);
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("Cycle")) return;
        CompoundTag c = tag.getCompound("Cycle");
        try {
            phase = Phase.valueOf(c.getString("Phase"));
        } catch (IllegalArgumentException e) {
            phase = Phase.NONE;
        }
        secondsLeft = c.getInt("SecondsLeft");
        phaseLength = Math.max(1, c.getInt("PhaseLength"));
        matedThisEstrus = c.getBoolean("Mated");
    }
}
