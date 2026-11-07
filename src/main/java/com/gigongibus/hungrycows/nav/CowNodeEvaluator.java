package com.gigongibus.hungrycows.nav;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.core.Direction;

/**
 * Пошук шляху корови: те саме, що ванільний WalkNodeEvaluator, але клітинка біля стіни (стіна поруч чи навскіс,
 * на рівні ніг або голови) «дорожча». Тоді шлях іде серединою входу й на блок від стін, а не впритул — корова
 * завширшки майже з блок, і впритул до стіни найменше відхилення = удар об стіну (Fij: «б'ються в стіни»).
 * Клітинки, де стоїть інша корова, яку зараз обходить (CowNavigation), теж «дорожчі».
 * Де інакше не пройти (вхід завширшки 1 блок), шлях однаково знаходиться — просто трохи «дорожчий».
 */
public class CowNodeEvaluator extends WalkNodeEvaluator {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final float WALL_MALUS = 2.0F;   // «ціна» клітинки біля стіни (як 2 зайві блоки дороги)

    private final Long2BooleanOpenHashMap nearWall = new Long2BooleanOpenHashMap();
    private final CowNavigation nav;   // звідси — клітинки інших корів, які зараз обходить

    public CowNodeEvaluator(CowNavigation nav) {
        this.nav = nav;
    }

    @Override
    public void prepare(PathNavigationRegion region, Mob mob) {
        super.prepare(region, mob);
        nearWall.clear();
    }

    @Override
    public void done() {
        super.done();
        nearWall.clear();
    }

    @Override
    protected Node findAcceptedNode(int x, int y, int z, int verticalDeltaLimit, double nodeFloorLevel, Direction direction,
                                    BlockPathTypes pathType) {
        Node node = super.findAcceptedNode(x, y, z, verticalDeltaLimit, nodeFloorLevel, direction, pathType);
        if (node != null && node.costMalus >= 0 && node.type == BlockPathTypes.WALKABLE && isNearWall(node.x, node.y, node.z)) {
            node.costMalus = Math.max(node.costMalus, WALL_MALUS);
        }
        if (node != null && node.costMalus >= 0 && nav != null && !nav.avoid.isEmpty()
                && nav.avoid.getOrDefault(CowNavigation.key(node.x, node.z), -1) >= nav.now()) {
            node.costMalus = Math.max(node.costMalus, CowNavigation.AVOID_MALUS);   // тут стоїть інша корова — обійти
        }
        return node;
    }

    // Чи є поруч (8 сусідніх клітинок) блок, у який не пройти, на рівні ніг або голови
    private boolean isNearWall(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        if (nearWall.containsKey(key)) return nearWall.get(key);
        boolean wall = false;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        outer:
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int dy = 0; dy <= 1; dy++) {
                    p.set(x + dx, y + dy, z + dz);
                    BlockState s = this.level.getBlockState(p);
                    if (!s.getCollisionShape(this.level, p).isEmpty()) {
                        // низенька перешкода під ногами (килим, півблок-сходинка) — не стіна
                        if (dy == 0 && s.getCollisionShape(this.level, p).max(Direction.Axis.Y) <= 0.5) continue;
                        wall = true;
                        break outer;
                    }
                }
            }
        }
        nearWall.put(key, wall);
        return wall;
    }
}
