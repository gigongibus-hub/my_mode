package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.Wind;
import com.gigongibus.hungrycows.chop.Chopping;
import com.gigongibus.hungrycows.fell.FallingTreeEntity;
import com.gigongibus.hungrycows.fell.Felling;
import com.gigongibus.hungrycows.fell.FellingCutBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** Падіння дерева (Fij, 19.11): зарубка, падіння в бік зарубки, руйнування за вагою, удар по істотах, колоди на землі */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowFellTests {
    private static final Logger LOG = LogUtils.getLogger();

    /** Справжній дуб, як росте у світі (ванільне «дерево дуба»), на траві */
    static boolean growOak(GameTestHelper h, BlockPos rel, long seed) {
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().setBlock(abs.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        var feat = h.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(TreeFeatures.OAK).value();
        return feat.place(h.getLevel(), h.getLevel().getChunkSource().getGenerator(), RandomSource.create(seed), abs);
    }

    // 1. Дуб: удари сокирою по стовбуру (горизонтально) — зарубка; ~¾ — падає в бік гравця (зарубки), ламає паркан на шляху,
    //    б'є свиню, лягає: колоди лежать на землі, лишився пень
    @GameTest(template = "arena", batch = "fell", timeoutTicks = 300)
    public static void oakFallsTowardNotch(GameTestHelper h) {
        TestWorld.calm(h);
        Wind.set(h.getLevel(), 0, true);
        BlockPos base = new BlockPos(3, 2, 7);
        boolean grown = growOak(h, base, 7);
        var p = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        Vec3 c = Vec3.atCenterOf(h.absolutePos(base));
        p.moveTo(c.x + 2.5, c.y, c.z, 90, 10);                         // гравець на схід від дерева — зарубка зі сходу
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        boolean trunk = Felling.isTrunk(h.getLevel(), h.absolutePos(base));
        BlockPos fence = new BlockPos(7, 2, 7);
        h.setBlock(fence, Blocks.OAK_FENCE);
        var pig = h.spawn(EntityType.PIG, new BlockPos(6, 2, 7));
        pig.setNoAi(true);
        float pigHp = pig.getHealth();
        var cow = TestWorld.cow(h, new BlockPos(2, 2, 13));              // корова неподалік — гуркіт лякає
        cow.fear = 0;
        int[] cowFear = { 0 };
        int hits = 0;
        Chopping.Result r = Chopping.Result.NONE;
        for (; hits < 12 && r != Chopping.Result.CLEAN; hits++)
            r = Chopping.chop(h.getLevel(), p, h.absolutePos(base), 0, Direction.Axis.Y, Felling.DEFAULT_AT, 0.8F);
        boolean sideways = Chopping.chop(h.getLevel(), p, h.absolutePos(base), 0, Direction.Axis.X, 8, 1F) == Chopping.Result.NONE;
        var cut = h.getLevel().getBlockEntity(h.absolutePos(base)) instanceof FellingCutBlockEntity be ? be : null;
        var trees = h.getLevel().getEntitiesOfClass(FallingTreeEntity.class, new AABB(h.absolutePos(base)).inflate(4));
        final int hitsF = hits;
        final Chopping.Result rF = r;
        final Vec3 dir = trees.isEmpty() ? Vec3.ZERO : trees.get(0).dir();
        long start = h.getTick();
        int[] fallTicks = { -1 };
        h.onEachTick(() -> {
            cowFear[0] = Math.max(cowFear[0], cow.fear);
            if (fallTicks[0] < 0 && h.getLevel().getEntitiesOfClass(FallingTreeEntity.class, new AABB(h.absolutePos(base)).inflate(20)).isEmpty())
                fallTicks[0] = (int) (h.getTick() - start);
        });
        h.runAfterDelay(200, () -> {
            int lying = 0, standing = 0;
            for (int x = 0; x < 15; x++) for (int y = 2; y < 9; y++) for (int z = 0; z < 15; z++) {
                var s = h.getBlockState(new BlockPos(x, y, z));
                if (s.is(Blocks.OAK_LOG)) { if (s.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y) standing++; else lying++; }
            }
            boolean fenceGone = !h.getBlockState(fence).is(Blocks.OAK_FENCE);
            float pigDmg = pig.isAlive() ? pigHp - pig.getHealth() : pigHp;
            boolean stump = cut != null && cut.felled();
            cow.discard();
            LOG.info("[тест] oakFallsTowardNotch: корова злякалась {}, дуб виріс {}, стовбур {}, ударів {} → {}, поперек не рубається {}, напрям {}, впало за {} тіків, колод лежить {} / стоїть {}, паркан зламано {}, свиня −{}, пень {}",
                    cowFear[0], grown, trunk, hitsF, rF, sideways, dir, fallTicks[0], lying, standing, fenceGone, pigDmg, stump);
            pig.discard();
            h.assertTrue(grown && trunk, "дуб не виріс або не визнано стовбуром");
            h.assertTrue(rF == Chopping.Result.CLEAN && hitsF >= 2 && hitsF <= 6 && sideways, "зарубка не та: " + hitsF + " " + rF);
            h.assertTrue(dir.x > 0.7, "впало не в бік зарубки (схід): " + dir);
            h.assertTrue(fallTicks[0] > 20 && fallTicks[0] < 120, "падало не 1–6 с: " + fallTicks[0]);
            h.assertTrue(lying >= 3 && standing == 0, "колоди не лягли: лежить " + lying + ", стоїть " + standing);
            h.assertTrue(fenceGone && pigDmg > 2 && stump, "паркан цілий / свиня ціла / немає пня");
            h.assertTrue(cowFear[0] >= 10, "корову не налякав гуркіт: " + cowFear[0]);
            h.succeed();
        });
    }

    // 3. Великий (розлогий) дуб з гілками: падає цілком — колоди й гілки лягають, листя сиплеться
    @GameTest(template = "arena", batch = "fell_big", timeoutTicks = 400)
    public static void fancyOakFalls(GameTestHelper h) {
        TestWorld.calm(h);
        Wind.set(h.getLevel(), 0, true);
        BlockPos base = new BlockPos(3, 2, 7);
        BlockPos abs = h.absolutePos(base);
        h.getLevel().setBlock(abs.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        var feat = h.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(TreeFeatures.FANCY_OAK).value();
        boolean grown = false;
        for (long seed = 1; seed < 20 && !grown; seed++) grown = feat.place(h.getLevel(), h.getLevel().getChunkSource().getGenerator(), RandomSource.create(seed), abs);
        int logsBefore = 0;
        for (int x = -8; x <= 8; x++) for (int y = 0; y < 20; y++) for (int z = -8; z <= 8; z++)
            if (h.getLevel().getBlockState(abs.offset(x, y, z)).is(Blocks.OAK_LOG)) logsBefore++;
        var tree = Felling.fellAt(h.getLevel(), abs, Direction.EAST);
        int parts = tree == null ? 0 : tree.parts.size();
        int logsF = logsBefore;
        boolean grownF = grown;
        h.runAfterDelay(300, () -> {
            boolean gone = h.getLevel().getEntitiesOfClass(FallingTreeEntity.class, new AABB(abs).inflate(30)).isEmpty();
            int lying = 0, standing = 0;
            for (int x = -16; x <= 20; x++) for (int y = -2; y < 20; y++) for (int z = -16; z <= 16; z++) {
                var s = h.getLevel().getBlockState(abs.offset(x, y, z));
                if (s.is(Blocks.OAK_LOG)) { if (s.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y) standing++; else lying++; }
            }
            LOG.info("[тест] fancyOakFalls: виріс {}, колод {} → частин {}, лягло {} / стоїть {}, сутність зникла {}", grownF, logsF, parts, lying, standing, gone);
            h.assertTrue(grownF && tree != null && gone, "великий дуб не впав");
            h.assertTrue(lying >= logsF / 2 && standing <= 2, "колоди не лягли: " + lying + " / " + standing);
            h.succeed();
        });
    }

    // 2. Стовп з колод без листя (будинок) — не дерево: не валиться; колона ламається, як завжди
    @GameTest(template = "arena", batch = "fell", timeoutTicks = 20)
    public static void pillarIsNotATree(GameTestHelper h) {
        BlockPos base = new BlockPos(11, 2, 3);
        for (int y = 0; y < 4; y++) h.setBlock(base.above(y), Blocks.OAK_LOG);
        boolean trunk = Felling.isTrunk(h.getLevel(), h.absolutePos(base));
        for (int y = 0; y < 4; y++) h.setBlock(base.above(y), Blocks.AIR);
        h.assertFalse(trunk, "стовп без листя визнано деревом");
        h.succeed();
    }
}
