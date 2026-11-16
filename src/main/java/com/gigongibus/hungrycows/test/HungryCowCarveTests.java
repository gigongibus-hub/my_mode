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

    // 1. Креслення на верстак (колода на голий верстак — ні), колода на креслення (дошки — ні). Вирізане лише вибивається.
    //    Точно за шаблоном → деталь з точністю ~1; ледь почата → палиці; креслення лишається. Удари зношують зубило (−1 за удар).
    //    Кинув зубило — випадає. Три деталі → кружка (використань за точністю); налив води, випив — −1, порожня.
    @GameTest(template = "arena", batch = "carve", timeoutTicks = 40)
    public static void workpieceOnTable(GameTestHelper h) {
        BlockPos table = new BlockPos(5, 2, 5), top = table.above(), table2 = new BlockPos(9, 2, 5);
        h.setBlock(table, Blocks.CRAFTING_TABLE);
        h.setBlock(table2, Blocks.CRAFTING_TABLE);
        var p = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        p.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 2, 3.5)));
        ItemStack log = new ItemStack(Items.STRIPPED_BIRCH_LOG, 3), planks = new ItemStack(Items.OAK_PLANKS, 2);
        ItemStack bp = new ItemStack(Carving.BLUEPRINT_MUG_BODY.get());
        boolean logOnBare = Carving.tryPlace(h.getLevel(), p, h.absolutePos(table2), log.copy());
        boolean bpPlaced = Carving.tryPlace(h.getLevel(), p, h.absolutePos(table), bp);
        boolean planksPlaced = Carving.tryPlace(h.getLevel(), p, h.absolutePos(top), planks);
        boolean logPlaced = Carving.tryPlace(h.getLevel(), p, h.absolutePos(top), log);
        var be = (WorkpieceBlockEntity) h.getBlockEntity(top);
        boolean birch = be != null && be.hasLog() && be.log() == Items.STRIPPED_BIRCH_LOG;
        // ледь почата (100 пікс.) — палиці
        CarveMask m = be.mask().copy();
        for (int i = 0; i < 100; i++) m.clear(i % 20, i / 20);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Carving.CHISEL.get()));
        CarveUpdatePacket.apply(p, h.absolutePos(top), m, false, 7);
        int wear = p.getMainHandItem().getDamageValue();
        CarveUpdatePacket.apply(p, h.absolutePos(top), new CarveMask(), false, 0);
        int afterRestore = be.mask().solidCount();
        ItemStack ruined = com.gigongibus.hungrycows.carve.WorkpieceBlock.finish(h.getLevel(), be);
        be.clearLog();
        // точно за шаблоном — заготовка кружки ~1
        Carving.tryPlace(h.getLevel(), p, h.absolutePos(top), log);
        CarveMask keep = com.gigongibus.hungrycows.carve.CarveTemplates.keep(h.getLevel().getServer(), "mug_body");
        CarveUpdatePacket.apply(p, h.absolutePos(top), keep.copy(), false, 0);
        ItemStack part = com.gigongibus.hungrycows.carve.WorkpieceBlock.finish(h.getLevel(), be);
        float q = com.gigongibus.hungrycows.carve.CarvedPartItem.quality(part);
        boolean blueprintStays = be.blueprint() == Carving.BLUEPRINT_MUG_BODY.get();
        // кружка з трьох деталей: точність 1, 1, 0,4 → середня 0,8
        var g = new net.minecraft.world.inventory.TransientCraftingContainer(new net.minecraft.world.inventory.AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player pl, int i) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player pl) { return true; }
        }, 3, 3);
        g.setItem(0, part);
        g.setItem(4, com.gigongibus.hungrycows.carve.CarvedPartItem.withQuality(new ItemStack(Carving.MUG_EARS.get()), 1));
        g.setItem(8, com.gigongibus.hungrycows.carve.CarvedPartItem.withQuality(new ItemStack(Carving.MUG_HANDLE.get()), 0.4F));
        var r = h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, g, h.getLevel());
        ItemStack mug = r.map(x -> x.assemble(g, h.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
        int uses = mug.isEmpty() ? -1 : mug.getMaxDamage() - mug.getDamageValue();
        com.gigongibus.hungrycows.carve.MugItem.fillWater(mug);
        boolean water = com.gigongibus.hungrycows.carve.MugItem.drinkKind(mug) == 1;
        mug.finishUsingItem(h.getLevel(), p);
        int usesAfter = mug.getMaxDamage() - mug.getDamageValue();
        boolean emptied = com.gigongibus.hungrycows.carve.MugItem.drink(mug).isEmpty();
        // кинув зубило
        CarveUpdatePacket.apply(p, h.absolutePos(top), be.mask().copy(), true, 0);
        boolean handEmpty = p.getMainHandItem().isEmpty();
        h.runAtTickTime(5, () -> {
            var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(6), e -> e.getItem().is(Carving.CHISEL.get()));
            LOG.info("[тест] workpieceOnTable: колода на голий верстак {}, креслення {}, дошки {}, колода {} (береза {}); 7 ударів → знос {}; «повернув» → {} пікс.; "
                    + "ледь почата → {}; точно за шаблоном → {} точність {}; креслення лишилось {}; кружка (1, 1, 0,4) → {} використань; вода {}, випила → {} і порожня {}; "
                    + "кинув зубило: рука порожня {}, на землі {}", logOnBare, bpPlaced, planksPlaced, logPlaced, birch, wear, afterRestore, ruined, part,
                    String.format("%.2f", q), blueprintStays, uses, water, usesAfter, emptied, handEmpty, items.size());
            items.forEach(ItemEntity::discard);
            h.setBlock(top, Blocks.AIR); h.setBlock(table, Blocks.AIR); h.setBlock(table2, Blocks.AIR);
            h.assertTrue(!logOnBare && bpPlaced && !planksPlaced && logPlaced && birch, "не той порядок: креслення, потім колода");
            h.assertTrue(wear == 7, "зубило не зносилось на 7: " + wear);
            h.assertTrue(afterRestore == 124 * 124 - 100, "вибите «повернулось»: " + afterRestore);
            h.assertTrue(ruined.is(Items.STICK), "ледь почата дала не палиці: " + ruined);
            h.assertTrue(part.is(Carving.MUG_BODY.get()) && q > 0.95F, "точно за шаблоном — не заготовка кружки ~1: " + part + " " + q);
            h.assertTrue(blueprintStays, "креслення зникло з верстака");
            h.assertTrue(uses == Carving.usesFor(0.8F), "кружка не з тією кількістю використань: " + uses);
            h.assertTrue(water && usesAfter == uses - 1 && emptied, "налити й випити не працює: " + water + " " + usesAfter + " " + emptied);
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
