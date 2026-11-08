package com.gigongibus.hungrycows.nav;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ходіння корови: ванільне (GroundPathNavigation), але:
 *  - пошук шляху свій (CowNodeEvaluator: тримається далі від стін);
 *  - ходіння за шляхом без зрізання кутів (followThePath): точка пройдена, лише коли корова в ній; раніше — лише на прямій;
 *  - не врізається в інших корів (Fij): пошук шляху гри інших тварин не бачить, тому корова сама дивиться вперед.
 *    Інша корова попереду на шляху йде в той самий бік — іде слідом повільніше; стоїть або йде назустріч — чекає 1 с,
 *    потім до 1 с крок убік, лише туди, де вільно (праворуч, ліворуч; обидва зайняті — чекає повільно). Шлях не
 *    перебудовує (перша версія з перебудовою смикала корову). Черга біля входу — у CowGate.
 * Усі числа зібрані на початку файлу.
 */
public class CowNavigation extends GroundPathNavigation {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    // обходити інших корів (перша версія з перебудовою шляху смикала корову: зіткнень 0–33 проти 0–1; тепер без перебудови)

    public static final boolean AVOID_COWS = true;
    public static final double AHEAD = 1.7;           // дивиться вперед на 1,7 блока (від центру до центру)
    public static final double SIDE_MARGIN = 0.15;    // «на шляху» = збоку ближче половин ширин + 0,15
    public static final int CHECK_EVERY = 4;          // дивиться раз на 4 тіки
    public static final double MOVING = 0.03;         // «іде» = швидкість понад 0,03 блока за тік у той самий бік
    public static final double FOLLOW_SPEED = 0.6;    // іде слідом / чекає — 60% швидкості (30% виглядало як «баг»)
    public static final int WAIT_TICKS = 20;          // та стоїть чи йде назустріч — чекає 1 с ...
    public static final int SIDESTEP_TICKS = 20;      // ... потім до 1 с відступає вбік (праворуч), обходячи її
    public static final double SIDESTEP_PUSH = 0.03;  // сила кроку вбік (раз на 4 тіки)
    public static final double NEAR_GOAL = 2.5;       // ціль ближче 2,5 бл. (підходить їсти поруч з іншою) — не обходить
    // Ходіння за шляхом (followThePath): не зрізати кути
    public static final double WAYPOINT_REACH = 0.35; // точку пройдено, коли центр корови ближче 0,35 бл. до центру точки (по X і по Z)
    public static final double WAYPOINT_DY = 1.0;     // ... і різниця висоти менша за 1 блок
    public static final double STRAIGHT_REACH = 0.6;  // на прямій (не поворот) точку можна пропустити раніше: ближче 0,6 бл.
    public static final int WAYPOINT_STUCK_TICKS = 20; // 1 с не наближається до точки (крок навскіс, пагорбок, точка блоком нижче) —
                                                      // раз діє ванільне правило (може зрізати кут), щоб не застрягти назавжди
    public static final double WAYPOINT_PROGRESS = 0.05; // «наближається» = за тік ближче хоча б на 0,05 бл. (сумарно)
    // Крок убік лише туди, де вільно
    public static final double SIDESTEP_CHECK = 0.6;  // рамку корови зсуває на 0,6 бл. у бік кроку й дивиться, чи там вільно

    private int nextCheck = 0;
    private int waitStart = -1;
    private int stuckNode = -1;        // до якої точки шляху корова зараз іде (для запасного виходу)
    private double stuckBest;          // найменша відстань до неї
    private int stuckTicks = 0;        // скільки тіків без наближення
    private boolean slowed = false;
    private double goalSpeed = 1.0;

    public CowNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new CowNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    int now() {
        return this.mob.tickCount;
    }


    @Override
    public boolean moveTo(Path path, double speed) {
        goalSpeed = speed;
        if (path != this.path) stuckNode = -1;   // новий шлях — запасний вихід рахує заново
        return super.moveTo(path, slowed ? speed * FOLLOW_SPEED : speed);
    }

    @Override
    public void tick() {
        if (AVOID_COWS && !this.level.isClientSide) avoidCows();
        super.tick();
    }

    // ---------- ходіння за шляхом ----------

    // Як ванільний followThePath, але без зрізання кутів: ванільний переходить до наступної точки, щойно корова ближче
    // половини своєї ширини (~0,45–0,52) або «дивиться» на наступну (canCutCorner + shouldTargetNextNodeInDirection) —
    // широка корова зрізала поворот і зачіпала кут стіни. Тут: точка пройдена, лише коли корова справді в ній (WAYPOINT_REACH);
    // раніше — лише на прямій ділянці (поточна, наступна й ще наступна точки на одній прямій), тобто не на повороті.
    // «Центр точки» — та сама точка, куди корову веде гра (Path.getEntityPosAtNode), а не центр блока: для корови ширшої
    // за блок вона в куті клітинки, і до центру блока корова ніколи б не дійшла.
    @Override
    protected void followThePath() {
        Vec3 tempPos = this.getTempMobPos();
        int i = this.path.getNextNodeIndex();
        Vec3 at = this.path.getEntityPosAtNode(this.mob, i);
        double dx = Math.abs(this.mob.getX() - at.x);
        double dz = Math.abs(this.mob.getZ() - at.z);
        double dy = Math.abs(this.mob.getY() - this.path.getNodePos(i).getY());
        boolean reached = dx < WAYPOINT_REACH && dz < WAYPOINT_REACH && dy < WAYPOINT_DY;
        // запасний вихід: точка та сама, а корова 1 с не наближається — раз ванільне правило
        double dist = dx + dz;
        if (i != stuckNode) {
            stuckNode = i;
            stuckBest = dist;
            stuckTicks = 0;
        } else if (dist < stuckBest - WAYPOINT_PROGRESS) {
            stuckBest = dist;
            stuckTicks = 0;
        } else if (!reached && ++stuckTicks >= WAYPOINT_STUCK_TICKS) {
            stuckTicks = 0;
            stuckNode = -1;
            super.followThePath();   // ванільне: ширший допуск і зрізання кута (у ньому й doStuckDetection)
            return;
        }
        boolean straightSkip = !reached && dy < WAYPOINT_DY && dx < STRAIGHT_REACH && dz < STRAIGHT_REACH && straightAt(i);
        if (reached || straightSkip) this.path.advance();
        this.doStuckDetection(tempPos);   // як у ванільному
    }

    // Точки i, i+1, i+2 на одній прямій і на одній висоті (тобто в точці i немає повороту)
    private boolean straightAt(int i) {
        if (i + 2 >= this.path.getNodeCount() || i < 0) return false;
        Vec3i a = this.path.getNodePos(i), b = this.path.getNodePos(i + 1), c = this.path.getNodePos(i + 2);
        if (a.getY() != b.getY() || b.getY() != c.getY()) return false;
        int d1x = b.getX() - a.getX(), d1z = b.getZ() - a.getZ();
        int d2x = c.getX() - b.getX(), d2z = c.getZ() - b.getZ();
        return d1x == d2x && d1z == d2z;
    }

    // ---------- обхід інших корів ----------

    // Чи вільно, якщо зсунути корову на SIDESTEP_CHECK у бік side: блоки (ноги й голова — уся рамка) і інші корови
    private boolean freeTowards(Vec3 side) {
        AABB box = this.mob.getBoundingBox().move(side.scale(SIDESTEP_CHECK));
        if (!this.level.noCollision(this.mob, box)) return false;
        return this.level.getEntitiesOfClass(HungryCow.class, box, o -> o != this.mob && o.isAlive()).isEmpty();
    }

    private void setSlowed(boolean s) {
        if (s == slowed) return;
        slowed = s;
        this.setSpeedModifier(s ? goalSpeed * FOLLOW_SPEED : goalSpeed);
    }

    private void avoidCows() {
        if (this.isDone() || this.path == null) {
            setSlowed(false);
            return;
        }
        int t = now();
        if (t < nextCheck) return;
        nextCheck = t + CHECK_EVERY;

        Vec3 pos = this.mob.position();
        BlockPos goal = this.path.getTarget();
        if (goal != null && goal.distToCenterSqr(pos) < NEAR_GOAL * NEAR_GOAL) {   // майже прийшла — не сповільнюватись
            waitStart = -1;
            setSlowed(false);
            return;
        }
        Vec3 next = this.path.getNextEntityPos(this.mob);
        Vec3 dir = new Vec3(next.x - pos.x, 0, next.z - pos.z);
        if (dir.lengthSqr() < 1.0E-4) return;
        dir = dir.normalize();

        HungryCow blocker = null;
        double best = AHEAD;
        for (HungryCow o : this.level.getEntitiesOfClass(HungryCow.class, this.mob.getBoundingBox().inflate(AHEAD + 1.0),
                o -> o != this.mob && o.isAlive())) {
            Vec3 to = new Vec3(o.getX() - pos.x, 0, o.getZ() - pos.z);
            double fwd = to.dot(dir);
            if (fwd <= 0 || fwd > best) continue;
            double lat = to.subtract(dir.scale(fwd)).length();
            if (lat > (this.mob.getBbWidth() + o.getBbWidth()) / 2.0 + SIDE_MARGIN) continue;
            best = fwd;
            blocker = o;
        }
        if (blocker == null) {
            waitStart = -1;
            setSlowed(false);
            return;
        }
        Vec3 ov = blocker.getDeltaMovement();
        if (ov.x * dir.x + ov.z * dir.z > MOVING) {   // іде попереду в той самий бік — слідом, повільніше
            waitStart = -1;
            setSlowed(true);
            return;
        }
        // стоїть або йде назустріч: зачекати (дуже повільно) до WAIT_TICKS, потім іти далі як є (без перебудови шляху —
        // перебудова смикала корову й зіткнень ставало більше)
        if (waitStart < 0) waitStart = t;
        boolean waiting = t - waitStart < WAIT_TICKS;
        setSlowed(waiting);
        // почекала, а та досі на шляху — крок убік (щонайбільше SIDESTEP_TICKS), але лише туди, де вільно:
        // праворуч вільно → праворуч; ні, а ліворуч вільно → ліворуч; обидва боки зайняті → не штовхати, чекати повільно
        if (!waiting && t - waitStart < WAIT_TICKS + SIDESTEP_TICKS) {
            Vec3 right = new Vec3(-dir.z, 0, dir.x);
            Vec3 side = freeTowards(right) ? right : freeTowards(right.scale(-1)) ? right.scale(-1) : null;
            if (side != null) {
                this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(side.scale(SIDESTEP_PUSH)));
            } else {
                setSlowed(true);
            }
        }
    }
}
