package com.gigongibus.hungrycows;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import com.gigongibus.hungrycows.CowCharacter.Experience;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

// Події гри (не завантаження мода): зомбі нападають на корів, корови лякаються вибухів
@Mod.EventBusSubscriber(modid = HungryCowsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModForgeEvents {

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int TEST_LABOR_IN_SECONDS = 10;  // команда /hungrycows birth: через скільки секунд почнуться пологи
    public static final int ZOMBIE_TARGET_PRIORITY = 4;
    public static final boolean REMOVE_VANILLA_COWS = true;   // етап 10: ванільних корів прибрати повністю
    public static final int CALF_PRICE = 30;                  // теля у мандрівного торговця — 30 смарагдів
    public static final int CALF_TRADES = 1;                  // продає одне (рідкісна пропозиція)  // у зомбі: 2 гравець, 3 селянин і залізний голем, 4 корова, 5 черепаха

    // Список «на кого нападати» (Mob.targetSelector) закритий, тому беремо його через рефлексію.
    // "f_21346_" — внутрішня назва поля targetSelector у Forge 1.20.1. Якщо поле не знайдеться,
    // гра не впаде: зомбі просто не нападатимуть на корів, а в лозі буде попередження.
    private static Field targetSelectorField = null;
    private static boolean targetSelectorFailed = false;

    private static GoalSelector targetSelector(Mob mob) {
        if (targetSelectorFailed) return null;
        try {
            if (targetSelectorField == null) {
                targetSelectorField = ObfuscationReflectionHelper.findField(Mob.class, "f_21346_");
            }
            return (GoalSelector) targetSelectorField.get(mob);
        } catch (Exception e) {
            targetSelectorFailed = true;
            LogUtils.getLogger().warn("Hungry Cows: не вдалося дати зомбі ціль «корова»", e);
            return null;
        }
    }

    // Зомбі (і кадавр, утоплений, зомбі-селянин) полюють на корів. Зомбований піглін — ні: він не ворожий сам по собі.
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        // етап 10: ванільних корів немає зовсім (не з'являються й зникають зі старих світів); грибні корови лишаються
        if (event.getEntity().getType() == net.minecraft.world.entity.EntityType.COW && REMOVE_VANILLA_COWS) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof Zombie zombie && !(zombie instanceof ZombifiedPiglin)) {
            GoalSelector targets = targetSelector(zombie);
            if (targets != null) {
                targets.addGoal(ZOMBIE_TARGET_PRIORITY, new NearestAttackableTargetGoal<>(zombie, HungryCow.class, true));
            }
        }
    }

    // Команди для тестів (потрібні права оператора / чити):
    //   /hungrycows birth single — вагітна самка, за 10 с пологи, одне теля
    //   /hungrycows birth twins  — те саме, але двійнята
    //   /hungrycows hay          — скрині з усіма видами сіна мода (стадії × стани × нитки), поруч із гравцем
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hungrycows")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("brain")
                        .then(Commands.literal("on").executes(c -> setBrain(c.getSource(), true)))
                        .then(Commands.literal("off").executes(c -> setBrain(c.getSource(), false))))
                .then(Commands.literal("path").executes(c -> pathTest(c.getSource())))
                .then(Commands.literal("old").executes(c -> spawnOld(c.getSource())))
                .then(Commands.literal("sick").executes(c -> spawnSick(c.getSource())))
                .then(Commands.literal("hay").executes(c -> hayChests(c.getSource())))
                .then(Commands.literal("birth")
                        .then(Commands.literal("single").executes(c -> spawnPregnant(c.getSource(), 1)))
                        .then(Commands.literal("twins").executes(c -> spawnPregnant(c.getSource(), 2)))));
    }

    // /hungrycows old — доросла корова віком 999 діб (перевірити старість)
    public static final double OLD_COW_DAYS = 999;

    private static int spawnOld(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        HungryCow cow = ModEntities.HUNGRY_COW.get().create(level);
        if (cow == null) return 0;
        net.minecraft.world.phys.Vec3 p = source.getPosition();
        cow.moveTo(p.x, p.y, p.z, 0, 0);
        cow.aging.ageSeconds = OLD_COW_DAYS * CowCycle.SECONDS_PER_DAY;
        level.addFreshEntity(cow);
        source.sendSuccess(() -> Component.literal(cow.family.name + ": " + cow.aging.line()), true);
        return 1;
    }

    // /hungrycows sick — заражена «кашлем» корова (перевірити хворобу)
    // ================= /hungrycows hay =================
    public static final int HAY_CHEST_COUNT_EACH = 64;   // скільки кожного виду в скрині

    /** Усі можливі види сіна: 4 стадії × стани (зірочник лише свіже, вогняний гриб лише сухе, яблуко/морква лише свіже) × нитки */
    public static java.util.List<ItemStack> allHayKinds() {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        net.minecraft.world.level.block.state.BlockState base = ModBlocks.HAY.get().defaultBlockState();
        for (boolean bound : new boolean[] { true, false }) {
            for (CowHayBlock.Stage st : CowHayBlock.Stage.values()) {
                for (CowHayBlock.Cond c : CowHayBlock.Cond.values()) {
                    if ((c == CowHayBlock.Cond.STAR_FUNGUS || c == CowHayBlock.Cond.APPLE || c == CowHayBlock.Cond.CARROT) && st != CowHayBlock.Stage.FRESH) continue;
                    if (c == CowHayBlock.Cond.FIRE_FUNGUS && st != CowHayBlock.Stage.DRY) continue;
                    ItemStack s = CowHayBlock.stackOf(base.setValue(CowHayBlock.STAGE, st).setValue(CowHayBlock.COND, c).setValue(CowHayBlock.BOUND, bound));
                    s.setCount(HAY_CHEST_COUNT_EACH);
                    out.add(s);
                }
            }
        }
        return out;
    }

    // Ставить скрині в ряд перед гравцем (на схід) і кладе в них усі види сіна
    private static int hayChests(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        java.util.List<ItemStack> kinds = allHayKinds();
        BlockPos start = BlockPos.containing(source.getPosition()).east(2);
        int chests = 0;
        for (int i = 0; i < kinds.size(); i += 27, chests++) {
            BlockPos pos = start.east(chests);
            level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
                for (int j = 0; j < 27 && i + j < kinds.size(); j++) chest.setItem(j, kinds.get(i + j));
            }
        }
        int n = chests;
        source.sendSuccess(() -> Component.literal("Скрині з сіном: " + kinds.size() + " видів у " + n + " скринях (на схід від тебе)"), true);
        return 1;
    }

    private static int spawnSick(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        HungryCow cow = ModEntities.HUNGRY_COW.get().create(level);
        if (cow == null) return 0;
        net.minecraft.world.phys.Vec3 p = source.getPosition();
        cow.moveTo(p.x, p.y, p.z, 0, 0);
        level.addFreshEntity(cow);
        cow.disease.infect();
        source.sendSuccess(() -> Component.literal(cow.family.name + ": " + cow.disease.line()), true);
        return 1;
    }

    // /hungrycows brain on|off — увімкнути чи вимкнути мозок усіх корів (до перезапуску гри)
    private static int setBrain(CommandSourceStack source, boolean on) {
        CowBrain.enabled = on;
        source.sendSuccess(() -> Component.literal(on
                ? "Мозок корів увімкнено"
                : "Мозок корів вимкнено: діють старі правила"), true);
        return 1;
    }

    // ================= ПЕРЕВІРКА ШЛЯХУ (/hungrycows path) =================
    private static final double PATH_TEST_RANGE = 48;   // які корови перевіряють шлях до гравця
    private static final int PATH_TEST_COWS = 3;        // скільки найближчих корів

    // /hungrycows path — чи можуть корови дійти до місця, де стоїть гравець, і що саме заважає
    private static int pathTest(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        net.minecraft.core.BlockPos target = net.minecraft.core.BlockPos.containing(source.getPosition());
        source.sendSuccess(() -> Component.literal("Місце " + target.toShortString() + ": можна стати "
                + (CowHome.standable(level, target) ? "так" : "ні") + ", дах " + (CowHome.hasRoof(level, target) ? "так" : "ні")
                + ", стін " + CowHome.walls(level, target) + ", бали дому " + CowHome.shelterScore(level, target)), false);
        java.util.List<HungryCow> cows = level.getEntitiesOfClass(HungryCow.class,
                new net.minecraft.world.phys.AABB(target).inflate(PATH_TEST_RANGE), c -> c.isAlive() && !c.isBaby());
        cows.sort(java.util.Comparator.comparingDouble(c -> c.distanceToSqr(source.getPosition())));
        if (cows.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Поруч немає дорослих корів"), false);
            return 0;
        }
        for (int i = 0; i < Math.min(PATH_TEST_COWS, cows.size()); i++) {
            HungryCow cow = cows.get(i);
            cow.getNavigation().setMaxVisitedNodesMultiplier(CowHome.PATH_SEARCH_MULTIPLIER);
            net.minecraft.world.level.pathfinder.Path path = cow.getNavigation().createPath(target, 1);
            cow.getNavigation().resetMaxVisitedNodesMultiplier();
            String name = cow.family.name + " (" + cow.blockPosition().toShortString() + ")";
            net.minecraft.core.BlockPos home = cow.home.home();
            net.minecraft.core.BlockPos door = cow.home.entrance();
            String homeText = home == null ? "дому немає" : "дім " + home.toShortString() + ", бали " + cow.home.homeScore()
                    + ", вхід " + (door == null ? "невідомий" : door.toShortString());
            String result;
            if (path == null || path.getEndNode() == null) {
                result = "шляху немає зовсім";
            } else {
                net.minecraft.core.BlockPos end = path.getEndNode().asBlockPos();
                if (end.distManhattan(target) <= 1) {
                    result = "дійде (" + path.getNodeCount() + " кроків)";
                } else {
                    result = "шлях обривається в " + end.toShortString() + "; далі " + obstacle(level, end, target);
                }
            }
            String text = name + ": " + homeText + "; " + result;
            source.sendSuccess(() -> Component.literal(text), false);
        }
        return 1;
    }

    // Що лежить на один блок далі від кінця шляху в бік цілі: під ногами, на рівні ніг і голови
    private static String obstacle(ServerLevel level, net.minecraft.core.BlockPos end, net.minecraft.core.BlockPos target) {
        int dx = target.getX() - end.getX();
        int dz = target.getZ() - end.getZ();
        net.minecraft.core.BlockPos next = Math.abs(dx) >= Math.abs(dz)
                ? end.offset(Integer.signum(dx), 0, 0) : end.offset(0, 0, Integer.signum(dz));
        net.minecraft.world.level.pathfinder.BlockPathTypes type =
                net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getBlockPathTypeStatic(level, next.mutable());
        return next.toShortString() + " [" + type.name() + "]: під ногами " + blockName(level, next.below())
                + ", ноги " + blockName(level, next) + ", голова " + blockName(level, next.above());
    }

    private static String blockName(ServerLevel level, net.minecraft.core.BlockPos pos) {
        net.minecraft.resources.ResourceLocation key =
                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock());
        return key == null ? "?" : key.getPath();
    }

    private static int spawnPregnant(CommandSourceStack source, int calves) {
        ServerLevel level = source.getLevel();
        HungryCow cow = ModEntities.HUNGRY_COW.get().create(level);
        if (cow == null) return 0;
        Vec3 pos = source.getPosition();
        cow.moveTo(pos.x, pos.y, pos.z, source.getRotation().y, 0.0F);
        cow.repro.startTestPregnancy(TEST_LABOR_IN_SECONDS, calves);
        cow.family.name = CowNames.random(true, level.getRandom());
        level.addFreshEntity(cow);
        String what = calves == 2 ? "двійнят" : "одне теля";
        source.sendSuccess(() -> Component.literal("Вагітна корова " + cow.family.name + ": через "
                + TEST_LABOR_IN_SECONDS + " с пологи, народить " + what), true);
        return 1;
    }

    // Відростання трави й моху: раз на секунду в кожному світі
    // Слух: кожен звук, який грає сервер (вибух, удар, дзвін, мукання…), — у CowHearing
    @SubscribeEvent
    public static void onSoundAtPosition(net.minecraftforge.event.PlayLevelSoundEvent.AtPosition event) {
        if (event.getLevel().isClientSide || event.getSound() == null) return;
        event.getSound().unwrapKey().ifPresent(k -> CowHearing.onSound(event.getLevel(), k.location().toString(), event.getPosition(), null, event.getOriginalVolume()));
    }

    @SubscribeEvent
    public static void onSoundAtEntity(net.minecraftforge.event.PlayLevelSoundEvent.AtEntity event) {
        if (event.getLevel().isClientSide || event.getSound() == null || event.getEntity() == null) return;
        Entity e = event.getEntity();
        event.getSound().unwrapKey().ifPresent(k -> CowHearing.onSound(event.getLevel(), k.location().toString(), e.position().add(0, e.getBbHeight() / 2, 0), e, event.getOriginalVolume()));
    }

    // етап 10: нове село — корови для нього
    @SubscribeEvent
    public static void onChunkLoad(net.minecraftforge.event.level.ChunkEvent.Load event) {
        if (event.isNewChunk() && event.getLevel() instanceof ServerLevel level) VillageCows.onNewChunk(level, event.getChunk());
    }

    // етап 10: мандрівний торговець іноді продає теля
    @SubscribeEvent
    public static void onWandererTrades(net.minecraftforge.event.village.WandererTradesEvent event) {
        event.getRareTrades().add((trader, random) -> new net.minecraft.world.item.trading.MerchantOffer(
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, CALF_PRICE),
                new net.minecraft.world.item.ItemStack(ModItems.CALF_TICKET.get()), CALF_TRADES, 10, 0.05F));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            VillageCows.tick(level);
            Regrowth.tick(level);
            if (level.getGameTime() % CowSmell.TRAIL_EVERY == 0) CowSmell.recordTrails(level);   // сліди запаху
        }
    }

    // Гравець зламав блок: на цьому місці нічого не відросте (наприклад, перекопав землю з-під моху)
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Regrowth.blockChanged(level, event.getPos());
            // слух: звук ламання гра грає лише в гравця (не на сервері) — корови «чують» саму подію
            CowHearing.onSound(level, event.getState().getSoundType().getBreakSound().getLocation().toString(), Vec3.atCenterOf(event.getPos()), event.getPlayer());
            // трава мотикою чи ножицями (не присівши) — пучок свіжої трави (Fij)
            if (GrassHarvest.harvest(level, event.getPos(), event.getState(), event.getPlayer())) event.setCanceled(true);
        }
    }

    // Вода крізь сіно: сіно з водою гра вважає «джерелом». Біля нього НЕ можна народитися новому джерелу (вода між двома
    // такими блоками сіна стала б вічним джерелом і живила б сіно далі, навіть коли справжнє джерело прибрали)
    @SubscribeEvent
    public static void onFluidSource(BlockEvent.CreateFluidSourceEvent event) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            net.minecraft.world.level.block.state.BlockState n = event.getLevel().getBlockState(event.getPos().relative(d));
            if (n.getBlock() instanceof CowHayBlock && n.getValue(CowHayBlock.WATERLOGGED)) {
                event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
                return;
            }
        }
    }

    // Гравець поставив блок: теж скасовує відростання на цьому місці
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Regrowth.blockChanged(level, event.getPos());
            CowHearing.onSound(level, event.getPlacedBlock().getSoundType().getPlaceSound().getLocation().toString(), Vec3.atCenterOf(event.getPos()), event.getEntity());
        }
    }

    // Вибух поруч: корови, яких він зачепив, отримують досвід «поруч вибух»
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;
        // слух: звук вибуху гра грає лише в гравця — корови «чують» сам вибух (до 64 бл.)
        CowHearing.onSound(event.getLevel(), "minecraft:entity.generic.explode", event.getExplosion().getPosition(), null);
        for (Entity e : event.getAffectedEntities()) {
            if (e instanceof HungryCow cow && cow.isAlive()) {
                cow.experience(Experience.EXPLOSION, HungryCow.EXPLOSION_EXPERIENCE_COOLDOWN);
            }
        }
    }
}
