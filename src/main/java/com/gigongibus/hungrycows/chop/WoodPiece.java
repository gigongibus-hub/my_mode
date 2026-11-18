package com.gigongibus.hungrycows.chop;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/**
 * Шматок колоди (Fij, 18.11: «розрубка дерева»): брусок у пікселях (1/16 блока) у купі — де лежить (x, y, z), розміри
 * (sx, sy, sz) уздовж осей світу, куди йдуть волокна (grain) і яка кожна грань: кора, розкол (свіже дерево вздовж волокон)
 * чи торець (кільця). Ділиться площиною навпіл (чи як ляже лінія): обидві частини лежать на тому самому місці.
 */
public final class WoodPiece {
    public static final byte BARK = 0, SPLIT = 1, END = 2;
    public static final int FULL = 16;

    public int x, y, z, sx, sy, sz;
    public Direction.Axis grain;
    /** вид кожної грані за `Direction.get3DDataValue()`: DOWN, UP, NORTH, SOUTH, WEST, EAST */
    public final byte[] faces = new byte[6];
    /** засічка: скільки «сили» вже вклали в розруб, що застряг (і де саме) — наступний удар туди ж доповнює */
    public float notch;
    public int notchAxis = -1, notchAt = -1;

    public WoodPiece(int x, int y, int z, int sx, int sy, int sz, Direction.Axis grain) {
        this.x = x; this.y = y; this.z = z;
        this.sx = sx; this.sy = sy; this.sz = sz;
        this.grain = grain;
    }

    /** Ціла колода (блок) з волокнами вздовж axis; bark — кора на боках, stripped — обтесана, wood — кора всюди */
    public static WoodPiece whole(Direction.Axis axis, boolean bark, boolean allSides) {
        WoodPiece p = new WoodPiece(0, 0, 0, FULL, FULL, FULL, axis);
        for (Direction d : Direction.values()) {
            byte side = bark ? BARK : SPLIT;
            p.faces[d.get3DDataValue()] = d.getAxis() == axis && !allSides ? END : side;
        }
        return p;
    }

    public int size(Direction.Axis a) {
        return a == Direction.Axis.X ? sx : a == Direction.Axis.Y ? sy : sz;
    }

    public int pos(Direction.Axis a) {
        return a == Direction.Axis.X ? x : a == Direction.Axis.Y ? y : z;
    }

    private void set(Direction.Axis a, int pos, int size) {
        switch (a) {
            case X -> { x = pos; sx = size; }
            case Y -> { y = pos; sy = size; }
            default -> { z = pos; sz = size; }
        }
    }

    public int volume() {
        return sx * sy * sz;
    }

    /** Частка цілої колоди (1 — ціла, ½ — половинка, …) */
    public float fraction() {
        return volume() / (float) (FULL * FULL * FULL);
    }

    public WoodPiece copy() {
        WoodPiece p = new WoodPiece(x, y, z, sx, sy, sz, grain);
        System.arraycopy(faces, 0, p.faces, 0, 6);
        p.notch = notch; p.notchAxis = notchAxis; p.notchAt = notchAt;
        return p;
    }

    /** Площа розрубу площиною, перпендикулярною до axis (пікс.²) */
    public int cutArea(Direction.Axis axis) {
        int a = 1;
        for (Direction.Axis o : Direction.Axis.values()) if (o != axis) a *= size(o);
        return a;
    }

    /**
     * Розрубати площиною, перпендикулярною до axis, на відстані at від початку шматка (1…розмір−1); lost — скільки пікселів
     * з кожного боку розрубу відлітає трісками (кривий удар). Повертає дві частини (або null, якщо не ділиться). Нова грань —
     * торець, якщо рубали впоперек волокон, інакше розкол.
     */
    public WoodPiece[] split(Direction.Axis axis, int at, int lost) {
        int n = size(axis), p0 = pos(axis);
        if (at < 1 || at > n - 1) return null;
        int la = Math.max(1, at - lost), lb = Math.max(1, n - at - lost);
        WoodPiece a = copy(), b = copy();
        a.set(axis, p0, la);
        b.set(axis, p0 + n - lb, lb);
        byte cut = axis == grain ? END : SPLIT;
        Direction pos = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        a.faces[pos.get3DDataValue()] = cut;
        b.faces[pos.getOpposite().get3DDataValue()] = cut;
        a.notch = b.notch = 0;
        a.notchAxis = b.notchAxis = a.notchAt = b.notchAt = -1;
        return new WoodPiece[]{ a, b };
    }

    /**
     * Повернути шматок (для купи): perm — котра вісь світу стає новою x, y, z (перестановка 0/1/2). Грані переносяться разом з
     * осями. Положення лишається 0, 0, 0 — купа сама кладе.
     */
    public WoodPiece reoriented(int[] perm) {
        Direction.Axis[] ax = Direction.Axis.values();
        int[] s = { sx, sy, sz };
        WoodPiece p = new WoodPiece(0, 0, 0, s[perm[0]], s[perm[1]], s[perm[2]], ax[indexOf(perm, grain.ordinal())]);
        for (int newAxis = 0; newAxis < 3; newAxis++) {
            Direction.Axis from = ax[perm[newAxis]], to = ax[newAxis];
            for (Direction.AxisDirection sign : Direction.AxisDirection.values())
                p.faces[Direction.fromAxisAndDirection(to, sign).get3DDataValue()] = faces[Direction.fromAxisAndDirection(from, sign).get3DDataValue()];
        }
        return p;
    }

    private static int indexOf(int[] perm, int v) {
        for (int i = 0; i < 3; i++) if (perm[i] == v) return i;
        return 0;
    }

    public boolean contains(double px, double py, double pz, double eps) {
        return px >= x - eps && px <= x + sx + eps && py >= y - eps && py <= y + sy + eps && pz >= z - eps && pz <= z + sz + eps;
    }

    // ---------- збереження (і в предметі) ----------
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putIntArray("Box", new int[]{ x, y, z, sx, sy, sz });
        t.putString("Grain", grain.getName());
        t.putByteArray("Faces", faces.clone());
        if (notch > 0) { t.putFloat("Notch", notch); t.putInt("NotchAxis", notchAxis); t.putInt("NotchAt", notchAt); }
        return t;
    }

    public static WoodPiece load(CompoundTag t) {
        int[] b = t.getIntArray("Box");
        if (b.length < 6) b = new int[]{ 0, 0, 0, FULL, FULL, FULL };
        Direction.Axis g = Direction.Axis.byName(t.getString("Grain"));
        WoodPiece p = new WoodPiece(b[0], b[1], b[2], b[3], b[4], b[5], g == null ? Direction.Axis.Y : g);
        byte[] f = t.getByteArray("Faces");
        if (f.length == 6) System.arraycopy(f, 0, p.faces, 0, 6);
        p.notch = t.getFloat("Notch");
        p.notchAxis = t.contains("NotchAxis") ? t.getInt("NotchAxis") : -1;
        p.notchAt = t.contains("NotchAt") ? t.getInt("NotchAt") : -1;
        return p;
    }

    /** Те саме, що лежить у предметі: без місця в купі й засічки (щоб однакові шматки складались у стос) */
    public CompoundTag itemTag() {
        WoodPiece p = copy();
        p.x = p.y = p.z = 0;
        p.notch = 0; p.notchAxis = p.notchAt = -1;
        return p.save();
    }
}
