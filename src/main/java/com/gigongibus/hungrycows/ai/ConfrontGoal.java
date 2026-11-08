package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowCharacter.Trait;
import com.gigongibus.hungrycows.CowTrust;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Протистояння з гравцем (ідея Fij): корова чи бик «боднуть» гравця, а не лише тікають. Приводи:
 *  1. гравець біжить на корову — корова 30%, бик 35%;
 *  2. корова з телям поруч — невідомий гравець (довіра ≤ 0) підійшов: 85%; відомий (1–30): 30%; далі менше, до 5% при довірі 100;
 *  3. невідомий гравець загнав у кут (тікати нікуди) — 60%;
 *  6. ватажок стада відганяє невідомого: іде на нього; не відійшов за 3 с — 30%;
 *  8. захищає подругу: гравець біля пораненої подруги або гравець бив корів (довіра < 0), а подруга про це не знає — 50%.
 * Шанси більшає: 4. поганий досвід (довіра < 0: +20%; агресивність ±15%); 5. тічка +10% (вагітна — ні).
 * 7. Бик перед нападом попереджає: опускає голову, не їсть, іде трохи боком (2,5 с); гравець не відійшов на 8 блоків —
 *    дуже швидко біжить і б'є: підкидає на ~4 блоки або протягує по землі (50/50), потім невидиме сповільнення 5 с.
 * Кожен привід кидає шанс раз на гравця, потім 30 с перерви. Друг (довіра 50+) — лише приводи 1 і 2 (з малим шансом).
 * Усі числа зібрані на початку файлу.
 */
public class ConfrontGoal extends Goal {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double RANGE = 8;                 // кого помічає
    public static final double RUN_RANGE = 6;             // 1. біжить на корову ближче 6 блоків ...
    public static final double RUN_CLOSER = 1.0;          // ... і за пів секунди наблизився хоча б на 1 блок
    public static final double RUN_COW = 0.30, RUN_BULL = 0.35;
    public static final double CALF_RANGE = 10;           // 2. теля ближче 10 блоків від матері ...
    public static final double CALF_APPROACH = 6;         // ... гравець ближче 6
    public static final double CALF_STRANGER = 0.85, CALF_KNOWN = 0.30, CALF_FRIEND = 0.05;
    public static final int KNOWN_MAX = 30;               // «відомий» = довіра 1–30 (Fij)
    public static final int FRIEND_FULL = 100;            // від 30 до 100 шанс спадає до CALF_FRIEND
    public static final double CORNER_RANGE = 3;          // 3. загнав у кут: невідомий ближче 3 блоків і тікати нікуди
    public static final double CORNERED = 0.60;
    public static final double BAD_TRUST = 0.20;          // 4. довіра < 0: +20%
    public static final double AGGRESSION = 0.30;         //    агресивність 100 → +15%, 0 → −15%
    public static final double ESTRUS = 0.10;             // 5. тічка +10%
    public static final double LEADER_RANGE = 8;          // 6. ватажок: невідомий ближче 8 блоків до неї ...
    public static final int LEADER_WARN_TICKS = 60;       //    ... 3 с наступає ...
    public static final double LEADER_STAY = 4;           //    ... гравець досі ближче 4 — ...
    public static final double LEADER_HIT = 0.30;         //    ... 30% б'є
    public static final double FRIEND_GUARD = 4;          // 8. гравець ближче 4 блоків до подруги ...
    public static final double FRIEND_HURT = 0.5;         //    ... подруга поранена (нижче 50% здоров'я)
    public static final double FRIEND_CHANCE = 0.50;
    public static final int RETRY_TICKS = 600;            // той самий гравець — наступний кидок шансу через 30 с
    public static final int CHECK_EVERY = 10;             // приводи перевіряє раз на пів секунди
    public static final int ATTACK_TICKS = 120;           // корова б'є щонайдовше 6 с
    public static final double ATTACK_SPEED = 1.4;
    // 7. бик
    public static final int BULL_WARN_TICKS = 50;         // 2,5 с попередження
    public static final float HEAD_DOWN = 40F;            // голова донизу (градуси)
    public static final float SIDESTEP = 0.35F;           // іде трохи боком
    public static final double BULL_BACK_OFF = 8;         // гравець відійшов на 8 блоків — бик заспокоюється
    public static final double CHARGE_SPEED = 2.4;        // дуже швидко біжить
    public static final int CHARGE_TICKS = 80;            // щонайдовше 4 с розгону
    public static final double TOSS_UP = 0.85;            // підкидає на ~4 блоки
    public static final double DRAG = 2.2;                // або протягує по землі далеко
    public static final int SLOW_TICKS = 100;             // невидиме сповільнення 5 с
    public static final double REACH = 1.0;               // б'є, коли між ними менше 1 блока

    private enum Phase { NONE, LEADER_WARN, ATTACK, BULL_WARN, CHARGE }

    private final HungryCow cow;
    private final Map<UUID, Integer> nextRoll = new HashMap<>();   // гравець → коли знову кидати шанс
    private final Map<UUID, Double> lastDist = new HashMap<>();    // гравець → відстань минулої перевірки
    private int cooldown = 0;
    private Phase phase = Phase.NONE;
    private Player target;
    private int phaseTicks;
    private int nextRam;
    private float sideDir = 1;

    public ConfrontGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    // ---------- приводи ----------

    private boolean stranger(Player p) {
        return cow.trustOf(p) <= 0;
    }

    // поганий досвід і характер, тічка
    private double modify(Player p, double chance) {
        if (cow.trustOf(p) < 0) chance += BAD_TRUST;
        chance += AGGRESSION * (cow.character.fraction(Trait.AGGRESSION) - 0.5);
        if (cow.repro.isFemale() && cow.cycle.inEstrus() && !cow.repro.isPregnant()) chance += ESTRUS;
        return Math.max(0, Math.min(0.97, chance));
    }

    // біжить (спринт) і за пів секунди наблизився (сервер не знає швидкості справжнього гравця — міряємо відстань)
    private boolean runningAtMe(Player p, double dist, double before) {
        return p.isSprinting() && dist < RUN_RANGE && before - dist > RUN_CLOSER;
    }

    private boolean hasCalfNearby() {
        return !cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(CALF_RANGE),
                c -> c.isBaby() && c.isAlive() && cow.isMotherOf(c)).isEmpty();
    }

    private double calfChance(Player p) {
        return calfChanceForTrust(cow.trustOf(p));
    }

    private boolean cornered(Player p) {
        if (!stranger(p) || cow.distanceToSqr(p) > CORNER_RANGE * CORNER_RANGE) return false;
        return DefaultRandomPos.getPosAway(cow, 6, 3, p.position()) == null;
    }

    private boolean guardsFriend(Player p) {
        boolean hitCows = cow.trustOf(p) < 0;
        return !cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(RANGE),
                f -> f != cow && f.isAlive() && f.distanceToSqr(p) < FRIEND_GUARD * FRIEND_GUARD
                        && (f.getHealth() < f.getMaxHealth() * FRIEND_HURT || f.disease.sick()
                        || (hitCows && f.trustOf(p) >= 0))).isEmpty();
    }

    // Один кидок шансу на гравця (і 30 с перерви)
    private boolean roll(Player p, double chance) {
        nextRoll.put(p.getUUID(), cow.tickCount + RETRY_TICKS);
        return cow.getRandom().nextDouble() < modify(p, chance);
    }

    @Override
    public boolean canUse() {
        if (phase != Phase.NONE && target != null) return true;   // почали ззовні (force)
        if (cooldown > 0) { cooldown--; return false; }
        cooldown = CHECK_EVERY / 2;   // canUse — раз на 2 тіки
        if (cow.isBaby() || cow.sleeping || cow.heat.unconscious || cow.getHealth() < cow.getMaxHealth() / 3) return false;
        for (Player p : cow.level().getEntitiesOfClass(Player.class, cow.getBoundingBox().inflate(RANGE),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && cow.vision.canSee(p))) {   // лише того, кого бачить
            double dist = Math.sqrt(cow.distanceToSqr(p));
            Double before = lastDist.put(p.getUUID(), dist);
            if (cow.tickCount < nextRoll.getOrDefault(p.getUUID(), 0)) continue;
            boolean friend = cow.trustOf(p) >= CowTrust.FRIEND_FROM;
            boolean attack = false;
            if (before != null && runningAtMe(p, dist, before)) {
                attack = roll(p, cow.isBull() ? RUN_BULL : RUN_COW);
            } else if (cow.repro.isFemale() && cow.distanceToSqr(p) < CALF_APPROACH * CALF_APPROACH && hasCalfNearby()) {
                attack = roll(p, calfChance(p));
            } else if (!friend && cornered(p)) {
                attack = roll(p, CORNERED);
            } else if (!friend && guardsFriend(p)) {
                attack = roll(p, FRIEND_CHANCE);
            } else if (!friend && stranger(p) && cow.herd.isLeader() && cow.herd.size() > 1) {
                nextRoll.put(p.getUUID(), cow.tickCount + RETRY_TICKS);
                start(p, Phase.LEADER_WARN);
                return true;
            }
            if (attack) {
                start(p, cow.isBull() ? Phase.BULL_WARN : Phase.ATTACK);
                return true;
            }
        }
        return false;
    }

    // Для тестів і команд: почати одразу (бик — з попередженням)
    public void force(Player p) {
        start(p, cow.isBull() ? Phase.BULL_WARN : Phase.ATTACK);
    }

    public String phaseName() {
        return phase.name();
    }

    public boolean busy() {
        return phase != Phase.NONE;
    }

    public static double calfChanceForTrust(int t) {
        if (t <= 0) return CALF_STRANGER;
        if (t <= KNOWN_MAX) return CALF_KNOWN;
        double k = Math.min(1, (t - KNOWN_MAX) / (double) (FRIEND_FULL - KNOWN_MAX));
        return CALF_KNOWN + (CALF_FRIEND - CALF_KNOWN) * k;
    }

    private void start(Player p, Phase ph) {
        target = p;
        phase = ph;
        phaseTicks = 0;
        sideDir = cow.getRandom().nextBoolean() ? 1 : -1;
        String what = switch (ph) {
            case LEADER_WARN -> " (ватажок) відганяє ";
            case BULL_WARN -> " (бик) опустив голову — готується напасти на ";
            default -> " бодає ";
        };
        cow.tell(cow.family.name + what + p.getName().getString());
    }

    @Override
    public boolean canContinueToUse() {
        return phase != Phase.NONE && target != null && target.isAlive() && !target.isCreative() && !target.isSpectator();
    }

    @Override
    public void tick() {
        if (target == null) return;
        phaseTicks++;
        double dist = Math.sqrt(cow.distanceToSqr(target));
        switch (phase) {
            case LEADER_WARN -> {
                cow.getLookControl().setLookAt(target, 30F, 30F);
                if (phaseTicks % 10 == 1) cow.getNavigation().moveTo(target, 1.0);
                if (phaseTicks == 20) cow.playSound(SoundEvents.COW_AMBIENT, 1.2F, 0.7F);
                if (phaseTicks >= LEADER_WARN_TICKS) {
                    if (dist < LEADER_STAY && cow.getRandom().nextDouble() < modify(target, LEADER_HIT)) {
                        phase = cow.isBull() ? Phase.BULL_WARN : Phase.ATTACK;
                        phaseTicks = 0;
                    } else {
                        phase = Phase.NONE;
                    }
                }
            }
            case ATTACK -> {
                cow.getLookControl().setLookAt(target, 30F, 30F);
                if (phaseTicks % 5 == 1 || cow.getNavigation().isDone()) cow.getNavigation().moveTo(target, ATTACK_SPEED);
                if (gap() <= REACH && cow.tickCount >= nextRam) {
                    cow.ram(target);
                    nextRam = cow.tickCount + CowTrust.ATTACK_COOLDOWN_TICKS;
                    phase = Phase.NONE;   // боднула раз — досить (наступний привід — через 30 с)
                }
                if (phaseTicks >= ATTACK_TICKS || dist > RANGE * 1.5) phase = Phase.NONE;
            }
            case BULL_WARN -> {
                // опущена голова, не їсть, трохи боком
                cow.getNavigation().stop();
                cow.headDown = true;
                cow.getLookControl().setLookAt(target, 30F, 0F);
                cow.setXRot(HEAD_DOWN);
                cow.getMoveControl().strafe(0F, SIDESTEP * sideDir);
                if (phaseTicks % 20 == 0) cow.playSound(SoundEvents.COW_AMBIENT, 1.0F, 0.5F);
                if (phaseTicks >= BULL_WARN_TICKS) {
                    cow.headDown = false;
                    phaseTicks = 0;
                    phase = dist < BULL_BACK_OFF ? Phase.CHARGE : Phase.NONE;   // відійшов — заспокоївся
                    if (phase == Phase.CHARGE) cow.tell(cow.family.name + " (бик) кидається на " + target.getName().getString() + "!");
                }
            }
            case CHARGE -> {
                cow.getLookControl().setLookAt(target, 30F, 30F);
                if (phaseTicks % 4 == 1) cow.getNavigation().moveTo(target, CHARGE_SPEED);
                if (gap() <= REACH) {
                    hitHard();
                    phase = Phase.NONE;
                } else if (phaseTicks >= CHARGE_TICKS) {
                    phase = Phase.NONE;
                }
            }
            default -> { }
        }
    }

    private double gap() {
        return Math.sqrt(cow.distanceToSqr(target)) - (cow.getBbWidth() + target.getBbWidth()) / 2.0;
    }

    // 7. удар бика з розгону: підкидає на ~4 блоки або протягує по землі; невидиме сповільнення
    private void hitHard() {
        cow.ram(target);
        Vec3 dir = target.position().subtract(cow.position()).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : dir.normalize();
        if (cow.getRandom().nextBoolean()) {
            target.setDeltaMovement(dir.x * 0.3, TOSS_UP, dir.z * 0.3);
        } else {
            target.setDeltaMovement(dir.x * DRAG, 0.15, dir.z * DRAG);
        }
        target.hurtMarked = true;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 0, false, false, false));
    }

    @Override
    public void stop() {
        cow.headDown = false;
        phase = Phase.NONE;
        target = null;
        cow.getNavigation().stop();
    }
}
