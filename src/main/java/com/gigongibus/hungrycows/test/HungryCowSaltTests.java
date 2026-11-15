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
    /** ПКМ сковородою по блоку (зверху), як гравець */
    static void placePan(GameTestHelper h, net.minecraft.world.entity.player.Player p, BlockPos rel) {
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Salt.SALT_PAN_ITEM.get()));
        p.setShiftKeyDown(false);
        BlockPos at = h.absolutePos(rel);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at).add(0, 0.5, 0), net.minecraft.core.Direction.UP, at, false);
        p.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(p, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
    }

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
        BlockPos fire = new BlockPos(4, 2, 4), pan = fire, fire2 = new BlockPos(10, 2, 4), pan2 = fire2, soul = new BlockPos(7, 2, 10), ground = new BlockPos(7, 2, 7);
        h.setBlock(fire, Blocks.CAMPFIRE);
        h.setBlock(fire2, Blocks.CAMPFIRE);
        h.setBlock(soul, Blocks.SOUL_CAMPFIRE);
        var p = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        // ставить сковороду ПКМ по багаттю: стає прямо на поліна, на місце багаття (Fij); на душ-багаття — ні
        for (BlockPos f : new BlockPos[]{ fire, fire2, soul, ground.below() }) placePan(h, p, f);
        boolean soulOk = h.getBlockState(soul).is(Salt.SALT_PAN.get());
        boolean onLogs = h.getBlockState(pan).is(Salt.SALT_PAN.get())
                && h.getBlockState(pan).getValue(com.gigongibus.hungrycows.salt.SaltPanBlock.CAMPFIRE) == com.gigongibus.hungrycows.salt.SaltPanBlock.Fire.LIT;
        boolean onGround = h.getBlockState(ground).is(Salt.SALT_PAN.get());
        for (BlockPos t : new BlockPos[]{ pan, pan2, ground }) for (int i = 0; i < 21; i++) use(h, t, p, new ItemStack(Salt.GROUND_SALT_HALF_PURIFIED.get(), 64), false);
        var be = (com.gigongibus.hungrycows.salt.SaltPanBlockEntity) h.getBlockEntity(pan);
        var be2 = (com.gigongibus.hungrycows.salt.SaltPanBlockEntity) h.getBlockEntity(pan2);
        int piles = h.getBlockState(pan).getValue(com.gigongibus.hungrycows.salt.SaltPanBlock.FILL);
        com.gigongibus.hungrycows.WorldClock.setSpeed(h.getLevel(), com.gigongibus.hungrycows.WorldClock.SPEED_MAX);
        int[] peak = { 0 };
        // друга: 5 тіків горить (~5000), потім згасає
        h.runAfterDelay(5, () -> {
            peak[0] = be2.progress();
            use(h, pan2, p, new ItemStack(net.minecraft.world.item.Items.WOODEN_SHOVEL), false);   // погасити лопатою
        });
        h.runAfterDelay(199, () -> com.gigongibus.hungrycows.WorldClock.setSpeed(h.getLevel(), 1));
        h.runAtTickTime(30, () -> {
            com.gigongibus.hungrycows.WorldClock.setSpeed(h.getLevel(), 1);
            boolean roasted = h.getBlockState(pan).getValue(com.gigongibus.hungrycows.salt.SaltPanBlock.ROASTED);
            int after = be2.progress();
            var beG = (com.gigongibus.hungrycows.salt.SaltPanBlockEntity) h.getBlockEntity(ground);
            int groundProgress = beG == null ? -1 : beG.progress();
            use(h, pan, p, ItemStack.EMPTY, false);
            int got = p.getInventory().countItem(Salt.GROUND_SALT_ROASTED.get());
            p.getInventory().clearContent();
            LOG.info("[тест] panRoastsSalt: поклала {} (рівень шару {}), прожарилась {}, забрала прожареної {}; згасле: було {} → {}; на душ-багаття {}; на поліна {}; на землю {} (прогрес {})",
                    20, piles, roasted, got, peak[0], after, soulOk, onLogs, onGround, groundProgress);
            // що випадає (Fij: як ванільне багаття): кайлом — сковорода + 2 деревні вугілля; шовковим дотиком — сковорода + багаття
            var sl = h.getLevel();
            BlockPos ap = h.absolutePos(pan2);
            ItemStack pick = new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE), silk = pick.copy();
            silk.enchant(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, 1);
            String plain = net.minecraft.world.level.block.Block.getDrops(sl.getBlockState(ap), sl, ap, null, null, pick).toString();
            String silky = net.minecraft.world.level.block.Block.getDrops(sl.getBlockState(ap), sl, ap, null, null, silk).toString();
            LOG.info("[тест] panRoastsSalt: зламати кайлом → {}, шовковим дотиком → {}", plain, silky);
            h.assertTrue(plain.contains("2 charcoal") && plain.contains("salt_pan") && !plain.contains("campfire"), "кайлом не сковорода + 2 вугілля: " + plain);
            h.assertTrue(silky.contains("campfire") && silky.contains("salt_pan") && !silky.contains("charcoal"), "шовковим не сковорода + багаття: " + silky);
            h.setBlock(pan, Blocks.AIR); h.setBlock(pan2, Blocks.AIR); h.setBlock(soul, Blocks.AIR); h.setBlock(soul.above(), Blocks.AIR); h.setBlock(ground, Blocks.AIR);
            h.assertTrue(onLogs, "сковорода не стала на поліна (на місце багаття)");
            h.assertTrue(onGround && groundProgress == 0, "на землю не ставиться або смажить без вогню: " + onGround + ", " + groundProgress);
            h.assertTrue(piles == 10, "шар не повний (10) на 20 солі: " + piles);
            h.assertTrue(roasted && got == 20, "не 20 прожареної: " + roasted + ", " + got);
            h.assertTrue(after < peak[0] && after > peak[0] - 25 * 1000 * 0.25 - 1, "прогрес згаслого не спадає повільно: " + peak[0] + " → " + after);
            h.assertTrue(!soulOk, "сковорода стає на душ-багаття");
            h.succeed();
        });
    }

    // ---------- крок 5: лоза ----------
    // 7. Ліана мечем → 1–4 лози (ліана зникає); ножицями присівши — ні (ванільно)
    @GameTest(template = "arena", batch = "salt", timeoutTicks = 20)
    public static void vineGivesWicker(GameTestHelper h) {
        var p = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        int min = 99, max = 0;
        BlockPos wall = new BlockPos(7, 2, 7), vine = new BlockPos(7, 2, 8);
        h.setBlock(wall, Blocks.STONE);
        for (int i = 0; i < 30; i++) {
            h.setBlock(vine, Blocks.VINE.defaultBlockState().setValue(net.minecraft.world.level.block.VineBlock.NORTH, true));
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            p.setShiftKeyDown(false);
            boolean done = com.gigongibus.hungrycows.salt.WickerHarvest.harvest(h.getLevel(), h.absolutePos(vine), h.getBlockState(vine), p);
            int n = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(vine)).inflate(2))
                    .stream().filter(e -> e.getItem().is(Salt.WICKER.get())).mapToInt(e -> e.getItem().getCount()).sum();
            h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(vine)).inflate(2)).forEach(e -> e.discard());
            h.assertTrue(done && h.getBlockState(vine).isAir(), "ліана не зібрана");
            min = Math.min(min, n); max = Math.max(max, n);
        }
        h.setBlock(vine, Blocks.VINE.defaultBlockState().setValue(net.minecraft.world.level.block.VineBlock.NORTH, true));
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
        p.setShiftKeyDown(true);
        boolean sneak = com.gigongibus.hungrycows.salt.WickerHarvest.harvest(h.getLevel(), h.absolutePos(vine), h.getBlockState(vine), p);
        p.setShiftKeyDown(false);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.setBlock(vine, Blocks.AIR);
        h.setBlock(wall, Blocks.AIR);
        LOG.info("[тест] vineGivesWicker: меч — {}–{} лози, ножиці присівши — лоза {}", min, max, sneak);
        h.assertTrue(min == 1 && max == 4, "не 1–4 лози: " + min + "–" + max);
        h.assertTrue(!sneak, "присівши з ножицями — теж лоза");
        h.succeed();
    }

    // 8. Пучок на сонці (×1000) → сухий за 2 «сонячні дні»; під дахом — свіжий; рецепти пучка, спиці, мішковини; гнилий — у компостер
    @GameTest(template = "arena", batch = "salt", timeoutTicks = 200)
    public static void bundleDriesInSun(GameTestHelper h) {
        h.getLevel().setDayTime(1000);
        TestWorld.calm(h);
        BlockPos sun = new BlockPos(4, 2, 4), shade = new BlockPos(10, 2, 4);
        h.setBlock(sun, Salt.WICKER_BUNDLE.get());
        h.setBlock(shade, Salt.WICKER_BUNDLE.get());
        h.setBlock(shade.above(3), Blocks.STONE);   // дах
        com.gigongibus.hungrycows.WorldClock.setSpeed(h.getLevel(), com.gigongibus.hungrycows.WorldClock.SPEED_MAX);
        h.runAfterDelay(199, () -> com.gigongibus.hungrycows.WorldClock.setSpeed(h.getLevel(), 1));
        java.util.List<String> seen = new java.util.ArrayList<>();
        h.onEachTick(() -> {
            String st = h.getBlockState(sun).hasProperty(com.gigongibus.hungrycows.salt.WickerBundleBlock.STAGE)
                    ? h.getBlockState(sun).getValue(com.gigongibus.hungrycows.salt.WickerBundleBlock.STAGE).getSerializedName() : "-";
            if (seen.isEmpty() || !seen.get(seen.size() - 1).equals(st)) seen.add(st);
        });
        h.succeedWhen(() -> {
            var sunState = h.getBlockState(sun);   // (після прибирання блока тут повітря — лише провал, без падіння сервера)
            h.assertTrue(sunState.hasProperty(com.gigongibus.hungrycows.salt.WickerBundleBlock.STAGE)
                    && sunState.getValue(com.gigongibus.hungrycows.salt.WickerBundleBlock.STAGE) == com.gigongibus.hungrycows.salt.WickerBundleBlock.Stage.DRY,
                    "на сонці ще не сухий: " + seen);
            com.gigongibus.hungrycows.WorldClock.setSpeed(h.getLevel(), 1);
            var shadeStage = h.getBlockState(shade).getValue(com.gigongibus.hungrycows.salt.WickerBundleBlock.STAGE);
            var drops = net.minecraft.world.level.block.Block.getDrops(h.getBlockState(sun), h.getLevel(), h.absolutePos(sun), h.getBlockEntity(sun));
            // рецепти
            var g = grid();
            for (int i = 0; i < 9; i++) g.setItem(i, new ItemStack(i == 4 ? Salt.KNITTING_NEEDLE.get() : Salt.WICKER_BUNDLE_DRY.get()));
            var r = h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, g, h.getLevel());
            var g2 = grid();
            for (int i = 0; i < 9; i++) g2.setItem(i, new ItemStack(i == 4 ? Items.COBWEB : Salt.WICKER.get()));
            var r2 = h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, g2, h.getLevel());
            var g3 = grid();
            g3.setItem(1, new ItemStack(Items.OAK_BUTTON)); g3.setItem(4, new ItemStack(Items.STICK)); g3.setItem(7, new ItemStack(Items.STICK));
            var r3 = h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, g3, h.getLevel());
            float compost = net.minecraft.world.level.block.ComposterBlock.COMPOSTABLES.getFloat(Salt.WICKER_BUNDLE_ROTTEN.get());
            LOG.info("[тест] bundleDriesInSun: на сонці {} за {} с (×1000), під дахом {}, випадає {}, мішковина {}, пучок {}, спиця {}, компостер {}",
                    seen, h.getTick() / 20.0, shadeStage, drops, r.isPresent(), r2.isPresent(), r3.isPresent(), compost);
            h.setBlock(sun, Blocks.AIR); h.setBlock(shade, Blocks.AIR); h.setBlock(shade.above(3), Blocks.AIR);
            h.assertTrue(seen.contains("wilted"), "не був підсохлим: " + seen);
            h.assertTrue(shadeStage == com.gigongibus.hungrycows.salt.WickerBundleBlock.Stage.FRESH, "під дахом сохне: " + shadeStage);
            h.assertTrue(drops.size() == 1 && drops.get(0).is(Salt.WICKER_BUNDLE_DRY.get()), "сухий пучок не випадає сухим: " + drops);
            h.assertTrue(r.isPresent() && r.get().assemble(g, h.getLevel().registryAccess()).is(Salt.BURLAP.get())
                    && r.get().getRemainingItems(g).get(4).getDamageValue() == 1, "мішковина: рецепт або спиця не зношується");
            h.assertTrue(r2.isPresent() && r2.get().assemble(g2, h.getLevel().registryAccess()).is(Salt.WICKER_BUNDLE_ITEM.get()), "рецепт пучка");
            h.assertTrue(r3.isPresent() && r3.get().assemble(g3, h.getLevel().registryAccess()).is(Salt.KNITTING_NEEDLE.get()), "рецепт спиці");
            h.assertTrue(Math.abs(compost - 0.65F) < 0.001F, "гнилий не в компостер: " + compost);
        });
    }

    // 9. Дощ: пучок мокне, ще доба мокрим — гниє; дощ «скінчився» раніше (переніс під дах) — свіжий, сохне з початку
    @GameTest(template = "arena", batch = "salt_rain", timeoutTicks = 200)
    public static void bundleRotsInRain(GameTestHelper h) {
        h.getLevel().setDayTime(1000);
        TestWorld.calm(h);
        h.getLevel().setWeatherParameters(0, 24000, true, false);
        BlockPos a = new BlockPos(4, 2, 4), b = new BlockPos(10, 2, 4);
        h.setBlock(a, Salt.WICKER_BUNDLE.get());
        h.setBlock(b, Salt.WICKER_BUNDLE.get().defaultBlockState().setValue(com.gigongibus.hungrycows.salt.WickerBundleBlock.STAGE,
                com.gigongibus.hungrycows.salt.WickerBundleBlock.Stage.DRY));
        com.gigongibus.hungrycows.WorldClock.setSpeed(h.getLevel(), 500);   // за одну перевірку (1 с) — 10000 тіків: мокне, але ще не гниє
        java.util.List<String> sa = new java.util.ArrayList<>(), sb = new java.util.ArrayList<>();
        h.onEachTick(() -> {
            for (var pair : java.util.List.of(java.util.Map.entry(a, sa), java.util.Map.entry(b, sb))) {
                var st = h.getBlockState(pair.getKey());
                if (!st.hasProperty(com.gigongibus.hungrycows.salt.WickerBundleBlock.STAGE)) continue;
                String n = st.getValue(com.gigongibus.hungrycows.salt.WickerBundleBlock.STAGE).getSerializedName();
                if (pair.getValue().isEmpty() || !pair.getValue().get(pair.getValue().size() - 1).equals(n)) pair.getValue().add(n);
            }
        });
        // другий змок — дах над ним (дощ для нього «скінчився»)
        h.runAfterDelay(45, () -> h.setBlock(b.above(3), Blocks.STONE));
        h.runAfterDelay(199, () -> { com.gigongibus.hungrycows.WorldClock.setSpeed(h.getLevel(), 1); h.getLevel().setWeatherParameters(24000, 0, false, false); });
        h.succeedWhen(() -> {
            h.assertTrue(sa.contains("rotten") && sb.size() >= 3, "ще не: " + sa + " / " + sb);
            com.gigongibus.hungrycows.WorldClock.setSpeed(h.getLevel(), 1);
            h.getLevel().setWeatherParameters(24000, 0, false, false);
            int sunB = h.getBlockEntity(b) instanceof com.gigongibus.hungrycows.salt.WickerBundleBlockEntity e ? e.sun() : -1;
            LOG.info("[тест] bundleRotsInRain: під дощем {}, переніс під дах {} (сонця {})", sa, sb, sunB);
            boolean okA = sa.equals(java.util.List.of("fresh", "wet", "rotten")), okB = sb.equals(java.util.List.of("dry", "wet", "fresh")) && sunB == 0;
            h.setBlock(a, Blocks.AIR); h.setBlock(b, Blocks.AIR); h.setBlock(b.above(3), Blocks.AIR);
            if (!okA) h.fail("під дощем не свіжий → мокрий → гнилий: " + sa);
            if (!okB) h.fail("дощ скінчився — не «з початку»: " + sb + ", сонця " + sunB);
            h.succeed();
        });
    }

    // ---------- крок 6: корзина, блок солі ----------
    // 10. Корзина висить під каменем, під нею повітря: 9 прожареної → доба (×1000) → блок солі; друга — під нею камінь — ні;
    //     без опори зверху не ставиться; блок солі → 9 чистих купок
    @GameTest(template = "arena", batch = "salt_tub", timeoutTicks = 200)
    public static void basketMakesSaltBlock(GameTestHelper h) {
        BlockPos b1 = new BlockPos(4, 4, 4), b2 = new BlockPos(10, 4, 4);
        for (BlockPos b : new BlockPos[]{ b1, b2 }) { h.setBlock(b.above(), Blocks.CHAIN); h.setBlock(b.above(2), Blocks.STONE); }
        h.setBlock(b2.below(), Blocks.STONE);
        h.setBlock(b1, Salt.SALT_BASKET.get());
        h.setBlock(b2, Salt.SALT_BASKET.get());
        // під каменем (не ланцюгом) у повітрі — не ставиться (Fij: «обов'язково ланцюг»); на землі — ставиться
        h.setBlock(new BlockPos(7, 5, 10), Blocks.STONE);
        boolean floating = Salt.SALT_BASKET.get().defaultBlockState().canSurvive(h.getLevel(), h.absolutePos(new BlockPos(7, 4, 10)));
        boolean onGround = Salt.SALT_BASKET.get().defaultBlockState().canSurvive(h.getLevel(), h.absolutePos(new BlockPos(7, 2, 12)));
        h.setBlock(new BlockPos(7, 5, 10), Blocks.AIR);
        var p = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        for (BlockPos b : new BlockPos[]{ b1, b2 }) use(h, b, p, new ItemStack(Salt.GROUND_SALT_ROASTED.get(), 12), false);
        int left = p.getMainHandItem().getCount();
        com.gigongibus.hungrycows.WorldClock.setSpeed(h.getLevel(), com.gigongibus.hungrycows.WorldClock.SPEED_MAX);
        h.runAfterDelay(199, () -> com.gigongibus.hungrycows.WorldClock.setSpeed(h.getLevel(), 1));
        h.succeedWhen(() -> {
            var be1 = (com.gigongibus.hungrycows.salt.SaltBasketBlockEntity) h.getBlockEntity(b1);
            h.assertTrue(be1 != null && be1.done(), "блок солі ще не готовий");
            com.gigongibus.hungrycows.WorldClock.setSpeed(h.getLevel(), 1);
            var be2 = (com.gigongibus.hungrycows.salt.SaltBasketBlockEntity) h.getBlockEntity(b2);
            int p2 = be2.progress();
            boolean done2 = be2.done();
            p.getInventory().clearContent();
            use(h, b1, p, ItemStack.EMPTY, false);
            boolean gotBlock = p.getInventory().countItem(Salt.SALT_BLOCK_ITEM.get()) == 1;
            p.getInventory().clearContent();
            var g = grid();
            g.setItem(4, new ItemStack(Salt.SALT_BLOCK_ITEM.get()));
            var r = h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, g, h.getLevel());
            ItemStack piles = r.map(x -> x.assemble(g, h.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
            boolean lick = Salt.SALT_BLOCK.get().defaultBlockState().is(Salt.SALT_LICKS) && com.gigongibus.hungrycows.ModBlocks.SALT_LICK.get().defaultBlockState().is(Salt.SALT_LICKS);
            LOG.info("[тест] basketMakesSaltBlock: блок солі за {} с (×1000), у руці лишилось {} з 12, друга (камінь знизу) прогрес {} готова {}, під каменем висить {}, на землю {}, "
                    + "забрала блок {}, блок → {}, лизунці {}", h.getTick() / 20.0, left, p2, done2, floating, onGround, gotBlock, piles, lick);
            for (BlockPos b : new BlockPos[]{ b1, b2 }) { h.setBlock(b, Blocks.AIR); h.setBlock(b.above(), Blocks.AIR); h.setBlock(b.above(2), Blocks.AIR); }
            h.setBlock(b2.below(), Blocks.AIR);
            if (left != 3) h.fail("поклала не 9 з 12: лишилось " + left);
            if (p2 != 0 || done2) h.fail("з каменем знизу теж іде");
            if (floating) h.fail("під каменем без ланцюга висить");
            if (!onGround) h.fail("на землю не ставиться");
            if (!gotBlock) h.fail("не забрала блок солі");
            if (!piles.is(Salt.CLEAN_SALT.get()) || piles.getCount() != 9) h.fail("блок не дає 9 чистих купок: " + piles);
            if (!lick) h.fail("блок солі не в тезі лизунців");
            h.succeed();
        });
    }

    // 11. Корова з бажанням солі 80 лиже новий блок солі
    @GameTest(template = "arena", batch = "salt_lick", timeoutTicks = 800)
    public static void cowLicksSaltBlock(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        com.gigongibus.hungrycows.HungryCow c = TestWorld.cow(h, new BlockPos(3, 2, 7));
        c.setAge(0);
        c.taste.salt = 80;
        BlockPos lick = new BlockPos(10, 2, 7);
        h.setBlock(lick, Salt.SALT_BLOCK.get());
        boolean[] cleared = { false };
        Runnable clear = () -> { cleared[0] = true; c.discard(); h.setBlock(lick, Blocks.AIR); };
        h.onEachTick(() -> c.hunger = 0);
        h.runAfterDelay(799, clear);
        h.succeedWhen(() -> {
            h.assertTrue(!cleared[0], "час вийшов (сіль " + Math.round(c.taste.salt) + ")");
            h.assertTrue(c.taste.salt < 40, "ще не лизала (сіль " + Math.round(c.taste.salt) + ")");
            LOG.info("[тест] cowLicksSaltBlock: полизала блок солі на {} с, бажання солі 80 -> {}", h.getTick() / 20.0, Math.round(c.taste.salt));
            clear.run();
        });
    }
}
