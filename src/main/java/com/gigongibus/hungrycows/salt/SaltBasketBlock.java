package com.gigongibus.hungrycows.salt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * КОРЗИНА ДЛЯ СОЛІ (сіль, крок 6, модель Fij). Ставиться будь-де (на землю) або вішається під ЛАНЦЮГ (Fij: «обов'язково ланцюг,
 * не інший блок»). Капає й робить блок солі, лише коли висить на ланцюгу. ПКМ прожареною сіллю — кладе (до 9);
 * 9 штук і під корзиною повітря — за ігрову добу знизу капає вода, всередині стає блок солі. ПКМ порожньою рукою — забрати.
 */
public class SaltBasketBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 16, 16);

    public SaltBasketBlock(Properties p) {
        super(p);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return hanging(level, pos) || Block.canSupportCenter(level, pos.below(), Direction.UP);   // на ланцюгу або на землі
    }

    /** Висить на ланцюгу (вертикальному) — лише так сіль стікає */
    public static boolean hanging(LevelReader level, BlockPos pos) {
        BlockState up = level.getBlockState(pos.above());
        return up.is(net.minecraft.world.level.block.Blocks.CHAIN) && up.getValue(net.minecraft.world.level.block.ChainBlock.AXIS) == Direction.Axis.Y;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return canSurvive(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos()) ? defaultBlockState() : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        if ((dir == Direction.UP || dir == Direction.DOWN) && !canSurvive(state, level, pos) && level instanceof Level l && !l.isClientSide) l.scheduleTick(pos, this, 1);
        return state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource r) {
        if (!canSurvive(state, level, pos)) level.destroyBlock(pos, true);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SaltBasketBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Salt.SALT_BASKET_BE.get(), SaltBasketBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !(level.getBlockEntity(pos) instanceof SaltBasketBlockEntity be)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (held.is(Salt.GROUND_SALT_ROASTED.get()) && !be.done() && be.count() < SaltBasketBlockEntity.CAPACITY) {
            int n = Math.min(held.getCount(), SaltBasketBlockEntity.CAPACITY - be.count());
            be.add(n);
            if (!player.getAbilities().instabuild) held.shrink(n);
            level.playSound(null, pos, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.6F, 1.2F);
            return InteractionResult.CONSUME;
        }
        if (held.isEmpty() && (be.done() || be.count() > 0)) {
            ItemStack out = be.takeAll();
            if (!player.getInventory().add(out)) player.drop(out, false);
            level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 0.6F, 1F);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SaltBasketBlockEntity be && (be.done() || be.count() > 0)) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), be.takeAll());
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
