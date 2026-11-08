package com.gigongibus.hungrycows;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Пам'ять подій і корисних місць (план Fij, етап 6). Одні правила сили й забування для всього, що корова пам'ятає:
 * - Сила спогаду — від того, що корова відчувала в мить події: адреналін, біль, радість (дофамін) + вага самої події.
 *   Дрібна (злякалась шуму) — 1–2 доби; середня (вдарили, смачно поїла) — 10–30 діб; дуже сильна (напад зомбі, смерть теляти) —
 *   усе життя, але слабшає (не нижче 0,3).
 * - Сон закріплює спогади дня (сила ×1,2); погано спала (спала менше половини ночі) — половину дрібних забуває.
 * - Корисні місця (до 32): вода, трава, тінь, лизунець, дім — корова йде туди одразу, коли треба; не ходила 5 діб — забуває.
 * Відраза до їжі, звикання до гравців і пам'ять 4×4-клітинок (CowMemory) теж слабшають з часом; пам'ять про корів
 * (дружба, хто сильніший) — у CowTouch.
 */
public class CowEpisodes {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double MINOR_BELOW = 0.35;         // дрібний спогад — сила до 0,35 ...
    public static final double STRONG_FROM = 0.75;         // ... дуже сильний — від 0,75; між ними — середній
    public static final double MINOR_DAYS = 1.5;           // дрібний забувається за ~1,5 доби
    public static final double MEDIUM_DAYS_MIN = 10, MEDIUM_DAYS_MAX = 30;   // середній — 10–30 діб (сильніший — довше)
    public static final double STRONG_FADE_PER_DAY = 0.02; // дуже сильний слабшає на 0,02 за добу ...
    public static final double STRONG_FLOOR = 0.3;         // ... але не нижче 0,3 (усе життя)
    public static final double ADRENALINE_W = 0.4, PAIN_W = 0.4, JOY_W = 0.3;   // вага відчуттів у силі спогаду
    public static final int EPISODES_MAX = 60;
    public static final double SLEEP_BOOST = 1.2;          // добре спала — спогади дня ×1,2
    public static final double SLEEP_GOOD = 0.5;           // «добре» = спала хоча б половину ночі
    public static final double POOR_SLEEP_FORGET = 0.5;    // погано спала — дрібні спогади дня забуває з шансом 50%
    public static final int PLACES_MAX = 32;
    public static final double PLACE_FORGET_DAYS = 5;      // корисне місце, де не була 5 діб, — забуває
    public static final double PLACE_SAME = 4;             // ближче 4 бл. — те саме місце
    public static final double PLACE_RANGE = 64;           // іде до пам'ятного місця до 64 бл.

    // Що сталося (вага події — основа сили)
    public enum Kind {
        FRIGHT("злякалась", 0.1), PETTED("гладили", 0.15), TASTY("смачно поїла", 0.3), HIT("вдарили", 0.45),
        BIRTH("народила", 0.7), RESCUED("врятували", 0.6), ATTACK("на неї напали", 0.7),
        FRIEND_DEATH("загинула подруга", 0.75), CALF_DEATH("загинуло теля", 0.9), NEAR_DROWN("ледь не втопилась", 0.7);

        public final String title;
        public final double weight;

        Kind(String title, double weight) {
            this.title = title;
            this.weight = weight;
        }
    }

    public enum Place {
        WATER("вода"), GRASS("добра трава"), SHADE("тінь"), SALT("лизунець"), HOME("дім");

        public final String title;

        Place(String title) {
            this.title = title;
        }
    }

    public static class Episode {
        public Kind kind;
        public String who;        // хто/що (ім'я, ключ запаху) — для травм і фобій
        public BlockPos pos;
        public double strength;
        public double day;        // коли (доба гри)
        boolean today;
    }

    private record Spot(BlockPos pos, double lastDay) {
    }

    private final HungryCow cow;
    private final List<Episode> episodes = new ArrayList<>();
    private final Map<Place, List<Spot>> places = new EnumMap<>(Place.class);
    private boolean wasNight = false;
    private int nightSeconds = 0, sleptSeconds = 0;

    public CowEpisodes(HungryCow cow) {
        this.cow = cow;
    }

    private double day() {
        return cow.level().getDayTime() / 24000.0;
    }

    // Запам'ятати подію (сила — від того, що зараз відчуває); повертає спогад
    public Episode remember(Kind kind, String who) {
        double s = kind.weight + ADRENALINE_W * cow.chem.adrenalineLevel() / 100.0 + PAIN_W * cow.chem.feelPain() / 100.0
                + JOY_W * Math.max(0, cow.chem.dopamineLevel() - 20) / 100.0;
        Episode e = new Episode();
        e.kind = kind;
        e.who = who == null ? "" : who;
        e.pos = cow.blockPosition();
        e.strength = Mth.clamp(s, 0.05, 1.0);
        e.day = day();
        e.today = true;
        episodes.add(e);
        if (episodes.size() > EPISODES_MAX) {
            episodes.sort((a, b) -> Double.compare(a.strength, b.strength));
            episodes.remove(0);
        }
        cow.psyche.onEpisode(e);
        return e;
    }

    public List<Episode> episodes() {
        return episodes;
    }

    public static String strengthName(double s) {
        return s >= STRONG_FROM ? "дуже сильний" : s >= MINOR_BELOW ? "середній" : "дрібний";
    }

    // ---------- корисні місця ----------

    public void visit(Place type, BlockPos pos) {
        List<Spot> list = places.computeIfAbsent(type, t -> new ArrayList<>());
        list.removeIf(s -> s.pos().distSqr(pos) < PLACE_SAME * PLACE_SAME);
        list.add(new Spot(pos.immutable(), day()));
        int total = places.values().stream().mapToInt(List::size).sum();
        if (total > PLACES_MAX) {
            // забути найдавніше
            Spot oldest = null;
            List<Spot> from = null;
            for (List<Spot> l : places.values()) for (Spot s : l) if (oldest == null || s.lastDay() < oldest.lastDay()) { oldest = s; from = l; }
            if (from != null) from.remove(oldest);
        }
    }

    // Найближче пам'ятне місце такого типу (до 64 бл.), крім skip
    public BlockPos nearest(Place type, java.util.function.Predicate<BlockPos> stillGood) {
        List<Spot> list = places.get(type);
        if (list == null) return null;
        BlockPos best = null;
        double bd = PLACE_RANGE * PLACE_RANGE;
        for (Spot s : list) {
            double d = s.pos().distSqr(cow.blockPosition());
            if (d < bd && stillGood.test(s.pos())) { bd = d; best = s.pos(); }
        }
        return best;
    }

    public void forgetPlace(Place type, BlockPos pos) {
        List<Spot> list = places.get(type);
        if (list != null) list.removeIf(s -> s.pos().equals(pos));
    }

    // ---------- раз на секунду ----------

    public void secondTick() {
        // сон: рахує ніч і скільки спала; уранці — закріпити або забути
        if (cow.sleeping && cow.tickCount % 1200 < 20) visit(Place.HOME, cow.blockPosition());   // де спить — дім
        boolean night = cow.isNightTime();
        if (night) {
            nightSeconds++;
            if (cow.sleeping) sleptSeconds++;
        } else if (wasNight) {
            consolidate(nightSeconds > 0 && sleptSeconds >= SLEEP_GOOD * nightSeconds);
            nightSeconds = 0;
            sleptSeconds = 0;
        }
        wasNight = night;
        // забування раз на ~хвилину (частка доби)
        if (cow.tickCount % 1200 < 20) {
            double perDay = 1200 / 24000.0;   // хвилина гри = 1/20 доби
            for (Iterator<Episode> it = episodes.iterator(); it.hasNext(); ) {
                Episode e = it.next();
                if (e.strength >= STRONG_FROM) {
                    e.strength = Math.max(STRONG_FLOOR, e.strength - STRONG_FADE_PER_DAY * perDay);
                    continue;
                }
                double days = e.strength < MINOR_BELOW ? MINOR_DAYS
                        : MEDIUM_DAYS_MIN + (MEDIUM_DAYS_MAX - MEDIUM_DAYS_MIN) * (e.strength - MINOR_BELOW) / (STRONG_FROM - MINOR_BELOW);
                e.strength -= Math.max(e.strength, 0.1) / days * perDay;
                if (e.strength <= 0.02) it.remove();
            }
            double now = day();
            for (List<Spot> l : places.values()) l.removeIf(s -> now - s.lastDay() > PLACE_FORGET_DAYS);
        }
    }

    private void consolidate(boolean sleptWell) {
        int forgotten = 0;
        for (Iterator<Episode> it = episodes.iterator(); it.hasNext(); ) {
            Episode e = it.next();
            if (!e.today) continue;
            e.today = false;
            if (sleptWell) e.strength = Math.min(1.0, e.strength * SLEEP_BOOST);
            else if (e.strength < MINOR_BELOW && cow.getRandom().nextDouble() < POOR_SLEEP_FORGET) { it.remove(); forgotten++; }
        }
        cow.tell(cow.family.name + (sleptWell ? ": виспалась — спогади дня закріпились" : ": погано спала — забула дрібниці (" + forgotten + ")"));
    }

    // Для тестів і команд: «ніч минула»
    public void morning(boolean sleptWell) {
        consolidate(sleptWell);
    }

    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("#Спогади");
        if (episodes.isEmpty()) out.add("Нічого особливого не пам'ятає");
        episodes.stream().sorted((a, b) -> Double.compare(b.strength, a.strength)).limit(6).forEach(e ->
                out.add("- " + e.kind.title + (e.who.isEmpty() ? "" : " (" + e.who + ")") + ": " + strengthName(e.strength)
                        + ", " + Math.round(day() - e.day) + " діб тому"));
        StringBuilder sb = new StringBuilder("Знає місця: ");
        boolean any = false;
        for (Map.Entry<Place, List<Spot>> e : places.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            sb.append(e.getKey().title).append(" ×").append(e.getValue().size()).append(", ");
            any = true;
        }
        out.add(any ? sb.substring(0, sb.length() - 2) : "Корисних місць ще не знає");
        return out;
    }

    public void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Episode e : episodes) {
            CompoundTag c = new CompoundTag();
            c.putString("K", e.kind.name());
            c.putString("W", e.who);
            c.putLong("P", e.pos.asLong());
            c.putDouble("S", e.strength);
            c.putDouble("D", e.day);
            list.add(c);
        }
        tag.put("Episodes", list);
        ListTag pl = new ListTag();
        for (Map.Entry<Place, List<Spot>> e : places.entrySet()) {
            for (Spot s : e.getValue()) {
                CompoundTag c = new CompoundTag();
                c.putString("T", e.getKey().name());
                c.putLong("P", s.pos().asLong());
                c.putDouble("D", s.lastDay());
                pl.add(c);
            }
        }
        tag.put("UsefulPlaces", pl);
    }

    public void load(CompoundTag tag) {
        episodes.clear();
        for (Tag t : tag.getList("Episodes", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            try {
                Episode e = new Episode();
                e.kind = Kind.valueOf(c.getString("K"));
                e.who = c.getString("W");
                e.pos = BlockPos.of(c.getLong("P"));
                e.strength = c.getDouble("S");
                e.day = c.getDouble("D");
                episodes.add(e);
            } catch (IllegalArgumentException ignored) {
            }
        }
        places.clear();
        for (Tag t : tag.getList("UsefulPlaces", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            try {
                places.computeIfAbsent(Place.valueOf(c.getString("T")), k -> new ArrayList<>()).add(new Spot(BlockPos.of(c.getLong("P")), c.getDouble("D")));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
