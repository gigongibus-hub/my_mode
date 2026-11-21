package com.gigongibus.hungrycows.phys;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * ТОЧНЕ ЗІТКНЕННЯ з фізичними тілами (Fij, 21.11: «як у Create Aeronautics»; «гравця смикає, коли стоїть на дереві»).
 * Раніше колода була обкладена прямими рамками-латками (гра вміє лише прямі): навскіс — «сходинки», рамки щотіку переставлялись,
 * і гравця смикало. Тепер (як Valkyrien Skies, на якому стоїть Create Aeronautics) крок істоти (`Entity.collide`, через mixin)
 * перевіряється з кожною колодою як із ПОВЕРНУТИМ бруском — за 15 осями (рамка істоти, брусок і їхні ребра; теорема про
 * розділову вісь), з часом удару на шляху. Поверхня, що дивиться вгору (до ~50°), — підлога: по ній ходять і стоять, не
 * ковзаючи, і на похилу колоду можна вийти; крутіша — стіна (ковзає вздовж), на невисоку стіну — крок угору, як на блок.
 * Хто стоїть на тілі, що рухається, їде разом з ним (точка під ногами в системі тіла).
 */
public final class BodyCollision {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double FLOOR_NY = 0.64;       // нормаль крутіше вгору (кут до ~50°) — підлога, інакше стіна
    public static final double TOUCH = 0.01;          // таке занурення — «торкається» (не виштовхує)
    public static final double PUSH_OUT_MAX = 0.6;    // застряг глибше (тіло наїхало) — виштовхує не більше 0,6 бл. за тік
    public static final double UP_PREFER = 0.5;       // виштовхує охочіше вгору (на колоду), ніж убік
    public static final int PASSES = 4;               // скільки разів «ковзати» за крок
    public static final double CARRY_MAX = 1.5;       // тіло зсунулось під ногами більше — не везти (злетів, телепорт)
    public static Entity DEBUG = null;   // налагодження: про цю істоту — кожен крок у журнал
    public static final double RIDE_GAP = 0.12;       // ноги вище бруска на стільки — ще «стоїть на ньому»

    private static final Map<Level, Set<WoodBodyEntity>> BODIES = new WeakHashMap<>();
    private static final Map<Entity, Ride> RIDES = Collections.synchronizedMap(new WeakHashMap<>());

    /** На чому стоїть істота: тіло й точка під ногами в системі тіла */
    private record Ride(WoodBodyEntity body, double lx, double ly, double lz) {}

    private BodyCollision() {}

    static synchronized void add(WoodBodyEntity e) {
        BODIES.computeIfAbsent(e.level(), l -> Collections.newSetFromMap(new WeakHashMap<>())).add(e);
    }

    static synchronized void remove(WoodBodyEntity e) {
        Set<WoodBodyEntity> s = BODIES.get(e.level());
        if (s != null) s.remove(e);
    }

    /** Тіла, чиї межі перетинають box */
    public static synchronized List<WoodBodyEntity> near(Level level, AABB box) {
        Set<WoodBodyEntity> s = BODIES.get(level);
        if (s == null || s.isEmpty()) return List.of();
        List<WoodBodyEntity> out = new ArrayList<>();
        for (WoodBodyEntity e : s) {
            if (e.isRemoved() || e.body.parts.isEmpty()) continue;
            double[] b = e.body.aabb();
            if (b[0] > box.maxX || b[3] < box.minX || b[1] > box.maxY || b[4] < box.minY || b[2] > box.maxZ || b[5] < box.minZ) continue;
            out.add(e);
        }
        return out;
    }

    /** Чи це тіло взагалі зачіпає таких, як e (сам із собою, глядач) */
    private static boolean skip(Entity e) {
        return e instanceof WoodBodyEntity || e.noPhysics || e instanceof Player p && p.isSpectator()
                || !e.level().isClientSide && e instanceof net.minecraft.server.level.ServerPlayer;   // гравця рахує його гра (сервер вірить)
    }

    // ---------- 1) перед кроком: тіло під ногами зсунулось — везти ----------

    public static Vec3 carry(Entity e, Vec3 move) {
        if (skip(e)) return move;
        Ride r = RIDES.get(e);
        if (r == null) return move;
        WoodBodyEntity b = r.body;
        if (b.isRemoved() || b.level() != e.level()) { RIDES.remove(e); return move; }
        Vector3d w = b.body.toWorld(r.lx, r.ly, r.lz, new Vector3d());
        double dx = w.x - e.getX(), dy = w.y - e.getY(), dz = w.z - e.getZ();
        if (dx * dx + dy * dy + dz * dz > CARRY_MAX * CARRY_MAX) { RIDES.remove(e); return move; }
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) < 1e-5) return move;
        if (DEBUG == e) org.slf4j.LoggerFactory.getLogger("BodyCollision").info("везе {} {} {}", dx, dy, dz);
        return move.add(dx, dy, dz);
    }

    // ---------- 2) після ванільного кроку (блоки): ще й тіла ----------

    /** Один брусок у світі: центр, осі, пів розмірів */
    private static final class Box {
        final double cx, cy, cz;
        final double[][] ax = new double[3][3];
        final double[] h = new double[3];
        final WoodBodyEntity owner;
        final int part;

        Box(WoodBodyEntity owner, int part, Part p) {
            this.owner = owner;
            this.part = part;
            RigidBody b = owner.body;
            Vector3d c = b.toWorld(p.c.x, p.c.y, p.c.z, new Vector3d());
            cx = c.x; cy = c.y; cz = c.z;
            for (int i = 0; i < 3; i++) for (int k = 0; k < 3; k++) ax[i][k] = b.rot.get(i, k);   // стовпчик i — вісь бруска i у світі
            h[0] = p.hx; h[1] = p.hy; h[2] = p.hz;
        }

        double radius(double nx, double ny, double nz) {
            double r = 0;
            for (int i = 0; i < 3; i++) r += h[i] * Math.abs(ax[i][0] * nx + ax[i][1] * ny + ax[i][2] * nz);
            return r;
        }
    }

    /** Результат перевірки руху рамки проти бруска */
    private static final class Hit {
        double t = Double.MAX_VALUE;      // коли вдарився (0…1); < 0 — уже всередині
        double nx, ny, nz;                // нормаль від бруска до рамки
        double depth;                     // всередині: глибина по нормалі
        Box box;
    }

    private static final double[][] WORLD = { { 1, 0, 0 }, { 0, 1, 0 }, { 0, 0, 1 } };

    /**
     * Рамка з центром (px,py,pz), пів розмірів (ex,ey,ez) рухається на d. Розділові осі: 3 світові, 3 бруска, 9 ребро×ребро.
     * Повертає false, якщо не торкнеться. hit — найпізніший «вхід» (час удару) і його вісь; уже всередині — вісь найменшого занурення.
     */
    private static boolean sweep(double px, double py, double pz, double ex, double ey, double ez, double dx, double dy, double dz, Box b, Hit out) {
        double enter = -Double.MAX_VALUE, exit = Double.MAX_VALUE;
        double enx = 0, eny = 0, enz = 0;
        double minPen = Double.MAX_VALUE, pnx = 0, pny = 0, pnz = 0;
        double sx = px - b.cx, sy = py - b.cy, sz = pz - b.cz;
        for (int k = 0; k < 15; k++) {
            double nx, ny, nz;
            if (k < 3) { nx = WORLD[k][0]; ny = WORLD[k][1]; nz = WORLD[k][2]; }
            else if (k < 6) { double[] a = b.ax[k - 3]; nx = a[0]; ny = a[1]; nz = a[2]; }
            else {
                double[] u = WORLD[(k - 6) / 3], a = b.ax[(k - 6) % 3];
                nx = u[1] * a[2] - u[2] * a[1]; ny = u[2] * a[0] - u[0] * a[2]; nz = u[0] * a[1] - u[1] * a[0];
                double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (l < 1e-4) continue;                         // ребра паралельні — вісь уже є серед граней
                nx /= l; ny /= l; nz /= l;
            }
            double rr = ex * Math.abs(nx) + ey * Math.abs(ny) + ez * Math.abs(nz) + b.radius(nx, ny, nz);
            double s = sx * nx + sy * ny + sz * nz, v = dx * nx + dy * ny + dz * nz;
            double sg = s >= 0 ? 1 : -1;
            double pen = rr - Math.abs(s);                      // зараз заходить на стільки (від'ємне — розділені)
            if (pen < minPen) { minPen = pen; pnx = nx * sg; pny = ny * sg; pnz = nz * sg; }
            if (Math.abs(v) < 1e-9) {
                if (pen <= 0) return false;                     // розділені й не зближуються
                continue;
            }
            double t1 = (-rr - s) / v, t2 = (rr - s) / v;
            double tin = Math.min(t1, t2), tout = Math.max(t1, t2);
            if (tin > enter) {
                enter = tin;
                // нормаль — бік, з якого рамка заходить (проти руху)
                double ns = v > 0 ? -1 : 1;
                enx = nx * ns; eny = ny * ns; enz = nz * ns;
            }
            if (tout < exit) exit = tout;
            if (enter > exit || enter >= 1 || exit <= 0) return false;
        }
        if (minPen > TOUCH) {                                   // уже всередині (тіло наїхало)
            out.t = -1;
            out.depth = minPen;
            out.nx = pnx; out.ny = pny; out.nz = pnz;
            out.box = b;
            return true;
        }
        // уже торкається: справжня нормаль — вісь найменшого занурення (а не та, по якій «зайшла» колись давно);
        // іде геть чи вздовж неї — не заважає
        if (enter <= 0) {
            if (dx * pnx + dy * pny + dz * pnz >= -1e-9) return false;
            enx = pnx; eny = pny; enz = pnz;
        }
        out.t = Math.max(0, enter);
        out.nx = enx; out.ny = eny; out.nz = enz;
        out.box = b;
        return true;
    }

    /** Усі тверді бруски (колоди, шматки; листя — ні) поблизу рамки */
    private static List<Box> boxes(Level level, AABB area) {
        List<Box> out = new ArrayList<>();
        for (WoodBodyEntity e : near(level, area)) {
            RigidBody b = e.body;
            for (int i = 0; i < b.parts.size(); i++) {
                Part p = b.parts.get(i);
                if (p.gone || p.leaf) continue;
                Box bx = new Box(e, i, p);
                double r = bx.radius(1, 0, 0), ry = bx.radius(0, 1, 0), rz = bx.radius(0, 0, 1);
                if (bx.cx + r < area.minX || bx.cx - r > area.maxX || bx.cy + ry < area.minY || bx.cy - ry > area.maxY
                        || bx.cz + rz < area.minZ || bx.cz - rz > area.maxZ) continue;
                out.add(bx);
            }
        }
        return out;
    }

    /** Крок рамки з ковзанням: повертає зсув; ground — на чому став (або null) */
    private static double[] slide(AABB box, double dx, double dy, double dz, List<Box> boxes, Box[] ground, boolean[] wall) {
        double px = (box.minX + box.maxX) / 2, py = (box.minY + box.maxY) / 2, pz = (box.minZ + box.maxZ) / 2;
        double ex = box.getXsize() / 2, ey = box.getYsize() / 2, ez = box.getZsize() / 2;
        double ox = px, oy = py, oz = pz;
        Hit hit = new Hit(), best = new Hit();
        for (int pass = 0; pass < PASSES; pass++) {
            best.t = Double.MAX_VALUE;
            best.box = null;
            for (Box b : boxes) {
                hit.box = null;
                if (!sweep(px, py, pz, ex, ey, ez, dx, dy, dz, b, hit)) continue;
                boolean better = hit.t < best.t || hit.t == best.t && hit.t < 0 && hit.depth > best.depth;
                if (better) { best.t = hit.t; best.nx = hit.nx; best.ny = hit.ny; best.nz = hit.nz; best.depth = hit.depth; best.box = hit.box; }
            }
            if (best.box == null) { px += dx; py += dy; pz += dz; break; }
            double nx = best.nx, ny = best.ny, nz = best.nz;
            if (best.t < 0) {
                // усередині: виштовхнути найкоротше (угору — охочіше) і далі як звичайний дотик
                double up = pushUp(px, py, pz, ex, ey, ez, best.box);
                double d = Math.min(PUSH_OUT_MAX, best.depth + 0.001);
                if (up >= 0 && up * UP_PREFER <= best.depth) { py += Math.min(PUSH_OUT_MAX, up + 0.001); nx = 0; ny = 1; nz = 0; }
                else { px += nx * d; py += ny * d; pz += nz * d; }
            } else {
                px += dx * best.t; py += dy * best.t; pz += dz * best.t;
                double rem = 1 - best.t;
                dx *= rem; dy *= rem; dz *= rem;
            }
            double into = dx * nx + dy * ny + dz * nz;
            if (ny >= FLOOR_NY) {
                ground[0] = best.box;
                // підлога: іде як ішов, лише висота — уздовж поверхні (не ковзає вниз, на похилу виходить)
                if (into < 0) dy = -(dx * nx + dz * nz) / ny;
            } else {
                if (Math.abs(ny) < FLOOR_NY) wall[0] = true;
                if (into < 0) { dx -= into * nx; dy -= into * ny; dz -= into * nz; }
            }
            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) < 1e-7) break;
            if (pass == PASSES - 1) { dx = dy = dz = 0; }
        }
        return new double[]{ px - ox, py - oy, pz - oz };
    }

    /** На скільки підняти рамку, щоб вона стала НА брусок (−1 — не вийде: брусок навскіс угору ширший) */
    private static double pushUp(double px, double py, double pz, double ex, double ey, double ez, Box b) {
        // найвища точка бруска під рамкою: шукаємо по вертикалі розділення за 15 осями — двійковим пошуком висоти
        double lo = 0, hi = 2.5;
        Hit h = new Hit();
        if (sweep(px, py + hi, pz, ex, ey, ez, 0, 0, 0, b, h) && h.t < 0) return -1;
        for (int i = 0; i < 14; i++) {
            double mid = (lo + hi) / 2;
            h.box = null;
            if (sweep(px, py + mid, pz, ex, ey, ez, 0, 0, 0, b, h) && h.t < 0) lo = mid; else hi = mid;
        }
        return hi;
    }

    /**
     * Крок істоти e: ванільний зсув (уже з блоками) — ще й з тілами. Хто став на тіло — запам'ятати, щоб везти.
     */
    public static Vec3 collide(Entity e, Vec3 move) {
        if (skip(e)) return move;
        AABB box = e.getBoundingBox();
        AABB area = box.expandTowards(move).inflate(1.0);
        List<Box> boxes = boxes(e.level(), area);
        if (boxes.isEmpty()) { RIDES.remove(e); return move; }
        Box[] ground = new Box[1];
        boolean[] wall = new boolean[1];
        double[] r = slide(box, move.x, move.y, move.z, boxes, ground, wall);
        // крок угору на невисоку стіну (як на блок): з землі, рух убік уперся
        double step = e.maxUpStep();
        if (wall[0] && step > 0 && (e.onGround() || ground[0] != null) && move.x * move.x + move.z * move.z > 1e-6) {
            Box[] g2 = new Box[1];
            boolean[] w2 = new boolean[1];
            double[] up = slide(box, 0, step, 0, boxes, new Box[1], new boolean[1]);
            AABB raised = box.move(up[0], up[1], up[2]);
            if (e.level().noCollision(e, raised)) {
                double[] side = slide(raised, move.x, 0, move.z, boxes, new Box[1], w2);
                AABB moved = raised.move(side[0], side[1], side[2]);
                double[] down = slide(moved, 0, -(up[1] + 0.05) + Math.min(0, move.y), 0, boxes, g2, new boolean[1]);
                double sx = up[0] + side[0] + down[0], sz = up[2] + side[2] + down[2];
                if (sx * sx + sz * sz > r[0] * r[0] + r[2] * r[2] + 1e-6 && g2[0] != null) {
                    r = new double[]{ sx, up[1] + side[1] + down[1], sz };
                    ground[0] = g2[0];
                }
            }
        }
        // тіла не змінили складову — повернути її точно ту саму: гра порівнює «==», і похибка в 15-му знаку вважалась ударом
        // об підлогу (гравець на краю колоди не падав, а повільно сповзав униз, «стоячи»)
        Vec3 out = new Vec3(same(r[0], move.x), same(r[1], move.y), same(r[2], move.z));
        if (out.x == move.x && out.y == move.y && out.z == move.z) out = move;
        // що тіла додали (виштовхнули вгору, підняли по схилу) — ще раз перевірити з блоками
        if (out != move) out = Entity.collideBoundingBox(e, out, box, e.level(), List.of());
        remember(e, box.move(out), ground[0]);
        if (DEBUG == e)
            org.slf4j.LoggerFactory.getLogger("BodyCollision").info("крок {} → {} (тіла {}) земля {} стіна {}", move, out, new Vec3(r[0], r[1], r[2]), ground[0] != null, wall[0]);
        return out;
    }

    private static double same(double got, double was) {
        return Math.abs(got - was) < 1e-7 ? was : got;
    }

    /** Запам'ятати, на якому тілі й де стоїть (точку під ногами — у системі тіла) */
    private static void remember(Entity e, AABB after, Box ground) {
        if (ground == null) { RIDES.remove(e); return; }
        double fx = (after.minX + after.maxX) / 2, fy = after.minY, fz = (after.minZ + after.maxZ) / 2;
        Vector3d l = ground.owner.body.toLocal(new Vector3d(fx, fy, fz), new Vector3d());
        RIDES.put(e, new Ride(ground.owner, l.x, l.y, l.z));
    }

    /** Сервер: гравець стоїть на тілі чи поруч (не «літає») */
    public static boolean nearBody(Entity e) {
        AABB box = e.getBoundingBox().inflate(0.0625).expandTowards(0, -0.55, 0);
        return !boxes(e.level(), box).isEmpty();
    }

    /** Для тестів і сервера: чи стоїть істота на тілі (останній крок) */
    public static WoodBodyEntity standsOn(Entity e) {
        Ride r = RIDES.get(e);
        return r == null ? null : r.body;
    }

    /** Рамка перетинає тверду частину тіла глибше depth? (на сервері: штовхнути легке тіло) */
    static boolean overlaps(AABB box, WoodBodyEntity owner, Part p, double depth) {
        Box b = new Box(owner, 0, p);
        Hit h = new Hit();
        double px = (box.minX + box.maxX) / 2, py = (box.minY + box.maxY) / 2, pz = (box.minZ + box.maxZ) / 2;
        if (!sweep(px, py, pz, box.getXsize() / 2, box.getYsize() / 2, box.getZsize() / 2, 0, 0, 0, b, h)) return false;
        return h.t < 0 ? h.depth > depth : depth <= TOUCH;
    }

    /** Найвища точка твердих частин тіла під рамкою (для сервера: «стоїть зверху»), NaN — нічого */
    static double topUnder(AABB box, WoodBodyEntity owner) {
        double best = Double.NaN;
        double px = (box.minX + box.maxX) / 2, py = box.minY + box.getYsize() / 2, pz = (box.minZ + box.maxZ) / 2;
        for (Part p : owner.body.parts) {
            if (p.gone || p.leaf) continue;
            Box b = new Box(owner, 0, p);
            double up = pushUp(px, py - 1.5, pz, box.getXsize() / 2, box.getYsize() / 2, box.getZsize() / 2, b);
            if (up > 0) best = Double.isNaN(best) ? box.minY - 1.5 + up : Math.max(best, box.minY - 1.5 + up);
        }
        return best;
    }
}
