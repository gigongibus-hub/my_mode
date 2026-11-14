package com.gigongibus.hungrycows;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Що роблять зілля з грибів (Fij, 14.11).
 * ГРИБОГОРІННЯ (вогнегриб): моб б'є всіх навколо в 10 бл., навіть своїх; гравець — раз на 5 с напад: персонаж сам біжить
 * і б'є все навколо в 10 бл.; корова — «Грибна лють» на той самий час.
 * ГРИБНЕ СЯЙВО (зірочник): усі моби в 15 бл. — і мирні — йдуть на того, хто під сяйвом, і б'ють його; големи — ні.
 */
@Mod.EventBusSubscriber(modid = HungryCowsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FungusEffects {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double BURN_RADIUS = 10;         // грибогоріння: б'є все в 10 бл. (Fij)
    public static final double GLOW_RADIUS = 15;         // сяйво: мирні збігаються з 15 бл. (Fij) ...
    public static final double GLOW_HOSTILE_FACTOR = 2;  // ... злі помічають удвічі далі, ніж зазвичай (Fij: «з 5 бл. — то з 10»), не ближче 15
    public static final double GLOW_HOSTILE_MAX = 64;    // ... але не далі 64 бл.
    public static final int SEIZURE_TICKS = 200;         // гравець: напад триває 10 с (Fij, було 2 с) ...
    public static final int SEIZURE_PAUSE = 100;         // ... потім 5 с керування знову є (Fij: «раз на 5 с»)
    public static final int SEIZURE_EVERY = SEIZURE_TICKS + SEIZURE_PAUSE;   // тобто напад повторюється кожні 15 с
    public static final double SEIZURE_PUSH = 0.28;      // під час нападу персонаж біжить до найближчого
    public static final double PLAYER_HIT_REACH = 3.0;   // і б'є, коли ближче 3 бл.
    public static final int HIT_EVERY = 20;              // моби під дією б'ють раз на 1 с
    public static final double MOB_SPEED = 1.2;          // і біжать до цілі
    public static final float NO_ATTACK_DAMAGE = 2F;     // урон мирних мобів (у них немає «сили атаки») — 1 серце
    public static final int FORCE_REFRESH = 40;          // ціль «сяйва» тримається 2 с і оновлюється щосекунди

    private record Forced(LivingEntity target, int until) { }
    private static final Map<Mob, Forced> FORCED = new WeakHashMap<>();
    private static final Map<Mob, Integer> NEXT_HIT = new WeakHashMap<>();

    private FungusEffects() {
    }

    private static boolean canBeHit(LivingEntity e, LivingEntity self) {
        if (e == self || !e.isAlive() || e instanceof ArmorStand) return false;
        return !(e instanceof Player p) || !(p.isCreative() || p.isSpectator());
    }

    private static LivingEntity nearest(LivingEntity self, double radius) {
        LivingEntity best = null;
        double bestD = radius * radius;
        for (LivingEntity e : self.level().getEntitiesOfClass(LivingEntity.class, self.getBoundingBox().inflate(radius), e -> canBeHit(e, self))) {
            double d = self.distanceToSqr(e);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    /** Моб іде на ціль і б'є її (і мирний — тоді урон 1 серце) */
    private static void force(Mob mob, LivingEntity target, int ticks) {
        FORCED.put(mob, new Forced(target, mob.tickCount + ticks));
        if (mob.getTarget() != target && !(mob instanceof HungryCow)) mob.setTarget(target);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide) return;
        if (e.hasEffect(ModEffects.FUNGUS_BURN.get())) burn(e);
        else if (e instanceof ServerPlayer p) {   // ефект скінчився посеред нападу — керування повертається
            var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && speed.getModifier(SEIZURE_ID) != null) speed.removeModifier(SEIZURE_ID);
        }
        if (e.hasEffect(ModEffects.FUNGUS_GLOW.get()) && e.tickCount % 20 == 0) glow(e);
        if (e instanceof Mob mob) chase(mob);
    }

    // ---------- грибогоріння ----------
    private static void burn(LivingEntity e) {
        int left = e.getEffect(ModEffects.FUNGUS_BURN.get()).getDuration();
        if (e instanceof HungryCow cow) {   // корова — наша «Грибна лють» на той самий час (б'є всіх біля місця)
            if (!cow.raging()) {
                cow.addEffect(new MobEffectInstance(ModEffects.MUSHROOM_RAGE.get(), left));
                cow.rageCenter = cow.blockPosition();
            }
            return;
        }
        if (e instanceof ServerPlayer p) {
            seizure(p);
            return;
        }
        if (e instanceof Mob mob && e.tickCount % 10 == 0) {
            LivingEntity t = nearest(mob, BURN_RADIUS);
            if (t != null) force(mob, t, 20);
        }
    }

    // гравець: раз на 5 с — напад (Fij: «не зможе керувати»): клавіші ходьби не діють (швидкість 0), голова повернута до цілі,
    // персонаж сам біжить до найближчого в 10 бл. і б'є
    private static final java.util.UUID SEIZURE_ID = java.util.UUID.fromString("2b7e4c1a-9d3f-4a6b-8e5c-1f0d2a3b4c5e");

    private static void seizure(ServerPlayer p) {
        int phase = p.tickCount % SEIZURE_EVERY;
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (phase >= SEIZURE_TICKS) {
            if (speed != null && speed.getModifier(SEIZURE_ID) != null) speed.removeModifier(SEIZURE_ID);
            return;
        }
        if (speed != null && speed.getModifier(SEIZURE_ID) == null) speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                SEIZURE_ID, "fungus seizure", -1.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
        LivingEntity t = nearest(p, BURN_RADIUS);
        if (phase == 0) p.displayClientMessage(net.minecraft.network.chat.Component.literal("Напад грибогоріння!"), true);
        if (t == null) return;
        Vec3 dir = t.position().subtract(p.position());
        double dist = dir.length();
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() > 1.0E-4) {
            float yaw = (float) (Math.toDegrees(Math.atan2(-flat.x, flat.z)));
            if (phase % 2 == 0) p.connection.teleport(p.getX(), p.getY(), p.getZ(), yaw, 10F);   // голова — до цілі (гравець не відверне)
            if (dist > PLAYER_HIT_REACH - 0.5) {
                Vec3 push = flat.normalize().scale(SEIZURE_PUSH);
                p.setDeltaMovement(push.x, p.getDeltaMovement().y, push.z);
                p.hurtMarked = true;
            }
        }
        if (dist <= PLAYER_HIT_REACH && phase % 10 == 0) {
            p.attack(t);
            p.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    // ---------- грибне сяйво ----------
    private static double glowReach(Mob m) {
        if (!(m instanceof net.minecraft.world.entity.monster.Enemy)) return GLOW_RADIUS;
        double normal = m.getAttribute(Attributes.FOLLOW_RANGE) != null ? m.getAttributeValue(Attributes.FOLLOW_RANGE) : 16;
        return Math.min(GLOW_HOSTILE_MAX, Math.max(GLOW_RADIUS, normal * GLOW_HOSTILE_FACTOR));
    }

    private static void glow(LivingEntity lit) {
        for (Mob m : lit.level().getEntitiesOfClass(Mob.class, lit.getBoundingBox().inflate(GLOW_HOSTILE_MAX),
                m -> m != lit && m.isAlive() && !(m instanceof AbstractGolem))) {
            double r = glowReach(m);
            if (m.distanceToSqr(lit) > r * r) continue;
            force(m, lit, FORCE_REFRESH);
            farSight(m, r);   // щоб злий моб міг дійти здалеку (пошук шляху не будує дорогу, довшу за «дальність стеження»)
        }
    }

    // тимчасово більша «дальність стеження» злого моба (знімається, коли сяйво скінчилось)
    private static final java.util.UUID FAR_SIGHT_ID = java.util.UUID.fromString("6f1d2c3b-4a59-4e68-9d7c-8b0a1f2e3d4c");

    private static void farSight(Mob m, double range) {
        var attr = m.getAttribute(Attributes.FOLLOW_RANGE);
        if (attr == null || attr.getModifier(FAR_SIGHT_ID) != null || attr.getValue() >= range) return;
        attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(FAR_SIGHT_ID, "fungus glow",
                range - attr.getValue(), net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
    }

    private static void farSightOff(Mob m) {
        var attr = m.getAttribute(Attributes.FOLLOW_RANGE);
        if (attr != null && attr.getModifier(FAR_SIGHT_ID) != null) attr.removeModifier(FAR_SIGHT_ID);
    }

    // ---------- моб, якого зілля змусило бити ----------
    private static void chase(Mob mob) {
        Forced f = FORCED.get(mob);
        if (f == null) return;
        LivingEntity t = f.target();
        if (mob.tickCount > f.until() || t == null || !canBeHit(t, mob) || t.level() != mob.level()) {
            FORCED.remove(mob);
            farSightOff(mob);
            if (mob.getTarget() == t) mob.setTarget(null);
            return;
        }
        mob.getLookControl().setLookAt(t, 30F, 30F);
        double reach = (mob.getBbWidth() + t.getBbWidth()) / 2.0 + 1.0;
        if (mob.distanceTo(t) > reach) {
            if (mob.tickCount % 5 == 0 || mob.getNavigation().isDone()) mob.getNavigation().moveTo(t, MOB_SPEED);
            return;
        }
        int next = NEXT_HIT.getOrDefault(mob, 0);
        if (mob.tickCount < next) return;
        NEXT_HIT.put(mob, mob.tickCount + HIT_EVERY);
        float dmg = mob.getAttribute(Attributes.ATTACK_DAMAGE) != null ? (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE) : NO_ATTACK_DAMAGE;
        mob.swing(InteractionHand.MAIN_HAND);
        t.hurt(mob.damageSources().mobAttack(mob), dmg);
    }

    /** Для тестів: чи змушений моб бити саме цю ціль */
    public static boolean isForcedOn(Mob mob, LivingEntity target) {
        Forced f = FORCED.get(mob);
        return f != null && f.target() == target;
    }
}
