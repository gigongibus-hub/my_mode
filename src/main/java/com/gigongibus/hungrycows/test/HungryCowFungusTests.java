package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.CowHayBlock;
import com.gigongibus.hungrycows.CowHayBlock.Cond;
import com.gigongibus.hungrycows.CowHayBlock.Stage;
import com.gigongibus.hungrycows.FungusEffects;
import com.gigongibus.hungrycows.FungusPatches;
import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModBlocks;
import com.gigongibus.hungrycows.ModEffects;
import com.gigongibus.hungrycows.ModItems;
import com.gigongibus.hungrycows.ModPotions;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** Гриби з сіна й зілля з них (Fij, 14.11) */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowFungusTests {
    private static final Logger LOG = LogUtils.getLogger();

    private static BlockState hay(Stage st, Cond c) {
        return ModBlocks.HAY.get().defaultBlockState().setValue(CowHayBlock.STAGE, st).setValue(CowHayBlock.COND, c).setValue(CowHayBlock.BOUND, true);
    }

    private static int dropped(GameTestHelper h, BlockPos p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p).inflate(2))) if (e.getItem().is(item)) n += e.getItem().getCount();
        return n;
    }

    // 1. ПКМ по сіну з грибом: 3 гриби, потім сіно гниле; відростає лише з вологою (вогнегриб — 1 вода, 3 доби; зірочник — 2, 4 доби)
    @GameTest(template = "platform", batch = "fungus", timeoutTicks = 20)
    public static void pickThreeAndRegrow(GameTestHelper h) {
        var level = h.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos p = h.absolutePos(new BlockPos(2, 2, 2)), s = h.absolutePos(new BlockPos(6, 2, 6));
        level.setBlockAndUpdate(p, hay(Stage.DRY, Cond.FIRE_FUNGUS));
        Cond[] after = new Cond[3];
        for (int i = 0; i < 3; i++) {
            level.getBlockState(p).use(level, player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false));
            after[i] = level.getBlockState(p).getValue(CowHayBlock.COND);
        }
        int fungi = dropped(h, p, ModItems.FIRE_FUNGUS.get());
        int day = FungusPatches.SECONDS_PER_DAY;
        boolean dryGrew = FungusPatches.tick(level, p, level.getBlockState(p), FungusPatches.FIRE_DAYS * day);
        level.setBlockAndUpdate(p.east(2), Blocks.WATER.defaultBlockState());   // вода за 2 бл.
        boolean early = FungusPatches.tick(level, p, level.getBlockState(p), FungusPatches.FIRE_DAYS * day - 10);
        boolean grew = FungusPatches.tick(level, p, level.getBlockState(p), 10);
        Cond back = level.getBlockState(p).getValue(CowHayBlock.COND);
        // зірочник: однієї води мало, двох досить
        level.setBlockAndUpdate(s, hay(Stage.FRESH, Cond.STAR_FUNGUS));
        for (int i = 0; i < 3; i++) level.getBlockState(s).use(level, player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(s), Direction.UP, s, false));
        level.setBlockAndUpdate(s.west(2), Blocks.WATER.defaultBlockState());
        boolean starOne = FungusPatches.tick(level, s, level.getBlockState(s), FungusPatches.STAR_DAYS * day);
        level.setBlockAndUpdate(s.north(2), Blocks.WATER.defaultBlockState());
        boolean starTwo = FungusPatches.tick(level, s, level.getBlockState(s), FungusPatches.STAR_DAYS * day);
        int stars = dropped(h, s, ModItems.STAR_FUNGUS.get());
        LOG.info("[тест] pickThreeAndRegrow: після зборів {}, вогнегрибів {}, без води {}, рано {}, відріс {} ({}); зірочників {}, 1 вода {}, 2 води {}",
                java.util.Arrays.toString(after), fungi, dryGrew, early, grew, back, stars, starOne, starTwo);
        for (BlockPos b : new BlockPos[] { p, p.east(2), s, s.west(2), s.north(2) }) level.setBlockAndUpdate(b, Blocks.AIR.defaultBlockState());
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(p).inflate(8))) e.discard();
        h.assertTrue(after[0] == Cond.FIRE_FUNGUS && after[1] == Cond.FIRE_FUNGUS && after[2] == Cond.ROTTEN, "збір: " + java.util.Arrays.toString(after));
        h.assertTrue(fungi == 3 && stars == 3, "грибів " + fungi + " / " + stars);
        h.assertTrue(!dryGrew && !early && grew && back == Cond.FIRE_FUNGUS, "вогнегриб відростає неправильно");
        h.assertTrue(!starOne && starTwo, "зірочнику потрібно 2 води");
        h.succeed();
    }

    // 2. Варіння: незграбне + вогнегриб → I; + редстоун → II; порох/дихання дракона — правильна тривалість; стріли — 1 хв
    @GameTest(template = "platform", batch = "fungus", timeoutTicks = 20)
    public static void brewingChain(GameTestHelper h) {
        ItemStack awkward = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.AWKWARD);
        ItemStack one = BrewingRecipeRegistry.getOutput(awkward, new ItemStack(ModItems.FIRE_FUNGUS.get()));
        ItemStack two = BrewingRecipeRegistry.getOutput(one, new ItemStack(Items.REDSTONE));
        ItemStack splash2 = BrewingRecipeRegistry.getOutput(two, new ItemStack(Items.GUNPOWDER));
        ModPotions.fix(splash2);
        ItemStack splash3 = BrewingRecipeRegistry.getOutput(splash2, new ItemStack(Items.GLOWSTONE_DUST));
        ItemStack lingering3 = BrewingRecipeRegistry.getOutput(splash3, new ItemStack(Items.DRAGON_BREATH));
        ModPotions.fix(lingering3);
        ItemStack arrow = PotionUtils.setPotion(new ItemStack(Items.TIPPED_ARROW), PotionUtils.getPotion(lingering3));
        ModPotions.fix(arrow);
        ItemStack starSplash2 = PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), PotionUtils.getPotion(
                BrewingRecipeRegistry.getOutput(BrewingRecipeRegistry.getOutput(awkward, new ItemStack(ModItems.STAR_FUNGUS.get())), new ItemStack(Items.REDSTONE))));
        ModPotions.fix(starSplash2);
        int s1 = sec(one), s2 = sec(two), sp2 = sec(splash2), sp3 = sec(splash3), l3 = sec(lingering3) / 4, a = sec(arrow) / 8, st = sec(starSplash2);
        LOG.info("[тест] brewingChain: I {} с, II {} с, вибухове II {} с, III {} с, осідаюче III {} с (у хмарі), стріла {} с; зірочник вибухове II {} с",
                s1, s2, sp2, sp3, l3, a, st);
        h.assertTrue(s1 == 45 && s2 == 105 && sp2 == 105 && sp3 == 180 && l3 == 30 && a == 60, "тривалості вогнегриба не ті");
        h.assertTrue(st == 60, "вибухове зілля зірочника II не 60 с: " + st);
        h.succeed();
    }

    private static int sec(ItemStack s) {
        var eff = PotionUtils.getPotion(s).getEffects();
        return eff.isEmpty() ? -1 : eff.get(0).getDuration() / 20;
    }

    // 3. Грибогоріння: зомбі б'є іншого зомбі («навіть своїх»)
    @GameTest(template = "arena", batch = "fungus_mobs", timeoutTicks = 200)
    public static void burnZombieHitsZombie(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        var a = h.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 7));
        var b = h.spawn(EntityType.ZOMBIE, new BlockPos(9, 2, 7));
        for (var z : java.util.List.of(a, b)) z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        a.addEffect(new MobEffectInstance(ModEffects.FUNGUS_BURN.get(), 600));
        float hp = b.getHealth();
        boolean[] cleared = { false };
        Runnable clear = () -> { cleared[0] = true; a.discard(); b.discard(); };
        h.runAfterDelay(199, clear);
        h.succeedWhen(() -> {
            h.assertFalse(cleared[0], "за 10 с зомбі з грибогорінням не вдарив іншого");
            h.assertTrue(b.getHealth() < hp, "ще не вдарив");
            LOG.info("[тест] burnZombieHitsZombie: за {} с, здоров'я {} → {}", h.getTick() / 20.0, hp, b.getHealth());
            clear.run();
        });
    }

    // 4. Грибне сяйво: свиня (мирна) й зомбі йдуть на курку під сяйвом і б'ють; залізний голем — ні
    @GameTest(template = "arena", batch = "fungus_mobs", timeoutTicks = 300)
    public static void glowLuresMobsNotGolem(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        var chicken = h.spawn(EntityType.CHICKEN, new BlockPos(7, 2, 7));
        chicken.setNoAi(true);
        var pig = h.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        var golem = h.spawn(EntityType.IRON_GOLEM, new BlockPos(12, 2, 12));
        chicken.addEffect(new MobEffectInstance(ModEffects.FUNGUS_GLOW.get(), 600));
        float hp = chicken.getHealth();
        boolean[] cleared = { false };
        Runnable clear = () -> { cleared[0] = true; chicken.discard(); pig.discard(); golem.discard(); };
        h.runAfterDelay(299, clear);
        h.succeedWhen(() -> {
            h.assertFalse(cleared[0], "за 15 с свиня не вдарила курку під сяйвом");
            h.assertTrue(chicken.getHealth() < hp, "ще не б'ють");
            LOG.info("[тест] glowLuresMobsNotGolem: за {} с, курка {} → {}, голем змушений {}", h.getTick() / 20.0, hp, chicken.getHealth(),
                    FungusEffects.isForcedOn(golem, chicken));
            h.assertFalse(FungusEffects.isForcedOn(golem, chicken), "голем реагує на сяйво");
            clear.run();
        });
    }

    // 5. Сяйво: злі помічають удвічі далі (зомбі за 25 бл.), мирні — лише з 15 (свиня за 20 бл. — ні)
    @GameTest(template = "arena", batch = "fungus_mobs", timeoutTicks = 60)
    public static void glowHostilesFromFarther(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        var chicken = h.spawn(EntityType.CHICKEN, new BlockPos(7, 2, 7));
        var zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(7, 2, 32));
        var pig = h.spawn(EntityType.PIG, new BlockPos(27, 2, 7));
        for (var m : java.util.List.of(chicken, zombie, pig)) m.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        chicken.addEffect(new MobEffectInstance(ModEffects.FUNGUS_GLOW.get(), 600));
        h.runAtTickTime(45, () -> {
            boolean z = FungusEffects.isForcedOn(zombie, chicken), p = FungusEffects.isForcedOn(pig, chicken);
            LOG.info("[тест] glowHostilesFromFarther: зомбі за {} бл. — {}, свиня за {} бл. — {}", zombie.distanceTo(chicken), z, pig.distanceTo(chicken), p);
            chicken.discard(); zombie.discard(); pig.discard();
            h.assertTrue(z && !p, "зомбі здалеку " + z + ", свиня здалеку " + p);
            h.succeed();
        });
    }
}
