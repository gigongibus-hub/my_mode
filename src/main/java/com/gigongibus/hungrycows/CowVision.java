package com.gigongibus.hungrycows;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Зір корови (план Fij, етап 1). Раз на 8 тіків (різні корови в різні тіки) складає список побаченого:
 * хто навколо, чи видно його (кут огляду, дальність, рух, блоки на шляху погляду) і чи чітко (спереду) чи краєм ока (збоку).
 * Побачене пам'ятає 10 с. Страх, протистояння, погляд, підхід до друга й пошук їжі беруть дані звідси.
 * Стіни для пошуку шляху рахуються окремо й завжди (не «як побачила»). Усі числа — у таблиці нижче.
 */
public class CowVision {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int EVERY = 8;                    // оновлює список раз на 8 тіків (0,4 с); корови — в різні тіки
    public static final double BLIND_ZONE = 30;           // сліпа зона позаду, градусів (огляд 330°)
    public static final double CLEAR_CONE = 50;           // чітко бачить спереду, градусів (±25°); збоку — «щось є» → повертає голову
    public static final double RANGE_DAY = 50;            // дальність удень, блоків
    public static final double RANGE_NIGHT = 20;          // уночі
    public static final double RANGE_RAIN = 15;           // у дощ
    public static final double WALK_FACTOR = 2.0 / 3.0;   // того, хто йде, бачить на 2/3 дальності (хто біжить — на всю)
    public static final double SNEAK_FACTOR = 1.0 / 3.0;  // того, хто крадеться (Shift), — на 1/3
    public static final double STILL_RANGE = 8;           // нерухомого — лише ближче 8 блоків (Fij) або коли дивиться прямо на нього
    public static final double RUN_SPEED = 0.2;           // «біжить» = швидше 0,2 бл./тік (або спринт)
    public static final double WALK_SPEED = 0.03;         // «іде» = швидше 0,03 бл./тік
    public static final int PARTIAL_LIMIT = 2;            // листя/висока трава: від 2 таких блоків на шляху погляду ...
    public static final double PARTIAL_RANGE = 8;         // ... помічає лише ближче 8 блоків або коли рухається
    public static final int MEMORY_TICKS = 200;           // побачене пам'ятає 10 с
    public static final int MAX_RAYS = 12;                // перевіряє погляд лише до 12 найважливіших за раз (найближчі, страшні)
    public static final double RAY_STEP = 0.25;           // крок променя погляду, блоків

    // Що корова бачить про когось
    public static class Seen {
        public final LivingEntity entity;
        public boolean clear;          // спереду (чітко) чи збоку (краєм ока)
        public int tick;               // коли бачила востаннє
        public Vec3 pos;               // де бачила востаннє

        Seen(LivingEntity e) {
            this.entity = e;
        }
    }

    private final HungryCow cow;
    private final Map<UUID, Seen> seen = new HashMap<>();
    private final Map<UUID, Vec3> lastPos = new HashMap<>();   // для швидкості руху (не зберігається)
    private LivingEntity glance = null;                          // помітила краєм ока — повернути голову
    private int glanceTick = -1000;
    private int seenNow = 0;

    public CowVision(HungryCow cow) {
        this.cow = cow;
    }

    // Дальність зору зараз: день / ніч / дощ
    public double range() {
        Level level = cow.level();
        if (level.isRaining() && HungryCow.rainReaches(level, cow.blockPosition())) return RANGE_RAIN;
        if (level.dimensionType().hasSkyLight() && !level.isDay()) return RANGE_NIGHT;
        return RANGE_DAY;
    }

    // Чи бачить (або бачила за останні 10 с)
    public boolean canSee(LivingEntity e) {
        if (e == null) return false;
        Seen s = seen.get(e.getUUID());
        return s != null && cow.tickCount - s.tick <= MEMORY_TICKS;
    }

    // Бачить саме зараз (останнє оновлення)
    public boolean seesNow(LivingEntity e) {
        Seen s = e == null ? null : seen.get(e.getUUID());
        return s != null && cow.tickCount - s.tick <= EVERY;
    }

    // Помітила краєм ока щось, що рухається (за останні 0,4 с) — WatchGoal повертає туди голову
    public LivingEntity glance() {
        return glance != null && glance.isAlive() && cow.tickCount - glanceTick <= EVERY * 2 ? glance : null;
    }

    // Інше чуття (нюх, слух) підказало, де щось є — повернути туди голову
    public void alertTo(LivingEntity e) {
        glance = e;
        glanceTick = cow.tickCount;
    }

    public void tick() {
        if ((cow.tickCount + cow.getId()) % EVERY != 0) return;
        update();
    }

    private void update() {
        double range = range();
        List<LivingEntity> around = cow.level().getEntitiesOfClass(LivingEntity.class, cow.getBoundingBox().inflate(range),
                e -> e != cow && e.isAlive() && !(e instanceof Player p && p.isSpectator()) && !e.hasEffect(MobEffects.INVISIBILITY));
        // найважливіші першими: страшні, далі ближчі
        around.sort(Comparator.comparingDouble(e -> cow.distanceToSqr(e) * (cow.isScary(e) ? 0.25 : 1.0)));
        Map<UUID, Vec3> now = new HashMap<>();
        int rays = 0;
        seenNow = 0;
        LivingEntity newGlance = null;
        for (LivingEntity e : around) {
            Vec3 before = lastPos.get(e.getUUID());
            now.put(e.getUUID(), e.position());
            if (rays >= MAX_RAYS) continue;
            double speed = before == null ? 0 : before.distanceTo(e.position()) / EVERY;
            double dist = Math.sqrt(cow.distanceToSqr(e));
            double angle = angleTo(e.getEyePosition());
            if (angle > 180 - BLIND_ZONE / 2) continue;               // позаду, у сліпій зоні
            boolean clear = angle <= CLEAR_CONE / 2;
            boolean running = speed > RUN_SPEED || e.isSprinting();
            boolean moving = speed > WALK_SPEED;
            double r = range;
            if (!running) {
                if (moving) r *= e.isCrouching() ? SNEAK_FACTOR : WALK_FACTOR;
                else if (!clear) r = Math.min(r, STILL_RANGE);       // нерухомого збоку — лише зблизька
            }
            if (dist > r) continue;
            rays++;
            int partial = lineOfSight(cow.getEyePosition(), e.getEyePosition(), null);
            if (partial < 0) partial = lineOfSight(cow.getEyePosition(), e.position().add(0, e.getBbHeight() / 2, 0), null);
            if (partial < 0) continue;                                // за стіною
            if (partial >= PARTIAL_LIMIT && dist > PARTIAL_RANGE && !moving) continue;   // у листі/траві, нерухомий
            Seen s = seen.computeIfAbsent(e.getUUID(), k -> new Seen(e));
            s.clear = clear;
            s.tick = cow.tickCount;
            s.pos = e.position();
            seenNow++;
            if (!clear && moving && newGlance == null) newGlance = e;
        }
        lastPos.clear();
        lastPos.putAll(now);
        if (newGlance != null) {
            glance = newGlance;
            glanceTick = cow.tickCount;
        }
        seen.values().removeIf(s -> cow.tickCount - s.tick > MEMORY_TICKS || s.entity.isRemoved());
    }

    // Кут між напрямом голови й напрямом на точку (0 — прямо, 180 — позаду), по горизонталі
    private double angleTo(Vec3 p) {
        double dx = p.x - cow.getX(), dz = p.z - cow.getZ();
        float toTarget = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
        return Math.abs(Mth.wrapDegrees(toTarget - cow.getYHeadRot()));
    }

    // Чи бачить блок (їжу): у межах дальності, не позаду й не за стіною. Сам блок (target) погляду не заступає.
    // Їжа нерухома: за листям і травою (від 2 блоків) — лише ближче PARTIAL_RANGE.
    public boolean canSeeBlock(BlockPos target, Vec3 point) {
        Vec3 eye = cow.getEyePosition();
        double dist = eye.distanceTo(point);
        if (dist > range()) return false;
        if (dist > 1.5 && angleTo(point) > 180 - BLIND_ZONE / 2) return false;
        int partial = lineOfSight(eye, point, target);
        if (partial < 0) return false;
        return partial < PARTIAL_LIMIT || dist <= PARTIAL_RANGE;
    }

    // Промінь погляду: −1 — заступає тверде (камінь, дерево, земля, зачинені двері…); інакше скільки «напівпрозорих»
    // блоків на шляху (листя, висока трава). Скло, паркани, повітря не заступають. skip — блок, що не рахується (ціль)
    public int lineOfSight(Vec3 from, Vec3 to, BlockPos skip) {
        Level level = cow.level();
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1e-6) return 0;
        Vec3 step = d.scale(RAY_STEP / len);
        int steps = (int) Math.ceil(len / RAY_STEP);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos startBlock = BlockPos.containing(from), endBlock = BlockPos.containing(to);
        long last = Long.MIN_VALUE;
        int partial = 0;
        Vec3 at = from;
        for (int i = 0; i <= steps; i++, at = at.add(step)) {
            p.set(Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z));
            long key = p.asLong();
            if (key == last) continue;
            last = key;
            if (p.equals(startBlock) || p.equals(endBlock) || (skip != null && p.equals(skip))) continue;
            if (!level.isLoaded(p)) return -1;
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            int k = kind(s, level, p);
            if (k == SEE_THROUGH) continue;
            if (k == PARTIAL) { partial++; continue; }
            // тверде: заступає, лише якщо промінь справді проходить крізь його форму (півблок, сходинки)
            VoxelShape shape = s.getShape(level, p);
            if (shape.isEmpty()) continue;
            if (shape.clip(from, to, p.immutable()) != null) return -1;
        }
        return partial;
    }

    private static final int SEE_THROUGH = 0, PARTIAL = 1, SOLID = 2;

    private static int kind(BlockState s, Level level, BlockPos p) {
        if (s.is(BlockTags.LEAVES) || s.is(Blocks.GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN)
                || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.VINE)) return PARTIAL;
        if (s.is(BlockTags.IMPERMEABLE) || s.is(Blocks.GLASS_PANE) || s.is(BlockTags.FENCES) || s.is(BlockTags.FENCE_GATES)
                || s.is(BlockTags.WALLS) || s.is(Blocks.IRON_BARS) || s.is(Blocks.CHAIN) || s.is(BlockTags.TRAPDOORS)) return SEE_THROUGH;
        if (s.getBlock() instanceof net.minecraft.world.level.block.StainedGlassPaneBlock) return SEE_THROUGH;
        if (s.is(BlockTags.DOORS)) return SOLID;   // двері: зачинені заступають (форма), відчинені — ні
        if (!s.canOcclude() && s.getCollisionShape(level, p).isEmpty()) return SEE_THROUGH;   // смолоскип, квітковий горщик…
        return SOLID;
    }

    // Скільки бачить (за 10 с) тих, хто підходить
    public int count(java.util.function.Predicate<LivingEntity> which) {
        int n = 0;
        for (Seen s : seen.values()) if (cow.tickCount - s.tick <= MEMORY_TICKS && s.entity.isAlive() && which.test(s.entity)) n++;
        return n;
    }

    // ---------- для книги й налагодження ----------

    public int seenCount() {
        return seenNow;
    }

    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("#Зір");
        out.add("Огляд " + Math.round(360 - BLIND_ZONE) + "°, чітко спереду " + Math.round(CLEAR_CONE) + "°");
        out.add("Бачить зараз до " + Math.round(range()) + " бл. (день " + Math.round(RANGE_DAY) + ", ніч "
                + Math.round(RANGE_NIGHT) + ", дощ " + Math.round(RANGE_RAIN) + ")");
        out.add("Того, хто йде, — на 2/3, хто крадеться — на 1/3, нерухомого — ближче " + Math.round(STILL_RANGE) + " бл.");
        int n = 0;
        for (Seen s : seen.values()) {
            if (n >= 6) break;
            String name = s.entity instanceof HungryCow c ? c.family.name : s.entity.getName().getString();
            int ago = (cow.tickCount - s.tick) / 20;
            out.add("- " + name + (ago == 0 ? (s.clear ? " (спереду)" : " (краєм ока)") : " (" + ago + " с тому)")
                    + ", " + Math.round(Math.sqrt(cow.distanceToSqr(s.pos))) + " бл.");
            n++;
        }
        if (seen.isEmpty()) out.add("Нікого не бачить");
        return out;
    }
}
