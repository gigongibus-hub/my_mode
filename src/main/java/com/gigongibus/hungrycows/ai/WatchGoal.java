package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowCharacter.Trait;
import com.gigongibus.hungrycows.CowTrust;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

// Погляд на когось (замість ванільного LookAtPlayerGoal). Корова помічає РУХ і дивиться на те, що для неї важливе:
//  - небезпеку (зомбі, вовк, ворожий гравець) не спускає з очей, доки та поруч;
//  - своє теля (мати), друга (висока довіра) — спокійно й довго; чужого гравця — насторожено; інших корів — коротко;
//  - рухомих помічає частіше, ніж тих, що стоять;
//  - допитлива дивиться довше; полохлива частіше зиркає на незнайомців.
public class WatchGoal extends Goal {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final double RANGE = 10;                 // помічає до 10 блоків
    private static final int CHECK_EVERY = 10;              // роздивляється навколо раз на пів секунди
    private static final double START_CHANCE = 0.10;        // шанс почати дивитися на когось за перевірку (×вага)
    private static final double MOVING = 0.1;               // «рухається» = за пів секунди зрушив більше ніж на 0,1 блока
    private static final double MOVING_FACTOR = 3.0;        // рухомих помічає втричі частіше
    // вага (наскільки важливо) і скільки дивиться (тіки, від і до)
    private static final double W_DANGER = 10, W_CALF = 4, W_FRIEND = 3, W_STRANGER = 3, W_COW = 1;
    private static final int[] T_FRIEND = { 60, 120 }, T_STRANGER = { 30, 60 }, T_CALF = { 60, 100 }, T_COW = { 20, 40 };
    private static final double FEARFUL_STRANGER = 1.0;     // полохлива зиркає на чужих до 2× частіше
    private static final double CURIOUS_LONGER = 0.5;       // допитлива дивиться до 1,5× довше (нецікава — 0,75×)
    private static final float HEAD_CALM = 10F, HEAD_ALERT = 30F;   // як швидко повертає голову: спокійно / насторожено

    private final HungryCow cow;
    private final Map<Integer, Vec3> lastPos = new HashMap<>();
    private LivingEntity target;
    private boolean danger;
    private int lookTime;
    private int cooldown = 0;

    public WatchGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        cooldown = CHECK_EVERY / 2;   // canUse викликається раз на 2 тіки
        target = pick();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (target == null || !target.isAlive() || cow.distanceToSqr(target) > RANGE * RANGE) return false;
        if (danger) return cow.vision.canSee(target);   // небезпеку не спускає з очей, доки бачить
        return lookTime > 0;
    }

    @Override
    public void start() {
        double curious = cow.character.fraction(Trait.CURIOSITY);
        int[] t = target instanceof Player p ? (cow.trustOf(p) >= CowTrust.FRIEND_FROM ? T_FRIEND : T_STRANGER)
                : target instanceof HungryCow c && cow.isMotherOf(c) ? T_CALF : T_COW;
        int base = t[0] + cow.getRandom().nextInt(t[1] - t[0] + 1);
        lookTime = (int) Math.round(base * (0.75 + CURIOUS_LONGER * curious));
    }

    @Override
    public void tick() {
        if (target == null) return;
        lookTime--;
        cow.getLookControl().setLookAt(target, danger ? HEAD_ALERT : HEAD_CALM, danger ? HEAD_ALERT : HEAD_CALM);
    }

    @Override
    public void stop() {
        target = null;
        danger = false;
    }

    private boolean isDanger(LivingEntity e) {
        if (e instanceof Player p) return cow.trustOf(p) < 0 && cow.isScary(p);
        return e instanceof Wolf || (cow.isScary(e) && !(e instanceof HungryCow));
    }

    // Кого помітила: важливих частіше, рухомих частіше; небезпеку — завжди
    private LivingEntity pick() {
        Map<Integer, Vec3> now = new HashMap<>();
        LivingEntity best = null;
        double bestScore = 0;
        boolean bestDanger = false;
        double fearful = cow.character.fraction(Trait.FEARFULNESS);
        for (LivingEntity e : cow.level().getEntitiesOfClass(LivingEntity.class, cow.getBoundingBox().inflate(RANGE),
                e -> e != cow && e.isAlive() && !(e instanceof Player p && p.isSpectator()))) {
            now.put(e.getId(), e.position());
            if (cow.distanceToSqr(e) > RANGE * RANGE || !cow.vision.canSee(e)) continue;
            boolean d = isDanger(e);
            double w;
            if (d) w = W_DANGER;
            else if (e instanceof Player p) w = cow.trustOf(p) >= CowTrust.FRIEND_FROM ? W_FRIEND : W_STRANGER * (1 + FEARFUL_STRANGER * fearful);
            else if (e instanceof HungryCow c) w = cow.isMotherOf(c) ? W_CALF : W_COW;
            else continue;   // інші тварини корову не цікавлять
            Vec3 before = lastPos.get(e.getId());
            if (before != null && before.distanceToSqr(e.position()) > MOVING * MOVING) w *= MOVING_FACTOR;
            double score = w / (1 + Math.sqrt(cow.distanceToSqr(e)) / RANGE);   // ближчі помітніші
            if (score > bestScore) {
                bestScore = score;
                best = e;
                bestDanger = d;
            }
        }
        lastPos.clear();
        lastPos.putAll(now);
        // зір: краєм ока помітила, що збоку щось рухається — повертає туди голову (якщо немає небезпеки)
        LivingEntity g = cow.vision.glance();
        if (!bestDanger && g != null && cow.distanceToSqr(g) < RANGE * RANGE) {
            danger = isDanger(g);
            return g;
        }
        if (best == null) return null;
        // небезпеку помічає одразу; решту — з шансом, що росте з важливістю
        if (!bestDanger && cow.getRandom().nextDouble() >= START_CHANCE * bestScore) return null;
        danger = bestDanger;
        return best;
    }
}
