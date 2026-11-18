package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.carve.CarveTemplates;
import com.gigongibus.hungrycows.carve.CarvedPartItem;
import com.gigongibus.hungrycows.carve.Carving;
import com.gigongibus.hungrycows.carve.VoxelCarve;
import com.gigongibus.hungrycows.carve.VoxelMask;
import com.gigongibus.hungrycows.carve.WorkpieceBlock;
import com.gigongibus.hungrycows.carve.WorkpieceBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.Random;

/** Справжня різьба у світі (Fij, 17.11): зубило + киянка, волокна, скол, стругання, відпадання, деталь за об'ємним шаблоном */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowVoxelTests {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Vec3 DOWN = new Vec3(0, -1, 0);

    /** Кубик, що ніколи не випадає «скол» (або завжди) */
    private static Random dice(float v) {
        return new Random() {
            @Override public float nextFloat() { return v; }
        };
    }

    // 1. Правила: легкий удар — 3 (лезо 3 × глибина 1), сильний — 12; у торець — удвічі мілкіше; лезо впоперек волокон — на 1 мілкіше;
    //    сильний удар лезом уздовж волокон біля торця — скол до торця; навскіс — стругає верхній шар; відрізане відпадає
    @GameTest(template = "arena", batch = "carve3d", timeoutTicks = 20)
    public static void carvingRules(GameTestHelper h) {
        VoxelMask m = new VoxelMask();
        int light = VoxelCarve.strike(m, 5, 11, 5, Direction.UP, DOWN, 0, false, dice(1)).total();
        m = new VoxelMask();
        int strong = VoxelCarve.strike(m, 5, 11, 5, Direction.UP, DOWN, 1, false, dice(1)).total();
        m = new VoxelMask();
        int endGrain = VoxelCarve.strike(m, 11, 6, 6, Direction.EAST, new Vec3(-1, 0, 0), 1, false, dice(1)).total();
        m = new VoxelMask();
        int across = VoxelCarve.strike(m, 5, 11, 5, Direction.UP, DOWN, 1, true, dice(1)).total();
        m = new VoxelMask();
        var split = VoxelCarve.strike(m, 9, 11, 5, Direction.UP, DOWN, 1, false, dice(0));
        m = new VoxelMask();
        Vec3 slant = new Vec3(Math.sin(Math.toRadians(60)), -Math.cos(Math.toRadians(60)), 0);
        var pare = VoxelCarve.strike(m, 3, 11, 5, Direction.UP, slant, 1, false, dice(1));
        boolean pareTop = pare.cut.stream().allMatch(c -> c[1] == 11);
        m = new VoxelMask();
        for (int z = 0; z < VoxelMask.N; z++) for (int x = 0; x < VoxelMask.N; x++) m.clear(x, 5, z);
        int loose = m.dropLoose().size();      // шари 0–4 (720) менші за 6–11 (864) — відпадають вони
        var settle = new VoxelMask();
        for (int y = 0; y < 3; y++) for (int z = 0; z < VoxelMask.N; z++) for (int x = 0; x < VoxelMask.N; x++) settle.clear(x, y, z);
        var sr = VoxelCarve.strike(settle, 5, 11, 5, Direction.UP, DOWN, 0, false, dice(1));
        LOG.info("[тест] carvingRules: легко {}, сильно {}, у торець {}, упоперек волокон {}, скол {} (+{}), стругає {} (лише верх {}), відпало {}",
                light, strong, endGrain, across, split.cut.size(), split.split.size(), pare.cut.size(), pareTop, loose
                        + " / осіла на " + sr.sink + " (низ " + settle.lowest() + ")");
        h.assertTrue(light == 3, "легкий удар не 3: " + light);
        h.assertTrue(strong == 12, "сильний удар не 12: " + strong);
        h.assertTrue(endGrain == 6, "у торець не удвічі мілкіше: " + endGrain);
        h.assertTrue(across == 9, "упоперек волокон не на 1 мілкіше: " + across);
        h.assertTrue(split.split.size() == 4, "скол не дійшов до торця: " + split.split.size());
        h.assertTrue(pare.cut.size() == 15 && pareTop, "стругання не верхній шар 3 × 5: " + pare.cut.size() + " " + pareTop);
        h.assertTrue(loose == 5 * 144, "відрізане не відпало: " + loose);
        h.assertTrue(sr.sink == 3 && settle.lowest() == 0, "шматок не осів на креслення: " + sr.sink + " " + settle.lowest());
        h.succeed();
    }

    // 2. У світі: креслення + обтесана ялина на верстаку, гравець з зубилом і киянкою б'є згори — дерево вибите, зубило й киянка
    //    зносились на 1; без киянки — найслабший удар (3). Точно за шаблоном → ручка кружки з точністю 1; ледь почата → палиці.
    //    Інше дерево (береза) — стара різьба (вікно), удар у світі не діє.
    @GameTest(template = "arena", batch = "carve3d", timeoutTicks = 40)
    public static void carveInWorld(GameTestHelper h) {
        BlockPos table = new BlockPos(5, 2, 5), top = table.above(), table2 = new BlockPos(9, 2, 5), top2 = table2.above();
        h.setBlock(table, Blocks.CRAFTING_TABLE);
        h.setBlock(table2, Blocks.CRAFTING_TABLE);
        var p = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        Carving.tryPlace(h.getLevel(), p, h.absolutePos(table), new ItemStack(Carving.BLUEPRINT_MUG_HANDLE.get()));
        Carving.tryPlace(h.getLevel(), p, h.absolutePos(top), new ItemStack(Items.STRIPPED_SPRUCE_LOG));
        Carving.tryPlace(h.getLevel(), p, h.absolutePos(table2), new ItemStack(Carving.BLUEPRINT_MUG_HANDLE.get()));
        Carving.tryPlace(h.getLevel(), p, h.absolutePos(top2), new ItemStack(Items.STRIPPED_BIRCH_LOG));
        var be = (WorkpieceBlockEntity) h.getBlockEntity(top);
        var be2 = (WorkpieceBlockEntity) h.getBlockEntity(top2);
        // гравець над колодою, дивиться прямо вниз
        Vec3 c = Vec3.atBottomCenterOf(h.absolutePos(top));
        p.moveTo(c.x, c.y + 1.2 - p.getEyeHeight(), c.z, 0, 90);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Carving.CHISEL.get()));
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Carving.MALLET.get()));
        var ev = new net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock(p, h.absolutePos(top), Direction.UP,
                net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.Action.START);
        VoxelCarve.forgetCharge(p);
        VoxelCarve.onLeftClick(ev);
        boolean started = be.voxels() != null;
        int afterMallet = started ? be.voxels().count() : -1;
        int chiselWear = p.getMainHandItem().getDamageValue(), malletWear = p.getOffhandItem().getDamageValue();
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        VoxelCarve.onLeftClick(ev);
        int byHand = afterMallet - (started ? be.voxels().count() : 0);
        // береза — стара різьба
        var ev2 = new net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock(p, h.absolutePos(top2), Direction.UP,
                net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.Action.START);
        VoxelCarve.onLeftClick(ev2);
        boolean birchOld = be2.voxels() == null;
        ItemStack ruined = WorkpieceBlock.finish(h.getLevel(), be);
        VoxelMask keep = CarveTemplates.keep3d(h.getLevel().getServer(), "mug_handle");
        be.setVoxels(keep.copy());
        ItemStack part = WorkpieceBlock.finish(h.getLevel(), be);
        float q = CarvedPartItem.quality(part);
        LOG.info("[тест] carveInWorld: почалась {}, з киянкою лишилось {} (вибито {}), знос зубила {} киянки {}, рукою вибито {}; береза — стара {}; "
                + "ледь почата → {}; за шаблоном → {} точність {}", started, afterMallet, 1728 - afterMallet, chiselWear, malletWear, byHand, birchOld,
                ruined, part, String.format("%.2f", q));
        h.setBlock(top, Blocks.AIR); h.setBlock(top2, Blocks.AIR); h.setBlock(table, Blocks.AIR); h.setBlock(table2, Blocks.AIR);
        h.assertTrue(started && afterMallet < 1728 - 3, "удар киянкою не вибив глибше за легкий: " + afterMallet);
        h.assertTrue(chiselWear == 1 && malletWear == 1, "знос не 1/1: " + chiselWear + "/" + malletWear);
        h.assertTrue(byHand == 2, "рукою середнім зубилом не найслабший удар (2): " + byHand);
        h.assertTrue(birchOld, "береза пішла в нову різьбу");
        h.assertTrue(ruined.is(Items.STICK), "ледь почата — не палиці: " + ruined);
        h.assertTrue(part.is(Carving.MUG_HANDLE.get()) && q > 0.99F, "за шаблоном — не ручка 1,0: " + part + " " + q);
        h.succeed();
    }

    // 2б. Зубила (Fij): мале — 1 піксель, середнє — 2, широке — 3 × 3; сильно — глибше
    @GameTest(template = "arena", batch = "carve3d", timeoutTicks = 20)
    public static void chiselSizes(GameTestHelper h) {
        int[][] sizes = { { 1, 1 }, { 2, 1 }, { 3, 3 } };
        StringBuilder out = new StringBuilder();
        int[] light = new int[3], strong = new int[3];
        for (int i = 0; i < 3; i++) {
            VoxelMask m = new VoxelMask();
            light[i] = VoxelCarve.strike(m, 5, 11, 5, Direction.UP, DOWN, 0, false, dice(1), null, sizes[i][0], sizes[i][1]).total();
            m = new VoxelMask();
            strong[i] = VoxelCarve.strike(m, 5, 11, 5, Direction.UP, DOWN, 1, false, dice(1), null, sizes[i][0], sizes[i][1]).total();
            out.append(String.format("%d×%d: легко %d, сильно %d; ", sizes[i][0], sizes[i][1], light[i], strong[i]));
        }
        LOG.info("[тест] chiselSizes: {}", out);
        h.assertTrue(light[0] == 1 && light[1] == 2 && light[2] == 9, "легкий удар не 1 / 2 / 9: " + out);
        h.assertTrue(strong[0] == 4 && strong[1] == 8 && strong[2] == 36, "сильний удар не 4 / 8 / 36: " + out);
        h.succeed();
    }

    // 3. Готова деталь випадає сама — будь-де в колоді й як завгодно повернута; дерево лишається. Шматок, що ще тримається, — ні.
    @GameTest(template = "arena", batch = "carve3d", timeoutTicks = 20)
    public static void partDropsOut(GameTestHelper h) {
        var handle = com.gigongibus.hungrycows.carve.PartMatcher.cells(CarveTemplates.keep3d(h.getLevel().getServer(), "mug_handle"));
        VoxelMask m = VoxelMask.empty();
        for (int y = 0; y < 12; y++) for (int z = 0; z < 6; z++) for (int x = 0; x < 12; x++) m.set(x, y, z);   // дерево, що лишилось
        for (int x = 2; x <= 8; x++) for (int z = 9; z <= 10; z++) m.set(x, 0, z);                              // ручка лежить окремо (повернута)
        var r = new VoxelCarve.Result();
        VoxelCarve.separate(m, handle, r);
        VoxelMask m2 = VoxelMask.empty();
        for (int y = 0; y < 12; y++) for (int z = 0; z < 6; z++) for (int x = 0; x < 12; x++) m2.set(x, y, z);
        for (int x = 2; x <= 8; x++) for (int z = 6; z <= 7; z++) m2.set(x, 0, z);                               // ще приросла до дерева
        var r2 = new VoxelCarve.Result();
        VoxelCarve.separate(m2, handle, r2);
        LOG.info("[тест] partDropsOut: окрема ручка → деталей {} {}, дерево лишилось {}; прирослa → деталей {}", r.parts.size(), r.parts,
                m.count(), r2.parts.size());
        h.assertTrue(r.parts.size() == 1 && r.parts.get(0) > 0.99F, "окрема ручка не випала: " + r.parts);
        h.assertTrue(m.count() == 12 * 12 * 6, "дерево не лишилось: " + m.count());
        h.assertTrue(r2.parts.isEmpty(), "приросла ручка випала");
        h.succeed();
    }

    // 4. Чи можна вирізати і скільки з однієї колоди: «бот» копає згори навколо чорного місця (не зачіпаючи його), поки деталь
    //    не випаде, потім — наступну, поки вміщається
    @GameTest(template = "arena", batch = "carve3d", timeoutTicks = 400)
    public static void botCarvesMugParts(GameTestHelper h) {
        StringBuilder out = new StringBuilder();
        float worst = 1;
        int handles = 0;
        for (String name : new String[]{ "mug_body", "mug_ears", "mug_handle" }) {
            VoxelMask keep = CarveTemplates.keep3d(h.getLevel().getServer(), name);
            var res = bot(keep);
            for (float q : res.parts) worst = Math.min(worst, q);
            if (res.parts.isEmpty()) worst = -1;
            if (name.equals("mug_handle")) handles = res.parts.size();
            out.append(String.format("%s: ударів %d, деталей %d %s, дерева лишилось %d; ", name, res.strikes, res.parts.size(), res.parts, res.left));
        }
        LOG.info("[тест] botCarvesMugParts: {}", out);
        h.assertTrue(worst >= 0.5F, "бот не вирізає деталь як слід: " + out);
        h.assertTrue(handles >= 2, "з однієї колоди не вийшло кількох ручок: " + out);
        h.succeed();
    }

    private record BotResult(int strikes, java.util.List<Float> parts, int left) {
    }

    private static BotResult bot(VoxelMask keep) {
        var tcells = com.gigongibus.hungrycows.carve.PartMatcher.cells(keep);
        VoxelMask m = new VoxelMask();
        VoxelMask g = com.gigongibus.hungrycows.carve.PartMatcher.findGuide(m, keep);
        int strikes = 0, N = VoxelMask.N;
        java.util.List<Float> parts = new java.util.ArrayList<>();
        Random rnd = new Random(1);
        while (g != null && parts.size() < 8) {
            int[] lo = { 99, 99, 99 }, hi = { -99, -99, -99 };
            for (int[] c : com.gigongibus.hungrycows.carve.PartMatcher.cells(g)) for (int a = 0; a < 3; a++) { lo[a] = Math.min(lo[a], c[a]); hi[a] = Math.max(hi[a], c[a]); }
            boolean dropped = false;
            for (int pass = 0; pass < 80 && !dropped; pass++) {
                boolean any = false;
                for (int y = N - 1; y >= 0 && !dropped; y--)
                    for (int z = lo[2] - 1; z <= hi[2] + 1 && !dropped; z++)
                        for (int x = lo[0] - 1; x <= hi[0] + 1 && !dropped; x++) {
                            if (y < lo[1] - 1 || !m.get(x, y, z) || g.get(x, y, z)) continue;
                            attempt:
                            for (Direction f : Direction.values()) {
                                if (m.get(x + f.getStepX(), y + f.getStepY(), z + f.getStepZ()) || (f == Direction.DOWN && y == 0)) continue;
                                Vec3 look = new Vec3(-f.getStepX(), -f.getStepY(), -f.getStepZ());
                                for (float s : new float[]{ 1F, 0.5F, 0F })
                                    for (boolean turned : new boolean[]{ false, true }) {
                                        VoxelMask c = m.copy();
                                        var r = VoxelCarve.strike(c, x, y, z, f, look, s, turned, rnd, tcells);
                                        if (r.total() == 0 && r.parts.isEmpty()) continue;
                                        boolean hurt = false;
                                        for (java.util.List<int[]> l : java.util.List.of(r.cut, r.split, r.loose))
                                            for (int[] v : l) if (g.get(v[0], v[1], v[2])) hurt = true;
                                        if (hurt) continue;
                                        m = c;
                                        strikes++;
                                        any = true;
                                        if (r.sink > 0) g.shiftDown(r.sink);
                                        if (!r.parts.isEmpty()) { parts.addAll(r.parts); dropped = true; }
                                        break attempt;
                                    }
                            }
                        }
                if (!any) break;
            }
            if (!dropped) break;
            g = m.count() == 0 ? null : com.gigongibus.hungrycows.carve.PartMatcher.findGuide(m, keep);
        }
        return new BotResult(strikes, parts, m.count());
    }
}
