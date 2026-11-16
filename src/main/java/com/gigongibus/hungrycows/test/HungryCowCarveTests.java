package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.carve.CarveMask;
import com.gigongibus.hungrycows.carve.CarveUpdatePacket;
import com.gigongibus.hungrycows.carve.Carving;
import com.gigongibus.hungrycows.carve.ChiselSim;
import com.gigongibus.hungrycows.carve.WorkpieceBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** Ремесло (Fij): заготовка на верстаку, збереження вирізаного, кидання зубила, фізика зубила */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowCarveTests {
    private static final Logger LOG = LogUtils.getLogger();

    // 1. Присів + ПКМ обтесаною колодою по верстаку → заготовка; дошки — ні. Вирізане лише вибивається (не «доростає»).
    //    Ціла — колода назад, почата — 2 палиці. Викинув зубило — зникає з руки й падає на землю.
    @GameTest(template = "arena", batch = "carve", timeoutTicks = 40)
    public static void workpieceOnTable(GameTestHelper h) {
        BlockPos table = new BlockPos(5, 2, 5), top = table.above(), table2 = new BlockPos(9, 2, 5);
        h.setBlock(table, Blocks.CRAFTING_TABLE);
        h.setBlock(table2, Blocks.CRAFTING_TABLE);
        var p = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        p.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 2, 3.5)));
        ItemStack log = new ItemStack(Items.STRIPPED_BIRCH_LOG, 2), planks = new ItemStack(Items.OAK_PLANKS, 2);
        boolean placed = Carving.tryPlace(h.getLevel(), p, h.absolutePos(table), log);
        boolean planksPlaced = Carving.tryPlace(h.getLevel(), p, h.absolutePos(table2), planks);
        var be = (WorkpieceBlockEntity) h.getBlockEntity(top);
        boolean birch = be != null && be.log() == Items.STRIPPED_BIRCH_LOG;
        var dropsWhole = Block.getDrops(h.getBlockState(top), h.getLevel(), h.absolutePos(top), be);
        // гравець вибив 100 пікселів і «прислав» маску, де ще й повернув уже вибиті — повертатися не має
        CarveMask m = be.mask().copy();
        for (int i = 0; i < 100; i++) m.clear(10 + i % 20, 10 + i / 20);
        CarveUpdatePacket.apply(p, h.absolutePos(top), m, false);
        int afterCut = be.mask().solidCount();
        CarveUpdatePacket.apply(p, h.absolutePos(top), new CarveMask(), false);
        int afterRestore = be.mask().solidCount();
        var dropsCarved = Block.getDrops(h.getBlockState(top), h.getLevel(), h.absolutePos(top), be);
        // кинув зубило
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Carving.CHISEL.get()));
        CarveUpdatePacket.apply(p, h.absolutePos(top), be.mask().copy(), true);
        boolean handEmpty = p.getMainHandItem().isEmpty();
        h.runAtTickTime(5, () -> {
            var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(6), e -> e.getItem().is(Carving.CHISEL.get()));
            LOG.info("[тест] workpieceOnTable: поклав {} (береза {}), колод лишилось {}, дошки {}; ціла дає {}; вибив → {} пікс., «повернув» → {}; почата дає {}; "
                    + "кинув зубило: рука порожня {}, на землі {}", placed, birch, log.getCount(), planksPlaced, dropsWhole, 124 * 124 - afterCut,
                    afterRestore, dropsCarved, handEmpty, items.size());
            items.forEach(ItemEntity::discard);
            h.setBlock(top, Blocks.AIR); h.setBlock(table, Blocks.AIR); h.setBlock(table2, Blocks.AIR);
            h.assertTrue(placed && birch && log.getCount() == 1, "колода не лягла на верстак");
            h.assertTrue(!planksPlaced, "дошки теж лягли");
            h.assertTrue(dropsWhole.size() == 1 && dropsWhole.get(0).is(Items.STRIPPED_BIRCH_LOG), "ціла не дає колоду: " + dropsWhole);
            h.assertTrue(afterCut == 124 * 124 - 100 && afterRestore == afterCut, "вирізане не так: " + afterCut + " / " + afterRestore);
            h.assertTrue(dropsCarved.size() == 1 && dropsCarved.get(0).is(Items.STICK) && dropsCarved.get(0).getCount() == 2, "почата не дає 2 палиці: " + dropsCarved);
            h.assertTrue(handEmpty && items.size() == 1, "зубило не випало: рука порожня " + handEmpty + ", на землі " + items.size());
            h.succeed();
        });
    }

    // 2. Фізика: сильний удар вістрям — вибиває; легенько поклав — ні; відпустив з розгону вбік — «викинув»; тягнеш мишею — іде за нею
    @GameTest(template = "arena", batch = "carve", timeoutTicks = 20)
    public static void chiselPhysics(GameTestHelper h) {
        float cx = ChiselSim.WOOD_X + 62, tipToCom = ChiselSim.TIP;
        ChiselSim hard = new ChiselSim(new CarveMask());
        hard.place(cx, ChiselSim.WOOD_Y - tipToCom - 2, 0, 0, 900);
        for (int i = 0; i < 60; i++) hard.step(1 / 60f);
        ChiselSim soft = new ChiselSim(new CarveMask());
        soft.place(cx, ChiselSim.WOOD_Y - tipToCom - 3, 0, 0, 0);
        for (int i = 0; i < 60; i++) soft.step(1 / 60f);
        float softTip = soft.y + ChiselSim.TIP;
        ChiselSim throwIt = new ChiselSim(new CarveMask());
        throwIt.place(60, 60, 0, -2500, -300);
        int t = 0;
        while (!throwIt.thrown && t < 120) { throwIt.step(1 / 60f); t++; }
        ChiselSim drag = new ChiselSim(new CarveMask());
        drag.place(300, 100, 0, 0, 0);
        drag.grab(300, 100);
        drag.drag(300, 40);
        for (int i = 0; i < 60; i++) drag.step(1 / 60f);
        // схопив за кінець ручки горизонтальне зубило — за секунду має повиснути лезом донизу (маятник)
        ChiselSim hang = new ChiselSim(new CarveMask());
        hang.place(40, 60, (float) (Math.PI / 2), 0, 0);   // ліворуч від колоди — щоб не сперлося на неї
        float hx = hang.x + (float) (Math.sin(Math.PI / 2) * 65), hy = hang.y - (float) (Math.cos(Math.PI / 2) * 65);   // точка на ручці
        hang.grab(hx, hy);
        for (int i = 0; i < 60; i++) hang.step(1 / 60f);
        float hangDeg = (float) Math.toDegrees(Math.atan2(Math.sin(hang.angle), Math.cos(hang.angle)));
        LOG.info("[тест] chiselPhysics: схопив за ручку горизонтальне — за 1 с кут {}° (0 = лезом донизу)", String.format("%.0f", hangDeg));
        h.assertTrue(Math.abs(hangDeg) < 25, "схоплене за ручку не висить лезом донизу: " + hangDeg);
        LOG.info("[тест] chiselPhysics: сильний удар (900 пікс/с) вибив {} пікс., стружки {}; легенько — {} (вістря на {}, верх дерева {}); "
                + "кинув — за {} кадрів; тягнув угору на 60 — центр з 100 до {}", hard.carvedPixels, hard.chips.size(), soft.carvedPixels,
                String.format("%.1f", softTip), ChiselSim.WOOD_Y, t, String.format("%.1f", drag.y));
        h.assertTrue(hard.carvedPixels > 50, "сильний удар не ріже: " + hard.carvedPixels);
        h.assertTrue(soft.carvedPixels == 0, "легенько теж ріже: " + soft.carvedPixels);
        h.assertTrue(softTip <= ChiselSim.WOOD_Y + 2, "зубило провалилось у дерево: " + softTip);
        h.assertTrue(throwIt.thrown, "кинуте не вилетіло");
        h.assertTrue(drag.y < 90, "не йде за мишею: " + drag.y);
        h.succeed();
    }
}
