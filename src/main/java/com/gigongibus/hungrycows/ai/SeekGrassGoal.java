package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowFood;
import com.gigongibus.hungrycows.CowGenes;
import com.gigongibus.hungrycows.CowHerd;
import com.gigongibus.hungrycows.CowMemory;
import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.Regrowth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SeekGrassGoal extends Goal {
    // з якого голоду шукає їжу: HungryCow.hungryAt() (30, ген ±30%)
    private static final int SEARCH_RADIUS = 15;          // радіус пошуку їжі по горизонталі
    private static final int SEARCH_HEIGHT = 3;           // радіус пошуку по висоті
    private static final float FLOWER_CHANCE = 0.30F;     // шанс, що голодна корова піде до квітки замість звичайної їжі
    private static final float POPPY_CHANCE = 0.15F;      // шанс, що піде саме до маку замість звичайної їжі (входить у FLOWER_CHANCE)
    private static final int PLACED_BONUS = 30;           // бонус привабливості блоків, поставлених гравцем
    private static final int PLACED_EAT_TICKS = 200;      // скільки їсть такий блок (10 сек)
    private static final double DISTANCE_PENALTY = 2.0;   // скільки поживності «коштує» кожен блок шляху
    private static final int MAX_RUN_TICKS = 400;         // скільки максимум йти до однієї цілі (20 сек)
    private static final int MAX_PATH_CHECKS = 6;         // скільки шляхів перевіряти за один вибір
    private static final int MAX_REACHABLE_COMPARED = 3;  // скільки досяжних варіантів порівнювати
    private static final boolean DEBUG = true;            // писати в чат, коли їжа недосяжна

    // Апетит до квіток: чим більше число, тим частіше корова обирає цей вид
    private static final Map<Block, Integer> FLOWER_APPETITE = new HashMap<>();

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

    public SeekGrassGoal(HungryCow cow) {
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
        if (cow.isBaby()) return false;  // теля, поки мале, п'є лише молоко
        if (cow.choking) return false;   // подавлена корова не шукає їжі
        if (!cow.shouldSeekFood()) return false;   // вирішує мозок (або відчуття голоду, якщо мозок вимкнено)
        // раз на 10 секунд забуваємо, що вважали недосяжним
        if (cow.tickCount - lastClearTick > 200) {
            unreachable.clear();
            lastClearTick = cow.tickCount;
        }
        if (cooldown > 0) { cooldown--; return false; }
        target = findFood();
        // нічого не знайшла: наступний пошук приблизно за 4 с, а не за 1 с (пошук важкий для сервера)
        cooldown = target != null ? 10 : 40;
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
        return !cow.choking && cow.shouldSeekFood() && target != null;
    }

    // Ієрархія біля їжі: якщо сильніша корова йде до тієї ж трави (до 2 блоків від цілі), слабша поступається;
    // сильніша трохи штовхає її головою (без шкоди)
    private static final double SAME_FOOD = 2.0;
    private static final double YIELD_PUSH = 0.4;

    private boolean yieldToStronger() {
        if (target == null) return false;
        double mine = CowHerd.dominance(cow);
        for (HungryCow other : cow.level().getEntitiesOfClass(HungryCow.class, cow.getBoundingBox().inflate(6),
                c -> c != cow && c.isAlive() && c.foodTarget != null)) {
            if (other.foodTarget.distSqr(target) > SAME_FOOD * SAME_FOOD) continue;
            if (CowHerd.dominance(other) <= mine) continue;
            if (cow.distanceToSqr(other) < 3 * 3) {
                cow.knockback(YIELD_PUSH, other.getX() - cow.getX(), other.getZ() - cow.getZ());
                cow.hurtMarked = true;
                other.playSound(net.minecraft.sounds.SoundEvents.COW_STEP, 0.8F, 0.8F);
            }
            cow.tell(cow.family.name + " поступилась їжею " + other.family.name);
            return true;
        }
        return false;
    }

    private void resetEating() {
        eatTicks = 0;
        cow.eatingTicksLeft = 0;
    }

    @Override
    public void tick() {
        if (target == null) return; // tick() може викликатися ще раз, коли ціль уже з'їдена
        cow.foodTarget = target;
        if (cow.tickCount % 20 == 0 && yieldToStronger()) {
            unreachable.add(target);   // ця трава зайнята сильнішою: пошукає іншу
            target = null;
            cow.foodTarget = null;
            resetEating();
            return;
        }
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
        cow.foodTarget = null;
        stuckTicks = 0;
        runTicks = 0;
        resetEating();
    }

    // скільки голоду знімає трава з урахуванням гена «користь від трави»
    private int grassFood(int base) {
        return (int) Math.round(base * cow.gene(CowGenes.Gene.GRASS_DIGEST));
    }

    private void eatTarget(BlockState state) {
        Block block = state.getBlock();
        cow.onAte();   // для ремигання
        ServerLevel server = cow.level() instanceof ServerLevel sl ? sl : null;
        if (block == Blocks.GRASS_BLOCK) {
            cow.level().setBlockAndUpdate(target, Blocks.DIRT.defaultBlockState());
            if (server != null) Regrowth.grassEaten(server, target);   // через 4–6 хв знову трава
            cow.milk.recordDiet(block.asItem());
            cow.memory.rememberMeal(block.asItem());
            cow.hunger = Math.max(0, cow.hunger - grassFood(HungryCow.GRASS_BLOCK_FOOD));
            cow.heal(HungryCow.GRASS_BLOCK_HEAL);
        } else if (block == Blocks.MOSS_BLOCK) {
            Block[] soils = { Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT };
            Block soil = soils[cow.getRandom().nextInt(soils.length)];
            cow.level().setBlockAndUpdate(target, soil.defaultBlockState());
            if (server != null) Regrowth.mossEaten(server, target, soil);   // через 8–12 хв знову мох
            cow.milk.recordDiet(block.asItem());
            cow.memory.rememberMeal(block.asItem());
            cow.hunger = Math.max(0, cow.hunger - grassFood(HungryCow.MOSS_BLOCK_FOOD));
        } else if (block == Blocks.GRASS || block == Blocks.TALL_GRASS) {
            boolean tall = block == Blocks.TALL_GRASS;
            // у високої трави рослина «стоїть» на нижній половині
            BlockPos plantPos = tall && state.hasProperty(DoublePlantBlock.HALF)
                    && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER ? target.below() : target;
            cow.level().destroyBlock(target, false);
            if (server != null) Regrowth.plantEaten(server, plantPos);   // з шансом 30% знову виросте низька
            cow.milk.recordDiet(block.asItem());
            cow.memory.rememberMeal(block.asItem());
            cow.hunger = Math.max(0, cow.hunger - grassFood(tall ? HungryCow.TALL_PLANT_FOOD : HungryCow.PLANT_FOOD));
            cow.heal(tall ? HungryCow.TALL_PLANT_HEAL : HungryCow.PLANT_HEAL);
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
        if (b == Blocks.TALL_GRASS) return HungryCow.TALL_PLANT_FOOD;
        if (b == Blocks.GRASS) return HungryCow.PLANT_FOOD;
        if (b == Blocks.GRASS_BLOCK) return HungryCow.GRASS_BLOCK_FOOD;
        if (b == Blocks.MOSS_BLOCK) return HungryCow.MOSS_BLOCK_FOOD;
        CowFood f = CowFood.get(b.asItem());
        return f != null ? f.hunger : 10;
    }

    // Привабливість їжі: поживність (і бонус за блок гравця) мінус довжина шляху
    private double score(Candidate c, double distance) {
        double bonus = c.kind == Kind.PLACED ? PLACED_BONUS : 0;
        // пам'ять: відраза до цієї їжі й те, яке це місце (добре чи там били)
        double memory = -CowMemory.AVERSION_SCORE * cow.memory.aversion(c.block.asItem())
                + CowMemory.PLACE_SCORE * cow.memory.placeValue(c.pos);
        // ідея 9: вдень корова охочіше пасеться в полі (над травою видно небо)
        BlockPos stand = (c.kind == Kind.GRASS_BLOCK || c.kind == Kind.MOSS_BLOCK) ? c.pos.above() : c.pos;
        double field = cow.level().isDay() && cow.level().canSeeSky(stand) ? HungryCow.FIELD_BONUS : 0;
        return c.value + bonus - DISTANCE_PENALTY * distance + memory + field;
    }

    // «апетит» до виду квітки з урахуванням відрази
    private double flowerWeight(Block b) {
        return FLOWER_APPETITE.getOrDefault(b, 1) * (1.0 - cow.memory.aversion(b.asItem()));
    }

    // Шлях до їжі (null, якщо дійти не можна). Кожна перевірка зменшує ліміт.
    private Path pathTo(Candidate c) {
        checksLeft--;
        BlockPos stand = (c.kind == Kind.GRASS_BLOCK || c.kind == Kind.MOSS_BLOCK) ? c.pos.above() : c.pos;
        Path path = cow.getNavigation().createPath(stand, 1);
        return (path != null && path.canReach()) ? path : null;
    }

    // Недосяжна їжа і вся їжа поруч (до 4 блоків) теж вважається недосяжною
    private void markUnreachable(Candidate c, List<Candidate> list) {
        unreachable.add(c.pos);
        for (Candidate other : list) {
            if (other.pos.distSqr(c.pos) < 16) unreachable.add(other.pos);
        }
        if (unreachable.size() > 500) unreachable.clear();
    }

    // Найближча досяжна їжа зі списку
    private BlockPos firstReachable(List<Candidate> list) {
        list.sort(Comparator.comparingDouble((Candidate c) -> c.dist));
        for (Candidate c : list) {
            if (unreachable.contains(c.pos)) continue;
            if (checksLeft <= 0) return null;
            if (pathTo(c) != null) return c.pos;
            markUnreachable(c, list);
        }
        return null;
    }

    // Звичайна їжа: вигода = поживність мінус довжина шляху
    private BlockPos chooseNormal(List<Candidate> list, boolean emergency) {
        if (list.isEmpty()) return null;
        if (emergency) return firstReachable(list); // голодує: найближча досяжна

        list.sort(Comparator.comparingDouble(
                (Candidate c) -> -score(c, Math.sqrt(c.dist))));
        BlockPos best = null;
        double bestScore = -Double.MAX_VALUE;
        int reachableFound = 0;
        for (Candidate c : list) {
            if (unreachable.contains(c.pos)) continue;
            if (checksLeft <= 0 || reachableFound >= MAX_REACHABLE_COMPARED) break;
            Path path = pathTo(c);
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
    private BlockPos pickFlower(List<Candidate> flowers) {
        Map<Block, List<Candidate>> byType = new HashMap<>();
        for (Candidate c : flowers) {
            byType.computeIfAbsent(c.block, k -> new ArrayList<>()).add(c);
        }
        while (!byType.isEmpty() && checksLeft > 0) {
            double total = 0;
            for (Block b : byType.keySet()) {
                total += flowerWeight(b);
            }
            if (total <= 0) break;   // до всіх цих квітів відраза
            double roll = cow.getRandom().nextDouble() * total;
            Block chosen = null;
            for (Block b : byType.keySet()) {
                roll -= flowerWeight(b);
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
        List<Candidate> normal = new ArrayList<>();
        List<Candidate> flowers = new ArrayList<>();
        List<Candidate> desperate = new ArrayList<>();
        List<Candidate> bluet = new ArrayList<>();
        int grassBlocks = 0;

        BlockPos from = origin.offset(-SEARCH_RADIUS, -SEARCH_HEIGHT, -SEARCH_RADIUS);
        BlockPos to = origin.offset(SEARCH_RADIUS, SEARCH_HEIGHT, SEARCH_RADIUS);
        // зазвичай усі чанки навколо завантажені: тоді не перевіряємо кожен блок окремо
        boolean allLoaded = cow.level().hasChunksAt(from, to);
        for (BlockPos p : BlockPos.betweenClosed(from, to)) {
            if (!allLoaded && !cow.level().hasChunkAt(p)) continue;
            BlockState state = cow.level().getBlockState(p);
            if (state.isAir()) continue;
            Kind k = classify(state);
            if (k == Kind.NONE) continue;
            if (k == Kind.GRASS_BLOCK) grassBlocks++;
            if (unreachable.contains(p)) continue;
            // відраза: цю їжу сама не їсть (хіба що голодує)
            if (cow.hunger < HungryCow.STARVE_AT && cow.memory.avoids(state.getBlock().asItem())) continue;
            Candidate c = new Candidate(p.immutable(), k, p.distSqr(origin), nutrition(state), state.getBlock());
            if (k == Kind.FLOWER) flowers.add(c);
            else if (k == Kind.DESPERATE) desperate.add(c);
            else if (k == Kind.BLUET) bluet.add(c);
            else normal.add(c);
        }

        checksLeft = MAX_PATH_CHECKS;
        boolean emergency = cow.hunger >= HungryCow.STARVE_AT;

        // 1) іноді корові хочеться квітки замість трави:
        //    мак з шансом POPPY_CHANCE (15%), інші квіти — решта до FLOWER_CHANCE (кульбаба частіше за інші)
        if (!emergency && !flowers.isEmpty()) {
            List<Candidate> poppies = new ArrayList<>();
            List<Candidate> otherFlowers = new ArrayList<>();
            for (Candidate c : flowers) {
                if (c.block == Blocks.POPPY) poppies.add(c);
                else otherFlowers.add(c);
            }
            float roll = cow.getRandom().nextFloat();
            float poppyChance = (float) (POPPY_CHANCE * cow.gene(CowGenes.Gene.POPPY_CRAVING));
            float flowerChance = (float) (FLOWER_CHANCE * cow.gene(CowGenes.Gene.FLOWER_CRAVING));
            if (!poppies.isEmpty() && roll < poppyChance) {
                BlockPos p = pickFlower(poppies);
                if (p != null) return p;
            } else if (!otherFlowers.isEmpty() && roll < Math.max(flowerChance, poppyChance)) {
                BlockPos p = pickFlower(otherFlowers);
                if (p != null) return p;
            }
        }
        // 2) звичайна їжа (трава, трав'яний і моховий блок, блоки гравця)
        BlockPos best = chooseNormal(normal, emergency);
        if (best != null) return best;
        // ще є їжа, яку не встигли перевірити: спробуємо наступного разу
        for (Candidate c : normal) {
            if (!unreachable.contains(c.pos)) return null;
        }
        // 3) звичайної їжі немає: безпечні квіти
        List<Candidate> safeFlowers = new ArrayList<>();
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
