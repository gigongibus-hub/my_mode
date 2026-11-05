package com.gigongibus.hungrycows;

import com.gigongibus.hungrycows.CowCharacter.Trait;
import com.gigongibus.hungrycows.CowGenes.Gene;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Мозок корови (крок 7). Раз на пів секунди дивиться на відчуття й на те, що навколо,
 * дає кожній із 10 дій бал і вибирає дію з відповідним шансом.
 * Бал = інстинкт (як старі правила) + ген схильності + маленька нейромережа (у 7а майже нульова, вчиться у 7б).
 * Рефлекси (голодування, виснаження, паніка, пологи, подавлення, захист теляти, ворожа корова) — поза мозком.
 * Коли мозок вимкнено (BRAIN_ENABLED / команда), корова діє за старими правилами.
 * Усі числа зібрані на початку файлу.
 */
public class CowBrain {

    // ================= ПЕРЕМИКАЧ =================
    public static final boolean BRAIN_ENABLED = true;    // чи ввімкнений мозок після запуску гри
    public static boolean enabled = BRAIN_ENABLED;       // змінює команда /hungrycows brain on|off (до перезапуску)

    // ================= ЯК ЧАСТО =================
    public static final int DECIDE_EVERY_TICKS = 10;     // вибір дії раз на пів секунди
    public static final int SENSE_EVERY_TICKS = 20;      // огляд навколо раз на секунду
    public static final int MIN_HOLD_TICKS = 100;        // вибрану дію робить хоча б 5 с ...
    public static final double SWITCH_MARGIN = 0.25;     // ... якщо нова дія не більш ніж на 25% імовірніша
    public static final double TEMPERATURE = 1.0;        // більше = вибір випадковіший

    // ================= ДЕ ДИВИТЬСЯ =================
    public static final double COW_RANGE = 16;
    public static final double SCARY_RANGE = 10;
    public static final double PLAYER_RANGE = 12;
    public static final int RUMINATE_AFTER_MEAL_TICKS = 3600; // ремигає до 3 хв після їжі
    public static final int EAT_MIN_HUNGER = 5;          // зовсім сита не піде їсти, хоч би що вирішив мозок

    // ================= ГЕНИ СХИЛЬНОСТЕЙ =================
    public static final double GENE_BIAS = 1.0;          // ген 0 або 100 додає до балу дії −1 або +1

    // ================= НЕЙРОМЕРЕЖА =================
    public static final int HIDDEN = 12;
    public static final double INIT_WEIGHT = 0.05;       // початкові ваги малі: мозок поводиться майже як інстинкт

    public enum Action {
        EAT("їсти", Gene.BRAIN_EAT),
        REST("відпочивати", Gene.BRAIN_REST),
        HERD("до стада", Gene.BRAIN_HERD),
        FRIEND("до друга", Gene.BRAIN_FRIEND),
        WANDER("гуляти", Gene.BRAIN_WANDER),
        WARY("стерегтися", Gene.BRAIN_WARY),
        MATE("шукати пару", Gene.BRAIN_MATE),
        FAMILY("до теляти/матері", Gene.BRAIN_FAMILY),
        RUMINATE("ремигати", Gene.BRAIN_RUMINATE),
        CURIOUS("цікавитися", Gene.BRAIN_CURIOUS);

        public final String title;
        public final Gene gene;

        Action(String title, Gene gene) {
            this.title = title;
            this.gene = gene;
        }
    }

    private static final Action[] ACTIONS = Action.values();
    private static final String[] INPUT_NAMES = {
            "голод", "втома", "страх", "стрес", "самотність", "біль", "нездужання", "комфорт", "бажання пари",
            "материнство", "адреналін", "окситоцин", "дофамін", "їжа поруч", "корова поруч", "страшне поруч",
            "друг поруч", "рідні поруч", "здоров'я", "вагітна", "тічка/самка в тічці", "ніч", "дощ",
            "допитливість", "товариськість", "активність", "нещодавно їла", "теля", "незнайомець поруч"
    };
    public static final int INPUTS = INPUT_NAMES.length;

    private final HungryCow cow;
    private Action current = Action.WANDER;
    private int heldTicks = 0;
    private final double[] probs = new double[ACTIONS.length];
    private final boolean[] possible = new boolean[ACTIONS.length];
    private final double[] input = new double[INPUTS];

    // що бачить (оновлюється раз на секунду)
    private double nearestCow = 0;           // 0 = немає, 1 = зовсім поруч
    private double scary = 0;
    private boolean friendNear = false;
    private Player stranger = null;
    private LivingEntity family = null;      // мати (для теляти) або власне теля (для матері)
    private double familyDist = 0;
    private boolean foodNear = false;
    private boolean estrusFemaleNear = false;

    // нейромережа: вхід → 12 прихованих → 10 дій
    private float[] w1 = new float[HIDDEN * (INPUTS + 1)];
    private float[] w2 = new float[ACTIONS.length * (HIDDEN + 1)];
    private final double[] hidden = new double[HIDDEN];

    public CowBrain(HungryCow cow) {
        this.cow = cow;
        for (int i = 0; i < w1.length; i++) w1[i] = (float) (cow.getRandom().nextGaussian() * INIT_WEIGHT);
        for (int i = 0; i < w2.length; i++) w2[i] = (float) (cow.getRandom().nextGaussian() * INIT_WEIGHT);
    }

    // ---------- для цілей ----------

    // Чи можна зараз робити цю дію (якщо мозок вимкнено — так, діють старі правила)
    public boolean allows(Action a) {
        return !enabled || current == a;
    }

    public boolean is(Action a) {
        return enabled && current == a;
    }

    public Action current() {
        return current;
    }

    public Player stranger() {
        return stranger;
    }

    public LivingEntity family() {
        return family;
    }

    // ---------- щотіку ----------

    public void tick() {
        if (!enabled) return;
        heldTicks++;
        if (cow.tickCount % SENSE_EVERY_TICKS == 0) sense();
        if (cow.tickCount % DECIDE_EVERY_TICKS == 0) decide();
    }

    private void sense() {
        double r = COW_RANGE;
        double best = r * r;
        estrusFemaleNear = false;
        for (HungryCow c : cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(r),
                c -> c != cow && c.isAlive())) {
            double d = cow.distanceToSqr(c);
            if (d < best) best = d;
            if (!cow.repro.isFemale() && c.repro.isFemale() && c.cycle.inEstrus() && !c.cycle.matedThisEstrus()) {
                estrusFemaleNear = true;
            }
        }
        nearestCow = best < r * r ? 1.0 - Math.sqrt(best) / r : 0;

        LivingEntity s = cow.findScary(SCARY_RANGE);
        scary = s != null ? 1.0 - Math.sqrt(cow.distanceToSqr(s)) / SCARY_RANGE : 0;

        friendNear = false;
        stranger = null;
        double strangerDist = PLAYER_RANGE * PLAYER_RANGE;
        for (Player p : cow.level().players()) {
            if (p.isSpectator() || p.hasEffect(MobEffects.INVISIBILITY)) continue;
            double d = cow.distanceToSqr(p);
            if (d > PLAYER_RANGE * PLAYER_RANGE) continue;
            int t = cow.trustOf(p);
            if (t >= CowTrust.FRIEND_FROM) friendNear = true;
            else if (t >= 0 && d < strangerDist) {
                strangerDist = d;
                stranger = p;
            }
        }

        // рідні: для теляти — мати; для матері — найближче власне теля
        family = null;
        familyDist = 0;
        double fr = CowFamily.MOTHER_RANGE;
        if (cow.isBaby()) {
            LivingEntity m = cow.family.findMother();
            if (m == null) m = cow.family.findPlayerMother();
            if (m != null && cow.distanceToSqr(m) < fr * fr) family = m;
        } else if (cow.repro.isFemale()) {
            double bd = fr * fr;
            for (HungryCow calf : cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(fr),
                    c -> c.isBaby() && cow.getUUID().equals(c.family.motherIdOrNull()))) {
                double d = cow.distanceToSqr(calf);
                if (d < bd) {
                    bd = d;
                    family = calf;
                }
            }
        }
        if (family != null) familyDist = Math.sqrt(cow.distanceToSqr(family));

        // їжа поруч: трава під ногами й навколо (дешева перевірка 5×5)
        foodNear = false;
        BlockPos o = cow.blockPosition();
        for (int dx = -2; dx <= 2 && !foodNear; dx++) {
            for (int dz = -2; dz <= 2 && !foodNear; dz++) {
                BlockState below = cow.level().getBlockState(o.offset(dx, -1, dz));
                BlockState at = cow.level().getBlockState(o.offset(dx, 0, dz));
                foodNear = below.is(Blocks.GRASS_BLOCK) || at.is(Blocks.GRASS) || at.is(Blocks.TALL_GRASS)
                        || at.is(BlockTags.SMALL_FLOWERS);
            }
        }
    }

    private void fillInputs() {
        CowChemistry ch = cow.chem;
        input[0] = ch.hungerFelt() / 100.0;
        input[1] = ch.fatigueFelt() / 100.0;
        input[2] = ch.fearFelt() / 100.0;
        input[3] = ch.feelStress() / 100.0;
        input[4] = ch.loneliness() / 100.0;
        input[5] = ch.feelPain() / 100.0;
        input[6] = ch.feelUnwell() / 100.0;
        input[7] = ch.feelComfort() / 100.0;
        input[8] = ch.feelMating() / 100.0;
        input[9] = ch.feelMotherhood() / 100.0;
        input[10] = ch.adrenalineLevel() / 100.0;
        input[11] = ch.oxytocinLevel() / 100.0;
        input[12] = ch.dopamineLevel() / 100.0;
        input[13] = foodNear ? 1 : 0;
        input[14] = nearestCow;
        input[15] = scary;
        input[16] = friendNear ? 1 : 0;
        input[17] = family != null ? 1.0 - familyDist / CowFamily.MOTHER_RANGE : 0;
        input[18] = cow.getHealth() / cow.getMaxHealth();
        input[19] = cow.repro.isPregnant() ? 1 : 0;
        input[20] = cow.repro.isFemale() ? (cow.cycle.inEstrus() ? 1 : 0) : (estrusFemaleNear ? 1 : 0);
        input[21] = cow.level().isDay() ? 0 : 1;
        input[22] = cow.level().isRainingAt(cow.blockPosition()) ? 1 : 0;
        input[23] = cow.character.fraction(Trait.CURIOSITY);
        input[24] = cow.character.fraction(Trait.SOCIABILITY);
        input[25] = cow.character.fraction(Trait.ACTIVITY);
        input[26] = cow.recentlyAte(RUMINATE_AFTER_MEAL_TICKS) ? 1 : 0;
        input[27] = cow.isBaby() ? 1 : 0;
        input[28] = stranger != null ? 1 : 0;
    }

    // Що взагалі можливо зараз
    private void fillPossible() {
        boolean adult = !cow.isBaby();
        possible[Action.EAT.ordinal()] = cow.hunger >= EAT_MIN_HUNGER && !cow.choking;
        possible[Action.REST.ordinal()] = cow.fatigue > 5 && !cow.cycle.inEstrus();
        possible[Action.HERD.ordinal()] = nearestCow > 0;
        possible[Action.FRIEND.ordinal()] = friendNear;
        possible[Action.WANDER.ordinal()] = true;
        possible[Action.WARY.ordinal()] = scary > 0;
        possible[Action.MATE.ordinal()] = adult && (cow.repro.isFemale()
                ? cow.cycle.inEstrus() && !cow.cycle.matedThisEstrus() && !cow.repro.isPregnant()
                : cow.getAge() == 0 && estrusFemaleNear);
        possible[Action.FAMILY.ordinal()] = family != null && familyDist > 3.0;
        possible[Action.RUMINATE.ordinal()] = adult && cow.recentlyAte(RUMINATE_AFTER_MEAL_TICKS) && !cow.wantsFood();
        possible[Action.CURIOUS.ordinal()] = stranger != null;
    }

    // Інстинкт: бал дії за старими правилами (0 — «на межі», більше — хочеться)
    private double instinct(Action a) {
        double hunger = cow.chem.hungerFelt() / 100.0;
        double tired = cow.chem.fatigueFelt() / 100.0;
        double fear = cow.chem.fearFelt() / 100.0;
        double scaredAt = cow.scaredAt() / 100.0;
        double hungryAt = cow.hungryAt() / 100.0;
        return switch (a) {
            case EAT -> 10 * (hunger - hungryAt) + (foodNear ? 0.5 : 0);
            case REST -> 10 * (tired - HungryCow.TIRED_AT / 100.0) - 3 * fear;
            case WARY -> 12 * (fear - scaredAt / 2);
            case HERD -> 3 * input[4] + 2 * input[24] * (1 - nearestCow)
                    + (cow.cycle.inProestrus() || cow.cycle.inEstrus() ? 2 : cow.cycle.inMetestrus() ? 0.5 : 0) - 1.5;
            case FRIEND -> 1 + 2 * input[24] - 3 * hunger;
            case WANDER -> 0.5 + input[25] - 2 * tired - 2 * hunger;
            case MATE -> 4;
            case FAMILY -> (cow.isBaby() ? 3 : 2 * input[9]) * Math.min(1, familyDist / 10.0) + 0.5;
            case RUMINATE -> 1.5 + input[7] - 3 * fear;
            case CURIOUS -> 3 * input[23] - 5 * fear - 1;
        };
    }

    private double network(int action) {
        int stride = INPUTS + 1;
        for (int h = 0; h < HIDDEN; h++) {
            double s = w1[h * stride + INPUTS];   // зсув
            for (int i = 0; i < INPUTS; i++) s += w1[h * stride + i] * input[i];
            hidden[h] = Math.tanh(s);
        }
        int stride2 = HIDDEN + 1;
        double out = w2[action * stride2 + HIDDEN];
        for (int h = 0; h < HIDDEN; h++) out += w2[action * stride2 + h] * hidden[h];
        return out;
    }

    private void decide() {
        fillInputs();
        fillPossible();
        double max = Double.NEGATIVE_INFINITY;
        double[] score = new double[ACTIONS.length];
        for (Action a : ACTIONS) {
            int i = a.ordinal();
            if (!possible[i]) {
                score[i] = Double.NEGATIVE_INFINITY;
                continue;
            }
            double geneBias = GENE_BIAS * (cow.genes.value(a.gene) - 50) / 50.0;
            score[i] = (instinct(a) + geneBias + network(i)) / TEMPERATURE;
            max = Math.max(max, score[i]);
        }
        double sum = 0;
        for (int i = 0; i < score.length; i++) {
            probs[i] = possible[i] ? Math.exp(score[i] - max) : 0;
            sum += probs[i];
        }
        for (int i = 0; i < probs.length; i++) probs[i] = sum > 0 ? probs[i] / sum : 0;

        // вибір з шансами
        double roll = cow.getRandom().nextDouble();
        Action pick = Action.WANDER;
        for (Action a : ACTIONS) {
            roll -= probs[a.ordinal()];
            if (roll <= 0) {
                pick = a;
                break;
            }
        }
        // щоб не смикалась: тримає дію, поки вона можлива, хоча б MIN_HOLD_TICKS
        if (pick != current && possible[current.ordinal()] && heldTicks < MIN_HOLD_TICKS
                && probs[pick.ordinal()] < probs[current.ordinal()] + SWITCH_MARGIN) {
            return;
        }
        if (pick != current) {
            current = pick;
            heldTicks = 0;
        }
    }

    // ---------- для палиці налагодження й книги ----------

    public String debugLine() {
        if (!enabled) return cow.family.name + ": мозок вимкнено (старі правила)";
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < probs.length; i++) order.add(i);
        order.sort((x, y) -> Double.compare(probs[y], probs[x]));
        StringBuilder sb = new StringBuilder(cow.family.name + ": " + current.title.toUpperCase() + " |");
        for (int k = 0; k < 4; k++) {
            int i = order.get(k);
            if (probs[i] <= 0) break;
            sb.append(" ").append(ACTIONS[i].title).append(" ").append(Math.round(probs[i] * 100)).append("%");
        }
        return sb.toString();
    }

    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("#Мозок");
        if (!enabled) {
            out.add("Вимкнено: корова діє за старими правилами (/hungrycows brain on)");
            return out;
        }
        out.add("Зараз: " + current.title + " (" + heldTicks / 20 + " с)");
        out.add("Шанси дій:");
        for (Action a : ACTIONS) {
            int i = a.ordinal();
            out.add("   " + a.title + ": " + (possible[i] ? Math.round(probs[i] * 100) + "%" : "неможливо"));
        }
        out.add("Схильності з генів: див. «Гени → Мозок»");
        out.add("Навчання: ще ні (крок 7б)");
        return out;
    }

    // ---------- збереження ----------

    public void save(CompoundTag tag) {
        CompoundTag c = new CompoundTag();
        c.putIntArray("W1", toBits(w1));
        c.putIntArray("W2", toBits(w2));
        c.putInt("Inputs", INPUTS);
        tag.put("Brain", c);
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("Brain")) return;
        CompoundTag c = tag.getCompound("Brain");
        if (c.getInt("Inputs") != INPUTS) return;   // змінився набір входів: лишаємо нові ваги
        int[] a = c.getIntArray("W1");
        int[] b = c.getIntArray("W2");
        if (a.length == w1.length) w1 = fromBits(a);
        if (b.length == w2.length) w2 = fromBits(b);
    }

    private static int[] toBits(float[] f) {
        int[] r = new int[f.length];
        for (int i = 0; i < f.length; i++) r[i] = Float.floatToIntBits(f[i]);
        return r;
    }

    private static float[] fromBits(int[] b) {
        float[] r = new float[b.length];
        for (int i = 0; i < b.length; i++) r[i] = Float.intBitsToFloat(b[i]);
        return r;
    }
}
