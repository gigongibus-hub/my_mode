package com.gigongibus.hungrycows;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import net.minecraft.world.entity.ai.goal.FloatGoal;
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
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Items;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import com.gigongibus.hungrycows.CowCharacter.Experience;
import com.gigongibus.hungrycows.CowCharacter.Trait;
import com.gigongibus.hungrycows.ai.GrieveGoal;
import java.util.EnumMap;
import java.util.UUID;
import net.minecraftforge.registries.ForgeRegistries;
import com.gigongibus.hungrycows.ai.ApproachPlayerGoal;
import com.gigongibus.hungrycows.ai.BullCourtGoal;
import com.gigongibus.hungrycows.ai.HerdUpGoal;
import com.gigongibus.hungrycows.ai.MateGoal;
import com.gigongibus.hungrycows.ai.DefendCalfGoal;
import com.gigongibus.hungrycows.ai.DrinkMilkGoal;
import com.gigongibus.hungrycows.ai.FindFosterGoal;
import com.gigongibus.hungrycows.ai.FollowMotherGoal;
import com.gigongibus.hungrycows.ai.NurseGoal;
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
    // ================= ОБМІН РЕЧОВИН І ВТОМА (6б) =================
    public static final double PREGNANT_HUNGER = 0.2;    // вагітна голодніє на 20% швидше
    public static final double LACTATING_HUNGER = 0.3;   // дійна (є молоко) — на 30% швидше (разом з вагітністю +50%)
    public static final double RUN_HUNGER = 2.0;         // бігає (тікає, гасає в тічку) — голодніє вдвічі швидше
    public static final double REST_HUNGER = 0.5;        // відпочиває — удвічі повільніше
    public static final double RUN_FATIGUE = 2.0;        // бігає — втомлюється вдвічі швидше
    public static final double RUN_BLOCKS_PER_SECOND = 3.5; // «бігає» = за секунду пройшла більше стількох блоків
    public static final int TIRED_AT = 40;               // з якої відчутої втоми корова йде відпочивати
    public static final int EXHAUSTED_AT = 100;          // з такої справжньої втоми відпочиває за будь-якого відчуття

    public static final int CALF_GROW_DAYS = 100;        // за скільки діб виростає теля
    public static final int CALF_GROW_TICKS = CALF_GROW_DAYS * 24000; // вік теляти в тіках (росте на 1 за тік)
    public static final int HUNGRY_AT = 30;              // з цього голоду корова шукає їжу (ген HUNGER_THRESHOLD ±30%)
    public static final int STARVE_DAMAGE_SECONDS = 4;   // при голодуванні урон раз на стільки секунд (ген STARVE_RESIST ±30%)
    public static final int STARVE_AT = 60;              // з цього голоду корова отримує урон
    public static final int GRASS_BLOCK_FOOD = 30;       // трав'яний блок: скільки голоду знімає
    public static final float GRASS_BLOCK_HEAL = 2.0F;   // трав'яний блок: скільки лікує
    public static final int PLANT_FOOD = 50;             // трава-рослина: скільки голоду знімає
    public static final float PLANT_HEAL = 4.0F;         // трава-рослина: скільки лікує
    public static final int TALL_PLANT_FOOD = 65;        // висока трава: трохи більше за низьку
    public static final float TALL_PLANT_HEAL = 5.0F;    // висока трава: скільки лікує
    public static final int MOSS_BLOCK_FOOD = 10;        // моховий блок: скільки голоду знімає

    public static final float CHOKE_MULTIPLIER = 100.0F; // множник шансу подавитися: 100 = завжди (для тестів), 1 = звичайно
    public static final float CHOKE_TEST_CHANCE = 0.5F;  // тест: однаковий шанс подавитися для всієї такої їжі (0.5 = 50%);
                                                         // 0 = шанс із CowFood × CHOKE_MULTIPLIER
    public static final float CHOKE_DAMAGE = 1.0F;       // урон від подавлення кожні 4 секунди
    public static final int CHOKE_SLOWNESS = 1;          // сповільнення: 0 = I, 1 = II
    public static final int CURE_FEAR = 10;              // скільки страху, якщо вдарити подавлену корову
    public static final int CALF_BUCKET_FOOD = 175;      // скільки голоду теляти знімає відро молока з рук гравця (≈25 одиниць)

    // ================= ЗАХИСТ ТЕЛЯТИ =================
    public static final int DEFEND_TICKS = 140;          // скільки корови б'ють того, хто вдарив теля (7 сек)
    public static final double DEFEND_RADIUS = 10.0;     // з якої відстані інші корови заступаються
    // шанс, що інша доросла корова заступиться (мати завжди), залежить від материнського інстинкту: див. DEFEND_CHANCE_MIN/MAX
    public static final float MOTHER_DAMAGE_MULTIPLIER = 2.0F; // мати б'є вдвічі сильніше
    public static final int DEFEND_TRUST_MOTHER = 30;    // скільки довіри втрачає мати
    public static final int DEFEND_TRUST_OTHERS = 10;    // скільки довіри втрачають інші
    public static final int FEAR_AFTER_DEFEND = 60;      // страх після захисту: корова тікає

    // ================= ДРУГ КОРОВИ =================
    public static final double FRIEND_EFFECT_RANGE = 8.0;    // з якої відстані корова з довірою 200 дає ефект
    public static final int FRIEND_EFFECT_TICKS = 6000;      // скільки діє ефект після того, як гравець відійшов (5 хв)

    // ================= ХАРАКТЕР: як риси змінюють поведінку =================
    public static final int SCARED_AT_CALM = 15;         // з якого страху тікає найменш полохлива (полохливість 0)
    public static final int SCARED_AT_TIMID = 5;         // з якого страху тікає найполохливіша (полохливість 100)
    public static final int FEAR_DECAY_MIN = 1;          // на скільки за секунду спадає страх: сміливість 0 ...
    public static final int FEAR_DECAY_MAX = 3;          // ... і сміливість 100 (при 50 — 2, як було)
    public static final double TRUST_GAIN_MIN = 0.5;     // множник росту довіри: прив'язаність до людей 0 ...
    public static final double TRUST_GAIN_MAX = 1.5;     // ... і 100 (при 50 — ×1)
    public static final double CHARGE_PAUSE_MAX = 1.5;   // пауза між нападами ворожої корови: агресивність 0 — ×1.5 ...
    public static final double CHARGE_PAUSE_MIN = 0.5;   // ... агресивність 100 — ×0.5
    public static final float DEFEND_CHANCE_MIN = 0.2F;  // шанс заступитися за чуже теля: материнський інстинкт 0 ...
    public static final float DEFEND_CHANCE_MAX = 0.8F;  // ... і 100 (при 50 — 50%, як було)
    public static final double PAIN_FEAR_MIN = 0.5;      // страх від удару: чутливість до болю 0 — ×0.5 ...
    public static final double PAIN_FEAR_MAX = 1.5;      // ... і 100 — ×1.5

    // ================= ДОСВІД: паузи, щоб повтори не накопичувались =================
    public static final int HIT_EXPERIENCE_COOLDOWN = 20;      // удар гравця: не частіше раз на 1 с
    public static final int SAW_HIT_EXPERIENCE_COOLDOWN = 200; // бачила удар іншої корови: раз на 10 с
    public static final int EXPLOSION_EXPERIENCE_COOLDOWN = 100; // вибух поруч: раз на 5 с
    public static final int FIRE_EXPERIENCE_COOLDOWN = 1200;   // вогонь поруч: раз на хвилину
    public static final int FIRE_CHECK_SECONDS = 5;            // як часто дивитись, чи щось горить поруч
    public static final int FIRE_RADIUS = 4;                   // на скільки блоків навколо

    // ================= ТІЛО (гени) =================
    public static final float SIZE_MIN = 0.85F;          // розмір: ген 0 → 85% звичайного ...
    public static final float SIZE_MAX = 1.15F;          // ... ген 100 → 115%
    public static final double SIZE_HEALTH = 1.0;        // більша на 15% → здоров'я +15% (×1.0 від різниці розміру)
    public static final double SIZE_SLOWDOWN = 0.5;      // більша на 15% → повільніша на 7.5% (×0.5 від різниці)
    public static final double KNOCKBACK_RESIST_MAX = 0.3; // ген «стійкість до відкидання» 100 → 30% стійкості (нижче 50 — 0)
    private static final UUID HEALTH_GENE_ID = UUID.fromString("3b0f6f7e-0c55-4a8e-8f61-6f3c8f0a9b21");
    private static final UUID SPEED_GENE_ID = UUID.fromString("a8f3d2c4-5e71-4b0a-9c2d-1e6f7a8b9c30");
    private static final UUID KNOCKBACK_GENE_ID = UUID.fromString("c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f");

    public static final boolean DEBUG_MESSAGES = true;   // писати в чат про подавлення, довіру, напади та пологи

    public int hunger = 0;
    public int fatigue = 0;
    public int fear = 0;
    public boolean choking = false;
    public int eatingTicksLeft = 0;                 // скільки тіків ще їсть блок, який поставив гравець

    public final CowTrust trust = new CowTrust();   // довіра до кожного гравця
    public final CowReproduction repro = new CowReproduction(this);   // стать, вагітність, пологи
    public final CowCycle cycle = new CowCycle(this);                 // статевий цикл самки (тічка)
    public final CowGenes genes = new CowGenes(this.getRandom());     // 71 ген, по дві копії
    public final CowMilk milk = new CowMilk(this);                    // шкала молока і якість
    public final CowFamily family = new CowFamily(this);              // ім'я, мати, родинне дерево
    public final CowCharacter character = new CowCharacter(genes, this.getRandom()); // 14 рис: гени + досвід
    public final CowChemistry chem = new CowChemistry(this);          // речовини в крові й відчуття
    private final EnumMap<Experience, Integer> lastExperience = new EnumMap<>(Experience.class); // не зберігається
    public int nurseUntil = 0;                      // до якого тіку мати стоїть і годує теля

    private Player defendTarget = null;             // кого б'є, захищаючи теля (не зберігається)
    private int defendUntil = 0;
    private float defendDamage = 0;
    private String lastLabel = null;                // останній напис над коровою (щоб помітити бирку з іменем)

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
        repro.rollBirthLimit();
        onGenesChanged();
        this.setHealth(this.getMaxHealth());   // нова корова з повним здоров'ям (при завантаженні здоров'я потім читається з NBT)
    }

    // ===================== ГЕНИ =====================

    private double hungerCarry = 0;  // накопичений голод між секундами (не зберігається)
    private double fatigueCarry = 0;
    public boolean resting = false;  // зараз відпочиває (RestGoal, NurseGoal)
    private boolean running = false; // за останню секунду пробігла більше RUN_BLOCKS_PER_SECOND
    private double lastX = Double.NaN;
    private double lastZ = Double.NaN;

    // Чи бігла корова за останню секунду
    private void updateActivity() {
        if (!Double.isNaN(lastX)) {
            double dx = this.getX() - lastX;
            double dz = this.getZ() - lastZ;
            running = Math.sqrt(dx * dx + dz * dz) > RUN_BLOCKS_PER_SECOND;
        }
        lastX = this.getX();
        lastZ = this.getZ();
    }

    public boolean isRunning() {
        return running;
    }

    // Обмін речовин: як швидко корова голодніє (1 = звичайно)
    public double metabolism() {
        double m = 1.0;
        if (repro.isPregnant()) m += PREGNANT_HUNGER;
        if (milk.isLactating()) m += LACTATING_HUNGER;
        if (running) m *= RUN_HUNGER;
        else if (resting) m *= REST_HUNGER;
        return m;
    }

    // 6б: чи хоче корова відпочити. Вирішує відчуття втоми; повне виснаження — рефлекс.
    public boolean wantsRest() {
        return fatigue >= EXHAUSTED_AT || chem.fatigueFelt() >= TIRED_AT;
    }
    private int starveTimer = 0;
    private double growthCarry = 0;  // накопичене прискорення/сповільнення росту теляти

    // Розмір тіла: синхронізується з грою гравця, щоб корову було видно більшою чи меншою
    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(HungryCow.class, EntityDataSerializers.FLOAT);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SIZE, 1.0F);
    }

    public float sizeScale() {
        return this.entityData.get(DATA_SIZE);
    }

    // хітбокс (розмір для зіткнень) теж більший чи менший
    @Override
    public float getScale() {
        return super.getScale() * sizeScale();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_SIZE.equals(key)) this.refreshDimensions();
        super.onSyncedDataUpdated(key);
    }

    // Викликається, коли гени стали відомі (нова корова, народження, завантаження): розмір, здоров'я, швидкість
    public void onGenesChanged() {
        if (this.level() == null || this.level().isClientSide) return;
        float size = SIZE_MIN + (SIZE_MAX - SIZE_MIN) * genes.value(CowGenes.Gene.SIZE) / 100.0F;
        this.entityData.set(DATA_SIZE, size);

        double health = gene(CowGenes.Gene.HEALTH) * (1.0 + (size - 1.0) * SIZE_HEALTH);
        double speed = gene(CowGenes.Gene.WALK_SPEED) * (1.0 - (size - 1.0) * SIZE_SLOWDOWN);
        double knockback = Math.max(0.0, KNOCKBACK_RESIST_MAX * (genes.value(CowGenes.Gene.KNOCKBACK_RESIST) - 50) / 50.0);
        setGeneModifier(Attributes.MAX_HEALTH, HEALTH_GENE_ID, "Health gene", health - 1.0, AttributeModifier.Operation.MULTIPLY_BASE);
        setGeneModifier(Attributes.MOVEMENT_SPEED, SPEED_GENE_ID, "Speed gene", speed - 1.0, AttributeModifier.Operation.MULTIPLY_BASE);
        setGeneModifier(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_GENE_ID, "Knockback gene", knockback, AttributeModifier.Operation.ADDITION);
        if (this.getHealth() > this.getMaxHealth()) this.setHealth(this.getMaxHealth());
    }

    private void setGeneModifier(net.minecraft.world.entity.ai.attributes.Attribute attribute, UUID id, String name,
                                 double value, AttributeModifier.Operation op) {
        AttributeInstance inst = this.getAttribute(attribute);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addPermanentModifier(new AttributeModifier(id, name, value, op));
    }

    // швидкість утечі з урахуванням гена (для FearGoal)
    public double fleeSpeed(double base) {
        return base * gene(CowGenes.Gene.FLEE_SPEED);
    }

    public double gene(CowGenes.Gene g) {
        return genes.factor(g);
    }

    // з якого голоду корова шукає їжу: ген «шукає їжу раніше» 100 → з 21, 0 → з 39
    public int hungryAt() {
        double t = HUNGRY_AT * genes.inverse(CowGenes.Gene.HUNGER_THRESHOLD);
        return (int) Math.round(t);
    }

    // 6б: чи хоче корова їсти. Вирішує відчуття голоду (апетит, стрес, біль, тічка),
    // але голодування — рефлекс: голодна понад STARVE_AT іде їсти за будь-якого відчуття.
    public boolean wantsFood() {
        return hunger >= STARVE_AT || chem.hungerFelt() >= hungryAt();
    }

    // Раз на секунду: голод, втома і голодування з урахуванням генів
    private void geneSecondTick() {
        updateActivity();
        // голод: ген × обмін речовин (вагітність, молоко, біг, відпочинок)
        hungerCarry += gene(CowGenes.Gene.HUNGER_RATE) * metabolism() / HUNGER_EVERY_SECONDS;
        while (hungerCarry >= 1.0) {
            hungerCarry -= 1.0;
            hunger += TIME_SPEED;
        }
        // втома: ген × біг; під час відпочинку не росте (її знімає RestGoal/NurseGoal)
        if (!resting) {
            fatigueCarry += gene(CowGenes.Gene.FATIGUE_RATE) * (running ? RUN_FATIGUE : 1.0) / FATIGUE_EVERY_SECONDS;
            while (fatigueCarry >= 1.0) {
                fatigueCarry -= 1.0;
                fatigue += TIME_SPEED;
            }
        }
        if (hunger > STARVE_AT) {
            int starveEvery = Math.max(1, (int) Math.round(STARVE_DAMAGE_SECONDS * gene(CowGenes.Gene.STARVE_RESIST)));
            if (++starveTimer >= starveEvery) {
                starveTimer = 0;
                this.hurt(this.damageSources().starve(), 1.0F);
            }
        } else {
            starveTimer = 0;
        }
        // теля росте швидше чи повільніше: ген 100 → на 30% швидше
        if (this.isBaby()) {
            // ген росту й тестовий перемикач часу (CowCycle.TIME_SPEED): додаткові тіки віку за секунду
            growthCarry += 20.0 * (gene(CowGenes.Gene.CALF_GROWTH) * CowCycle.TIME_SPEED - 1.0);
            int whole = (int) growthCarry;
            if (whole != 0) {
                growthCarry -= whole;
                this.setAge(Math.min(-1, this.getAge() + whole));
            }
        }
    }

    // Усі цілі задані тут явно (без super.registerGoals()), кожна зі своїм пріоритетом:
    // менше число = важливіше. Ванільні цілі корови теж тут, але змінені під наш мод.
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));                 // пливти, щоб не втопитися
        this.goalSelector.addGoal(1, new LaborGoal(this));                 // пологи: стоїть на місці
        this.goalSelector.addGoal(2, new DefendCalfGoal(this));            // б'є того, хто вдарив теля
        this.goalSelector.addGoal(3, new FearGoal(this));                  // страх і напади ворожої корови
        this.goalSelector.addGoal(4, new FirePanicGoal(this));             // паніка лише від вогню й морозу
        this.goalSelector.addGoal(5, new NurseGoal(this));                 // мати стоїть, поки теля п'є
        this.goalSelector.addGoal(6, new DrinkMilkGoal(this));             // теля йде пити молоко
        this.goalSelector.addGoal(7, new SeekGrassGoal(this));             // пошук їжі
        this.goalSelector.addGoal(8, new MateGoal(this));                  // самка в тічці йде до бика (замість ванільного розмноження)
        this.goalSelector.addGoal(9, new BullCourtGoal(this));             // бик іде до самки в тічці
        this.goalSelector.addGoal(10, new RestGoal(this));                 // відпочинок
        this.goalSelector.addGoal(11, new FindFosterGoal(this));           // теля-сирота шукає іншу матір
        this.goalSelector.addGoal(12, new TemptGoal(this, 1.25, TEMPT_ITEMS, false)); // йде за ласощами
        this.goalSelector.addGoal(13, new AvoidBluetGoal(this));           // відходить від черемші
        this.goalSelector.addGoal(14, new HerdUpGoal(this));               // проеструс/тічка: тримається біля корів
        this.goalSelector.addGoal(15, new ApproachPlayerGoal(this));       // підходить до друга
        this.goalSelector.addGoal(16, new GrieveGoal(this));               // теля ходить біля місця смерті матері
        this.goalSelector.addGoal(17, new FollowMotherGoal(this));         // теля тримається біля матері
        this.goalSelector.addGoal(18, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(19, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(20, new RandomLookAroundGoal(this));
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
            cycle.secondTick();
            milk.secondTick();
            family.secondTick();
            giveFriendEffect();
            chem.secondTick();
            if (second % FIRE_CHECK_SECONDS == 0 && fireNearby()) {
                experience(Experience.FIRE, FIRE_EXPERIENCE_COOLDOWN);
            }
            geneSecondTick();
            int gain = fearGainNearby();
            if (gain > 0) {
                fear = Math.min(100, fear + gain);
            } else {
                fear = Math.max(0, fear - fearDecay());
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
            // хтось назвав корову биркою: беремо це ім'я
            Component current = this.getCustomName();
            if (current != null && lastLabel != null && !current.getString().equals(lastLabel)) {
                family.name = current.getString();
                tell("Корову назвали: " + family.name);
            }
            name = repro.decorate(name);
            lastLabel = name;
            this.setCustomName(Component.literal(name));
            this.setCustomNameVisible(true);
        }
    }

    public void tell(String text) {
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
        if (real > 0) {
            // прив'язаність до людей: довіра росте повільніше або швидше
            double k = TRUST_GAIN_MIN + (TRUST_GAIN_MAX - TRUST_GAIN_MIN) * character.fraction(Trait.HUMAN_ATTACHMENT);
            real = Math.max(1, (int) Math.round(real * k));
        }
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
            experience(Experience.TREAT, 0);
            chem.onTreat();
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

    // Корови, що бачать цю корову (у радіусі свідків і з прямою видимістю)
    private java.util.List<HungryCow> witnesses() {
        double r = CowTrust.WITNESS_RADIUS;
        return this.level().getEntitiesOfClass(HungryCow.class, this.getBoundingBox().inflate(r),
                c -> c != this && c.isAlive() && c.distanceToSqr(this) <= r * r && c.hasLineOfSight(this));
    }

    // Ця корова — мати тієї (рідна чи прийомна, поки та маленька)
    private boolean isMotherOf(HungryCow calf) {
        return calf.isBaby() && this.getUUID().equals(calf.family.motherIdOrNull());
    }

    // ===================== ХАРАКТЕР =====================

    // Подія досвіду; cooldownTicks — не частіше за стільки тіків (0 = щоразу)
    public void experience(Experience e, int cooldownTicks) {
        Integer last = lastExperience.get(e);
        if (cooldownTicks > 0 && last != null && this.tickCount - last < cooldownTicks) return;
        lastExperience.put(e, this.tickCount);
        String changes = character.apply(e);
        tell(family.name + " (" + e.title + "): " + changes);
    }

    // Поріг страху: полохлива корова тікає раніше
    public int scaredAt() {
        double t = SCARED_AT_CALM - (SCARED_AT_CALM - SCARED_AT_TIMID) * character.fraction(Trait.FEARFULNESS);
        return (int) Math.round(t * chem.fearThresholdFactor());   // самотня тікає раніше
    }

    // Як швидко минає страх: смілива заспокоюється швидше
    private int fearDecay() {
        return (int) Math.round(FEAR_DECAY_MIN + (FEAR_DECAY_MAX - FEAR_DECAY_MIN) * character.fraction(Trait.BRAVERY));
    }

    // Страх від удару: чутлива до болю лякається сильніше
    private int painFear(int base) {
        double k = PAIN_FEAR_MIN + (PAIN_FEAR_MAX - PAIN_FEAR_MIN) * character.fraction(Trait.PAIN_SENSITIVITY);
        return (int) Math.round(base * k);
    }

    // Пауза між нападами ворожої корови: агресивна нападає частіше
    private int chargeInterval(int trust) {
        double k = CHARGE_PAUSE_MAX - (CHARGE_PAUSE_MAX - CHARGE_PAUSE_MIN) * character.fraction(Trait.AGGRESSION);
        return (int) Math.round(CowTrust.attackInterval(trust) * k);
    }

    // Шанс заступитися за чуже теля
    private float defendChance() {
        return (float) (DEFEND_CHANCE_MIN + (DEFEND_CHANCE_MAX - DEFEND_CHANCE_MIN) * character.fraction(Trait.MATERNAL));
    }

    // Чи горить щось поруч (вогонь, вогонь душ)
    private boolean fireNearby() {
        BlockPos o = this.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-FIRE_RADIUS, -1, -FIRE_RADIUS), o.offset(FIRE_RADIUS, 2, FIRE_RADIUS))) {
            if (this.level().getBlockState(p).is(BlockTags.FIRE)) return true;
        }
        return false;
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
            nextChargeTick = this.tickCount + chargeInterval(t);
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
            attackPlayer(player, CowTrust.ATTACK_DAMAGE);
            lastAttackTick = this.tickCount;
            if (t > CowTrust.MIN) {          // при -100 переслідує далі, інакше відскакує
                charging = false;
                nextChargeTick = this.tickCount + chargeInterval(t);
            }
        }
        return true;
    }

    private void attackPlayer(Player player, float damage) {
        // у творчому режимі й на «Мирній» складності удар не проходить, тоді й не відкидаємо
        // ген «сила удару»: ±30%
        damage = (float) (damage * gene(CowGenes.Gene.KICK_STRENGTH));
        if (!player.hurt(this.damageSources().mobAttack(this), damage)) return;
        player.knockback(CowTrust.ATTACK_KNOCKBACK, this.getX() - player.getX(), this.getZ() - player.getZ());
        player.hurtMarked = true;
        this.playSound(SoundEvents.COW_HURT, 1.0F, 0.8F);
        tell("Корова вдарила " + player.getName().getString());
    }

    // ===================== ЗАХИСТ ТЕЛЯТИ =====================

    // Гравець ударив теля: мати (завжди) та інші дорослі поруч (з шансом) б'ють його 7 секунд
    private void callForHelp(Player player) {
        HungryCow mother = family.findMother();
        if (mother != null && mother.distanceToSqr(this) <= CowFamily.MOTHER_RANGE * CowFamily.MOTHER_RANGE) {
            mother.startDefending(player, CowTrust.ATTACK_DAMAGE * MOTHER_DAMAGE_MULTIPLIER,
                    DEFEND_TRUST_MOTHER, "вдарив її теля");
        }
        for (HungryCow other : this.level().getEntitiesOfClass(HungryCow.class,
                this.getBoundingBox().inflate(DEFEND_RADIUS),
                c -> c != this && c != mother && c.isAlive() && !c.isBaby())) {
            if (this.getRandom().nextFloat() < other.defendChance()) {
                other.startDefending(player, CowTrust.ATTACK_DAMAGE, DEFEND_TRUST_OTHERS, "вдарив теля");
            }
        }
    }

    public void startDefending(Player player, float damage, int trustPenalty, String reason) {
        if (player.isSpectator()) return;   // у творчому режимі теж нападають (удар не пройде), щоб було видно в тестах
        boolean already = defendTarget == player && this.tickCount < defendUntil;
        defendTarget = player;
        defendUntil = this.tickCount + DEFEND_TICKS;
        defendDamage = damage;
        if (!already) {
            addTrust(player, -trustPenalty, reason, false);
            experience(Experience.DEFENDED_CALF, 0);
            tell(family.name + " захищає теля від " + player.getName().getString());
        }
    }

    // кого зараз б'є, захищаючи теля (null, якщо нікого)
    public Player defendTarget() {
        if (defendTarget == null || !defendTarget.isAlive() || this.tickCount >= defendUntil) return null;
        return defendTarget;
    }

    // викликається з DefendCalfGoal щотіку
    public void defendTick(Player player) {
        if (this.tickCount % 5 == 0 || this.getNavigation().isDone()) {
            this.getNavigation().moveTo(player, 1.5);
        }
        this.getLookControl().setLookAt(player, 30.0F, 30.0F);
        double reach = CowTrust.ATTACK_REACH;
        if (this.distanceToSqr(player) < reach * reach
                && this.tickCount - lastAttackTick >= CowTrust.ATTACK_COOLDOWN_TICKS) {
            attackPlayer(player, defendDamage);
            lastAttackTick = this.tickCount;
        }
    }

    // 7 секунд минуло: корова тікає
    public void stopDefending() {
        defendTarget = null;
        defendUntil = 0;
        fear = Math.max(fear, FEAR_AFTER_DEFEND);
        this.getNavigation().stop();
    }

    // ===================== ДРУГ КОРОВИ =====================

    // Корова з довірою 200 дає гравцям поруч ефект «Друг корови»
    private void giveFriendEffect() {
        if (this.isBaby()) return;
        for (Player p : this.level().players()) {
            if (p.isSpectator() || this.distanceToSqr(p) > FRIEND_EFFECT_RANGE * FRIEND_EFFECT_RANGE) continue;
            if (trustOf(p) >= CowTrust.BOND_FROM) {
                p.addEffect(new MobEffectInstance(ModEffects.COW_FRIEND.get(), FRIEND_EFFECT_TICKS, 0, false, true, true));
            }
        }
    }

    // ===================== МОЛОКО =====================

    // ПКМ відром: молоко дає лише самка після пологів
    private void tryMilk(Player player, InteractionHand hand, ItemStack stack) {
        if (this.isBaby() || !repro.isFemale()) {
            tell(family.name + " не дає молока: " + (this.isBaby() ? "це теля" : "це бик"));
            return;
        }
        if (!milk.isLactating()) {
            tell(family.name + " ще не має молока: воно з'являється лише після пологів");
            return;
        }
        boolean friend = player.hasEffect(ModEffects.COW_FRIEND.get());
        int cost = friend ? CowMilk.FRIEND_BUCKET_COST : CowMilk.BUCKET_COST;
        if (!milk.take(cost)) {
            tell("У " + family.name + " замало молока: " + milk.amount() + " з " + cost + " потрібних");
            return;
        }
        player.playSound(SoundEvents.COW_MILK, 1.0F, 1.0F);
        ItemStack result = ItemUtils.createFilledResult(stack, player, QualityMilkBucketItem.create(milk.quality()));
        player.setItemInHand(hand, result);
        tell("Подоєно " + family.name + ": якість " + milk.quality().letter + ", лишилось " + milk.amount());
    }

    // ПКМ по теляті відром молока: теля наїдається; сирота вважає гравця матір'ю
    private void feedCalfMilk(Player player, InteractionHand hand, ItemStack stack) {
        hunger = Math.max(0, hunger - CALF_BUCKET_FOOD);
        this.playSound(SoundEvents.GENERIC_DRINK, 1.0F, 1.0F);
        player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
        if (family.isOrphan()) {
            family.adoptByPlayer(player);
        } else {
            tell(player.getName().getString() + " напоїв(ла) теля " + family.name);
        }
    }

    // Гравець стоїть ззаду корови (і близько)
    private int lastAssistHint = -1000;

    private boolean isBehind(Player player) {
        double dx = player.getX() - this.getX();
        double dz = player.getZ() - this.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > CowReproduction.ASSIST_REACH || dist < 1.0E-4) return dist < 1.0E-4;
        float yaw = this.yBodyRot * ((float) Math.PI / 180F);
        double fx = -Math.sin(yaw);   // куди дивиться корова
        double fz = Math.cos(yaw);
        double dot = (dx * fx + dz * fz) / dist;
        return dot < CowReproduction.ASSIST_BEHIND_DOT;
    }

    // Теля росте 100 ігрових діб (ванільно 1 доба); тестовий перемикач CowCycle.TIME_SPEED прискорює
    @Override
    public void setBaby(boolean baby) {
        this.setAge(baby ? -CALF_GROW_TICKS : 0);
    }

    // Теля виросло.
    // Ванільний AgeableMob.setAge() викликає цей метод лише коли вік переходить через межу «теля/доросла»
    // (з від'ємного в ≥ 0 або навпаки), і вже ПІСЛЯ оновлення isBaby(). Пауза після розмноження
    // (вік 6000 → 0) межу не перетинає, тож для дорослих він не спрацьовує.
    @Override
    protected void ageBoundaryReached() {
        super.ageBoundaryReached();
        if (!this.level().isClientSide && !this.isBaby()) {
            family.onGrownUp();
        }
    }

    // ===================== ГОДУВАННЯ =====================

    // Годування з руки (ПКМ предметом із таблиці CowFood)
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Item item = stack.getItem();
        boolean server = !this.level().isClientSide;

        // книга «Корововедення»: ПКМ по корові відкриває сторінки про неї
        if (stack.is(ModItems.COW_BOOK.get())) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) CowBook.open(serverPlayer, this);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        // пологи: присісти ззаду й тримати ПКМ порожньою рукою = допомогти
        if (repro.inLabor() && player.isShiftKeyDown() && stack.isEmpty()) {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (server) {
                if (isBehind(player)) {
                    repro.assistTick(player);
                } else if (this.tickCount - lastAssistHint > 40) {
                    lastAssistHint = this.tickCount;
                    player.sendSystemMessage(Component.literal("Щоб допомогти з пологами, підійди ззаду"));
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        // присісти + ПКМ порожньою рукою: родинне дерево в чат (поки для тестів)
        if (player.isShiftKeyDown() && stack.isEmpty()) {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (server) family.showTo(player);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        // доїння: замість ванільного молока для будь-якої корови
        if (stack.is(Items.BUCKET)) {
            if (server) tryMilk(player, hand, stack);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        // теля п'є молоко з відра
        if (this.isBaby() && (stack.is(Items.MILK_BUCKET) || stack.is(ModItems.QUALITY_MILK_BUCKET.get()))) {
            if (server) feedCalfMilk(player, hand, stack);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        CowFood food = CowFood.get(item);
        if (food == null) {
            return super.mobInteract(player, hand);
        }
        // теля, поки мале, п'є лише молоко
        if (this.isBaby()) {
            if (server) tell("Теля " + family.name + " п'є лише молоко");
            return InteractionResult.sidedSuccess(this.level().isClientSide);
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
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                this.playSound(SoundEvents.GENERIC_EAT, 1.0F, 1.0F);
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    // чи діє зараз власний урон від їжі (токсин або повільна смерть)
    public boolean toxinActive() {
        return toxinLeft > 0 || dyingLeft > 0;
    }

    // Тривалість отруєння з урахуванням гена «стійкість до отрути»
    private int poisonTicks(int seconds) {
        return Math.max(20, (int) Math.round(seconds * 20 * genes.inverse(CowGenes.Gene.POISON_RESIST)));
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

        // овочі й фрукти насичують більше чи менше за геном
        String dietGroup = CowMilk.dietGroup(item);
        int foodHunger = ("veg".equals(dietGroup) || "fruit".equals(dietGroup))
                ? (int) Math.round(food.hunger * gene(CowGenes.Gene.PRODUCE_DIGEST)) : food.hunger;
        hunger = Math.max(0, hunger - foodHunger);
        milk.recordDiet(item);
        chem.onEat();
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
            this.addEffect(new MobEffectInstance(MobEffects.POISON, poisonTicks(food.poisonSeconds)));
        }
        if (food.limitFrom > 0 && count >= food.limitFrom) {
            this.addEffect(new MobEffectInstance(MobEffects.POISON, poisonTicks(food.limitPoisonSeconds)));
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
            toxinLeft = Math.max(1, (int) Math.round(food.toxinSeconds * genes.inverse(CowGenes.Gene.POISON_RESIST)));
            toxinDamage = food.toxinDamage;
        }
        if (food.chokeChance > 0) {
            float chance = CHOKE_TEST_CHANCE > 0
                    ? CHOKE_TEST_CHANCE
                    : Math.min(1.0F, food.chokeChance * CHOKE_MULTIPLIER);
            chance = (float) Math.min(1.0, chance * gene(CowGenes.Gene.CHOKE_RISK));
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
        return false;   // ванільного розмноження немає: лише цикл тічки (CowCycle, MateGoal)
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
            int f = fearFromPlayer(player);
            // з ефектом «Друг корови» корови бояться вдвічі менше
            return player.hasEffect(ModEffects.COW_FRIEND.get()) ? f / 2 : f;
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
        if (result && !this.level().isClientSide) chem.onHurt(amount);   // біль і адреналін від будь-якого урону
        Entity attacker = source.getEntity();
        if (result && !this.level().isClientSide && attacker != null) {
            if (attacker instanceof Player player) {
                lastPlayerHitTick = this.tickCount;
                if (this.isBaby()) {
                    callForHelp(player);
                }
                if (choking) {
                    // удар гравця рятує подавлену корову: без штрафу довіри, страх невеликий
                    stopChoking();
                    fear = Math.min(100, fear + CURE_FEAR);
                } else {
                    int before = trustOf(player);
                    addTrust(player, -CowTrust.HIT_PENALTY, "удар", true);
                    fear = Math.min(100, fear + painFear(CowTrust.hitFear(before)));
                    experience(Experience.HIT_BY_PLAYER, HIT_EXPERIENCE_COOLDOWN);
                    if (!player.isSpectator() && !player.hasEffect(MobEffects.INVISIBILITY)) {
                        for (HungryCow w : witnesses()) {
                            if (w.hasLineOfSight(player)) w.experience(Experience.SAW_PLAYER_HIT_COW, SAW_HIT_EXPERIENCE_COOLDOWN);
                        }
                    }
                }
            } else {
                // страх лише від ударів істот, а не від голоду чи отрути
                fear = Math.min(100, fear + painFear(50));
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
            Entity killerEntity = source.getEntity();
            if (killerEntity instanceof Player killer) {
                punishWitnesses(killer);
            }
            // хто бачив цю смерть, отримує досвід; телята цієї корови — окремий, сильніший
            for (HungryCow w : witnesses()) {
                if (isMotherOf(w)) {
                    w.experience(killerEntity instanceof Player ? Experience.MOTHER_KILLED_BY_PLAYER : Experience.MOTHER_DIED, 0);
                } else if (killerEntity instanceof Player p && !p.isSpectator() && !p.hasEffect(MobEffects.INVISIBILITY)) {
                    w.experience(Experience.SAW_PLAYER_KILL, 0);
                } else if (killerEntity instanceof Zombie) {
                    w.experience(Experience.SAW_ZOMBIE_KILL, 0);
                }
            }
            // телята цієї корови поруч сумують на місці смерті
            if (!this.isBaby()) {
                double mr = CowFamily.MOTHER_RANGE;
                for (HungryCow calf : this.level().getEntitiesOfClass(HungryCow.class, this.getBoundingBox().inflate(mr),
                        c -> isMotherOf(c) && c.distanceToSqr(this) <= mr * mr)) {
                    calf.family.onMotherDied(this.blockPosition());
                }
            }
            if (this.isBaby()) {
                HungryCow mother = family.findMother();
                if (mother != null) mother.milk.onCalfDied();
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
        milk.save(tag);
        family.save(tag);
        character.save(tag);
        genes.save(tag);
        cycle.save(tag);
        chem.save(tag);
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
        repro.load(tag);   // спершу стать, від неї залежить ім'я старої корови
        milk.load(tag);
        family.load(tag);
        genes.load(tag);       // перед character: гени характеру беруться звідси
        cycle.load(tag);
        character.load(tag);
        chem.load(tag);
        onGenesChanged();
    }
}
