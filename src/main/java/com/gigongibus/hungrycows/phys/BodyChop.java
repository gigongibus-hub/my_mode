package com.gigongibus.hungrycows.phys;

import com.gigongibus.hungrycows.chop.Chopping;
import com.gigongibus.hungrycows.chop.WoodPiece;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * РОЗРУБКА ФІЗИЧНОГО ДЕРЕВА (Fij, 21.11: «фізичне дерево не можна розрубити»; «рубається фізичним»). Та сама розрубка, що й на
 * колоді (`Chopping.chopPieces`: шкала, рамка, засічка, рівно/засильно), але колода чи шматок — частина тіла, повернута як завгодно:
 * рамка й осі — у системі тіла. Після удару тіло складається наново: розрубану частину замінюють дві половинки (застрягла — та
 * сама з засічкою), і що вже ні з чим не з'єднане, стає окремим тілом (розрубав стовбур упоперек — дві частини дерева; розколов
 * окрему колоду вздовж — дві половинки). Половинки трохи розходяться.
 */
public final class BodyChop {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double APART_SPEED = 0.25;     // відрубане відходить на 0,25 бл./с …
    public static final double APART_PER_POWER = 0.35; // … + 0,35 за повну шкалу (важке — повільніше: швидкість ÷ √маси)

    private BodyChop() {}

    /** Частину тіла можна рубати: колода (не листя) чи шматок */
    public static WoodPiece pieceOf(Part p) {
        if (p == null || p.gone || p.leaf) return null;
        if (p.kind == Part.PIECE) return p.piece;
        return p.state == null ? null : Chopping.pieceOfLog(p.state);
    }

    /** Нижній кут клітинки (у системі тіла), у якій лежить шматок частини (пікселі шматка — від нього) */
    public static Vector3d cellOf(Part p) {
        WoodPiece w = pieceOf(p);
        if (w == null || p.kind == Part.BLOCK) return new Vector3d(p.c).sub(0.5, 0.5, 0.5);
        return new Vector3d(p.c).sub((w.x + w.sx / 2.0) / 16, (w.y + w.sy / 2.0) / 16, (w.z + w.sz / 2.0) / 16);
    }

    /**
     * Куди дивиться гравець на тілі: {частина, грань (Direction у системі тіла), відстань ×1000 (бл.)} або null. Промінь — у систему тіла.
     */
    public static int[] pick(RigidBody b, Vec3 eye, Vec3 look, double reach) {
        Vector3d o = b.toLocal(new Vector3d(eye.x, eye.y, eye.z), new Vector3d());
        Vector3d d = b.rot.transformTranspose(new Vector3d(look.x, look.y, look.z));
        double best = reach;
        int[] res = null;
        for (int i = 0; i < b.parts.size(); i++) {
            Part p = b.parts.get(i);
            WoodPiece w = pieceOf(p);
            if (w == null) continue;
            Vector3d cell = cellOf(p);
            double[] mn = { cell.x + w.x / 16.0, cell.y + w.y / 16.0, cell.z + w.z / 16.0 };
            double[] mx = { mn[0] + w.sx / 16.0, mn[1] + w.sy / 16.0, mn[2] + w.sz / 16.0 };
            double[] oo = { o.x, o.y, o.z }, dd = { d.x, d.y, d.z };
            double tmin = 0, tmax = reach;
            int axis = -1;
            boolean ok = true;
            for (int a = 0; a < 3 && ok; a++) {
                if (Math.abs(dd[a]) < 1e-9) { if (oo[a] < mn[a] || oo[a] > mx[a]) ok = false; continue; }
                double t1 = (mn[a] - oo[a]) / dd[a], t2 = (mx[a] - oo[a]) / dd[a];
                if (t1 > t2) { double t = t1; t1 = t2; t2 = t; }
                if (t1 > tmin) { tmin = t1; axis = a; }
                tmax = Math.min(tmax, t2);
                if (tmin > tmax) ok = false;
            }
            if (!ok || axis < 0 || tmin >= best) continue;
            best = tmin;
            Direction face = Direction.fromAxisAndDirection(Direction.Axis.values()[axis],
                    dd[axis] > 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
            res = new int[]{ i, face.get3DDataValue(), (int) (tmin * 1000) };
        }
        return res;
    }

    /** Удар гравця по частині тіла (з пакета) */
    public static Chopping.Result chop(ServerLevel level, Player player, WoodBodyEntity e, int index, Direction.Axis axis, int at, float power) {
        if (e == null || e.isRemoved() || !Chopping.holdsAxe(player) || player.isShiftKeyDown()) return Chopping.Result.NONE;
        RigidBody b = e.body;
        if (index < 0 || index >= b.parts.size()) return Chopping.Result.NONE;
        Part target = b.parts.get(index);
        WoodPiece w = pieceOf(target);
        if (w == null) return Chopping.Result.NONE;
        Vector3d cell = cellOf(target);
        Vector3d mid = b.toWorld(cell.x + (w.x + w.sx / 2.0) / 16, cell.y + (w.y + w.sy / 2.0) / 16, cell.z + (w.z + w.sz / 2.0) / 16, new Vector3d());
        if (player.distanceToSqr(mid.x, mid.y, mid.z) > Chopping.REACH * Chopping.REACH) return Chopping.Result.NONE;
        power = Math.max(0, Math.min(1, power));
        List<WoodPiece> work = new ArrayList<>();
        work.add(w.copy());
        Chopping.Result r = Chopping.chopPieces(work, 0, axis, at, power, level.random);
        if (r == Chopping.Result.NONE) return r;
        rebuild(level, e, index, work, cell, axis, power, r == Chopping.Result.HARD);
        Chopping.effects(level, new Vec3(mid.x, mid.y, mid.z), w.wood, r, player);
        return r;
    }

    /**
     * Тіло наново: частина index → шматки work (у тій самій клітинці); що розпалось — окремими тілами. Старе тіло зникає (гравцям
     * нове приходить звичайною появою — так усі бачать нові частини).
     */
    static void rebuild(ServerLevel level, WoodBodyEntity e, int index, List<WoodPiece> work, Vector3d cell, Direction.Axis axis,
                        float power, boolean hard) {
        RigidBody old = e.body;
        old.updateWorld();
        if (e.ropeHorse != null) Rope.untie(e, null);                         // мотузку перерубано
        Vector3d oldCom = old.comWorld(new Vector3d());
        // усі частини підряд; половинки — в кінці (їхні номери знаємо)
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i < old.parts.size(); i++) if (i != index && !old.parts.get(i).gone) parts.add(old.parts.get(i).copy());
        int first = parts.size();
        for (WoodPiece wp : work) parts.add(Part.piece(wp, cell, PhysicsWorld.LOG_MASS));
        // хто з ким тримається: тимчасове тіло з усіма частинами (граф стиків), без стику між самими половинками
        RigidBody all = new RigidBody();
        all.parts.addAll(parts);
        all.pos.set(old.pos);
        all.q.set(old.q);
        all.recompute();
        int n = parts.size();
        int[] comp = new int[n];
        for (int i = 0; i < n; i++) comp[i] = i;
        for (int k = 0; k < all.ea.length; k++) {
            int a = all.ea[k], c = all.eb[k];
            if (a >= first && c >= first) continue;                          // розрубані половинки одна одну не тримають
            union(comp, a, c);
        }
        for (int i = 0; i < n; i++) if (parts.get(i).leaf && all.owner.length > i && all.owner[i] != i) union(comp, i, all.owner[i]);
        // групи → тіла
        java.util.Map<Integer, List<Integer>> groups = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) groups.computeIfAbsent(find(comp, i), k -> new ArrayList<>()).add(i);
        Vector3d axisW = old.rot.transform(new Vector3d(axis == Direction.Axis.X ? 1 : 0, axis == Direction.Axis.Y ? 1 : 0, axis == Direction.Axis.Z ? 1 : 0));
        Vector3d cutW = old.toWorld(cell.x + 0.5, cell.y + 0.5, cell.z + 0.5, new Vector3d());
        boolean apart = groups.size() > 1;
        for (List<Integer> g : groups.values()) {
            boolean solid = false;
            for (int i : g) if (!parts.get(i).leaf) { solid = true; break; }
            if (!solid) continue;                                                // саме листя — осипалось
            RigidBody nb = new RigidBody();
            boolean logs = false;
            for (int i : g) {
                Part p = parts.get(i);
                nb.parts.add(p);
                if (p.kind == Part.BLOCK && !p.leaf) logs = true;
            }
            nb.pos.set(old.pos);
            nb.q.set(old.q);
            nb.w.set(old.w);
            nb.recompute();
            Vector3d nc = nb.comWorld(new Vector3d());
            nb.v.set(old.v).add(new Vector3d(old.w).cross(new Vector3d(nc).sub(oldCom)));
            if (apart) {                                                       // відходить від площини розрубу
                double side = Math.signum(new Vector3d(nc).sub(cutW).dot(axisW));
                double speed = (APART_SPEED + APART_PER_POWER * power) * (hard ? Chopping.SPLIT_HARD : 1) / Math.sqrt(Math.max(1, nb.mass));
                nb.v.fma(side * speed, axisW);
                nb.v.y += Chopping.SPLIT_UP * 0.5;
            }
            WoodBodyEntity ne = new WoodBodyEntity(level, nb, logs ? WoodBodyEntity.TREE : WoodBodyEntity.PIECE);
            level.addFreshEntity(ne);
        }
        e.discard();
    }

    private static int find(int[] c, int i) {
        while (c[i] != i) { c[i] = c[c[i]]; i = c[i]; }
        return i;
    }

    private static void union(int[] c, int a, int b) {
        c[find(c, a)] = find(c, b);
    }
}
