package com.gigongibus.hungrycows.glue;

import com.gigongibus.hungrycows.carve.CarvedPartItem;
import com.gigongibus.hungrycows.carve.Carving;
import com.gigongibus.hungrycows.carve.MugItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.LevelAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Склеювання кружки (Fij, 19.11). Заготовка кружки стоїть, де поставили. По черзі: ПКМ пензликом зі смолою — намазати;
 * ПКМ вушками — приставити; пензликом — намазати вушка; ПКМ ручкою — приставити; пів доби сохне (свіжу смолу видно);
 * ПКМ порожньою рукою — забрати кружку. Без смоли деталь не ставиться.
 */
public class MugAssemblyBlock extends BaseEntityBlock {
    public static final int BODY = 0, BODY_GLUED = 1, EARS = 2, EARS_GLUED = 3, DRYING = 4, DONE = 5;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 5);
    private static final VoxelShape SHAPE = box(4.5, 0, 4, 11.5, 6, 12);

    public MugAssemblyBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STAGE, BODY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, STAGE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        if (dir == Direction.DOWN && !canSurvive(state, level, pos) && level instanceof Level l && !l.isClientSide) l.destroyBlock(pos, true);
        return state;
    }

    /** Поставити заготовку кружки (ПКМ нею по верху блока); ручкою до гравця */
    public static boolean place(Level level, BlockPos at, Player player, ItemStack body) {
        BlockState st = Glue.MUG_ASSEMBLY.get().defaultBlockState().setValue(FACING, player == null ? Direction.NORTH : player.getDirection());
        if (!level.getBlockState(at).canBeReplaced() || !st.canSurvive(level, at)) return false;
        if (level.isClientSide) return true;
        level.setBlock(at, st, 3);
        if (level.getBlockEntity(at) instanceof MugAssemblyBlockEntity be) be.qBody = CarvedPartItem.quality(body);
        level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.3F);
        if (player == null || !player.getAbilities().instabuild) body.shrink(1);
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MugAssemblyBlockEntity be)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        int stage = state.getValue(STAGE);
        // намазати: заготовку (під вушка) або вушка (під ручку)
        if (held.is(Glue.BRISTLE_BRUSH.get()) && ResinBrushItem.hasResin(held) && (stage == BODY || stage == EARS)) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(STAGE, stage + 1), 3);
                ResinBrushItem.setResin(held, false);
                held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
                level.playSound(null, pos, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.BLOCKS, 0.8F, 1.2F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        boolean ears = held.is(Carving.MUG_EARS.get()) && stage == BODY_GLUED;
        boolean handle = held.is(Carving.MUG_HANDLE.get()) && stage == EARS_GLUED;
        if (ears || handle) {
            if (!level.isClientSide) {
                if (ears) be.qEars = CarvedPartItem.quality(held);
                else be.qHandle = CarvedPartItem.quality(held);
                be.setChanged();
                level.setBlock(pos, state.setValue(STAGE, stage + 1), 3);
                if (!player.getAbilities().instabuild) held.shrink(1);
                level.playSound(null, pos, SoundEvents.HONEY_BLOCK_STEP, SoundSource.BLOCKS, 1F, 0.9F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        // готова — забрати
        if (held.isEmpty() && stage == DONE && hand == InteractionHand.MAIN_HAND) {
            if (!level.isClientSide) {
                ItemStack mug = MugItem.withUses(new ItemStack(Carving.MUG.get()), Carving.usesFor(be.quality()));
                level.removeBlock(pos, false);
                if (!player.getInventory().add(mug)) player.drop(mug, false);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    /** Зламав: готова — кружка; ні — ті деталі, що вже стоять (смола пропала) */
    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder b) {
        List<ItemStack> out = new ArrayList<>();
        if (!(b.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof MugAssemblyBlockEntity be)) return out;
        int stage = state.getValue(STAGE);
        if (stage == DONE) {
            out.add(MugItem.withUses(new ItemStack(Carving.MUG.get()), Carving.usesFor(be.quality())));
            return out;
        }
        out.add(CarvedPartItem.withQuality(new ItemStack(Carving.MUG_BODY.get()), be.qBody));
        if (stage >= EARS) out.add(CarvedPartItem.withQuality(new ItemStack(Carving.MUG_EARS.get()), be.qEars));
        if (stage >= DRYING) out.add(CarvedPartItem.withQuality(new ItemStack(Carving.MUG_HANDLE.get()), be.qHandle));
        return out;
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(Carving.MUG_BODY.get());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MugAssemblyBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Glue.MUG_ASSEMBLY_BE.get(), MugAssemblyBlockEntity::serverTick);
    }
}
