package com.gigongibus.hungrycows.carve;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Зубило: ЛКМ по заготовці — удар різьби, а не ламання (і в творчому режимі) */
public class ChiselItem extends Item {
    public ChiselItem(Properties p) {
        super(p);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !state.is(Carving.WORKPIECE.get());
    }
}
