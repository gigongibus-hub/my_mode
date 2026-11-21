package com.gigongibus.hungrycows;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Характер корови: 14 рис від 0 до 100.
 * Риса = ген × GENE_SHARE + досвід × (1 − GENE_SHARE).
 * Ген береться з CowGenes (група «Темперамент», дві копії від батьків) і не змінюється все життя.
 * Досвід змінюється від того, що з коровою відбувається (таблиця Experience нижче).
 * Усі числа зібрані на початку файлу.
 */
public class CowCharacter {

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double GENE_SHARE = 0.35;        // частка генів у рисі (у справжніх корів спадковість характеру ≈ 30–40%)
    public static final int START_EXPERIENCE = 50;       // досвід при народженні: в середньому 50 ...
    public static final double START_EXPERIENCE_SPREAD = 8.0;  // ... ± випадковий розкид (умови перших днів)
    // спадковість і мутації генів: див. CowGenes

    // ================= 14 РИС =================
    public enum Trait {
        FEARFULNESS("полохливість"),
        BRAVERY("сміливість"),
        CURIOSITY("допитливість"),
        OBEDIENCE("слухняність"),
        AGGRESSION("агресивність"),
        DOMINANCE("домінантність"),
        SOCIABILITY("товариськість"),
        HUMAN_ATTACHMENT("прив'язаність до людей"),
        ACTIVITY("активність"),
        APPETITE("апетит"),
        STRESS_SENSITIVITY("чутливість до стресу"),
        MATERNAL("материнський інстинкт"),
        LEARNING("здатність вчитися"),
        PAIN_SENSITIVITY("чутливість до болю");

        public final String title;

        Trait(String title) {
            this.title = title;
        }
    }

    // ================= ДОСВІД: що змінює риси =================
    public enum Experience {
        TREAT("ласощі з руки",
                Trait.HUMAN_ATTACHMENT, 1, Trait.OBEDIENCE, 1),
        HIT_BY_PLAYER("удар гравця",
                Trait.FEARFULNESS, 3, Trait.HUMAN_ATTACHMENT, -2, Trait.AGGRESSION, 1),
        SAW_PLAYER_HIT_COW("бачила, як гравець ударив корову",
                Trait.FEARFULNESS, 2, Trait.HUMAN_ATTACHMENT, -1, Trait.AGGRESSION, 1),
        SAW_PLAYER_KILL("бачила, як гравець убив корову",
                Trait.FEARFULNESS, 3, Trait.STRESS_SENSITIVITY, 2, Trait.AGGRESSION, 1,
                Trait.HUMAN_ATTACHMENT, -5, Trait.OBEDIENCE, -2),
        SAW_ZOMBIE_KILL("бачила, як зомбі убив корову",
                Trait.FEARFULNESS, 3, Trait.STRESS_SENSITIVITY, 2, Trait.AGGRESSION, 1,
                Trait.HUMAN_ATTACHMENT, -1),
        MOTHER_KILLED_BY_PLAYER("бачила, як гравець убив її матір",
                Trait.FEARFULNESS, 5, Trait.STRESS_SENSITIVITY, 5, Trait.AGGRESSION, 2,
                Trait.HUMAN_ATTACHMENT, -10, Trait.OBEDIENCE, -6),
        MOTHER_DIED("бачила смерть матері",
                Trait.FEARFULNESS, 5, Trait.STRESS_SENSITIVITY, 5, Trait.AGGRESSION, 2,
                Trait.HUMAN_ATTACHMENT, -1),
        DEFENDED_CALF("захистила теля",
                Trait.BRAVERY, 2, Trait.MATERNAL, 1),
        EXPLOSION("поруч вибух",
                Trait.FEARFULNESS, 5, Trait.STRESS_SENSITIVITY, 5),
        FIRE("поруч вогонь",
                Trait.FEARFULNESS, 3, Trait.STRESS_SENSITIVITY, 3),
        RESCUED_FROM_WATER("гравець витягнув з води",
                Trait.HUMAN_ATTACHMENT, 8, Trait.OBEDIENCE, 2),
        BULL_FIGHT_WON("переміг у бійці биків",
                Trait.DOMINANCE, 4, Trait.BRAVERY, 1),
        BULL_FIGHT_LOST("програв у бійці биків",
                Trait.DOMINANCE, -3, Trait.FEARFULNESS, 1);

        public final String title;
        final Object[] changes;   // пари: Trait, скільки

        Experience(String title, Object... changes) {
            this.title = title;
            this.changes = changes;
        }
    }

    private final CowGenes genes;
    private final int[] experience = new int[Trait.values().length];

    public CowCharacter(CowGenes genes, RandomSource random) {
        this.genes = genes;
        resetExperience(random);
    }

    private int gene(Trait t) {
        return genes.value(CowGenes.Gene.forTrait(t));
    }

    private void resetExperience(RandomSource random) {
        for (int i = 0; i < experience.length; i++) {
            experience[i] = clamp((int) Math.round(START_EXPERIENCE + random.nextGaussian() * START_EXPERIENCE_SPREAD));
        }
    }

    // ---------- читання ----------

    public int get(Trait t) {
        int i = t.ordinal();
        return clamp((int) Math.round(gene(t) * GENE_SHARE + experience[i] * (1.0 - GENE_SHARE)));
    }

    // 0..1 замість 0..100: зручно для формул
    public double fraction(Trait t) {
        return get(t) / 100.0;
    }

    // ---------- народження ----------

    // Теля: досвід з нуля (≈50); гени успадковує CowGenes
    public void newborn(RandomSource random) {
        resetExperience(random);
    }

    // ---------- досвід ----------

    // Повертає текст змін для налагодження, наприклад "полохливість +3, прив'язаність до людей -2"
    public String apply(Experience e) {
        List<String> parts = new ArrayList<>();
        for (int k = 0; k + 1 < e.changes.length; k += 2) {
            Trait t = (Trait) e.changes[k];
            int delta = (Integer) e.changes[k + 1];
            experience[t.ordinal()] = clamp(experience[t.ordinal()] + delta);
            parts.add(t.title + " " + (delta > 0 ? "+" : "") + delta);
        }
        return String.join(", ", parts);
    }

    // ---------- для перегляду ----------

    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("Характер (риса = ген " + Math.round(GENE_SHARE * 100) + "% + досвід):");
        for (Trait t : Trait.values()) {
            int i = t.ordinal();
            out.add("   " + t.title + ": " + get(t) + " (ген " + gene(t) + ", досвід " + experience[i] + ")");
        }
        return out;
    }

    // ---------- збереження ----------

    public void save(CompoundTag tag) {
        tag.putIntArray("Experience", experience);
    }

    public void load(CompoundTag tag) {
        int[] e = tag.getIntArray("Experience");
        // стара корова без досвіду лишає випадковий із конструктора; старі гени "Genes" переносить CowGenes.load
        if (e.length == experience.length) System.arraycopy(e, 0, experience, 0, experience.length);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
