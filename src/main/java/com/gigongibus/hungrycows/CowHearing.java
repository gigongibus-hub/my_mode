package com.gigongibus.hungrycows;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Слух корови (план Fij, етап 3).
 * - Звуки гри (вибух, удар, постріл, ламання блока, голоси мобів, мукання, клич теляти, дзвін, музика) ловить ModForgeEvents
 *   (PlayLevelSoundEvent) і кладе сюди; кожна корова раз на 0,5 с слухає, що прозвучало поруч.
 * - Кроки рахує сама з руху (біг 8 бл., хода 5, крадькома 1,5).
 * - Дальність залежить від звуку (таблиця нижче); тверді блоки між джерелом і коровою глушать — дальність удвічі менша.
 * - Почула — повертає голову туди (зір допомагає побачити). Різкий гучний звук лякає одразу, навіть без видимого джерела
 *   (StartleGoal — відбігає від звуку). Частий безпечний звук перестає лякати (звикання).
 * - Приємне: музика (програвач, нотний блок), спокійне мукання своїх, вода й шелест листя поруч — заспокоює, місце «добре».
 * - Навчання: звук перед чимось добрим (частування, смачна їжа) стає приємним; почула його — іде туди (ComeToSoundGoal):
 *   дзвін перед годуванням → корови сходяться.
 * - Теля, що відбилося від матері, кличе її; мати чує до 32 бл. і йде до нього.
 */
public class CowHearing {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int EVERY = 10;                      // слухає раз на 0,5 с
    public static final int SOUND_MEMORY = 40;               // звук «висить у повітрі» 2 с (поки корови не почують)
    public static final int SOUNDS_MAX = 600;
    public static final double SOURCE_NEAR = 1.5;            // звук «у точці» належить тому, хто ближче 1,5 бл. до неї
    // дальність (бл.) і наскільки лякає (страх одразу, поблизу; далі слабше)
    public static final double R_EXPLOSION = 64, R_CALF = 32, R_BELL = 32, R_MOO = 24, R_MUSIC = 24, R_MOB = 16, R_HIT = 16,
            R_ARROW = 16, R_BREAK = 12;
    public static final int S_EXPLOSION = 45, S_ARROW = 20, S_HIT = 15, S_BREAK = 10, S_MOB = 10;
    public static final double STEP_RUN = 8, STEP_WALK = 5, STEP_SNEAK = 1.5;   // кроки: біг / хода / крадькома
    public static final double RUN_SPEED = 0.2, WALK_SPEED = 0.03;              // бл. за тік
    public static final double MUFFLE = 0.5;                 // крізь тверді блоки — удвічі тихіше (половина дальності)
    public static final double FAR_WEAK = 0.6;               // на межі дальності звук лякає на 40% (поруч — повністю)
    public static final double HABIT_GAIN = 0.08;            // звук минув без лиха (10 с) — звикання +0,08 (до 0,9)
    public static final double HABIT_MAX = 0.9;
    public static final double HABIT_FORGET = 1.0 / 6000;    // за секунду звикання слабшає (≈ 5 діб)
    public static final int CONSEQUENCE_TICKS = 200;         // звук пов'язується з тим, що сталося за 10 с після нього
    public static final double LIKE_GAIN = 0.12;             // звук перед добрим — приємніший на 0,12
    public static final double LIKE_LOSS = -0.3;             // звук перед ударом — неприємний
    public static final double LIKE_COME = 0.3;              // з такої приємності йде на звук
    public static final double LIKE_FORGET = 1.0 / 12000;    // приємність звуку забувається (≈ 10 діб)
    public static final int STARTLE_MIN = 8;                 // від такого переляку відбігає від звуку (StartleGoal)
    public static final int STARTLE_TICKS = 60;              // ... 3 с
    public static final int CALF_CALL_FROM = 10;             // теля кличе матір, коли воно далі 10 бл. від неї ...
    public static final int CALLS_TO_GO = 3;                 // мати йде до теляти, лише коли воно кличе 3 рази ...
    public static final int CALLS_WINDOW = 600;              // ... за 30 с (інакше відгукується, а теля йде само)
    public static final int ANSWER_EVERY = 100;              // відгукується не частіше раз на 5 с
    public static final int CALF_CALL_EVERY = 5;             // ... раз на 5 с (Fij: було 10 с, не помітно)
    public static final float CALF_CALL_VOLUME = 2.0F;       // гучно (гравцю чути здалеку)
    public static final double OTHER_PER_VOLUME = 16, OTHER_MIN = 4;   // інші звуки: 16 бл. × гучність, щонайменше 4
    public static final int AMBIENT_EVERY = 5;               // вода й листя довкола — раз на 5 с
    public static final int AMBIENT_RADIUS = 6;
    public static final int WATER_PLEASANT = 3, LEAVES_PLEASANT = 6;   // стільки блоків води / листя поруч — приємно
    public static final double PLEASANT_CALM = 0.5;          // кортизол −0,5 за 0,5 с за кожне приємне (до 3)
    public static final double PLACE_PLEASANT = 0.005;
    public static final int COME_TICKS = 400;                // на приємний звук іде до 20 с

    public enum Kind {
        EXPLOSION("вибух", R_EXPLOSION, S_EXPLOSION), CALF("клич теляти", R_CALF, 0), BELL("дзвін", R_BELL, 0),
        MOO("мукання", R_MOO, 0), MUSIC("музика", R_MUSIC, 0), MOB("голос моба", R_MOB, S_MOB), HIT("удар", R_HIT, S_HIT),
        ARROW("постріл", R_ARROW, S_ARROW), BREAK("ламання блока", R_BREAK, S_BREAK), OTHER("звук", 8, 0);

        public final String title;
        public final double range;
        public final int startle;

        Kind(String title, double range, int startle) {
            this.title = title;
            this.range = range;
            this.startle = startle;
        }
    }

    // Звук, що прозвучав у світі
    public record Sound(long seq, String id, Kind kind, Vec3 pos, long time, Entity source, double range) {
    }

    private static long nextSeq = 0;   // номер звуку по порядку (за часом не можна: у тому самому тіку звук міг прозвучати після того, як корова слухала)

    private static final Map<Level, Deque<Sound>> SOUNDS = new WeakHashMap<>();

    // Що визначити з назви звуку гри
    public static Kind classify(String id, Entity source) {
        if (id.contains("explode")) return Kind.EXPLOSION;
        if (id.contains("bell")) return Kind.BELL;
        if (id.contains("music_disc") || id.contains("note_block") || id.contains("jukebox")) return Kind.MUSIC;
        if (id.contains("entity.cow.")) return source instanceof HungryCow c && c.isBaby() ? Kind.CALF : Kind.MOO;
        if (id.contains("arrow.shoot") || id.contains("crossbow.shoot") || id.contains("trident.throw")) return Kind.ARROW;
        if (id.contains("attack") || (id.endsWith(".hurt") && !(source instanceof HungryCow))) return Kind.HIT;
        if (id.contains("zombie") || id.contains("skeleton") || id.contains("spider") || id.contains("wolf.growl")
                || id.contains("creeper.primed") || id.contains("husk") || id.contains("drowned") || id.contains("pillager")) return Kind.MOB;
        if (id.startsWith("minecraft:block.") && id.endsWith(".break")) return Kind.BREAK;
        if (id.contains(".step") || id.startsWith("minecraft:ui.") || id.startsWith("minecraft:music.") || id.startsWith("minecraft:ambient.")) return null;   // кроки рахує сама; музика гри й фон — не у світі
        return Kind.OTHER;   // усе інше теж чути (двері, скриня, жування, плескіт…) — без переляку, але можна звикнути чи полюбити
    }

    public static void onSound(Level level, String id, Vec3 pos, Entity source) {
        onSound(level, id, pos, source, 1.0F);
    }

    // volume — гучність, з якою гра грає звук: інші звуки чути на 16 бл. × гучність (як у грі), щонайменше 4
    public static void onSound(Level level, String id, Vec3 pos, Entity source, float volume) {
        // голоси тварин гра часто грає «у точці», без джерела: хто стоїть там — той і подав голос
        if (source == null && id.startsWith("minecraft:entity.")) {
            double best = SOURCE_NEAR * SOURCE_NEAR;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(pos, pos).inflate(SOURCE_NEAR))) {
                double d = e.position().add(0, e.getBbHeight() / 2, 0).distanceToSqr(pos);
                if (d < best) { best = d; source = e; }
            }
        }
        Kind k = classify(id, source);
        if (k == null) return;
        Deque<Sound> q = SOUNDS.computeIfAbsent(level, l -> new ArrayDeque<>());
        long now = level.getGameTime();
        while (!q.isEmpty() && now - q.peekFirst().time() > SOUND_MEMORY) q.pollFirst();
        double range = k == Kind.OTHER ? Math.max(OTHER_MIN, OTHER_PER_VOLUME * Math.min(volume, 2.0F)) : k.range;
        q.addLast(new Sound(++nextSeq, id, k, pos, now, source, range));
        while (q.size() > SOUNDS_MAX) q.pollFirst();
    }

    // ---------- корова ----------

    private final HungryCow cow;
    private final Map<String, Double> habit = new HashMap<>();   // звук → звикання 0..0,9 (зберігається)
    private final Map<String, Double> like = new HashMap<>();    // звук → приємність −1..1 (зберігається)
    private final Map<String, Long> pending = new HashMap<>();   // звук → коли почула (чекає наслідку 10 с)
    private long lastHeard = -1;                                 // номер останнього обробленого звуку
    private final Map<Integer, Vec3> lastPos = new HashMap<>();
    private int calfCallIn = 0;
    private final Deque<Integer> calfCalls = new ArrayDeque<>();   // коли мати чула клич свого теляти
    private int answerAt = 0;
    private int ambientIn = 0;
    private int pleasantAmbient = 0;
    private int pleasantNow = 0;
    // для інших цілей
    public Vec3 startleFrom = null;
    public int startleUntil = 0;
    public Vec3 comeTo = null;          // приємний звук або клич свого теляти — куди йти
    public int comeUntil = 0;
    public String comeWhy = "";
    private final Deque<String> recentHeard = new ArrayDeque<>();   // для книги

    public CowHearing(HungryCow cow) {
        this.cow = cow;
    }

    public void tick() {
        if ((cow.tickCount + cow.getId() * 3) % EVERY != 0) return;
        Level level = cow.level();
        long now = level.getGameTime();
        Vec3 ear = cow.getEyePosition();
        pleasantNow = 0;

        // 1. звуки гри
        Deque<Sound> q = SOUNDS.get(level);
        if (q != null) {
            for (Sound s : q) {
                if (s.seq() <= lastHeard || s.source() == cow) continue;
                double range = s.range();
                double d = s.pos().distanceTo(ear);
                if (d > range) continue;
                if (blocked(level, s.pos(), ear)) range *= MUFFLE;
                if (d > range) continue;
                hear(s.id(), s.kind(), s.pos(), s.source(), d / range);
            }
            if (!q.isEmpty()) lastHeard = Math.max(lastHeard, q.peekLast().seq());
        }
        // 2. кроки гравців і мобів поруч
        Map<Integer, Vec3> nowPos = new HashMap<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, cow.getBoundingBox().inflate(STEP_RUN),
                e -> e != cow && e.isAlive() && !(e instanceof HungryCow) && !(e instanceof Player p && p.isSpectator()))) {
            nowPos.put(e.getId(), e.position());
            Vec3 before = lastPos.get(e.getId());
            if (before == null || !e.onGround()) continue;
            double speed = before.distanceTo(e.position()) / EVERY;
            double range = speed > RUN_SPEED || e.isSprinting() ? STEP_RUN : speed > WALK_SPEED ? (e.isCrouching() ? STEP_SNEAK : STEP_WALK) : 0;
            if (range <= 0) continue;
            if (blocked(level, e.position().add(0, 0.2, 0), ear)) range *= MUFFLE;
            if (e.distanceTo(cow) > range) continue;
            // кроки не лякають, лише привертають увагу: незнайоме, чого не бачить, — повертає голову
            if (!cow.vision.canSee(e)) cow.vision.alertTo(e);
            note("кроки (" + e.getName().getString() + ")");
        }
        lastPos.clear();
        lastPos.putAll(nowPos);
        // 3. наслідки: що сталося за 10 с після звуку
        pending.entrySet().removeIf(en -> {
            if (now - en.getValue() < CONSEQUENCE_TICKS) return false;
            habit.merge(en.getKey(), HABIT_GAIN, (a, b) -> Math.min(HABIT_MAX, a + b));   // минув без лиха — звикає
            return true;
        });
        // 4. вода й листя довкола, теля кличе матір
        if (--ambientIn <= 0) {
            ambientIn = AMBIENT_EVERY * 20 / EVERY;
            scanAmbient();
        }
        pleasantNow += pleasantAmbient;
        if (cow.isBaby() && --calfCallIn <= 0) {
            calfCallIn = CALF_CALL_EVERY * 20 / EVERY;
            HungryCow m = cow.family.findMother();
            if (m != null && cow.distanceToSqr(m) > CALF_CALL_FROM * CALF_CALL_FROM) {
                cow.playSound(SoundEvents.COW_AMBIENT, CALF_CALL_VOLUME, 1.6F);   // клич теляти (високий голос)
                cow.tell("Теля " + cow.family.name + " кличе матір (" + m.family.name + ", " + Math.round(cow.distanceTo(m)) + " бл.)");
                if (cow.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE, cow.getX(), cow.getEyeY() + 0.5, cow.getZ(), 1, 0, 0, 0, 0);
                }
            }
        }
        if (pleasantNow > 0) {
            cow.chem.onPleasantSmell(PLEASANT_CALM * Math.min(3, pleasantNow));
            if (cow.tickCount % 100 < EVERY) cow.memory.addPlace(cow.blockPosition(), PLACE_PLEASANT);
        }
        // 5. забування раз на секунду
        if (cow.tickCount % 20 < EVERY) {
            habit.replaceAll((k, v) -> Math.max(0, v - HABIT_FORGET));
            like.replaceAll((k, v) -> v > 0 ? Math.max(0, v - LIKE_FORGET) : Math.min(0, v + LIKE_FORGET));
            habit.values().removeIf(v -> v == 0);
            like.values().removeIf(v -> v == 0);
        }
    }

    // Почула звук: far — 0 поруч … 1 на межі чутності
    private int lastHeardTick = -100000;

    private void hear(String id, Kind kind, Vec3 pos, Entity source, double far) {
        lastHeardTick = cow.tickCount;
        note(kind.title);
        if (kind != Kind.MOO && kind != Kind.CALF) pending.put(id, cow.level().getGameTime());   // мукання й клич — вроджене значення, не вчиться
        // повертає голову на звук (на звичні тихі звуки — ні, інакше смикалась би від кожного)
        if (kind != Kind.OTHER || habit.getOrDefault(id, 0.0) < 0.5) {
            if (source instanceof LivingEntity le && le != cow) cow.vision.alertTo(le);
            else cow.getLookControl().setLookAt(pos.x, pos.y, pos.z);
        }
        // переляк: різкий звук лякає одразу (звикання зменшує)
        if (kind.startle > 0 && !cow.isBaby() || kind == Kind.EXPLOSION) {
            double h = habit.getOrDefault(id, 0.0);
            int s = (int) Math.round(kind.startle * (1 - FAR_WEAK * far) * (1 - h));
            if (s > 0) {
                cow.fear = Math.min(100, cow.fear + s);
                cow.chem.onAlarm();
                if (s >= STARTLE_MIN) {
                    startleFrom = pos;
                    startleUntil = cow.tickCount + STARTLE_TICKS;
                }
            }
        }
        // приємне: музика, спокійне мукання своїх
        if (kind == Kind.MUSIC || (kind == Kind.MOO && source instanceof HungryCow c && c.fear < 20)) pleasantNow++;
        // вивчений приємний звук (дзвін перед годуванням) — іде туди
        double l = like.getOrDefault(id, 0.0);
        if (l >= LIKE_COME && !cow.isBaby()) {
            comeTo = pos;
            comeUntil = cow.tickCount + COME_TICKS;
            comeWhy = kind.title;
        }
        // теля чує мукання матері (вона кличе) — іде до неї (етап 8: «кликати теля»)
        if (kind == Kind.MOO && cow.isBaby() && source instanceof HungryCow m && m.isMotherOf(cow)) {
            comeTo = m.position();
            comeUntil = cow.tickCount + COME_TICKS;
            comeWhy = "кличе мати " + m.family.name;
        }
        // клич свого теляти (Fij: «мама частіше ходить за телям, ніж теля за нею») — мати відгукується муканням, а теля йде на
        // голос; сама йде до нього, лише коли воно кличе 3 рази за 30 с (не може прийти: застрягло, на повідку)
        if (kind == Kind.CALF && source instanceof HungryCow calf && cow.isMotherOf(calf)) {
            calfCalls.addLast(cow.tickCount);
            while (!calfCalls.isEmpty() && cow.tickCount - calfCalls.peekFirst() > CALLS_WINDOW) calfCalls.pollFirst();
            if (calfCalls.size() >= CALLS_TO_GO) {
                comeTo = calf.position();
                comeUntil = cow.tickCount + COME_TICKS;
                comeWhy = "клич теляти " + calf.family.name + " (не може прийти)";
            } else if (cow.tickCount >= answerAt) {
                answerAt = cow.tickCount + ANSWER_EVERY;
                cow.playSound(SoundEvents.COW_AMBIENT, 1.5F, 0.8F);   // відгук матері (низький голос)
                cow.tell(cow.family.name + " відгукується на клич теляти " + calf.family.name);
            }
        }
    }

    // Добре сталося (частування, смачна їжа): звуки за останні 10 с стають приємнішими
    public void onGood() {
        long now = cow.level().getGameTime();
        for (Map.Entry<String, Long> e : pending.entrySet()) {
            if (now - e.getValue() <= CONSEQUENCE_TICKS) like.merge(e.getKey(), LIKE_GAIN, (a, b) -> Mth.clamp(a + b, -1, 1));
        }
    }

    // Погане (удар): звуки перед ним стають неприємними й знову лякають (звикання скидається)
    public void onBad() {
        long now = cow.level().getGameTime();
        for (Map.Entry<String, Long> e : pending.entrySet()) {
            if (now - e.getValue() > CONSEQUENCE_TICKS) continue;
            like.merge(e.getKey(), LIKE_LOSS, (a, b) -> Mth.clamp(a + b, -1, 1));
            habit.remove(e.getKey());
        }
        pending.clear();
    }

    private void scanAmbient() {
        Level level = cow.level();
        BlockPos o = cow.blockPosition();
        int water = 0, leaves = 0;
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-AMBIENT_RADIUS, -2, -AMBIENT_RADIUS), o.offset(AMBIENT_RADIUS, 4, AMBIENT_RADIUS))) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.WATER)) water++;
            else if (s.is(BlockTags.LEAVES)) leaves++;
        }
        pleasantAmbient = (water >= WATER_PLEASANT ? 1 : 0) + (leaves >= LEAVES_PLEASANT ? 1 : 0);
    }

    // Тверді блоки між джерелом і вухом (як у зору: повний блок чи його форма на шляху)
    private boolean blocked(Level level, Vec3 from, Vec3 to) {
        return cow.vision.lineOfSight(from, to, null) < 0;
    }

    private void note(String what) {
        if (!recentHeard.isEmpty() && recentHeard.peekLast().equals(what)) return;
        recentHeard.addLast(what);
        while (recentHeard.size() > 6) recentHeard.pollFirst();
    }

    public int pleasantNow() {
        return pleasantNow;
    }

    public int lastHeardTick() {
        return lastHeardTick;
    }

    public double maxHabit() {
        double m = 0;
        for (double v : habit.values()) m = Math.max(m, v);
        return m;
    }

    public double habituation(String id) {
        return habit.getOrDefault(id, 0.0);
    }

    public double liking(String id) {
        return like.getOrDefault(id, 0.0);
    }

    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("#Слух");
        out.add("Вибух — до " + Math.round(R_EXPLOSION) + " бл., клич теляти й дзвін — " + Math.round(R_CALF) + ", мукання й музика — "
                + Math.round(R_MOO) + ", удари й голоси мобів — " + Math.round(R_HIT) + ", кроки — до " + Math.round(STEP_RUN));
        out.add("Крізь стіни — удвічі тихіше");
        if (recentHeard.isEmpty()) out.add("Нічого не чула");
        else out.add("Нещодавно чула: " + String.join(", ", recentHeard));
        if (pleasantNow > 0) out.add("Приємні звуки довкола (заспокоюють)");
        for (Map.Entry<String, Double> e : habit.entrySet()) {
            if (e.getValue() >= 0.3) out.add("Звикла: " + shortName(e.getKey()) + " (лякає на " + Math.round(e.getValue() * 100) + "% менше)");
        }
        for (Map.Entry<String, Double> e : like.entrySet()) {
            if (Math.abs(e.getValue()) >= 0.2) out.add((e.getValue() > 0 ? "Подобається: " : "Не любить: ") + shortName(e.getKey())
                    + (e.getValue() >= LIKE_COME ? " (іде на нього)" : ""));
        }
        return out;
    }

    private static String shortName(String id) {
        return id.replace("minecraft:", "").replace("block.", "").replace("entity.", "");
    }

    public void save(CompoundTag tag) {
        tag.put("HearHabit", saveMap(habit));
        tag.put("HearLike", saveMap(like));
    }

    public void load(CompoundTag tag) {
        loadMap(tag.getList("HearHabit", Tag.TAG_COMPOUND), habit);
        loadMap(tag.getList("HearLike", Tag.TAG_COMPOUND), like);
    }

    private static ListTag saveMap(Map<String, Double> m) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Double> e : m.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("K", e.getKey());
            t.putDouble("V", e.getValue());
            list.add(t);
        }
        return list;
    }

    private static void loadMap(ListTag list, Map<String, Double> m) {
        m.clear();
        for (Tag t : list) {
            CompoundTag c = (CompoundTag) t;
            m.put(c.getString("K"), c.getDouble("V"));
        }
    }
}
