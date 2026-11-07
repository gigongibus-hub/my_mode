package com.gigongibus.hungrycows.nav;

import com.gigongibus.hungrycows.HungryCow;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
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
 *  - не врізається в інших корів (Fij): пошук шляху гри інших тварин не бачить, тому корова сама дивиться вперед.
 *    Інша корова попереду на шляху йде в той самий бік — іде слідом повільніше; стоїть або йде назустріч — шлях
 *    будується заново в обхід її клітинок (2 с ці клітинки «дорогі»). Немає де обійти (вузький прохід) — шлях однаково
 *    через неї (черга біля входу — у CowGate).
 * Усі числа зібрані на початку файлу.
 */
public class CowNavigation extends GroundPathNavigation {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    // обходити інших корів (перша версія з перебудовою шляху смикала корову: зіткнень 0–33 проти 0–1; тепер без перебудови)

    public static final boolean AVOID_COWS = true;
    public static final double AHEAD = 2.2;           // дивиться вперед на 2,2 блока (від центру до центру)
    public static final double SIDE_MARGIN = 0.35;    // «на шляху» = збоку ближче половин ширин + 0,35
    public static final int CHECK_EVERY = 2;          // дивиться раз на 2 тіки
    public static final double MOVING = 0.03;         // «іде» = швидкість понад 0,03 блока за тік у той самий бік
    public static final double FOLLOW_SPEED = 0.3;    // іде слідом / чекає — повільно (30%)
    public static final int WAIT_TICKS = 30;          // та стоїть чи йде назустріч — чекає 1,5 с ...
    public static final int SIDESTEP_TICKS = 40;      // ... потім до 2 с відступає вбік (праворуч), обходячи її
    public static final double SIDESTEP_PUSH = 0.03;  // сила кроку вбік (раз на 2 тіки)
    public static final int AVOID_TICKS = 40;         // (не вживається, поки обхід без перебудови шляху)
    public static final float AVOID_MALUS = 6.0F;     // «ціна» клітинки, де стоїть інша корова

    // клітинка (x, z) → до якого тіку її обходити
    final Long2IntOpenHashMap avoid = new Long2IntOpenHashMap();
    private int nextCheck = 0;
    private int waitStart = -1;
    private boolean slowed = false;
    private double goalSpeed = 1.0;

    public CowNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new CowNodeEvaluator(this);
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    int now() {
        return this.mob.tickCount;
    }

    static long key(int x, int z) {
        return BlockPos.asLong(x, 0, z);
    }

    @Override
    public boolean moveTo(Path path, double speed) {
        goalSpeed = speed;
        return super.moveTo(path, slowed ? speed * FOLLOW_SPEED : speed);
    }

    @Override
    public void tick() {
        if (AVOID_COWS && !this.level.isClientSide) avoidCows();
        super.tick();
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
        if (!avoid.isEmpty() && t % 100 == 0) avoid.long2IntEntrySet().removeIf(e -> e.getIntValue() < t);

        Vec3 pos = this.mob.position();
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
        // почекала, а та досі на шляху — крок управо (як люди в коридорі), поки не обійде (щонайбільше SIDESTEP_TICKS)
        if (!waiting && t - waitStart < WAIT_TICKS + SIDESTEP_TICKS) {
            Vec3 right = new Vec3(-dir.z, 0, dir.x);
            this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(right.scale(SIDESTEP_PUSH)));
        }
    }
}
