package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowTrust;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

public class FearGoal extends Goal {
    // з якого страху корова тікає, залежить від полохливості: див. HungryCow.scaredAt() (5–15, при 50 — 10)
    private final HungryCow cow;
    private LivingEntity scary;
    // «бий або тікай» (ідея 12): на початку страху корова вирішує раз — тікати чи напасти
    private boolean fighting = false;
    private int fightUntil = 0;
    private int nextRam = 0;

    public FearGoal(HungryCow cow) {
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
        scary = cow.findScary(10.0);
        if (scary == null) return false;
        // ворожа корова (довіра < 0) — рефлекс поза мозком; інакше тікати чи ні вирішує мозок (або старе правило)
        return hostile() ? cow.wantsToFlee() : cow.shouldFlee();
    }

    // Шукати заново кожні 10 тіків, а не щотіку: пошук істот поруч дорогий
    @Override
    public boolean canContinueToUse() {
        if (!(hostile() ? cow.stillAfraid() : cow.keepFleeing())) return false;   // заспокоюється на половині порогу
        if (scary == null || !scary.isAlive() || cow.tickCount % 10 == 0) {
            scary = cow.findScary(14.0);
        }
        return scary != null;
    }

    private boolean hostile() {
        return scary instanceof Player p && cow.trustOf(p) < 0;
    }

    @Override
    public void start() {
        cow.spreadAlarm(scary);   // корови поруч теж лякаються
        if (scary instanceof Player player) {
            cow.onScaredBy(player);
        }
        fighting = !hostile() && cow.getRandom().nextDouble() < cow.fightChance(scary);
        if (fighting) {
            fightUntil = cow.tickCount + HungryCow.FIGHT_TICKS;
            cow.tell(cow.family.name + (cow.isBull() ? " (бик)" : "") + " не тікає, а нападає на " + scary.getName().getString());
        }
    }

    @Override
    public void tick() {
        if (scary == null) return;
        // ворожа корова (довіра менша за 0) інколи атакує замість того, щоб тікати
        if (scary instanceof Player player && cow.trustOf(player) < 0 && cow.hostileBehavior(player)) {
            return;
        }
        // нападає: іде на ворога й б'є лобом; минуло 10 с, ворог зник чи сама поранена до третини — далі тікає
        if (fighting) {
            if (cow.tickCount >= fightUntil || !scary.isAlive() || cow.getHealth() <= cow.getMaxHealth() * HungryCow.FIGHT_HEALTH_MIN) {
                fighting = false;
            } else {
                if (cow.tickCount % 5 == 0 || cow.getNavigation().isDone()) cow.getNavigation().moveTo(scary, HungryCow.FIGHT_SPEED);
                double gap = Math.sqrt(cow.distanceToSqr(scary)) - (cow.getBbWidth() + scary.getBbWidth()) / 2.0;
                if (gap <= HungryCow.RAM_REACH && cow.tickCount >= nextRam) {
                    cow.ram(scary);
                    nextRam = cow.tickCount + CowTrust.ATTACK_COOLDOWN_TICKS;
                }
                return;
            }
        }
        if (cow.tickCount % 10 == 0 || cow.getNavigation().isDone()) {
            double speed = cow.fleeSpeed(1.0 + cow.fear / 100.0 * 1.2);
            Vec3 away = DefaultRandomPos.getPosAway(cow, 10, 4, scary.position());
            if (away != null) {
                cow.getNavigation().moveTo(away.x, away.y, away.z, speed);
            }
        }
    }

    @Override
    public void stop() {
        scary = null;
        fighting = false;
        cow.cancelCharge();
        cow.getNavigation().stop();
    }
}
