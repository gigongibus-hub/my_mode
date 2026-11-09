package com.gigongibus.hungrycows;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Пучки свіжої трави (Fij): зламав траву мотикою чи ножицями — пучок свіжої трави (висока — 2).
 * Щоб зібрати саму траву (як у ванільній грі), треба присісти й зламати її ножицями.
 */
public final class GrassHarvest {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int BUNDLES_SHORT = 1;   // низька трава — 1 пучок
    public static final int BUNDLES_TALL = 2;    // висока трава — 2 пучки

    private GrassHarvest() {
    }

    /** true — трава зібрана пучками (звичайне ламання треба скасувати) */
    public static boolean harvest(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        if (player == null || player.isCreative()) return false;
        boolean tall = state.is(Blocks.TALL_GRASS);
        if (!state.is(Blocks.GRASS) && !tall) return false;
        ItemStack tool = player.getMainHandItem();
        boolean shears = tool.getItem() instanceof ShearsItem;
        boolean hoe = tool.getItem() instanceof HoeItem;
        if (!shears && !hoe) return false;
        if (shears && player.isShiftKeyDown()) return false;   // присів з ножицями — сама трава (як завжди)
        level.destroyBlock(pos, false, player);
        Block.popResource(level, pos, new ItemStack(ModBlocks.HAY_BUNDLE.get(), tall ? BUNDLES_TALL : BUNDLES_SHORT));
        tool.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        return true;
    }
}
