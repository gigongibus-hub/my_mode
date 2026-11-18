package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.carve.Carving;
import com.gigongibus.hungrycows.carve.WorkpieceBlock;
import com.gigongibus.hungrycows.carve.WorkpieceBlockEntity;
import com.gigongibus.hungrycows.chop.Chopping;
import com.gigongibus.hungrycows.chop.WoodPiece;
import com.gigongibus.hungrycows.chop.WoodPieceItem;
import com.gigongibus.hungrycows.chop.WoodPileBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/** Розрубка дерева (Fij, 18.11): сила, волокна, засічка, кривий удар, 16 шматків, купи, різьба зі шматка */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowChopTests {
    private static final Logger LOG = LogUtils.getLogger();

    // 1. Правила: колоду вздовж волокон — потрібно 0,7 (половини, нова грань — розкол); упоперек 0,95 — слабкий удар застрягає,
    //    другий туди ж доповнює (грань — торець); забагато — криво, з трісками; з цілої — 16 шматків 4 × 16 × 4
    @GameTest(template = "arena", batch = "chop", timeoutTicks = 20)
    public static void chopRules(GameTestHelper h) {
        RandomSource rnd = RandomSource.create(1);
        List<WoodPiece> l = new ArrayList<>(List.of(WoodPiece.whole(Direction.Axis.Y, true, false)));
        float needAlong = Chopping.need(l.get(0), Direction.Axis.X), needAcross = Chopping.need(l.get(0), Direction.Axis.Y);
        var weak = Chopping.chopPieces(l, 0, Direction.Axis.X, 8, 0.3F, rnd);
        var clean = Chopping.chopPieces(l, 0, Direction.Axis.X, 8, 0.75F, rnd);   // 0,3 + 0,75 — засічка доповнює
        boolean halves = l.size() == 2 && l.get(0).sx == 8 && l.get(1).sx == 8 && l.get(1).x == 8;
        boolean splitFace = l.get(0).faces[Direction.EAST.get3DDataValue()] == WoodPiece.SPLIT && l.get(0).faces[Direction.WEST.get3DDataValue()] == WoodPiece.BARK;
        // поперек половинки: слабко — застрягла, ще раз — розрубана, торець
        List<WoodPiece> c = new ArrayList<>(List.of(l.get(0).copy()));
        var s1 = Chopping.chopPieces(c, 0, Direction.Axis.Y, 8, 0.4F, rnd);
        var s2 = Chopping.chopPieces(c, 0, Direction.Axis.Y, 8, 0.4F, rnd);
        boolean endFace = c.size() == 2 && c.get(0).faces[Direction.UP.get3DDataValue()] == WoodPiece.END;
        // засильно: половинка вздовж (треба 0,45) ударом 1,0 — розруб так само рівно по рамці, дерево не губиться
        List<WoodPiece> k = new ArrayList<>(List.of(l.get(0).copy()));
        int before = k.get(0).volume();
        var crooked = Chopping.chopPieces(k, 0, Direction.Axis.Z, 8, 1F, rnd);
        int after = k.stream().mapToInt(WoodPiece::volume).sum();
        // 16 шматків: кожен шматок навпіл уздовж волокон, поки ширший за 4
        List<WoodPiece> all = new ArrayList<>(List.of(WoodPiece.whole(Direction.Axis.Y, true, false)));
        int strikes = 0;
        for (boolean any = true; any; ) {
            any = false;
            for (int i = 0; i < all.size(); i++) {
                WoodPiece p = all.get(i);
                Direction.Axis ax = p.sx > 4 ? Direction.Axis.X : p.sz > 4 ? Direction.Axis.Z : null;
                if (ax == null) continue;
                Chopping.chopPieces(all, i, ax, p.size(ax) / 2, Chopping.need(p, ax) + 0.05F, rnd);
                strikes++;
                any = true;
                break;
            }
        }
        boolean sixteen = all.size() == 16 && all.stream().allMatch(p -> p.sx == 4 && p.sz == 4 && p.sy == 16);
        LOG.info("[тест] chopRules: треба вздовж {} / упоперек {}; слабко {} → потім {} (половини {}, розкол {}); поперек {} → {} (торець {}); "
                        + "засильно {} ({} → {} пікс.); 16 шматків {} за {} ударів", String.format("%.2f", needAlong), String.format("%.2f", needAcross),
                weak, clean, halves, splitFace, s1, s2, endFace, crooked, before, after, sixteen, strikes);
        h.assertTrue(weak == Chopping.Result.STUCK && clean == Chopping.Result.CLEAN && halves && splitFace, "колода не розкололась навпіл як слід");
        h.assertTrue(s1 == Chopping.Result.STUCK && s2 == Chopping.Result.CLEAN && endFace, "поперек: засічка не доповнила або не торець");
        boolean onLine = k.size() == 2 && k.get(0).sz == 8 && k.get(1).sz == 8 && k.get(1).z == 8;
        h.assertTrue(crooked == Chopping.Result.HARD && after == before && onLine, "засильно — не рівно по рамці: " + crooked + " " + after + " " + onLine);
        h.assertTrue(sixteen && strikes == 15, "не 16 шматків 4×16×4: " + all.size() + " за " + strikes);
        h.succeed();
    }

    // 2. У світі: колода темного дуба на камені — сокирою розколюється, лишається купою з двох половинок, сокира −1; присівши — ні;
    //    стовбур (колода над колодою) і дуб — ні
    @GameTest(template = "arena", batch = "chop", timeoutTicks = 20)
    public static void chopInWorld(GameTestHelper h) {
        BlockPos log = new BlockPos(4, 2, 4), trunk = new BlockPos(8, 2, 4), oak = new BlockPos(10, 2, 4);
        h.setBlock(log, Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
        h.setBlock(trunk, Blocks.DARK_OAK_LOG);
        h.setBlock(trunk.above(), Blocks.DARK_OAK_LOG);
        h.setBlock(oak, Blocks.OAK_LOG);
        var p = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        var c = net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(log));
        p.moveTo(c.x, c.y, c.z - 2, 0, 30);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        p.setShiftKeyDown(true);
        var sneak = Chopping.chop(h.getLevel(), p, h.absolutePos(log), 0, Direction.Axis.X, 8, 0.8F);
        p.setShiftKeyDown(false);
        var r = Chopping.chop(h.getLevel(), p, h.absolutePos(log), 0, Direction.Axis.X, 8, 0.8F);
        var pile = h.getLevel().getBlockEntity(h.absolutePos(log)) instanceof WoodPileBlockEntity be ? be : null;
        int wear = p.getMainHandItem().getDamageValue();
        var onTrunk = Chopping.chop(h.getLevel(), p, h.absolutePos(trunk), 0, Direction.Axis.X, 8, 0.8F);
        var onOak = Chopping.chop(h.getLevel(), p, h.absolutePos(oak), 0, Direction.Axis.X, 8, 0.8F);
        LOG.info("[тест] chopInWorld: присівши {}, удар {}, купа {} шматків, знос сокири {}; стовбур {}, дуб {}", sneak, r,
                pile == null ? -1 : pile.pieces().size(), wear, onTrunk, onOak);
        for (BlockPos b : new BlockPos[]{ log, trunk, trunk.above(), oak }) h.setBlock(b, Blocks.AIR);
        h.assertTrue(sneak == Chopping.Result.NONE, "присівши теж рубає");
        h.assertTrue(r == Chopping.Result.CLEAN && pile != null && pile.pieces().size() == 2 && wear == 1, "колода не стала купою з 2 половинок");
        h.assertTrue(onTrunk == Chopping.Result.NONE && onOak == Chopping.Result.NONE, "рубає стовбур або не темний дуб");
        h.succeed();
    }

    // 3. Купи: 16 скіпок у купу влазять, 17-та — ні; різні разом (половинка + четвертинка + поліна); назви за розміром; однакові — стос
    @GameTest(template = "arena", batch = "chop", timeoutTicks = 20)
    public static void pilesAndItems(GameTestHelper h) {
        BlockPos at = new BlockPos(4, 2, 4), at2 = new BlockPos(8, 2, 4);
        h.setBlock(at, Chopping.WOOD_PILE.get());
        h.setBlock(at2, Chopping.WOOD_PILE.get());
        var pile = (WoodPileBlockEntity) h.getBlockEntity(at);
        var pile2 = (WoodPileBlockEntity) h.getBlockEntity(at2);
        WoodPiece splinter = new WoodPiece(0, 0, 0, 4, 16, 4, Direction.Axis.Y);
        int fitted = 0;
        for (int i = 0; i < 17; i++) if (pile.add(WoodPieceItem.piece(WoodPieceItem.of(splinter)), false)) fitted++;
        boolean mixed = pile2.add(new WoodPiece(0, 0, 0, 8, 16, 16, Direction.Axis.Y), false)
                && pile2.add(new WoodPiece(0, 0, 0, 8, 16, 8, Direction.Axis.Y), false)
                && pile2.add(new WoodPiece(0, 0, 0, 4, 16, 8, Direction.Axis.Y), false)
                && pile2.add(new WoodPiece(0, 0, 0, 4, 16, 8, Direction.Axis.Y), false);
        String[] names = new String[5];
        int[][] dims = { { 16, 16, 16 }, { 8, 16, 16 }, { 8, 16, 8 }, { 4, 16, 8 }, { 4, 16, 4 } };
        for (int i = 0; i < 5; i++) names[i] = WoodPieceItem.of(new WoodPiece(0, 0, 0, dims[i][0], dims[i][1], dims[i][2], Direction.Axis.Y)).getHoverName().getString();
        ItemStack a = WoodPieceItem.of(new WoodPiece(3, 0, 5, 4, 16, 8, Direction.Axis.Y)), b = WoodPieceItem.of(new WoodPiece(0, 0, 0, 8, 16, 4, Direction.Axis.Y));
        boolean stacks = ItemStack.isSameItemSameTags(a, b);
        // куди клацнув: половинка праворуч (x 8), друга — ліворуч (x 0); лежачи вздовж z — волокна по z
        BlockPos at4 = new BlockPos(4, 2, 8);
        h.setBlock(at4, Chopping.WOOD_PILE.get());
        var pile4 = (WoodPileBlockEntity) h.getBlockEntity(at4);
        WoodPiece half = new WoodPiece(0, 0, 0, 8, 16, 16, Direction.Axis.Y);
        pile4.add(half, false, 13, 8, Direction.Axis.X);
        pile4.add(half, false, 2, 8, Direction.Axis.X);
        int right = pile4.pieces().get(0).x, left = pile4.pieces().get(1).x;
        BlockPos at5 = new BlockPos(8, 2, 8);
        h.setBlock(at5, Chopping.WOOD_PILE.get());
        var pile5 = (WoodPileBlockEntity) h.getBlockEntity(at5);
        WoodPiece quarter = new WoodPiece(0, 0, 0, 8, 16, 8, Direction.Axis.Y);
        int q = 0;
        for (double[] c : new double[][]{ { 3, 3 }, { 12, 3 }, { 3, 12 }, { 12, 12 } }) if (pile5.add(quarter, false, c[0], c[1], Direction.Axis.X)) q++;
        boolean tidy = q == 4 && pile5.pieces().stream().allMatch(p -> p.x % 8 == 0 && p.z % 8 == 0 && p.y == 0);
        BlockPos at6 = new BlockPos(11, 2, 8);
        h.setBlock(at6, Chopping.WOOD_PILE.get());
        var pile6 = (WoodPileBlockEntity) h.getBlockEntity(at6);
        pile6.add(new WoodPiece(0, 0, 0, 4, 16, 4, Direction.Axis.Y), true, 8, 8, Direction.Axis.Z);
        WoodPiece lyingP = pile6.pieces().get(0);
        boolean lyingOk = lyingP.grain == Direction.Axis.Z && lyingP.sz == 16 && lyingP.y == 0;
        for (BlockPos bp : new BlockPos[]{ at4, at5, at6 }) h.setBlock(bp, Blocks.AIR);
        h.assertTrue(right == 8 && left == 0, "не кладеться туди, куди клацнув: " + right + " " + left);
        h.assertTrue(tidy, "четвертинки не лягли рівно 2×2: " + q);
        h.assertTrue(lyingOk, "присівши не лягла вздовж погляду: " + lyingP.grain + " " + lyingP.sz);
        // забрали нижню половинку — верхня осідає
        BlockPos at3 = new BlockPos(11, 2, 4);
        h.setBlock(at3, Chopping.WOOD_PILE.get());
        var pile3 = (WoodPileBlockEntity) h.getBlockEntity(at3);
        List<WoodPiece> two = new ArrayList<>(List.of(WoodPiece.whole(Direction.Axis.Y, true, false)));
        Chopping.chopPieces(two, 0, Direction.Axis.Y, 8, 1F, RandomSource.create(2));
        pile3.setPieces(two);
        pile3.pieces().remove(0);
        pile3.changed();
        int fellTo = pile3.pieces().get(0).y;
        h.setBlock(at3, Blocks.AIR);
        LOG.info("[тест] pilesAndItems: скіпок влізло {} з 17, різні разом {}, назви {}, однакові складаються {}; верхня впала на {}", fitted, mixed,
                String.join(" / ", names), stacks, fellTo);
        h.assertTrue(fellTo == 0, "верхня половинка висить: " + fellTo);
        h.setBlock(at, Blocks.AIR);
        h.setBlock(at2, Blocks.AIR);
        h.assertTrue(fitted == 16, "у купу не 16 скіпок: " + fitted);
        h.assertTrue(mixed, "різні шматки разом не складаються");
        h.assertTrue(stacks, "однакові шматки не стосуються");
        h.succeed();
    }

    // 4. Різьба зі шматка: скіпку на креслення ручки — заготовка 3 × 12 × 3, місце ручки (лежачи) знайдене; не чіпав — назад та сама скіпка
    @GameTest(template = "arena", batch = "chop", timeoutTicks = 20)
    public static void carveFromPiece(GameTestHelper h) {
        BlockPos table = new BlockPos(5, 2, 5), top = table.above();
        h.setBlock(table, Blocks.CRAFTING_TABLE);
        var p = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        Carving.tryPlace(h.getLevel(), p, h.absolutePos(table), new ItemStack(Carving.BLUEPRINT_MUG_HANDLE.get()));
        ItemStack sp = WoodPieceItem.of(new WoodPiece(0, 0, 0, 4, 16, 4, Direction.Axis.Y));
        boolean placed = Carving.tryPlace(h.getLevel(), p, h.absolutePos(top), sp.copy());
        var be = (WorkpieceBlockEntity) h.getBlockEntity(top);
        int vox = be.voxels() == null ? -1 : be.voxels().count();
        int guide = be.guide() == null ? 0 : be.guide().count();
        ItemStack back = WorkpieceBlock.finish(h.getLevel(), be);
        boolean same = ItemStack.isSameItemSameTags(back, sp);
        LOG.info("[тест] carveFromPiece: поклалась {}, «пікселів» {}, місце ручки {} клітинок, не чіпав → {} (та сама {})", placed, vox, guide, back.getHoverName().getString(), same);
        h.setBlock(top, Blocks.AIR);
        h.setBlock(table, Blocks.AIR);
        h.assertTrue(placed && vox == 3 * 12 * 3, "скіпка — не заготовка 3×12×3: " + vox);
        h.assertTrue(guide == 14, "місце ручки в скіпці не знайдене: " + guide);
        h.assertTrue(same, "нечіпана скіпка повернулась не тією: " + back);
        h.succeed();
    }
}
