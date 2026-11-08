package com.gigongibus.hungrycows;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Близькоспоріднене схрещування й генетичні вади (ідеї Fij 14–15, «Після плану» 1).
 * Коефіцієнт спорідненості теляти F (формула Райта): за кожен шлях через спільного предка батька й матері
 * додається ½^(кроків від матері + кроків від батька + 1). Брат × сестра, батько × дочка — 25%; напівбрат × напівсестра — 12,5%;
 * двоюрідні — 6,25%. Предків видно в родоводі до 4 поколінь (CowFamily.TREE_DEPTH), тож F рахується лише в цих межах.
 * Наслідки: більше мертвих телят; копії генів частіше однакові (від спільного предка); менше здоров'я;
 * шанс вади (слабке серце, кульгавість, безпліддя). Усе видно в книзі (розділ «Родовід»).
 * Усі числа зібрані на початку файлу.
 */
public class CowInbreeding {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double DEATH_PER_F = 1.0;        // мертвонароджені: +F (брат × сестра: +25%)
    public static final double DEATH_MAX = 0.9;
    public static final double HEALTH_PER_F = 0.6;       // здоров'я −60% × F (брат × сестра: −15%)
    public static final double ANOMALY_BASE = 0.01;      // вада: 1% у будь-якого теляти ...
    public static final double ANOMALY_PER_F = 0.6;      // ... + 60% × F (брат × сестра: 16%)
    public static final double WEAK_HEART_HEALTH = 0.7;  // слабке серце: здоров'я ×0,7
    public static final double LAME_SPEED = 0.8;         // кульгавість: швидкість ×0,8
    public static final double F_MAX = 0.9;

    public enum Anomaly {
        NONE("немає"),
        WEAK_HEART("слабке серце (здоров'я −30%)"),
        LAME("кульгавість (повільніша на 20%)"),
        STERILE("безпліддя");

        public final String title;

        Anomaly(String title) {
            this.title = title;
        }
    }

    private final HungryCow cow;
    public double coefficient = 0;           // F цієї корови (спорідненість її батьків)
    public Anomaly anomaly = Anomaly.NONE;

    public CowInbreeding(HungryCow cow) {
        this.cow = cow;
    }

    // ---------- формула ----------

    // Шляхи від батька чи матері до кожного предка (список Id від неї самої до предка включно)
    private static void collect(CompoundTag node, List<UUID> path, List<List<UUID>> out) {
        if (node == null || node.isEmpty() || !node.hasUUID("Id")) return;
        List<UUID> here = new ArrayList<>(path);
        here.add(node.getUUID("Id"));
        out.add(here);
        for (String key : new String[]{"M", "F"}) {
            if (node.contains(key)) collect(node.getCompound(key), here, out);
        }
    }

    // Коефіцієнт спорідненості теляти від матері й батька (їхні вузли родоводу: CowFamily.asNode).
    // Формула Райта: сума ½^(n1+n2+1) за кожну пару шляхів (від матері й від батька) до того самого предка,
    // які не мають спільних корів, крім самого предка (інакше рахувався б і предок «через» ближчого спільного родича)
    public static double coefficient(CompoundTag motherNode, CompoundTag fatherNode) {
        if (motherNode == null || fatherNode == null) return 0;
        List<List<UUID>> m = new ArrayList<>(), f = new ArrayList<>();
        collect(motherNode, new ArrayList<>(), m);
        collect(fatherNode, new ArrayList<>(), f);
        double sum = 0;
        for (List<UUID> a : m) {
            UUID end = a.get(a.size() - 1);
            for (List<UUID> b : f) {
                if (!b.get(b.size() - 1).equals(end)) continue;
                boolean disjoint = true;
                for (int i = 0; i < a.size() - 1 && disjoint; i++) {
                    if (b.subList(0, b.size() - 1).contains(a.get(i))) disjoint = false;
                }
                if (disjoint) sum += Math.pow(0.5, (a.size() - 1) + (b.size() - 1) + 1);
            }
        }
        return Math.min(F_MAX, sum);
    }

    public static float stillbirthBonus(double f) {
        return (float) (DEATH_PER_F * f);
    }

    // ---------- теля ----------

    // Народилося: запам'ятати F, копії генів частіше однакові, кинути шанс вади
    public void onBorn(double f) {
        coefficient = f;
        cow.genes.makeHomozygous(f, cow.getRandom());
        anomaly = Anomaly.NONE;
        if (cow.getRandom().nextDouble() < ANOMALY_BASE + ANOMALY_PER_F * f) {
            Anomaly[] all = {Anomaly.WEAK_HEART, Anomaly.LAME, Anomaly.STERILE};
            anomaly = all[cow.getRandom().nextInt(all.length)];
        }
    }

    public double healthFactor() {
        return (1.0 - HEALTH_PER_F * coefficient) * (anomaly == Anomaly.WEAK_HEART ? WEAK_HEART_HEALTH : 1.0);
    }

    public double speedFactor() {
        return anomaly == Anomaly.LAME ? LAME_SPEED : 1.0;
    }

    public boolean sterile() {
        return anomaly == Anomaly.STERILE;
    }

    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("Спорідненість батьків: " + Math.round(coefficient * 1000) / 10.0 + "%" + (coefficient >= 0.25 ? " (дуже близькі родичі)"
                : coefficient > 0 ? " (родичі)" : ""));
        if (anomaly != Anomaly.NONE) out.add("Вада: " + anomaly.title);
        return out;
    }

    public void save(CompoundTag tag) {
        tag.putDouble("Inbreeding", coefficient);
        tag.putString("Anomaly", anomaly.name());
    }

    public void load(CompoundTag tag) {
        coefficient = tag.getDouble("Inbreeding");
        try {
            anomaly = tag.contains("Anomaly") ? Anomaly.valueOf(tag.getString("Anomaly")) : Anomaly.NONE;
        } catch (IllegalArgumentException e) {
            anomaly = Anomaly.NONE;
        }
    }
}
