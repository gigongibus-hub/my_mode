package com.gigongibus.hungrycows.carve;

import java.util.BitSet;

/**
 * Що лишилось від заготовки: сітка SIZE × SIZE, біт = «дерево є». Чиста Java (без Майнкрафта) — щоб рахувати й на сервері,
 * і в тестах. x — праворуч, y — донизу.
 */
public final class CarveMask {
    public static final int SIZE = 124;        // Fij: заготовка 124 × 124 пікселі
    private final BitSet solid;

    public CarveMask() {
        solid = new BitSet(SIZE * SIZE);
        solid.set(0, SIZE * SIZE);
    }

    private CarveMask(BitSet b) {
        solid = b;
    }

    public static CarveMask fromBytes(byte[] b) {
        if (b == null || b.length == 0) return new CarveMask();
        BitSet s = BitSet.valueOf(b);
        if (s.length() > SIZE * SIZE) s.clear(SIZE * SIZE, s.length());
        return new CarveMask(s);
    }

    public byte[] toBytes() {
        return solid.toByteArray();
    }

    public CarveMask copy() {
        return new CarveMask((BitSet) solid.clone());
    }

    public boolean get(int x, int y) {
        return x >= 0 && y >= 0 && x < SIZE && y < SIZE && solid.get(y * SIZE + x);
    }

    /** Вибити піксель. true — там було дерево */
    public boolean clear(int x, int y) {
        if (!get(x, y)) return false;
        solid.clear(y * SIZE + x);
        return true;
    }

    public int solidCount() {
        return solid.cardinality();
    }

    public boolean untouched() {
        return solidCount() == SIZE * SIZE;
    }

    /** Лише вибивати, не «доростити»: лишається те, що є і тут, і в тому, що прислав гравець */
    public void keepOnly(CarveMask other) {
        solid.and(other.solid);
    }
}
