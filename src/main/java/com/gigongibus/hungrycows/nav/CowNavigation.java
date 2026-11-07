package com.gigongibus.hungrycows.nav;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;

// Ходіння корови: ванільне (GroundPathNavigation), але пошук шляху — свій (CowNodeEvaluator: тримається далі від стін)
public class CowNavigation extends GroundPathNavigation {

    public CowNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new CowNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }
}
