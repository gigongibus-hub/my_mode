package com.gigongibus.hungrycows;

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
    public static final int ZOMBIE_TARGET_PRIORITY = 4;  // у зомбі: 2 гравець, 3 селянин і залізний голем, 4 корова, 5 черепаха

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
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hungrycows")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("brain")
                        .then(Commands.literal("on").executes(c -> setBrain(c.getSource(), true)))
                        .then(Commands.literal("off").executes(c -> setBrain(c.getSource(), false))))
                .then(Commands.literal("birth")
                        .then(Commands.literal("single").executes(c -> spawnPregnant(c.getSource(), 1)))
                        .then(Commands.literal("twins").executes(c -> spawnPregnant(c.getSource(), 2)))));
    }

    // /hungrycows brain on|off — увімкнути чи вимкнути мозок усіх корів (до перезапуску гри)
    private static int setBrain(CommandSourceStack source, boolean on) {
        CowBrain.enabled = on;
        source.sendSuccess(() -> Component.literal(on
                ? "Мозок корів увімкнено"
                : "Мозок корів вимкнено: діють старі правила"), true);
        return 1;
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
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            Regrowth.tick(level);
        }
    }

    // Гравець зламав блок: на цьому місці нічого не відросте (наприклад, перекопав землю з-під моху)
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) Regrowth.blockChanged(level, event.getPos());
    }

    // Гравець поставив блок: теж скасовує відростання на цьому місці
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) Regrowth.blockChanged(level, event.getPos());
    }

    // Вибух поруч: корови, яких він зачепив, отримують досвід «поруч вибух»
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;
        for (Entity e : event.getAffectedEntities()) {
            if (e instanceof HungryCow cow && cow.isAlive()) {
                cow.experience(Experience.EXPLOSION, HungryCow.EXPLOSION_EXPERIENCE_COOLDOWN);
            }
        }
    }
}
