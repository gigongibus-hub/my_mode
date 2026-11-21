package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.Wind;
import com.gigongibus.hungrycows.chop.Chopping;
import com.gigongibus.hungrycows.fell.Felling;
import com.gigongibus.hungrycows.fell.FellingCutBlockEntity;
import com.gigongibus.hungrycows.phys.WoodBodyEntity;
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
    @GameTest(template = "arena", batch = "fell", timeoutTicks = 700)
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
        for (int dz = -3; dz <= 3; dz++) h.setBlock(fence.offset(0, 0, dz), Blocks.OAK_FENCE);   // паркан упоперек падіння (дуб лягає ±1,5 бл. убік)
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
        var trees = bodies(h, h.absolutePos(base), 4);
        final int hitsF = hits;
        final Chopping.Result rF = r;
        final Vec3 dir = trees.isEmpty() ? Vec3.ZERO : new Vec3(trees.get(0).body.fallDir.x, 0, trees.get(0).body.fallDir.z);
        long start = h.getTick();
        int[] fallTicks = { -1 }, restTicks = { -1 };
        StringBuilder tr0 = new StringBuilder();
        h.onEachTick(() -> {
            if ((h.getTick() - start) % 40 == 0) tr0.append(" ").append(h.getTick() - start).append(trace(h, h.absolutePos(new BlockPos(7, 2, 7))));
            cowFear[0] = Math.max(cowFear[0], cow.fear);
            var list = bodies(h, h.absolutePos(base), 24);
            WoodBodyEntity main = list.stream().max(java.util.Comparator.comparingDouble(e -> e.body.mass)).orElse(null);
            if (main != null) {
                var up = main.body.q.transform(new org.joml.Vector3d(0, 1, 0));
                if (fallTicks[0] < 0 && up.y < 0.35) fallTicks[0] = (int) (h.getTick() - start);
            }
            if (restTicks[0] < 0 && !list.isEmpty() && list.stream().allMatch(e -> e.body.asleep)) {
                restTicks[0] = (int) (h.getTick() - start);
                list.forEach(WoodBodyEntity::toBlocks);                  // не чекати 20 с — лягли, хай стають блоками
            }
        });
        h.runAfterDelay(560, () -> {
            int lying = 0, standing = 0, leaves = 0, piles = 0;
            StringBuilder where = new StringBuilder();
            for (int x = -2; x < 17; x++) for (int y = 1; y < 9; y++) for (int z = -2; z < 17; z++) {
                var s = h.getBlockState(new BlockPos(x, y, z));
                if (s.is(Blocks.OAK_LOG)) { where.append(" ").append(x).append(",").append(y).append(",").append(z); if (s.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y) standing++; else lying++; }
                if (s.is(Blocks.OAK_LEAVES)) leaves++;
                if (s.is(Chopping.WOOD_PILE.get())) {
                    piles++;
                    if (h.getBlockEntity(new BlockPos(x, y, z)) instanceof com.gigongibus.hungrycows.chop.WoodPileBlockEntity pe)
                        for (var w : pe.pieces()) where.append(" купа ").append(x).append(",").append(y).append(",").append(z)
                                .append(" ").append(w.sx).append("x").append(w.sy).append("x").append(w.sz).append(" волокна ").append(w.grain);
                }
            }
            int fences = 0;
            for (int dz = -3; dz <= 3; dz++) if (h.getBlockState(fence.offset(0, 0, dz)).is(Blocks.OAK_FENCE)) fences++;
            boolean fenceGone = fences < 7;
            float pigDmg = pig.isAlive() ? pigHp - pig.getHealth() : pigHp;
            boolean stump = cut != null && cut.felled();
            int left = bodies(h, h.absolutePos(base), 30).size();
            cow.discard();
            LOG.info("[тест] oakFallsTowardNotch: корова злякалась {}, дуб виріс {}, стовбур {}, ударів {} → {}, поперек не рубається {}, напрям {}, впало за {} тіків, лягло за {}, колод лежить {} / стоїть {}, листя лишилось {}, купа з обрубком {}, паркан зламано {}, свиня −{}, пень {}, тіл лишилось {}",
                    cowFear[0], grown, trunk, hitsF, rF, sideways, dir, fallTicks[0], restTicks[0], lying, standing, leaves, piles, fenceGone, pigDmg, stump, left);
            LOG.info("[тест] oakFallsTowardNotch, тіла:{}; колоди:{}", tr0, where);
            pig.discard();
            clear(h);
            h.assertTrue(grown && trunk, "дуб не виріс або не визнано стовбуром");
            h.assertTrue(rF == Chopping.Result.CLEAN && hitsF >= 2 && hitsF <= 6 && sideways, "зарубка не та: " + hitsF + " " + rF);
            h.assertTrue(dir.x > 0.7, "впало не в бік зарубки (схід): " + dir);
            h.assertTrue(fallTicks[0] > 20 && fallTicks[0] < 140, "падало не 1–7 с: " + fallTicks[0]);
            h.assertTrue(restTicks[0] > 0 && left == 0, "не лягло");
            h.assertTrue(lying >= 3 && standing == 0, "колоди не лягли: лежить " + lying + ", стоїть " + standing);
            h.assertTrue(leaves >= 5, "листя не лишилось: " + leaves);
            h.assertTrue(piles >= 1, "обрубок над зарубкою зник");
            h.assertTrue(fenceGone && pigDmg > 2 && stump, "паркан цілий / свиня ціла / немає пня");
            h.assertTrue(cowFear[0] >= 10, "корову не налякав гуркіт: " + cowFear[0]);
            h.succeed();
        });
    }

    /** Що з тілами: скільки, скільки сплять, найбільші швидкості */
    static String trace(GameTestHelper h, BlockPos abs) {
        var l = bodies(h, abs, 40);
        double v = 0, w = 0;
        int asleep = 0, parts = 0;
        StringBuilder sb = new StringBuilder();
        for (WoodBodyEntity e : l) {
            v = Math.max(v, e.body.v.length()); w = Math.max(w, e.body.w.length()); if (e.body.asleep) asleep++; parts += e.body.alive();
            double up = e.body.q.transform(new org.joml.Vector3d(0, 1, 0)).y;
            if (!e.body.asleep || e.body.mass > 5) sb.append(String.format(" {%d ч., m %.1f, v %.2f, w %.2f, угору %.2f%s%s}", e.body.alive(), e.body.mass, e.body.v.length(),
                    e.body.w.length(), up, e.body.hinged ? ", шарнір" : "", e.body.asleep ? ", спить" : ""));
        }
        return String.format(" [%d тіл, сплять %d, частин %d, v %.2f, w %.2f%s]", l.size(), asleep, parts, v, w, sb);
    }

    static java.util.List<WoodBodyEntity> bodies(GameTestHelper h, BlockPos abs, double r) {
        return h.getLevel().getEntitiesOfClass(WoodBodyEntity.class, new AABB(abs).inflate(r));
    }

    /** Прибрати за собою колоди, листя й купи (споруди лишаються у світі для наступних тестів) */
    static void clear(GameTestHelper h) {
        clear(h, 6);
    }

    /** Лише свій майданчик (0…14) і ще margin довкола — сусідні тести тієї ж партії не зачепити */
    static void clear(GameTestHelper h, int margin) {
        for (int x = -margin; x < 15 + margin; x++) for (int y = 2; y < 30; y++) for (int z = -margin; z < 15 + margin; z++) {
            BlockPos p = h.absolutePos(new BlockPos(x, y, z));
            var s = h.getLevel().getBlockState(p);
            if (s.is(net.minecraft.tags.BlockTags.LOGS) || s.is(net.minecraft.tags.BlockTags.LEAVES) || s.is(Chopping.WOOD_PILE.get())
                    || s.is(Felling.FELLING_CUT.get()) || s.is(Blocks.VINE))
                h.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        h.getLevel().getEntitiesOfClass(WoodBodyEntity.class, new AABB(h.absolutePos(new BlockPos(-margin, 1, -margin)),
                h.absolutePos(new BlockPos(15 + margin, 30, 15 + margin)))).forEach(net.minecraft.world.entity.Entity::discard);
    }

    // 3. Великий (розлогий) дуб з гілками: падає цілком — колоди й гілки лягають, листя сиплеться
    @GameTest(template = "arena", batch = "fell_big", timeoutTicks = 700)
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
        int parts = tree == null ? 0 : tree.body.parts.size();
        int logsF = logsBefore;
        boolean grownF = grown;
        int[] maxBodies = { 0 }, restTicks = { -1 };
        long start = h.getTick();
        StringBuilder tr1 = new StringBuilder();
        h.onEachTick(() -> {
            if ((h.getTick() - start) % 40 == 0) tr1.append(" ").append(h.getTick() - start).append(trace(h, h.absolutePos(new BlockPos(7, 2, 7))));
            var list = bodies(h, abs, 30);
            maxBodies[0] = Math.max(maxBodies[0], list.size());
            if (restTicks[0] < 0 && h.getTick() - start > 20 && !list.isEmpty() && list.stream().allMatch(e -> e.body.asleep)) {
                restTicks[0] = (int) (h.getTick() - start);
                list.forEach(WoodBodyEntity::toBlocks);
            }
        });
        h.runAfterDelay(560, () -> {
            boolean gone = bodies(h, abs, 30).isEmpty();
            int lying = 0, standing = 0, leaves = 0;
            for (int x = -16; x <= 20; x++) for (int y = -2; y < 20; y++) for (int z = -16; z <= 16; z++) {
                var s = h.getLevel().getBlockState(abs.offset(x, y, z));
                if (s.is(Blocks.OAK_LOG)) { if (s.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y) standing++; else lying++; }
                if (s.is(Blocks.OAK_LEAVES)) leaves++;
            }
            LOG.info("[тест] fancyOakFalls: виріс {}, колод {} → частин {}, найбільше тіл {} (розламалось), лягло за {}, колод лягло {} / стоїть {}, листя {}, тіл лишилось {}",
                    grownF, logsF, parts, maxBodies[0], restTicks[0], lying, standing, leaves, !gone);
            LOG.info("[тест] fancyOakFalls, тіла:{}", tr1);
            clear(h);
            h.assertTrue(grownF && tree != null && gone, "великий дуб не впав / не ліг");
            // гілки розлогого дуба, що лежали вздовж напрямку падіння, після падіння стоять сторч — тож «стоїть» буває багато
            h.assertTrue(lying + standing >= logsF * 0.7 && lying >= logsF / 3, "колоди не лягли: " + lying + " / " + standing);
            h.succeed();
        });
    }

    // 4. Ялина (Fij: «додай ще сосну»): падає так само; велика ялина 2×2 тримається, поки не підрубано всі 4 стовбури
    @GameTest(template = "arena", batch = "fell_spruce", timeoutTicks = 600)
    public static void spruceFalls(GameTestHelper h) {
        TestWorld.calm(h);
        Wind.set(h.getLevel(), 0, true);
        BlockPos abs = h.absolutePos(new BlockPos(3, 2, 7));
        h.getLevel().setBlock(abs.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        var feat = h.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(TreeFeatures.SPRUCE).value();
        boolean grown = false;
        for (long seed = 1; seed < 20 && !grown; seed++) grown = feat.place(h.getLevel(), h.getLevel().getChunkSource().getGenerator(), RandomSource.create(seed), abs);
        boolean trunk = Felling.isTrunk(h.getLevel(), abs);
        // велика ялина далі, на півночі
        BlockPos megaAbs = h.absolutePos(new BlockPos(11, 2, 3));
        for (int dx = -1; dx <= 2; dx++) for (int dz = -1; dz <= 2; dz++) h.getLevel().setBlock(megaAbs.offset(dx, -1, dz), Blocks.PODZOL.defaultBlockState(), 3);
        var mega = h.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(TreeFeatures.MEGA_SPRUCE).value();
        boolean megaGrown = false;
        for (long seed = 1; seed < 20 && !megaGrown; seed++) megaGrown = mega.place(h.getLevel(), h.getLevel().getChunkSource().getGenerator(), RandomSource.create(seed), megaAbs);
        // з якого кута 2×2 почався стовбур — шукаємо нижню колоду
        BlockPos megaBase = null;
        for (int dx = -1; dx <= 1 && megaBase == null; dx++) for (int dz = -1; dz <= 1 && megaBase == null; dz++)
            if (Felling.isTrunk(h.getLevel(), megaAbs.offset(dx, 0, dz))) megaBase = megaAbs.offset(dx, 0, dz);
        var p = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        Chopping.Result megaR = Chopping.Result.NONE;
        if (megaBase != null) {
            Vec3 c = Vec3.atCenterOf(megaBase);
            p.moveTo(c.x + 2.5, c.y, c.z, 90, 10);
            for (int k = 0; k < 8; k++) megaR = Chopping.chop(h.getLevel(), p, megaBase, 0, Direction.Axis.Y, Felling.DEFAULT_AT, 1F);
        }
        boolean megaHolds = megaBase != null && bodies(h, megaBase, 6).isEmpty();
        var tree = trunk ? Felling.fellAt(h.getLevel(), abs, Direction.EAST) : null;
        var megaTree = megaBase != null ? Felling.fellAt(h.getLevel(), megaBase, Direction.SOUTH) : null;
        boolean trunkF = trunk, grownF = grown, megaGrownF = megaGrown;
        final Chopping.Result megaRF = megaR;
        final boolean megaTrunk = megaBase != null;
        int[] restTicks = { -1 };
        long start = h.getTick();
        StringBuilder tr2 = new StringBuilder();
        h.onEachTick(() -> {
            if ((h.getTick() - start) % 40 == 0) tr2.append(" ").append(h.getTick() - start).append(trace(h, h.absolutePos(new BlockPos(7, 2, 7))));
            var list = bodies(h, h.absolutePos(new BlockPos(7, 2, 7)), 40);
            if (restTicks[0] < 0 && h.getTick() - start > 20 && !list.isEmpty() && list.stream().allMatch(e -> e.body.asleep)) {
                restTicks[0] = (int) (h.getTick() - start);
                list.forEach(WoodBodyEntity::toBlocks);
            }
        });
        h.runAfterDelay(560, () -> {
            int lying = 0, standing = 0;
            for (int x = -16; x < 30; x++) for (int y = 1; y < 30; y++) for (int z = -16; z < 30; z++) {
                var s = h.getBlockState(new BlockPos(x, y, z));
                if (s.is(Blocks.SPRUCE_LOG)) { if (s.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y) standing++; else lying++; }
            }
            LOG.info("[тест] spruceFalls: ялина виросла {}, стовбур {}, тіло {}; велика виросла {}, стовбур {}, один підрубаний → {} тримається {}, звалена {}, лягло за {}, колод лежить {} / стоїть {}",
                    grownF, trunkF, tree != null, megaGrownF, megaTrunk, megaRF, megaHolds, megaTree != null, restTicks[0], lying, standing);
            LOG.info("[тест] spruceFalls, тіла:{}", tr2);
            clear(h);
            h.assertTrue(grownF && trunkF && tree != null, "ялина не впала");
            h.assertTrue(!megaGrownF || megaHolds && megaRF == Chopping.Result.STUCK && megaTree != null, "велика ялина: не тримається або не впала");
            h.assertTrue(lying >= 4 && standing * 4 <= lying, "колоди ялини не лягли: " + lying + " / " + standing);
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

    // Fij (21.11): «зарубав нижню, потім трохи вищу — і зламав нижню: дерево не впало / колода з зарубкою висить над пнем».
    // Над зарубкою другої не почати; зламав колоду під дубом (гравець) — дерево падає фізичним тілом
    @GameTest(template = "arena", batch = "fell_break", timeoutTicks = 200)
    public static void breakUnderTreeFalls(GameTestHelper h) {
        TestWorld.calm(h);
        Wind.set(h.getLevel(), 0, true);
        BlockPos base = new BlockPos(7, 2, 7);
        boolean grown = growOak(h, base, 7);
        var lvl = h.getLevel();
        BlockPos abs = h.absolutePos(base);
        Felling.cut(lvl, axePlayer(h), abs, 5, 0.3F);                         // почата зарубка внизу
        boolean secondAllowed = Felling.isTrunk(lvl, abs.above());
        var p = axePlayer(h);
        p.moveTo(abs.getX() - 2, abs.getY(), abs.getZ() + 0.5);
        boolean broke = p.gameMode.destroyBlock(abs);
        h.runAfterDelay(5, () -> {
            var bodies = lvl.getEntitiesOfClass(com.gigongibus.hungrycows.phys.WoodBodyEntity.class, new net.minecraft.world.phys.AABB(abs).inflate(8));
            boolean logsLeft = lvl.getBlockState(abs.above()).is(Blocks.OAK_LOG);
            LOG.info("[тест] breakUnderTreeFalls: дуб {}, друга зарубка над першою {}, зламав {}, тіл {}, колода над місцем лишилась {}",
                    grown, secondAllowed, broke, bodies.size(), logsLeft);
            bodies.forEach(e -> e.discard());
            clear(h, 0);
            h.assertTrue(grown && !secondAllowed, "другу зарубку над першою можна почати");
            h.assertTrue(broke && bodies.size() == 1 && !logsLeft, "дерево не впало, коли зламали колоду під ним");
            h.succeed();
        });
    }

    static net.minecraftforge.common.util.FakePlayer axePlayer(GameTestHelper h) {
        var p = net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("breaker".getBytes()), "break_tester"));
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_AXE));
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return p;
    }
}
