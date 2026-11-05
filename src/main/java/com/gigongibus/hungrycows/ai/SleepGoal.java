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
    private static final int RETRY_SPOT_TICKS = 200;    // не знайшла свого місця — пробує знову через 10 с ...
    private static final int MAX_SPOT_RETRIES = 3;      // ... щонайбільше 3 рази за ніч
    private int retryAt = 0;
    private static final double CHECK_PATHS_NEAR_DOOR = 10;   // шляхи до місць усередині перевіряє лише ближче 10 блоків від входу
    private BlockPos door = null;          // вхід у дім, через який іде (спершу до нього, потім усередину)
    private boolean passedDoor = false;
    private static final int RETRY_HOME_TICKS = 600;   // заснула надворі, хоча дім є — через 30 с знову пробує дійти додому
    private int outsideSince = -1;
    private int retries = 0;

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
        // настав ранок, а ціль перервали ще вночі: звільнити своє місце
        if (!cow.isNightTime() && claimed != null) {
            claimed = null;
            claimBase = null;
            cow.sleepClaim = null;
        }
        // розбуджена корова з цілі не виходить: стоїть на своєму місці, поки не засне знову
        return cow.isNightTime() && !cow.isBaby() && !cow.cycle.inEstrus();
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
        // повернулась уночі (поїла, попила): якщо вона надворі, знову йде через вхід
        if (door != null) passedDoor = CowHome.hasRoof(cow.level(), cow.blockPosition());
        outsideSince = -1;
    }

    @Override
    public void tick() {
        ticks++;
        // стадо спить разом: туди ж, де ватажок (якщо вона поруч і має де спати)
        BlockPos home = cow.home.sleepSpot();
        BlockPos homeDoor = cow.home.home() != null && cow.home.home().equals(home) ? cow.home.entrance() : null;
        HungryCow leader = cow.herd.leader();
        if (leader != null && leader != cow && leader.home.sleepSpot() != null
                && cow.distanceToSqr(leader) < SAME_SLEEP_RANGE * SAME_SLEEP_RANGE) {
            home = leader.home.sleepSpot();
            homeDoor = leader.home.home() != null && leader.home.home().equals(home) ? leader.home.entrance() : null;
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
            boolean newBase = !home.equals(claimBase);
            // нове спільне місце — шукає одразу; не знайшла свого — ще кілька спроб через 10 с
            if (newBase || (cow.tickCount >= retryAt && retries < MAX_SPOT_RETRIES)) {
                if (newBase) retries = 0; else retries++;
                claimBase = home;
                door = homeDoor;
                passedDoor = door == null || CowHome.hasRoof(cow.level(), cow.blockPosition());   // уже всередині — вхід не потрібен
                excluded.clear();
                claimed = chooseSpot(home);
                retryAt = cow.tickCount + RETRY_SPOT_TICKS;
                walkTicks = 0;
                failedPaths = 0;
                lastDist = Double.MAX_VALUE;
            }
        }
        if (claimed != null && walkTicks < MAX_WALK_TICKS && !atSpot(claimed)) {
            walkTicks++;
            cow.sleeping = false;
            cow.resting = false;
            cow.sleepClaim = claimed;
            if (walkTicks == 1 || walkTicks % 20 == 0) {
                // спершу до входу (надворі шлях простий), біля входу — усередину й заново вибирає своє місце
                if (!passedDoor && door != null && cow.distanceToSqr(door.getX() + 0.5, door.getY(), door.getZ() + 0.5)
                        <= CowHome.ENTRANCE_REACH * CowHome.ENTRANCE_REACH) {
                    passedDoor = true;
                    excluded.clear();
                    BlockPos better = chooseSpot(home);
                    if (better != null) claimed = better;
                    lastDist = Double.MAX_VALUE;
                    failedPaths = 0;
                }
                BlockPos goal = passedDoor ? claimed : door;
                cow.getNavigation().setMaxVisitedNodesMultiplier(CowHome.PATH_SEARCH_MULTIPLIER);   // довгий шлях: обійти будівлю до входу
                boolean ok = cow.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0);
                cow.getNavigation().resetMaxVisitedNodesMultiplier();
                // не наближається (ходить колом) або шляху немає — це місце відкладає і бере наступне
                double d = cow.distanceToSqr(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5);
                if (!ok || (walkTicks % 60 == 0 && d > lastDist - 0.5)) failedPaths++;
                if (walkTicks % 60 == 0) lastDist = d;
                if (failedPaths >= MAX_FAILED_PATHS && !passedDoor) {
                    passedDoor = true;   // до входу не дійти — пробує прямо до місця
                    failedPaths = 0;
                    lastDist = Double.MAX_VALUE;
                } else if (failedPaths >= MAX_FAILED_PATHS) {
                    excluded.add(claimed);
                    claimed = chooseSpot(home);
                    failedPaths = 0;
                    lastDist = Double.MAX_VALUE;
                }
            }
            return;
        }
        // розбудили: стоїть на місці без сну (і не відпочиває), прислухається, потім засинає знову
        // (лише вдома: надворі, коли дім є, неспання не заважає йти додому)
        boolean settled = home == null || CowHome.hasRoof(cow.level(), cow.blockPosition()) || (claimed != null && atSpot(claimed));
        if (cow.tickCount < awakeUntil && settled) {
            cow.getNavigation().stop();
            cow.sleeping = false;
            cow.resting = false;
            cow.sleepClaim = claimed != null ? claimed : cow.blockPosition();
            if (ticks % LISTEN_EVERY == 0) listen();
            return;
        }
        // дім є, а вона не дійшла (спить надворі): через 30 с починає шлях додому заново, а не спить так до ранку
        if (home != null && (claimed == null || !atSpot(claimed)) && !CowHome.hasRoof(cow.level(), cow.blockPosition())) {
            if (outsideSince < 0) {
                outsideSince = cow.tickCount;
            } else if (cow.tickCount - outsideSince >= RETRY_HOME_TICKS) {
                outsideSince = -1;
                cow.tell(cow.family.name + " не дійшла додому, пробує знову");
                claimed = null;
                claimBase = null;   // наступного тіку — нове місце, шлях і лічильники з нуля
                retries = 0;
                return;
            }
        } else {
            outsideSince = -1;
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
        // далеко від входу шлях до місця всередині гра часто не знаходить (задовгий обхід) — тоді бере найглибше без перевірки,
        // а перевірить, коли дійде до входу
        if (door != null && !passedDoor && cow.distanceToSqr(door.getX() + 0.5, door.getY(), door.getZ() + 0.5)
                > CHECK_PATHS_NEAR_DOOR * CHECK_PATHS_NEAR_DOOR) {
            return spots.isEmpty() ? null : spots.get(0);
        }
        int checks = 0;
        for (BlockPos p : spots) {
            if (checks++ >= MAX_SPOT_CHECKS) break;
            cow.getNavigation().setMaxVisitedNodesMultiplier(CowHome.PATH_SEARCH_MULTIPLIER);
            net.minecraft.world.level.pathfinder.Path path = cow.getNavigation().createPath(p, 1);
            cow.getNavigation().resetMaxVisitedNodesMultiplier();
            if (path != null && path.getEndNode() != null) {
                BlockPos end = path.getEndNode().asBlockPos();
                if (end.distManhattan(p) <= 1) return p;
                // до самого місця не пролізти (вузький куток), але шлях закінчується теж усередині й вільно — годиться
                boolean endInside = building ? CowHome.shelterScore(cow.level(), end) > 0 : CowHome.shaded(cow.level(), end);
                if (endInside && CowHome.standable(cow.level(), end) && !claimedByOther(end) && !excluded.contains(end)) {
                    return end.immutable();
                }
            }
            excluded.add(p);
        }
        if (!spots.isEmpty()) cow.tell(cow.family.name + " не знайшла вільного місця в домі, до якого можна дійти");
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
        boolean wasAwake = cow.tickCount < awakeUntil;   // уже не спить: шум лише продовжує неспання, без нового повідомлення
        awakeUntil = cow.tickCount + WAKE_TICKS;
        boolean friend = heard instanceof Player p && cow.trustOf(p) >= CowTrust.FRIEND_FROM;
        if (cow.isScary(heard) && !friend) {
            cow.fear = Math.max(cow.fear, WAKE_FEAR);
            if (!wasAwake) cow.tell(cow.family.name + " прокинулась від шуму і злякалась: " + heard.getName().getString());
        } else if (!wasAwake) {
            cow.tell(cow.family.name + " прокинулась від шуму: " + heard.getName().getString());
        }
    }

    @Override
    public void stop() {
        cow.sleeping = false;
        cow.resting = false;
        heardPos.clear();
        // ціль перервали вночі (поїсти, попити, злякалась): своє місце тримає і потім повертається туди
        if (cow.isNightTime()) return;
        cow.sleepClaim = null;
        claimed = null;
        claimBase = null;
        door = null;
    }
}
