package com.gigongibus.hungrycows.carve;

import com.gigongibus.hungrycows.HungryCowsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * СПРАВЖНЯ РІЗЬБА У СВІТІ (Fij, 17.11: «стара система — для переміщення чи цвяхів; зроби справжню різьбу, але в майні»).
 * Стара (вікно з фізикою) лишається. Нова — лише з обтесаної ЯЛИНИ (Fij: «з дерева сосни»; сосни в грі немає).
 * Креслення + обтесана ялина на верстаку, як було. Зубило в основній руці, киянка в другій, ЛКМ по колоді — удар:
 * зубило стає туди, куди дивишся, і йде в ту грань, яку бачиш. Сила удару — скільки чекав після попереднього (як замах мечем).
 * <ul>
 *   <li>Дивишся на грань майже прямо — РУБАЄ: вглиб на 1–4 (сильніше — глибше), лезо 3 пікселі завширшки.</li>
 *   <li>Дивишся на грань навскіс — СТРУГАЄ: верхній шар, смужка 1–4 у той бік, куди дивишся.</li>
 *   <li>Волокна йдуть уздовж колоди: у торець рубати важко (удвічі мілкіше), упоперек волокон — на 1 мілкіше,
 *       стругати вздовж — на 1 довше. Сильний удар лезом уздовж волокон може СКОЛОТИ шмат аж до торця.</li>
 *   <li>Присів — лезо повернуте (на боковій грані — сторч). Без киянки — лише рукою: найслабший удар.</li>
 *   <li>Відрізане відпадає (лишається найбільший шматок); шматок, що вже не лежить на кресленні, осідає на нього.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = HungryCowsMod.MODID)
public final class VoxelCarve {
    private VoxelCarve() {
    }

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int OFFSET_XZ = (16 - VoxelMask.N) / 2;    // колода посередині блока (у пікселях)
    public static final int OFFSET_Y = 1;                           // на 1 піксель вище — під нею креслення
    public static final int EDGE_WIDTH = 3;                         // ширина леза (середнє зубило), пікселі
    public static final int CHOP_MAX_DEPTH = 4;                     // найглибше за удар
    public static final int PARE_MAX_LENGTH = 4;                    // найдовша смужка, коли стругаєш
    public static final double CHOP_MAX_ANGLE = 40;                 // кут від прямого, до якого — рубає; більший — стругає
    public static final float END_GRAIN_DEPTH = 0.5F;               // у торець — удвічі мілкіше
    public static final int ACROSS_GRAIN_LESS = 1;                  // упоперек волокон — на 1 мілкіше
    public static final int ALONG_GRAIN_PARE_MORE = 1;              // стругати вздовж волокон — на 1 довше
    public static final float SPLIT_MIN_STRENGTH = 0.6F;            // сколює лише сильний удар…
    public static final float SPLIT_CHANCE = 0.75F;                 // …і не завжди
    public static final int SPLIT_MAX = 5;                          // скол іде вздовж волокон щонайдалі на 5 пікс. (до торця/порожнечі)
    public static final int CHARGE_TICKS = 20;                      // через 1 с після удару — повна сила
    public static final float MIN_STRENGTH = 0.1F;                  // зовсім поспіль — найслабший
    public static final float HAND_STRENGTH = 0.1F;                 // без киянки (рукою)
    public static final double REACH = 5.0;                         // блоків
    public static final int MAX_PARTICLES = 24;

    private static final Map<Player, Long> LAST_STRIKE = new WeakHashMap<>();

    public static boolean isSpruce(Item log) {
        return log == Items.STRIPPED_SPRUCE_LOG || log == Items.STRIPPED_SPRUCE_WOOD;
    }

    /** Що зробив удар: прибране лезом, сколене, відпале */
    public static final class Result {
        public final List<int[]> cut = new ArrayList<>(), split = new ArrayList<>();
        public List<int[]> loose = List.of();
        public boolean chop;
        public int sink;          // на скільки шарів осів шматок, що вже не лежав на кресленні
        public final List<Float> parts = new ArrayList<>();     // готові деталі, що випали (точність кожної)
        public final List<int[]> partCells = new ArrayList<>();

        public int total() {
            return cut.size() + split.size() + loose.size();
        }
    }

    // ---------- сама різьба (без світу — для тестів) ----------

    /**
     * Удар у клітинку (hx, hy, hz), що дивиться назовні гранню face. look — куди дивиться гравець (одиничний),
     * strength 0…1, turned — лезо повернуте (присів).
     */
    public static Result strike(VoxelMask m, int hx, int hy, int hz, Direction face, Vec3 look, float strength, boolean turned, Random rnd) {
        return strike(m, hx, hy, hz, face, look, strength, turned, rnd, null);
    }

    /** template — клітинки деталі з креслення: шматок, що відокремився й схожий на неї, випадає готовою деталлю */
    public static Result strike(VoxelMask m, int hx, int hy, int hz, Direction face, Vec3 look, float strength, boolean turned, Random rnd,
                                List<int[]> template) {
        Result r = new Result();
        Direction.Axis a = face.getAxis();
        int[] in = { -face.getStepX(), -face.getStepY(), -face.getStepZ() };
        double cos = Math.abs(look.x * face.getStepX() + look.y * face.getStepY() + look.z * face.getStepZ());
        r.chop = cos >= Math.cos(Math.toRadians(CHOP_MAX_ANGLE));
        Direction.Axis e;
        int[] t = null;
        if (r.chop) {
            if (a == Direction.Axis.Y) e = Math.abs(look.x) > Math.abs(look.z) ? Direction.Axis.Z : Direction.Axis.X;
            else e = a == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            if (turned) e = a == Direction.Axis.Y ? (e == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X) : Direction.Axis.Y;
        } else {
            Direction.Axis p = null, q = null;
            for (Direction.Axis ax : Direction.Axis.values()) if (ax != a) { if (p == null) p = ax; else q = ax; }
            Direction.Axis ta = Math.abs(comp(look, p)) >= Math.abs(comp(look, q)) ? p : q;
            e = ta == p ? q : p;
            int s = comp(look, ta) >= 0 ? 1 : -1;
            t = unit(ta, s);
        }
        int[] eu = unit(e, 1);
        int lo = -(EDGE_WIDTH - 1) / 2, hi = EDGE_WIDTH / 2;
        if (r.chop) {
            int d = 1 + Math.round(strength * (CHOP_MAX_DEPTH - 1));
            if (a == Direction.Axis.X) d = Math.max(1, Math.round(d * END_GRAIN_DEPTH));
            else if (e != Direction.Axis.X) d = Math.max(1, d - ACROSS_GRAIN_LESS);
            for (int k = 0; k < d; k++)
                for (int o = lo; o <= hi; o++) take(m, r.cut, hx + o * eu[0] + k * in[0], hy + o * eu[1] + k * in[1], hz + o * eu[2] + k * in[2]);
            // скол: лезо вздовж волокон, удар сильний — тріщина йде вздовж x до торця або порожнечі
            if (e == Direction.Axis.X && a != Direction.Axis.X && strength >= SPLIT_MIN_STRENGTH && rnd.nextFloat() < SPLIT_CHANCE)
                for (int dir = -1; dir <= 1; dir += 2) {
                    int start = dir > 0 ? hx + hi + 1 : hx + lo - 1;
                    int end = -1;
                    for (int s = 0; s <= SPLIT_MAX; s++) {
                        int x = start + dir * s;
                        if (x < 0 || x >= VoxelMask.N || !columnHasWood(m, x, hy, hz, in, d)) { end = s; break; }
                    }
                    if (end <= 0) continue;
                    for (int s = 0; s < end; s++)
                        for (int k = 0; k < d; k++) take(m, r.split, start + dir * s, hy + k * in[1], hz + k * in[2]);
                }
        } else {
            int len = 1 + Math.round(strength * (PARE_MAX_LENGTH - 1));
            if (t[0] != 0) len += ALONG_GRAIN_PARE_MORE;
            for (int j = 0; j < len; j++)
                for (int o = lo; o <= hi; o++) take(m, r.cut, hx + o * eu[0] + j * t[0], hy + o * eu[1] + j * t[1], hz + o * eu[2] + j * t[2]);
        }
        separate(m, template, r);
        return r;
    }

    /**
     * Після удару (Fij, 17.11): шматок, що відокремився й схожий на деталь з креслення, — ВИПАДАЄ готовою деталлю (де б його не
     * вирізали); з решти лишається найбільший шматок (дерево лишається — можна різати ще), дрібне відпадає стружкою; що вже не лежить
     * на кресленні — осідає на нього.
     */
    public static void separate(VoxelMask m, List<int[]> template, Result r) {
        List<List<int[]>> comps = m.components();
        List<List<int[]>> rest = new ArrayList<>();
        for (List<int[]> c : comps) {
            float q = template == null ? -1 : Carving.quality3d(PartMatcher.best(c, template));
            if (q >= 0) {
                r.parts.add(q);
                r.partCells.addAll(c);
                for (int[] v : c) m.clear(v[0], v[1], v[2]);
            } else rest.add(c);
        }
        int best = -1;
        for (int k = 0; k < rest.size(); k++) if (best < 0 || rest.get(k).size() > rest.get(best).size()) best = k;
        List<int[]> loose = new ArrayList<>();
        for (int k = 0; k < rest.size(); k++) if (k != best) loose.addAll(rest.get(k));
        for (int[] v : loose) m.clear(v[0], v[1], v[2]);
        r.loose = loose;
        int low = m.lowest();
        if (low > 0 && low < VoxelMask.N) { m.shiftDown(low); r.sink = low; }
    }

    private static boolean columnHasWood(VoxelMask m, int x, int hy, int hz, int[] in, int d) {
        for (int k = 0; k < d; k++) if (m.get(x, hy + k * in[1], hz + k * in[2])) return true;
        return false;
    }

    private static void take(VoxelMask m, List<int[]> out, int x, int y, int z) {
        if (m.clear(x, y, z)) out.add(new int[]{ x, y, z });
    }

    private static double comp(Vec3 v, Direction.Axis a) {
        return a == Direction.Axis.X ? v.x : a == Direction.Axis.Y ? v.y : v.z;
    }

    private static int[] unit(Direction.Axis a, int s) {
        return new int[]{ a == Direction.Axis.X ? s : 0, a == Direction.Axis.Y ? s : 0, a == Direction.Axis.Z ? s : 0 };
    }

    /** Куди влучив погляд: перша клітинка з деревом і грань, через яку промінь у неї ввійшов; null — мимо */
    public static int[] pick(VoxelMask m, BlockPos pos, Vec3 eye, Vec3 look) {
        double ox = (eye.x - pos.getX()) * 16 - OFFSET_XZ, oy = (eye.y - pos.getY()) * 16 - OFFSET_Y, oz = (eye.z - pos.getZ()) * 16 - OFFSET_XZ;
        int px = (int) Math.floor(ox), py = (int) Math.floor(oy), pz = (int) Math.floor(oz);
        for (double t = 0; t < REACH * 16; t += 0.02) {
            int x = (int) Math.floor(ox + look.x * t), y = (int) Math.floor(oy + look.y * t), z = (int) Math.floor(oz + look.z * t);
            if (x == px && y == py && z == pz) continue;
            if (m.get(x, y, z)) {
                Direction f;
                if (x != px) f = x < px ? Direction.EAST : Direction.WEST;
                else if (y != py) f = y < py ? Direction.UP : Direction.DOWN;
                else f = z < pz ? Direction.SOUTH : Direction.NORTH;
                return new int[]{ x, y, z, f.get3DDataValue() };
            }
            px = x; py = y; pz = z;
        }
        return null;
    }

    // ---------- у світі ----------

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock e) {
        if (e.getLevel().isClientSide || e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        Player p = e.getEntity();
        if (!p.getMainHandItem().is(Carving.CHISEL.get())) return;
        if (!(e.getLevel().getBlockEntity(e.getPos()) instanceof WorkpieceBlockEntity be) || !be.hasLog()) return;
        if (be.voxels() == null) {
            if (!isSpruce(be.log()) || !be.mask().untouched()) return;     // інше дерево чи вже почате у вікні — стара різьба
            be.startVoxels();
        }
        hit(be, p, (ServerLevel) e.getLevel());
    }

    /** Удар гравця по заготовці (з подій і з тестів) */
    public static Result hit(WorkpieceBlockEntity be, Player p, ServerLevel level) {
        VoxelMask m = be.voxels();
        if (m == null) return null;
        BlockPos pos = be.getBlockPos();
        int[] h = pick(m, pos, p.getEyePosition(), p.getLookAngle());
        if (h == null) return null;
        boolean mallet = p.getOffhandItem().is(Carving.MALLET.get());
        long now = level.getGameTime();
        Long last = LAST_STRIKE.put(p, now);
        float charge = last == null ? 1 : Math.max(MIN_STRENGTH, Math.min(1, (now - last) / (float) CHARGE_TICKS));
        float strength = mallet ? charge : HAND_STRENGTH;
        BlueprintItem bp = be.blueprint() instanceof BlueprintItem b ? b : null;
        VoxelMask keep = bp == null ? null : CarveTemplates.keep3d(level.getServer(), bp.template);
        Result r = strike(m, h[0], h[1], h[2], Direction.from3DDataValue(h[3]), p.getLookAngle(), strength, p.isShiftKeyDown(), new Random(),
                keep == null ? null : PartMatcher.cells(keep));
        if (r.sink > 0) be.sinkGuide(r.sink);
        // готова деталь випадає сама (з точністю); місце для наступної — знову чорним, поки вміщається
        for (float q : r.parts) {
            var item = new net.minecraft.world.entity.item.ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                    CarvedPartItem.withQuality(new net.minecraft.world.item.ItemStack(bp.result.get()), q));
            item.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.1, 0.2, (level.random.nextDouble() - 0.5) * 0.1);
            level.addFreshEntity(item);
        }
        if (!r.parts.isEmpty()) {
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 0.8F);
            if (m.count() == 0) be.clearLog();
            else be.setGuide(PartMatcher.findGuide(m, keep));
        }
        be.voxelsChanged();
        // видно й чути: стружка, тук киянки, тріск сколу
        var dust = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        int n = 0;
        for (List<int[]> l : List.of(r.cut, r.split, r.loose, r.partCells))
            for (int[] c : l) {
                if (n++ >= MAX_PARTICLES) break;
                level.sendParticles(dust, pos.getX() + (OFFSET_XZ + c[0] + 0.5) / 16, pos.getY() + (OFFSET_Y + c[1] + 0.5) / 16,
                        pos.getZ() + (OFFSET_XZ + c[2] + 0.5) / 16, 1, 0.01, 0.01, 0.01, 0.02);
            }
        level.playSound(null, pos, mallet ? SoundEvents.WOOD_HIT : SoundEvents.WOOD_STEP, SoundSource.BLOCKS, 0.4F + 0.6F * strength, 1.4F - 0.5F * strength);
        if (!r.split.isEmpty() || !r.loose.isEmpty()) level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.7F, 1.3F);
        if (!p.getAbilities().instabuild) {
            p.getMainHandItem().hurtAndBreak(1, p, pl -> pl.broadcastBreakEvent(EquipmentSlot.MAINHAND));
            if (mallet) p.getOffhandItem().hurtAndBreak(1, p, pl -> pl.broadcastBreakEvent(EquipmentSlot.OFFHAND));
        }
        return r;
    }

    /** Для тестів: забути, коли гравець бив (наступний удар — повна сила) */
    public static void forgetCharge(Player p) {
        LAST_STRIKE.remove(p);
    }

}
