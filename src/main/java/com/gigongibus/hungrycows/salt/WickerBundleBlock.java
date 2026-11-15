package com.gigongibus.hungrycows.salt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * ПУЧОК ЛОЗИ на землі (сіль/лоза, Fij — «як у TerraFirmaCraft»). На одному блоці до 6 пучків (ПКМ пучком по блоку), кожен лежить
 * у випадковому місці й повернутий випадково (малює `client/WickerBundleRenderer`) і сохне окремо. Пучок сохне на сонці (свіжий → підсохлий → сухий), уночі й під дахом
 * стоїть; дощ — мокрий, ще доба мокрим — гнилий; дощ скінчився раніше — сохне з початку.
 */
public class WickerBundleBlock extends BaseEntityBlock {
    public enum Stage implements StringRepresentable {
        FRESH, WILTED, DRY, WET, ROTTEN;

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    public static final EnumProperty<Stage> STAGE = EnumProperty.create("stage", Stage.class);
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 1, 15);   // пласкі пучки по всьому блоку

    public WickerBundleBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(STAGE, Stage.FRESH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(STAGE);
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
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        return dir == Direction.DOWN && !canSurvive(state, level, pos) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : super.updateShape(state, dir, other, level, pos, otherPos);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
        return new ItemStack(Salt.bundleItem(state.getValue(STAGE)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                     net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof WickerBundleItem item) || !(level.getBlockEntity(pos) instanceof WickerBundleBlockEntity be))
            return net.minecraft.world.InteractionResult.PASS;
        if (be.count() >= WickerBundleBlockEntity.MAX_PIECES) return net.minecraft.world.InteractionResult.PASS;   // повний — кладе поруч
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        be.add(item.stage());
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.GRASS_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1F, 0.9F);
        if (!player.getAbilities().instabuild) held.shrink(1);
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    /** Ламання: кожен пучок своєю стадією (а не за станом блока) */
    @Override
    @SuppressWarnings("deprecation")
    public java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder b) {
        if (b.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof WickerBundleBlockEntity be)
            return be.drops();
        return super.getDrops(state, b);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WickerBundleBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Salt.WICKER_BUNDLE_BE.get(), WickerBundleBlockEntity::serverTick);
    }
}
