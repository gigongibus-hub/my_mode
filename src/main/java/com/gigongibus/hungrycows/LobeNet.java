package com.gigongibus.hungrycows;

import net.minecraft.nbt.CompoundTag;

import java.util.Random;

/**
 * Мозок із часток (план Fij, етап 8): ~500 нейронів, з'єднаних лише з потрібними (кілька тисяч зв'язків, не «кожен з кожним»).
 * Шлях: чуття (зір, нюх, слух, смак і дотик), тіло, пам'ять → увага та емоції → рішення → дії.
 * Які нейрони з якими з'єднані — від «зерна» корови (зберігається), тож зберігаються лише ваги (~20 КБ).
 * Гени зору, нюху й слуху трохи змінюють розмір цих часток (±20%), решту бере частка «рішення» — разом завжди 500.
 */
public class LobeNet {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public enum Lobe {
        VISION("зір", 60), SMELL("нюх", 40), HEARING("слух", 30), TASTE_TOUCH("смак і дотик", 30), BODY("тіло", 40),
        MEMORY("пам'ять", 60), ATTENTION("увага", 40), EMOTION("емоції", 30), DECISION("рішення", 149), ACTION("дії", 21);

        public final String title;
        public final int base;

        Lobe(String title, int base) {
            this.title = title;
            this.base = base;
        }
    }

    public static final int TOTAL = 500;
    public static final double GENE_SIZE = 0.2;     // ген чуття 0…100 → частка −20%…+20%
    public static final int K_SENSE = 6;            // нейрон чуття, тіла й пам'яті бере 6 входів зі своєї групи
    public static final int K_MEMORY_BODY = 2;      // ... пам'ять ще 2 з тіла
    public static final int K_ATTENTION = 8;        // увага — 8 нейронів чуттів
    public static final int K_EMOTION = 8;          // емоції — 8 з тіла, уваги, пам'яті
    public static final int K_DECISION = 12;        // рішення — 12 з уваги, емоцій, пам'яті, тіла
    public static final int K_ACTION = 24;          // дія — 24 нейрони рішення
    public static final double INIT_HIDDEN = 0.5;   // початкові ваги прихованих часток (сигнал іде від народження)
    public static final double INIT_ACTION = 0.05;  // дій — малі: спершу корова діє за інстинктом, досвід додається за життя
    public static final float WEIGHT_LIMIT = 3.0F;

    // групи входів (індекси у векторі відчуттів) для часток чуттів, тіла й пам'яті
    public record Groups(int[] vision, int[] smell, int[] hearing, int[] tasteTouch, int[] body, int[] memory) {
    }

    private final long seed;
    private final int[] start = new int[Lobe.values().length];
    private final int[] size = new int[Lobe.values().length];
    private final int n;
    private final int[][] src;        // джерела нейрона: ≥ 0 — нейрон, < 0 — вхід −(i+1)
    private float[][] w;
    private float[] bias;
    private final double[] act;
    private final int actions;

    public LobeNet(long seed, double visionGene, double smellGene, double hearingGene, Groups g, int actions) {
        this.seed = seed;
        this.actions = actions;
        for (Lobe l : Lobe.values()) size[l.ordinal()] = l.base;
        size[Lobe.VISION.ordinal()] = scaled(Lobe.VISION.base, visionGene);
        size[Lobe.SMELL.ordinal()] = scaled(Lobe.SMELL.base, smellGene);
        size[Lobe.HEARING.ordinal()] = scaled(Lobe.HEARING.base, hearingGene);
        size[Lobe.ACTION.ordinal()] = actions;
        int others = 0;
        for (Lobe l : Lobe.values()) if (l != Lobe.DECISION) others += size[l.ordinal()];
        size[Lobe.DECISION.ordinal()] = TOTAL - others;
        int s = 0;
        for (Lobe l : Lobe.values()) { start[l.ordinal()] = s; s += size[l.ordinal()]; }
        n = s;
        act = new double[n];
        src = new int[n][];
        w = new float[n][];
        bias = new float[n];
        Random r = new Random(seed);
        for (Lobe l : Lobe.values()) {
            for (int i = start[l.ordinal()]; i < start[l.ordinal()] + size[l.ordinal()]; i++) {
                src[i] = switch (l) {
                    case VISION -> inputs(r, g.vision(), K_SENSE);
                    case SMELL -> inputs(r, g.smell(), K_SENSE);
                    case HEARING -> inputs(r, g.hearing(), K_SENSE);
                    case TASTE_TOUCH -> inputs(r, g.tasteTouch(), K_SENSE);
                    case BODY -> inputs(r, g.body(), K_SENSE);
                    case MEMORY -> concat(inputs(r, g.memory(), K_SENSE), inputs(r, g.body(), K_MEMORY_BODY));
                    case ATTENTION -> neurons(r, K_ATTENTION, Lobe.VISION, Lobe.SMELL, Lobe.HEARING, Lobe.TASTE_TOUCH);
                    case EMOTION -> neurons(r, K_EMOTION, Lobe.BODY, Lobe.ATTENTION, Lobe.MEMORY);
                    case DECISION -> neurons(r, K_DECISION, Lobe.ATTENTION, Lobe.EMOTION, Lobe.MEMORY, Lobe.BODY);
                    case ACTION -> neurons(r, K_ACTION, Lobe.DECISION);
                };
                double init = l == Lobe.ACTION ? INIT_ACTION : INIT_HIDDEN;
                w[i] = new float[src[i].length];
                for (int k = 0; k < w[i].length; k++) w[i][k] = (float) (r.nextGaussian() * init);
            }
        }
    }

    private static int scaled(int base, double gene) {
        return (int) Math.round(base * (1.0 + GENE_SIZE * (gene - 50) / 50.0));
    }

    private static int[] inputs(Random r, int[] group, int k) {
        int[] out = new int[k];
        for (int i = 0; i < k; i++) out[i] = -(group[r.nextInt(group.length)] + 1);
        return out;
    }

    private static int[] concat(int[] a, int[] b) {
        int[] out = new int[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private int[] neurons(Random r, int k, Lobe... from) {
        int total = 0;
        for (Lobe l : from) total += size[l.ordinal()];
        int[] out = new int[k];
        for (int i = 0; i < k; i++) {
            int pick = r.nextInt(total);
            for (Lobe l : from) {
                if (pick < size[l.ordinal()]) { out[i] = start[l.ordinal()] + pick; break; }
                pick -= size[l.ordinal()];
            }
        }
        return out;
    }

    // Прохід від відчуттів до дій; повертає вихід кожної дії (лінійний)
    public double[] forward(double[] x) {
        double[] out = new double[actions];
        int actStart = start[Lobe.ACTION.ordinal()];
        for (int i = 0; i < n; i++) {
            double s = bias[i];
            int[] si = src[i];
            float[] wi = w[i];
            for (int k = 0; k < si.length; k++) s += wi[k] * (si[k] >= 0 ? act[si[k]] : x[-si[k] - 1]);
            act[i] = i >= actStart ? s : Math.tanh(s);
        }
        System.arraycopy(act, actStart, out, 0, actions);
        return out;
    }

    // Навчання: g[k] — у який бік штовхнути вихід дії k. Міняються ваги дій і нейронів рішення, що їх живлять
    public void train(double[] x, double[] g) {
        forward(x);
        int actStart = start[Lobe.ACTION.ordinal()];
        double[] d = new double[n];
        for (int k = 0; k < actions; k++) {
            if (g[k] == 0) continue;
            int i = actStart + k;
            for (int j = 0; j < src[i].length; j++) {
                int s = src[i][j];
                if (s >= 0) d[s] += g[k] * w[i][j];
                w[i][j] = clamp(w[i][j] + g[k] * value(s, x));
            }
            bias[i] = clamp(bias[i] + g[k]);
        }
        int ds = start[Lobe.DECISION.ordinal()], de = ds + size[Lobe.DECISION.ordinal()];
        for (int i = ds; i < de; i++) {
            if (d[i] == 0) continue;
            double dz = d[i] * (1 - act[i] * act[i]);
            for (int j = 0; j < src[i].length; j++) w[i][j] = clamp(w[i][j] + dz * value(src[i][j], x));
            bias[i] = clamp(bias[i] + dz);
        }
    }

    private double value(int s, double[] x) {
        return s >= 0 ? act[s] : x[-s - 1];
    }

    private static float clamp(double v) {
        return (float) Math.max(-WEIGHT_LIMIT, Math.min(WEIGHT_LIMIT, v));
    }

    // Забування: непідтверджене слабшає (лише дії й рішення — їх і вчимо)
    public void decay(double k) {
        int from = start[Lobe.DECISION.ordinal()];
        for (int i = from; i < n; i++) for (int j = 0; j < w[i].length; j++) w[i][j] *= (float) (1 - k);
    }

    public int size(Lobe l) {
        return size[l.ordinal()];
    }

    public int connections() {
        int c = 0;
        for (int[] s : src) c += s.length;
        return c;
    }

    // Наскільки «збуджена» частка зараз (середнє |активність|), для книги
    public double activity(Lobe l) {
        double s = 0;
        for (int i = start[l.ordinal()]; i < start[l.ordinal()] + size[l.ordinal()]; i++) s += Math.abs(act[i]);
        return size[l.ordinal()] == 0 ? 0 : s / size[l.ordinal()];
    }

    public CompoundTag save() {
        CompoundTag c = new CompoundTag();
        c.putLong("Seed", seed);
        c.putIntArray("Sizes", size);
        int total = 0;
        for (float[] wi : w) total += wi.length;
        int[] all = new int[total];
        int p = 0;
        for (float[] wi : w) for (float v : wi) all[p++] = Float.floatToIntBits(v);
        c.putIntArray("W", all);
        int[] b = new int[n];
        for (int i = 0; i < n; i++) b[i] = Float.floatToIntBits(bias[i]);
        c.putIntArray("B", b);
        return c;
    }

    // Ваги зі збереження (лише якщо той самий устрій мережі)
    public boolean load(CompoundTag c) {
        if (c.getLong("Seed") != seed || !java.util.Arrays.equals(c.getIntArray("Sizes"), size)) return false;
        int[] all = c.getIntArray("W"), b = c.getIntArray("B");
        if (all.length != connections() || b.length != n) return false;
        int p = 0;
        for (float[] wi : w) for (int j = 0; j < wi.length; j++) wi[j] = Float.intBitsToFloat(all[p++]);
        for (int i = 0; i < n; i++) bias[i] = Float.intBitsToFloat(b[i]);
        return true;
    }

    public long seed() {
        return seed;
    }
}
