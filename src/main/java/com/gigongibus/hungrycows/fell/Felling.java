package com.gigongibus.hungrycows.fell;

import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.Wind;
import com.gigongibus.hungrycows.chop.Chopping;
import com.gigongibus.hungrycows.chop.WoodPiece;
import com.gigongibus.hungrycows.phys.Part;
import com.gigongibus.hungrycows.phys.PhysicsWorld;
import com.gigongibus.hungrycows.phys.RigidBody;
import com.gigongibus.hungrycows.phys.WoodBodyEntity;
import org.joml.Vector3d;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
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
 * ПАДІННЯ ДЕРЕВА (Fij, 19.11: «як Tree Physics, але в десятки разів краще: смак, тяжкість дерева, його руйнівна сила»).
 * Дуб і ялина (Fij, 20.11: «додай ще сосну»; сосни в грі немає). Нижню колоду стовбура рубають нашою розрубкою (затиснув ЛКМ сокирою, шкала сили) — лише горизонтально: кожен удар
 * поглиблює зарубку з того боку, звідки рубаєш. Зарубка ~¾ стовбура — дерево тріщить і падає цілком — фізичним тілом (`phys/WoodBodyEntity`):
 * у бік зарубки (туди, звідки рубаєш — відбігай!), трохи туди, куди нахилена крона, і за вітром. Лишається пень.
 */
public final class Felling {
    private Felling() {
    }

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final float FELL_DEPTH = 12;       // зарубка 12 з 16 пікс. (¾ стовбура) — дерево падає
    public static final float CUT_MIN = 1;           // кожен удар заглиблює щонайменше на 1 пікс. …
    public static final float CUT_PER_POWER = 5;     // … і до +5 за повну шкалу (2–4 сильні удари на дуб; слабкими — довше)
    public static final int MIN_AT = 3, MAX_AT = 13; // висота розрубу в колоді (пікс.): не біля самого краю
    public static final int DEFAULT_AT = 5;          // спершу рамка низько — пень невисокий
    public static final double NOTCH_WEIGHT = 1.0;   // напрям падіння: бік зарубки …
    public static final double LEAN_WEIGHT = 0.6;    // … + нахил крони (на кожен блок зсуву центру ваги) …
    public static final double WIND_WEIGHT = 0.25;   // … + вітер
    public static final int AXE_WEAR = 1;            // сокира −1 за удар
    // Fij (20.11, «реальна фізика»): дерево хилиться на «шарнірі» — недорубаних волокнах — і відривається від пня
    public static final double HINGE_RELEASE_DEG = 30;   // волокна рвуться на 30° …
    public static final double HINGE_PER_PX = 3;         // … +3° за кожен недорубаний піксель (товща зарубка тримає довше)
    public static final double KICK_ALPHA = 0.25;        // поки центр ваги не перейшов через край пня — хилиться з таким прискоренням (рад/с²; Fij «падає швидше»: було 0,12)
    public static final double START_OMEGA = 0.03;       // з чого починає (рад/с) — тріщить і хилиться (було 0,012)

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HungryCowsMod.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HungryCowsMod.MODID);

    public static final RegistryObject<Block> FELLING_CUT = HungryCowsMod.BLOCKS.register("felling_cut", () ->
            new FellingCutBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<FellingCutBlockEntity>> FELLING_CUT_BE = BLOCK_ENTITIES.register("felling_cut", () ->
            BlockEntityType.Builder.of(FellingCutBlockEntity::new, FELLING_CUT.get()).build(null));

    public static void init() {
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
        ENTITIES.register(bus);
    }

    // ---------- що можна валити ----------

    /** Нижня колода стовбура (дуб, ялина; стоїть, під нею не колода, над нею — дерево з листям) або вже почата зарубка */
    public static boolean isTrunk(BlockGetter level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (level.getBlockEntity(pos) instanceof FellingCutBlockEntity be) return !be.felled();
        String wood = TreeShape.woodOf(s);
        if (wood == null || s.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y) return false;
        if (TreeShape.isLog(level.getBlockState(pos.below()), wood) || !TreeShape.isLog(level.getBlockState(pos.above()), wood)) return false;
        return cachedScan(level, pos);
    }

    /** Вид дерева стовбура / зарубки */
    public static String woodAt(BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof FellingCutBlockEntity be) return be.wood();
        String w = TreeShape.woodOf(level.getBlockState(pos));
        return w == null ? "oak" : w;
    }

    // огляд дерева — недешевий; клієнт питає щотіку, поки дивишся на стовбур
    private static BlockPos lastPos;
    private static BlockGetter lastLevel;
    private static long lastTime;
    private static boolean lastResult;

    private static boolean cachedScan(BlockGetter level, BlockPos pos) {
        long now = level instanceof Level l ? l.getGameTime() : 0;
        if (level == lastLevel && pos.equals(lastPos) && now - lastTime < 20) return lastResult;
        lastLevel = level;
        lastPos = pos.immutable();
        lastTime = now;
        lastResult = TreeShape.scan(level, pos) != null;
        return lastResult;
    }

    private static void startCut(Level level, BlockPos pos, Direction side, int at) {
        String wood = woodAt(level, pos);
        level.setBlock(pos, FELLING_CUT.get().defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof FellingCutBlockEntity be) be.start(side, Math.max(MIN_AT, Math.min(MAX_AT, at)), wood);
    }

    /** Удар сокирою по стовбуру (з `Chopping.chop`): зарубка глибшає; досить глибока — дерево падає */
    public static Chopping.Result cut(ServerLevel level, Player player, BlockPos pos, int at, float power) {
        if (!isTrunk(level, pos)) return Chopping.Result.NONE;
        if (!(level.getBlockEntity(pos) instanceof FellingCutBlockEntity)) {
            Vec3 to = player.position().subtract(Vec3.atCenterOf(pos));
            startCut(level, pos, Direction.getNearest(to.x, 0, to.z), at);
        }
        if (!(level.getBlockEntity(pos) instanceof FellingCutBlockEntity be)) return Chopping.Result.NONE;
        be.deepen(CUT_MIN + CUT_PER_POWER * Math.max(0, Math.min(1, power)));
        Vec3 notch = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(be.side().getNormal()).scale(0.5)).add(0, be.at() / 16.0 - 0.5, 0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, WoodPiece.block(be.wood(), true).defaultBlockState()),
                notch.x, notch.y, notch.z, 6, 0.1, 0.05, 0.1, 0.05);
        level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1F, 0.7F + power * 0.3F);
        player.swing(InteractionHand.MAIN_HAND, true);
        if (!player.getAbilities().instabuild)
            player.getMainHandItem().hurtAndBreak(AXE_WEAR, player, pl -> pl.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        if (be.depth() < FELL_DEPTH || !readyToFall(level, pos)) {
            if (be.depth() > FELL_DEPTH * 0.6F)          // уже тріщить — попередження
                level.playSound(null, pos, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.BLOCKS, 0.4F, 0.5F);
            return Chopping.Result.STUCK;
        }
        fell(level, pos, be);
        return Chopping.Result.CLEAN;
    }

    /** Велику ялину (2×2) тримає кожен стовбур: падає, лише коли всі підрубані досить глибоко */
    public static boolean readyToFall(ServerLevel level, BlockPos pos) {
        TreeShape t = TreeShape.scan(level, pos);
        if (t == null) return true;
        if (!t.holders.isEmpty()) return false;
        for (BlockPos c : t.cuts) if (level.getBlockEntity(c) instanceof FellingCutBlockEntity be && be.depth() < FELL_DEPTH) return false;
        return true;
    }

    /** Звалити одразу (команда, тести): зарубка з боку side на звичайній висоті (у всіх стовбурах) — і падає */
    public static WoodBodyEntity fellAt(ServerLevel level, BlockPos pos, Direction side) {
        if (!isTrunk(level, pos)) return null;
        TreeShape t = TreeShape.scan(level, pos);
        if (t == null) return null;
        List<BlockPos> all = new ArrayList<>(t.cuts);
        all.addAll(t.holders);
        for (BlockPos c : all) {
            if (!(level.getBlockEntity(c) instanceof FellingCutBlockEntity)) startCut(level, c, side, DEFAULT_AT);
            if (level.getBlockEntity(c) instanceof FellingCutBlockEntity be) be.deepen(FELL_DEPTH);
        }
        return level.getBlockEntity(pos) instanceof FellingCutBlockEntity be ? fell(level, pos, be) : null;
    }

    /**
     * Дерево падає (Fij, 20.11): дерево зі світу стає фізичним тілом (`WoodBodyEntity`) — колоди, листя й обрубок колоди над
     * зарубкою (раніше він зникав). Тіло хилиться на пні, поки волокна шарніра не порвуться, далі летить само.
     */
    public static WoodBodyEntity fell(ServerLevel level, BlockPos pos, FellingCutBlockEntity be) {
        TreeShape tree = TreeShape.scan(level, pos);
        if (tree == null) { be.fell(); return null; }
        // центр стовбура (у великої ялини — середина 4 стовбурів) на висоті розрубу
        double cx = 0, cz = 0, minAt = 16, maxDepth = 0;
        for (BlockPos c : tree.cuts) {
            cx += c.getX() + 0.5;
            cz += c.getZ() + 0.5;
            if (level.getBlockEntity(c) instanceof FellingCutBlockEntity cb) { minAt = Math.min(minAt, cb.at()); maxDepth = Math.max(maxDepth, cb.depth()); }
        }
        cx /= tree.cuts.size();
        cz /= tree.cuts.size();
        Vec3 center = new Vec3(cx, pos.getY() + be.at() / 16.0, cz);
        Vec3 dir = fallDirection(level, tree, center, be.side());
        double half = 0;
        for (BlockPos c : tree.cuts) half = Math.max(half, (c.getX() + 0.5 - cx) * dir.x + (c.getZ() + 0.5 - cz) * dir.z + 0.5);
        Vec3 pivot = center.add(dir.scale(half * 0.9));                 // шарнір — край пня з того боку, куди падає
        Vector3d o = new Vector3d(pivot.x, pivot.y, pivot.z);
        RigidBody body = new RigidBody();
        for (BlockPos p : tree.logs)
            body.parts.add(Part.block(level.getBlockState(p), new Vector3d(p.getX(), p.getY(), p.getZ()).sub(o), PhysicsWorld.LOG_MASS, false));
        for (BlockPos c : tree.cuts) {                                  // обрубок над зарубкою — частина дерева
            int at = level.getBlockEntity(c) instanceof FellingCutBlockEntity cb ? cb.at() : be.at();
            WoodPiece butt = WoodPiece.whole(Direction.Axis.Y, true, false, tree.wood);
            butt.y = at;
            butt.sy = 16 - at;
            butt.cut = (byte) (1 << Direction.DOWN.get3DDataValue());
            butt.faces[Direction.DOWN.get3DDataValue()] = WoodPiece.END;
            body.parts.add(Part.piece(butt, new Vector3d(c.getX(), c.getY(), c.getZ()).sub(o), PhysicsWorld.LOG_MASS));
        }
        for (BlockPos p : tree.leaves)
            body.parts.add(Part.block(level.getBlockState(p), new Vector3d(p.getX(), p.getY(), p.getZ()).sub(o), PhysicsWorld.LEAF_MASS, true));
        for (BlockPos p : tree.leaves) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        for (BlockPos p : tree.logs) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (BlockPos c : tree.cuts) if (level.getBlockEntity(c) instanceof FellingCutBlockEntity cb) cb.fell();
        body.pos.set(o);
        body.hinged = true;
        body.pivot.set(o);
        body.anchor.zero();
        body.fallDir.set(dir.x, 0, dir.z);
        body.axis.set(dir.z, 0, -dir.x);
        body.hingeRelease = Math.toRadians(HINGE_RELEASE_DEG + HINGE_PER_PX * Math.max(0, 16 - maxDepth));
        body.recompute();
        WoodBodyEntity e = new WoodBodyEntity(level, body, WoodBodyEntity.TREE);
        level.addFreshEntity(e);
        level.playSound(null, pos, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1.2F, 0.5F);
        level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.5F, 0.4F);
        return e;
    }

    /** Куди впаде: бік зарубки + нахил крони (зсув центру ваги) + вітер */
    public static Vec3 fallDirection(Level level, TreeShape tree, Vec3 pivot, Direction side) {
        double mx = 0, mz = 0, m = 0;
        for (BlockPos p : tree.logs) { mx += (p.getX() + 0.5 - pivot.x) * PhysicsWorld.LOG_MASS; mz += (p.getZ() + 0.5 - pivot.z) * PhysicsWorld.LOG_MASS; m += PhysicsWorld.LOG_MASS; }
        for (BlockPos p : tree.leaves) { mx += (p.getX() + 0.5 - pivot.x) * PhysicsWorld.LEAF_MASS; mz += (p.getZ() + 0.5 - pivot.z) * PhysicsWorld.LEAF_MASS; m += PhysicsWorld.LEAF_MASS; }
        Vec3 lean = m > 0 ? new Vec3(mx / m, 0, mz / m) : Vec3.ZERO;
        Wind w = Wind.of(level);
        Vec3 wind = w.calm() ? Vec3.ZERO : w.direction().multiply(1, 0, 1);
        Vec3 d = Vec3.atLowerCornerOf(side.getNormal()).scale(NOTCH_WEIGHT).add(lean.scale(LEAN_WEIGHT)).add(wind.scale(WIND_WEIGHT));
        if (d.lengthSqr() < 1e-6) d = Vec3.atLowerCornerOf(side.getNormal());
        return d.normalize();
    }
}
