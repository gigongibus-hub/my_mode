package com.gigongibus.hungrycows;

import com.gigongibus.hungrycows.CowCharacter.Trait;
import net.minecraft.core.BlockPos;

/**
 * Черга біля входу (ідея Fij): корови заходять у хлів і виходять з нього по одній.
 * Корова, що йде через вхід, чекає за 3–15 блоків від нього, якщо:
 *  - у проході вже інша корова (ближче 2,5 блока до входу), або
 *  - до того самого входу йде старша (ватажок перша, далі за рангом) і вона вже ближче 15 блоків.
 * Слабша з меншою домінантністю чекає далі. Чекає щонайбільше 20 с — потім іде (щоб не застрягти).
 * Цілі (SleepGoal, GoOutGoal) кажуть approach(вхід), поки йдуть до нього, і cancel(), коли зупинились.
 * Усі числа зібрані на початку файлу.
 */
public class CowGate {

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double DOORWAY = 2.5;        // «у проході» = ближче 2,5 блока до входу
    public static final double WAIT_MIN = 3;          // чекає щонайближче за 3 блоки від входу ...
    public static final double WAIT_MAX = 15;         // ... щонайдалі за 15 (домінантна ближче, слабша далі, + випадково)
    public static final double QUEUE_RANGE = 15;      // старша, що йде до того самого входу ближче 15 блоків, — проходить першою
    public static final double SAME_DOOR = 3;         // «той самий вхід» = входи ближче 3 блоків
    public static final double LOOK_RANGE = 32;       // кого перевіряє
    public static final int MAX_WAIT_TICKS = 400;     // чекає щонайбільше 20 с, потім іде
    public static final double BEHIND = 3;            // старша прямо позаду (ближче 3 бл. і далі від входу) — пройти не може, тож іде сама
    public static final int STUCK_TICKS = 60;         // у проході 3 с стоїть на місці — застрягла, прохід не займає
    public static final double STUCK_MOVE = 0.3;      // «стоїть» = за 3 с зрушила менше 0,3 бл.

    private final HungryCow cow;
    private BlockPos door = null;       // до якого входу зараз іде (null — ні до якого)
    private boolean reachedDoor = false; // уже була в проході
    private BlockPos passedDoor = null; // цей вхід уже пройшла (до cancel)
    private double waitDist;
    private int waitedTicks = 0;
    private int lastCheckTick = -1;
    private int lastApproachTick = -1000;
    private boolean told = false;
    private BlockPos memDoor = null;     // останній вхід, до якого стояла в черзі
    public static final int REMEMBER_TICKS = 600;     // перервали й знову йде до того самого входу за 30 с — та сама черга        // уже сказала в чат, що чекає (раз на підхід)
    private double stillX, stillZ;       // де стояла, коли почала стояти
    private int stillTicks = 0;          // скільки тіків майже не рухається
    public boolean waiting = false;
    public static final int STALE_TICKS = 60;          // ціль 3 с не кликала approach — у черзі вже не стоїть

    public CowGate(HungryCow cow) {
        this.cow = cow;
    }

    public BlockPos door() {
        return door;
    }

    // Справді йде через вхід зараз (а не лишився старий запис)
    public boolean active() {
        return door != null && cow.tickCount - lastApproachTick <= STALE_TICKS;
    }

    // Іде до входу (кожен крок ходьби). Уже пройдений вхід удруге в чергу не ставить
    public void approach(BlockPos d) {
        if (d == null) return;
        int sinceLast = cow.tickCount - lastApproachTick;
        lastApproachTick = cow.tickCount;
        if (passedDoor != null && passedDoor.distSqr(d) <= SAME_DOOR * SAME_DOOR) return;
        if (door != null && door.distSqr(d) <= SAME_DOOR * SAME_DOOR) {
            door = d;   // вхід трохи зсунувся — та сама черга (лічильник чекання не з нуля)
            return;
        }
        // той самий вхід після короткої перерви (ціль перервали й почали знову) — черга та сама: не з нуля, без нового повідомлення
        boolean same = memDoor != null && memDoor.distSqr(d) <= SAME_DOOR * SAME_DOOR && sinceLast <= REMEMBER_TICKS;
        door = d;
        memDoor = d;
        stillTicks = 0;
        reachedDoor = false;
        waiting = false;
        if (!same) {
            told = false;
            waitedTicks = 0;
            double dom = cow.character.fraction(Trait.DOMINANCE);
            waitDist = WAIT_MIN + (WAIT_MAX - WAIT_MIN) * (0.5 * (1 - dom) + 0.5 * cow.getRandom().nextDouble());
        }
    }

    // Ціль зупинилась: з черги вийшла
    public void cancel() {
        door = null;
        passedDoor = null;
        waiting = false;
        reachedDoor = false;
    }

    private double distTo(HungryCow c, BlockPos d) {
        return Math.sqrt(c.distanceToSqr(d.getX() + 0.5, d.getY(), d.getZ() + 0.5));
    }

    // Хто проходить першим: ватажок, потім домінантніша (рівні — за номером)
    private static boolean goesBefore(HungryCow a, HungryCow b) {
        boolean aLead = a.herd.isLeader(), bLead = b.herd.isLeader();
        if (aLead != bLead) return aLead;
        double da = CowHerd.dominance(a), db = CowHerd.dominance(b);
        if (da != db) return da > db;
        return a.getId() < b.getId();
    }

    // Пройшла вхід — з черги; чи треба зараз чекати (можна кликати й щотіку: час рахується за тіками)
    public boolean mustWait() {
        if (lastCheckTick == cow.tickCount) return waiting;   // цього тіку вже перевірено
        int dt = lastCheckTick < 0 ? 1 : Math.max(1, Math.min(20, cow.tickCount - lastCheckTick));
        lastCheckTick = cow.tickCount;
        if (door == null) { waiting = false; return false; }
        double d = distTo(cow, door);
        if (d <= DOORWAY) reachedDoor = true;
        // чи стоїть на місці (для інших: застрягла в проході — прохід не займає)
        if ((cow.getX() - stillX) * (cow.getX() - stillX) + (cow.getZ() - stillZ) * (cow.getZ() - stillZ) > STUCK_MOVE * STUCK_MOVE) {
            stillX = cow.getX();
            stillZ = cow.getZ();
            stillTicks = 0;
        } else {
            stillTicks += dt;
        }
        if (reachedDoor && d > DOORWAY + 0.5) {   // пройшла: прохід вільний для наступної
            passedDoor = door;
            door = null;
            waiting = false;
            return false;
        }
        boolean wait = false;
        if (!reachedDoor && d <= waitDist && waitedTicks < MAX_WAIT_TICKS) {
            for (HungryCow o : cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(LOOK_RANGE),
                    o -> o != cow && o.isAlive() && o.gate.active())) {
                if (o.gate.door.distSqr(door) > SAME_DOOR * SAME_DOOR) continue;
                double od = distTo(o, o.gate.door);
                // у проході інша (і йде, а не застрягла)
                if (od <= DOORWAY && !o.gate.waiting && o.gate.stillTicks < STUCK_TICKS) { wait = true; break; }
                // старша йде першою — але якщо вона прямо позаду, обійти не може (у вузькому проході стояли б обидві)
                boolean rightBehind = od > d && o.distanceTo(cow) < BEHIND;
                if (od <= QUEUE_RANGE && !o.gate.reachedDoor && !rightBehind && goesBefore(o, cow)) { wait = true; break; }
            }
        }
        if (wait && !told) {
            told = true;
            cow.tell(cow.family.name + " чекає своєї черги біля входу");
        }
        waiting = wait;
        if (wait) waitedTicks += dt;
        return wait;
    }

    // Стоїть у черзі: не йде, дивиться на вхід
    public void holdStill() {
        cow.getNavigation().stop();
        if (door != null) cow.getLookControl().setLookAt(door.getX() + 0.5, door.getY() + 1, door.getZ() + 0.5);
    }
}
