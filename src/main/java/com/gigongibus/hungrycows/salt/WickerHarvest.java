package com.gigongibus.hungrycows.salt;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Лоза (Fij): ванільні ліани мечем чи ножицями → 1–4 лози. Присівши з ножицями — сама ліана, як у ванільній грі. */
public final class WickerHarvest {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int MIN = 1, MAX = 4;

    private WickerHarvest() {
    }

    /** true — зібрано лозою (звичайне ламання скасувати) */
    public static boolean harvest(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        if (player == null || player.isCreative() || !state.is(Blocks.VINE)) return false;
        ItemStack tool = player.getMainHandItem();
        boolean shears = tool.getItem() instanceof ShearsItem, sword = tool.getItem() instanceof SwordItem;
        if (!shears && !sword) return false;
        if (shears && player.isShiftKeyDown()) return false;
        level.destroyBlock(pos, false, player);
        Block.popResource(level, pos, new ItemStack(Salt.WICKER.get(), MIN + level.random.nextInt(MAX - MIN + 1)));
        tool.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        return true;
    }
}
