package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowCharacter.Experience;
import com.gigongibus.hungrycows.CowGenes;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Бійки биків (ідея Fij 13). Два дорослі бики ближче 10 блоків: раз на хвилину шанс 20% (50%, якщо поруч самка в тічці),
 * що зчепляться (раз на 2 хв: 10%, при самці в тічці 30%). Б'ються лобами 5–15 с, кожен своєю силою (ген «сила удару» × розмір).
 * Звичайна бійка — до 40% здоров'я; 5% бійок «серйозні» — до смерті одного (щонайдовше 1 хв). Кінець бійки: переможець — у кого більша частка здоров'я; переможений 30 с тікає від
 * нього. Досвід: переможець домінантніший, переможений — менш. Головного бика стада видно в книзі (сторінка «Стадо»).
 * Усі числа зібрані на початку файлу.
 */
public class BullFightGoal extends Goal {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double FIGHT_RANGE = 10;          // суперник ближче 10 блоків
    public static final int CHECK_EVERY = 2400;           // раз на 2 хв кидає шанс (Fij: «не так часто»)
    public static final double CHANCE = 0.1;              // 10% ...
    public static final double CHANCE_ESTRUS = 0.3;       // ... 30%, коли поруч самка в тічці
    public static final double DEADLY_CHANCE = 0.05;      // 5% бійок «серйозні» — до смерті (Fij, варіант Б)
    public static final int DEADLY_MAX_TICKS = 1200;      // серйозна бійка — щонайдовше 1 хв
    public static final double ESTRUS_RANGE = 16;         // «поруч» для самки
    public static final int MIN_TICKS = 100, MAX_TICKS = 300;   // бійка триває 5–15 с
    public static final double STOP_HEALTH = 0.4;         // нижче 40% здоров'я — програв (до смерті не б'ються)
    public static final double START_HEALTH = 0.6;        // поранений (нижче 60%) у бійку не лізе
    public static final float BUTT_DAMAGE = 1.5F;         // удар лобом × ген «сила удару» × розмір
    public static final float BUTT_KNOCKBACK = 0.5F;
    public static final double BUTT_REACH = 0.8;          // б'є, коли між ними менше 0,8 бл. (від краю до краю)
    public static final int BUTT_PAUSE_MIN = 25, BUTT_PAUSE_MAX = 40;   // пауза між ударами 1,25–2 с
    public static final double SPEED = 1.2;
    public static final int RETREAT_TICKS = 600;          // переможений тікає 30 с
    public static final double RETREAT_SPEED = 1.3;

    private final HungryCow cow;
    private int cooldown;
    private int nextButt = 0;

    public BullFightGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        this.cooldown = cow.getRandom().nextInt(CHECK_EVERY / 2);   // не всі бики кидають шанс одночасно
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private static boolean able(HungryCow c) {
        return c.isAlive() && c.isBull() && !c.sleeping && !c.heat.unconscious && c.fightRival == null
                && c.getHealth() >= c.getMaxHealth() * START_HEALTH && !c.retreating();
    }

    private boolean fighting() {
        return cow.fightRival != null && cow.fightRival.isAlive() && cow.tickCount < cow.fightUntil;
    }

    @Override
    public boolean canUse() {
        if (fighting() || cow.retreating()) return true;   // суперник почав бійку / тікає після поразки
        if (cooldown > 0) { cooldown--; return false; }
        cooldown = CHECK_EVERY / 2;                         // canUse — раз на 2 тіки
        if (!able(cow)) return false;
        HungryCow rival = null;
        double best = FIGHT_RANGE * FIGHT_RANGE;
        for (HungryCow o : cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(FIGHT_RANGE),
                o -> o != cow && able(o))) {
            double d = cow.distanceToSqr(o);
            if (d < best) { best = d; rival = o; }
        }
        if (rival == null) return false;
        boolean estrus = !cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(ESTRUS_RANGE),
                o -> o.isAlive() && o.repro.isFemale() && o.cycle.inEstrus()).isEmpty();
        if (cow.getRandom().nextDouble() >= (estrus ? CHANCE_ESTRUS : CHANCE)) return false;
        startFight(cow, rival);
        return true;
    }

    // Почати бійку двох биків (обидва отримують одне одного суперником і однаковий кінець)
    public static void startFight(HungryCow a, HungryCow b) {
        int until = a.tickCount + MIN_TICKS + a.getRandom().nextInt(MAX_TICKS - MIN_TICKS + 1);
        boolean deadly = a.getRandom().nextDouble() < DEADLY_CHANCE;
        if (deadly) until = a.tickCount + DEADLY_MAX_TICKS;
        a.fightRival = b;
        b.fightRival = a;
        a.fightDeadly = deadly;
        b.fightDeadly = deadly;
        a.fightUntil = until;
        b.fightUntil = b.tickCount + (until - a.tickCount);
        a.tell(a.family.name + " і " + b.family.name + " (бики) б'ються" + (deadly ? " НА СМЕРТЬ" : ""));
    }

    @Override
    public boolean canContinueToUse() {
        if (cow.retreating()) return true;
        HungryCow r = cow.fightRival;
        if (r == null || !r.isAlive() || cow.tickCount >= cow.fightUntil) return false;
        if (cow.fightDeadly) return true;   // серйозна — доки один не загине (або хвилина)
        return cow.getHealth() > cow.getMaxHealth() * STOP_HEALTH && r.getHealth() > r.getMaxHealth() * STOP_HEALTH;
    }

    @Override
    public void tick() {
        // переможений: тікає від переможця
        if (cow.retreating()) {
            if (cow.retreatFrom == null) return;
            if (cow.tickCount % 10 == 0 || cow.getNavigation().isDone()) {
                Vec3 away = DefaultRandomPos.getPosAway(cow, 12, 4, cow.retreatFrom.position());
                if (away != null) cow.getNavigation().moveTo(away.x, away.y, away.z, RETREAT_SPEED);
            }
            return;
        }
        HungryCow r = cow.fightRival;
        if (r == null) return;
        cow.getLookControl().setLookAt(r, 30F, 30F);
        if (cow.tickCount % 5 == 0 || cow.getNavigation().isDone()) cow.getNavigation().moveTo(r, SPEED);
        double gap = Math.sqrt(cow.distanceToSqr(r)) - (cow.getBbWidth() + r.getBbWidth()) / 2.0;
        if (gap <= BUTT_REACH && cow.tickCount >= nextButt) {
            float damage = (float) (BUTT_DAMAGE * cow.gene(CowGenes.Gene.KICK_STRENGTH) * cow.sizeScale());
            if (r.hurt(cow.damageSources().mobAttack(cow), damage)) {
                r.knockback(BUTT_KNOCKBACK, cow.getX() - r.getX(), cow.getZ() - r.getZ());
                cow.playSound(SoundEvents.COW_HURT, 1.0F, 0.5F);
            }
            nextButt = cow.tickCount + BUTT_PAUSE_MIN + cow.getRandom().nextInt(BUTT_PAUSE_MAX - BUTT_PAUSE_MIN + 1);
        }
    }

    @Override
    public void stop() {
        HungryCow r = cow.fightRival;
        if (r != null && !cow.retreating()) finish(cow, r);
        cow.getNavigation().stop();
    }

    // Кінець бійки (рахує той, хто закінчив першим; другий уже без суперника)
    private static void finish(HungryCow a, HungryCow b) {
        a.fightRival = null;
        if (b.fightRival == a) b.fightRival = null;
        if (!a.isAlive() || !b.isAlive()) return;
        double fa = a.getHealth() / a.getMaxHealth(), fb = b.getHealth() / b.getMaxHealth();
        HungryCow winner = fa >= fb ? a : b, loser = winner == a ? b : a;
        loser.retreatFrom = winner;
        loser.retreatUntil = loser.tickCount + RETREAT_TICKS;
        winner.experience(Experience.BULL_FIGHT_WON, 0);
        loser.experience(Experience.BULL_FIGHT_LOST, 0);
        winner.tell(winner.family.name + " переміг у бійці, " + loser.family.name + " тікає");
    }
}
