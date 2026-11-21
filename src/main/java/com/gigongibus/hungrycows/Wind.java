package com.gigongibus.hungrycows;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Вітер (план Fij, етап 2): один на світ. Напрям змінюється раз на 3–6 хв (повертає до 90°), іноді штиль.
 * Нюх корови залежить від нього: запах, який вітер несе до корови, — до 48 блоків, без вітру — 16, проти вітру — 4.
 * Не зберігається: після перезапуску гри вітер новий.
 */
public final class Wind {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int CHANGE_MIN = 3600, CHANGE_MAX = 7200;   // змінюється раз на 3–6 хв (тіки)
    public static final double CALM_CHANCE = 0.2;                   // 20% — штиль
    public static final double TURN_MAX = 90;                       // за раз повертає щонайбільше на 90°
    public static final double RANGE_WITH = 48;                     // запах за вітром (вітер несе до корови), блоків
    public static final double RANGE_CALM = 16;                     // без вітру
    public static final double RANGE_AGAINST = 4;                   // проти вітру

    private static final Map<Level, Wind> WINDS = new WeakHashMap<>();

    private double angle;        // куди дме, градуси (0 = на південь, +z; 90 = на захід, −x — як поворот у грі)
    private boolean calm;
    private long nextChange = -1;
    private boolean fixed = false;   // задано вручну (тест, команда) — не змінюється сам

    private Wind() {
    }

    public static Wind of(Level level) {
        Wind w = WINDS.computeIfAbsent(level, l -> new Wind());
        w.update(level);
        return w;
    }

    private void update(Level level) {
        long now = level.getGameTime();
        if (fixed && nextChange >= 0) return;
        if (nextChange >= 0 && now < nextChange) return;
        RandomSource r = level.getRandom();
        if (nextChange < 0) angle = r.nextDouble() * 360;
        else angle = (angle + (r.nextDouble() * 2 - 1) * TURN_MAX + 360) % 360;
        calm = r.nextDouble() < CALM_CHANCE;
        nextChange = now + CHANGE_MIN + r.nextInt(CHANGE_MAX - CHANGE_MIN + 1);
    }

    // Задати вітер вручну (тест): angle — куди дме; calm — штиль
    public static void set(Level level, double angle, boolean calm) {
        Wind w = WINDS.computeIfAbsent(level, l -> new Wind());
        w.angle = angle;
        w.calm = calm;
        w.fixed = true;
        w.nextChange = level.getGameTime();
    }

    public boolean calm() {
        return calm;
    }

    // Куди дме (одиничний вектор по горизонталі)
    public Vec3 direction() {
        double rad = Math.toRadians(angle);
        return new Vec3(-Math.sin(rad), 0, Math.cos(rad));
    }

    // Як далеко корова (nose) чує запах від source: за вітром 48, штиль 16, проти вітру 4 (і плавно між ними)
    public double range(Vec3 source, Vec3 nose) {
        if (calm) return RANGE_CALM;
        Vec3 d = new Vec3(nose.x - source.x, 0, nose.z - source.z);
        if (d.lengthSqr() < 1e-6) return RANGE_WITH;
        double cos = d.normalize().dot(direction());   // 1 — вітер несе від джерела прямо до корови
        return cos >= 0 ? RANGE_CALM + (RANGE_WITH - RANGE_CALM) * cos : RANGE_CALM - (RANGE_CALM - RANGE_AGAINST) * -cos;
    }

    public static double range(Level level, Vec3 source, Vec3 nose) {
        return of(level).range(source, nose);
    }

    // «Вітер з півночі» тощо: звідки дме. Куди дме 0° (на південь) = з півночі; 90° (на захід) = зі сходу
    public String describe() {
        if (calm) return "штиль";
        String[] from = { "з півночі", "з північного сходу", "зі сходу", "з південного сходу", "з півдня", "з південного заходу", "із заходу", "з північного заходу" };
        int i = (int) Math.round((((angle % 360) + 360) % 360) / 45.0) % 8;
        return "вітер " + from[i];
    }
}
