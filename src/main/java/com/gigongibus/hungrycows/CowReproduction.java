package com.gigongibus.hungrycows;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

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

    private final HungryCow cow;
    private int sex;                       // 1 = жіноча, 2 = чоловіча
    private boolean pregnant = false;
    private int pregnantSeconds = 0;
    private int laborTicksLeft = 0;

    // зустріч пари (не зберігається)
    private int matingSince = 0;
    private int matingLastCall = -100000;

    public CowReproduction(HungryCow cow) {
        this.cow = cow;
        this.sex = cow.getRandom().nextBoolean() ? 1 : 2;
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

    // Можуть розмножуватися: різні статі, ніхто не вагітний
    public boolean canMateWith(HungryCow other) {
        return isFemale() != other.repro.isFemale() && !pregnant && !other.repro.pregnant;
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
        if (now - m.matingSince < MATING_TICKS) return;   // ще не 15 секунд разом

        m.conceive(father);
    }

    private void conceive(HungryCow father) {
        pregnant = true;
        pregnantSeconds = PREGNANCY_SECONDS;
        cow.resetLove();
        father.resetLove();
        cow.setAge(BREED_COOLDOWN_TICKS);
        father.setAge(BREED_COOLDOWN_TICKS);
        cow.level().broadcastEntityEvent(cow, (byte) 18);   // сердечка
        say("Корова завагітніла!");
    }

    // ---------- вагітність і пологи ----------

    // Раз на секунду (викликається з aiStep корови)
    public void secondTick() {
        if (pregnant && laborTicksLeft <= 0) {
            if (pregnantSeconds > 0) pregnantSeconds--;
            if (pregnantSeconds <= 0) {
                laborTicksLeft = LABOR_TICKS;
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
            float pain = LABOR_DAMAGE / (LABOR_TICKS / 20.0F);
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

        if (cow.getRandom().nextFloat() < CALF_DEATH_CHANCE) {
            cow.playSound(SoundEvents.COW_DEATH, 0.8F, 1.3F);
            say("Теля народилося мертвим...");
            return;
        }
        HungryCow baby = ModEntities.HUNGRY_COW.get().create(cow.level());
        if (baby != null) {
            baby.setBaby(true);
            baby.moveTo(cow.getX(), cow.getY(), cow.getZ(), cow.getYRot(), 0.0F);
            cow.level().addFreshEntity(baby);
            cow.level().broadcastEntityEvent(cow, (byte) 18);
            say("Народилося теля!");
        }
    }

    // ---------- напис над коровою ----------

    public String decorate(String name) {
        String result = "[" + (isFemale() ? "Ж" : "Ч") + "] " + name;
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
    }

    public void load(CompoundTag tag) {
        if (tag.contains("Sex")) {
            sex = tag.getInt("Sex");
        }
        pregnant = tag.getBoolean("Pregnant");
        pregnantSeconds = tag.getInt("PregnantSeconds");
        laborTicksLeft = tag.getInt("LaborTicks");
    }
}
