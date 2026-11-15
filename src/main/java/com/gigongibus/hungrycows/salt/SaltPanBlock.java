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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * СОЛЬОВА СКОВОРОДА (сіль, крок 5, модель Fij). Ставиться лише на звичайне багаття (не душ). ПКМ недоочищеною сіллю — кладе ПО ОДНІЙ
 * (до 20, на моделі видно 1–4 купки); 20 штук + багаття горить — за пів доби прожарюється; згасло — прогрес потроху спадає.
 * ПКМ порожньою рукою — забрати все (готову — прожарену).
 */
public class SaltPanBlock extends BaseEntityBlock {
    public static final IntegerProperty PILES = IntegerProperty.create("piles", 0, 4);
    public static final BooleanProperty ROASTED = BooleanProperty.create("roasted");
    private static final VoxelShape SHAPE = box(0, -8, 0, 16, -3, 16);   // сковорода опущена на багаття

    public SaltPanBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(PILES, 0).setValue(ROASTED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(PILES, ROASTED);
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
        return level.getBlockState(pos.below()).is(Blocks.CAMPFIRE);   // лише звичайне багаття (Fij: душ — ні)
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = defaultBlockState();
        return canSurvive(s, ctx.getLevel(), ctx.getClickedPos()) ? s : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        if (dir == Direction.DOWN && !canSurvive(state, level, pos) && level instanceof Level l && !l.isClientSide) {
            l.scheduleTick(pos, this, 1);   // багаття прибрали — сковорода падає (з вмістом)
        }
        return state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource r) {
        if (!canSurvive(state, level, pos)) level.destroyBlock(pos, true);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SaltPanBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Salt.SALT_PAN_BE.get(), SaltPanBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !(level.getBlockEntity(pos) instanceof SaltPanBlockEntity be)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (held.is(Salt.GROUND_SALT_HALF_PURIFIED.get()) && !state.getValue(ROASTED) && be.count() < SaltPanBlockEntity.CAPACITY) {
            be.add();   // по одній (Fij)
            if (!player.getAbilities().instabuild) held.shrink(1);
            level.playSound(null, pos, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.6F, 1.3F);
            return InteractionResult.CONSUME;
        }
        if (held.isEmpty() && be.count() > 0) {
            ItemStack out = be.takeAll();
            if (!player.getInventory().add(out)) player.drop(out, false);
            level.playSound(null, pos, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 0.6F, 1.2F);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SaltPanBlockEntity be && be.count() > 0) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), be.takeAll());
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
