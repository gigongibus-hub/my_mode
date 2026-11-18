package com.gigongibus.hungrycows.chop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Купа шматків колоди (Fij): малює `client/WoodPileRenderer`. ЛКМ сокирою (не присівши) — розруб шматка, на який дивишся
 * (`Chopping`); ПКМ порожньою рукою — забрати цей шматок; ПКМ шматком — докласти; зламав — усі шматки випадають.
 * Нитка (Fij, 18.11): ПКМ ниткою — +1, дві — перев'язана (не рубається, шматки не забрати й не докласти), зламав — одна
 * «в'язанка дров»; присів + ПКМ порожньою рукою — розв'язати, нитки назад.
 */
public class WoodPileBlock extends BaseEntityBlock {
    public WoodPileBlock(Properties p) {
        super(p);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return level.getBlockEntity(pos) instanceof WoodPileBlockEntity be ? be.shape() : box(4, 0, 4, 12, 4, 12);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        if (dir == Direction.DOWN && !level.getBlockState(otherPos).isFaceSturdy(level, otherPos, Direction.UP)
                && level instanceof Level l && !l.isClientSide) l.destroyBlock(pos, true);
        return state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof WoodPileBlockEntity be)) return InteractionResult.PASS;
        ItemStack held = player.getMainHandItem();
        // нитка (Fij): дві нитки — купа перев'язана
        if (held.is(Items.STRING)) {
            if (be.tied() || be.pieces().isEmpty()) return InteractionResult.PASS;
            if (!level.isClientSide) {
                be.setStrings(be.strings() + 1);
                if (!player.getAbilities().instabuild) held.shrink(1);
                level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8F, 1.3F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!held.isEmpty()) return InteractionResult.PASS;
        // присів + порожня рука — розв'язати, нитки назад
        if (be.strings() > 0) {
            if (!player.isShiftKeyDown()) return InteractionResult.PASS;
            if (!level.isClientSide) {
                ItemStack s = new ItemStack(Items.STRING, be.strings());
                be.setStrings(0);
                if (!player.getInventory().add(s)) player.drop(s, false);
                level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 0.8F, 1.3F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            int i = Chopping.pieceAt(be.pieces(), pos, hit.getLocation(), hit.getDirection());
            if (i < 0) return InteractionResult.PASS;
            WoodPiece p = be.pieces().remove(i);
            ItemStack got = WoodPieceItem.of(p);
            if (!player.getInventory().add(got)) player.drop(got, false);
            level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.2F);
            if (be.pieces().isEmpty()) level.removeBlock(pos, false);
            else be.changed();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder b) {
        List<ItemStack> out = new ArrayList<>();
        if (b.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof WoodPileBlockEntity be) {
            if (be.tied()) { out.add(WoodBundleItem.of(be)); return out; }
            for (WoodPiece p : be.pieces()) out.add(WoodPieceItem.of(p));
            if (be.strings() > 0) out.add(new ItemStack(Items.STRING, be.strings()));
        }
        return out;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WoodPileBlockEntity(pos, state);
    }
}
