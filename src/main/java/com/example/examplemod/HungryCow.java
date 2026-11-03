package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class HungryCow extends Cow {
    public static final int TIME_SPEED = 1;              // у скільки разів швидше росте голод і втома (1 = звичайно)
    public static final int HUNGER_EVERY_SECONDS = 10;   // голод росте на 1 раз на стільки секунд (для швидких тестів постав 1)
    public static final int FATIGUE_EVERY_SECONDS = 10;  // втома росте на 1 раз на стільки секунд
    public static final int STARVE_AT = 60;              // з цього голоду корова отримує урон
    public static final int GRASS_BLOCK_FOOD = 30;       // трав'яний блок: скільки голоду знімає
    public static final float GRASS_BLOCK_HEAL = 2.0F;   // трав'яний блок: скільки лікує
    public static final int PLANT_FOOD = 50;             // трава-рослина: скільки голоду знімає
    public static final float PLANT_HEAL = 4.0F;         // трава-рослина: скільки лікує
    public static final int MOSS_BLOCK_FOOD = 10;        // моховий блок: скільки голоду знімає

    public static final float CHOKE_MULTIPLIER = 100.0F; // множник шансу подавитися: 100 = завжди (для тестів), 1 = звичайно
    public static final float CHOKE_DAMAGE = 1.0F;       // урон від подавлення кожні 4 секунди
    public static final int CHOKE_SLOWNESS = 1;          // сповільнення: 0 = I, 1 = II
    public static final int CURE_FEAR = 10;              // скільки страху, якщо вдарити подавлену корову
    public static final boolean DEBUG_MESSAGES = true;   // писати в чат про подавлення, довіру, напади та пологи

    public int hunger = 0;
    public int fatigue = 0;
    public int fear = 0;
    public boolean choking = false;
    public int eatingTicksLeft = 0;                 // скільки тіків ще їсть блок, який поставив гравець

    public final CowTrust trust = new CowTrust();   // довіра до кожного гравця
    public final CowReproduction repro = new CowReproduction(this);   // стать, вагітність, пологи

    private boolean charging = false;               // ворожа корова зараз біжить на гравця
    private int chargeStartTick = 0;
    private int nextChargeTick = 0;
    private int lastAttackTick = -1000;
    private int lastPlayerHitTick = -1000;          // коли гравець востаннє бив цю корову

    private CompoundTag foodCounts = new CompoundTag(); // скільки разів за добу з'їдено кожне
    private long foodDay = -1;                          // номер ігрової доби для лічильників
    private int toxinLeft = 0;                          // скільки секунд ще діє власний урон
    private float toxinDamage = 0;                      // скільки HP за секунду
    private int dyingLeft = 0;                          // скільки секунд ще триває повільна смерть

    public HungryCow(EntityType<? extends Cow> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(-1, new LaborGoal(this));
        this.goalSelector.addGoal(0, new FearGoal(this));
        this.goalSelector.addGoal(1, new SeekGrassGoal(this));
        this.goalSelector.addGoal(2, new RestGoal(this));
        this.goalSelector.addGoal(3, new AvoidBluetGoal(this));
        this.goalSelector.addGoal(4, new ApproachPlayerGoal(this));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && this.tickCount % 20 == 0) {
            if (toxinLeft > 0) {
                this.hurt(this.damageSources().magic(), toxinDamage);
                toxinLeft--;
            }
            if (dyingLeft > 0) {
                dyingLeft--;
                if (dyingLeft % 5 == 0) {
                    this.hurt(this.damageSources().magic(), 1.0F);
                }
            }
            if (choking) {
                this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, CHOKE_SLOWNESS));
                if (this.tickCount % 80 == 0) {
                    this.hurt(this.damageSources().generic(), CHOKE_DAMAGE);
                }
            }
            if (!this.isAlive()) return;

            long second = this.tickCount / 20;
            repro.secondTick();
            if (second % HUNGER_EVERY_SECONDS == 0) hunger += TIME_SPEED;
            if (second % FATIGUE_EVERY_SECONDS == 0) fatigue += TIME_SPEED;
            if (hunger > STARVE_AT && this.tickCount % 80 == 0) {
                this.hurt(this.damageSources().starve(), 1.0F);
            }
            int gain = fearGainNearby();
            if (gain > 0) {
                fear = Math.min(100, fear + gain);
            } else {
                fear = Math.max(0, fear - 2);
            }

            // діагностика: показники й активні цілі
            String goals = this.goalSelector.getRunningGoals()
                    .map(g -> g.getGoal().getClass().getSimpleName().replace("Goal", ""))
                    .collect(Collectors.joining(","));
            String name = "Голод: " + hunger + " | Втома: " + fatigue + " | Страх: " + fear;
            Player nearest = this.level().getNearestPlayer(this, 16.0);
            if (nearest != null) {
                name += " | Довіра: " + trustOf(nearest);
            }
            name += " | " + goals;
            if (choking) {
                name = "ПОДАВИЛАСЬ! | " + name;
            }
            if (eatingTicksLeft > 0) {
                name = "ЇСТЬ ще " + (eatingTicksLeft / 20 + 1) + " с | " + name;
            }
            name = repro.decorate(name);
            this.setCustomName(Component.literal(name));
            this.setCustomNameVisible(true);
        }
    }

    private void tell(String text) {
        if (!DEBUG_MESSAGES) return;
        Component msg = Component.literal(text);
        this.level().players().forEach(p -> p.sendSystemMessage(msg));
    }

    // ===================== ПОДАВЛЕННЯ =====================

    private void startChoking() {
        if (choking) return;
        choking = true;
        tell("Корова подавилась!");
    }

    public void stopChoking() {
        if (!choking) return;
        choking = false;
        tell("Корову врятовано!");
    }

    // ===================== ДОВІРА =====================

    public int trustOf(Player player) {
        return trust.get(player.getUUID());
    }

    // scaled = true: застосувати тестовий множник CowTrust.TEST_MULTIPLIER
    public void addTrust(Player player, int delta, String reason, boolean scaled) {
        int real = scaled ? delta * CowTrust.TEST_MULTIPLIER : delta;
        int before = trust.get(player.getUUID());
        int after = trust.add(player.getUUID(), real);
        if (after != before) {
            tell("Довіра до " + player.getName().getString() + ": " + before + " -> " + after + " (" + reason + ")");
        }
    }

    // Корова почала тікати від цього гравця
    public void onScaredBy(Player player) {
        if (this.tickCount - lastPlayerHitTick < 60) return;           // страх від удару вже враховано
        if (trustOf(player) < CowTrust.SCARE_MIN_TRUST) return;
        if (trust.tryScare(player.getUUID(), this.tickCount)) {
            addTrust(player, -CowTrust.SCARE_PENALTY, "налякав", false);
        }
    }

    // Нагорода або покарання за годування з руки
    private void rewardPlayer(Player player, Item item, CowFood food) {
        boolean harmful = food.poisonSeconds > 0 || food.toxinSeconds > 0 || food.selfDamage > 0;
        if (harmful) {
            addTrust(player, -CowTrust.POISON_FEED_PENALTY, "отруйна їжа", true);
            return;
        }
        int gain = CowTrust.feedGain(item);
        if (gain <= 0) return;
        long day = this.level().getDayTime() / 24000L;
        if (!CowTrust.ONE_FEED_PER_DAY || trust.tryDailyFeed(player.getUUID(), day)) {
            addTrust(player, gain, "ласощі", true);
        } else {
            tell("Сьогодні вона вже брала ласощі від " + player.getName().getString());
        }
    }

    // Корови, що бачили вбивство, втрачають довіру до вбивці
    private void punishWitnesses(Player killer) {
        if (killer.isSpectator() || killer.hasEffect(MobEffects.INVISIBILITY)) return;
        double r = CowTrust.WITNESS_RADIUS;
        for (HungryCow other : this.level().getEntitiesOfClass(
                HungryCow.class, this.getBoundingBox().inflate(r), c -> c != this && c.isAlive())) {
            if (other.distanceToSqr(killer) <= r * r && other.hasLineOfSight(killer)) {
                other.addTrust(killer, -CowTrust.WITNESS_PENALTY, "бачила вбивство", true);
            }
        }
    }

    // ===================== ВОРОЖА КОРОВА =====================

    public void cancelCharge() {
        charging = false;
    }

    // Викликається з FearGoal щотіку для ворожого гравця.
    // true = корова зайнята атакою (не тікає), false = тікає як зазвичай.
    public boolean hostileBehavior(Player player) {
        int t = trustOf(player);
        if (charging && this.tickCount - chargeStartTick > CowTrust.CHARGE_TIMEOUT_TICKS) {
            charging = false;
            nextChargeTick = this.tickCount + CowTrust.attackInterval(t);
        }
        if (!charging) {
            boolean ready = t <= CowTrust.MIN || this.tickCount >= nextChargeTick;
            double range = CowTrust.CHARGE_RANGE;
            if (!ready || this.distanceToSqr(player) > range * range || !this.hasLineOfSight(player)) {
                return false;
            }
            charging = true;
            chargeStartTick = this.tickCount;
        }

        if (this.tickCount % 5 == 0 || this.getNavigation().isDone()) {
            this.getNavigation().moveTo(player, 1.5);
        }

        double reach = CowTrust.ATTACK_REACH;
        if (this.distanceToSqr(player) < reach * reach
                && this.tickCount - lastAttackTick >= CowTrust.ATTACK_COOLDOWN_TICKS) {
            attackPlayer(player);
            lastAttackTick = this.tickCount;
            if (t > CowTrust.MIN) {          // при -100 переслідує далі, інакше відскакує
                charging = false;
                nextChargeTick = this.tickCount + CowTrust.attackInterval(t);
            }
        }
        return true;
    }

    private void attackPlayer(Player player) {
        player.hurt(this.damageSources().mobAttack(this), CowTrust.ATTACK_DAMAGE);
        player.knockback(CowTrust.ATTACK_KNOCKBACK, this.getX() - player.getX(), this.getZ() - player.getZ());
        player.hurtMarked = true;
        this.playSound(SoundEvents.COW_HURT, 1.0F, 0.8F);
        tell("Корова вдарила " + player.getName().getString());
    }

    // ===================== ГОДУВАННЯ =====================

    // Годування з руки (ПКМ предметом із таблиці CowFood)
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Item item = stack.getItem();
        CowFood food = CowFood.get(item);
        if (food == null) {
            return super.mobInteract(player, hand);
        }
        // подавлена корова нічого не бере
        if (choking) {
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (!this.level().isClientSide) {
            // ласощі (що дають довіру) беруться завжди, іншу безпечну їжу сита здорова корова відкидає
            boolean treat = CowTrust.feedGain(item) > 0;
            boolean refuses = hunger == 0 && food.isSafe() && this.getHealth() >= this.getMaxHealth() && !treat;
            if (!refuses) {
                eatFood(food, item);
                rewardPlayer(player, item, food);
                if (item == Items.MELON || item == Items.PUMPKIN) {
                    tryStartLove(player);
                }
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                this.playSound(SoundEvents.GENERIC_EAT, 1.0F, 1.0F);
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    // Їжа з таблиці CowFood: з руки гравця або з землі
    public void eatFood(CowFood food, Item item) {
        // лічильники скидаються раз на ігрову добу
        long day = this.level().getDayTime() / 24000L;
        if (day != foodDay) {
            foodDay = day;
            foodCounts = new CompoundTag();
        }
        String key = food.group != null ? food.group : ForgeRegistries.ITEMS.getKey(item).toString();
        int count = foodCounts.getInt(key) + 1;
        foodCounts.putInt(key, count);

        hunger = Math.max(0, hunger - food.hunger);
        if (food.heal > 0) {
            this.heal(food.heal);
        }
        if (food.regenSeconds > 0) {
            this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, food.regenSeconds * 20));
        }
        if (food.firstRegenSeconds > 0 && count == 1) {
            this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, food.firstRegenSeconds * 20));
        }
        if (food.poisonSeconds > 0) {
            this.addEffect(new MobEffectInstance(MobEffects.POISON, food.poisonSeconds * 20));
        }
        if (food.limitFrom > 0 && count >= food.limitFrom) {
            this.addEffect(new MobEffectInstance(MobEffects.POISON, food.limitPoisonSeconds * 20));
        }
        if (food.slowFrom > 0 && count >= food.slowFrom) {
            this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, food.slowSeconds * 20));
        }
        if (food.dyingFrom > 0 && count >= food.dyingFrom) {
            dyingLeft = food.dyingSeconds;
        }
        if (food.selfDamage > 0) {
            this.hurt(this.damageSources().cactus(), food.selfDamage);
        }
        if (food.toxinSeconds > 0) {
            toxinLeft = food.toxinSeconds;
            toxinDamage = food.toxinDamage;
        }
        if (food.chokeChance > 0) {
            float chance = Math.min(1.0F, food.chokeChance * CHOKE_MULTIPLIER);
            if (this.getRandom().nextFloat() < chance) {
                startChoking();
            }
        }
    }

    // ===================== РОЗМНОЖЕННЯ =====================

    // Пшениця більше не розмножує: розмножує лише блок кавуна й гарбуз (див. mobInteract)
    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    // Блок кавуна або гарбуза: доросла корова входить у режим розмноження, теля швидше росте
    private void tryStartLove(Player player) {
        if (this.isBaby()) {
            this.ageUp(AgeableMob.getSpeedUpSecondsWhenFeeding(-this.getAge()), true);
        } else if (this.getAge() == 0 && this.canFallInLove()) {
            this.setInLove(player);
        }
    }

    // Теля теж буде нашою коровою (інакше народжується ванільна)
    @Override
    public Cow getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return ModEntities.HUNGRY_COW.get().create(level);
    }

    // Розмножуються лише корови різної статі, і не вагітні
    @Override
    public boolean canMate(Animal other) {
        return other instanceof HungryCow mate && super.canMate(other) && repro.canMateWith(mate);
    }

    // Вагітна корова не може знову увійти в режим розмноження
    @Override
    public boolean canFallInLove() {
        return super.canFallInLove() && !repro.isPregnant();
    }

    // Замість миттєвого теляти: після 15 секунд разом корова вагітніє (див. CowReproduction)
    @Override
    public void spawnChildFromBreeding(ServerLevel level, Animal partner) {
        if (partner instanceof HungryCow other) {
            repro.onBreeding(other);
        }
    }

    // ===================== СТРАХ =====================

    // Хто лякає корову
    public boolean isScary(LivingEntity e) {
        if (e instanceof Player p) return !p.isSpectator() && !p.hasEffect(MobEffects.INVISIBILITY);
        return e instanceof Zombie
                || e instanceof AbstractSkeleton
                || e instanceof Spider
                || e instanceof Creeper
                || e instanceof EnderMan
                || e instanceof Villager;
    }

    // Найближчий, хто лякає, у радіусі range (або null)
    public LivingEntity findScary(double range) {
        LivingEntity best = null;
        double bestDist = range * range;
        for (LivingEntity e : this.level().getEntitiesOfClass(
                LivingEntity.class, this.getBoundingBox().inflate(range), this::isScary)) {
            double d = this.distanceToSqr(e);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    // Скільки страху за секунду дають усі, хто поруч (береться найбільше)
    private int fearGainNearby() {
        int max = 0;
        for (LivingEntity e : this.level().getEntitiesOfClass(
                LivingEntity.class, this.getBoundingBox().inflate(10.0), this::isScary)) {
            // ворожого гравця корова помічає з 10 блоків, решту з 6
            double range = (e instanceof Player p && trustOf(p) < 0) ? 10.0 : 6.0;
            if (this.distanceToSqr(e) < range * range) {
                max = Math.max(max, fearFrom(e));
            }
        }
        return max;
    }

    private int fearFrom(LivingEntity e) {
        if (e instanceof Player player) {
            return fearFromPlayer(player);
        }
        return 10;
    }

    // Страх від гравця залежить від того, що він робить і яка довіра
    private int fearFromPlayer(Player player) {
        int t = trustOf(player);
        boolean running = player.isSprinting();
        boolean jumping = !player.onGround() && !player.getAbilities().flying;
        int base;
        if (running) {
            base = 40;
        } else if (jumping) {
            base = 35;
        } else if (player.isCrouching()) {
            base = 2;
        } else {
            base = 10;
        }

        if (t < 0) {
            return Math.max(base, CowTrust.HOSTILE_MIN_FEAR);   // ворожа корова завжди насторожена
        }
        if (t == 0) {
            return base;
        }
        // друг: від ходьби й крадіжки не лякається, від бігу й стрибків менше й менше
        if (!running && !jumping) {
            return 0;
        }
        return Math.round(base * CowTrust.fearMultiplier(t));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        Entity attacker = source.getEntity();
        if (result && !this.level().isClientSide && attacker != null) {
            if (attacker instanceof Player player) {
                lastPlayerHitTick = this.tickCount;
                if (choking) {
                    // удар гравця рятує подавлену корову: без штрафу довіри, страх невеликий
                    stopChoking();
                    fear = Math.min(100, fear + CURE_FEAR);
                } else {
                    int before = trustOf(player);
                    addTrust(player, -CowTrust.HIT_PENALTY, "удар", true);
                    fear = Math.min(100, fear + CowTrust.hitFear(before));
                }
            } else {
                // страх лише від ударів істот, а не від голоду чи отрути
                fear = Math.min(100, fear + 50);
            }
        }
        return result;
    }

    // діагностика: пише в чат, від чого померла корова; інші корови можуть бачити вбивство
    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide) {
            Component msg = Component.literal("Корова померла від: " + source.getMsgId()
                    + " | голод " + hunger + " | втома " + fatigue + " | страх " + fear);
            this.level().players().forEach(p -> p.sendSystemMessage(msg));
            if (source.getEntity() instanceof Player killer) {
                punishWitnesses(killer);
            }
        }
        super.die(source);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Hunger", hunger);
        tag.putInt("Fatigue", fatigue);
        tag.putInt("Fear", fear);
        tag.putBoolean("Choking", choking);
        tag.put("FoodCounts", foodCounts);
        tag.putLong("FoodDay", foodDay);
        tag.putInt("ToxinLeft", toxinLeft);
        tag.putFloat("ToxinDamage", toxinDamage);
        tag.putInt("DyingLeft", dyingLeft);
        tag.put("Trust", trust.save());
        repro.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        hunger = tag.getInt("Hunger");
        fatigue = tag.getInt("Fatigue");
        fear = tag.getInt("Fear");
        choking = tag.getBoolean("Choking");
        foodCounts = tag.getCompound("FoodCounts");
        foodDay = tag.contains("FoodDay") ? tag.getLong("FoodDay") : -1;
        toxinLeft = tag.getInt("ToxinLeft");
        toxinDamage = tag.getFloat("ToxinDamage");
        dyingLeft = tag.getInt("DyingLeft");
        trust.load(tag.getList("Trust", Tag.TAG_COMPOUND));
        repro.load(tag);
    }
}

class FearGoal extends Goal {
    private static final int SCARED_AT = 10;
    private static final int CALM_AT = 5;
    private final HungryCow cow;
    private LivingEntity scary;

    FearGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    // викликати tick() кожного тіку, а не раз на два
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (cow.fear < SCARED_AT) return false;
        scary = cow.findScary(10.0);
        return scary != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (cow.fear < CALM_AT) return false;
        scary = cow.findScary(14.0);
        return scary != null;
    }

    @Override
    public void start() {
        if (scary instanceof Player player) {
            cow.onScaredBy(player);
        }
    }

    @Override
    public void tick() {
        if (scary == null) return;
        // ворожа корова (довіра менша за 0) інколи атакує замість того, щоб тікати
        if (scary instanceof Player player && cow.trustOf(player) < 0 && cow.hostileBehavior(player)) {
            return;
        }
        if (cow.tickCount % 10 == 0 || cow.getNavigation().isDone()) {
            double speed = 1.0 + cow.fear / 100.0 * 1.2;
            Vec3 away = DefaultRandomPos.getPosAway(cow, 10, 4, scary.position());
            if (away != null) {
                cow.getNavigation().moveTo(away.x, away.y, away.z, speed);
            }
        }
    }

    @Override
    public void stop() {
        scary = null;
        cow.cancelCharge();
        cow.getNavigation().stop();
    }
}

class RestGoal extends Goal {
    private static final int TIRED_AT = 40;           // з якої втоми корова йде відпочивати
    private static final int RECOVER_PER_SECOND = 5;  // на скільки зменшується втома за секунду відпочинку
    private static final int MAX_REST_TICKS = 400;    // максимум відпочинку за раз (20 сек)
    private final HungryCow cow;
    private int restTimer = 0;

    RestGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    // викликати tick() кожного тіку, а не раз на два
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    // втомлена корова з режимом розмноження не відпочиває: втома не заважає розмноженню
    @Override
    public boolean canUse() {
        return cow.fatigue >= TIRED_AT && !cow.isInLove();
    }

    @Override
    public boolean canContinueToUse() {
        return cow.fatigue > 0 && restTimer < MAX_REST_TICKS && !cow.isInLove();
    }

    @Override
    public void start() {
        restTimer = 0;
        cow.getNavigation().stop();
    }

    // втома зменшується поступово, тож перерваний відпочинок не пропадає
    @Override
    public void tick() {
        restTimer++;
        if (restTimer % 20 == 0) {
            cow.fatigue = Math.max(0, cow.fatigue - RECOVER_PER_SECOND);
        }
    }

    @Override
    public void stop() {
        restTimer = 0;
    }
}

// Корова відходить від черемші (Azure Bluet)
class AvoidBluetGoal extends Goal {
    private static final int RADIUS = 3;
    private final HungryCow cow;
    private BlockPos bluet;
    private int cooldown = 0;

    AvoidBluetGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        cooldown = 20;
        bluet = findBluet();
        return bluet != null;
    }

    @Override
    public boolean canContinueToUse() {
        return bluet != null && !cow.getNavigation().isDone();
    }

    @Override
    public void start() {
        Vec3 away = DefaultRandomPos.getPosAway(cow, 8, 3, Vec3.atCenterOf(bluet));
        if (away != null) {
            cow.getNavigation().moveTo(away.x, away.y, away.z, 1.0);
        } else {
            bluet = null;
        }
    }

    @Override
    public void stop() {
        bluet = null;
    }

    private BlockPos findBluet() {
        BlockPos origin = cow.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(
                origin.offset(-RADIUS, -1, -RADIUS), origin.offset(RADIUS, 1, RADIUS))) {
            if (cow.level().getBlockState(p).is(Blocks.AZURE_BLUET)) {
                return p.immutable();
            }
        }
        return null;
    }
}

// Корова з довірою від +50 сама підходить до гравця, коли спокійна
class ApproachPlayerGoal extends Goal {
    private static final double SEE_RANGE = 12.0;     // з якої відстані помічає друга
    private static final double STOP_DISTANCE = 3.0;  // на скільки блоків підходить
    private static final int STAY_TICKS = 200;        // скільки стоїть поруч (10 сек)
    private static final int PAUSE_TICKS = 400;       // пауза перед наступним підходом (20 сек)
    private final HungryCow cow;
    private Player player;
    private int stayTicks = 0;
    private int pauseUntil = 0;

    ApproachPlayerGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    // викликати tick() кожного тіку, а не раз на два
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    // корова спокійна: не голодна, не втомлена, не подавилась
    private boolean calm() {
        return cow.hunger < 30 && cow.fatigue < 40 && !cow.choking;
    }

    @Override
    public boolean canUse() {
        if (cow.tickCount < pauseUntil || !calm()) return false;
        player = findFriend();
        return player != null;
    }

    @Override
    public boolean canContinueToUse() {
        return calm()
                && player != null
                && player.isAlive()
                && cow.trustOf(player) >= CowTrust.FRIEND_FROM
                && cow.distanceToSqr(player) < 16.0 * 16.0
                && stayTicks < STAY_TICKS;
    }

    @Override
    public void start() {
        stayTicks = 0;
    }

    @Override
    public void tick() {
        if (player == null) return;
        cow.getLookControl().setLookAt(player, 30.0F, 30.0F);
        if (cow.distanceToSqr(player) > STOP_DISTANCE * STOP_DISTANCE) {
            if (cow.tickCount % 10 == 0) {
                cow.getNavigation().moveTo(player, 1.0);
            }
        } else {
            cow.getNavigation().stop();
            stayTicks++;
        }
    }

    @Override
    public void stop() {
        player = null;
        cow.getNavigation().stop();
        stayTicks = 0;
        pauseUntil = cow.tickCount + PAUSE_TICKS;
    }

    // Найближчий видимий гравець, якому корова довіряє
    private Player findFriend() {
        Player best = null;
        double bestDist = SEE_RANGE * SEE_RANGE;
        for (Player p : cow.level().players()) {
            if (p.isSpectator() || p.hasEffect(MobEffects.INVISIBILITY)) continue;
            if (cow.trustOf(p) < CowTrust.FRIEND_FROM) continue;
            double d = cow.distanceToSqr(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }
}

class SeekGrassGoal extends Goal {
    private static final int HUNGRY_AT = 30;
    private static final int SEARCH_RADIUS = 15;          // радіус пошуку їжі по горизонталі
    private static final int SEARCH_HEIGHT = 3;           // радіус пошуку по висоті
    private static final float FLOWER_CHANCE = 0.30F;     // шанс, що голодна корова піде до квітки замість звичайної їжі
    private static final int PLACED_BONUS = 30;           // бонус привабливості блоків, поставлених гравцем
    private static final int PLACED_EAT_TICKS = 200;      // скільки їсть такий блок (10 сек)
    private static final double DISTANCE_PENALTY = 2.0;   // скільки поживності «коштує» кожен блок шляху
    private static final int MAX_RUN_TICKS = 400;         // скільки максимум йти до однієї цілі (20 сек)
    private static final int MAX_PATH_CHECKS = 6;         // скільки шляхів перевіряти за один вибір
    private static final int MAX_REACHABLE_COMPARED = 3;  // скільки досяжних варіантів порівнювати
    private static final boolean DEBUG = true;            // писати в чат, коли їжа недосяжна

    // Апетит до квіток: чим більше число, тим частіше корова обирає цей вид
    private static final java.util.Map<Block, Integer> FLOWER_APPETITE = new java.util.HashMap<>();

    static {
        FLOWER_APPETITE.put(Blocks.DANDELION, 10);    // кульбаба: найчастіше
        FLOWER_APPETITE.put(Blocks.ALLIUM, 4);        // різанець
        FLOWER_APPETITE.put(Blocks.OXEYE_DAISY, 4);   // королиця
        FLOWER_APPETITE.put(Blocks.SUNFLOWER, 4);
        FLOWER_APPETITE.put(Blocks.POPPY, 4);         // мак
        FLOWER_APPETITE.put(Blocks.CORNFLOWER, 3);
        FLOWER_APPETITE.put(Blocks.RED_TULIP, 2);
        FLOWER_APPETITE.put(Blocks.ORANGE_TULIP, 2);
        FLOWER_APPETITE.put(Blocks.WHITE_TULIP, 2);
        FLOWER_APPETITE.put(Blocks.PINK_TULIP, 2);
        FLOWER_APPETITE.put(Blocks.PEONY, 2);
        FLOWER_APPETITE.put(Blocks.LILAC, 2);
        FLOWER_APPETITE.put(Blocks.FERN, 1);
        FLOWER_APPETITE.put(Blocks.LARGE_FERN, 1);
    }

    // Що це за їжа на землі
    private enum Kind { NONE, PLACED, PLANT, GRASS_BLOCK, MOSS_BLOCK, FLOWER, DESPERATE, BLUET }

    private static class Candidate {
        final BlockPos pos;
        final Kind kind;
        final double dist;   // квадрат відстані по прямій
        final int value;     // скільки голоду знімає
        final Block block;

        Candidate(BlockPos pos, Kind kind, double dist, int value, Block block) {
            this.pos = pos;
            this.kind = kind;
            this.dist = dist;
            this.value = value;
            this.block = block;
        }
    }

    private final HungryCow cow;
    private BlockPos target;
    private int cooldown = 0;
    private int stuckTicks = 0;
    private int runTicks = 0;
    private int eatTicks = 0;
    private double lastDist = 0;
    private int checksLeft = 0;
    private int lastClearTick = 0;
    private final Set<BlockPos> unreachable = new HashSet<>();

    SeekGrassGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    // викликати tick() кожного тіку, а не раз на два (інакше половина корів не йде до їжі)
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (cow.choking) return false;   // подавлена корова не шукає їжі
        if (cow.hunger < HUNGRY_AT) return false;
        // раз на 10 секунд забуваємо, що вважали недосяжним
        if (cow.tickCount - lastClearTick > 200) {
            unreachable.clear();
            lastClearTick = cow.tickCount;
        }
        if (cooldown > 0) { cooldown--; return false; }
        cooldown = 10;
        target = findFood();
        stuckTicks = 0;
        runTicks = 0;
        eatTicks = 0;
        if (target != null) {
            lastDist = cow.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        }
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !cow.choking && cow.hunger >= HUNGRY_AT && target != null;
    }

    private void resetEating() {
        eatTicks = 0;
        cow.eatingTicksLeft = 0;
    }

    @Override
    public void tick() {
        if (target == null) return; // tick() може викликатися ще раз, коли ціль уже з'їдена
        BlockState state = cow.level().getBlockState(target);
        Kind kind = classify(state);
        if (kind == Kind.NONE) {
            target = null; // їжу вже хтось прибрав
            resetEating();
            return;
        }
        Block block = state.getBlock();

        double x = target.getX() + 0.5;
        double y = target.getY() + ((block == Blocks.GRASS_BLOCK || block == Blocks.MOSS_BLOCK) ? 1 : 0);
        double z = target.getZ() + 0.5;

        if (cow.distanceToSqr(x, y, z) < 4.0) {
            if (kind == Kind.PLACED) {
                // блок, поставлений гравцем, корова їсть 10 секунд
                cow.getNavigation().stop();
                cow.getLookControl().setLookAt(x, target.getY() + 0.5, z);
                eatTicks++;
                cow.eatingTicksLeft = PLACED_EAT_TICKS - eatTicks;
                if (eatTicks % 20 == 0) {
                    cow.playSound(SoundEvents.GENERIC_EAT, 0.8F, 1.0F);
                }
                if (eatTicks < PLACED_EAT_TICKS) return;
            }
            eatTarget(state);
            unreachable.clear();
            target = null;
            resetEating();
            return;
        }

        resetEating();   // відійшла від їжі: відлік починається знову
        runTicks++;

        // перший рух одразу, далі кожні 10 тіків
        if (runTicks == 1 || runTicks % 10 == 0) {
            cow.getNavigation().moveTo(x, y, z, 1.0);
        }

        // кожні 20 тіків перевіряємо, чи корова справді наближається до цілі
        if (runTicks % 20 == 0) {
            double d = cow.distanceToSqr(x, y, z);
            if (d > lastDist - 1.0) {
                stuckTicks += 20;   // не наблизилась
            } else {
                stuckTicks = 0;
            }
            lastDist = d;
        }

        if (stuckTicks >= 60 || runTicks > MAX_RUN_TICKS) {
            if (DEBUG) {
                Component msg = Component.literal("Недосяжна їжа: " + target.getX() + " "
                        + target.getY() + " " + target.getZ() + " | голод " + cow.hunger);
                cow.level().players().forEach(p -> p.sendSystemMessage(msg));
            }
            unreachable.add(target);
            if (unreachable.size() > 500) unreachable.clear();
            target = null;
        }
    }

    @Override
    public void stop() {
        target = null;
        stuckTicks = 0;
        runTicks = 0;
        resetEating();
    }

    private void eatTarget(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.GRASS_BLOCK) {
            cow.level().setBlockAndUpdate(target, Blocks.DIRT.defaultBlockState());
            cow.hunger = Math.max(0, cow.hunger - HungryCow.GRASS_BLOCK_FOOD);
            cow.heal(HungryCow.GRASS_BLOCK_HEAL);
        } else if (block == Blocks.MOSS_BLOCK) {
            Block[] soils = { Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT };
            Block soil = soils[cow.getRandom().nextInt(soils.length)];
            cow.level().setBlockAndUpdate(target, soil.defaultBlockState());
            cow.hunger = Math.max(0, cow.hunger - HungryCow.MOSS_BLOCK_FOOD);
        } else if (block == Blocks.GRASS || block == Blocks.TALL_GRASS) {
            cow.level().destroyBlock(target, false);
            cow.hunger = Math.max(0, cow.hunger - HungryCow.PLANT_FOOD);
            cow.heal(HungryCow.PLANT_HEAL);
        } else {
            // кавун, гарбуз, сіно, квіти: за таблицею CowFood
            Item item = block.asItem();
            CowFood food = CowFood.get(item);
            cow.level().destroyBlock(target, false);
            if (food != null) {
                cow.eatFood(food, item);
            }
        }
        Component msg = Component.literal("Корова з'їла: ").append(block.getName());
        cow.level().players().forEach(p -> p.sendSystemMessage(msg));
    }

    private Kind classify(BlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.MELON || b == Blocks.PUMPKIN || b == Blocks.HAY_BLOCK) return Kind.PLACED;
        if (b == Blocks.GRASS || b == Blocks.TALL_GRASS) return Kind.PLANT;
        if (b == Blocks.GRASS_BLOCK) return Kind.GRASS_BLOCK;
        if (b == Blocks.MOSS_BLOCK) return Kind.MOSS_BLOCK;
        if (b == Blocks.AZURE_BLUET) return Kind.BLUET;
        if (FLOWER_APPETITE.containsKey(b)) return Kind.FLOWER;
        CowFood f = CowFood.get(b.asItem());
        if (f == null) return Kind.NONE;
        if (f.ground == CowFood.Ground.SAFE) return Kind.PLANT;       // світлолишайник, моховий покрив
        if (f.ground == CowFood.Ground.DESPERATE) return Kind.DESPERATE;
        return Kind.NONE;
    }

    // Скільки голоду знімає ця їжа
    private int nutrition(BlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.GRASS || b == Blocks.TALL_GRASS) return HungryCow.PLANT_FOOD;
        if (b == Blocks.GRASS_BLOCK) return HungryCow.GRASS_BLOCK_FOOD;
        if (b == Blocks.MOSS_BLOCK) return HungryCow.MOSS_BLOCK_FOOD;
        CowFood f = CowFood.get(b.asItem());
        return f != null ? f.hunger : 10;
    }

    // Привабливість їжі: поживність (і бонус за блок гравця) мінус довжина шляху
    private double score(Candidate c, double distance) {
        double bonus = c.kind == Kind.PLACED ? PLACED_BONUS : 0;
        return c.value + bonus - DISTANCE_PENALTY * distance;
    }

    // Шлях до їжі (null, якщо дійти не можна). Кожна перевірка зменшує ліміт.
    private net.minecraft.world.level.pathfinder.Path pathTo(Candidate c) {
        checksLeft--;
        BlockPos stand = (c.kind == Kind.GRASS_BLOCK || c.kind == Kind.MOSS_BLOCK) ? c.pos.above() : c.pos;
        net.minecraft.world.level.pathfinder.Path path = cow.getNavigation().createPath(stand, 1);
        return (path != null && path.canReach()) ? path : null;
    }

    // Недосяжна їжа і вся їжа поруч (до 4 блоків) теж вважається недосяжною
    private void markUnreachable(Candidate c, java.util.List<Candidate> list) {
        unreachable.add(c.pos);
        for (Candidate other : list) {
            if (other.pos.distSqr(c.pos) < 16) unreachable.add(other.pos);
        }
        if (unreachable.size() > 500) unreachable.clear();
    }

    // Найближча досяжна їжа зі списку
    private BlockPos firstReachable(java.util.List<Candidate> list) {
        list.sort(java.util.Comparator.comparingDouble((Candidate c) -> c.dist));
        for (Candidate c : list) {
            if (unreachable.contains(c.pos)) continue;
            if (checksLeft <= 0) return null;
            if (pathTo(c) != null) return c.pos;
            markUnreachable(c, list);
        }
        return null;
    }

    // Звичайна їжа: вигода = поживність мінус довжина шляху
    private BlockPos chooseNormal(java.util.List<Candidate> list, boolean emergency) {
        if (list.isEmpty()) return null;
        if (emergency) return firstReachable(list); // голодує: найближча досяжна

        list.sort(java.util.Comparator.comparingDouble(
                (Candidate c) -> -score(c, Math.sqrt(c.dist))));
        BlockPos best = null;
        double bestScore = -Double.MAX_VALUE;
        int reachableFound = 0;
        for (Candidate c : list) {
            if (unreachable.contains(c.pos)) continue;
            if (checksLeft <= 0 || reachableFound >= MAX_REACHABLE_COMPARED) break;
            net.minecraft.world.level.pathfinder.Path path = pathTo(c);
            if (path == null) {
                markUnreachable(c, list);
                continue;
            }
            reachableFound++;
            double s = score(c, path.getNodeCount());
            if (s > bestScore) {
                bestScore = s;
                best = c.pos;
            }
        }
        return best;
    }

    // Вибирає вид квітки за апетитом (кульбаба частіше за мак), потім найближчу досяжну квітку цього виду
    private BlockPos pickFlower(java.util.List<Candidate> flowers) {
        java.util.Map<Block, java.util.List<Candidate>> byType = new java.util.HashMap<>();
        for (Candidate c : flowers) {
            byType.computeIfAbsent(c.block, k -> new java.util.ArrayList<>()).add(c);
        }
        while (!byType.isEmpty() && checksLeft > 0) {
            int total = 0;
            for (Block b : byType.keySet()) {
                total += FLOWER_APPETITE.getOrDefault(b, 1);
            }
            int roll = cow.getRandom().nextInt(total);
            Block chosen = null;
            for (Block b : byType.keySet()) {
                roll -= FLOWER_APPETITE.getOrDefault(b, 1);
                if (roll < 0) {
                    chosen = b;
                    break;
                }
            }
            if (chosen == null) break;
            BlockPos p = firstReachable(byType.get(chosen));
            if (p != null) return p;
            byType.remove(chosen);
        }
        return null;
    }

    // Вибирає, куди йти їсти
    private BlockPos findFood() {
        BlockPos origin = cow.blockPosition();
        java.util.List<Candidate> normal = new java.util.ArrayList<>();
        java.util.List<Candidate> flowers = new java.util.ArrayList<>();
        java.util.List<Candidate> desperate = new java.util.ArrayList<>();
        java.util.List<Candidate> bluet = new java.util.ArrayList<>();
        int grassBlocks = 0;

        for (BlockPos p : BlockPos.betweenClosed(
                origin.offset(-SEARCH_RADIUS, -SEARCH_HEIGHT, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, SEARCH_HEIGHT, SEARCH_RADIUS))) {
            if (!cow.level().hasChunkAt(p)) continue;
            BlockState state = cow.level().getBlockState(p);
            if (state.isAir()) continue;
            Kind k = classify(state);
            if (k == Kind.NONE) continue;
            if (k == Kind.GRASS_BLOCK) grassBlocks++;
            if (unreachable.contains(p)) continue;
            Candidate c = new Candidate(p.immutable(), k, p.distSqr(origin), nutrition(state), state.getBlock());
            if (k == Kind.FLOWER) flowers.add(c);
            else if (k == Kind.DESPERATE) desperate.add(c);
            else if (k == Kind.BLUET) bluet.add(c);
            else normal.add(c);
        }

        checksLeft = MAX_PATH_CHECKS;
        boolean emergency = cow.hunger >= HungryCow.STARVE_AT;

        // 1) іноді корові хочеться квітки: кульбаба частіше за мак
        if (!emergency && !flowers.isEmpty() && cow.getRandom().nextFloat() < FLOWER_CHANCE) {
            BlockPos p = pickFlower(flowers);
            if (p != null) return p;
        }
        // 2) звичайна їжа (трава, трав'яний і моховий блок, блоки гравця)
        BlockPos best = chooseNormal(normal, emergency);
        if (best != null) return best;
        // ще є їжа, яку не встигли перевірити: спробуємо наступного разу
        for (Candidate c : normal) {
            if (!unreachable.contains(c.pos)) return null;
        }
        // 3) звичайної їжі немає: безпечні квіти
        java.util.List<Candidate> safeFlowers = new java.util.ArrayList<>();
        for (Candidate c : flowers) {
            CowFood f = CowFood.get(c.block.asItem());
            if (f != null && f.ground == CowFood.Ground.SAFE) safeFlowers.add(c);
        }
        if (!safeFlowers.isEmpty()) {
            BlockPos p = pickFlower(safeFlowers);
            if (p != null) return p;
        }
        // 4) черемша, лише коли трави майже немає
        if (grassBlocks < 5) {
            BlockPos p = firstReachable(bluet);
            if (p != null) return p;
        }
        // 5) група В: лише коли більше нічого немає
        return firstReachable(desperate);
    }
}