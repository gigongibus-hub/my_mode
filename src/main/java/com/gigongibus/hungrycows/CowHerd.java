package com.gigongibus.hungrycows;

import com.gigongibus.hungrycows.CowCharacter.Trait;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Стадо (ідея 11). Кожні 5 секунд корова дивиться, хто поруч (до 32 блоків), визначає ватажка
 * (доросла самка з найбільшою «лідерською силою») і своє місце в ієрархії (ранг).
 * Згуртованість, сон разом, поступатися біля їжі та «робити те саме, що ватажок» — у цілях і мозку.
 * Усі числа зібрані на початку файлу.
 */
public class CowHerd {

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double HERD_RANGE = 32;          // хто ближче — та в одному стаді
    public static final int UPDATE_EVERY_SECONDS = 5;    // як часто переглядає стадо
    // Відстань до ватажка за рангом (Fij): найсильніша після ватажка — найближче, найслабша — найдалі
    public static final double FAR_FROM_LEADER = 14;     // (середнє; див. farFromLeader) далі цього — повертається до стада
    public static final double FAR_TOP = 8, FAR_BOTTOM = 22;     // найвищий ранг повертається з 8 бл., найнижчий — з 22
    public static final double BACK_MARGIN = 6;                  // повертається, поки не буде ближче (поріг − 6)
    public static final double SPREAD_TOP = 3, SPREAD_BOTTOM = 12; // повертається в точку до 3 бл. від ватажка (найвищий) ... 12 (найнижчий)
    // лідерська сила (для ватажка): домінантність + досвід матері + трохи сміливості
    public static final double LEAD_PER_CALF = 5;        // кожне народжене теля (до 5) додає стільки
    public static final double LEAD_BRAVERY = 0.2;       // сміливість × це
    // ранг (хто кому поступається): домінантність + розмір + бик
    public static final double RANK_SIZE = 100;          // більша на 10% — сильніша на 10
    public static final double RANK_BULL = 20;           // бик сильніший на стільки

    private final HungryCow cow;
    private HungryCow leader = null;
    private int size = 1;          // скільки корів у стаді (разом з нею)
    private int rank = 0;          // 0 = найсильніша (серед дорослих)
    private int adults = 1;
    private int seconds = 0;

    public CowHerd(HungryCow cow) {
        this.cow = cow;
    }

    // ---------- сили ----------

    public static double leadership(HungryCow c) {
        return c.character.get(Trait.DOMINANCE) + LEAD_PER_CALF * Math.min(5, c.repro.births())
                + LEAD_BRAVERY * c.character.get(Trait.BRAVERY);
    }

    public static double dominance(HungryCow c) {
        double d = c.character.get(Trait.DOMINANCE) + (c.sizeScale() - 1.0) * RANK_SIZE;
        if (!c.repro.isFemale()) d += RANK_BULL;
        if (c.isBaby()) d -= 50;
        return d;
    }

    // ---------- стан ----------

    // Ватажок стада (може бути й вона сама); null — стада немає
    public HungryCow leader() {
        if (leader != null && !leader.isAlive()) leader = null;
        return leader;
    }

    public boolean isLeader() {
        return leader == cow;
    }

    public int size() {
        return size;
    }

    public int rank() {
        return rank;
    }

    public int adults() {
        return adults;
    }

    // ---------- раз на секунду ----------

    public void secondTick() {
        if (++seconds % UPDATE_EVERY_SECONDS != 0 && leader() != null) return;
        List<HungryCow> herd = new ArrayList<>(cow.level().getEntitiesOfClass(HungryCow.class,
                cow.getBoundingBox().inflate(HERD_RANGE),
                c -> c.isAlive() && c.distanceToSqr(cow) <= HERD_RANGE * HERD_RANGE));
        if (!herd.contains(cow)) herd.add(cow);
        size = herd.size();
        HungryCow best = null;
        double bestLead = Double.NEGATIVE_INFINITY;
        double myDom = dominance(cow);
        rank = 0;
        adults = 0;
        for (HungryCow c : herd) {
            if (c.isBaby()) continue;
            adults++;
            if (c != cow && dominance(c) > myDom) rank++;
            if (!c.repro.isFemale()) continue;   // стадо водить самка, бик лише поряд
            double l = leadership(c);
            // однакова сила: та, що має менший номер у світі, щоб уся група вибрала ту саму
            if (l > bestLead || (l == bestLead && best != null && c.getId() < best.getId())) {
                bestLead = l;
                best = c;
            }
        }
        HungryCow old = leader;
        leader = size > 1 ? best : null;
        if (leader != null && leader != old && leader == cow) cow.tell(cow.family.name + " тепер ватажок стада (" + size + ")");
    }

    // Куди повертатися до стада: випадкова точка біля ватажка (а не впритул)
    public Vec3 pointNearLeader() {
        HungryCow l = leader();
        if (l == null) return null;
        double a = cow.getRandom().nextDouble() * Math.PI * 2;
        double r = cow.getRandom().nextDouble() * byRank(SPREAD_TOP, SPREAD_BOTTOM);
        return new Vec3(l.getX() + Math.cos(a) * r, l.getY(), l.getZ() + Math.sin(a) * r);
    }

    // 0 — найсильніша в стаді, 1 — найслабша
    public double rankFraction() {
        return adults <= 1 ? 0 : Math.min(1, rank / (double) (adults - 1));
    }

    private double byRank(double top, double bottom) {
        return top + (bottom - top) * rankFraction();
    }

    public double farFromLeader() {
        return byRank(FAR_TOP, FAR_BOTTOM);
    }

    public double backNear() {
        return Math.max(3, farFromLeader() - BACK_MARGIN);
    }

    public double distanceToLeader() {
        HungryCow l = leader();
        return l == null ? 0 : Math.sqrt(cow.distanceToSqr(l));
    }

    // ---------- для книги ----------

    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("#Стадо");
        HungryCow l = leader();
        if (l == null) {
            out.add("Стада поруч немає (сама)");
            return out;
        }
        out.add("Корів у стаді: " + size + " (дорослих " + adults + ")");
        out.add(isLeader() ? "Вона — ватажок стада" : "Ватажок: " + l.family.name + " (" + Math.round(distanceToLeader()) + " бл.)");
        out.add("Ранг: " + (rank + 1) + " з " + adults + (rank == 0 ? " (найсильніша)" : ""));
        out.add("Лідерська сила: " + Math.round(leadership(cow)) + ", сила в ієрархії: " + Math.round(dominance(cow)));
        return out;
    }
}
