package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.CowHayBlock;
import com.gigongibus.hungrycows.CowHayBlock.Cond;
import com.gigongibus.hungrycows.CowHayBlock.Stage;
import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModBlocks;
import com.gigongibus.hungrycows.ModEffects;
import com.gigongibus.hungrycows.ModEntities;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Сіно мода (план Fij «блоки сіна, плісняве сіно, гриби»). Логіку блока перевіряє напряму (випадковий тік — `age` з
 * «кубиком», що завжди випадає), бо справжні випадкові тіки бувають раз на ~68 с. Ігрова логіка не змінюється.
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowHayTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    private static BlockState hay(Stage st, Cond c, boolean bound) {
        return ModBlocks.HAY.get().defaultBlockState().setValue(CowHayBlock.STAGE, st).setValue(CowHayBlock.COND, c).setValue(CowHayBlock.BOUND, bound);
    }

    /** «Кубик», що завжди випадає (усе, що може статись, стається) */
    private static RandomSource always() {
        return new net.minecraft.world.level.levelgen.LegacyRandomSource(1) {
            @Override public double nextDouble() { return 0.0; }
            @Override public boolean nextBoolean() { return false; }
        };
    }

    // 1. Свіже незв'язане окреме розпадається на 9 пучків свіжої трави; у купі з 4 — ні; нитка зв'язує
    @GameTest(template = "platform", batch = "hay", timeoutTicks = 20)
    public static void looseHayDecaysPileAndCobweb(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos lone = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(lone, hay(Stage.FRESH, Cond.CLEAN, false));
        boolean scheduled = level.getBlockTicks().hasScheduledTick(lone, ModBlocks.HAY.get());
        boolean decayed = CowHayBlock.decayIfLoose(level.getBlockState(lone), level, lone);
        int wheat = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(lone).inflate(2))) if (e.getItem().is(ModBlocks.HAY_BUNDLE.get())) wheat += e.getItem().getCount();
        // купа 2×2
        BlockPos p0 = h.absolutePos(new BlockPos(5, 2, 5));
        for (BlockPos p : new BlockPos[] { p0, p0.east(), p0.south(), p0.east().south() }) level.setBlockAndUpdate(p, hay(Stage.FRESH, Cond.CLEAN, false));
        boolean pileDecayed = CowHayBlock.decayIfLoose(level.getBlockState(p0), level, p0);
        // павутина
        var player = FakePlayerFactory.getMinecraft(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STRING));   // нитка (Fij)
        BlockPos b = h.absolutePos(new BlockPos(2, 2, 6));
        level.setBlockAndUpdate(b, hay(Stage.FRESH, Cond.CLEAN, false));
        level.getBlockState(b).use(level, player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(b), Direction.UP, b, false));
        boolean bound = level.getBlockState(b).getValue(CowHayBlock.BOUND);
        boolean boundDecayed = CowHayBlock.decayIfLoose(level.getBlockState(b), level, b);
        LOG.info("[тест] looseHayDecaysPileAndCobweb: відлік {}, окреме розпалось {} ({} пучків), купа розпалась {}, нитка зв'язала {}, зв'язане розпалось {}",
                scheduled, decayed, wheat, pileDecayed, bound, boundDecayed);
        level.getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(12)).forEach(ItemEntity::discard);
        h.assertTrue(scheduled && decayed && wheat == 9, "окреме свіже незв'язане не розпалось на 9 пучків свіжої трави");
        // вигляд предмета: різні стани — різні номери (інакше всі предмети однакові)
        int v1 = CowHayBlock.variant(CowHayBlock.stackOf(hay(Stage.FRESH, Cond.CLEAN, false)));
        int v2 = CowHayBlock.variant(CowHayBlock.stackOf(hay(Stage.OVERDRIED, Cond.CLEAN, true)));
        h.assertTrue(v1 == 0 && v2 == (3 * 11) * 2 + 1, "номер вигляду предмета неправильний: " + v1 + " / " + v2);
        h.assertFalse(pileDecayed, "купа з 4 розпалась");
        h.assertTrue(bound && !boundDecayed, "нитка не зв'язала / зв'язане розпалось");
        h.succeed();
    }

    // 2. Стадії, гриби, бруд від корів, пліснява, ламання
    @GameTest(template = "platform", batch = "hay", timeoutTicks = 20)
    public static void hayStatesAndDrops(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos p = h.absolutePos(new BlockPos(4, 3, 4));   // у повітрі: сусіди знизу — не підлога майданчика
        RandomSource r = always();
        // гниле свіже → зірочник, гниле сухе → вогняний гриб (правило Fij)
        Cond star = CowHayBlock.age(hay(Stage.FRESH, Cond.ROTTEN, true), level, p, r).getValue(CowHayBlock.COND);
        Cond fire = CowHayBlock.age(hay(Stage.DRY, Cond.ROTTEN, true), level, p, r).getValue(CowHayBlock.COND);
        // бруд від корів: 2 хв стояння → трохи брудне
        level.setBlockAndUpdate(p, hay(Stage.DRY, Cond.CLEAN, true));
        for (int i = 0; i < CowHayBlock.COW_DIRT_SECONDS; i++) CowHayBlock.trample(level, p, 1);
        Cond trampled = level.getBlockState(p).getValue(CowHayBlock.COND);
        // пліснява заражає мокрого сусіда (кубик випадає, напрям — увесь сусідній ряд мокрий)
        level.setBlockAndUpdate(p, hay(Stage.DRY, Cond.MOLD, true));
        for (Direction d : Direction.values()) level.setBlockAndUpdate(p.relative(d), hay(Stage.DRY, Cond.WET, true));
        CowHayBlock.spreadMold(level, p, r);
        int infected = 0;
        for (Direction d : Direction.values()) if (level.getBlockState(p.relative(d)).getValue(CowHayBlock.COND).moldy()) infected++;
        // ламання: брудне — земля + 2–3 пшениці, гниле — земля, чисте — сам блок
        List<ItemStack> dirty = Block.getDrops(hay(Stage.DRY, Cond.VERY_DIRTY, true), level, p, null);
        List<ItemStack> rotten = Block.getDrops(hay(Stage.DRY, Cond.ROTTEN, true), level, p, null);
        List<ItemStack> clean = Block.getDrops(hay(Stage.OVERDRIED, Cond.CLEAN, true), level, p, null);
        LOG.info("[тест] hayStatesAndDrops: {} / {}, після корів {}, заражено {}, брудне {} гниле {} чисте {} ({})", star, fire, trampled, infected,
                dirty, rotten, clean, clean.isEmpty() ? "" : clean.get(0).getHoverName().getString());
        for (Direction d : Direction.values()) level.setBlockAndUpdate(p.relative(d), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        level.getEntitiesOfClass(ItemEntity.class, new AABB(p).inflate(4)).forEach(ItemEntity::discard);
        h.assertTrue(star == Cond.STAR_FUNGUS && fire == Cond.FIRE_FUNGUS, "гриби ростуть не там");
        h.assertTrue(trampled == Cond.SLIGHTLY_DIRTY, "корови не забруднили сіно");
        h.assertTrue(infected == 1, "пліснява заразила не одного сусіда, а " + infected);
        h.assertTrue(dirty.size() == 2 && dirty.get(0).is(Items.DIRT) && dirty.get(1).is(Items.WHEAT) && dirty.get(1).getCount() >= 2, "брудне дає не землю й пшеницю");
        h.assertTrue(rotten.size() == 1 && rotten.get(0).is(Items.DIRT), "гниле дає не землю");
        h.assertTrue(clean.size() == 1 && clean.get(0).is(ModBlocks.HAY_ITEM.get())
                && "overdried".equals(clean.get(0).getTag().getCompound("BlockStateTag").getString("stage")), "чисте не дає сам блок з тією самою стадією");
        h.succeed();
    }

    // 3. Поживність і ефекти (таблиця Fij)
    @GameTest(template = "platform", batch = "hay", timeoutTicks = 20)
    public static void hayNutritionAndEffects(GameTestHelper h) {
        int fresh = CowHayBlock.nutrition(hay(Stage.FRESH, Cond.CLEAN, true));
        int apple = CowHayBlock.nutrition(hay(Stage.FRESH, Cond.APPLE, true));
        int wilted = CowHayBlock.nutrition(hay(Stage.WILTED, Cond.CLEAN, true));
        int dryDirty = CowHayBlock.nutrition(hay(Stage.DRY, Cond.VERY_DIRTY, true));
        int over = CowHayBlock.nutrition(hay(Stage.OVERDRIED, Cond.CLEAN, true));
        int rotten = CowHayBlock.nutrition(hay(Stage.DRY, Cond.ROTTEN, true));
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(4, 2, 4));
        cow.setAge(0);
        cow.setNoAi(true);
        cow.hunger = 50;
        cow.eatHay(hay(Stage.FRESH, Cond.VERY_DIRTY, true));
        boolean hungerFx = cow.hasEffect(MobEffects.HUNGER);
        cow.eatHay(hay(Stage.FRESH, Cond.MOLD, true));
        boolean poison = cow.hasEffect(MobEffects.POISON);
        cow.eatHay(hay(Stage.DRY, Cond.FIRE_FUNGUS, true));
        boolean rage = cow.hasEffect(ModEffects.MUSHROOM_RAGE.get()) && cow.rageCenter != null && cow.hasEffect(MobEffects.WITHER);
        LOG.info("[тест] hayNutritionAndEffects: свіже {}, з яблуком {}, підсохле {}, сухе дуже брудне {}, пересохле {}, гниле {}; голод {}, отрута {}, лють {}",
                fresh, apple, wilted, dryDirty, over, rotten, hungerFx, poison, rage);
        cow.discard();
        h.assertTrue(fresh == 30 && apple == 35 && wilted == 25 && dryDirty == 5 && over == 5 && rotten == 5, "поживність не за таблицею");
        h.assertTrue(hungerFx && poison && rage, "ефекти сіна не ті");
        h.succeed();
    }

    // 4. Корова їсть чисте сіно, а пліснявого не чіпає (не голодує)
    private static final int AVOID_TIMEOUT = 600;

    @GameTest(template = "arena", batch = "hay_eat", timeoutTicks = AVOID_TIMEOUT)
    public static void cowEatsCleanAvoidsMold(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        BlockPos mold = new BlockPos(6, 2, 7), clean = new BlockPos(10, 2, 7);
        h.setBlock(mold, hay(Stage.DRY, Cond.MOLD, true));
        h.setBlock(clean, hay(Stage.DRY, Cond.CLEAN, true));
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(4, 2, 7));
        cow.setAge(0);
        cow.setYRot(-90);
        cow.setYHeadRot(-90);
        cow.hunger = 35;
        boolean[] cleared = { false };
        Runnable clear = () -> { cleared[0] = true; cow.discard(); h.setBlock(mold, net.minecraft.world.level.block.Blocks.AIR); h.setBlock(clean, net.minecraft.world.level.block.Blocks.AIR); };
        h.runAfterDelay(AVOID_TIMEOUT - 1, clear);
        h.succeedWhen(() -> {
            h.assertFalse(cleared[0], "за 30 с не з'їла чисте сіно (голод " + cow.hunger + ")");
            h.assertTrue(h.getBlockState(clean).isAir(), "ще не з'їла чисте");
            boolean moldLeft = h.getBlockState(mold).getBlock() instanceof CowHayBlock;
            LOG.info("[тест] cowEatsCleanAvoidsMold: з'їла чисте за {} с, плісняве лишилось {}", h.getTick() / 20.0, moldLeft);
            h.assertTrue(moldLeft, "з'їла плісняве, хоча не голодує");
            clear.run();
        });
    }

    // 5. Грибна лють: б'є свиню поруч, теля не чіпає
    private static final int RAGE_TIMEOUT = 300;

    @GameTest(template = "arena", batch = "hay_rage", timeoutTicks = RAGE_TIMEOUT)
    public static void mushroomRageHitsButSparesCalf(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(7, 2, 7));
        cow.setAge(0);
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(9, 2, 7));
        pig.setNoAi(true);
        HungryCow calf = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(5, 2, 7));
        calf.setAge(-24000);
        calf.setNoAi(true);
        float pigHp = pig.getHealth(), calfHp = calf.getHealth();
        cow.eatHay(hay(Stage.DRY, Cond.FIRE_FUNGUS, true));
        cow.removeEffect(MobEffects.WITHER);
        boolean[] cleared = { false };
        Runnable clear = () -> { cleared[0] = true; cow.discard(); pig.discard(); calf.discard(); };
        h.runAfterDelay(RAGE_TIMEOUT - 1, clear);
        h.succeedWhen(() -> {
            h.assertFalse(cleared[0], "за 15 с у люті не вдарила свиню");
            h.assertTrue(pig.getHealth() < pigHp, "ще не вдарила свиню");
            LOG.info("[тест] mushroomRageHitsButSparesCalf: свиня {} -> {} за {} с, теля {} -> {}", pigHp, pig.getHealth(), h.getTick() / 20.0, calfHp, calf.getHealth());
            h.assertTrue(calf.getHealth() >= calfHp, "у люті вдарила теля");
            h.assertTrue(pig.isAlive(), "лють добила свиню (має бути не смертельна)");
            clear.run();
        });
    }

    // 6. Трава мотикою чи ножицями — пучок свіжої трави; присівши з ножицями — звичайне ламання. Пучок з рук −10, пшениця −5
    @GameTest(template = "platform", batch = "hay", timeoutTicks = 20)
    public static void grassBundlesAndFeeding(GameTestHelper h) {
        var level = h.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        BlockPos g1 = h.absolutePos(new BlockPos(2, 2, 2)), g2 = h.absolutePos(new BlockPos(4, 2, 2));
        level.setBlockAndUpdate(g1.below(), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(g2.below(), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(g1, net.minecraft.world.level.block.Blocks.GRASS.defaultBlockState());
        level.setBlockAndUpdate(g2, net.minecraft.world.level.block.Blocks.GRASS.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
        player.setShiftKeyDown(false);
        boolean hoe = com.gigongibus.hungrycows.GrassHarvest.harvest(level, g1, level.getBlockState(g1), player);
        int bundles = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(g1).inflate(2))) if (e.getItem().is(ModBlocks.HAY_BUNDLE.get())) bundles += e.getItem().getCount();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
        player.setShiftKeyDown(true);
        boolean sneakShears = com.gigongibus.hungrycows.GrassHarvest.harvest(level, g2, level.getBlockState(g2), player);
        player.setShiftKeyDown(false);
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(6, 2, 6));
        cow.setAge(0);
        cow.setNoAi(true);
        cow.hunger = 50;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.HAY_BUNDLE.get()));
        cow.mobInteract(player, InteractionHand.MAIN_HAND);
        int afterBundle = cow.hunger;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT));
        cow.mobInteract(player, InteractionHand.MAIN_HAND);
        int afterWheat = cow.hunger;
        LOG.info("[тест] grassBundlesAndFeeding: мотика {} ({} пучків), присівши з ножицями {}, голод 50 -> {} (пучок) -> {} (пшениця)",
                hoe, bundles, sneakShears, afterBundle, afterWheat);
        cow.discard();
        level.getEntitiesOfClass(ItemEntity.class, new AABB(g1).inflate(6)).forEach(ItemEntity::discard);
        level.setBlockAndUpdate(g2, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(g1.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(g2.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        h.assertTrue(hoe && bundles == 1 && level.getBlockState(g1).isAir(), "мотика не дала пучок свіжої трави");
        h.assertFalse(sneakShears, "присівши з ножицями — має ламатись як звичайно");
        h.assertTrue(afterBundle == 40 && afterWheat == 35, "пучок −10 / пшениця −5 не так: " + afterBundle + " / " + afterWheat);
        h.succeed();
    }
}
