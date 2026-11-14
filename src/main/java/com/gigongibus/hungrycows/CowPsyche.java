package com.gigongibus.hungrycows;

import com.gigongibus.hungrycows.CowCharacter.Trait;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Психологічні стани (план Fij, етап 9). Поки без анімацій — напис над головою (cow.status) і книга.
 * - Травма: дуже сильна подія (≥ 0,75) з шансом від чутливості до стресу й полохливості. Місце, запах чи той, хто вчинив, —
 *   лякають знову. Минає повільно; швидше, коли поруч подруги, гладять, вилизують.
 * - Фобія: одна сильна або 3 погані події з одним і тим самим (вода, моб, гравець) — боїться саме цього. Минає дуже повільно.
 * - Вивчена безпорадність: довго погано (самопочуття ≈ доба нижче −30) — менше тікає, менше їсть, більше лежить. Минає, коли краще.
 * - Туга: загинула подруга чи теля — гірше їсть, мукає, ходить на те місце (GrieveGoal і для дорослих). Минає за ~3 доби.
 * - Стереотипії: довго нудно, тісно чи самотньо — крутить язиком, лиже паркан, хитається. Минає в кращих умовах і в стаді.
 * - Задоволеність: довго добре — спокійніша, більше молока, рідше хворіє.
 * - Гра телят: ai/PlayGoal.
 */
public class CowPsyche {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double DAY_SECONDS = 1200;          // доба гри = 1200 с
    // травма
    public static final double TRAUMA_FROM = 0.75;          // з такої сили спогаду може бути травма ...
    public static final double TRAUMA_BASE = 0.2, TRAUMA_SENS = 0.6;   // ... шанс 20% + 60% × (чутливість до стресу + полохливість)/2
    public static final double TRAUMA_HEAL_PER_DAY = 0.1;   // минає на 0,1 за добу ...
    public static final double TRAUMA_CARE = 3;             // ... утричі швидше, коли поруч подруга / гладять / вилизують
    public static final double TRAUMA_PLACE = 6;            // місце травми — ближче 6 бл.
    public static final int TRAUMA_FEAR = 15;               // згадала — страх +15 (раз на 10 с)
    public static final int TRIGGER_EVERY = 10;
    // фобія
    public static final int PHOBIA_EVENTS = 3;              // 3 погані події з тим самим — фобія
    public static final double PHOBIA_HEAL_PER_DAY = 0.02;  // минає дуже повільно (≈ 50 діб)
    public static final int PHOBIA_FEAR = 10;               // бачить/у тому — страх +10 (раз на 5 с)
    public static final float PHOBIA_HIT_DAMAGE = 4.0F;     // гравець ударив одразу на 4+ (2 серця) — фобія з одного разу (Fij)
    // безпорадність
    public static final double WELL_AVG_DAYS = 1;           // середнє самопочуття за ~добу
    public static final double HELPLESS_BELOW = -30;        // середнє нижче −30 — безпорадність росте
    public static final double HELPLESS_RISE_PER_DAY = 0.5, HELPLESS_FALL_PER_DAY = 1.0;
    // туга
    public static final double GRIEF_DAYS = 3;              // туга минає за ~3 доби (з подругами поруч — швидше)
    public static final int GRIEF_MOO_EVERY = 45;           // мукає раз на ~45 с
    // стереотипії
    public static final double STEREO_RISE_PER_DAY = 1.0, STEREO_FALL_PER_DAY = 2.0;
    public static final double LICK_FENCE_REACH = 2.0;      // «лиже паркан» — паркан не далі 2 бл.
    public static final double STEREO_FROM = 0.6;           // з 0,6 — видно (крутить язиком, лиже паркан, хитається)
    public static final int CROWD_COWS = 4, CROWD_RADIUS = 3;   // тісно: 4+ корови ближче 3 бл.
    public static final double BORED_STIM = 0.2;            // нудно: «стимуляція» (нове, різна їжа, пасовище, вилизування) нижче 0,2
    public static final double STIM_DECAY_PER_DAY = 1.0;
    // задоволеність
    public static final double CONTENT_ABOVE = 30;          // середнє самопочуття понад 30 ...
    public static final double CONTENT_RISE_PER_DAY = 0.5, CONTENT_FALL_PER_DAY = 1.0;
    public static final double CONTENT_CALM = 0.2;          // задоволена — поріг страху до +20%
    public static final double CONTENT_MILK = 0.1;          // ... молоко до +10%
    public static final double CONTENT_HEALTH = 0.2;        // ... хворіє до −20%
    public static final double HELPLESS_FLEE = 0.5;         // безпорадна — поріг утечі до +50% (менше тікає)
    public static final double HELPLESS_APPETITE = 0.3;     // ... апетит до −30%
    public static final double HELPLESS_TIRED = 0.5;        // ... втома до +50% (більше лежить)
    public static final double GRIEF_APPETITE = 0.4;        // туга — апетит до −40%

    private static class Trauma {
        BlockPos place;
        String who;
        double level;
    }

    private final HungryCow cow;
    private final List<Trauma> traumas = new ArrayList<>();
    private final Map<String, Integer> badCounts = new HashMap<>();   // що → скільки поганих подій
    private final Map<String, Double> phobias = new HashMap<>();      // що → сила фобії
    public double helpless = 0, grief = 0, stereotypy = 0, content = 0;
    private double wellAvg = 0, stimulation = 0.5;
    private String griefName = "";
    public BlockPos griefPlace = null;
    private String stereoKind = "";
    private int seconds = 0;

    public CowPsyche(HungryCow cow) {
        this.cow = cow;
    }

    private double susceptibility() {
        return (cow.character.fraction(Trait.STRESS_SENSITIVITY) + cow.character.fraction(Trait.FEARFULNESS)) / 2.0;
    }

    // Подія (з CowEpisodes.remember): травма, фобія, туга
    public void onEpisode(CowEpisodes.Episode e) {
        boolean bad = e.kind == CowEpisodes.Kind.HIT || e.kind == CowEpisodes.Kind.ATTACK || e.kind == CowEpisodes.Kind.NEAR_DROWN;
        if (bad) {
            String what = e.kind == CowEpisodes.Kind.NEAR_DROWN ? "вода" : e.who;
            int n = badCounts.merge(what, 1, Integer::sum);
            if ((e.strength >= TRAUMA_FROM || n >= PHOBIA_EVENTS) && !what.isEmpty() && !phobias.containsKey(what)) {
                phobias.put(what, 1.0);
                cow.tell(cow.family.name + " тепер боїться: " + nice(what) + " (фобія)");
            }
        }
        if (e.strength >= TRAUMA_FROM && (bad || e.kind == CowEpisodes.Kind.CALF_DEATH || e.kind == CowEpisodes.Kind.FRIEND_DEATH)
                && cow.getRandom().nextDouble() < TRAUMA_BASE + TRAUMA_SENS * susceptibility()) {
            Trauma t = new Trauma();
            t.place = e.pos;
            t.who = e.who;
            t.level = 1.0;
            traumas.add(t);
            if (traumas.size() > 5) traumas.remove(0);
            cow.tell(cow.family.name + " отримала травму: " + e.kind.title);
        }
        if (e.kind == CowEpisodes.Kind.CALF_DEATH || e.kind == CowEpisodes.Kind.FRIEND_DEATH) {
            grief = Math.max(grief, e.strength);
            griefName = e.who;
            griefPlace = e.pos;
            cow.status("тужить за " + e.who);
        }
        if (e.kind == CowEpisodes.Kind.TASTY || e.kind == CowEpisodes.Kind.PETTED) stimulate(0.1);
    }

    // Сильний удар гравця (урон від PHOBIA_HIT_DAMAGE) — фобія до цього гравця одразу, без 3 подій (Fij: «думаю так»)
    public void onStrongHit(Player player, float damage) {
        if (player == null || damage < PHOBIA_HIT_DAMAGE) return;
        String who = player.getName().getString();
        if (phobias.getOrDefault(who, 0.0) >= 0.99) return;
        phobias.put(who, 1.0);
        cow.tell(cow.family.name + " тепер боїться: " + who + " (фобія від сильного удару)");
    }

    // Щось нове й цікаве (досліджувала, різна їжа, пасовище, вилизування) — менше нудно
    public void stimulate(double amount) {
        stimulation = Math.min(1, stimulation + amount);
    }

    private static String nice(String what) {
        return what.replace("m:minecraft:", "").replace("minecraft:", "");
    }

    public double phobia(String what) {
        return phobias.getOrDefault(what, 0.0);
    }

    // ---------- раз на секунду ----------

    public void secondTick() {
        seconds++;
        double perSec = 1.0 / DAY_SECONDS;
        boolean care = cow.tickCount - cow.touch.lastTouchedTick < 1200 || cow.herd.size() >= 3;
        // самопочуття в середньому за добу
        double w = cow.brain.wellbeing();
        wellAvg += (w - wellAvg) * perSec / WELL_AVG_DAYS;
        // травми: тригери і загоєння
        for (Trauma t : traumas) t.level = Math.max(0, t.level - TRAUMA_HEAL_PER_DAY * perSec * (care ? TRAUMA_CARE : 1));
        traumas.removeIf(t -> t.level <= 0.05);
        if (seconds % TRIGGER_EVERY == 0) {
            for (Trauma t : traumas) {
                boolean place = t.place != null && t.place.distSqr(cow.blockPosition()) < TRAUMA_PLACE * TRAUMA_PLACE;
                boolean who = !t.who.isEmpty() && (cow.smell.smellsKey(t.who) || sees(t.who));
                if (place || who) {
                    cow.fear = Math.min(100, cow.fear + (int) Math.round(TRAUMA_FEAR * t.level));
                    cow.chem.addStress(5 * t.level);
                    cow.status(place ? "згадала, що тут сталося" : "згадала страшне");
                    break;
                }
            }
        }
        // фобії: вода, моби, гравці
        phobias.replaceAll((k, v) -> Math.max(0, v - PHOBIA_HEAL_PER_DAY * perSec));
        phobias.values().removeIf(v -> v <= 0.05);
        if (seconds % 5 == 0) {
            for (Map.Entry<String, Double> e : phobias.entrySet()) {
                boolean now = e.getKey().equals("вода") ? cow.isInWater() || nearWater() : sees(e.getKey()) || cow.smell.smellsKey(e.getKey());
                if (now) {
                    cow.fear = Math.min(100, cow.fear + (int) Math.round(PHOBIA_FEAR * e.getValue()));
                    cow.status("боїться " + (e.getKey().equals("вода") ? "води" : nice(e.getKey())));
                    break;
                }
            }
        }
        // безпорадність і задоволеність — від середнього самопочуття
        if (wellAvg < HELPLESS_BELOW) helpless = Math.min(1, helpless + HELPLESS_RISE_PER_DAY * perSec);
        else helpless = Math.max(0, helpless - HELPLESS_FALL_PER_DAY * perSec);
        if (wellAvg > CONTENT_ABOVE) content = Math.min(1, content + CONTENT_RISE_PER_DAY * perSec);
        else content = Math.max(0, content - CONTENT_FALL_PER_DAY * perSec);
        // туга
        if (grief > 0) {
            grief = Math.max(0, grief - perSec / GRIEF_DAYS * (care ? 1.5 : 1));
            if (grief > 0.3 && seconds % GRIEF_MOO_EVERY == 0) {
                cow.playSound(SoundEvents.COW_AMBIENT, 1.2F, 0.7F);
                cow.status("тужить за " + griefName);
            }
            if (grief <= 0) griefPlace = null;
        }
        // стереотипії: нудно, тісно чи самотньо довго
        stimulation = Math.max(0, stimulation - STIM_DECAY_PER_DAY * perSec);
        if (cow.recentlyAte(200) && cow.memory.placeValue(cow.blockPosition()) >= 0) stimulation = Math.min(1, stimulation + 0.002);
        int close = cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(CROWD_RADIUS), c -> c != cow).size();
        boolean bad = stimulation < BORED_STIM || close >= CROWD_COWS || cow.chem.isLonely();
        stereotypy = bad ? Math.min(1, stereotypy + STEREO_RISE_PER_DAY * perSec) : Math.max(0, stereotypy - STEREO_FALL_PER_DAY * perSec);
        if (stereotypy >= STEREO_FROM && cow.getNavigation().isDone() && cow.getDeltaMovement().horizontalDistanceSqr() < 0.0004
                && seconds % 8 == 0) doStereotypy();   // лише коли стоїть (не йде за гравцем)
    }

    private boolean sees(String who) {
        for (Player p : cow.level().players()) {
            if (p.getName().getString().equals(who) && cow.vision.canSee(p)) return true;
        }
        if (who.startsWith("m:")) {
            return cow.vision.count(e -> ("m:" + net.minecraft.world.entity.EntityType.getKey(e.getType())).equals(who)) > 0;
        }
        return false;
    }

    private boolean nearWater() {
        BlockPos o = cow.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, -1, -2), o.offset(2, 0, 2))) {
            if (cow.level().getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)) return true;
        }
        return false;
    }

    // Повторювана дія без мети (поки без анімацій — погляд/поворот і напис)
    private void doStereotypy() {
        // «лиже паркан» — лише коли паркан справді поруч (Fij: лизала, хоча паркану поруч не було, бо вид вибирався раз назавжди)
        BlockPos post = cow.touch.findScratchPost();
        boolean fence = post != null && post.distToCenterSqr(cow.position()) <= LICK_FENCE_REACH * LICK_FENCE_REACH;
        if (stereoKind.isEmpty() || (stereoKind.equals("лиже паркан") && !fence)) {
            String[] kinds = fence ? new String[]{ "крутить язиком", "лиже паркан", "хитається" } : new String[]{ "крутить язиком", "хитається" };
            stereoKind = kinds[cow.getRandom().nextInt(kinds.length)];
        }
        switch (stereoKind) {
            case "лиже паркан" -> cow.getLookControl().setLookAt(post.getX() + 0.5, post.getY() + 0.5, post.getZ() + 0.5);
            case "хитається" -> cow.setYBodyRot(cow.yBodyRot + (seconds % 16 < 8 ? 10 : -10));
            default -> cow.getLookControl().setLookAt(cow.getX() + cow.getLookAngle().x, cow.getEyeY() - 0.4, cow.getZ() + cow.getLookAngle().z);
        }
        cow.status(stereoKind);
    }

    // ---------- вплив на інше ----------

    public double fleeThresholdFactor() {   // безпорадна менше тікає, задоволена спокійніша
        return 1.0 + HELPLESS_FLEE * helpless + CONTENT_CALM * content;
    }

    public double appetiteFactor() {
        return (1.0 - HELPLESS_APPETITE * helpless) * (1.0 - GRIEF_APPETITE * grief);
    }

    public double tiredFactor() {
        return 1.0 + HELPLESS_TIRED * helpless;
    }

    public double milkFactor() {
        return 1.0 + CONTENT_MILK * content;
    }

    public double sicknessFactor() {
        return 1.0 - CONTENT_HEALTH * content;
    }

    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("#Настрій і стани");
        out.add("Самопочуття в середньому за добу: " + Math.round(wellAvg));
        boolean any = false;
        for (Trauma t : traumas) {
            out.add("Травма (" + (t.who.isEmpty() ? "місце" : nice(t.who)) + "): " + Math.round(t.level * 100) + "%");
            any = true;
        }
        for (Map.Entry<String, Double> e : phobias.entrySet()) {
            out.add("Фобія: боїться " + (e.getKey().equals("вода") ? "води" : nice(e.getKey())) + " (" + Math.round(e.getValue() * 100) + "%)");
            any = true;
        }
        if (helpless > 0.1) { out.add("Вивчена безпорадність: " + Math.round(helpless * 100) + "% (менше тікає, їсть, більше лежить)"); any = true; }
        if (grief > 0.05) { out.add("Туга за " + griefName + ": " + Math.round(grief * 100) + "%"); any = true; }
        if (stereotypy > 0.2) { out.add("Стереотипія" + (stereoKind.isEmpty() ? "" : " (" + stereoKind + ")") + ": " + Math.round(stereotypy * 100) + "%"); any = true; }
        if (content > 0.1) { out.add("Задоволена: " + Math.round(content * 100) + "% (спокійніша, більше молока, рідше хворіє)"); any = true; }
        if (!any) out.add("Особливих станів немає");
        return out;
    }

    public void save(CompoundTag tag) {
        CompoundTag c = new CompoundTag();
        c.putDouble("Helpless", helpless);
        c.putDouble("Grief", grief);
        c.putString("GriefName", griefName);
        if (griefPlace != null) c.putLong("GriefPlace", griefPlace.asLong());
        c.putDouble("Stereo", stereotypy);
        c.putString("StereoKind", stereoKind);
        c.putDouble("Content", content);
        c.putDouble("WellAvg", wellAvg);
        c.putDouble("Stim", stimulation);
        ListTag tl = new ListTag();
        for (Trauma t : traumas) {
            CompoundTag x = new CompoundTag();
            if (t.place != null) x.putLong("P", t.place.asLong());
            x.putString("W", t.who);
            x.putDouble("L", t.level);
            tl.add(x);
        }
        c.put("Traumas", tl);
        CompoundTag ph = new CompoundTag();
        phobias.forEach(ph::putDouble);
        c.put("Phobias", ph);
        CompoundTag bc = new CompoundTag();
        badCounts.forEach(bc::putInt);
        c.put("BadCounts", bc);
        tag.put("Psyche", c);
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("Psyche")) return;
        CompoundTag c = tag.getCompound("Psyche");
        helpless = c.getDouble("Helpless");
        grief = c.getDouble("Grief");
        griefName = c.getString("GriefName");
        griefPlace = c.contains("GriefPlace") ? BlockPos.of(c.getLong("GriefPlace")) : null;
        stereotypy = c.getDouble("Stereo");
        stereoKind = c.getString("StereoKind");
        content = c.getDouble("Content");
        wellAvg = c.getDouble("WellAvg");
        stimulation = c.contains("Stim") ? c.getDouble("Stim") : 0.5;
        traumas.clear();
        for (Tag t : c.getList("Traumas", Tag.TAG_COMPOUND)) {
            CompoundTag x = (CompoundTag) t;
            Trauma tr = new Trauma();
            tr.place = x.contains("P") ? BlockPos.of(x.getLong("P")) : null;
            tr.who = x.getString("W");
            tr.level = x.getDouble("L");
            traumas.add(tr);
        }
        phobias.clear();
        CompoundTag ph = c.getCompound("Phobias");
        for (String k : ph.getAllKeys()) phobias.put(k, ph.getDouble(k));
        badCounts.clear();
        CompoundTag bc = c.getCompound("BadCounts");
        for (String k : bc.getAllKeys()) badCounts.put(k, bc.getInt(k));
    }
}
