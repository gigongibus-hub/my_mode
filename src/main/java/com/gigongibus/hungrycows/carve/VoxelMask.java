package com.gigongibus.hungrycows.carve;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * СПРАВЖНЯ РІЗЬБА (Fij, 17.11): колода на верстаку — N × N × N «пікселів» (1 = 1/16 блока). Біт = ще є дерево.
 * Осі: x — уздовж волокон (колода лежить уздовж x), y — угору (y 0 лежить на кресленні), z — упоперек.
 */
public final class VoxelMask {
    public static final int N = 12;
    private final BitSet bits;

    public VoxelMask() {
        bits = new BitSet(N * N * N);
        bits.set(0, N * N * N);
    }

    private VoxelMask(BitSet b) {
        bits = b;
    }

    public static VoxelMask empty() {
        return new VoxelMask(new BitSet(N * N * N));
    }

    private static int idx(int x, int y, int z) {
        return (y * N + z) * N + x;
    }

    public static boolean in(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < N && y < N && z < N;
    }

    public boolean get(int x, int y, int z) {
        return in(x, y, z) && bits.get(idx(x, y, z));
    }

    public void set(int x, int y, int z) {
        if (in(x, y, z)) bits.set(idx(x, y, z));
    }

    /** Прибрати; true — там було дерево */
    public boolean clear(int x, int y, int z) {
        if (!get(x, y, z)) return false;
        bits.clear(idx(x, y, z));
        return true;
    }

    public int count() {
        return bits.cardinality();
    }

    public boolean untouched() {
        return count() == N * N * N;
    }

    public VoxelMask copy() {
        return new VoxelMask((BitSet) bits.clone());
    }

    /** Схожість на шаблон: спільне / разом */
    public float match(VoxelMask keep) {
        BitSet and = (BitSet) bits.clone();
        and.and(keep.bits);
        BitSet or = (BitSet) bits.clone();
        or.or(keep.bits);
        int u = or.cardinality();
        return u == 0 ? 0 : and.cardinality() / (float) u;
    }

    /** Шматки дерева, з'єднані гранями (клітинки x, y, z) */
    public List<List<int[]>> components() {
        int[] comp = new int[N * N * N];
        java.util.Arrays.fill(comp, -1);
        int[][] dirs = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        List<List<int[]>> out = new ArrayList<>();
        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
            if (comp[i] >= 0) continue;
            List<int[]> cells = new ArrayList<>();
            ArrayDeque<Integer> q = new ArrayDeque<>();
            q.add(i);
            comp[i] = out.size();
            while (!q.isEmpty()) {
                int c = q.poll();
                int x = c % N, z = c / N % N, y = c / (N * N);
                cells.add(new int[]{ x, y, z });
                for (int[] d : dirs) {
                    int xx = x + d[0], yy = y + d[1], zz = z + d[2];
                    if (!get(xx, yy, zz)) continue;
                    int j = idx(xx, yy, zz);
                    if (comp[j] < 0) { comp[j] = out.size(); q.add(j); }
                }
            }
            out.add(cells);
        }
        return out;
    }

    /** Лишається найбільший шматок, решта відпадає; повертає клітинки, що відпали */
    public List<int[]> dropLoose() {
        List<List<int[]>> comps = components();
        List<int[]> out = new ArrayList<>();
        int best = 0;
        for (int k = 1; k < comps.size(); k++) if (comps.get(k).size() > comps.get(best).size()) best = k;
        for (int k = 0; k < comps.size(); k++) if (k != best) out.addAll(comps.get(k));
        for (int[] c : out) bits.clear(idx(c[0], c[1], c[2]));
        return out;
    }

    /** Найнижчий шар, де ще є дерево (N — порожньо) */
    public int lowest() {
        for (int y = 0; y < N; y++)
            for (int z = 0; z < N; z++)
                for (int x = 0; x < N; x++) if (get(x, y, z)) return y;
        return N;
    }

    /** Шматок, що вже не лежить на кресленні, сідає на нього: зсув униз на d шарів */
    public void shiftDown(int d) {
        if (d <= 0) return;
        BitSet nb = new BitSet(N * N * N);
        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
            int x = i % N, z = i / N % N, y = i / (N * N);
            if (y - d >= 0) nb.set(idx(x, y - d, z));
        }
        bits.clear();
        bits.or(nb);
    }

    public byte[] toBytes() {
        byte[] b = new byte[N * N * N / 8];
        byte[] raw = bits.toByteArray();
        System.arraycopy(raw, 0, b, 0, Math.min(raw.length, b.length));
        return b;
    }

    public static VoxelMask fromBytes(byte[] b) {
        return new VoxelMask(BitSet.valueOf(b));
    }
}
