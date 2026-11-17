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
        VoxelMask m2 = new VoxelMask();
        for (int z = 0; z < VoxelMask.N; z++) for (int x = 0; x < VoxelMask.N; x++) m2.clear(x, 5, z);
        VoxelMask keepLow = VoxelMask.empty();
        keepLow.set(5, 1, 5);
        int looseKeep = m2.dropLoose(keepLow, 0).size();   // деталь унизу — тримається менший, відпадає більший
        var settle = new VoxelMask();
        for (int y = 0; y < 3; y++) for (int z = 0; z < VoxelMask.N; z++) for (int x = 0; x < VoxelMask.N; x++) settle.clear(x, y, z);
        var sr = VoxelCarve.strike(settle, 5, 11, 5, Direction.UP, DOWN, 0, false, dice(1));
        LOG.info("[тест] carvingRules: легко {}, сильно {}, у торець {}, упоперек волокон {}, скол {} (+{}), стругає {} (лише верх {}), відпало {}",
                light, strong, endGrain, across, split.cut.size(), split.split.size(), pare.cut.size(), pareTop, loose + " / з деталлю " + looseKeep
                        + " / осіла на " + sr.sink + " (низ " + settle.lowest() + ")");
        h.assertTrue(light == 3, "легкий удар не 3: " + light);
        h.assertTrue(strong == 12, "сильний удар не 12: " + strong);
        h.assertTrue(endGrain == 6, "у торець не удвічі мілкіше: " + endGrain);
        h.assertTrue(across == 9, "упоперек волокон не на 1 мілкіше: " + across);
        h.assertTrue(split.split.size() == 4, "скол не дійшов до торця: " + split.split.size());
        h.assertTrue(pare.cut.size() == 15 && pareTop, "стругання не верхній шар 3 × 5: " + pare.cut.size() + " " + pareTop);
        h.assertTrue(loose == 5 * 144, "відрізане не відпало: " + loose);
        h.assertTrue(looseKeep == 6 * 144, "відпала частина з деталлю: " + looseKeep);
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
        h.assertTrue(byHand == 3, "рукою не найслабший удар: " + byHand);
        h.assertTrue(birchOld, "береза пішла в нову різьбу");
        h.assertTrue(ruined.is(Items.STICK), "ледь почата — не палиці: " + ruined);
        h.assertTrue(part.is(Carving.MUG_HANDLE.get()) && q > 0.99F, "за шаблоном — не ручка 1,0: " + part + " " + q);
        h.succeed();
    }

    // 3. Чи можна вирізати: «бот» б'є з усіх боків (кружка посередині колоди), не зачіпаючи деталь — що вийде
    @GameTest(template = "arena", batch = "carve3d", timeoutTicks = 200)
    public static void botCarvesMugParts(GameTestHelper h) {
        StringBuilder out = new StringBuilder();
        float worst = 1;
        for (String name : new String[]{ "mug_body", "mug_ears", "mug_handle" }) {
            VoxelMask keep = CarveTemplates.keep3d(h.getLevel().getServer(), name);
            int[] res = bot(keep);
            VoxelMask m = lastBot;
            float match = m.match(keep, res[1]);
            worst = Math.min(worst, Carving.quality3d(match));
            out.append(String.format("%s: ударів %d, осіла %d, схожість %.3f, точність %.2f; ", name, res[0], res[1], match, Carving.quality3d(match)));
        }
        LOG.info("[тест] botCarvesMugParts: {}", out);
        h.assertTrue(worst >= 0.5F, "навіть обережний бот не вирізає деталь: " + out);
        h.succeed();
    }

    private static VoxelMask lastBot;

    private static int keepCount(VoxelMask m, VoxelMask keep, int sink) {
        int n = 0, N = VoxelMask.N;
        for (int y = 0; y < N; y++) for (int z = 0; z < N; z++) for (int x = 0; x < N; x++) if (m.get(x, y, z) && keep.get(x, y + sink, z)) n++;
        return n;
    }

    /** Жадібно: будь-яка відкрита грань зайвої клітинки, удар (сильний, далі від деталі — легкий), лише якщо деталь ціла */
    private static int[] bot(VoxelMask keep) {
        VoxelMask m = new VoxelMask();
        int sink = 0, strikes = 0, N = VoxelMask.N;
        Random rnd = new Random(1);
        int whole = keepCount(m, keep, 0);
        for (int pass = 0; pass < 60; pass++) {
            boolean any = false;
            for (int y = N - 1; y >= 0; y--)
                for (int z = 0; z < N; z++)
                    for (int x = 0; x < N; x++) {
                        if (!m.get(x, y, z) || keep.get(x, y + sink, z)) continue;
                        attempt:
                        for (Direction f : Direction.values()) {
                            if (m.get(x + f.getStepX(), y + f.getStepY(), z + f.getStepZ())) continue;
                            if (f == Direction.DOWN && y == 0) continue;               // знизу — креслення й стіл
                            Vec3 look = new Vec3(-f.getStepX(), -f.getStepY(), -f.getStepZ());
                            for (float s : new float[]{ 1F, 0.5F, 0F })
                                for (boolean turned : new boolean[]{ false, true }) {
                                    VoxelMask c = m.copy();
                                    var r = VoxelCarve.strike(c, x, y, z, f, look, s, turned, rnd, keep, sink);
                                    if (r.total() == 0 || keepCount(c, keep, sink + r.sink) < whole) continue;
                                    m = c;
                                    sink += r.sink;
                                    strikes++;
                                    any = true;
                                    break attempt;
                                }
                        }
                    }
            if (!any) break;
        }
        lastBot = m;
        return new int[]{ strikes, sink };
    }
}
