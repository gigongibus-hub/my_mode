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
}
