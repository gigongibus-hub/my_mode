package com.gigongibus.hungrycows.chop;

import com.gigongibus.hungrycows.HungryCowsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * РОЗРУБКА ДЕРЕВА (Fij, 18.11: «Charged Attack + Power Gauge»). Колода темного дуба на землі (під нею твердий блок, над нею —
 * не стовбур і не листя) або шматок у купі. Затиснув ЛКМ із сокирою (не присівши) — з'являється рамка розрубу й шкала сили, що
 * наповнюється, поки тримаєш (`client/ChopClient`); стрілки ←/→ зсувають рамку, ↑/↓ повертають; відпустив — удар (`ChopPacket` → `chop`).
 * Розруб уздовж волокон легший за поперечний, більший шматок — важчий. Замало сили — сокира застрягає (наступний удар туди ж
 * доповнює); вистачило — розруб рівно по рамці; засильно — так само рівно, але тріски летять і сокира зношується вдвічі. Обидві частини лишаються лежати купою (`WoodPileBlock`): рубай далі
 * (половини → четвертини → … → 16), забирай ПКМ, складай будь-які разом.
 */
public final class Chopping {
    private Chopping() {
    }

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int GAUGE_TICKS = 30;                // шкала сили наповнюється за 1,5 с, далі стоїть повна
    public static final float ALONG_BASE = 0.2F;             // скільки сили треба: уздовж волокон (розколоти) — від 0,2…
    public static final float ACROSS_BASE = 0.45F;           // …упоперек (перерубати) — від 0,45
    public static final float AREA_NEED = 0.5F;              // + 0,5 × площа розрубу / площа колоди (більше дерева — важче)
    // Fij (18.11: «рубиться криво й не по лінії») — розруб ЗАВЖДИ точно по рамці; було: забагато сили — лінія з'їжджала на 1–2
    // і губилось по пікселю. Тепер забагато — лише тріски летять і сокира зношується вдвічі.
    public static final float GOOD_RANGE = 0.2F;             // понад потрібне більше, ніж на стільки, — «засильно»
    public static final int HARD_EXTRA_WEAR = 1;             // засильний удар — сокира −1 ще
    public static final int MIN_PIECE = 1;                   // тонше 1 пікселя не рубається
    public static final double REACH = 6.0;                  // блоків
    public static final int AXE_WEAR = 1;                    // сокира −1 за удар

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HungryCowsMod.MODID);

    public static final RegistryObject<Block> WOOD_PILE = HungryCowsMod.BLOCKS.register("wood_pile", () ->
            new WoodPileBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(1.0F).sound(SoundType.WOOD).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<WoodPileBlockEntity>> WOOD_PILE_BE = BLOCK_ENTITIES.register("wood_pile", () ->
            BlockEntityType.Builder.of(WoodPileBlockEntity::new, WOOD_PILE.get()).build(null));
    public static final RegistryObject<Item> WOOD_PIECE = HungryCowsMod.ITEMS.register("wood_piece", () ->
            new WoodPieceItem(new Item.Properties().stacksTo(16)));
    /** Перев'язана купа (Fij, 18.11): зламав перев'язану — вся купа одним предметом, поставив — стоїть так само */
    public static final RegistryObject<Item> WOOD_BUNDLE = HungryCowsMod.ITEMS.register("wood_bundle", () ->
            new WoodBundleItem(new Item.Properties().stacksTo(1)));

    public static void init() {
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }

    // ---------- що можна рубати ----------

    /** Ванільна колода темного дуба (Fij: «поки темний дуб») */
    public static boolean isDarkOak(BlockState s) {
        return s.is(Blocks.DARK_OAK_LOG) || s.is(Blocks.DARK_OAK_WOOD) || s.is(Blocks.STRIPPED_DARK_OAK_LOG) || s.is(Blocks.STRIPPED_DARK_OAK_WOOD);
    }

    /** Дуб (Fij, 19.11: повалений дуб рубається нашим способом) */
    public static boolean isOak(BlockState s) {
        return s.is(Blocks.OAK_LOG) || s.is(Blocks.OAK_WOOD) || s.is(Blocks.STRIPPED_OAK_LOG) || s.is(Blocks.STRIPPED_OAK_WOOD);
    }

    /** Ялина (Fij, 20.11: «додай ще сосну» — у грі сосни немає) */
    public static boolean isSpruce(BlockState s) {
        return s.is(Blocks.SPRUCE_LOG) || s.is(Blocks.SPRUCE_WOOD) || s.is(Blocks.STRIPPED_SPRUCE_LOG) || s.is(Blocks.STRIPPED_SPRUCE_WOOD);
    }

    /** Вид дерева колоди, яку можна рубати; null — не рубається */
    public static String woodOf(BlockState s) {
        return isDarkOak(s) ? WoodPiece.DARK_OAK : isOak(s) ? WoodPiece.OAK : isSpruce(s) ? WoodPiece.SPRUCE : null;
    }

    /**
     * Колода «на землі»: під нею твердий верх, над нею не стовбур і не листя (дерево в лісі рубається, як завжди). Лежача колода
     * (поваленого дерева) — і з листям над нею (Fij, 20.11: повалене дерево лягає разом з листям).
     */
    public static boolean loggable(BlockGetter level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (woodOf(s) == null) return false;
        BlockState up = level.getBlockState(pos.above());
        boolean lying = s.hasProperty(RotatedPillarBlock.AXIS) && s.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y;
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                && !(up.is(BlockTags.LOGS) && up.hasProperty(RotatedPillarBlock.AXIS) && up.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y)
                && (lying || !up.is(BlockTags.LEAVES));
    }

    /** Шматки, які можна рубати на цьому місці (колода — одна ціла); null — тут нічого рубати */
    public static List<WoodPiece> targetPieces(BlockGetter level, BlockPos pos) {
        // стовбур дерева (Fij): ціла колода, рубати лише горизонтально (`Felling`)
        if (com.gigongibus.hungrycows.fell.Felling.isTrunk(level, pos)) {
            List<WoodPiece> l = new ArrayList<>();
            l.add(WoodPiece.whole(Direction.Axis.Y, true, false, com.gigongibus.hungrycows.fell.Felling.woodAt(level, pos)));
            return l;
        }
        if (level.getBlockEntity(pos) instanceof WoodPileBlockEntity pile) return pile.pieces().isEmpty() || pile.strings() > 0 ? null : pile.pieces();
        if (!loggable(level, pos)) return null;
        BlockState s = level.getBlockState(pos);
        Direction.Axis axis = s.hasProperty(RotatedPillarBlock.AXIS) ? s.getValue(RotatedPillarBlock.AXIS) : Direction.Axis.Y;
        String id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(s.getBlock()).getPath();
        boolean bark = !id.startsWith("stripped_");
        boolean allSides = id.endsWith("_wood");
        List<WoodPiece> l = new ArrayList<>();
        l.add(WoodPiece.whole(axis, bark, allSides, woodOf(s)));
        return l;
    }

    public static boolean holdsAxe(Player p) {
        return p.getMainHandItem().getItem() instanceof AxeItem;
    }

    /** Шматок, у який влучила точка (світові координати) на грані face; −1 — жоден */
    public static int pieceAt(List<WoodPiece> pieces, BlockPos pos, Vec3 hit, Direction face) {
        double px = (hit.x - pos.getX()) * 16 - face.getStepX() * 0.01, py = (hit.y - pos.getY()) * 16 - face.getStepY() * 0.01,
                pz = (hit.z - pos.getZ()) * 16 - face.getStepZ() * 0.01;
        for (int i = pieces.size() - 1; i >= 0; i--) if (pieces.get(i).contains(px, py, pz, 0.02)) return i;
        return -1;
    }

    /** Куди дивиться гравець: шматок і грань (промінь крізь бруски); null — мимо */
    public static int[] pick(List<WoodPiece> pieces, BlockPos pos, Vec3 eye, Vec3 look) {
        Vec3 o = eye.subtract(pos.getX(), pos.getY(), pos.getZ()).scale(16), d = look;
        double best = Double.MAX_VALUE;
        int[] res = null;
        for (int i = 0; i < pieces.size(); i++) {
            WoodPiece p = pieces.get(i);
            double[] mn = { p.x, p.y, p.z }, mx = { p.x + p.sx, p.y + p.sy, p.z + p.sz }, oo = { o.x, o.y, o.z }, dd = { d.x, d.y, d.z };
            double tmin = 0, tmax = REACH * 16;
            int axis = -1;
            boolean ok = true;
            for (int a = 0; a < 3 && ok; a++) {
                if (Math.abs(dd[a]) < 1e-9) { if (oo[a] < mn[a] || oo[a] > mx[a]) ok = false; continue; }
                double t1 = (mn[a] - oo[a]) / dd[a], t2 = (mx[a] - oo[a]) / dd[a];
                if (t1 > t2) { double t = t1; t1 = t2; t2 = t; }
                if (t1 > tmin) { tmin = t1; axis = a; }
                tmax = Math.min(tmax, t2);
                if (tmin > tmax) ok = false;
            }
            if (!ok || axis < 0 || tmin >= best) continue;
            best = tmin;
            Direction.Axis ax = Direction.Axis.values()[axis];
            Direction face = Direction.fromAxisAndDirection(ax, dd[axis] > 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
            res = new int[]{ i, face.get3DDataValue() };
        }
        return res;
    }

    /** Скільки сили треба, щоб розрубати шматок площиною поперек axis */
    public static float need(WoodPiece p, Direction.Axis axis) {
        return (axis == p.grain ? ACROSS_BASE : ALONG_BASE) + AREA_NEED * p.cutArea(axis) / (float) (WoodPiece.FULL * WoodPiece.FULL);
    }

    public enum Result { STUCK, CLEAN, HARD, NONE }

    /**
     * Сама розрубка (без світу): шматок index у списку, площина поперек axis на відстані at від початку шматка, сила 0…1.
     * Список змінюється: шматок замінюють дві частини (або лишається з засічкою).
     */
    public static Result chopPieces(List<WoodPiece> pieces, int index, Direction.Axis axis, int at, float power, RandomSource rnd) {
        if (index < 0 || index >= pieces.size()) return Result.NONE;
        WoodPiece p = pieces.get(index);
        int n = p.size(axis);
        if (n < 2 * MIN_PIECE || at < MIN_PIECE || at > n - MIN_PIECE) return Result.NONE;
        float need = need(p, axis);
        boolean sameNotch = p.notchAxis == axis.ordinal() && p.notchAt == at;
        float total = power + (sameNotch ? p.notch : 0);
        if (total < need) {                              // застрягла
            p.notch = total;
            p.notchAxis = axis.ordinal();
            p.notchAt = at;
            return Result.STUCK;
        }
        WoodPiece[] two = p.split(axis, at, 0);           // точно по рамці
        if (two == null) return Result.NONE;
        pieces.set(index, two[0]);
        pieces.add(index + 1, two[1]);
        return power > need + GOOD_RANGE ? Result.HARD : Result.CLEAN;
    }

    /** Удар гравця (з пакета): перевірки, розруб, звук, тріски, знос сокири */
    public static Result chop(ServerLevel level, Player player, BlockPos pos, int index, Direction.Axis axis, int at, float power) {
        if (!holdsAxe(player) || player.isShiftKeyDown() || player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH * REACH) return Result.NONE;
        if (com.gigongibus.hungrycows.fell.Felling.isTrunk(level, pos))         // стовбур — зарубка, лише горизонтально
            return axis == Direction.Axis.Y ? com.gigongibus.hungrycows.fell.Felling.cut(level, player, pos, at, power) : Result.NONE;
        List<WoodPiece> pieces = targetPieces(level, pos);
        if (pieces == null) return Result.NONE;
        power = Math.max(0, Math.min(1, power));
        boolean wasLog = !(level.getBlockEntity(pos) instanceof WoodPileBlockEntity);
        List<WoodPiece> work = new ArrayList<>(pieces);
        WoodPiece target = index >= 0 && index < work.size() ? work.get(index).copy() : null;
        if (target == null) return Result.NONE;
        work.set(index, target);
        Result r = chopPieces(work, index, axis, at, power, level.random);
        if (r == Result.NONE) return r;
        // Fij (20.11): розрубані половинки розлітаються — фізичними тілами (`phys/WoodBodyEntity`), лягли — знову купа
        List<WoodPiece> halves = new ArrayList<>();
        if (r != Result.STUCK) {
            halves.add(work.remove(index));
            halves.add(work.remove(index));
        }
        if (wasLog) level.setBlock(pos, work.isEmpty() ? Blocks.AIR.defaultBlockState() : WOOD_PILE.get().defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof WoodPileBlockEntity pile) {
            if (work.isEmpty()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            else pile.setPieces(work);
        }
        if (!halves.isEmpty()) flyApart(level, pos, halves, axis, power, r == Result.HARD, player);
        // видно й чути
        effects(level, new Vec3(pos.getX() + (target.x + target.sx / 2.0) / 16, pos.getY() + (target.y + target.sy / 2.0) / 16,
                pos.getZ() + (target.z + target.sz / 2.0) / 16), target.wood, r, player);
        return r;
    }

    /** Звук, тріски, помах і знос сокири після удару (і по колоді, і по фізичному тілу) */
    public static void effects(ServerLevel level, Vec3 at, String wood, Result r, Player player) {
        BlockPos pos = BlockPos.containing(at);
        var chips = new BlockParticleOption(ParticleTypes.BLOCK, WoodPiece.block(wood, true).defaultBlockState());
        switch (r) {
            case STUCK -> {
                level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 0.8F, 0.6F);
                level.sendParticles(chips, at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0.02);
            }
            case CLEAN -> {
                level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1F, 1.1F);
                level.sendParticles(chips, at.x, at.y, at.z, 8, 0.1, 0.1, 0.1, 0.05);
            }
            case HARD -> {
                level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1F, 0.7F);
                level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1F, 1.4F);
                level.sendParticles(chips, at.x, at.y, at.z, 24, 0.2, 0.2, 0.2, 0.12);
            }
            default -> {
                return;
            }
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        if (!player.getAbilities().instabuild)
            player.getMainHandItem().hurtAndBreak(AXE_WEAR + (r == Result.HARD ? HARD_EXTRA_WEAR : 0), player,
                    pl -> pl.broadcastBreakEvent(EquipmentSlot.MAINHAND));
    }

    /** Ціла колода (блок) як шматок для розрубки; null — не рубається */
    public static WoodPiece pieceOfLog(BlockState s) {
        String wood = woodOf(s);
        if (wood == null) return null;
        Direction.Axis axis = s.hasProperty(RotatedPillarBlock.AXIS) ? s.getValue(RotatedPillarBlock.AXIS) : Direction.Axis.Y;
        String id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(s.getBlock()).getPath();
        return WoodPiece.whole(axis, !id.startsWith("stripped_"), id.endsWith("_wood"), wood);
    }

    // Fij (20.11: «відскакує занадто сильно») — було 1 + 1,5 × сила, угору 0,8, обертання 3
    public static final double SPLIT_SPEED = 0.35;           // половинки розпадаються: 0,35 бл./с …
    public static final double SPLIT_PER_POWER = 0.5;        // … + 0,5 за повну шкалу
    public static final double SPLIT_HARD = 1.5;             // засильно — у 1,5 раза швидше
    public static final double SPLIT_UP = 0.25;              // і ледь угору
    public static final double SPLIT_SPIN = 1.0;             // обертаються до 1 рад/с

    /** Дві половинки — у різні боки від площини розрубу (вниз, у землю, не летять), трохи вгору й з обертанням */
    private static void flyApart(ServerLevel level, BlockPos pos, List<WoodPiece> halves, Direction.Axis axis, float power, boolean hard, Player player) {
        double speed = (SPLIT_SPEED + SPLIT_PER_POWER * power) * (hard ? SPLIT_HARD : 1);
        for (int i = 0; i < halves.size(); i++) {
            double sg = i == 0 ? -1 : 1;
            org.joml.Vector3d v = new org.joml.Vector3d(axis == Direction.Axis.X ? sg : 0, axis == Direction.Axis.Y ? sg : 0, axis == Direction.Axis.Z ? sg : 0)
                    .mul(speed);
            if (v.y < 0) v.y = 0;
            v.y += SPLIT_UP * (0.5 + power);
            org.joml.Vector3d w = new org.joml.Vector3d(level.random.nextGaussian(), level.random.nextGaussian(), level.random.nextGaussian())
                    .mul(SPLIT_SPIN * power / 2);
            com.gigongibus.hungrycows.phys.WoodBodyEntity.spawnPiece(level, halves.get(i),
                    new org.joml.Vector3d(pos.getX(), pos.getY(), pos.getZ()), v, w);
        }
    }

    /** Для клієнта: чи можна рубати те, на що дивиться гравець */
    public static boolean canChop(Level level, Player p, BlockPos pos) {
        return holdsAxe(p) && !p.isShiftKeyDown() && targetPieces(level, pos) != null;
    }
}
