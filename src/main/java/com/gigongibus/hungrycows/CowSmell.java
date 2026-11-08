package com.gigongibus.hungrycows;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Нюх корови (план Fij, етап 2). Раз на секунду (різні корови в різні секунди) корова «нюхає»:
 * - кого чує: корів (рідних упізнає), гравців (у кожного свій запах), мобів — і невидимих теж;
 * - сліди запаху: де за останню хвилину пройшли гравці й моби (слабкіші — на половину дальності);
 * - кров (поранена чи мертва корова, 1 хв) — тривога; дим і вогонь поруч — тривога;
 * - їжу (SeekGrassGoal питає, коли не бачить їжі) — SearchFoodGoal іде на запах;
 * - приємне: квіти, свіже сіно, трава після дощу, знайомий гравець, мати чи теля — заспокоює (кортизол ↓), місце «добре».
 * Дальність — від вітру (Wind): за вітром 48, штиль 16, проти вітру 4. Запах проходить крізь дерево, землю, двері, паркани,
 * листя; зупиняють камінь і все кам'яне, цегла, скло, метал, бетон, обсидіан.
 * Значення запаху вчиться з досвіду: спершу нейтральний (−1…+1), після удару від когось — страшний, після частування — приємний.
 * Теля в перші хвилини поруч з матір'ю запам'ятовує її запах — потім чує її далі, ніж бачить.
 */
public class CowSmell {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int EVERY = 20;                    // нюхає раз на секунду
    public static final int MAX_SNIFFS = 10;               // скількох істот за раз перевіряє (найближчі)
    public static final int TRAIL_SECONDS = 60;            // сліди й кров пахнуть 1 хв
    public static final int TRAIL_EVERY = 20;              // гравці й моби лишають слід раз на секунду
    public static final int TRAILS_MAX = 4000;             // скільки точок сліду пам'ятає світ (найстаріші забуваються)
    public static final double TRAIL_RANGE = 0.5;          // слід чути на половину дальності
    public static final double TRAIL_STRENGTH = 0.5;       // і він удвічі слабший
    public static final double LEARN_HURT = -0.5;          // хтось ударив — його запах стає страшнішим на 0,5
    public static final double LEARN_TREAT = 0.15;         // гравець почастував чи приголубив — запах приємніший
    public static final double FORGET_PER_SECOND = 1.0 / 12000; // вивчене значення запаху повільно забувається (≈ 10 діб)
    public static final int VALUES_REMEMBERED = 40;        // скільки запахів пам'ятає
    public static final double KIN_VALUE = 0.5;            // рідні (мати, теля) пахнуть приємно від народження
    public static final double BLOOD_VALUE = -0.6;         // кров — тривога від народження
    public static final double SMOKE_VALUE = -0.5;         // дим і вогонь — тривога від народження
    public static final double FEAR_MAX = 12;              // страшний запах (−1) дає до +12 страху за секунду
    public static final double FEAR_FROM = -0.2;
    public static final double FAR_WEAK = 0.75;            // далекий запах слабший: на межі дальності лише чверть сили
    public static final int FEAR_BELOW_FLEE = 5;           // сам запах лякає лише до порогу втечі мінус 5: насторожена, але не тікає
                                                           // (тікає, коли побачить, від кого)
    public static final float BLOOD_DAMAGE = 2;            // кров — від удару когось або урону від 2           // запах страшний, якщо його значення нижче −0,2
    public static final int SCAN_EVERY = 5;                // блоки довкола (вогонь, квіти, сіно) оглядає раз на 5 с
    public static final int SCAN_RADIUS = 8;               // ... до 8 блоків
    public static final int SCAN_HEIGHT = 3;
    public static final int FLOWERS_PLEASANT = 3;          // 3+ квітки поруч — приємно
    public static final int RAIN_GRASS_TICKS = 2400;       // трава пахне після дощу ще 2 хв
    public static final double PLEASANT_CALM = 0.6;        // приємний запах: кортизол −0,6 за секунду (кожне джерело, до 3)
    public static final int PLEASANT_MAX = 3;
    public static final double PLACE_PLEASANT = 0.01;      // приємне місце: +0,01 до пам'яті місця раз на 5 с
    public static final int IMPRINT_TICKS = 6000;          // теля запам'ятовує запах матері в перші 5 хв (поруч, до 8 бл.)
    public static final double IMPRINT_NEAR = 8;
    public static final int FRESH_TICKS = 40;              // «чує зараз» = чула за останні 2 с

    // Що корова чує
    public static class Scent {
        public final String key;     // чий запах: "p:<uuid>", "c:<uuid>", "m:<тип>", "blood", "smoke"
        public final String name;    // для книги
        public Vec3 pos;
        public double strength;      // 1 — сам тут, 0,5 — лише слід
        public int tick;
        public Entity entity;        // істота, якщо чує її саму (а не слід)

        Scent(String key, String name) {
            this.key = key;
            this.name = name;
        }
    }

    // Слід: де хтось пройшов (спільний для світу)
    private record Trail(String key, String name, Vec3 pos, long time, UUID owner) {
    }

    private static final Map<Level, Deque<Trail>> TRAILS = new WeakHashMap<>();

    private final HungryCow cow;
    private final Map<String, Scent> smelled = new HashMap<>();
    private final Map<String, Double> values = new HashMap<>();   // вивчене: запах → −1…+1 (зберігається)
    private UUID motherScent = null;                              // запах матері (теля запам'ятало; зберігається)
    private int lastRainTick = -100000;
    private int pleasantNow = 0;
    private int seconds = 0;
    private boolean smokeNear = false;

    public CowSmell(HungryCow cow) {
        this.cow = cow;
    }

    // ---------- сліди (кличе ModForgeEvents раз на секунду) ----------

    public static void recordTrails(Level level) {
        Deque<Trail> trails = TRAILS.computeIfAbsent(level, l -> new ArrayDeque<>());
        long now = level.getGameTime();
        while (!trails.isEmpty() && now - trails.peekFirst().time() > TRAIL_SECONDS * 20L) trails.pollFirst();
        for (Player p : level.players()) {
            if (p.isSpectator()) continue;
            trails.addLast(new Trail(keyOf(p), nameOf(p), p.position(), now, p.getUUID()));
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(64),
                    e -> e instanceof net.minecraft.world.entity.monster.Enemy || e instanceof net.minecraft.world.entity.animal.Wolf)) {
                trails.addLast(new Trail(keyOf(e), nameOf(e), e.position(), now, e.getUUID()));
            }
        }
        while (trails.size() > TRAILS_MAX) trails.pollFirst();
    }

    // Кров: поранена чи мертва корова лишає запах тривоги на 1 хв
    public static void addBlood(Level level, Vec3 pos, UUID owner) {
        TRAILS.computeIfAbsent(level, l -> new ArrayDeque<>()).addLast(new Trail("blood", "кров", pos, level.getGameTime(), owner));
    }

    // ---------- ключі ----------

    public static String keyOf(Entity e) {
        if (e instanceof Player) return "p:" + e.getUUID();
        if (e instanceof HungryCow) return "c:" + e.getUUID();
        return "m:" + EntityType.getKey(e.getType());
    }

    private static String nameOf(Entity e) {
        if (e instanceof HungryCow c) return c.family.name;
        return e.getName().getString();
    }

    // Наскільки приємний (+) чи страшний (−) запах: вивчене + вроджене (рідні, кров, дим)
    public double value(String key) {
        double v = values.getOrDefault(key, 0.0);
        if (key.equals("blood")) v += BLOOD_VALUE;
        else if (key.equals("smoke")) v += SMOKE_VALUE;
        else if (key.startsWith("c:") && isKin(key)) v += KIN_VALUE;
        return Mth.clamp(v, -1, 1);
    }

    private boolean isKin(String key) {
        UUID id = UUID.fromString(key.substring(2));
        if (id.equals(cow.family.motherIdOrNull()) || id.equals(motherScent)) return true;
        if (cow.level() instanceof net.minecraft.server.level.ServerLevel s && s.getEntity(id) instanceof HungryCow c) {
            return cow.isMotherOf(c);
        }
        return false;
    }

    // Навчитися: подія від цієї істоти (удар — мінус, частування — плюс)
    public void learn(Entity source, double delta) {
        if (source == null || source == cow) return;
        String key = keyOf(source);
        values.merge(key, delta, (a, b) -> Mth.clamp(a + b, -1, 1));
        if (values.size() > VALUES_REMEMBERED) {
            String weakest = null;
            double w = Double.MAX_VALUE;
            for (Map.Entry<String, Double> e : values.entrySet()) {
                if (Math.abs(e.getValue()) < w) { w = Math.abs(e.getValue()); weakest = e.getKey(); }
            }
            if (weakest != null) values.remove(weakest);
        }
    }

    // ---------- чи доходить запах ----------

    // Чи чує корова запах з точки source: дальність за вітром і чи не заступає камінь/скло/метал
    public boolean reaches(Vec3 source, double rangeFactor, BlockPos skip) {
        Vec3 nose = cow.getEyePosition();
        double range = Wind.range(cow.level(), source, nose) * rangeFactor;
        if (source.distanceToSqr(nose) > range * range) return false;
        return !blocked(cow.level(), source, nose, skip);
    }

    public boolean smellsBlock(BlockPos pos) {
        return reaches(Vec3.atCenterOf(pos), 1.0, pos);
    }

    // Камінь і все кам'яне, цегла, скло, метал, бетон, обсидіан (за звуком блока) зупиняють запах; решта — ні
    public static boolean blocked(Level level, Vec3 from, Vec3 to, BlockPos skip) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1e-6) return false;
        Vec3 step = d.scale(0.25 / len);
        int steps = (int) Math.ceil(len / 0.25);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos a = BlockPos.containing(from), b = BlockPos.containing(to);
        long last = Long.MIN_VALUE;
        Vec3 at = from;
        for (int i = 0; i <= steps; i++, at = at.add(step)) {
            p.set(Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z));
            if (p.asLong() == last) continue;
            last = p.asLong();
            if (p.equals(a) || p.equals(b) || (skip != null && p.equals(skip))) continue;
            if (!level.isLoaded(p)) return true;
            BlockState s = level.getBlockState(p);
            if (s.isAir() || !stopsSmell(s)) continue;
            VoxelShape shape = s.getCollisionShape(level, p);
            if (!shape.isEmpty() && shape.clip(from, to, p.immutable()) != null) return true;
        }
        return false;
    }

    private static final Set<SoundType> STOPPERS = Set.of(SoundType.STONE, SoundType.DEEPSLATE, SoundType.DEEPSLATE_BRICKS,
            SoundType.DEEPSLATE_TILES, SoundType.POLISHED_DEEPSLATE, SoundType.TUFF, SoundType.CALCITE, SoundType.BASALT,
            SoundType.NETHER_BRICKS, SoundType.NETHERRACK, SoundType.GLASS, SoundType.METAL, SoundType.ANVIL, SoundType.COPPER,
            SoundType.NETHERITE_BLOCK, SoundType.AMETHYST, SoundType.MUD_BRICKS, SoundType.BONE_BLOCK, SoundType.GILDED_BLACKSTONE,
            SoundType.LODESTONE, SoundType.CHAIN, SoundType.LANTERN);

    private static boolean stopsSmell(BlockState s) {
        if (s.is(BlockTags.LEAVES) || s.is(BlockTags.DOORS) || s.is(BlockTags.TRAPDOORS) || s.is(BlockTags.FENCES)) return false;
        return STOPPERS.contains(s.getSoundType());
    }

    // ---------- раз на секунду ----------

    public void tick() {
        if ((cow.tickCount + cow.getId() * 7) % EVERY != 0) return;
        update();
    }

    private void update() {
        Level level = cow.level();
        seconds++;
        if (level.isRaining()) lastRainTick = cow.tickCount;
        // вивчене повільно забувається
        values.replaceAll((k, v) -> v > 0 ? Math.max(0, v - FORGET_PER_SECOND) : Math.min(0, v + FORGET_PER_SECOND));
        values.values().removeIf(v -> v == 0);

        // 1. істоти довкола (і невидимі — нюх не обдуриш)
        List<LivingEntity> around = level.getEntitiesOfClass(LivingEntity.class, cow.getBoundingBox().inflate(Wind.RANGE_WITH),
                e -> e != cow && e.isAlive() && !(e instanceof Player p && p.isSpectator()));
        around.sort(Comparator.comparingDouble(cow::distanceToSqr));
        int sniffs = 0;
        for (LivingEntity e : around) {
            if (sniffs >= MAX_SNIFFS) break;
            Vec3 src = e.position().add(0, e.getBbHeight() / 2, 0);
            double range = Wind.range(level, src, cow.getEyePosition());
            if (src.distanceToSqr(cow.getEyePosition()) > range * range) continue;
            sniffs++;
            if (blocked(level, src, cow.getEyePosition(), null)) continue;
            note(keyOf(e), nameOf(e), e.position(), 1.0, e);
        }
        // 2. сліди й кров (половина дальності, удвічі слабші)
        Deque<Trail> trails = TRAILS.get(level);
        if (trails != null) {
            long now = level.getGameTime();
            Map<String, Trail> nearest = new HashMap<>();
            for (Trail t : trails) {
                if (now - t.time() > TRAIL_SECONDS * 20L || cow.getUUID().equals(t.owner())) continue;   // свою кров не лякається
                double d = t.pos().distanceToSqr(cow.position());
                if (d > Wind.RANGE_WITH * TRAIL_RANGE * Wind.RANGE_WITH * TRAIL_RANGE) continue;
                Trail best = nearest.get(t.key());
                if (best == null || d < best.pos().distanceToSqr(cow.position())) nearest.put(t.key(), t);
            }
            for (Trail t : nearest.values()) {
                if (t.key().equals(keyOf(cow))) continue;
                Scent s = smelled.get(t.key());
                if (s != null && s.tick == cow.tickCount) continue;   // саму істоту вже чує
                if (reaches(t.pos().add(0, 0.5, 0), TRAIL_RANGE, null)) note(t.key(), t.name(), t.pos(), TRAIL_STRENGTH, null);
            }
        }
        // 3. блоки довкола раз на 5 с: дим і вогонь, квіти, свіже сіно
        if (seconds % SCAN_EVERY == 0) scanBlocks();
        // 4. теля запам'ятовує запах матері
        if (motherScent == null && cow.isBaby() && cow.tickCount < IMPRINT_TICKS) {
            HungryCow m = cow.family.findMother();
            if (m != null && cow.distanceToSqr(m) < IMPRINT_NEAR * IMPRINT_NEAR) {
                motherScent = m.getUUID();
                cow.tell(cow.family.name + " запам'ятало запах матері (" + m.family.name + ")");
            }
        }
        smelled.values().removeIf(s -> cow.tickCount - s.tick > FRESH_TICKS * 3);
        react();
    }

    private void note(String key, String name, Vec3 pos, double strength, Entity e) {
        Scent s = smelled.computeIfAbsent(key, k -> new Scent(k, name));
        s.pos = pos;
        s.strength = strength;
        s.tick = cow.tickCount;
        s.entity = e;
    }

    private int flowersNear = 0;
    private boolean hayNear = false;

    private void scanBlocks() {
        Level level = cow.level();
        BlockPos o = cow.blockPosition();
        flowersNear = 0;
        hayNear = false;
        smokeNear = false;
        BlockPos smoke = null;
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-SCAN_RADIUS, -SCAN_HEIGHT, -SCAN_RADIUS), o.offset(SCAN_RADIUS, SCAN_HEIGHT, SCAN_RADIUS))) {
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            if (s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES) && s.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
                if (smoke == null && reaches(Vec3.atCenterOf(p), 1.0, p.immutable())) smoke = p.immutable();
            } else if (s.is(BlockTags.FLOWERS) && p.distSqr(o) < 16) {
                flowersNear++;
            } else if (s.is(Blocks.HAY_BLOCK) && !hayNear && reaches(Vec3.atCenterOf(p), 1.0, p.immutable())) {
                hayNear = true;
            }
        }
        if (smoke != null) {
            smokeNear = true;
            note("smoke", "дим", Vec3.atCenterOf(smoke), 1.0, null);
        }
    }

    // Що робить з того, що чує: страшне — страх і повертає голову туди; приємне — заспокоює
    private void react() {
        double fearGain = 0;
        Scent scary = null;
        int pleasant = 0;
        for (Scent s : smelled.values()) {
            if (cow.tickCount - s.tick > FRESH_TICKS) continue;
            double range = Wind.range(cow.level(), s.pos, cow.getEyePosition());
            double near = 1.0 - FAR_WEAK * Math.min(1.0, Math.sqrt(s.pos.distanceToSqr(cow.getEyePosition())) / Math.max(1, range));
            double v = value(s.key) * s.strength * (value(s.key) < 0 ? near : 1.0);
            if (v < FEAR_FROM) {
                double g = -v * FEAR_MAX;
                if (g > fearGain) { fearGain = g; scary = s; }
            } else if (v > 0.2 || (s.key.startsWith("p:") && friendly(s))) {
                pleasant++;
            }
        }
        if (flowersNear >= FLOWERS_PLEASANT) pleasant++;
        if (hayNear) pleasant++;
        if (cow.tickCount - lastRainTick < RAIN_GRASS_TICKS && cow.level().getBlockState(cow.blockPosition().below()).is(Blocks.GRASS_BLOCK)) pleasant++;
        pleasantNow = Math.min(PLEASANT_MAX, pleasant);
        if (fearGain > 0 && !cow.sleeping) {
            int cap = Math.max(0, cow.scaredAt() - FEAR_BELOW_FLEE);
            if (cow.fear < cap) cow.fear = Math.min(cap, cow.fear + (int) Math.round(fearGain));
            // повертає голову туди, звідки пахне (зір допоможе побачити)
            if (scary.entity instanceof LivingEntity le) cow.vision.alertTo(le);
            else cow.getLookControl().setLookAt(scary.pos.x, scary.pos.y + 1, scary.pos.z);
        }
        if (pleasantNow > 0) {
            cow.chem.onPleasantSmell(PLEASANT_CALM * pleasantNow);
            if (seconds % SCAN_EVERY == 0) cow.memory.addPlace(cow.blockPosition(), PLACE_PLEASANT);
        }
    }

    private boolean friendly(Scent s) {
        return s.entity instanceof Player p && cow.trustOf(p) >= CowTrust.FRIEND_FROM;
    }

    // ---------- для інших ----------

    // Чи чує саму цю істоту зараз
    public boolean smells(Entity e) {
        Scent s = e == null ? null : smelled.get(keyOf(e));
        return s != null && s.entity == e && cow.tickCount - s.tick <= FRESH_TICKS;
    }

    // Теля чує матір (запам'ятало її запах)
    public boolean smellsMother(Entity mother) {
        return mother != null && mother.getUUID().equals(motherScent) && smells(mother);
    }

    public boolean knowsMotherScent() {
        return motherScent != null;
    }

    // Чи чує зараз запах із таким ключем (кров, дим, ...)
    public boolean smellsKey(String key) {
        Scent s = smelled.get(key);
        return s != null && cow.tickCount - s.tick <= FRESH_TICKS;
    }

    // Найстрашніший запах зараз (0…1)
    public double scariest() {
        double m = 0;
        for (Scent s : smelled.values()) {
            if (cow.tickCount - s.tick > FRESH_TICKS) continue;
            m = Math.max(m, -value(s.key) * s.strength);
        }
        return m;
    }

    public int pleasant() {
        return pleasantNow;
    }

    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("#Нюх");
        Wind w = Wind.of(cow.level());
        out.add("Зараз " + w.describe() + ": за вітром чує до " + Math.round(Wind.RANGE_WITH) + " бл., без вітру "
                + Math.round(Wind.RANGE_CALM) + ", проти вітру " + Math.round(Wind.RANGE_AGAINST));
        out.add("Крізь камінь, скло й метал запах не проходить");
        int n = 0;
        for (Scent s : smelled.values()) {
            if (n >= 6 || cow.tickCount - s.tick > FRESH_TICKS) continue;
            double v = value(s.key);
            out.add("- " + s.name + (s.strength < 1 ? " (слід)" : "") + ": " + (v < FEAR_FROM ? "страшно" : v > 0.2 ? "приємно" : "байдуже"));
            n++;
        }
        if (n == 0) out.add("Нічого особливого не чує");
        if (pleasantNow > 0) out.add("Приємні запахи довкола: " + pleasantNow + " (заспокоюють)");
        if (motherScent != null) out.add("Знає запах матері");
        for (Map.Entry<String, Double> e : values.entrySet()) {
            if (Math.abs(e.getValue()) < 0.2) continue;
            String who = e.getKey().startsWith("m:") ? e.getKey().substring(2).replace("minecraft:", "") : "знайомий запах";
            if (e.getKey().startsWith("p:") && cow.level() instanceof net.minecraft.server.level.ServerLevel s) {
                Player p = s.getPlayerByUUID(UUID.fromString(e.getKey().substring(2)));
                if (p != null) who = p.getName().getString();
            }
            out.add("Пам'ятає: " + who + " — " + (e.getValue() < 0 ? "страшно" : "приємно"));
        }
        return out;
    }

    public void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Double> e : values.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("K", e.getKey());
            t.putDouble("V", e.getValue());
            list.add(t);
        }
        tag.put("SmellValues", list);
        if (motherScent != null) tag.putUUID("MotherScent", motherScent);
    }

    public void load(CompoundTag tag) {
        values.clear();
        for (Tag t : tag.getList("SmellValues", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            values.put(c.getString("K"), c.getDouble("V"));
        }
        motherScent = tag.hasUUID("MotherScent") ? tag.getUUID("MotherScent") : null;
    }
}
