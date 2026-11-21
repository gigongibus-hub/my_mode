package com.gigongibus.hungrycows.salt;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Кришка діжки, поставлена як блок (Fij): просто лежить. Закрити діжку — ПКМ кришкою по діжці, як і раніше. */
public class TubLidBlock extends Block {
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 3, 15);

    public TubLidBlock(Properties p) {
        super(p);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }
}
