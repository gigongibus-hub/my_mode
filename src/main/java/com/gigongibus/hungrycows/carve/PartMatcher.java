package com.gigongibus.hungrycows.carve;

import java.util.ArrayList;
import java.util.List;

/**
 * Чи схожий шматок дерева на деталь з креслення — де б його не вирізали і як би не повернули (Fij, 17.11: «готовий предмет
 * повинен випасти сам; дерево лишається — можна зробити багато ручок»). Схожість = спільне / разом, найкраща з 48 поворотів
 * (і віддзеркалень) і зсувів ±1.
 */
public final class PartMatcher {
    private PartMatcher() {
    }

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int SHIFT = 1;            // шукати й зі зсувом на 1 піксель

    private static final int[][] PERMS = { { 0, 1, 2 }, { 0, 2, 1 }, { 1, 0, 2 }, { 1, 2, 0 }, { 2, 0, 1 }, { 2, 1, 0 } };

    /** Клітинки шаблону (x, y, z) */
    public static List<int[]> cells(VoxelMask m) {
        List<int[]> out = new ArrayList<>();
        int n = VoxelMask.N;
        for (int y = 0; y < n; y++) for (int z = 0; z < n; z++) for (int x = 0; x < n; x++) if (m.get(x, y, z)) out.add(new int[]{ x, y, z });
        return out;
    }

    /** Найкраща схожість шматка на шаблон (0…1) */
    public static float best(List<int[]> piece, List<int[]> template) {
        if (piece.isEmpty() || template.isEmpty()) return 0;
        float bound = Math.min(piece.size(), template.size()) / (float) Math.max(piece.size(), template.size());
        if (bound < Carving.MATCH3D_MIN) return bound * 0.5F;   // за розміром уже не схоже — не рахувати
        int s = 2 * VoxelMask.N + 4;
        boolean[] t = new boolean[s * s * s];
        int[] tmin = min(template);
        for (int[] c : template) t[idx(c[0] - tmin[0] + 2, c[1] - tmin[1] + 2, c[2] - tmin[2] + 2, s)] = true;
        float best = 0;
        int[] p = new int[3];
        for (int[] perm : PERMS)
            for (int signs = 0; signs < 8; signs++) {
                List<int[]> rot = new ArrayList<>(piece.size());
                for (int[] c : piece) {
                    for (int a = 0; a < 3; a++) p[a] = ((signs >> a & 1) == 1 ? -1 : 1) * c[perm[a]];
                    rot.add(new int[]{ p[0], p[1], p[2] });
                }
                int[] mn = min(rot);
                for (int dx = -SHIFT; dx <= SHIFT; dx++)
                    for (int dy = -SHIFT; dy <= SHIFT; dy++)
                        for (int dz = -SHIFT; dz <= SHIFT; dz++) {
                            int common = 0;
                            for (int[] c : rot) {
                                int x = c[0] - mn[0] + 2 + dx, y = c[1] - mn[1] + 2 + dy, z = c[2] - mn[2] + 2 + dz;
                                if (x >= 0 && y >= 0 && z >= 0 && x < s && y < s && z < s && t[idx(x, y, z, s)]) common++;
                            }
                            float iou = common / (float) (piece.size() + template.size() - common);
                            if (iou > best) best = iou;
                        }
            }
        return best;
    }

    private static int[] min(List<int[]> l) {
        int[] m = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE };
        for (int[] c : l) for (int a = 0; a < 3; a++) m[a] = Math.min(m[a], c[a]);
        return m;
    }

    private static int idx(int x, int y, int z, int s) {
        return (y * s + z) * s + x;
    }

    /**
     * Де показати наступну деталь (чорним, коли до неї дійдеш): зсув шаблону (як у файлі) так, щоб уся деталь була в
     * дереві, що лишилось; найближче до середини. null — уже не вміщається.
     */
    public static int[] findGuide(VoxelMask wood, VoxelMask keep) {
        List<int[]> t = cells(keep);
        if (t.isEmpty()) return null;
        int n = VoxelMask.N;
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        double tc[] = { 0, 0, 0 };
        for (int[] c : t) for (int a = 0; a < 3; a++) tc[a] += c[a] / (double) t.size();
        for (int ox = -n; ox <= n; ox++)
            for (int oy = -n; oy <= n; oy++)
                for (int oz = -n; oz <= n; oz++) {
                    boolean fits = true;
                    for (int[] c : t) if (!wood.get(c[0] + ox, c[1] + oy, c[2] + oz)) { fits = false; break; }
                    if (!fits) continue;
                    double cx = tc[0] + ox - (n - 1) / 2.0, cy = tc[1] + oy - (n - 1) / 2.0, cz = tc[2] + oz - (n - 1) / 2.0;
                    double d = cx * cx + cy * cy + cz * cz;
                    if (d < bestD) { bestD = d; best = new int[]{ ox, oy, oz }; }
                }
        return best;
    }
}
