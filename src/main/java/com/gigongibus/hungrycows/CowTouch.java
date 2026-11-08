package com.gigongibus.hungrycows;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Дотик (план Fij, етап 5).
 * - Гладити: присісти + ПКМ порожньою рукою. Чужого (довіра ≤ 0) корова не підпускає — відходить.
 *   Де гладять — за тим, де стоїть гравець: спереду (голова, шия) найприємніше, боки — нормально,
 *   ззаду (сліпа зона) — лякається, інколи хвицає. Приємне: окситоцин ↑, кортизол ↓, довіра трохи ↑.
 * - Вилизування (GroomGoal): подруги, мати й теля вилизують одна одній шию — дружба ↑, обом приємно.
 *   Дружба з іншими коровами (−100…+100) зберігається; етап 6 (пам'ять) візьме її за основу.
 * - Погода на шкірі: мокра (дощ, вода), холодно (сніговий біом надворі), спека (CowHeat) — холод і мокро разом
 *   підвищують стрес; сухо й тепло під дахом уночі — приємно.
 * - Колючки (кущ троянди, ягідний кущ, кактус): поранилась — місце стає «поганим» (CowMemory) і корова його обходить.
 */
public class CowTouch {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int PET_COOLDOWN = 20;            // гладити можна раз на 1 с
    public static final int PET_TRUST_EVERY = 200;        // довіра від гладіння — не частіше раз на 10 с
    public static final double FRONT_ANGLE = 60;          // спереду = гравець у межах ±60° від морди
    public static final double BACK_ANGLE = 150;          // ззаду = далі 150° (сліпа зона)
    public static final double OXY_FRONT = 8, OXY_SIDE = 4;           // окситоцин за одне погладжування
    public static final double CALM_FRONT = 4, CALM_SIDE = 2;         // кортизол −
    public static final int TRUST_FRONT = 2, TRUST_SIDE = 1;          // довіра + (раз на 10 с)
    public static final int STRANGER_TRUST = 0;           // довіра ≤ 0 — чужий, не підпускає
    public static final int STRANGER_FEAR = 15;           // ... і лякається трохи
    public static final int BACK_FEAR = 25;               // чіпали ззаду — переляк
    public static final double BACK_KICK = 0.3;           // ... і 30% хвицне (друг 50+ — не хвицає)
    public static final float BACK_KICK_DAMAGE = 1.0F;    // × ген «сила удару»
    public static final double BOND_GROOM = 5;            // вилизали одна одну — дружба +5 обом
    public static final double BOND_FORGET = 1.0 / 2400;  // дружба забувається на 1 за 40 хв (за секунду)
    public static final int BONDS_MAX = 50;               // пам'ятає до 50 корів
    public static final double OXY_GROOM = 6;             // вилизування — окситоцин обом
    public static final double COLD_BELOW = 0.15;         // холодно: температура біому нижче 0,15 (сніжні) ...
    public static final double COLD_WET_STRESS = 0.3;     // ... і мокра — кортизол +0,3 за секунду
    public static final double COSY_CALM = 0.2;           // уночі під дахом суха й не спекотно — кортизол −0,2/с

    private final HungryCow cow;
    private final Map<UUID, Double> bonds = new HashMap<>();   // дружба з коровами (зберігається)
    private final Map<UUID, String> bondNames = new HashMap<>();
    private int nextPet = 0, nextPetTrust = 0;
    public boolean wet, cold, cosy;

    public CowTouch(HungryCow cow) {
        this.cow = cow;
    }

    // ---------- гладити ----------

    // Кут між мордою корови й гравцем (0 — спереду, 180 — позаду)
    private double angleTo(Vec3 p) {
        float toTarget = (float) (Mth.atan2(p.z - cow.getZ(), p.x - cow.getX()) * (180F / Math.PI)) - 90F;
        return Math.abs(Mth.wrapDegrees(toTarget - cow.yBodyRot));
    }

    // Гравець гладить (присів + ПКМ порожньою рукою). true — оброблено
    public boolean pet(Player player) {
        if (cow.tickCount < nextPet) return true;
        nextPet = cow.tickCount + PET_COOLDOWN;
        int trust = cow.trustOf(player);
        double angle = angleTo(player.position());
        if (angle > BACK_ANGLE) {
            // ззаду, у сліпій зоні: лякається, інколи хвицає
            cow.fear = Math.min(100, cow.fear + BACK_FEAR);
            if (trust < CowTrust.FRIEND_FROM && cow.getRandom().nextDouble() < BACK_KICK) {
                float dmg = (float) (BACK_KICK_DAMAGE * cow.gene(CowGenes.Gene.KICK_STRENGTH));
                player.hurt(cow.damageSources().mobAttack(cow), dmg);
                cow.tell(cow.family.name + " злякалась дотику ззаду й хвицнула");
            } else {
                cow.tell(cow.family.name + " смикнулась: її торкнулись ззаду");
            }
            return true;
        }
        if (trust <= STRANGER_TRUST) {
            cow.fear = Math.min(100, cow.fear + STRANGER_FEAR);
            cow.tell(cow.family.name + " не підпускає чужого — відходить");
            Vec3 away = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(cow, 4, 2, player.position());
            if (away != null) cow.getNavigation().moveTo(away.x, away.y, away.z, 1.1);
            return true;
        }
        boolean front = angle <= FRONT_ANGLE;
        cow.chem.onPetted(front ? OXY_FRONT : OXY_SIDE, front ? CALM_FRONT : CALM_SIDE);
        cow.fear = Math.max(0, cow.fear - (front ? 6 : 3));
        if (cow.tickCount >= nextPetTrust) {
            nextPetTrust = cow.tickCount + PET_TRUST_EVERY;
            cow.addTrust(player, front ? TRUST_FRONT : TRUST_SIDE, front ? "гладили шию" : "гладили бік", false);
        }
        hearts(front ? 2 : 1);
        cow.getNavigation().stop();
        cow.getLookControl().setLookAt(player, 10F, 10F);
        return true;
    }

    public void hearts(int n) {
        if (cow.level() instanceof ServerLevel s) {
            s.sendParticles(ParticleTypes.HEART, cow.getX(), cow.getEyeY() + 0.4, cow.getZ(), n, 0.3, 0.2, 0.3, 0);
        }
    }

    // ---------- дружба й вилизування ----------

    public double bond(HungryCow other) {
        return bonds.getOrDefault(other.getUUID(), 0.0);
    }

    public void addBond(HungryCow other, double delta) {
        bonds.merge(other.getUUID(), delta, (a, b) -> Mth.clamp(a + b, -100, 100));
        bondNames.put(other.getUUID(), other.family.name);
        if (bonds.size() > BONDS_MAX) {
            UUID weakest = null;
            double w = Double.MAX_VALUE;
            for (Map.Entry<UUID, Double> e : bonds.entrySet()) {
                if (Math.abs(e.getValue()) < w) { w = Math.abs(e.getValue()); weakest = e.getKey(); }
            }
            if (weakest != null) { bonds.remove(weakest); bondNames.remove(weakest); }
        }
    }

    // Вилизала (або її вилизали): обом приємно, дружба росте
    public void groomed(HungryCow other) {
        addBond(other, BOND_GROOM);
        cow.chem.onPetted(OXY_GROOM, CALM_SIDE);
        hearts(1);
    }

    public String bestFriend() {
        UUID best = null;
        double v = 10;
        for (Map.Entry<UUID, Double> e : bonds.entrySet()) {
            if (e.getValue() > v) { v = e.getValue(); best = e.getKey(); }
        }
        return best == null ? null : bondNames.getOrDefault(best, "?");
    }

    // ---------- шкіра (раз на секунду) ----------

    public void secondTick() {
        bonds.replaceAll((k, v) -> v > 0 ? Math.max(0, v - BOND_FORGET) : Math.min(0, v + BOND_FORGET));
        wet = cow.isInWater() || cow.rainedOn();
        var p = cow.blockPosition();
        boolean roof = CowHome.hasRoof(cow.level(), p);
        cold = !roof && cow.level().getBiome(p).value().getBaseTemperature() < COLD_BELOW;
        cosy = roof && !wet && cow.isNightTime() && cow.heat.heat < 30;
        if (cold && wet) cow.chem.addStress(COLD_WET_STRESS);
        if (cosy) cow.chem.onPetted(0, COSY_CALM);
    }

    // Поранилась колючками (троянда, ягідний кущ, кактус): це місце погане — обходитиме
    public void onThorns() {
        cow.memory.addPlace(cow.blockPosition(), CowMemory.PLACE_HURT);
        cow.tell(cow.family.name + " вкололась — запам'ятала це місце");
    }

    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("#Дотик");
        out.add("Гладити: присісти + ПКМ порожньою рукою. Спереду (шия) — найприємніше, збоку — добре, ззаду — лякається.");
        out.add("Чужого (довіра 0 і менше) не підпускає.");
        StringBuilder skin = new StringBuilder("Шкіра: ");
        skin.append(wet ? "мокра" : "суха");
        if (cold) skin.append(", холодно");
        if (cow.heat.heat >= 30) skin.append(", спекотно");
        if (cosy) skin.append(", затишно");
        out.add(skin.toString());
        String f = bestFriend();
        out.add("Найкраща подруга: " + (f != null ? f : "немає"));
        int n = 0;
        for (Map.Entry<UUID, Double> e : bonds.entrySet()) {
            if (n >= 4 || Math.abs(e.getValue()) < 5) continue;
            out.add("- " + bondNames.getOrDefault(e.getKey(), "?") + ": дружба " + Math.round(e.getValue()));
            n++;
        }
        return out;
    }

    public void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Double> e : bonds.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("Id", e.getKey());
            c.putDouble("V", e.getValue());
            c.putString("N", bondNames.getOrDefault(e.getKey(), ""));
            list.add(c);
        }
        tag.put("Bonds", list);
    }

    public void load(CompoundTag tag) {
        bonds.clear();
        bondNames.clear();
        for (Tag t : tag.getList("Bonds", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            bonds.put(c.getUUID("Id"), c.getDouble("V"));
            bondNames.put(c.getUUID("Id"), c.getString("N"));
        }
    }
}
