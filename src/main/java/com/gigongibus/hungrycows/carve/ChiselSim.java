package com.gigongibus.hungrycows.carve;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ФІЗИКА ЗУБИЛА (Fij: «як зброя в People Playground»). Чиста Java, одиниці — пікселі заготовки (124 × 124), y — донизу.
 * Зубило — тверде тіло (положення, кут, швидкості). Схопив мишею — точку, за яку взяв, тягне до курсора пружина з гасником
 * (spring-damper), тож зубило хитається й крутиться навколо неї; гравітація; коліщатко — крутить. Удар вістрям у дерево —
 * вибиває шматок: що швидше вздовж леза, то глибше. Стінки, дно, бік зубила — відскакує. Відпустив і воно вилетіло за край
 * вікна (ліворуч, праворуч, угору) — «викинув» (Fij: крафт кінчається).
 */
public class ChiselSim {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final float WORLD_W = 372, WORLD_H = 260;        // поле вікна (3 × 124 завширшки), підлога — низ
    public static final float WOOD_X = (WORLD_W - CarveMask.SIZE) / 2, WOOD_Y = WORLD_H - CarveMask.SIZE;   // заготовка лежить на підлозі посередині
    public static final float THROW_MARGIN = 60;                    // вилетіло за край далі цього — викинуто
    // зубило (текстура Fij 124 × 124: лезо x 50–67, вістря y 119, ручка зверху): центр мас і розмір
    public static final float COM_X = 59, COM_Y = 70;
    public static final float HALF_W = 9, TOP = -70, TIP = 49;      // від центру мас: півширина, верх ручки, вістря
    public static final float MASS = 1, INERTIA = MASS * (120 * 120 + 18 * 18) / 12f;
    public static final float GRAVITY = 900;                        // пікс/с²
    public static final float GRAB_K = 900, GRAB_DAMP = 30;         // пружина й гасник «рука → зубило»
    public static final float MAX_GRAB_ACC = 60000;                 // щоб не рвало кудись у безмежність
    public static final float AIR_DRAG = 0.4f, SPIN_DRAG = 1.8f;    // опір повітря (за секунду)
    public static final float HELD_SPIN_DRAG = 1.0f;                // у руці хитається й потроху заспокоюється
    public static final float SCROLL_SPIN = 5f;                     // коліщатко: рад/с за один «клік»
    public static final float CUT_MIN_SPEED = 160;                  // повільніше вздовж леза — лише стукає, не ріже
    public static final float CUT_DEPTH_PER_SPEED = 0.025f;         // глибина зрізу на кожен пікс/с понад поріг
    public static final float CUT_MAX_DEPTH = 14;                   // найглибший шматок за удар
    public static final float CUT_SPEED_LOSS = 0.55f;               // після зрізу лишається 45% швидкості вздовж леза
    public static final float RESTITUTION = 0.25f, FRICTION = 0.35f;// відскок і тертя об дерево/стіл
    public static final float START_X = WORLD_W - 72;                // де лежить зубило, коли відкрив вікно
    public static final int SUBSTEPS = 8;
    public static final float MAX_DT = 1 / 30f;

    public final CarveMask mask;
    public float x, y, angle, vx, vy, omega;    // центр мас (поле), кут (0 — вістрям донизу), швидкості
    private boolean held;
    private float grabU, grabV, mouseX, mouseY;
    public boolean thrown;
    public int carvedPixels;                    // скільки вибито за цей раз (для зносу й тестів)
    public final List<float[]> chips = new ArrayList<>();   // стружка: x, y, vx, vy, життя, відтінок
    private final Random rnd = new Random();
    private final float[][] contour;            // точки обрису (u, v, вістря?)

    public ChiselSim(CarveMask mask) {
        this.mask = mask;
        x = START_X;                // лежить на столі праворуч від заготовки, ручкою назовні
        y = WORLD_H - HALF_W - 0.5f;
        angle = (float) (Math.PI / 2);
        List<float[]> pts = new ArrayList<>();
        for (float u = -HALF_W; u <= HALF_W; u += 2) pts.add(new float[]{ u, TIP, 1 });          // вістря
        for (float v = TOP; v < TIP; v += 4) { pts.add(new float[]{ -HALF_W, v, 0 }); pts.add(new float[]{ HALF_W, v, 0 }); }
        for (float u = -HALF_W; u <= HALF_W; u += 3) pts.add(new float[]{ u, TOP, 0 });
        contour = pts.toArray(new float[0][]);
    }

    // ---------- керування ----------
    /** Чи влучив курсор у зубило (координати поля) */
    public boolean hits(float wx, float wy) {
        float[] l = toLocal(wx, wy);
        return Math.abs(l[0]) <= HALF_W + 2 && l[1] >= TOP - 2 && l[1] <= TIP + 2;
    }

    public void grab(float wx, float wy) {
        float[] l = toLocal(wx, wy);
        grabU = l[0];
        grabV = l[1];
        mouseX = wx;
        mouseY = wy;
        held = true;
    }

    public void drag(float wx, float wy) {
        mouseX = wx;
        mouseY = wy;
    }

    public void release() {
        held = false;
    }

    public boolean held() {
        return held;
    }

    public void scroll(double amount) {
        omega += (float) amount * SCROLL_SPIN;
    }

    // ---------- геометрія ----------
    private float[] toWorld(float u, float v) {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        return new float[]{ x + u * c - v * s, y + u * s + v * c };
    }

    private float[] toLocal(float wx, float wy) {
        float dx = wx - x, dy = wy - y, c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        return new float[]{ dx * c + dy * s, -dx * s + dy * c };
    }

    /** Дерево або стіл у цій точці поля */
    public boolean solid(float wx, float wy) {
        if (wy >= WORLD_H) return true;
        return mask.get((int) Math.floor(wx - WOOD_X), (int) Math.floor(wy - WOOD_Y));
    }

    // ---------- крок ----------
    public void step(float dt) {
        if (thrown) return;
        dt = Math.min(dt, MAX_DT);
        float h = dt / SUBSTEPS;
        for (int i = 0; i < SUBSTEPS; i++) sub(h);
        for (int i = chips.size() - 1; i >= 0; i--) {   // стружка падає й підстрибує
            float[] c = chips.get(i);
            c[3] += GRAVITY * dt;
            c[0] += c[2] * dt;
            c[1] += c[3] * dt;
            if (c[1] >= WORLD_H - 1) { c[1] = WORLD_H - 1; c[3] *= -0.3f; c[2] *= 0.6f; }
            c[4] -= dt;
            if (c[4] <= 0) chips.remove(i);
        }
        if (!held && (x < -THROW_MARGIN || x > WORLD_W + THROW_MARGIN || y < -THROW_MARGIN)) thrown = true;
    }

    private void sub(float h) {
        // рука: пружина з гасником тягне точку хвату до курсора
        if (held) {
            float[] g = toWorld(grabU, grabV);
            float rx = g[0] - x, ry = g[1] - y;
            // пружина з гасником — у точці хвату: точка йде за рукою, а саме зубило вільно гойдається навколо неї (маятник)
            float pvx = vx - omega * ry, pvy = vy + omega * rx;
            float ax = GRAB_K * (mouseX - g[0]) - GRAB_DAMP * pvx, ay = GRAB_K * (mouseY - g[1]) - GRAB_DAMP * pvy;
            float a = (float) Math.hypot(ax, ay);
            if (a > MAX_GRAB_ACC) { ax *= MAX_GRAB_ACC / a; ay *= MAX_GRAB_ACC / a; }
            float inv = 1 / MASS + (rx * rx + ry * ry) / INERTIA;    // «важкість» точки хвату: щоб пружина не розгойдувала
            ax /= inv * MASS;
            ay /= inv * MASS;
            vx += ax * h;
            vy += ay * h;
            omega += (rx * ay - ry * ax) * MASS / INERTIA * h;
            omega *= 1 - HELD_SPIN_DRAG * h;
        }
        vy += GRAVITY * h;
        vx *= 1 - AIR_DRAG * h;
        vy *= 1 - AIR_DRAG * h;
        omega *= 1 - SPIN_DRAG * h;
        x += vx * h;
        y += vy * h;
        angle += omega * h;
        collide();
    }

    private void collide() {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        float axisX = -s, axisY = c;                 // напрям від ручки до вістря
        // 1) вістря в дереві й швидко вздовж леза — зріз
        float bestSpeed = 0;
        for (float[] p : contour) {
            if (p[2] == 0) continue;
            float[] w = toWorld(p[0], p[1]);
            if (!solid(w[0], w[1]) || w[1] >= WORLD_H) continue;
            float rx = w[0] - x, ry = w[1] - y;
            float pvx = vx - omega * ry, pvy = vy + omega * rx;
            bestSpeed = Math.max(bestSpeed, pvx * axisX + pvy * axisY);
        }
        if (bestSpeed > CUT_MIN_SPEED) {
            float depth = Math.min(CUT_MAX_DEPTH, (bestSpeed - CUT_MIN_SPEED) * CUT_DEPTH_PER_SPEED + 1);
            int removed = cut(depth);
            if (removed > 0) {
                float along = vx * axisX + vy * axisY;
                vx -= axisX * along * CUT_SPEED_LOSS;
                vy -= axisY * along * CUT_SPEED_LOSS;
            }
        }
        // 2) будь-яка точка в дереві чи столі — відштовхнути й відскочити
        for (float[] p : contour) {
            float[] w = toWorld(p[0], p[1]);
            if (!solid(w[0], w[1])) continue;
            float nx, ny;
            if (w[1] >= WORLD_H) { nx = 0; ny = -1; }
            else {
                nx = (solid(w[0] - 2, w[1]) ? 1 : 0) - (solid(w[0] + 2, w[1]) ? 1 : 0);
                ny = (solid(w[0], w[1] - 2) ? 1 : 0) - (solid(w[0], w[1] + 2) ? 1 : 0);
                float l = (float) Math.hypot(nx, ny);
                if (l < 0.01f) { nx = 0; ny = -1; } else { nx /= l; ny /= l; }
            }
            float rx = w[0] - x, ry = w[1] - y;
            float pvx = vx - omega * ry, pvy = vy + omega * rx;
            float vn = pvx * nx + pvy * ny;
            if (vn < 0) {
                float rn = rx * ny - ry * nx;
                float j = -(1 + RESTITUTION) * vn / (1 / MASS + rn * rn / INERTIA);
                float tx = -ny, ty = nx, vt = pvx * tx + pvy * ty;
                float jt = -vt * FRICTION / (1 / MASS + (rx * ty - ry * tx) * (rx * ty - ry * tx) / INERTIA);
                vx += (j * nx + jt * tx) / MASS;
                vy += (j * ny + jt * ty) / MASS;
                omega += (rn * j + (rx * ty - ry * tx) * jt) / INERTIA;
            }
            x += nx * 0.5f;      // виштовхнути з дерева
            y += ny * 0.5f;
        }
    }

    /** Вибити прямокутник під вістрям: ширина леза × глибина вздовж леза */
    private int cut(float depth) {
        int removed = 0;
        for (float v = TIP - 1; v <= TIP + depth; v += 0.5f) {
            for (float u = -HALF_W; u <= HALF_W; u += 0.5f) {
                float[] w = toWorld(u, v);
                if (w[1] >= WORLD_H) continue;
                int mx = (int) Math.floor(w[0] - WOOD_X), my = (int) Math.floor(w[1] - WOOD_Y);
                if (mask.clear(mx, my)) {
                    removed++;
                    if (rnd.nextInt(3) == 0 && chips.size() < 400)
                        chips.add(new float[]{ w[0], w[1], (rnd.nextFloat() - 0.5f) * 260, -120 - rnd.nextFloat() * 220, 1.5f + rnd.nextFloat(), rnd.nextFloat() });
                }
            }
        }
        carvedPixels += removed;
        return removed;
    }

    // ---------- для тестів ----------
    public void place(float wx, float wy, float ang, float svx, float svy) {
        x = wx; y = wy; angle = ang; vx = svx; vy = svy; omega = 0;
    }
}
