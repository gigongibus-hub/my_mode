package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.WorldClock;
import com.gigongibus.hungrycows.carve.CarvedPartItem;
import com.gigongibus.hungrycows.carve.Carving;
import com.gigongibus.hungrycows.carve.MugItem;
import com.gigongibus.hungrycows.glue.Glue;
import com.gigongibus.hungrycows.glue.MugAssemblyBlock;
import com.gigongibus.hungrycows.glue.ResinBrushItem;
import com.gigongibus.hungrycows.glue.ResinCauldronBlock;
import com.gigongibus.hungrycows.glue.ResinCauldronBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** Склеювання кружки (Fij, 19.11): смола з ялини, щетина свині, пензлик, котел зі смолою на багатті, склеювання */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowGlueTests {
    private static final Logger LOG = LogUtils.getLogger();

    private static FakePlayer player(GameTestHelper h, BlockPos near) {
        var p = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        Vec3 c = Vec3.atCenterOf(h.absolutePos(near));
        p.moveTo(c.x, c.y, c.z - 2, 0, 30);
        p.getInventory().clearContent();
        return p;
    }

    private static void use(GameTestHelper h, FakePlayer p, BlockPos rel, ItemStack held) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().getBlockState(abs).use(h.getLevel(), p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
    }

    // 1. Ялину сокирою — смола (50%, 1–6 на колоду); ножиці по свині — 1–3 щетини, 3 рази за 5 діб; рецепт пензлика
    @GameTest(template = "arena", batch = "glue", timeoutTicks = 40)
    public static void resinBristleBrush(GameTestHelper h) {
        TestWorld.calm(h);
        var p = player(h, new BlockPos(7, 2, 7));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));
        int logs = 40, withResin = 0, resin = 0, max = 0;
        for (int i = 0; i < logs; i++) {
            BlockPos b = new BlockPos(7, 2, 7);
            h.setBlock(b, Blocks.SPRUCE_LOG);
            p.gameMode.destroyBlock(h.absolutePos(b));
            int n = 0;
            for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(b)).inflate(2)))
                if (e.getItem().is(Glue.RESIN.get())) { n += e.getItem().getCount(); e.discard(); }
                else e.discard();
            if (n > 0) withResin++;
            resin += n;
            max = Math.max(max, n);
        }
        var pig = h.spawn(EntityType.PIG, new BlockPos(4, 2, 4));
        long t = h.getLevel().getGameTime();
        boolean a = Glue.shear(pig, t), b = Glue.shear(pig, t + 10), c = Glue.shear(pig, t + 20), d = Glue.shear(pig, t + 30);
        boolean later = Glue.shear(pig, t + Glue.SHEAR_PERIOD + 1);
        pig.discard();
        var recipe = h.getLevel().getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation(HungryCowsMod.MODID, "bristle_brush"));
        ItemStack out = recipe.map(r -> r.getResultItem(h.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
        LOG.info("[тест] resinBristleBrush: смола з {} колод з {} (усього {}, найбільше {}); стрижки {} {} {} {}, через 5 діб {}; пензлик {} ({} разів)",
                withResin, logs, resin, max, a, b, c, d, later, out, out.getMaxDamage());
        h.assertTrue(withResin > logs / 5 && withResin < logs * 4 / 5 && max <= Glue.RESIN_MAX, "смола не ~50% або забагато");
        h.assertTrue(a && b && c && !d && later, "стрижка не 3 рази на 5 діб");
        h.assertTrue(out.is(Glue.BRISTLE_BRUSH.get()) && out.getMaxDamage() == Glue.BRUSH_USES, "немає рецепта пензлика");
        h.succeed();
    }

    // 2. Котел: смола на запаленому багатті за добу плавиться (тут ×1000), пензлик набирає (−1 смола); без вогню застигає;
    //    у розплавленій — сповільнення
    // окрема партія: тести однієї партії йдуть разом, а швидкість світу спільна (склеювання повертало її на 1 посеред плавлення)
    @GameTest(template = "arena", batch = "glue_cauldron", timeoutTicks = 200)
    public static void cauldronMeltsResin(GameTestHelper h) {
        TestWorld.calm(h);
        BlockPos fire = new BlockPos(5, 1, 5), pot = fire.above();
        h.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        h.setBlock(pot, Blocks.CAULDRON);
        ResinCauldronBlock.place(h.getLevel(), h.absolutePos(pot), 5);
        var p = player(h, pot);
        use(h, p, pot, new ItemStack(Glue.RESIN.get(), 3));
        var be = (ResinCauldronBlockEntity) h.getBlockEntity(pot);
        int afterAdd = be.resin();
        ItemStack brush = new ItemStack(Glue.BRISTLE_BRUSH.get());
        use(h, p, pot, brush);
        boolean coldBrush = ResinBrushItem.hasResin(brush);
        WorldClock.setSpeed(h.getLevel(), 1000);
        java.util.Set<Integer> stages = new java.util.TreeSet<>();   // стадії плавлення, які було видно (Fij: «хоча б три»)
        h.onEachTick(() -> {
            var st = h.getBlockState(pot);
            if (st.is(Glue.RESIN_CAULDRON.get())) stages.add(st.getValue(ResinCauldronBlock.MOLTEN) ? 3 : st.getValue(ResinCauldronBlock.MELT));
        });
        h.runAfterDelay(40, () -> {
            boolean molten = h.getBlockState(pot).getValue(ResinCauldronBlock.MOLTEN);
            LOG.info("[тест] котел: вогонь {} ({}), прогрів {}, швидкість {}", ResinCauldronBlockEntity.hasFire(h.getLevel(), h.absolutePos(pot)),
                    h.getBlockState(fire), be.heat(), WorldClock.speed(h.getLevel()));
            use(h, p, pot, brush);
            boolean got = ResinBrushItem.hasResin(brush);
            int left = be.resin();
            var pig = h.spawn(EntityType.PIG, pot.above());
            pig.moveTo(Vec3.atBottomCenterOf(h.absolutePos(pot)).add(0, 0.3, 0));
            h.runAfterDelay(10, () -> {
                boolean slowed = pig.hasEffect(MobEffects.MOVEMENT_SLOWDOWN);
                pig.discard();
                h.setBlock(fire, Blocks.STONE);
                h.runAfterDelay(20, () -> {
                    boolean hard = !h.getBlockState(pot).getValue(ResinCauldronBlock.MOLTEN);
                    WorldClock.setSpeed(h.getLevel(), 1);
                    LOG.info("[тест] cauldronMeltsResin: стадії {}, після +1 {}, холодним пензлем {}, розплавилась {}, пензлик {}, лишилось {}, свиня сповільнена {}, застигла {}",
                            stages, afterAdd, coldBrush, molten, got, left, slowed, hard);
                    h.setBlock(pot, Blocks.AIR);
                    h.assertTrue(afterAdd == 6 && !coldBrush, "смола не додається або холодна береться");
                    h.assertTrue(molten && got && left == 5, "не розплавилась або пензлик не набрав");
                    h.assertTrue(slowed, "розплавлена не сповільнює");
                    h.assertTrue(hard, "без вогню не застигла");
                    h.assertTrue(stages.containsAll(java.util.List.of(0, 1, 2, 3)), "не всі стадії плавлення: " + stages);
                    h.succeed();
                });
            });
        });
    }

    // 3. Склеювання: без смоли вушка не ставляться; смола → вушка → смола → ручка → пів доби (тут ×1000) → кружка
    @GameTest(template = "arena", batch = "glue", timeoutTicks = 120)
    public static void glueMug(GameTestHelper h) {
        TestWorld.calm(h);
        BlockPos at = new BlockPos(9, 2, 9);
        var p = player(h, at);
        ItemStack body = CarvedPartItem.withQuality(new ItemStack(Carving.MUG_BODY.get()), 1F);
        boolean placed = MugAssemblyBlock.place(h.getLevel(), h.absolutePos(at), p, body);
        ItemStack ears = CarvedPartItem.withQuality(new ItemStack(Carving.MUG_EARS.get()), 1F);
        use(h, p, at, ears);
        int noGlue = h.getBlockState(at).getValue(MugAssemblyBlock.STAGE);
        ItemStack brush = new ItemStack(Glue.BRISTLE_BRUSH.get());
        ResinBrushItem.setResin(brush, true);
        use(h, p, at, brush);
        int s1 = h.getBlockState(at).getValue(MugAssemblyBlock.STAGE);
        use(h, p, at, ears);
        int s2 = h.getBlockState(at).getValue(MugAssemblyBlock.STAGE);
        ResinBrushItem.setResin(brush, true);
        use(h, p, at, brush);
        use(h, p, at, CarvedPartItem.withQuality(new ItemStack(Carving.MUG_HANDLE.get()), 1F));
        int s4 = h.getBlockState(at).getValue(MugAssemblyBlock.STAGE);
        int brushWear = brush.getDamageValue();
        use(h, p, at, ItemStack.EMPTY);
        boolean wetTaken = !h.getBlockState(at).isAir();
        WorldClock.setSpeed(h.getLevel(), 1000);
        h.runAfterDelay(20, () -> {
            WorldClock.setSpeed(h.getLevel(), 1);
            int s5 = h.getBlockState(at).getValue(MugAssemblyBlock.STAGE);
            use(h, p, at, ItemStack.EMPTY);
            ItemStack mug = ItemStack.EMPTY;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++)
                if (p.getInventory().getItem(i).is(Carving.MUG.get())) mug = p.getInventory().getItem(i);
            int uses = mug.isEmpty() ? -1 : mug.getMaxDamage() - mug.getDamageValue();
            LOG.info("[тест] glueMug: поставлено {}, без смоли {}, стадії {} {} {} → {}, знос пензлика {}, мокру не забрати {}, кружка {} ({} разів)",
                    placed, noGlue, s1, s2, s4, s5, brushWear, wetTaken, mug, uses);
            h.setBlock(at, Blocks.AIR);
            h.assertTrue(placed && noGlue == 0, "вушка стали без смоли");
            h.assertTrue(s1 == 1 && s2 == 2 && s4 == 4 && s5 == 5, "стадії склеювання не по черзі");
            h.assertTrue(brushWear == 2 && wetTaken, "пензлик не зношується або мокру кружку забрали");
            h.assertTrue(uses == MugItem.MAX_USES, "кружка не та (точність 1 — найбільше разів)");
            h.succeed();
        });
    }
}
