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
        int loose = m.dropLoose().size();
        LOG.info("[тест] carvingRules: легко {}, сильно {}, у торець {}, упоперек волокон {}, скол {} (+{}), стругає {} (лише верх {}), відпало {}",
                light, strong, endGrain, across, split.cut.size(), split.split.size(), pare.cut.size(), pareTop, loose);
        h.assertTrue(light == 3, "легкий удар не 3: " + light);
        h.assertTrue(strong == 12, "сильний удар не 12: " + strong);
        h.assertTrue(endGrain == 6, "у торець не удвічі мілкіше: " + endGrain);
        h.assertTrue(across == 9, "упоперек волокон не на 1 мілкіше: " + across);
        h.assertTrue(split.split.size() == 4, "скол не дійшов до торця: " + split.split.size());
        h.assertTrue(pare.cut.size() == 15 && pareTop, "стругання не верхній шар 3 × 5: " + pare.cut.size() + " " + pareTop);
        h.assertTrue(loose == 6 * 144, "відрізане не відпало: " + loose);
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

    // 3. Чи можна вирізати: «бот» ударами лише згори й з боків, обережно (без сильних ударів біля стінок) вирізає заготовку кружки
    @GameTest(template = "arena", batch = "carve3d", timeoutTicks = 20)
    public static void botCarvesMugBody(GameTestHelper h) {
        VoxelMask keep = CarveTemplates.keep3d(h.getLevel().getServer(), "mug_body");
        VoxelMask m = new VoxelMask();
        Random rnd = new Random(1);
        int strikes = 0, N = VoxelMask.N;
        // зовнішні шари: зверху вниз, лезо вздовж і впоперек, сила — щоб не зачепити деталь
        for (int pass = 0; pass < 40; pass++) {
            boolean any = false;
            for (int z = 0; z < N; z++)
                for (int x = 0; x < N; x++) {
                    int top = -1;
                    for (int y = N - 1; y >= 0; y--) if (m.get(x, y, z)) { top = y; break; }
                    if (top < 0 || keep.get(x, top, z)) continue;
                    // скільки можна вглиб, не зачепивши деталь, у смузі леза (вздовж x або впоперек)
                    for (boolean turned : new boolean[]{ false, true }) {
                        boolean ok = true;
                        int room = 99;
                        for (int o = -1; o <= 1; o++) {
                            int xx = turned ? x : x + o, zz = turned ? z + o : z;
                            if (!VoxelMask.in(xx, 0, zz)) continue;
                            int r = 0;
                            for (int y = top; y >= 0 && !keep.get(xx, y, zz); y--) r++;
                            if (m.get(xx, top, zz) && keep.get(xx, top, zz)) ok = false;
                            room = Math.min(room, r);
                        }
                        if (!ok || room < 1) continue;
                        int depth = Math.min(room, turned ? 3 : 2);     // уздовж волокон — не сильніше 0,5 (скол)
                        float s = turned ? (depth) / 3F : (depth - 1) / 3F;   // упоперек: глибина = 1 + round(3s) − 1
                        if (turned) s = Math.max(0, (depth + 1 - 1) / 3F);
                        int before = m.count();
                        VoxelCarve.strike(m, x, top, z, Direction.UP, DOWN, Math.min(s, 0.55F), turned, rnd);
                        strikes++;
                        if (m.count() < before) { any = true; break; }
                    }
                }
            if (!any) break;
        }
        float match = m.match(keep);
        int damaged = 0, left = 0;
        for (int y = 0; y < N; y++) for (int z = 0; z < N; z++) for (int x = 0; x < N; x++) {
            if (keep.get(x, y, z) && !m.get(x, y, z)) damaged++;
            if (!keep.get(x, y, z) && m.get(x, y, z)) left++;
        }
        LOG.info("[тест] botCarvesMugBody: ударів {}, схожість {}, точність {}, зачепив деталь {}, лишилось зайвого {}", strikes,
                String.format("%.3f", match), String.format("%.2f", Carving.quality3d(match)), damaged, left);
        h.assertTrue(Carving.quality3d(match) >= 0.5F, "навіть обережний бот не вирізає заготовку кружки: " + match);
        h.succeed();
    }
}
