package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowTrust;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

// Вночі корова йде додому (укриття) і спить: стоїть, відпочиває, гірше помічає дрібниці.
// Якщо дому немає — спить під деревом (будь-яке накриття); якщо й дерева немає — там, де є. Голод, спрага, страх і пологи важливіші (вищий пріоритет).
public class SleepGoal extends Goal {
    private static final double HOME_REACH = 3.0;       // «вдома» = ближче цього (стадо стоїть поруч)
    private static final double SAME_SLEEP_RANGE = 32;  // ватажок ближче цього — стадо спить там, де вона
    private static final int MAX_WALK_TICKS = 1200;     // іде додому щонайбільше 1 хв, потім спить, де є
    private static final int RECOVER_EVERY_TICKS = 20;  // втома −2 щосекунди сну
    private static final int RECOVER = 2;
    // ================= СЛУХ УВІ СНІ =================
    private static final double HEAR_RANGE = 5.0;      // чує, як хтось рухається, до 5 блоків
    private static final double HEAR_SNEAK = 2.5;      // гравця, що крадеться присівши, — лише до 2,5
    private static final double MOVE_HEARD = 0.3;      // «рухається» = за пів секунди зрушив більше ніж на 0,3 блока
    private static final int LISTEN_EVERY = 10;        // прислухається раз на пів секунди
    private static final int WAKE_TICKS = 600;         // прокинулась — пів хвилини не засинає
    private static final int WAKE_FEAR = 40;           // небезпечний моб поруч: такий страх (тікає)
    private final Map<Integer, Vec3> heardPos = new HashMap<>();
    private int awakeUntil = 0;
    private final HungryCow cow;
    private int walkTicks = 0;
    private int ticks = 0;

    public SleepGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        return cow.isNightTime() && !cow.isBaby() && !cow.cycle.inEstrus() && cow.tickCount >= awakeUntil;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        walkTicks = 0;
        ticks = 0;
    }

    @Override
    public void tick() {
        ticks++;
        // стадо спить разом: туди ж, де ватажок (якщо вона поруч і має де спати)
        BlockPos home = cow.home.sleepSpot();
        HungryCow leader = cow.herd.leader();
        if (leader != null && leader != cow && leader.home.sleepSpot() != null
                && cow.distanceToSqr(leader) < SAME_SLEEP_RANGE * SAME_SLEEP_RANGE) {
            home = leader.home.sleepSpot();
        }
        if (home != null && walkTicks < MAX_WALK_TICKS
                && cow.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) > HOME_REACH * HOME_REACH) {
            walkTicks++;
            cow.sleeping = false;
            cow.resting = false;
            if (walkTicks == 1 || walkTicks % 20 == 0) {
                cow.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
            }
            return;
        }
        // спить
        cow.getNavigation().stop();
        cow.sleeping = true;
        cow.resting = true;
        if (ticks % RECOVER_EVERY_TICKS == 0) cow.fatigue = Math.max(0, cow.fatigue - RECOVER);
        if (ticks % LISTEN_EVERY == 0) listen();
    }

    // Слух: хтось рухається поруч — прокидається; небезпечний — ще й тікає
    private void listen() {
        Map<Integer, Vec3> now = new HashMap<>();
        LivingEntity heard = null;
        for (LivingEntity e : cow.level().getEntitiesOfClass(LivingEntity.class,
                cow.getBoundingBox().inflate(HEAR_RANGE), e -> e != cow && e.isAlive() && !(e instanceof HungryCow))) {
            Vec3 pos = e.position();
            now.put(e.getId(), pos);
            Vec3 before = heardPos.get(e.getId());
            if (before == null || before.distanceToSqr(pos) < MOVE_HEARD * MOVE_HEARD) continue;
            double range = e instanceof Player p && p.isCrouching() ? HEAR_SNEAK : HEAR_RANGE;
            if (cow.distanceToSqr(e) > range * range) continue;
            if (e instanceof Player p && p.isSpectator()) continue;
            heard = e;
            break;
        }
        heardPos.clear();
        heardPos.putAll(now);
        if (heard == null) return;
        awakeUntil = cow.tickCount + WAKE_TICKS;
        boolean friend = heard instanceof Player p && cow.trustOf(p) >= CowTrust.FRIEND_FROM;
        if (cow.isScary(heard) && !friend) {
            cow.fear = Math.max(cow.fear, WAKE_FEAR);
            cow.tell(cow.family.name + " прокинулась від шуму і злякалась: " + heard.getName().getString());
        } else {
            cow.tell(cow.family.name + " прокинулась від шуму: " + heard.getName().getString());
        }
    }

    @Override
    public void stop() {
        cow.sleeping = false;
        cow.resting = false;
        heardPos.clear();
    }
}
