package com.gigongibus.hungrycows;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Гени корови. Кожного гена ДВІ копії: одна від матері, одна від батька.
 * Корова «показує» середнє між копіями (0–100, де 50 = звичайна корова).
 * Тому двоє спокійних батьків, що несуть приховану «полохливу» копію, можуть мати полохливе теля.
 * Усі числа зібрані на початку файлу.
 */
public class CowGenes {

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double EFFECT_RANGE = 0.30;       // ген 0 або 100 змінює звичайне число на ±30%
    public static final int RANDOM_MIN = 30;              // копії генів корови без батьків (з яйця призову): 30–70
    public static final int RANDOM_MAX = 70;
    public static final double MUTATION_SMALL = 2.0;      // звичайна мутація копії: зазвичай ±0–4
    public static final float MUTATION_BIG_CHANCE = 0.01F; // 1% копій мутують сильно
    public static final double MUTATION_BIG = 10.0;       // сильна мутація: зазвичай ±10–20
    public static final int NOTABLE_LOW = 35;             // «помітний» ген: нижчий за це ...
    public static final int NOTABLE_HIGH = 65;            // ... або вищий за це

    public enum Group {
        TEMPERAMENT("Темперамент"),
        METABOLISM("Обмін речовин і голод"),
        REPRODUCTION("Розмноження"),
        MILK("Молоко"),
        BODY("Тіло"),
        HORMONES("Гормони й стрес (діятимуть пізніше)"),
        IMMUNITY("Імунітет (діятиме з хворобами)"),
        BRAIN("Мозок: схильності до дій");

        public final String title;

        Group(String title) {
            this.title = title;
        }
    }

    public enum Gene {
        // ---- темперамент: гени 14 рис характеру ----
        TEMPER_FEARFULNESS(Group.TEMPERAMENT, "полохливість"),
        TEMPER_BRAVERY(Group.TEMPERAMENT, "сміливість"),
        TEMPER_CURIOSITY(Group.TEMPERAMENT, "допитливість"),
        TEMPER_OBEDIENCE(Group.TEMPERAMENT, "слухняність"),
        TEMPER_AGGRESSION(Group.TEMPERAMENT, "агресивність"),
        TEMPER_DOMINANCE(Group.TEMPERAMENT, "домінантність"),
        TEMPER_SOCIABILITY(Group.TEMPERAMENT, "товариськість"),
        TEMPER_HUMAN_ATTACHMENT(Group.TEMPERAMENT, "прив'язаність до людей"),
        TEMPER_ACTIVITY(Group.TEMPERAMENT, "активність"),
        TEMPER_APPETITE(Group.TEMPERAMENT, "апетит"),
        TEMPER_STRESS_SENSITIVITY(Group.TEMPERAMENT, "чутливість до стресу"),
        TEMPER_MATERNAL(Group.TEMPERAMENT, "материнський інстинкт"),
        TEMPER_LEARNING(Group.TEMPERAMENT, "здатність вчитися"),
        TEMPER_PAIN_SENSITIVITY(Group.TEMPERAMENT, "чутливість до болю"),
        // ---- обмін речовин і голод (12) ----
        HUNGER_RATE(Group.METABOLISM, "швидкість голоду"),
        THIRST_RATE(Group.METABOLISM, "швидкість спраги"),
        HUNGER_THRESHOLD(Group.METABOLISM, "шукає їжу раніше"),
        STARVE_RESIST(Group.METABOLISM, "стійкість до голодування"),
        GRASS_DIGEST(Group.METABOLISM, "користь від трави"),
        PRODUCE_DIGEST(Group.METABOLISM, "користь від овочів і фруктів"),
        POISON_RESIST(Group.METABOLISM, "стійкість до отрути"),
        CHOKE_RISK(Group.METABOLISM, "ризик подавитися"),
        FATIGUE_RATE(Group.METABOLISM, "швидкість втоми"),
        REST_RECOVERY(Group.METABOLISM, "відновлення під час відпочинку"),
        FLOWER_CRAVING(Group.METABOLISM, "тяга до квітів"),
        POPPY_CRAVING(Group.METABOLISM, "тяга до маку"),
        BLUET_RESIST(Group.METABOLISM, "стійкість до черемші"),
        // ---- розмноження (11) ----
        BIRTH_LIMIT(Group.REPRODUCTION, "скільки телят за життя"),
        BIG_FAMILY(Group.REPRODUCTION, "шанс великої родини"),
        PREGNANCY_LENGTH(Group.REPRODUCTION, "тривалість вагітності"),
        STILLBIRTH_RISK(Group.REPRODUCTION, "ризик мертвого теляти"),
        LABOR_LENGTH(Group.REPRODUCTION, "тривалість пологів"),
        LABOR_PAIN(Group.REPRODUCTION, "біль при пологах"),
        BREED_PAUSE(Group.REPRODUCTION, "пауза після розмноження"),
        MATING_TIME(Group.REPRODUCTION, "скільки разом до зачаття"),
        CALF_GROWTH(Group.REPRODUCTION, "швидкість росту теляти"),
        BREED_DRIVE(Group.REPRODUCTION, "тяга до розмноження (як далеко шукає пару)"),
        TWINS(Group.REPRODUCTION, "схильність до двійні"),
        FERTILITY(Group.REPRODUCTION, "плідність"),
        // ---- молоко (8) ----
        MILK_VOLUME(Group.MILK, "об'єм молока"),
        MILK_REGEN(Group.MILK, "швидкість відновлення молока"),
        LACTATION_LENGTH(Group.MILK, "тривалість лактації"),
        QUALITY_RESIST(Group.MILK, "стійкість якості до поганих квітів"),
        CALF_DRINK(Group.MILK, "скільки випиває теля"),
        MILK_RESERVE_1(Group.MILK, "запасний 1"),
        MILK_RESERVE_2(Group.MILK, "запасний 2"),
        MILK_RESERVE_3(Group.MILK, "запасний 3"),
        // ---- тіло (8) ----
        HEALTH(Group.BODY, "здоров'я"),
        WALK_SPEED(Group.BODY, "швидкість ходьби"),
        FLEE_SPEED(Group.BODY, "швидкість утечі"),
        KICK_STRENGTH(Group.BODY, "сила удару"),
        KNOCKBACK_RESIST(Group.BODY, "стійкість до відкидання"),
        STAMINA(Group.BODY, "витривалість (пізніше)"),
        SIZE(Group.BODY, "розмір"),
        LIFESPAN(Group.BODY, "тривалість життя"),
        // ---- гормони й стрес (10) ----
        ADRENALINE_BASE(Group.HORMONES, "адреналін"),
        CORTISOL_BASE(Group.HORMONES, "кортизол"),
        OXYTOCIN_BASE(Group.HORMONES, "окситоцин"),
        DOPAMINE_BASE(Group.HORMONES, "дофамін"),
        ESTROGEN_BASE(Group.HORMONES, "естроген"),
        PROGESTERONE_BASE(Group.HORMONES, "прогестерон"),
        PROLACTIN_BASE(Group.HORMONES, "пролактин"),
        ADRENALINE_DECAY(Group.HORMONES, "спад адреналіну"),
        CORTISOL_DECAY(Group.HORMONES, "спад кортизолу"),
        HORMONE_SENSITIVITY(Group.HORMONES, "чутливість до гормонів"),
        // ---- імунітет (8) ----
        IMMUNITY(Group.IMMUNITY, "загальний імунітет"),
        HAY_INFECTION_RESIST(Group.IMMUNITY, "стійкість до зараження із сіна"),
        WATER_INFECTION_RESIST(Group.IMMUNITY, "стійкість до зараження з води"),
        CONTACT_INFECTION_RESIST(Group.IMMUNITY, "стійкість до зараження від корів"),
        RECOVERY_SPEED(Group.IMMUNITY, "швидкість одужання"),
        FEVER(Group.IMMUNITY, "температура при хворобі"),
        COLOSTRUM_IMMUNITY(Group.IMMUNITY, "імунітет телятам з молоком"),
        SICK_STRESS_RESIST(Group.IMMUNITY, "спокій під час хвороби"),
        // ---- мозок: схильність до кожної дії (10) ----
        BRAIN_EAT(Group.BRAIN, "схильність їсти"),
        BRAIN_REST(Group.BRAIN, "схильність відпочивати"),
        BRAIN_HERD(Group.BRAIN, "схильність до стада"),
        BRAIN_FRIEND(Group.BRAIN, "схильність до друга"),
        BRAIN_WANDER(Group.BRAIN, "схильність гуляти"),
        BRAIN_WARY(Group.BRAIN, "схильність стерегтися"),
        BRAIN_MATE(Group.BRAIN, "схильність шукати пару"),
        BRAIN_FAMILY(Group.BRAIN, "схильність до рідних"),
        BRAIN_RUMINATE(Group.BRAIN, "схильність ремигати"),
        BRAIN_CURIOUS(Group.BRAIN, "схильність цікавитися"),
        BRAIN_DRINK(Group.BRAIN, "схильність пити"),
        // ---- тіло, додано пізніше (у кінці, щоб не зсунути інші) ----
        HEAT_TOLERANCE(Group.BODY, "витривалість до спеки"),
        COAT_COLOR(Group.BODY, "масть (0 біла … 100 чорна)"),
        COAT_PATTERN(Group.BODY, "візерунок (суцільна, білоголова, з поясом, ряба)"),
        RAIN_LIKING(Group.TEMPERAMENT, "любов до дощу");

        public final Group group;
        public final String title;

        Gene(Group group, String title) {
            this.group = group;
            this.title = title;
        }

        public static Gene forTrait(CowCharacter.Trait trait) {
            return valueOf("TEMPER_" + trait.name());
        }
    }

    private static final int N = Gene.values().length;
    private final int[] copyA = new int[N];   // копія від матері
    private final int[] copyB = new int[N];   // копія від батька

    // Корова без батьків: випадкові копії 30–70; масть і візерунок — будь-які 0–100 (обидві копії однакові:
    // інакше середнє майже завжди «посередині» й усі дикі корови були б бурі)
    public CowGenes(RandomSource random) {
        for (int i = 0; i < N; i++) {
            copyA[i] = RANDOM_MIN + random.nextInt(RANDOM_MAX - RANDOM_MIN + 1);
            copyB[i] = RANDOM_MIN + random.nextInt(RANDOM_MAX - RANDOM_MIN + 1);
        }
        for (Gene g : new Gene[]{Gene.COAT_COLOR, Gene.COAT_PATTERN}) {
            int v = random.nextInt(101);
            copyA[g.ordinal()] = v;
            copyB[g.ordinal()] = v;
        }
    }

    // ---------- вигляд (ідея Fij: масті й візерунки; текстури — tools/make_cow_textures.py) ----------
    public static final int COAT_COLORS = 6;      // 0 біла, 1 палева, 2 руда, 3 бура, 4 сіра, 5 чорна
    public static final int COAT_PATTERNS = 4;    // 0 суцільна, 1 білоголова, 2 з поясом, 3 ряба
    public static final String[] COLOR_NAMES = {"біла", "палева", "руда", "бура", "сіра", "чорна"};
    public static final String[] PATTERN_NAMES = {"суцільна", "білоголова", "з поясом", "ряба"};

    public int coatColor() {
        return Math.min(COAT_COLORS - 1, value(Gene.COAT_COLOR) * COAT_COLORS / 101);
    }

    public int coatPattern() {
        return Math.min(COAT_PATTERNS - 1, value(Gene.COAT_PATTERN) * COAT_PATTERNS / 101);
    }

    public String coatName() {
        int c = coatColor(), p = coatPattern();
        String pattern = (p == 1 && c == 0) ? "темноголова" : PATTERN_NAMES[p];
        return COLOR_NAMES[c] + ", " + pattern;
    }

    // ---------- читання ----------

    // Що корова «показує»: середнє двох копій
    public int value(Gene g) {
        return (int) Math.round((copyA[g.ordinal()] + copyB[g.ordinal()]) / 2.0);
    }

    // Множник для звичайного числа: ген 50 → ×1, 100 → ×1.3, 0 → ×0.7
    public double factor(Gene g) {
        return 1.0 + EFFECT_RANGE * (value(g) - 50) / 50.0;
    }

    // Зворотний множник: ген 100 → ×0.7, 0 → ×1.3 (для «стійкості»: вищий ген — менше шкоди)
    public double inverse(Gene g) {
        return 2.0 - factor(g);
    }

    // Задати ген (обидві копії) — для тестів і команд
    public void set(Gene g, int v) {
        copyA[g.ordinal()] = v;
        copyB[g.ordinal()] = v;
    }

    // ---------- спадковість ----------

    // Повний набір копій для збереження (батько вагітності, наприклад)
    public CompoundTag saveTag() {
        CompoundTag tag = new CompoundTag();
        for (Gene g : Gene.values()) {
            tag.putIntArray(g.name(), new int[]{copyA[g.ordinal()], copyB[g.ordinal()]});
        }
        return tag;
    }

    // Теля: одна копія — випадкова з двох материних, друга — випадкова з двох батькових (+ мутація).
    // Якщо батька не знаємо (тестова корова з команди), обидві копії від матері.
    public void inherit(CompoundTag mother, CompoundTag father, RandomSource random) {
        for (Gene g : Gene.values()) {
            int i = g.ordinal();
            int fromMother = pick(mother, g, random);
            int fromFather = father != null ? pick(father, g, random) : pick(mother, g, random);
            if (fromMother >= 0) copyA[i] = mutate(fromMother, random);
            if (fromFather >= 0) copyB[i] = mutate(fromFather, random);
        }
    }

    // Близькоспоріднене схрещування: з шансом F обидві копії гена однакові (успадковані від спільного предка)
    public void makeHomozygous(double f, RandomSource random) {
        if (f <= 0) return;
        for (Gene g : Gene.values()) {
            int i = g.ordinal();
            if (random.nextDouble() < f) {
                if (random.nextBoolean()) copyB[i] = copyA[i];
                else copyA[i] = copyB[i];
            }
        }
    }

    private static int pick(CompoundTag parent, Gene g, RandomSource random) {
        int[] pair = parent.getIntArray(g.name());
        if (pair.length != 2) return -1;   // у батьків такого гена ще не було: лишаємо випадковий
        return pair[random.nextInt(2)];
    }

    private static int mutate(int v, RandomSource random) {
        double r = v + random.nextGaussian() * MUTATION_SMALL;
        if (random.nextFloat() < MUTATION_BIG_CHANCE) r += random.nextGaussian() * MUTATION_BIG;
        return clamp((int) Math.round(r));
    }

    // ---------- для книги ----------

    public List<String> lines() {
        List<String> out = new ArrayList<>();
        Group current = null;
        for (Gene g : Gene.values()) {
            if (g.group != current) {
                current = g.group;
                out.add("#" + current.title);
            }
            int v = value(g);
            String mark = v < NOTABLE_LOW ? " ▼" : v > NOTABLE_HIGH ? " ▲" : "";
            out.add(g.title + ": " + v + mark + " (" + copyA[g.ordinal()] + "/" + copyB[g.ordinal()] + ")");
        }
        return out;
    }

    // ---------- збереження ----------

    public void save(CompoundTag tag) {
        tag.put("Genome", saveTag());
    }

    public void load(CompoundTag tag) {
        if (tag.contains("Genome")) {
            CompoundTag genome = tag.getCompound("Genome");
            for (Gene g : Gene.values()) {
                int[] pair = genome.getIntArray(g.name());
                if (pair.length == 2) {
                    copyA[g.ordinal()] = clamp(pair[0]);
                    copyB[g.ordinal()] = clamp(pair[1]);
                }
            }
        } else if (tag.contains("Genes")) {
            // корова з попередньої версії: 14 генів характеру по одній копії → обидві копії однакові
            int[] old = tag.getIntArray("Genes");
            CowCharacter.Trait[] traits = CowCharacter.Trait.values();
            for (int k = 0; k < traits.length && k < old.length; k++) {
                int i = Gene.forTrait(traits[k]).ordinal();
                copyA[i] = clamp(old[k]);
                copyB[i] = clamp(old[k]);
            }
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
