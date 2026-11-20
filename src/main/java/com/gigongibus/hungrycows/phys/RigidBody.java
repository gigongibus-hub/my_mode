package com.gigongibus.hungrycows.phys;

import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Тверде тіло (Fij, 20.11): набір брусків (`Part`) у своїй системі координат; рухається й обертається як ціле. Маса й момент
 * інерції — з усіх брусків (колода важка, листок легкий). Стан: де початок системи тіла (pos), поворот (q), швидкість центру
 * мас (v) і кутова швидкість (w). Чиста математика — світ і удари рахує `PhysicsWorld`.
 */
public final class RigidBody {
    public final List<Part> parts = new ArrayList<>();
    public final Vector3d pos = new Vector3d();       // початок системи тіла у світі
    public final Quaterniond q = new Quaterniond();
    public final Vector3d v = new Vector3d(), w = new Vector3d();

    public final Vector3d com = new Vector3d();       // центр мас у системі тіла
    public double mass, invMass, radius, leafMass;
    final Matrix3d iLocal = new Matrix3d(), iLocalInv = new Matrix3d();
    final Matrix3d rot = new Matrix3d(), iWorldInv = new Matrix3d();

    // сон: лежить нерухомо — не рахується
    public boolean asleep;
    public int still, asleepTicks;

    // шарнір (дерево хилиться на пні): точка pivot (світ) нерухома, обертання лише довкола axis
    public boolean hinged;
    public final Vector3d pivot = new Vector3d(), anchor = new Vector3d(), axis = new Vector3d(), fallDir = new Vector3d();
    public double hingeRelease;
    double kickOmega;
    int hingeTicks;

    // зв'язки між частинами (де можна зламатися)
    int[] ea = new int[0], eb = new int[0];
    boolean[] weak = new boolean[0];
    int[] owner = new int[0];                           // листок → його колода
    boolean dirty = true;

    public RigidBody() {
    }

    // ---------- маса, інерція, зв'язки ----------

    public void recompute() {
        dirty = false;
        mass = 0;
        leafMass = 0;
        com.zero();
        for (Part p : parts) {
            if (p.gone) continue;
            mass += p.mass;
            if (p.leaf) leafMass += p.mass;
            com.fma(p.mass, p.c);
        }
        if (mass <= 1e-9) { invMass = 0; return; }
        com.div(mass);
        invMass = 1 / mass;
        double xx = 0, yy = 0, zz = 0, xy = 0, xz = 0, yz = 0;
        radius = 0;
        for (Part p : parts) {
            if (p.gone) continue;
            double m = p.mass, dx = p.c.x - com.x, dy = p.c.y - com.y, dz = p.c.z - com.z;
            xx += m / 3 * (p.hy * p.hy + p.hz * p.hz) + m * (dy * dy + dz * dz);
            yy += m / 3 * (p.hx * p.hx + p.hz * p.hz) + m * (dx * dx + dz * dz);
            zz += m / 3 * (p.hx * p.hx + p.hy * p.hy) + m * (dx * dx + dy * dy);
            xy -= m * dx * dy;
            xz -= m * dx * dz;
            yz -= m * dy * dz;
            radius = Math.max(radius, Math.sqrt(dx * dx + dy * dy + dz * dz) + Math.sqrt(p.hx * p.hx + p.hy * p.hy + p.hz * p.hz));
        }
        iLocal.set(xx, xy, xz, xy, yy, yz, xz, yz, zz);
        iLocalInv.set(iLocal).invert();
        buildSamples();
        buildGraph();
        updateWorld();
    }

    /** Точки дотику кожного бруска: кути, середини ребер і граней, крім тих, що всередині сусідніх брусків */
    private void buildSamples() {
        for (Part p : parts) {
            if (p.gone || p.leaf) { p.samples = null; continue; }
            double[] tmp = new double[26 * 3];
            int n = 0;
            for (int i = -1; i <= 1; i++)
                for (int j = -1; j <= 1; j++)
                    for (int k = -1; k <= 1; k++) {
                        if (i == 0 && j == 0 && k == 0) continue;
                        double x = p.c.x + i * p.hx, y = p.c.y + j * p.hy, z = p.c.z + k * p.hz;
                        boolean inside = false;
                        for (Part o : parts) if (o != p && !o.gone && !o.leaf && o.contains(x, y, z, 0.01)) { inside = true; break; }
                        if (inside) continue;
                        tmp[n++] = x;
                        tmp[n++] = y;
                        tmp[n++] = z;
                    }
            p.samples = java.util.Arrays.copyOf(tmp, n);
        }
    }

    /** Хто з ким з'єднаний: гранню — міцно, ребром чи кутом (гілка навскіс) — слабко; листок належить найближчій колоді */
    private void buildGraph() {
        List<int[]> edges = new ArrayList<>();
        List<Boolean> wk = new ArrayList<>();
        int n = parts.size();
        for (int i = 0; i < n; i++) {
            Part a = parts.get(i);
            if (a.gone || a.leaf) continue;
            for (int j = i + 1; j < n; j++) {
                Part b = parts.get(j);
                if (b.gone || b.leaf) continue;
                int overlap = 0, touch = 0;
                boolean apart = false;
                for (int ax = 0; ax < 3; ax++) {
                    double gap = Math.abs(a.c(ax) - b.c(ax)) - a.h(ax) - b.h(ax);
                    if (gap > 0.02) { apart = true; break; }
                    if (gap < -0.02) overlap++; else touch++;
                }
                if (apart) continue;
                edges.add(new int[]{ i, j });
                wk.add(overlap < 2);
            }
        }
        ea = new int[edges.size()];
        eb = new int[edges.size()];
        weak = new boolean[edges.size()];
        for (int k = 0; k < edges.size(); k++) { ea[k] = edges.get(k)[0]; eb[k] = edges.get(k)[1]; weak[k] = wk.get(k); }
        owner = new int[n];
        for (int i = 0; i < n; i++) {
            owner[i] = i;
            Part p = parts.get(i);
            if (!p.leaf || p.gone) continue;
            double best = Double.MAX_VALUE;
            for (int j = 0; j < n; j++) {
                Part o = parts.get(j);
                if (o.leaf || o.gone) continue;
                double d = o.c.distanceSquared(p.c);
                if (d < best) { best = d; owner[i] = j; }
            }
        }
    }

    // ---------- перетворення ----------

    public void updateWorld() {
        rot.set(q);
        Matrix3d t = new Matrix3d(rot).transpose();
        iWorldInv.set(rot).mul(iLocalInv).mul(t);
    }

    public Vector3d comWorld(Vector3d out) {
        return rot.transform(out.set(com)).add(pos);
    }

    public Vector3d toWorld(double x, double y, double z, Vector3d out) {
        return rot.transform(out.set(x, y, z)).add(pos);
    }

    public Vector3d toLocal(Vector3d world, Vector3d out) {
        out.set(world).sub(pos);
        return rot.transformTranspose(out);
    }

    public Vector3d velAt(Vector3d world, Vector3d comW, Vector3d out) {
        double rx = world.x - comW.x, ry = world.y - comW.y, rz = world.z - comW.z;
        return out.set(v.x + w.y * rz - w.z * ry, v.y + w.z * rx - w.x * rz, v.z + w.x * ry - w.y * rx);
    }

    /** Поштовх J у точці (світ) */
    public void impulse(Vector3d j, Vector3d point, Vector3d comW) {
        if (invMass == 0) return;
        v.fma(invMass, j);
        Vector3d r = new Vector3d(point).sub(comW);
        Vector3d t = r.cross(j);
        iWorldInv.transform(t);
        w.add(t);
    }

    /** Наскільки важко штовхнути точку (r від центру мас) уздовж n: 1/m + (r×n)·I⁻¹(r×n) */
    public double invMassAlong(Vector3d r, Vector3d n) {
        if (invMass == 0) return 0;
        Vector3d rn = new Vector3d(r).cross(n);
        Vector3d t = iWorldInv.transform(new Vector3d(rn));
        return invMass + rn.dot(t);
    }

    /** Крок руху: центр мас за швидкістю, поворот за кутовою швидкістю */
    public void integrate(double dt) {
        Vector3d cw = comWorld(new Vector3d());
        cw.fma(dt, v);
        Quaterniond dq = new Quaterniond(w.x * dt * 0.5, w.y * dt * 0.5, w.z * dt * 0.5, 0).mul(q);
        q.add(dq).normalize();
        updateWorld();
        Vector3d rc = rot.transform(new Vector3d(com));
        pos.set(cw).sub(rc);
    }

    /** Межі тіла у світі */
    public double[] aabb() {
        double[] b = { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE };
        Vector3d c = new Vector3d();
        for (Part p : parts) {
            if (p.gone) continue;
            toWorld(p.c.x, p.c.y, p.c.z, c);
            for (int a = 0; a < 3; a++) {
                double e = Math.abs(rot.get(0, a)) * p.hx + Math.abs(rot.get(1, a)) * p.hy + Math.abs(rot.get(2, a)) * p.hz;
                double cc = a == 0 ? c.x : a == 1 ? c.y : c.z;
                b[a] = Math.min(b[a], cc - e);
                b[a + 3] = Math.max(b[a + 3], cc + e);
            }
        }
        return b;
    }

    public int alive() {
        int n = 0;
        for (Part p : parts) if (!p.gone) n++;
        return n;
    }

    public boolean onlyLeaves() {
        for (Part p : parts) if (!p.gone && !p.leaf) return false;
        return true;
    }

    /** Відділити частини idx у нове тіло (та сама система координат, ті самі швидкості точок) */
    public RigidBody split(BitSet idx) {
        Vector3d oldCom = comWorld(new Vector3d());
        RigidBody b = new RigidBody();
        for (int i = idx.nextSetBit(0); i >= 0; i = idx.nextSetBit(i + 1)) {
            Part p = parts.get(i);
            if (p.gone) continue;
            b.parts.add(p.copy());
            p.gone = true;
        }
        b.pos.set(pos);
        b.q.set(q);
        b.w.set(w);
        b.recompute();
        Vector3d nc = b.comWorld(new Vector3d());
        b.v.set(v).add(new Vector3d(w).cross(new Vector3d(nc).sub(oldCom)));
        recompute();
        Vector3d mc = comWorld(new Vector3d());
        v.add(new Vector3d(w).cross(new Vector3d(mc).sub(oldCom)));
        return b;
    }
}
