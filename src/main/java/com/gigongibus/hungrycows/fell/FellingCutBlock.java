package com.gigongibus.hungrycows.fell;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Колода стовбура дуба з зарубкою (Fij): малює `client/FellingCutRenderer` (дуб із вирізаним клином). Дерево над нею стоїть, поки
 * зарубка не глибока; впало — лишається пень. Зламав — дубова колода.
 */
public class FellingCutBlock extends BaseEntityBlock {
    public FellingCutBlock(Properties p) {
        super(p);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (level.getBlockEntity(pos) instanceof FellingCutBlockEntity be && be.felled())
            return box(0, 0, 0, 16, Math.max(1, be.at()), 16);
        return Shapes.block();
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder b) {
        var be = b.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        return List.of(new ItemStack(be instanceof FellingCutBlockEntity f ? com.gigongibus.hungrycows.chop.WoodPiece.block(f.wood(), false) : net.minecraft.world.level.block.Blocks.OAK_LOG));
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(level.getBlockEntity(pos) instanceof FellingCutBlockEntity f ? com.gigongibus.hungrycows.chop.WoodPiece.block(f.wood(), false) : net.minecraft.world.level.block.Blocks.OAK_LOG);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FellingCutBlockEntity(pos, state);
    }
}
