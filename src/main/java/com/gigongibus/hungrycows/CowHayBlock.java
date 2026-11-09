package com.gigongibus.hungrycows;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HayBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Сіно мода (план Fij «блоки сіна, плісняве сіно, гриби»). ОДИН блок із властивостями, а не 60 блоків:
 *  - стадія (stage): свіже → підсохле → сухе → пересохле (висушене на сонці, для сну);
 *  - стан (condition): чисте, трохи брудне, дуже брудне, пліснява з брудом, пліснява, гниле, мокре,
 *    зірочник (гниле свіже), вогняний гриб (гниле сухе), з яблуком, з морквою;
 *  - нитки (bound): зв'язане (ПКМ ниткою або павутиною) не розпадається; незв'язане через 5 хв розпадається на 9 пшениці,
 *    якщо воно не в купі з 4+ блоків сіна.
 * Усе, що змінюється з часом, — випадкові тіки блока (у середньому раз на ~68 с; дешево для гри), лише розпад — точний таймер.
 * Ванільний блок сіна лишається як був.
 */
public class CowHayBlock extends HayBlock {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    // зв'язування й розпад (Fij)
    public static final int UNBOUND_DECAY_SECONDS = 300;   // незв'язане розпадається через 5 хв ...
    public static final int HAY_PILE_MIN = 4;              // ... хіба що в купі з 4+ блоків сіна (сусідство гранями)
    public static final int DECAY_WHEAT = 9;               // розпалось — 9 пшениці (свіже — 9 пучків свіжої трави)
    // шанси за один випадковий тік (≈ раз на 68 с) — ПРОПОЗИЦІЯ Claude, Fij може змінити
    public static final double RAIN_WET = 0.5;             // під дощем без даху: чисте → мокре
    public static final double RAIN_DIRT = 0.15;           // мокре під дощем → трохи брудне
    public static final double RAIN_MORE_DIRT = 0.10;      // трохи брудне під дощем → дуже брудне
    public static final double DRY_IN_SUN = 0.5;           // мокре висихає на сонці ...
    public static final double DRY_IN_SHADE = 0.2;         // ... і під дахом повільніше
    public static final double MOLD_ON_WET = 0.25;         // мокре висохло — з шансом 25% пліснява (Fij)
    public static final double MOLD_SPREAD_WET = 0.5;      // пліснява заражає сусіда: мокрого швидко ...
    public static final double MOLD_SPREAD_DRY = 0.1;      // ... сухого повільно
    public static final double AGE_ROOF_FRESH = 0.10;      // під навісом: свіже → підсохле
    public static final double AGE_ROOF_WILTED = 0.06;     // під навісом: підсохле → сухе
    public static final double AGE_OPEN_FRESH = 0.05;      // надворі сохне повільніше (а гниє швидше)
    public static final double AGE_OPEN_WILTED = 0.03;
    public static final double OVERDRY_IN_SUN = 0.10;      // сухе на сонці → пересохле
    public static final double[] ROT = { 0.02, 0.012, 0.004, 0.002 };   // гниття за стадією (свіже гниє найшвидше)
    public static final double ROT_OPEN = 2.0;             // без даху гниє вдвічі швидше
    public static final double ROT_WET = 3.0;              // мокре — втричі
    public static final double FUNGUS = 0.05;              // гниле свіже → зірочник, гниле сухе → вогняний гриб (правило Fij)
    // бруд від корів (Fij: де ходять і лежать — брудніше, у кутку — чистіше)
    public static final int COW_DIRT_SECONDS = 120;        // 2 хв корови на блоці (лежить — удвічі швидше) → на щабель брудніше
    // світло плісняви (як у сяйливого лишайника)
    public static final int MOLD_LIGHT = 7;
    // поживність (Fij): база за стадією, мінус стан
    public static final int[] BASE_FOOD = { 30, 25, 20, 5 };   // свіже, підсохле, сухе, пересохле
    public static final int FRUIT_BONUS = 5;               // з яблуком / морквою: 35 замість 30
    public static final int ROTTEN_FOOD = 5;               // гниле (і з грибами)
    // ефекти (Fij)
    public static final int HUNGER_SLIGHT_SECONDS = 10, HUNGER_VERY_SECONDS = 20, HUNGER_MOLD_DIRT_SECONDS = 10;
    public static final int POISON_MOLD_SECONDS = 5;
    public static final int WITHER_ROTTEN_SECONDS = 5;
    public static final float ROTTEN_MIN_HEALTH = 6.0F;    // слабка (нижче 3 сердець) гниле не їсть — «Висушення» її не вб'є
    // комфорт підлоги для сну — ПРОПОЗИЦІЯ Claude (0 = камінь)
    public static final int COMFORT_STONE = 0, COMFORT_DIRT = 5, COMFORT_GRASS = 10;
    public static final int[] COMFORT_STAGE = { 25, 30, 35, 45 };   // сіно: пересохле — найкраще для сну (Fij)
    public static final int COMFORT_SLIGHT = -5, COMFORT_VERY = -15, COMFORT_WET = -20, COMFORT_ROTTEN = -25;
    public static final int COMFORT_PILE = 10;             // купа 4+ — «матрац»

    public enum Stage implements StringRepresentable {
        FRESH("fresh", "свіже"), WILTED("wilted", "підсохле"), DRY("dry", "сухе"), OVERDRIED("overdried", "пересохле");
        public final String id, title;
        Stage(String id, String title) { this.id = id; this.title = title; }
        @Override public String getSerializedName() { return id; }
        public Stage next() { return this == OVERDRIED ? OVERDRIED : values()[ordinal() + 1]; }
    }

    public enum Cond implements StringRepresentable {
        CLEAN("clean", "чисте"), SLIGHTLY_DIRTY("slightly_dirty", "трохи брудне"), VERY_DIRTY("very_dirty", "дуже брудне"),
        MOLD_DIRT("mold_dirt", "з пліснявою та брудом"), MOLD("mold", "з пліснявою"), ROTTEN("rotten", "гниле"),
        WET("wet", "мокре"), STAR_FUNGUS("star_fungus", "із зірочником"), FIRE_FUNGUS("fire_fungus", "з вогняним грибом"),
        APPLE("apple", "з яблуком"), CARROT("carrot", "з морквою");
        public final String id, title;
        Cond(String id, String title) { this.id = id; this.title = title; }
        @Override public String getSerializedName() { return id; }
        public boolean moldy() { return this == MOLD || this == MOLD_DIRT; }
        public boolean rotten() { return this == ROTTEN || this == STAR_FUNGUS || this == FIRE_FUNGUS; }
        public boolean dirty() { return this == SLIGHTLY_DIRTY || this == VERY_DIRTY || this == MOLD_DIRT; }
        public boolean cleanLike() { return this == CLEAN || this == APPLE || this == CARROT; }
        /** Корова не їсть, поки не голодує (як черемшу) */
        public boolean avoided() { return moldy() || rotten(); }
    }

    public static final EnumProperty<Stage> STAGE = EnumProperty.create("stage", Stage.class);
    public static final EnumProperty<Cond> COND = EnumProperty.create("condition", Cond.class);
    public static final BooleanProperty BOUND = BooleanProperty.create("bound");

    public CowHayBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(STAGE, Stage.FRESH).setValue(COND, Cond.CLEAN).setValue(BOUND, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);   // вісь, як у ванільного сіна
        builder.add(STAGE, COND, BOUND);
    }

    public static int light(BlockState s) {
        return s.getValue(COND).moldy() ? MOLD_LIGHT : 0;
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    // ---------- зв'язування і розпад ----------

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        scheduleDecay(state, level, pos);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        scheduleDecay(state, level, pos);   // купа зменшилась нижче 4 — відлік 5 хв знову
    }

    private void scheduleDecay(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide || state.getValue(BOUND)) return;
        if (pileSize(level, pos, HAY_PILE_MIN) >= HAY_PILE_MIN) return;
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) level.scheduleTick(pos, this, UNBOUND_DECAY_SECONDS * 20);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        decayIfLoose(state, level, pos);
    }

    /** Незв'язане й не в купі — розпадається на 9 пшениці. Повертає true, якщо розпалось. */
    public static boolean decayIfLoose(BlockState state, ServerLevel level, BlockPos pos) {
        if (!(state.getBlock() instanceof CowHayBlock) || state.getValue(BOUND)) return false;
        if (pileSize(level, pos, HAY_PILE_MIN) >= HAY_PILE_MIN) return false;
        level.removeBlock(pos, false);
        Block.popResource(level, pos, state.getValue(STAGE) == Stage.FRESH
                ? new ItemStack(ModBlocks.HAY_BUNDLE.get(), DECAY_WHEAT)    // свіже — 9 пучків свіжої трави (Fij)
                : new ItemStack(Items.WHEAT, DECAY_WHEAT));
        level.playSound(null, pos, SoundEvents.GRASS_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /** Скільки блоків сіна мода з'єднано гранями з цим (рахує до limit) */
    public static int pileSize(Level level, BlockPos start, int limit) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty() && seen.size() < limit) {
            BlockPos c = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = c.relative(d);
                if (seen.contains(n) || !(level.getBlockState(n).getBlock() instanceof CowHayBlock)) continue;
                seen.add(n.immutable());
                queue.add(n.immutable());
            }
        }
        return seen.size();
    }

    // ПКМ: ниткою чи павутиною — зв'язати; яблуком/морквою по свіжому чистому — сіно з фруктами/овочами
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        BlockState next = null;
        boolean tie = stack.is(Items.STRING) || stack.is(Items.COBWEB);   // нитка (Fij) або павутина
        if (tie && state.getValue(BOUND)) {
            if (!level.isClientSide) player.displayClientMessage(net.minecraft.network.chat.Component.literal("Сіно вже зв'язане"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (tie) next = state.setValue(BOUND, true);
        else if (state.getValue(STAGE) == Stage.FRESH && state.getValue(COND) == Cond.CLEAN && stack.is(Items.APPLE)) next = state.setValue(COND, Cond.APPLE);
        else if (state.getValue(STAGE) == Stage.FRESH && state.getValue(COND) == Cond.CLEAN && stack.is(Items.CARROT)) next = state.setValue(COND, Cond.CARROT);
        if (next == null) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, next);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            level.playSound(null, pos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ---------- з часом (випадкові тіки) ----------

    /** Над блоком дах (інший блок сіна зверху теж накриває) */
    public static boolean roofed(Level level, BlockPos pos) {
        return CowHome.hasRoof(level, pos.below());
    }

    public static boolean rainedOn(Level level, BlockPos pos) {
        return level.isRaining() && !roofed(level, pos)
                && level.getBiome(pos).value().getPrecipitationAt(pos) == Biome.Precipitation.RAIN;
    }

    public static boolean sunny(Level level, BlockPos pos) {
        return level.isDay() && !level.isRaining() && !roofed(level, pos);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState next = age(state, level, pos, random);
        if (next != state) level.setBlockAndUpdate(pos, next);
        if (next.getValue(COND).moldy()) spreadMold(level, pos, random);
        trampled(level).computeIfPresent(pos.asLong(), (k, v) -> v / 2 > 0 ? v / 2 : null);   // бруд від корів «затухає»
    }

    /** Що станеться з блоком за один випадковий тік (без запису у світ — для тестів теж) */
    public static BlockState age(BlockState s, Level level, BlockPos pos, RandomSource r) {
        Stage stage = s.getValue(STAGE);
        Cond c = s.getValue(COND);
        boolean rain = rainedOn(level, pos), roof = roofed(level, pos), sun = sunny(level, pos);
        // гниле: на свіжому може вирости зірочник, на сухому — вогняний гриб
        if (c == Cond.ROTTEN) {
            if (stage == Stage.FRESH && r.nextDouble() < FUNGUS) return s.setValue(COND, Cond.STAR_FUNGUS);
            if (stage == Stage.DRY && r.nextDouble() < FUNGUS) return s.setValue(COND, Cond.FIRE_FUNGUS);
            return s;
        }
        if (c.rotten()) return s;
        // гниття: термін придатності за стадією
        double rot = ROT[stage.ordinal()] * (roof ? 1 : ROT_OPEN) * (c == Cond.WET ? ROT_WET : 1);
        if (r.nextDouble() < rot) return s.setValue(COND, Cond.ROTTEN);
        // дощ і висихання
        if (c == Cond.WET) {
            if (rain) return r.nextDouble() < RAIN_DIRT ? s.setValue(COND, Cond.SLIGHTLY_DIRTY) : s;
            if (r.nextDouble() < (sun ? DRY_IN_SUN : DRY_IN_SHADE)) {
                if (r.nextDouble() < MOLD_ON_WET) return s.setValue(COND, Cond.MOLD);
                return s.setValue(COND, r.nextBoolean() ? Cond.SLIGHTLY_DIRTY : Cond.CLEAN);
            }
            return s;
        }
        if (rain) {
            if (c.cleanLike() && r.nextDouble() < RAIN_WET) return s.setValue(COND, Cond.WET);
            if (c == Cond.SLIGHTLY_DIRTY && r.nextDouble() < RAIN_MORE_DIRT) return s.setValue(COND, Cond.VERY_DIRTY);
            if (c == Cond.MOLD && r.nextDouble() < RAIN_MORE_DIRT) return s.setValue(COND, Cond.MOLD_DIRT);
            return s;
        }
        // висихання: під навісом швидше, на сонці сухе стає пересохлим
        double p = switch (stage) {
            case FRESH -> roof ? AGE_ROOF_FRESH : AGE_OPEN_FRESH;
            case WILTED -> roof ? AGE_ROOF_WILTED : AGE_OPEN_WILTED;
            case DRY -> sun ? OVERDRY_IN_SUN : 0;
            case OVERDRIED -> 0;
        };
        if (r.nextDouble() < p) {
            BlockState n = s.setValue(STAGE, stage.next());
            if (c == Cond.APPLE || c == Cond.CARROT) n = n.setValue(COND, Cond.CLEAN);   // фрукти/овочі бувають лише на свіжому
            return n;
        }
        return s;
    }

    /** Пліснява заражає одного випадкового сусіда (не миттєво: мокрого 50%, сухого 10%) */
    public static void spreadMold(ServerLevel level, BlockPos pos, RandomSource r) {
        BlockPos n = pos.relative(Direction.getRandom(r));
        BlockState ns = level.getBlockState(n);
        if (!(ns.getBlock() instanceof CowHayBlock)) return;
        Cond c = ns.getValue(COND);
        if (c.moldy() || c.rotten()) return;
        if (r.nextDouble() >= (c == Cond.WET ? MOLD_SPREAD_WET : MOLD_SPREAD_DRY)) return;
        level.setBlockAndUpdate(n, ns.setValue(COND, c.dirty() ? Cond.MOLD_DIRT : Cond.MOLD));
    }

    // ---------- бруд від корів ----------

    private static final Map<Level, Map<Long, Integer>> TRAMPLED = new java.util.WeakHashMap<>();

    private static Map<Long, Integer> trampled(Level level) {
        return TRAMPLED.computeIfAbsent(level, l -> new HashMap<>());
    }

    /** Корова стоїть (1) чи лежить (2) на цьому блоці секунду — накопичує бруд */
    public static void trample(Level level, BlockPos pos, int amount) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof CowHayBlock)) return;
        int v = trampled(level).merge(pos.asLong(), amount, Integer::sum);
        if (v < COW_DIRT_SECONDS) return;
        trampled(level).remove(pos.asLong());
        Cond c = s.getValue(COND);
        Cond n = switch (c) {
            case CLEAN, APPLE, CARROT, WET -> Cond.SLIGHTLY_DIRTY;
            case SLIGHTLY_DIRTY -> Cond.VERY_DIRTY;
            case MOLD -> Cond.MOLD_DIRT;
            default -> c;
        };
        if (n != c) level.setBlockAndUpdate(pos, s.setValue(COND, n));
    }

    // ---------- корови: поживність, ефекти, комфорт ----------

    public static int nutrition(BlockState s) {
        Cond c = s.getValue(COND);
        if (c.rotten()) return ROTTEN_FOOD;
        int base = BASE_FOOD[s.getValue(STAGE).ordinal()];
        int v = switch (c) {
            case APPLE, CARROT -> base + FRUIT_BONUS;
            case SLIGHTLY_DIRTY, MOLD -> base - 10;
            case VERY_DIRTY, MOLD_DIRT -> base - 15;
            default -> base;
        };
        return Math.max(0, v);
    }

    /** Чи може корова зараз це їсти (гниле — не тоді, коли слабка) */
    public static boolean safeFor(BlockState s, HungryCow cow) {
        return !(s.getValue(COND).rotten() && cow.getHealth() < ROTTEN_MIN_HEALTH);
    }

    public static int comfort(Level level, BlockPos floor) {
        BlockState s = level.getBlockState(floor);
        if (!(s.getBlock() instanceof CowHayBlock)) {
            if (s.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) || s.is(net.minecraft.tags.BlockTags.WOOL)
                    || s.is(net.minecraft.world.level.block.Blocks.HAY_BLOCK)) return COMFORT_GRASS;
            if (s.is(net.minecraft.tags.BlockTags.DIRT)) return COMFORT_DIRT;
            return COMFORT_STONE;
        }
        Cond c = s.getValue(COND);
        int v = COMFORT_STAGE[s.getValue(STAGE).ordinal()];
        if (c == Cond.SLIGHTLY_DIRTY) v += COMFORT_SLIGHT;
        else if (c == Cond.VERY_DIRTY || c == Cond.MOLD_DIRT) v += COMFORT_VERY;
        else if (c == Cond.WET) v += COMFORT_WET;
        if (c.rotten() || c.moldy()) v += COMFORT_ROTTEN;
        if (pileSize(level, floor, HAY_PILE_MIN) >= HAY_PILE_MIN) v += COMFORT_PILE;
        return Math.max(0, v);
    }

    // ---------- ламання ----------

    @Override
    public List<ItemStack> getDrops(BlockState s, LootParams.Builder params) {
        List<ItemStack> out = new ArrayList<>();
        Cond c = s.getValue(COND);
        RandomSource r = params.getLevel().getRandom();
        if (c.rotten()) {
            out.add(new ItemStack(Items.DIRT));                          // гниле — земля
        } else if (c.dirty()) {
            out.add(new ItemStack(Items.DIRT));                          // брудне — земля і 2–3 пшениці (Fij)
            out.add(new ItemStack(Items.WHEAT, 2 + r.nextInt(2)));
        } else if (c == Cond.MOLD) {
            out.add(new ItemStack(Items.WHEAT, 2 + r.nextInt(2)));       // пліснява без бруду — лише пшениця (спитати Fij)
        } else {
            out.add(stackOf(s));                                         // чисте, мокре, з фруктами — сам блок з тим самим станом
        }
        return out;
    }

    /** Предмет-блок із тим самим станом (стадія, стан, нитки) */
    public static ItemStack stackOf(BlockState s) {
        ItemStack st = new ItemStack(ModBlocks.HAY_ITEM.get());
        CompoundTag bs = new CompoundTag();
        bs.putString("stage", s.getValue(STAGE).id);
        bs.putString("condition", s.getValue(COND).id);
        bs.putString("bound", String.valueOf(s.getValue(BOUND)));
        st.getOrCreateTag().put("BlockStateTag", bs);
        return st;
    }

    /** Номер вигляду предмета (як у tools/make_hay_assets.py): (стадія × 11 + стан) × 2 + нитки; без тегу — 0 (свіже чисте) */
    public static int variant(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("BlockStateTag")) return 0;
        CompoundTag bs = tag.getCompound("BlockStateTag");
        int st = 0, c = 0;
        for (Stage s : Stage.values()) if (s.id.equals(bs.getString("stage"))) st = s.ordinal();
        for (Cond x : Cond.values()) if (x.id.equals(bs.getString("condition"))) c = x.ordinal();
        return (st * Cond.values().length + c) * 2 + ("true".equals(bs.getString("bound")) ? 1 : 0);
    }

    /** «Сухе, трохи брудне, зв'язане» — для назви предмета й книги */
    public static String describe(String stage, String cond, String bound) {
        StringBuilder b = new StringBuilder();
        for (Stage s : Stage.values()) if (s.id.equals(stage)) b.append(s.title);
        for (Cond c : Cond.values()) if (c.id.equals(cond) && c != Cond.CLEAN) b.append(b.length() > 0 ? ", " : "").append(c.title);
        if (bound != null) b.append(b.length() > 0 ? ", " : "").append("true".equals(bound) ? "зв'язане" : "незв'язане");
        return b.toString();
    }
}
