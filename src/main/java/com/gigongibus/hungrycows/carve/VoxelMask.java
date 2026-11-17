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
     * Fij (17.11: кружка посередині колоди): після удару лишається шматок, у якому найбільше деталі з креслення (keep, осіла
     * на sink), а без креслення чи порівну — найбільший; решта відпадає. Повертає клітинки, що відпали (x, y, z).
     */
    public List<int[]> dropLoose() {
        return dropLoose(null, 0);
    }

    public List<int[]> dropLoose(VoxelMask keep, int sink) {
        int[] comp = new int[N * N * N];
        java.util.Arrays.fill(comp, -1);
        int[][] dirs = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        List<Integer> sizes = new ArrayList<>();
        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
            if (comp[i] >= 0) continue;
            int id = sizes.size(), size = 0, inKeep = 0;
            ArrayDeque<Integer> q = new ArrayDeque<>();
            q.add(i);
            comp[i] = id;
            while (!q.isEmpty()) {
                int c = q.poll();
                size++;
                int x = c % N, z = c / N % N, y = c / (N * N);
                if (keep != null && keep.get(x, y + sink, z)) inKeep++;
                for (int[] d : dirs) {
                    int xx = x + d[0], yy = y + d[1], zz = z + d[2];
                    if (!get(xx, yy, zz)) continue;
                    int j = idx(xx, yy, zz);
                    if (comp[j] < 0) { comp[j] = id; q.add(j); }
                }
            }
            sizes.add(inKeep * (N * N * N + 1) + size);    // спершу деталь, потім розмір
        }
        int best = 0;
        for (int k = 1; k < sizes.size(); k++) if (sizes.get(k) > sizes.get(best)) best = k;
        List<int[]> out = new ArrayList<>();
        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1))
            if (comp[i] != best) out.add(new int[]{ i % N, i / (N * N), i / N % N });
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

    /** Схожість на шаблон, якщо шматок осів на sink шарів (шаблон теж «опускається») */
    public float match(VoxelMask keep, int sink) {
        int and = 0, or = 0;
        for (int y = 0; y < N; y++)
            for (int z = 0; z < N; z++)
                for (int x = 0; x < N; x++) {
                    boolean a = get(x, y, z), k = keep.get(x, y + sink, z);
                    if (a && k) and++;
                    if (a || k) or++;
                }
        // частина шаблону нижче sink (уже «під столом») — теж рахується як невирізана
        for (int y = 0; y < Math.min(sink, N); y++)
            for (int z = 0; z < N; z++)
                for (int x = 0; x < N; x++) if (keep.get(x, y, z)) or++;
        return or == 0 ? 0 : and / (float) or;
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
