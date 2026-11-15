package com.gigongibus.hungrycows.salt;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Сковорода-предмет. ПКМ по звичайному багаттю — сковорода стає ПРЯМО НА ПОЛІНА (Fij): займає місце багаття (блок
 * «сковорода на багатті», горить чи згасле — як було багаття). Інакше ставиться, як звичайний блок, будь-де.
 */
public class SaltPanItem extends BlockItem {
    public SaltPanItem(Block block, Properties p) {
        super(block, p);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState camp = level.getBlockState(pos);
        Player player = ctx.getPlayer();
        if (camp.is(Blocks.CAMPFIRE) && (player == null || !player.isSecondaryUseActive())) {
            if (!level.isClientSide) {
                SaltPanBlock.Fire f = camp.getValue(CampfireBlock.LIT) ? SaltPanBlock.Fire.LIT : SaltPanBlock.Fire.UNLIT;
                level.setBlock(pos, getBlock().defaultBlockState().setValue(SaltPanBlock.CAMPFIRE, f), 3);
                level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1F, 0.9F);
                level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
                if (player == null || !player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        // порожня сковорода на землі — скласти стосом (Fij)
        if (camp.is(getBlock()) && (player == null || !player.isSecondaryUseActive()) && SaltPanBlock.canStack(level, pos, camp)) {
            if (!level.isClientSide) {
                level.setBlock(pos, camp.setValue(SaltPanBlock.STACK, camp.getValue(SaltPanBlock.STACK) + 1), 3);
                level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1F, 1.1F);
                level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
                if (player == null || !player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useOn(ctx);
    }
}
