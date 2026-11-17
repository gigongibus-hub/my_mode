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

    /**
     * Шматки, що вже ні до чого не тримаються (не з'єднані гранями з нижнім шаром, що лежить на кресленні), відпадають.
     * Повертає їхні клітинки (x, y, z).
     */
    public List<int[]> dropLoose() {
        BitSet seen = new BitSet(N * N * N);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int z = 0; z < N; z++)
            for (int x = 0; x < N; x++)
                if (get(x, 0, z)) { seen.set(idx(x, 0, z)); q.add(new int[]{ x, 0, z }); }
        int[][] dirs = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : dirs) {
                int x = c[0] + d[0], y = c[1] + d[1], z = c[2] + d[2];
                if (get(x, y, z) && !seen.get(idx(x, y, z))) { seen.set(idx(x, y, z)); q.add(new int[]{ x, y, z }); }
            }
        }
        List<int[]> out = new ArrayList<>();
        for (int y = 0; y < N; y++)
            for (int z = 0; z < N; z++)
                for (int x = 0; x < N; x++)
                    if (get(x, y, z) && !seen.get(idx(x, y, z))) { bits.clear(idx(x, y, z)); out.add(new int[]{ x, y, z }); }
        return out;
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
