package com.gigongibus.hungrycows.carve;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Зубило: ЛКМ по заготовці — удар різьби, а не ламання (і в творчому режимі). Fij (17.11): мале вирізає 1 піксель, середнє — 2,
 * широке — 3 × 3 (width — уздовж леза, thick — упоперек).
 */
public class ChiselItem extends Item {
    public final int width, thick;

    public ChiselItem(Properties p, int width, int thick) {
        super(p);
        this.width = width;
        this.thick = thick;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !state.is(Carving.WORKPIECE.get());
    }
}
