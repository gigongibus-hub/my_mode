package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModItems;
import com.gigongibus.hungrycows.salt.Salt;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Сіль (план Fij): руда, ступка (кроки 1–2). */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowSaltTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final int CUBE = 13;           // кам'яний куб 13³ над загоном — туди «ростимо» жилу
    private static final int CUBE_Y = 20;
    private static final int VEINS = 30;          // скільки жил виростити для заміру
    private static final int DROP_SAMPLES = 60;   // скільки разів «зламати» руду для заміру випадіння
    private static final int CRAFTS = 200;        // скільки разів потовкти

    private static void fillCube(GameTestHelper h, Block b) {
        for (int x = 1; x <= CUBE; x++) for (int y = CUBE_Y; y < CUBE_Y + CUBE; y++) for (int z = 1; z <= CUBE; z++) h.setBlock(new BlockPos(x, y, z), b);
    }

    // 1. Жила (Fij «від 10 до 25 блоків»): виростити жилу в камені 30 разів і порахувати блоки
    @GameTest(template = "arena", batch = "salt", timeoutTicks = 100)
    public static void oreVeinSize(GameTestHelper h) {
        var feature = h.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .get(new ResourceLocation(HungryCowsMod.MODID, "salt_ore"));
        h.assertTrue(feature != null, "немає жили hungrycows:salt_ore");
        int min = Integer.MAX_VALUE, max = 0, sum = 0;
        BlockPos center = h.absolutePos(new BlockPos(7, CUBE_Y + CUBE / 2, 7));
        for (int i = 0; i < VEINS; i++) {
            fillCube(h, Blocks.STONE);
            feature.place(h.getLevel(), h.getLevel().getChunkSource().getGenerator(), h.getLevel().random, center);
            int n = 0;
            for (int x = 1; x <= CUBE; x++) for (int y = CUBE_Y; y < CUBE_Y + CUBE; y++) for (int z = 1; z <= CUBE; z++)
                if (h.getBlockState(new BlockPos(x, y, z)).is(Salt.SALT_ORE.get())) n++;
            min = Math.min(min, n); max = Math.max(max, n); sum += n;
        }
        fillCube(h, Blocks.AIR);
        LOG.info("[тест] oreVeinSize: жила {}–{} блоків, у середньому {}", min, max, String.format("%.1f", sum / (double) VEINS));
        h.assertTrue(sum / (double) VEINS >= 10 && sum / (double) VEINS <= 25, "середня жила не 10–25: " + sum / (double) VEINS);
        h.succeed();
    }

    // 2. Що падає: кам'яне кайло — 1–4 рудної солі (удача більше), шовковий дотик — сама руда, дерев'яне — нічого
    @GameTest(template = "arena", batch = "salt", timeoutTicks = 20)
    public static void oreDrops(GameTestHelper h) {
        BlockState ore = Salt.SALT_ORE.get().defaultBlockState();
        BlockPos at = h.absolutePos(new BlockPos(7, 2, 7));
        ItemStack stone = new ItemStack(Items.STONE_PICKAXE), silk = new ItemStack(Items.STONE_PICKAXE), fortune = new ItemStack(Items.STONE_PICKAXE);
        silk.enchant(Enchantments.SILK_TOUCH, 1);
        fortune.enchant(Enchantments.BLOCK_FORTUNE, 3);
        int min = 99, max = 0, sumF = 0;
        for (int i = 0; i < DROP_SAMPLES; i++) {
            int n = Block.getDrops(ore, h.getLevel(), at, null, null, stone).stream().filter(s -> s.is(Salt.ROCK_SALT.get())).mapToInt(ItemStack::getCount).sum();
            min = Math.min(min, n); max = Math.max(max, n);
            sumF += Block.getDrops(ore, h.getLevel(), at, null, null, fortune).stream().mapToInt(ItemStack::getCount).sum();
        }
        var silkDrop = Block.getDrops(ore, h.getLevel(), at, null, null, silk);
        boolean wood = new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(ore), stoneOk = stone.isCorrectToolForDrops(ore);
        LOG.info("[тест] oreDrops: кам'яне кайло {}–{}, удача III у середньому {}, шовк {}, дерев'яне кайло годиться {}", min, max,
                String.format("%.1f", sumF / (double) DROP_SAMPLES), silkDrop, wood);
        h.assertTrue(min == 1 && max == 4, "не 1–4 шматки: " + min + "–" + max);
        h.assertTrue(sumF / (double) DROP_SAMPLES > 3.0, "удача не дає більше");
        h.assertTrue(silkDrop.size() == 1 && silkDrop.get(0).is(Salt.SALT_ORE_ITEM.get()), "шовковий дотик не дає руду");
        h.assertTrue(stoneOk && !wood, "кайло: кам'яне " + stoneOk + ", дерев'яне " + wood);
        h.succeed();
    }

    // 3. Ступка + рудна сіль → 1–2 «не чистої» (2 — з шансом 50%), ступка зношується на 1
    @GameTest(template = "arena", batch = "salt", timeoutTicks = 20)
    public static void mortarGrindsSalt(GameTestHelper h) {
        var menu = new net.minecraft.world.inventory.AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p, int i) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player p) { return true; }
        };
        var grid = new net.minecraft.world.inventory.TransientCraftingContainer(menu, 3, 3);
        grid.setItem(0, new ItemStack(ModItems.MORTAR.get()));
        grid.setItem(4, new ItemStack(Salt.ROCK_SALT.get()));
        var recipe = h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, h.getLevel());
        h.assertTrue(recipe.isPresent(), "рецепт ступки й рудної солі не знайдено");
        ItemStack out = recipe.get().assemble(grid, h.getLevel().registryAccess());
        ItemStack left = recipe.get().getRemainingItems(grid).get(0);
        var player = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        player.getInventory().clearContent();
        for (int i = 0; i < CRAFTS; i++) net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent(player, out.copy(), grid));
        int extra = player.getInventory().countItem(Salt.GROUND_SALT_DIRTY.get());
        player.getInventory().clearContent();
        LOG.info("[тест] mortarGrindsSalt: {} x{}, ступка після: знос {}, додаткова за {} разів: {}", out.getItem(), out.getCount(),
                left.getDamageValue(), CRAFTS, extra);
        h.assertTrue(out.is(Salt.GROUND_SALT_DIRTY.get()) && out.getCount() == 1, "не 1 «не чиста»");
        h.assertTrue(left.is(ModItems.MORTAR.get()) && left.getDamageValue() == 1, "ступка не зношена на 1");
        h.assertTrue(extra > CRAFTS * 0.35 && extra < CRAFTS * 0.65, "додаткова не ~50%: " + extra + " з " + CRAFTS);
        h.succeed();
    }

    // ---------- крок 3: діжка ----------
    static net.minecraft.world.InteractionResult use(GameTestHelper h, BlockPos rel, net.minecraft.world.entity.player.Player p, ItemStack held, boolean sneak) {
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
        p.setShiftKeyDown(sneak);
        BlockPos at = h.absolutePos(rel);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at), net.minecraft.core.Direction.UP, at, false);
        return h.getLevel().getBlockState(at).use(h.getLevel(), p, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
    }

    // 4. 10 відер води + 30 «не чистої» + кришка → доба (прискорено ×1000) → 10 відер розчину; з 29 сіллю — нічого
    @GameTest(template = "arena", batch = "salt_tub", timeoutTicks = 200)
    public static void tubMakesBrine(GameTestHelper h) {
        BlockPos tub = new BlockPos(5, 2, 7), tub2 = new BlockPos(9, 2, 7);
        h.setBlock(tub, Salt.TUB.get());
        h.setBlock(tub2, Salt.TUB.get());
        var p = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        for (BlockPos t : new BlockPos[]{ tub, tub2 }) {
            for (int i = 0; i < 10; i++) use(h, t, p, new ItemStack(Items.WATER_BUCKET), false);
            use(h, t, p, new ItemStack(Items.WATER_BUCKET), false);   // 11-те — не влазить
        }
        h.assertTrue(h.getBlockState(tub).getValue(com.gigongibus.hungrycows.salt.TubBlock.LEVEL) == 10, "не 10 відер води");
        ((com.gigongibus.hungrycows.salt.TubBlockEntity) h.getBlockEntity(tub)).salt.setItem(0, new ItemStack(Salt.GROUND_SALT_DIRTY.get(), 30));
        ((com.gigongibus.hungrycows.salt.TubBlockEntity) h.getBlockEntity(tub2)).salt.setItem(0, new ItemStack(Salt.GROUND_SALT_DIRTY.get(), 29));
        use(h, tub, p, new ItemStack(Salt.TUB_LID.get()), false);
        use(h, tub2, p, new ItemStack(Salt.TUB_LID.get()), false);
        h.assertTrue(h.getBlockState(tub).getValue(com.gigongibus.hungrycows.salt.TubBlock.LID), "кришка не закрилась");
        com.gigongibus.hungrycows.WorldClock.setSpeed(h.getLevel(), com.gigongibus.hungrycows.WorldClock.SPEED_MAX);
        h.runAfterDelay(199, () -> com.gigongibus.hungrycows.WorldClock.setSpeed(h.getLevel(), 1));
        h.succeedWhen(() -> {
            h.assertTrue(h.getBlockState(tub).getValue(com.gigongibus.hungrycows.salt.TubBlock.LIQUID) == com.gigongibus.hungrycows.salt.TubBlock.Liquid.BRINE,
                    "розчин ще не готовий: " + ((com.gigongibus.hungrycows.salt.TubBlockEntity) h.getBlockEntity(tub)).progress());
            com.gigongibus.hungrycows.WorldClock.setSpeed(h.getLevel(), 1);
            int left = ((com.gigongibus.hungrycows.salt.TubBlockEntity) h.getBlockEntity(tub2)).progress();
            boolean brine2 = h.getBlockState(tub2).getValue(com.gigongibus.hungrycows.salt.TubBlock.LIQUID) == com.gigongibus.hungrycows.salt.TubBlock.Liquid.BRINE;
            var openTry = use(h, tub, p, new ItemStack(Items.BUCKET), false);   // під кришкою розчин не набрати
            int levelAfterTry = h.getBlockState(tub).getValue(com.gigongibus.hungrycows.salt.TubBlock.LEVEL);
            use(h, tub, p, ItemStack.EMPTY, true);                              // зняти кришку
            int buckets = 0;
            for (int i = 0; i < 12; i++) {
                use(h, tub, p, new ItemStack(Items.BUCKET), false);
                if (p.getMainHandItem().is(Salt.SALT_SOLUTION.get())) buckets++;
            }
            boolean empty = h.getBlockState(tub).getValue(com.gigongibus.hungrycows.salt.TubBlock.LEVEL) == 0
                    && ((com.gigongibus.hungrycows.salt.TubBlockEntity) h.getBlockEntity(tub)).salt.isEmpty();
            LOG.info("[тест] tubMakesBrine: розчин готовий за {} с (×1000), відер розчину {}, друга (29 солі) прогрес {} розчин {}, під кришкою відро: рівень {}",
                    h.getTick() / 20.0, buckets, left, brine2, levelAfterTry);
            h.assertTrue(levelAfterTry == 10, "набрала розчин під кришкою");
            h.assertTrue(buckets == 10 && empty, "не 10 відер розчину (" + buckets + ") або сіль лишилась");
            h.assertTrue(left == 0 && !brine2, "з 29 сіллю теж пішло");
            h.setBlock(tub, Blocks.AIR);
            h.setBlock(tub2, Blocks.AIR);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p.setShiftKeyDown(false);
        });
    }

    static net.minecraft.world.inventory.TransientCraftingContainer grid() {
        var menu = new net.minecraft.world.inventory.AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p, int i) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player p) { return true; }
        };
        return new net.minecraft.world.inventory.TransientCraftingContainer(menu, 3, 3);
    }

    // 5. Відро розчину + сито → 5 недоочищеної; лишаються порожнє відро й сито −1; сито ламається на 20-й раз; рецепт сита
    @GameTest(template = "arena", batch = "salt", timeoutTicks = 20)
    public static void sieveFiltersBrine(GameTestHelper h) {
        var g = grid();
        ItemStack sieve = new ItemStack(Salt.SIEVE.get());
        g.setItem(3, new ItemStack(Salt.SALT_SOLUTION.get()));
        g.setItem(5, sieve);
        var r = h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, g, h.getLevel());
        h.assertTrue(r.isPresent(), "рецепт сита з розчином не знайдено");
        ItemStack out = r.get().assemble(g, h.getLevel().registryAccess());
        var left = r.get().getRemainingItems(g);
        sieve.setDamageValue(Salt.SIEVE_USES - 1);
        boolean broke = r.get().getRemainingItems(g).get(5).isEmpty();
        var g2 = grid();
        Object[] pat = { Items.STICK, Salt.BURLAP.get(), Items.STICK, Items.STRING, Salt.BURLAP.get(), Items.STRING, Items.STICK, Salt.BURLAP.get(), Items.STICK };
        for (int i = 0; i < 9; i++) g2.setItem(i, new ItemStack((net.minecraft.world.item.Item) pat[i]));
        var r2 = h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, g2, h.getLevel());
        LOG.info("[тест] sieveFiltersBrine: {} x{}, лишилось: {} і {}, на 20-й раз сито зламалось {}, рецепт сита {}", out.getItem(), out.getCount(),
                left.get(3), left.get(5), broke, r2.map(x -> x.assemble(g2, h.getLevel().registryAccess()).toString()).orElse("немає"));
        h.assertTrue(out.is(Salt.GROUND_SALT_HALF_PURIFIED.get()) && out.getCount() == Salt.SIEVE_OUTPUT, "не 5 недоочищеної");
        h.assertTrue(left.get(3).is(Items.BUCKET), "відро не повернулось");
        h.assertTrue(left.get(5).is(Salt.SIEVE.get()) && left.get(5).getDamageValue() == 1, "сито не зношене на 1");
        h.assertTrue(broke, "сито не зламалось після 20 разів");
        h.assertTrue(r2.isPresent() && r2.get().assemble(g2, h.getLevel().registryAccess()).is(Salt.SIEVE.get()), "рецепт сита не працює");
        h.succeed();
    }

    // 6. Сковорода на багатті: по одній до 20 → пів доби (×1000) → 20 прожареної; згасло — прогрес спадає повільно; на душ-багатті — ні
    @GameTest(template = "arena", batch = "salt_tub", timeoutTicks = 200)
    public static void panRoastsSalt(GameTestHelper h) {
        BlockPos fire = new BlockPos(4, 2, 4), pan = fire.above(), fire2 = new BlockPos(10, 2, 4), pan2 = fire2.above(), soul = new BlockPos(7, 2, 10);
        h.setBlock(fire, Blocks.CAMPFIRE);
        h.setBlock(fire2, Blocks.CAMPFIRE);
        h.setBlock(soul, Blocks.SOUL_CAMPFIRE);
        h.setBlock(pan, Salt.SALT_PAN.get());
        h.setBlock(pan2, Salt.SALT_PAN.get());
        boolean soulOk = Salt.SALT_PAN.get().defaultBlockState().canSurvive(h.getLevel(), h.absolutePos(soul.above()));
        var p = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        for (BlockPos t : new BlockPos[]{ pan, pan2 }) for (int i = 0; i < 21; i++) use(h, t, p, new ItemStack(Salt.GROUND_SALT_HALF_PURIFIED.get(), 64), false);
        var be = (com.gigongibus.hungrycows.salt.SaltPanBlockEntity) h.getBlockEntity(pan);
        var be2 = (com.gigongibus.hungrycows.salt.SaltPanBlockEntity) h.getBlockEntity(pan2);
        int piles = h.getBlockState(pan).getValue(com.gigongibus.hungrycows.salt.SaltPanBlock.PILES);
        com.gigongibus.hungrycows.WorldClock.setSpeed(h.getLevel(), com.gigongibus.hungrycows.WorldClock.SPEED_MAX);
        int[] peak = { 0 };
        // друга: 5 тіків горить (~5000), потім згасає
        h.runAfterDelay(5, () -> {
            peak[0] = be2.progress();
            h.setBlock(fire2, Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
        });
        h.runAfterDelay(199, () -> com.gigongibus.hungrycows.WorldClock.setSpeed(h.getLevel(), 1));
        h.runAtTickTime(30, () -> {
            com.gigongibus.hungrycows.WorldClock.setSpeed(h.getLevel(), 1);
            boolean roasted = h.getBlockState(pan).getValue(com.gigongibus.hungrycows.salt.SaltPanBlock.ROASTED);
            int after = be2.progress();
            use(h, pan, p, ItemStack.EMPTY, false);
            int got = p.getInventory().countItem(Salt.GROUND_SALT_ROASTED.get());
            p.getInventory().clearContent();
            LOG.info("[тест] panRoastsSalt: поклала {} (купок {}), прожарилась {}, забрала прожареної {}; згасле: було {} → {}; на душ-багаття {}",
                    20, piles, roasted, got, peak[0], after, soulOk);
            h.setBlock(pan, Blocks.AIR); h.setBlock(pan2, Blocks.AIR); h.setBlock(fire, Blocks.AIR); h.setBlock(fire2, Blocks.AIR); h.setBlock(soul, Blocks.AIR);
            h.assertTrue(piles == 4, "не 4 купки на 20 солі");
            h.assertTrue(roasted && got == 20, "не 20 прожареної: " + roasted + ", " + got);
            h.assertTrue(after < peak[0] && after > peak[0] - 25 * 1000 * 0.25 - 1, "прогрес згаслого не спадає повільно: " + peak[0] + " → " + after);
            h.assertTrue(!soulOk, "сковорода стає на душ-багаття");
            h.succeed();
        });
    }
}
