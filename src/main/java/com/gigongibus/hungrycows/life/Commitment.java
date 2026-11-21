package com.gigongibus.hungrycows.life;

/**
 * ПРИХИЛЬНІСТЬ ДО СПРАВИ (Fij, «жвавість»): мозок видає бажання (шанси дій), а цей клас вирішує, що тварина РОБИТЬ.
 * Раніше мозок кожні 0,5 с кидав кубик і після 5 с міг одразу перескочити на іншу дію, хоча нічого не змінилось
 * («телепортація наміру»). Тепер:
 * - поточна дія має прихильність (0…1,5): вона росте, поки дія ще найкраща, і повільно згасає, коли ні;
 * - інша дія набирає очки (накопичувач), лише коли її бажання стабільно вище за поточне з урахуванням прихильності;
 * - перемикання — коли накопичувач кандидата перевищив поріг; близькі бажання — тварина «вагається» (hesitating).
 * Рефлекси (страх тощо) і неможлива дія — перемикання одразу. Клас не знає, яка це тварина: лише числа дій.
 */
public final class Commitment {
    // ================= ТАБЛИЦЯ ЧИСЕЛ (на одне рішення мозку, тобто раз на 0,5 с) =================
    public static final double COMMIT_START = 1.0;        // нова дія починає з прихильністю 1
    public static final double COMMIT_MAX = 1.5;          // більше не росте
    public static final double COMMIT_GAIN = 0.05;        // поки дія найкраща (або майже) — +0,05 за рішення
    public static final double COMMIT_DECAY = 0.08;       // бажання її вже не підтримує — −0,08 (від 1 до 0 за ~6 с)
    public static final double COMMIT_WEIGHT = 0.10;      // прихильність 1 = +10 відсоткових пунктів до шансу поточної дії
    public static final double ACCUMULATOR_GAIN = 5.0;    // кандидат: + 5 × (наскільки він кращий), напр. на 0,2 → +1
    public static final double ACCUMULATOR_DECAY = 0.12;  // кандидат не кращий — накопичене −12% за рішення
    public static final double SAMPLE_GAIN = 0.4;         // мозок «випадково подумав» про дію (кубик) — їй очки (так лишається різноманітність)
    public static final double SWITCH_THRESHOLD = 1.6;    // перемкнутись, коли кандидат набрав 1,6 (сильна перевага — за 0,5–1 с, слабка — за кілька с)
    public static final int BORED_TICKS = 400;            // одна справа довше 20 с — «набридає»: прихильність згасає й тоді, коли дія ще найкраща ...
    public static final double BORED_DECAY = 0.06;        // ... на 0,06 за рішення (з першою версією корова за хвилину не змінила дію жодного разу)
    public static final double SPONTANEOUS_BELOW = 0.3;   // прихильність згасла нижче 0,3 — випадкова думка мозку (кубик) одразу стає справою:
                                                          // так лишаються рідкісні дії (вилизати подругу, почухатися, помукати), але не кожні 5 с
    public static final int MIN_HOLD_TICKS = 40;          // перші 2 с нову дію не міняє ніщо, крім рефлексу (було 5 с жорстко)
    public static final double SUPPORT_GAP = 0.05;        // поточна дія «ще підтримана», якщо гірша за найкращу не більш ніж на 5 пунктів
    public static final double HESITATE_GAP = 0.08;       // кандидат майже такий самий (до 8 пунктів) ...
    public static final double HESITATE_ACC = 0.5;        // ... або вже набрав чверть порогу — тварина вагається

    private int current;
    private double commitment = COMMIT_START;
    private final double[] acc;
    private int heldTicks;
    private boolean hesitating;
    private int rival = -1;

    public Commitment(int actions, int start) {
        this.acc = new double[actions];
        this.current = start;
    }

    public int current() { return current; }
    public double commitment() { return commitment; }
    public boolean hesitating() { return hesitating; }
    /** Дія, яка зараз «тягне» найсильніше (або -1) */
    public int rival() { return rival; }
    public double accumulator(int i) { return acc[i]; }
    public int heldTicks() { return heldTicks; }

    /** Щотіку: скільки поточна дія триває */
    public void tick() {
        heldTicks++;
    }

    /**
     * Одне рішення. probs — шанси дій від мозку (бажання), possible — що можна, sampled — дія, яку випав кубик,
     * reflex — дія, на яку треба перейти одразу (або -1). Повертає дію, яку тварина робить далі.
     */
    public int decide(double[] probs, boolean[] possible, int sampled, int reflex) {
        if (reflex >= 0 && reflex != current) return switchTo(reflex);
        if (!possible[current]) return switchTo(sampled >= 0 && possible[sampled] ? sampled : best(probs, possible));
        int top = best(probs, possible);
        // прихильність: росте, поки дія ще (майже) найкраща, інакше згасає
        if (heldTicks > BORED_TICKS) commitment = Math.max(0, commitment - BORED_DECAY);
        else if (probs[current] >= probs[top] - SUPPORT_GAP) commitment = Math.min(COMMIT_MAX, commitment + COMMIT_GAIN);
        else commitment = Math.max(0, commitment - COMMIT_DECAY);
        double hold = probs[current] + COMMIT_WEIGHT * commitment;
        rival = -1;
        double rivalAcc = 0;
        boolean close = false;
        for (int i = 0; i < acc.length; i++) {
            if (i == current || !possible[i]) { acc[i] = 0; continue; }
            double lead = probs[i] - hold;
            if (lead > 0) acc[i] += ACCUMULATOR_GAIN * lead;
            else acc[i] *= 1 - ACCUMULATOR_DECAY;
            if (i == sampled) acc[i] += SAMPLE_GAIN;
            if (Math.abs(probs[i] - probs[current]) < HESITATE_GAP && probs[i] > 0.1) close = true;
            if (acc[i] > rivalAcc) { rivalAcc = acc[i]; rival = i; }
        }
        hesitating = close || rivalAcc >= HESITATE_ACC;
        if (heldTicks >= MIN_HOLD_TICKS && rival >= 0 && rivalAcc >= SWITCH_THRESHOLD) return switchTo(rival);
        // лише від нудьги (справа триває довше BORED_TICKS); інакше кидала пошук їжі посередині
        if (heldTicks > BORED_TICKS && commitment < SPONTANEOUS_BELOW && sampled >= 0 && sampled != current && possible[sampled]) {
            return switchTo(sampled);
        }
        return current;
    }

    private static int best(double[] probs, boolean[] possible) {
        int b = 0;
        double bp = -1;
        for (int i = 0; i < probs.length; i++) if (possible[i] && probs[i] > bp) { bp = probs[i]; b = i; }
        return b;
    }

    private int switchTo(int a) {
        if (a == current) return current;
        current = a;
        commitment = COMMIT_START;
        heldTicks = 0;
        hesitating = false;
        rival = -1;
        java.util.Arrays.fill(acc, 0);
        return current;
    }

    /** Ззовні поставили дію (завантаження, налагодження) */
    public void set(int a) {
        switchTo(a);
    }
}
