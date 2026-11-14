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

    // 4б. Вогняний гриб пахне приємно й смачно (Fij): сита корова (не голодує) іде до нього повз чисте сіно
    @GameTest(template = "arena", batch = "hay_eat", timeoutTicks = AVOID_TIMEOUT)
    public static void cowPrefersFireFungus(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        BlockPos clean = new BlockPos(6, 2, 7), fungus = new BlockPos(10, 2, 4);   // не за чистим (інакше не видно)
        h.setBlock(clean, hay(Stage.DRY, Cond.CLEAN, true));
        h.setBlock(fungus, hay(Stage.DRY, Cond.FIRE_FUNGUS, true));
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(4, 2, 7));
        cow.setAge(0);
        cow.setYRot(-90);
        cow.setYHeadRot(-90);
        cow.hunger = 35;
        boolean[] cleared = { false };
        Runnable clear = () -> { cleared[0] = true; cow.discard(); h.setBlock(clean, net.minecraft.world.level.block.Blocks.AIR); h.setBlock(fungus, net.minecraft.world.level.block.Blocks.AIR); };
        h.runAfterDelay(AVOID_TIMEOUT - 1, clear);
        h.succeedWhen(() -> {
            h.assertFalse(cleared[0], "за 30 с не з'їла сіно з вогняним грибом (голод " + cow.hunger + ")");
            boolean cleanLeft = h.getBlockState(clean).getBlock() instanceof CowHayBlock;
            h.assertTrue(cleanLeft || h.getBlockState(fungus).isAir(), "з'їла чисте замість гриба");
            h.assertTrue(h.getBlockState(fungus).isAir(), "ще не з'їла гриб");
            LOG.info("[тест] cowPrefersFireFungus: з'їла гриб за {} с, чисте лишилось {}, лють {}", h.getTick() / 20.0, cleanLeft,
                    cow.hasEffect(com.gigongibus.hungrycows.ModEffects.MUSHROOM_RAGE.get()));
            h.assertTrue(cleanLeft, "спершу з'їла чисте, а не гриб");
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

    // 5б. Лють (Fij): корова в люті тримається місця гриба, інші корови її бояться
    @GameTest(template = "arena", batch = "hay_rage", timeoutTicks = RAGE_TIMEOUT)
    public static void rageCowStaysAndIsFeared(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(4, 2, 4));
        cow.setAge(0);
        cow.hunger = 0;
        HungryCow other = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(7, 2, 4));
        other.setAge(0);
        other.hunger = 0;
        other.setYRot(90);
        other.setYHeadRot(90);
        cow.eatHay(hay(Stage.DRY, Cond.FIRE_FUNGUS, true));
        cow.removeEffect(MobEffects.WITHER);
        BlockPos center = cow.rageCenter;
        double[] maxAway = { 0 };
        int[] fear = { 0 };
        double[] apart = { 0 };
        boolean[] cleared = { false };
        Runnable clear = () -> { cleared[0] = true; cow.discard(); other.discard(); };
        h.runAfterDelay(RAGE_TIMEOUT - 1, clear);
        h.onEachTick(() -> {
            if (cleared[0]) return;
            maxAway[0] = Math.max(maxAway[0], Math.sqrt(cow.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(center))));
            fear[0] = Math.max(fear[0], other.fear);
            apart[0] = Math.max(apart[0], other.distanceTo(cow));
        });
        h.runAtTickTime(200, () -> {
            if (cleared[0]) return;
            LOG.info("[тест] rageCowStaysAndIsFeared: найдалі від місця {} бл., страх іншої {}, відбігла на {} бл.", maxAway[0], fear[0], apart[0]);
            h.assertTrue(apart[0] >= 8, "інша корова не відбігла (найдалі " + apart[0] + " бл.)");
            h.assertTrue(cow.raging(), "лють скінчилась");
            h.assertTrue(maxAway[0] <= HungryCow.RAGE_STAY + 2.5, "відійшла від місця гриба на " + maxAway[0]);
            h.assertTrue(fear[0] > 0, "інша корова не боїться корови в люті");
            clear.run();
            h.succeed();
        });
    }

    // 5г. Зірочник (Fij): світиться (4, потім +2) і приманює злих мобів (скелет без цілі за 12 бл. іде на неї)
    @GameTest(template = "arena", batch = "hay_rage", timeoutTicks = RAGE_TIMEOUT)
    public static void starFungusGlowsAndLures(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(2, 2, 2));
        cow.setAge(0);
        cow.setNoAi(true);
        cow.eatHay(hay(Stage.FRESH, Cond.STAR_FUNGUS, true));
        int glow1 = cow.starGlowLevel();
        cow.eatHay(hay(Stage.FRESH, Cond.STAR_FUNGUS, true));
        int glow2 = cow.starGlowLevel();
        cow.removeEffect(MobEffects.WITHER);
        cow.setHealth(cow.getMaxHealth());
        h.assertTrue(glow1 == HungryCow.STAR_GLOW_FIRST && glow2 == HungryCow.STAR_GLOW_FIRST + HungryCow.STAR_GLOW_STEP, "світіння " + glow1 + " / " + glow2);
        int[] lightSeen = { 0 };
        h.onEachTick(() -> {   // блок світла біля корови (світяться й блоки навколо)
            for (BlockPos p : BlockPos.betweenClosed(cow.blockPosition(), cow.blockPosition().above()))
                if (h.getLevel().getBlockState(p).is(net.minecraft.world.level.block.Blocks.LIGHT))
                    lightSeen[0] = Math.max(lightSeen[0], h.getLevel().getBlockState(p).getValue(net.minecraft.world.level.block.LightBlock.LEVEL));
        });
        cow.starEaten = 9;   // шанс приманити 100% (2 рази — 30% на 5 с, тест був би випадковим)
        var sk = h.spawn(EntityType.SKELETON, new BlockPos(12, 2, 12));
        sk.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));   // не горить на сонці
        sk.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        boolean[] cleared = { false };
        Runnable clear = () -> {
            cleared[0] = true; cow.discard(); sk.discard();
            h.assertFalse(h.getLevel().getBlockState(cow.blockPosition()).is(net.minecraft.world.level.block.Blocks.LIGHT)
                    || h.getLevel().getBlockState(cow.blockPosition().above()).is(net.minecraft.world.level.block.Blocks.LIGHT), "після корови лишилось світло");
        };
        h.runAfterDelay(RAGE_TIMEOUT - 1, clear);
        h.succeedWhen(() -> {
            h.assertFalse(cleared[0], "за 15 с скелет не пішов на корову");
            h.assertTrue(sk.getTarget() == cow, "скелет ще не приманений");
            h.assertTrue(lightSeen[0] > 0, "навколо корови немає світла");
            LOG.info("[тест] starFungusGlowsAndLures: світіння {} -> {}, скелет приманений за {} с, світло {}", glow1, glow2, h.getTick() / 20.0, lightSeen[0]);
            clear.run();
        });
    }

    // 5в. Тяга (Fij): з'їла гриб 3 рази — іде до нього, хоча ще не голодна (голод 17 < 25)
    @GameTest(template = "arena", batch = "hay_eat", timeoutTicks = AVOID_TIMEOUT)
    public static void fungusCraving(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        BlockPos fungus = new BlockPos(9, 2, 10);
        h.setBlock(fungus, hay(Stage.DRY, Cond.FIRE_FUNGUS, true));
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(5, 2, 10));
        cow.setAge(0);
        cow.setYRot(-90);
        cow.setYHeadRot(-90);
        cow.fungusEaten = 3;
        cow.hunger = 17;
        boolean[] cleared = { false };
        Runnable clear = () -> { cleared[0] = true; cow.discard(); h.setBlock(fungus, net.minecraft.world.level.block.Blocks.AIR); };
        h.runAfterDelay(AVOID_TIMEOUT - 1, clear);
        h.succeedWhen(() -> {
            h.assertFalse(cleared[0], "за 30 с не пішла до гриба (поріг тяги " + cow.fungusCraveHunger() + ", голод " + cow.hunger + ")");
            h.assertTrue(h.getBlockState(fungus).isAir(), "ще не з'їла");
            LOG.info("[тест] fungusCraving: з'їла за {} с, тяга тепер {} (поріг голоду {})", h.getTick() / 20.0, cow.fungusEaten, cow.fungusCraveHunger());
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
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);   // тестовий гравець спільний — не лишати йому предметів
        level.getEntitiesOfClass(ItemEntity.class, new AABB(g1).inflate(6)).forEach(ItemEntity::discard);
        level.setBlockAndUpdate(g2, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(g1.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(g2.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        h.assertTrue(hoe && bundles == 1 && level.getBlockState(g1).isAir(), "мотика не дала пучок свіжої трави");
        h.assertFalse(sneakShears, "присівши з ножицями — має ламатись як звичайно");
        h.assertTrue(afterBundle == 40 && afterWheat == 35, "пучок −10 / пшениця −5 не так: " + afterBundle + " / " + afterWheat);
        h.succeed();
    }

    // 7. Вода будь-яким боком: 1 хв — мокре, ще 2 хв — гниле/пліснява; біля вогнища мокре сохне за 30 с; сухе без води не гниє
    @GameTest(template = "platform", batch = "hay", timeoutTicks = 20)
    public static void hayWaterRotAndFireDry(GameTestHelper h) {
        var level = h.getLevel();
        level.setWeatherParameters(6000, 0, false, false);
        RandomSource r = always();
        BlockPos wet = h.absolutePos(new BlockPos(2, 3, 2)), dry = h.absolutePos(new BlockPos(6, 3, 6)), keep = h.absolutePos(new BlockPos(2, 3, 6));
        // мокне: вода збоку
        level.setBlockAndUpdate(wet, hay(Stage.DRY, Cond.CLEAN, true));
        level.setBlockAndUpdate(wet.east(), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
        CowHayBlock.check(level.getBlockState(wet), level, wet, r, 30);
        Cond after30 = level.getBlockState(wet).getValue(CowHayBlock.COND);
        CowHayBlock.check(level.getBlockState(wet), level, wet, r, 30);
        Cond after60 = level.getBlockState(wet).getValue(CowHayBlock.COND);
        CowHayBlock.check(level.getBlockState(wet), level, wet, r, 120);
        Cond after180 = level.getBlockState(wet).getValue(CowHayBlock.COND);
        // сохне біля запаленого вогнища
        level.setBlockAndUpdate(dry, hay(Stage.DRY, Cond.WET, true));
        level.setBlockAndUpdate(dry.east(), net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState());
        CowHayBlock.check(level.getBlockState(dry), level, dry, r, 30);
        Cond fireDried = level.getBlockState(dry).getValue(CowHayBlock.COND);
        // суха без води: 200 випадкових тіків з «кубиком, що завжди випадає» — не гниє
        BlockState k = hay(Stage.DRY, Cond.CLEAN, true);
        level.setBlockAndUpdate(keep, k);
        for (int i = 0; i < 200; i++) k = CowHayBlock.age(k, level, keep, r);
        for (int i = 0; i < 10; i++) CowHayBlock.check(k, level, keep, r, 60);
        Cond kept = level.getBlockState(keep).getValue(CowHayBlock.COND);
        LOG.info("[тест] hayWaterRotAndFireDry: вода 30 с {}, 60 с {}, 180 с {}; біля вогнища {}; суха без води {} ({})",
                after30, after60, after180, fireDried, k.getValue(CowHayBlock.COND), kept);
        for (BlockPos p : new BlockPos[] { wet, wet.east(), dry, dry.east(), keep }) level.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        h.assertTrue(after30 == Cond.CLEAN && after60 == Cond.WET, "не намокло за 1 хв (30 с: " + after30 + ", 60 с: " + after60 + ")");
        h.assertTrue(after180 == Cond.ROTTEN || after180.moldy(), "мокре не зіпсувалось ще за 2 хв: " + after180);
        h.assertTrue(fireDried == Cond.CLEAN, "біля вогнища не висохло: " + fireDried);
        h.assertTrue(k.getValue(CowHayBlock.COND) == Cond.CLEAN && kept == Cond.CLEAN, "суха без води зіпсувалась");
        h.succeed();
    }

    // 8. Вода крізь сіно (Fij, варіант Б): джерело над сіном — вода проходить крізь сіно й тече з боків; прибрали джерело — висихає.
    //    Сіно лишається твердим: свиня стоїть на ньому, не провалюється
    private static final int WATER_TIMEOUT = 300;

    @GameTest(template = "platform", batch = "hay_water", timeoutTicks = WATER_TIMEOUT)
    public static void waterFlowsThroughHay(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos hayPos = new BlockPos(4, 2, 4), source = hayPos.above(), side = hayPos.east(), stand = new BlockPos(2, 2, 6);
        h.setBlock(hayPos, hay(Stage.DRY, Cond.CLEAN, true));
        h.setBlock(stand, hay(Stage.DRY, Cond.CLEAN, true));
        Pig pig = h.spawn(EntityType.PIG, stand.above());
        pig.setNoAi(true);
        h.setBlock(source, net.minecraft.world.level.block.Blocks.WATER);
        int[] flowedAt = { -1 }, driedAt = { -1 };
        boolean[] removed = { false };
        h.onEachTick(() -> {
            boolean inside = h.getBlockState(hayPos).getBlock() instanceof CowHayBlock && h.getBlockState(hayPos).getValue(CowHayBlock.WATERLOGGED);
            boolean out = level.getFluidState(h.absolutePos(side)).is(net.minecraft.tags.FluidTags.WATER);
            if (flowedAt[0] < 0 && inside && out) {
                flowedAt[0] = (int) h.getTick();
                h.setBlock(source, net.minecraft.world.level.block.Blocks.AIR);   // прибрати джерело
                removed[0] = true;
            }
            if (removed[0] && driedAt[0] < 0 && !inside && !out) driedAt[0] = (int) h.getTick();
        });
        h.succeedWhen(() -> {
            h.assertTrue(flowedAt[0] >= 0, "вода не пройшла крізь сіно");
            h.assertTrue(driedAt[0] >= 0, "після джерела вода в сіні не висохла");
            double top = h.absolutePos(stand).getY() + 1.0;
            LOG.info("[тест] waterFlowsThroughHay: протекла на {} с, висохла на {} с, свиня y {} (верх сіна {})",
                    flowedAt[0] / 20.0, driedAt[0] / 20.0, String.format("%.3f", pig.getY()), top);
            h.assertTrue(pig.getY() > top - 0.01, "свиня провалилась у сіно");
            pig.discard();
            h.setBlock(hayPos, net.minecraft.world.level.block.Blocks.AIR);
            h.setBlock(stand, net.minecraft.world.level.block.Blocks.AIR);
        });
    }

    // 9. Скріншот Fij: стовп сіна над сіном з водою — вода НЕ лізе вгору (лише вниз і вбік); кут купи не стає вічним джерелом
    @GameTest(template = "platform", batch = "hay_water", timeoutTicks = 200)
    public static void waterDoesNotClimbHay(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos b0 = new BlockPos(3, 2, 3), b1 = b0.above(), b2 = b1.above(), corner = new BlockPos(4, 2, 4);
        h.setBlock(b0, hay(Stage.DRY, Cond.CLEAN, true));
        h.setBlock(b1, hay(Stage.DRY, Cond.CLEAN, true));
        h.setBlock(b2, hay(Stage.DRY, Cond.CLEAN, true));
        h.setBlock(b0.east(), hay(Stage.DRY, Cond.CLEAN, true));    // кут: (4,2,3) і (3,2,4) — сіно, (4,2,4) — порожньо
        h.setBlock(b0.south(), hay(Stage.DRY, Cond.CLEAN, true));
        h.setBlock(b0.west(), net.minecraft.world.level.block.Blocks.WATER);   // джерело збоку внизу
        h.runAfterDelay(60, () -> {
            boolean bottom = h.getBlockState(b0).getValue(CowHayBlock.WATERLOGGED);
            boolean mid = h.getBlockState(b1).getValue(CowHayBlock.WATERLOGGED), top = h.getBlockState(b2).getValue(CowHayBlock.WATERLOGGED);
            boolean cornerSource = level.getFluidState(h.absolutePos(corner)).isSource();
            h.setBlock(b0.west(), net.minecraft.world.level.block.Blocks.AIR);   // прибрати джерело
            h.runAfterDelay(60, () -> {
                boolean stillWet = h.getBlockState(b0).getValue(CowHayBlock.WATERLOGGED);
                boolean water = level.getFluidState(h.absolutePos(corner)).is(net.minecraft.tags.FluidTags.WATER);
                LOG.info("[тест] waterDoesNotClimbHay: низ {}, вище {} / {}, у куті джерело {}; після прибирання вода в сіні {}, у куті {}",
                        bottom, mid, top, cornerSource, stillWet, water);
                for (BlockPos p : new BlockPos[] { b0, b1, b2, b0.east(), b0.south(), corner }) h.setBlock(p, net.minecraft.world.level.block.Blocks.AIR);
                h.assertTrue(bottom, "нижнє сіно біля джерела не пропустило воду");
                h.assertFalse(mid || top, "вода полізла вгору стовпом сіна");
                h.assertFalse(cornerSource, "у куті купи з'явилось нове джерело");
                h.assertFalse(stillWet || water, "після прибирання джерела вода лишилась");
                h.succeed();
            });
        });
    }

    // 10. Скріншот Fij «у всі боки і вниз»: вода проходить лише крізь сіно, що торкається джерела; сусіднє сіно — сухе
    @GameTest(template = "platform", batch = "hay_water", timeoutTicks = 200)
    public static void waterPassesOnlyOneHay(GameTestHelper h) {
        BlockPos src = new BlockPos(2, 2, 4), h1 = new BlockPos(3, 2, 4), h2 = new BlockPos(4, 2, 4), h3 = new BlockPos(3, 2, 5),
                h4 = new BlockPos(5, 2, 4), h5 = new BlockPos(4, 3, 4);
        for (BlockPos p : new BlockPos[] { h1, h2, h3, h4, h5 }) h.setBlock(p, hay(Stage.DRY, Cond.CLEAN, true));
        h.setBlock(src, net.minecraft.world.level.block.Blocks.WATER);
        h.runAfterDelay(60, () -> {
            boolean w1 = h.getBlockState(h1).getValue(CowHayBlock.WATERLOGGED);
            int others = 0;
            for (BlockPos p : new BlockPos[] { h2, h3, h4, h5 }) if (h.getBlockState(p).getValue(CowHayBlock.WATERLOGGED)) others++;
            LOG.info("[тест] waterPassesOnlyOneHay: сіно біля джерела з водою {}, інших з водою {} з 4", w1, others);
            for (BlockPos p : new BlockPos[] { src, h1, h2, h3, h4, h5 }) h.setBlock(p, net.minecraft.world.level.block.Blocks.AIR);
            h.assertTrue(w1, "сіно біля джерела не пропустило воду");
            h.assertTrue(others == 0, "вода пішла далі крізь сусіднє сіно: " + others);
            h.succeed();
        });
    }

    // 11. /hungrycows hay — скрині з усіма видами сіна
    @GameTest(template = "platform", batch = "hay_cmd", timeoutTicks = 20)
    public static void hayChestCommand(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos at = h.absolutePos(new BlockPos(1, 2, 4));
        var src = level.getServer().createCommandSourceStack().withLevel(level).withPosition(Vec3.atBottomCenterOf(at)).withPermission(4).withSuppressedOutput();
        level.getServer().getCommands().performPrefixedCommand(src, "hungrycows hay");
        int stacks = 0, chests = 0;
        java.util.Set<Integer> kinds = new java.util.HashSet<>();
        for (int k = 0; k < 4; k++) {
            if (level.getBlockEntity(at.east(2 + k)) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
                chests++;
                for (int j = 0; j < chest.getContainerSize(); j++) {
                    ItemStack st = chest.getItem(j);
                    if (st.is(ModBlocks.HAY_ITEM.get())) { stacks++; kinds.add(CowHayBlock.variant(st)); }
                }
            }
            level.setBlockAndUpdate(at.east(2 + k), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        LOG.info("[тест] hayChestCommand: скринь {}, стосів сіна {}, різних видів {}", chests, stacks, kinds.size());
        h.assertTrue(chests == 3 && stacks == 64 && kinds.size() == 64, "у скринях не всі види сіна");
        h.succeed();
    }
}
