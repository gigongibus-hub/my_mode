package com.gigongibus.hungrycows;

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
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import com.gigongibus.hungrycows.ai.ApproachPlayerGoal;
import com.gigongibus.hungrycows.ai.AvoidBluetGoal;
import com.gigongibus.hungrycows.ai.FearGoal;
import com.gigongibus.hungrycows.ai.LaborGoal;
import com.gigongibus.hungrycows.ai.RestGoal;
import com.gigongibus.hungrycows.ai.SeekGrassGoal;

import java.util.stream.Collectors;

public class HungryCow extends Cow {
    public static final int TIME_SPEED = 1;              // у скільки разів швидше росте голод і втома (1 = звичайно)
    public static final int HUNGER_EVERY_SECONDS = 7;    // голод росте на 1 раз на стільки секунд (було 10) (для швидких тестів постав 1)
    public static final int FATIGUE_EVERY_SECONDS = 10;  // втома росте на 1 раз на стільки секунд
    public static final int STARVE_AT = 60;              // з цього голоду корова отримує урон
    public static final int GRASS_BLOCK_FOOD = 30;       // трав'яний блок: скільки голоду знімає
    public static final float GRASS_BLOCK_HEAL = 2.0F;   // трав'яний блок: скільки лікує
    public static final int PLANT_FOOD = 50;             // трава-рослина: скільки голоду знімає
    public static final float PLANT_HEAL = 4.0F;         // трава-рослина: скільки лікує
    public static final int MOSS_BLOCK_FOOD = 10;        // моховий блок: скільки голоду знімає

    public static final float CHOKE_MULTIPLIER = 100.0F; // множник шансу подавитися: 100 = завжди (для тестів), 1 = звичайно
    public static final float CHOKE_TEST_CHANCE = 0.5F;  // тест: однаковий шанс подавитися для всієї такої їжі (0.5 = 50%);
                                                         // 0 = шанс із CowFood × CHOKE_MULTIPLIER
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

    // Усі цілі задані тут явно (без super.registerGoals()), кожна зі своїм пріоритетом:
    // менше число = важливіше. Ванільні цілі корови теж тут, але змінені під наш мод.
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));                 // пливти, щоб не втопитися
        this.goalSelector.addGoal(1, new LaborGoal(this));                 // пологи: стоїть на місці
        this.goalSelector.addGoal(2, new FearGoal(this));                  // страх і напади ворожої корови
        this.goalSelector.addGoal(3, new FirePanicGoal(this));             // паніка лише від вогню й морозу
        this.goalSelector.addGoal(4, new SeekGrassGoal(this));             // пошук їжі
        this.goalSelector.addGoal(5, new BreedGoal(this, 1.0));            // розмноження
        this.goalSelector.addGoal(6, new RestGoal(this));                  // відпочинок
        this.goalSelector.addGoal(7, new TemptGoal(this, 1.25, TEMPT_ITEMS, false)); // йде за ласощами
        this.goalSelector.addGoal(8, new AvoidBluetGoal(this));            // відходить від черемші
        this.goalSelector.addGoal(9, new ApproachPlayerGoal(this));        // підходить до друга
        this.goalSelector.addGoal(10, new FollowParentGoal(this, 1.25));   // теля йде за матір'ю
        this.goalSelector.addGoal(11, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(12, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(13, new RandomLookAroundGoal(this));
    }

    // За чим корова йде (замість пшениці, яку вона більше не їсть): ласощі, що дають довіру
    private static final Ingredient TEMPT_ITEMS = Ingredient.of(
            Items.MELON, Items.PUMPKIN, Items.MELON_SLICE, Items.APPLE, Items.CARROT);

    // Ванільна паніка спрацьовує й від ударів, а це вже робить FearGoal.
    // Тут лишається тільки втеча від вогню й замерзання.
    private static class FirePanicGoal extends PanicGoal {
        FirePanicGoal(HungryCow cow) {
            super(cow, 2.0);
        }

        @Override
        protected boolean shouldPanic() {
            return this.mob.isFreezing() || this.mob.isOnFire();
        }
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
        // у творчому режимі й на «Мирній» складності удар не проходить, тоді й не відкидаємо
        if (!player.hurt(this.damageSources().mobAttack(this), CowTrust.ATTACK_DAMAGE)) return;
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
            float chance = CHOKE_TEST_CHANCE > 0
                    ? CHOKE_TEST_CHANCE
                    : Math.min(1.0F, food.chokeChance * CHOKE_MULTIPLIER);
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

    // Хто лякає корову найбільше в радіусі range (або null).
    // Ворожий гравець (довіра < 0) у межах кидка важливіший за будь-кого,
    // інакше зомбі поруч заважав би корові напасти на кривдника.
    public LivingEntity findScary(double range) {
        LivingEntity best = null;
        double bestDist = range * range;
        Player enemy = null;
        double enemyDist = CowTrust.CHARGE_RANGE * CowTrust.CHARGE_RANGE;
        for (LivingEntity e : this.level().getEntitiesOfClass(
                LivingEntity.class, this.getBoundingBox().inflate(range), this::isScary)) {
            double d = this.distanceToSqr(e);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
            if (e instanceof Player p && d < enemyDist && trustOf(p) < 0) {
                enemyDist = d;
                enemy = p;
            }
        }
        return enemy != null ? enemy : best;
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
