package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowHome;
import com.gigongibus.hungrycows.CowSleep;
import com.gigongibus.hungrycows.Trails;
import com.gigongibus.hungrycows.CowTrust;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Вночі корова йде додому (укриття) і спить: стоїть, відпочиває, гірше помічає дрібниці.
// Якщо дому немає — спить під деревом (будь-яке накриття); якщо й дерева немає — там, де є.
// Голод, спрага, страх і пологи важливіші (вищий пріоритет).
//
// Як корова йде спати:
//   1) спільне місце = дім ватажка (якщо вона поруч), інакше свій дім чи дерево;
//   2) своє місце біля спільного (усередині, найглибше, не зайняте іншою);
//   3) якщо надворі — спершу до входу (вхід відомий або вона дізнається його сама), далі всередину;
//   4) поблизу дому перевіряє, чи до місця справді можна дійти (здалеку гра часто не знаходить шляху);
//   5) не дійшла — через 30 с пробує знову; без дому — знову шукає дім;
//   6) шкала сну (CowSleep) дійшла до 100, а корова ще не вдома — засинає, де є;
//   7) дійшла вперше — запам'ятовує дорогу точками, наступного разу йде нею (від точки до точки);
//      дорогою додому витоптує стежку (Trails).
public class SleepGoal extends Goal {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final double HOME_REACH = 1.0;        // «на своєму місці» = ближче цього
    private static final int SPOT_RADIUS = 5;            // своє місце шукає до 5 блоків від спільного
    private static final double CLAIM_RANGE = 16;        // чиї зайняті місця враховує
    private static final double CLAIM_SPACE = 1.5;       // своє місце + сусідні клітинки (квадрат відстані): спершу шукає з простором
    private static final int MAX_SPOT_CHECKS = 6;        // скільки найкращих місць перевіряє, чи можна дійти
    private static final double CHECK_NEAR_BASE = 12;    // шлях до місць перевіряє лише ближче 12 блоків від спільного місця
    private static final double SAME_SLEEP_RANGE = 32;   // ватажок ближче цього — стадо спить там, де вона
    private static final int MAX_WALK_TICKS = 2400;      // іде додому щонайбільше 2 хв, потім спить, де є (і пізніше пробує знову)
    private static final int WAIT_LEADER_MIN = 200;      // увечері ватажок ще не вдома — інші зволікають 10 ...
    private static final int WAIT_LEADER_MAX = 400;      // ... – 20 с (ватажок уже вдома — ідуть одразу)
    private static final int MAX_FAILED_PATHS = 5;       // стільки разів шляху немає / не наближається — інше місце
    private static final int LEARN_DOOR_EVERY = 100;     // вхід невідомий — пробує дізнатися раз на 5 с
    private static final int RETRY_HOME_TICKS = 600;     // спить надворі, хоча дім є — через 30 с знову йде додому
    private static final int SEEK_TICKS = 600;           // без місця для сну шукає 30 с ...
    private static final int RESEEK_TICKS = 1200;        // ... і, якщо не знайшла, ще раз через 1 хв
    private static final double STAY_NEAR_LEADER = 6;    // під час пошуку тримається не далі 6 блоків від ватажка
    private static final int RECOVER_EVERY_TICKS = 20;   // втома −2 щосекунди сну
    private static final int RECOVER = 2;
    // ================= СЛУХ УВІ СНІ =================
    private static final double HEAR_RANGE = 5.0;        // чує, як хтось рухається, до 5 блоків
    private static final double HEAR_SNEAK = 2.5;        // гравця, що крадеться присівши, — лише до 2,5
    private static final double MOVE_HEARD = 0.3;        // «рухається» = за пів секунди зрушив більше ніж на 0,3 блока
    private static final int LISTEN_EVERY = 10;          // прислухається раз на пів секунди
    private static final int WAKE_TICKS = 600;           // прокинулась — пів хвилини не засинає
    private static final int WAKE_FEAR = 40;             // небезпечний моб поруч: такий страх (тікає)

    private final HungryCow cow;
    private final Map<Integer, Vec3> heardPos = new HashMap<>();
    private final Set<BlockPos> excluded = new HashSet<>();
    private int awakeUntil = 0;
    private int ticks = 0;
    private int walkTicks = 0;
    private int seekTicks = 0;
    private int homelessSince = -1;      // коли заснула без дому (для повторного пошуку)
    private int failedPaths = 0;
    private double lastDist = Double.MAX_VALUE;
    private BlockPos claimed = null;     // своє місце цієї ночі
    private BlockPos claimBase = null;   // спільне місце, біля якого вибрано своє
    private boolean spotChecked = false; // чи перевірено, що до свого місця можна дійти
    private BlockPos door = null;        // вхід, через який іде (спершу до нього, потім усередину)
    private boolean passedDoor = false;
    private int nextDoorTry = 0;
    private int outsideSince = -1;
    private boolean tight = false;       // у домі тісно: досить, щоб саме ця клітинка була вільна
    private int routeIdx = -1;           // до якої точки запам'ятованої дороги йде (−1 — не дорогою)
    private boolean usedRoute = false;   // цієї ночі йде своєю дорогою (дійде — пам'ятатиме міцніше)
    private boolean startedOutside = false;
    private boolean arrived = false;
    private final java.util.List<BlockPos> recording = new java.util.ArrayList<>();   // нова дорога, поки йде
    private BlockPos lastStep = null;    // блок, на якому стояла (для стежки)

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
        if (!cow.isNightTime() && claimed != null) forgetNight();
        if (!cow.isNightTime()) leaderWaitUntil = -1;
        // розбуджена корова з цілі не виходить: стоїть на своєму місці, поки не засне знову
        if (!(cow.isNightTime() && !cow.isBaby() && !cow.cycle.inEstrus())) return false;
        return !waitingForLeader();
    }

    @Override
    public boolean canContinueToUse() {
        return cow.isNightTime() && !cow.isBaby() && !cow.cycle.inEstrus();
    }

    // Увечері ватажок ще не вдома: інші трохи зволікають (10–20 с), а щойно вона зайшла — ідуть одразу (Fij)
    private int leaderWaitUntil = -1;

    private boolean waitingForLeader() {
        HungryCow leader = cow.herd.leader();
        if (leader == null || leader == cow || cow.sleep.full() || isInside()) return false;
        if (cow.distanceToSqr(leader) > SAME_SLEEP_RANGE * SAME_SLEEP_RANGE) return false;
        if (leader.sleeping || CowHome.hasRoof(leader.level(), leader.blockPosition())) return false;   // ватажок уже вдома
        if (leaderWaitUntil < 0) {
            leaderWaitUntil = cow.tickCount + WAIT_LEADER_MIN + cow.getRandom().nextInt(WAIT_LEADER_MAX - WAIT_LEADER_MIN + 1);
        }
        return cow.tickCount < leaderWaitUntil;
    }

    @Override
    public void start() {
        ticks = 0;
        walkTicks = 0;
        seekTicks = 0;
        failedPaths = 0;
        lastDist = Double.MAX_VALUE;
        outsideSince = -1;
        // повернулась уночі (поїла, попила, злякалась): якщо вона не вдома — знову йде через вхід
        passedDoor = isInside();
        if (claimBase != null) beginWay(claimBase);
    }

    // Почати дорогу до base: своєю дорогою, якщо пам'ятає й вона поруч; інакше записує нову
    private void beginWay(BlockPos base) {
        recording.clear();
        arrived = false;
        startedOutside = !isInside();
        routeIdx = startedOutside ? cow.sleep.joinRoute(base) : -1;
        usedRoute = routeIdx >= 0;
        if (startedOutside && !usedRoute) recording.add(cow.blockPosition());
    }

    @Override
    public void tick() {
        ticks++;
        BlockPos base = sharedSpot();

        // 1. Ще не знає, де спати: 30 с активно шукає (будівлю, потім дерево), тримаючись стада
        if (base == null) {
            if (seekTicks < SEEK_TICKS && !cow.sleep.full()) {
                seekTicks++;
                awake();
                if (seekTicks % 20 == 1) {
                    cow.home.searchHard();
                    HungryCow l = cow.herd.leader();
                    if (l != null && l != cow && cow.distanceToSqr(l) > STAY_NEAR_LEADER * STAY_NEAR_LEADER) {
                        cow.getNavigation().moveTo(l, 1.0);
                    }
                }
                if (seekTicks == SEEK_TICKS) {
                    cow.tell(cow.family.name + " не знайшла, де спати, — спить тут");
                    homelessSince = cow.tickCount;
                }
                return;
            }
            // не знайшла: спить, де є, а через хвилину шукає ще раз
            if (homelessSince >= 0 && cow.tickCount - homelessSince >= RESEEK_TICKS) {
                seekTicks = 0;
                homelessSince = -1;
                return;
            }
            sleepHere();
            return;
        }

        // 2. Своє місце біля спільного (нове спільне місце — вибирає заново)
        if (!base.equals(claimBase)) {
            claimBase = base;
            claimed = null;
            door = doorOf(base);
            passedDoor = isInside();   // вхід невідомий — дізнається дорогою (walkStep)
            excluded.clear();
            claimed = chooseSpot(base);
            walkTicks = 0;
            failedPaths = 0;
            lastDist = Double.MAX_VALUE;
            beginWay(base);
        }

        // шкала сну повна, а корова ще не вдома: далі не йде, засинає, де є
        if (cow.sleep.full() && !isInside()) {
            sleepHere();
            return;
        }

        // 3. Іде до свого місця
        if (claimed != null && walkTicks < MAX_WALK_TICKS && !atSpot(claimed)) {
            walkTicks++;
            awake();
            cow.sleepClaim = claimed;
            trample();
            // у черзі біля входу — щотіку (за секунду корова проскочила б межу черги)
            if (cow.gate.active() && cow.gate.mustWait()) {
                cow.gate.holdStill();
                walkTicks--;   // час у черзі не рахується в 2 хв дороги
                return;
            }
            if (walkTicks == 1 || walkTicks % 20 == 0) walkStep(base);
            return;
        }
        // дійшла: перша дорога — запам'ятати; своєю дорогою — пам'ятати міцніше
        if (claimed != null && atSpot(claimed) && !arrived) {
            arrived = true;
            if (usedRoute) {
                cow.sleep.reinforce();
            } else if (startedOutside) {
                recording.add(claimed);
                cow.sleep.saveRoute(base, recording);
            }
        }

        // 4. Розбудили: стоїть на місці без сну, прислухається, потім засинає знову (лише вдома;
        //    надворі неспання не заважає йти додому)
        if (cow.tickCount < awakeUntil && isInside()) {
            cow.getNavigation().stop();
            awake();
            cow.sleepClaim = claimed != null ? claimed : cow.blockPosition();
            if (ticks % LISTEN_EVERY == 0) listen();
            return;
        }

        // 5. Не дійшла (надворі або на порозі): через 30 с починає шлях додому заново
        if (!isInside()) {
            if (outsideSince < 0) {
                outsideSince = cow.tickCount;
            } else if (cow.tickCount - outsideSince >= RETRY_HOME_TICKS) {
                cow.tell(cow.family.name + " не дійшла додому, пробує знову");
                outsideSince = -1;
                claimBase = null;   // наступного тіку — нове місце, шлях і лічильники з нуля
                claimed = null;
                return;
            }
        } else {
            outsideSince = -1;
        }

        // 6. Спить
        sleepHere();
    }

    // Один крок дороги (раз на секунду): вхід → своє місце; перевірка досяжності поблизу; заміна місця, якщо не виходить
    private void walkStep(BlockPos base) {
        boolean grounded = cow.onGround() || cow.isInWater();
        // черга біля входу: по одній, старша перша (CowGate)
        if (!passedDoor && door != null) cow.gate.approach(door);
        if (cow.gate.mustWait()) {
            cow.gate.holdStill();
            failedPaths = 0;
            lastDist = Double.MAX_VALUE;
            return;
        }
        // своєю дорогою: точку пройдено — до наступної; остання — далі до свого місця
        if (routeIdx >= 0) {
            BlockPos rp = cow.sleep.routePoint(routeIdx);
            if (rp == null) {
                routeIdx = -1;
            } else if (cow.distanceToSqr(rp.getX() + 0.5, rp.getY(), rp.getZ() + 0.5) <= CowSleep.ROUTE_REACH * CowSleep.ROUTE_REACH) {
                routeIdx++;
                failedPaths = 0;
                lastDist = Double.MAX_VALUE;
                if (routeIdx >= cow.sleep.routeSize()) {
                    routeIdx = -1;
                    passedDoor = true;
                }
            }
        }
        // нова дорога: точка кожні 5 блоків
        if (!usedRoute && startedOutside && grounded) {
            BlockPos here = cow.blockPosition();
            if (recording.isEmpty() || recording.get(recording.size() - 1).distSqr(here) >= CowSleep.ROUTE_STEP * CowSleep.ROUTE_STEP) {
                recording.add(here);
            }
        }
        // вхід невідомий, а корова надворі: пробує дізнатися його сама
        if (routeIdx < 0 && door == null && !passedDoor && cow.tickCount >= nextDoorTry && grounded) {
            nextDoorTry = cow.tickCount + LEARN_DOOR_EVERY;
            door = doorOf(base);
            if (door == null) door = cow.home.findEntrance(claimed);
            if (door == null) passedDoor = true;   // не знайшла — іде прямо
        }
        // дійшла до входу — далі всередину
        if (!passedDoor && door != null && cow.distanceToSqr(door.getX() + 0.5, door.getY(), door.getZ() + 0.5)
                <= CowHome.ENTRANCE_REACH * CowHome.ENTRANCE_REACH) {
            passedDoor = true;
            failedPaths = 0;
            lastDist = Double.MAX_VALUE;
        }
        // уже близько до дому, а місце ще не перевірене: вибирає заново, тепер з перевіркою шляху
        if (!spotChecked && grounded && nearBase(base)) {
            excluded.clear();
            BlockPos better = chooseSpot(base);
            if (better != null) claimed = better;
            failedPaths = 0;
            lastDist = Double.MAX_VALUE;
            if (claimed == null) return;
        }
        BlockPos goal = routeIdx >= 0 ? cow.sleep.routePoint(routeIdx) : (passedDoor || door == null ? claimed : door);
        cow.getNavigation().setMaxVisitedNodesMultiplier(CowHome.PATH_SEARCH_MULTIPLIER);   // довгий шлях: обійти будівлю до входу
        boolean ok = cow.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0);
        cow.getNavigation().resetMaxVisitedNodesMultiplier();
        // шляху немає (але не тому, що корова в повітрі) або за 3 с не наблизилась — невдача
        double d = cow.distanceToSqr(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5);
        if (!ok && grounded) failedPaths++;
        if (walkTicks % 60 == 0) {
            if (d > lastDist - 0.5) failedPaths++;
            lastDist = d;
        }
        if (failedPaths < MAX_FAILED_PATHS) return;
        failedPaths = 0;
        lastDist = Double.MAX_VALUE;
        if (routeIdx >= 0) {
            // своєю дорогою не пройти (перегородили): забуває її, іде, як уперше, і запише нову
            cow.sleep.forgetRoute();
            routeIdx = -1;
            usedRoute = false;
            recording.clear();
            recording.add(cow.blockPosition());
        } else if (!passedDoor) {
            passedDoor = true;   // до входу не дійти — пробує прямо до місця
        } else {
            excluded.add(claimed);   // до цього місця не дійти — бере наступне
            claimed = chooseSpot(base);
        }
    }

    // Спільне місце: дім (чи дерево) ватажка, якщо вона поруч, інакше своє
    private BlockPos sharedSpot() {
        HungryCow leader = cow.herd.leader();
        if (leader != null && leader != cow && leader.home.sleepSpot() != null
                && cow.distanceToSqr(leader) < SAME_SLEEP_RANGE * SAME_SLEEP_RANGE
                && (leader.home.home() != null || cow.home.home() == null)) {   // дерево ватажка не краще за свій дім
            return leader.home.sleepSpot();
        }
        if (cow.home.home() == null) cow.home.adoptFromHerd();   // нова корова: дім стада, а не сон на місці
        return cow.home.sleepSpot();
    }

    // Відомий вхід до спільного місця (свого дому чи дому ватажка)
    private BlockPos doorOf(BlockPos base) {
        if (base.equals(cow.home.home())) return cow.home.entrance();
        HungryCow leader = cow.herd.leader();
        if (leader != null && base.equals(leader.home.home())) return leader.home.entrance();
        return null;
    }

    // Чи корова вже вдома: на своєму місці, а якщо місця немає — справді в укритті (дах і стіни, не просто під навісом)
    private boolean isInside() {
        if (claimed != null) return atSpot(claimed);
        BlockPos pos = cow.blockPosition();
        if (claimBase != null && CowHome.shelterScore(cow.level(), claimBase) == 0) {
            return CowHome.shaded(cow.level(), pos);   // спить під деревом: досить тіні
        }
        return CowHome.shelterScore(cow.level(), pos) > 0;
    }

    // Дорогою додому витоптує стежку: кожен новий блок під ногами
    private void trample() {
        if (!(cow.level() instanceof net.minecraft.server.level.ServerLevel level) || !cow.onGround()) return;
        BlockPos here = cow.blockPosition();
        if (here.equals(lastStep)) return;
        lastStep = here.immutable();
        Trails.step(level, here.below());
    }

    private boolean nearBase(BlockPos base) {
        return cow.distanceToSqr(base.getX() + 0.5, base.getY(), base.getZ() + 0.5) <= CHECK_NEAR_BASE * CHECK_NEAR_BASE;
    }

    private boolean atSpot(BlockPos spot) {
        return cow.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) <= HOME_REACH * HOME_REACH;
    }

    private void awake() {
        cow.sleeping = false;
        cow.resting = false;
    }

    // Спить на місці (і тримає своє місце, щоб інші його не займали)
    private void sleepHere() {
        cow.getNavigation().stop();
        cow.sleeping = true;
        cow.resting = true;
        cow.sleepClaim = claimed != null ? claimed : cow.blockPosition();
        if (ticks % RECOVER_EVERY_TICKS == 0) cow.fatigue = Math.max(0, cow.fatigue - RECOVER);
        if (ticks % LISTEN_EVERY == 0) listen();
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
            if (tight ? c.sleepClaim.equals(p) : c.sleepClaim.distSqr(p) < CLAIM_SPACE) return true;
        }
        return false;
    }

    // Вибрати своє місце біля спільного: спершу з простором навколо; якщо тісно — будь-яка вільна клітинка
    private BlockPos chooseSpot(BlockPos base) {
        tight = false;
        BlockPos p = chooseSpotOnce(base);
        if (p == null) {
            tight = true;
            excluded.clear();
            p = chooseSpotOnce(base);
            tight = false;
        }
        if (p == null) cow.tell(cow.family.name + " не знайшла вільного місця в домі, до якого можна дійти");
        return p;
    }

    // Усередині укриття (або в тіні дерева), найглибше вільне. Близько до дому й на землі — ще й перевіряє шлях;
    // здалеку гра шлях часто не знаходить, тому тоді бере найглибше без перевірки (перевірить, коли підійде)
    private BlockPos chooseSpotOnce(BlockPos base) {
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
        if (spots.isEmpty()) return null;
        BlockPos me = cow.blockPosition();
        spots.sort((x, y) -> {
            int c = Integer.compare(depth(y, building), depth(x, building));   // глибше — раніше
            return c != 0 ? c : Double.compare(x.distSqr(me), y.distSqr(me));
        });
        boolean canCheck = (cow.onGround() || cow.isInWater()) && nearBase(base);
        spotChecked = canCheck;
        if (!canCheck) return spots.get(0);
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
        return null;
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

    // Нова доба: забути своє місце й вхід
    private void forgetNight() {
        cow.sleepClaim = null;
        claimed = null;
        claimBase = null;
        door = null;
        homelessSince = -1;
    }

    @Override
    public void stop() {
        awake();
        heardPos.clear();
        cow.gate.cancel();
        // ціль перервали вночі (поїсти, попити, злякалась): своє місце тримає і потім повертається туди
        if (!cow.isNightTime()) forgetNight();
    }
}
