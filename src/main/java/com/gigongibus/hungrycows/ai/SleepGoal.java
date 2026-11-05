package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowHome;
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
    private static final double HOME_REACH = 1.0;       // «на своєму місці» = ближче цього
    private static final int SPOT_RADIUS = 5;           // своє місце шукає до 5 блоків від спільного
    private static final double CLAIM_RANGE = 16;       // чиї зайняті місця враховує
    private static final int MAX_SPOT_CHECKS = 6;       // скільки найкращих місць перевіряє, чи можна дійти
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
    private int seekTicks = 0;
    private int failedPaths = 0;
    private BlockPos claimed = null;      // своє місце цієї ночі
    private BlockPos claimBase = null;
    private double lastDist = Double.MAX_VALUE;
    private final java.util.Set<BlockPos> excluded = new java.util.HashSet<>();
    private static final int SEEK_TICKS = 600;          // без місця для сну шукає щонайбільше 30 с
    private static final double STAY_NEAR_LEADER = 6;   // під час пошуку тримається не далі 6 блоків від ватажка
    private static final int MAX_FAILED_PATHS = 5;      // стільки разів не знайшла шляху — спить, де є
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
        seekTicks = 0;
        failedPaths = 0;
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
        // ще не знає, де спати: до пів хвилини активно шукає (будівлю, потім дерево), тримаючись стада
        if (home == null && seekTicks < SEEK_TICKS) {
            seekTicks++;
            cow.sleeping = false;
            cow.resting = false;
            if (seekTicks % 20 == 1) {
                cow.home.searchHard();
                HungryCow l = cow.herd.leader();
                if (l != null && l != cow && cow.distanceToSqr(l) > STAY_NEAR_LEADER * STAY_NEAR_LEADER) {
                    cow.getNavigation().moveTo(l, 1.0);
                }
            }
            if (seekTicks == SEEK_TICKS) cow.tell(cow.family.name + " не знайшла, де спати, — спить тут");
            return;
        }
        // своє місце: усередині укриття, глибше, не зайняте іншою коровою, досяжне
        if (home != null && (claimed == null || !home.equals(claimBase))) {
            claimBase = home;
            excluded.clear();
            claimed = chooseSpot(home);
            walkTicks = 0;
            failedPaths = 0;
            lastDist = Double.MAX_VALUE;
        }
        if (claimed != null && walkTicks < MAX_WALK_TICKS && !atSpot(claimed)) {
            walkTicks++;
            cow.sleeping = false;
            cow.resting = false;
            cow.sleepClaim = claimed;
            if (walkTicks == 1 || walkTicks % 20 == 0) {
                boolean ok = cow.getNavigation().moveTo(claimed.getX() + 0.5, claimed.getY(), claimed.getZ() + 0.5, 1.0);
                // не наближається (ходить колом) або шляху немає — це місце відкладає і бере наступне
                double d = cow.distanceToSqr(claimed.getX() + 0.5, claimed.getY(), claimed.getZ() + 0.5);
                if (!ok || (walkTicks % 60 == 0 && d > lastDist - 0.5)) failedPaths++;
                if (walkTicks % 60 == 0) lastDist = d;
                if (failedPaths >= MAX_FAILED_PATHS) {
                    excluded.add(claimed);
                    claimed = chooseSpot(home);
                    failedPaths = 0;
                    lastDist = Double.MAX_VALUE;
                }
            }
            return;
        }
        // спить (і тримає своє місце, щоб інші його не займали)
        cow.getNavigation().stop();
        cow.sleeping = true;
        cow.sleepClaim = claimed != null ? claimed : cow.blockPosition();
        cow.resting = true;
        if (ticks % RECOVER_EVERY_TICKS == 0) cow.fatigue = Math.max(0, cow.fatigue - RECOVER);
        if (ticks % LISTEN_EVERY == 0) listen();
    }

    private boolean atSpot(BlockPos spot) {
        return cow.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) <= HOME_REACH * HOME_REACH;
    }

    // Наскільки місце «глибоко» в укритті: більше стін і сусідніх місць під дахом — глибше (вхід — найменше)
    private int depth(BlockPos p, boolean building) {
        int d = building ? CowHome.shelterScore(cow.level(), p) * 2 : 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos n = p.offset(dx, 0, dz);
                boolean covered = building ? CowHome.shelterScore(cow.level(), n) > 0 : CowHome.shaded(cow.level(), n);
                if (covered) d++;
            }
        }
        return d;
    }

    // Чи займає це місце інша корова (уже спить там або йде туди)
    private boolean claimedByOther(BlockPos p) {
        for (HungryCow c : cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(CLAIM_RANGE),
                c -> c != cow && c.isAlive() && c.sleepClaim != null)) {
            if (c.sleepClaim.distSqr(p) < 1.5) return true;
        }
        return false;
    }

    // Вибрати своє місце біля спільного: усередині укриття (або в тіні дерева), найглибше вільне, до якого можна дійти
    private BlockPos chooseSpot(BlockPos base) {
        boolean building = CowHome.shelterScore(cow.level(), base) > 0;
        java.util.List<BlockPos> spots = new java.util.ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-SPOT_RADIUS, -1, -SPOT_RADIUS), base.offset(SPOT_RADIUS, 1, SPOT_RADIUS))) {
            if (!CowHome.standable(cow.level(), p)) continue;
            boolean inside = building ? CowHome.shelterScore(cow.level(), p) > 0 : CowHome.shaded(cow.level(), p);
            if (!inside) continue;
            BlockPos ip = p.immutable();
            if (excluded.contains(ip) || claimedByOther(ip)) continue;
            spots.add(ip);
        }
        BlockPos me = cow.blockPosition();
        spots.sort((x, y) -> {
            int c = Integer.compare(depth(y, building), depth(x, building));   // глибше — раніше
            return c != 0 ? c : Double.compare(x.distSqr(me), y.distSqr(me));
        });
        int checks = 0;
        for (BlockPos p : spots) {
            if (checks++ >= MAX_SPOT_CHECKS) break;
            net.minecraft.world.level.pathfinder.Path path = cow.getNavigation().createPath(p, 0);
            if (path != null && path.canReach()) return p;
            excluded.add(p);
        }
        return null;   // вільних місць усередині немає — спить, де є
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
        cow.sleepClaim = null;
        claimed = null;
        claimBase = null;
        heardPos.clear();
    }
}
