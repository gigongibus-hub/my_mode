package com.gigongibus.hungrycows.life;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ШАР ПРОСТОЮ (Fij, «жвавість»): дрібні рухи, коли тварина нічого термінового не робить, — переступає з ноги на ногу,
 * робить крок-два вбік, озирається, коли вагається між двома справами, жує жуйку; під час неспішної ходи інколи
 * коротко зупиняється. Дешево: вирішує раз на 5 тіків, діє лише на рух тіла (керування рухом) і голову.
 * Нічого не перебиває: що робити можна, каже тварина через {@link Host}. Клас не знає, яка це тварина.
 */
public final class IdleLife {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int THINK_EVERY = 5;              // вирішує раз на 5 тіків
    public static final double SHIFT_CHANCE = 0.03;       // переступити (0,3–0,6 бл.) — 3% за раз, коли стоїть (~раз на 8 с)
    public static final double SHIFT_MIN = 0.3, SHIFT_MAX = 0.6;
    public static final double SHIFT_SPEED = 0.35;        // повільно
    public static final double SIDESTEP_CHANCE = 0.012;   // крок-два вбік — ~раз на 20 с
    public static final double SIDESTEP_MIN = 1.0, SIDESTEP_MAX = 2.0;
    public static final double SIDESTEP_SPEED = 0.5;
    public static final int STEP_MAX_TICKS = 50;          // не дійшла за 2,5 с — досить
    public static final double STEP_REACH = 0.2;
    public static final double LOOK_CHANCE = 0.04;        // озирнутися (без вагання — рідко) ...
    public static final double HESITATE_LOOK_CHANCE = 0.2; // ... вагається — часто (~раз на 1,5 с)
    public static final int LOOK_MIN = 20, LOOK_MAX = 45; // дивиться 1–2,25 с
    public static final float LOOK_SPREAD = 120F;         // кудись у межах ±120° від тулуба
    public static final double CHEW_CHANCE = 0.02;        // поїла недавно й стоїть — пожувати (~раз на 12 с)
    public static final int CHEW_MIN = 60, CHEW_MAX = 120;
    public static final double PAUSE_CHANCE = 0.012;      // неспішно йде — коротка зупинка (~раз на 20 с) ...
    public static final double HESITATE_PAUSE_CHANCE = 0.05; // ... вагається — частіше
    public static final int PAUSE_MIN = 15, PAUSE_MAX = 35; // 0,75–1,75 с (довше — навігація вважає, що застрягла)

    /** Що тварина дозволяє шару простою */
    public interface Host {
        /** Стоїть без термінової справи (немає страху, болю, пологів, шляху тощо) */
        boolean idleAllowed();
        /** Іде без поспіху (гуляє, до стада) — можна коротко зупинитися */
        boolean pauseAllowed();
        /** Голову не веде нічого важливішого (небезпека, теля, друг) */
        boolean lookAllowed();
        /** Мозок вагається між двома справами */
        boolean hesitating();
        /** Поїла недавно — можна жувати */
        boolean canChew();
        void chew(int ticks);
        /** Для замірів: зроблено мікро-дію */
        void onMicro(String kind);
    }

    private final PathfinderMob mob;
    private final Host host;
    private Vec3 stepTo;
    private double stepSpeed;
    private int stepTicks;
    private int lookUntil;
    private float lookYaw;
    private int pauseUntil;

    public IdleLife(PathfinderMob mob, Host host) {
        this.mob = mob;
        this.host = host;
    }

    /** Зараз коротка зупинка (керування рухом тримає швидкість 0) */
    public boolean paused() {
        return mob.tickCount < pauseUntil;
    }

    /** Зараз робить мікро-крок (для інших цілей: не заважати) */
    public boolean stepping() {
        return stepTo != null;
    }

    public void tick() {
        if (stepTo != null) continueStep();
        if (mob.tickCount < lookUntil) {
            if (host.lookAllowed()) {
                Vec3 eye = mob.getEyePosition();
                double r = Math.toRadians(lookYaw);
                mob.getLookControl().setLookAt(eye.x - Math.sin(r) * 4, eye.y - 0.3, eye.z + Math.cos(r) * 4, 10F, 30F);
            } else lookUntil = 0;
        }
        if (paused() && !host.pauseAllowed()) pauseUntil = 0;   // з'явилась справжня справа — не стояти
        if ((mob.tickCount + mob.getId()) % THINK_EVERY != 0) return;
        RandomSource rnd = mob.getRandom();
        boolean hes = host.hesitating();
        // голова: озирнутися (при ваганні — частіше)
        if (mob.tickCount >= lookUntil && host.lookAllowed() && rnd.nextDouble() < (hes ? HESITATE_LOOK_CHANCE : LOOK_CHANCE)) {
            lookYaw = mob.yBodyRot + (rnd.nextFloat() * 2 - 1) * LOOK_SPREAD;
            lookUntil = mob.tickCount + LOOK_MIN + rnd.nextInt(LOOK_MAX - LOOK_MIN + 1);
            host.onMicro(hes ? "озирається (вагається)" : "озирається");
        }
        // коротка зупинка посеред неспішної ходи
        if (!paused() && host.pauseAllowed() && !mob.getNavigation().isDone()
                && rnd.nextDouble() < (hes ? HESITATE_PAUSE_CHANCE : PAUSE_CHANCE)) {
            pauseUntil = mob.tickCount + PAUSE_MIN + rnd.nextInt(PAUSE_MAX - PAUSE_MIN + 1);
            host.onMicro("зупинилась");
            return;
        }
        if (stepTo != null || !host.idleAllowed()) return;
        double roll = rnd.nextDouble();
        if (roll < SHIFT_CHANCE) {
            startStep(SHIFT_MIN + rnd.nextDouble() * (SHIFT_MAX - SHIFT_MIN), rnd.nextFloat() * 360F, SHIFT_SPEED, "переступає");
        } else if (roll < SHIFT_CHANCE + SIDESTEP_CHANCE) {
            float side = mob.yBodyRot + (rnd.nextBoolean() ? 90F : -90F) + (rnd.nextFloat() - 0.5F) * 40F;
            startStep(SIDESTEP_MIN + rnd.nextDouble() * (SIDESTEP_MAX - SIDESTEP_MIN), side, SIDESTEP_SPEED, "крок убік");
        } else if (roll < SHIFT_CHANCE + SIDESTEP_CHANCE + CHEW_CHANCE && host.canChew()) {
            host.chew(CHEW_MIN + rnd.nextInt(CHEW_MAX - CHEW_MIN + 1));
            host.onMicro("жує");
        }
    }

    private void startStep(double dist, float yaw, double speed, String kind) {
        double r = Math.toRadians(yaw);
        Vec3 to = mob.position().add(-Math.sin(r) * dist, 0, Math.cos(r) * dist);
        if (!standable(to)) return;
        stepTo = to;
        stepSpeed = speed;
        stepTicks = 0;
        host.onMicro(kind);
    }

    // туди можна стати: та сама висота, не впирається, під ногами тверде (та сама перевірка, що в пошуку шляху гри)
    private boolean standable(Vec3 to) {
        BlockPos p = BlockPos.containing(to.x, mob.getY() + 0.1, to.z);
        if (WalkNodeEvaluator.getBlockPathTypeStatic(mob.level(), p.mutable()) != BlockPathTypes.WALKABLE) return false;
        AABB box = mob.getBoundingBox().move(to.x - mob.getX(), 0, to.z - mob.getZ());
        return mob.level().noCollision(mob, box);
    }

    private void continueStep() {
        stepTicks++;
        double dx = stepTo.x - mob.getX(), dz = stepTo.z - mob.getZ();
        if (!host.idleAllowed() || stepTicks > STEP_MAX_TICKS || dx * dx + dz * dz < STEP_REACH * STEP_REACH) {
            stepTo = null;
            return;
        }
        mob.getMoveControl().setWantedPosition(stepTo.x, mob.getY(), stepTo.z, stepSpeed);
    }
}
