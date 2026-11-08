package com.gigongibus.hungrycows;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ім'я, мати теляти (корова або гравець), родинне дерево і діти.
 * Усі числа зібрані на початку файлу.
 */
public class CowFamily {

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double MOTHER_RANGE = 25.0;     // з якої відстані теля впізнає матір і йде до неї
    public static final int FORGET_SECONDS = 300;       // через стільки секунд окремо теля й мати забувають одне одного (5 хв)
    public static final int TREE_DEPTH = 4;             // скільки поколінь пам'ятати (мати → ... → прапрабабуся)

    private static final String[][] LABELS = {
            {"Мати", "Батько"},
            {"Бабуся", "Дідусь"},
            {"Прабабуся", "Прадідусь"},
            {"Прапрабабуся", "Прапрадідусь"}
    };

    private final HungryCow cow;
    public String name;

    // хто зараз мати теляти
    private UUID motherId = null;           // корова-мати (рідна або прийомна)
    private String motherName = "";
    private boolean fosterMother = false;
    private UUID playerMotherId = null;     // гравець, що вигодував теля молоком
    private String playerMotherName = "";
    private int separatedSeconds = 0;
    private BlockPos motherDeathPos = null; // де померла мати (теля ходить біля цього місця, поки не забуде)
    private boolean lostMotherToDeath = false; // мати померла: інші самки приймають рідше

    // рідні предки: {M: {Name, M, F}, F: {Name, M, F}}
    private CompoundTag ancestors = new CompoundTag();
    private ListTag children = new ListTag();

    public CowFamily(HungryCow cow) {
        this.cow = cow;
        this.name = CowNames.random(cow.repro.isFemale(), cow.getRandom());
    }

    // ---------- мати ----------

    public HungryCow findMother() {
        if (motherId == null || !(cow.level() instanceof ServerLevel level)) return null;
        Entity e = level.getEntity(motherId);
        return e instanceof HungryCow mother && mother.isAlive() ? mother : null;
    }

    public Player findPlayerMother() {
        return playerMotherId == null ? null : cow.level().getPlayerByUUID(playerMotherId);
    }

    public UUID motherIdOrNull() {
        return motherId;
    }

    public BlockPos motherDeathPos() {
        return motherDeathPos;
    }

    public boolean lostMotherToDeath() {
        return lostMotherToDeath;
    }

    // Мати померла поруч: теля ходить біля місця смерті, поки не забуде її (FORGET_SECONDS)
    public void onMotherDied(BlockPos where) {
        motherDeathPos = where.immutable();
        lostMotherToDeath = true;
        cow.tell("Теля " + name + " залишилось без матері " + motherName);
    }

    public boolean hasCowMother() {
        return motherId != null;
    }

    public boolean hasPlayerMother() {
        return playerMotherId != null;
    }

    public boolean isOrphan() {
        return motherId == null && playerMotherId == null;
    }

    // Прийомна мати прийняла теля
    public void adoptBy(HungryCow foster) {
        motherId = foster.getUUID();
        motherName = foster.family.name;
        fosterMother = true;
        separatedSeconds = 0;
        motherDeathPos = null;
        lostMotherToDeath = false;
        cow.tell(foster.family.name + " прийняла теля " + name);
    }

    // Гравець вигодовує теля-сироту молоком
    public void adoptByPlayer(Player player) {
        playerMotherId = player.getUUID();
        playerMotherName = player.getName().getString();
        motherDeathPos = null;
        lostMotherToDeath = false;
        cow.tell(player.getName().getString() + " тепер мати теляти " + name);
    }

    // Раз на секунду: теля, яке довго не бачило матір, забуває її
    public void secondTick() {
        if (!cow.isBaby() || motherId == null) return;
        HungryCow mother = findMother();
        if (mother != null && cow.distanceToSqr(mother) <= MOTHER_RANGE * MOTHER_RANGE) {
            separatedSeconds = 0;
            return;
        }
        if (++separatedSeconds >= FORGET_SECONDS) {
            cow.tell("Теля " + name + " і " + motherName + " забули одне одного");
            motherId = null;
            motherName = "";
            fosterMother = false;
            separatedSeconds = 0;
            motherDeathPos = null;   // lostMotherToDeath лишається: інші самки приймуть рідше
        }
    }

    // Теля виросло: якщо його вигодував гравець, довіра до нього +200 (найкращий друг)
    public void onGrownUp() {
        motherId = null;
        motherName = "";
        fosterMother = false;
        if (playerMotherId != null) {
            cow.trust.bond(playerMotherId);
            cow.tell(name + " виросла і вважає " + playerMotherName + " своєю матір'ю: довіра " + CowTrust.MAX);
        }
    }

    // ---------- родинне дерево ----------

    // Вузол дерева для цієї корови: ім'я і предки (обрізано до depth поколінь, включно з нею)
    public CompoundTag asNode() {
        CompoundTag node = new CompoundTag();
        node.putString("Name", name);
        node.putUUID("Id", cow.getUUID());   // для спорідненості (імена повторюються)
        copyParents(ancestors, node, TREE_DEPTH - 1);
        return node;
    }

    private static void copyParents(CompoundTag from, CompoundTag to, int levels) {
        if (levels <= 0) return;
        for (String key : new String[]{"M", "F"}) {
            if (!from.contains(key)) continue;
            CompoundTag src = from.getCompound(key);
            CompoundTag dst = new CompoundTag();
            dst.putString("Name", src.getString("Name"));
            if (src.hasUUID("Id")) dst.putUUID("Id", src.getUUID("Id"));
            copyParents(src, dst, levels - 1);
            to.put(key, dst);
        }
    }

    // Для новонародженого теляти
    public void setupNewborn(HungryCow mother, CompoundTag fatherNode) {
        ancestors = new CompoundTag();
        ancestors.put("M", mother.family.asNode());
        if (fatherNode != null) ancestors.put("F", fatherNode.copy());
        motherId = mother.getUUID();
        motherName = mother.family.name;
        fosterMother = false;
    }

    public void addChild(String childName) {
        children.add(StringTag.valueOf(childName));
    }

    // Присісти + ПКМ порожньою рукою: усе про корову в чат (поки для тестів)
    public void showTo(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("=== " + name + " [" + (cow.repro.isFemale() ? "Ж" : "Ч") + "]" + (cow.isBaby() ? " (теля)" : "") + " ===");
        if (cow.repro.isFemale() && !cow.isBaby()) {
            lines.add("Телят народила: " + cow.repro.births() + " з " + cow.repro.birthLimit()
                    + (cow.repro.isExhausted() ? " (більше не може)" : ""));
            String milk = cow.milk.label();
            lines.add(milk != null ? milk + " з " + cow.milk.max() : "Молока немає");
            lines.add("Раціон: " + cow.milk.dietText());
        }
        lines.addAll(familyLines());
        lines.addAll(cow.character.lines());
        for (String line : lines) player.sendSystemMessage(Component.literal(line));
    }

    // Хто доглядає, предки, діти (для чату й книги)
    public List<String> familyLines() {
        List<String> lines = new ArrayList<>();
        if (cow.isBaby()) {
            if (motherId != null) {
                lines.add("Доглядає: " + motherName + (fosterMother ? " (прийомна мати)" : ""));
            } else if (playerMotherId != null) {
                lines.add("Доглядає: гравець " + playerMotherName);
            } else {
                lines.add("Сирота: шукає іншу матір");
            }
        } else if (playerMotherId != null) {
            lines.add("Вигодував(ла): " + playerMotherName);
        }
        if (ancestors.isEmpty()) {
            lines.add("Предки: невідомі");
        } else {
            printParents(lines, ancestors, 0, "");
        }
        if (!children.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < children.size(); i++) names.add(children.getString(i));
            lines.add("Діти (" + names.size() + "): " + String.join(", ", names));
        }
        return lines;
    }

    private static void printParents(List<String> lines, CompoundTag node, int depth, String indent) {
        if (depth >= LABELS.length) return;
        String[] keys = {"M", "F"};
        for (int i = 0; i < 2; i++) {
            if (!node.contains(keys[i])) continue;
            CompoundTag parent = node.getCompound(keys[i]);
            lines.add(indent + LABELS[depth][i] + ": " + parent.getString("Name"));
            printParents(lines, parent, depth + 1, indent + "   ");
        }
    }

    // Коротко для напису над теляти
    public String calfLabel() {
        if (motherId != null) return "Мати: " + motherName + (fosterMother ? " (прийомна)" : "");
        if (playerMotherId != null) return "Мати: " + playerMotherName;
        return "Сирота";
    }

    // ---------- збереження ----------

    public void save(CompoundTag tag) {
        tag.putString("CowName", name);
        if (motherId != null) tag.putUUID("MotherId", motherId);
        tag.putString("MotherName", motherName);
        tag.putBoolean("FosterMother", fosterMother);
        if (playerMotherId != null) tag.putUUID("PlayerMotherId", playerMotherId);
        tag.putString("PlayerMotherName", playerMotherName);
        tag.putInt("SeparatedSeconds", separatedSeconds);
        if (motherDeathPos != null) tag.putLong("MotherDeathPos", motherDeathPos.asLong());
        tag.putBoolean("LostMotherToDeath", lostMotherToDeath);
        tag.put("Ancestors", ancestors);
        tag.put("Children", children);
    }

    public void load(CompoundTag tag) {
        // стара корова без імені: ім'я за статтю, яку вже завантажено
        name = tag.contains("CowName") ? tag.getString("CowName") : CowNames.random(cow.repro.isFemale(), cow.getRandom());
        motherId = tag.hasUUID("MotherId") ? tag.getUUID("MotherId") : null;
        motherName = tag.getString("MotherName");
        fosterMother = tag.getBoolean("FosterMother");
        playerMotherId = tag.hasUUID("PlayerMotherId") ? tag.getUUID("PlayerMotherId") : null;
        playerMotherName = tag.getString("PlayerMotherName");
        separatedSeconds = tag.getInt("SeparatedSeconds");
        motherDeathPos = tag.contains("MotherDeathPos") ? BlockPos.of(tag.getLong("MotherDeathPos")) : null;
        lostMotherToDeath = tag.getBoolean("LostMotherToDeath");
        ancestors = tag.getCompound("Ancestors");
        children = tag.getList("Children", Tag.TAG_STRING);
    }
}
