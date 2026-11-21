package com.gigongibus.hungrycows.salt;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * ДІЖКА (сіль, крок 3, модель Fij). ПКМ відром води — +1 відро (до 10, рідину видно); ПКМ кришкою — закрити (відлік іде, лише коли
 * 30 солі й 10 відер води); присівши ПКМ порожньою рукою — зняти кришку; ПКМ порожнім відром — набрати розчин; ПКМ іншим —
 * інтерфейс (місце для солі, прогрес).
 */
public class TubBlock extends BaseEntityBlock {
    public enum Liquid implements StringRepresentable {
        WATER, BRINE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, TubBlockEntity.MAX_WATER);
    public static final EnumProperty<Liquid> LIQUID = EnumProperty.create("liquid", Liquid.class);
    public static final BooleanProperty LID = BooleanProperty.create("lid");
    // порожня всередині, як котел (Fij: «щоб можна було залізти»): стінки 1 пікс., дно 1 пікс.
    private static final VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.join(box(0, 0, 0, 16, 15, 16), box(1, 1, 1, 15, 15, 15),
            net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);
    private static final VoxelShape INSIDE = box(1, 1, 1, 15, 15, 15);   // куди можна клацнути всередині (як у котла)
    private static final VoxelShape SHAPE_LID = box(0, 0, 0, 16, 16, 16);

    public TubBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 0).setValue(LIQUID, Liquid.WATER).setValue(LID, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> b) {
        b.add(LEVEL, LIQUID, LID);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(LID) ? SHAPE_LID : SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return INSIDE;
    }

    /** У діжці з водою вогонь гасне (як у котлі) */
    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.Entity e) {
        int lvl = state.getValue(LEVEL);
        if (!level.isClientSide && lvl > 0 && e.isOnFire() && !state.getValue(LID)
                && e.getY() < pos.getY() + (1 + 12 * lvl / (double) TubBlockEntity.MAX_WATER) / 16.0
                && e.getBoundingBox().intersects(INSIDE.bounds().move(pos))) {
            e.clearFire();
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TubBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Salt.TUB_BE.get(), TubBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        boolean lid = state.getValue(LID);
        int lvl = state.getValue(LEVEL);
        Liquid liquid = state.getValue(LIQUID);
        if (level.isClientSide) return InteractionResult.SUCCESS;
        // налити воду
        if (held.is(Items.WATER_BUCKET) && !lid && lvl < TubBlockEntity.MAX_WATER && (lvl == 0 || liquid == Liquid.WATER)) {
            level.setBlock(pos, state.setValue(LEVEL, lvl + 1).setValue(LIQUID, Liquid.WATER), 3);
            if (!player.getAbilities().instabuild) player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1F, 1F);
            return InteractionResult.CONSUME;
        }
        // набрати розчин
        if (held.is(Items.BUCKET) && !lid && lvl > 0 && liquid == Liquid.BRINE) {
            BlockState next = lvl - 1 == 0 ? state.setValue(LEVEL, 0).setValue(LIQUID, Liquid.WATER) : state.setValue(LEVEL, lvl - 1);
            level.setBlock(pos, next, 3);
            give(player, hand, held, new ItemStack(Salt.SALT_SOLUTION.get()));
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1F, 1F);
            return InteractionResult.CONSUME;
        }
        // закрити кришкою
        if (held.is(Salt.TUB_LID.get()) && !lid) {
            level.setBlock(pos, state.setValue(LID, true), 3);
            if (!player.getAbilities().instabuild) held.shrink(1);
            level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1F, 1F);
            return InteractionResult.CONSUME;
        }
        // зняти кришку
        if (held.isEmpty() && player.isShiftKeyDown() && lid) {
            level.setBlock(pos, state.setValue(LID, false), 3);
            player.setItemInHand(hand, new ItemStack(Salt.TUB_LID.get()));
            level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.6F, 1.2F);
            return InteractionResult.CONSUME;
        }
        if (level.getBlockEntity(pos) instanceof TubBlockEntity be && player instanceof ServerPlayer sp) {
            net.minecraftforge.network.NetworkHooks.openScreen(sp, be, pos);
        }
        return InteractionResult.CONSUME;
    }

    private static void give(Player player, InteractionHand hand, ItemStack held, ItemStack got) {
        if (player.getAbilities().instabuild) {
            if (!player.getInventory().contains(got)) player.getInventory().add(got);
            return;
        }
        held.shrink(1);
        if (held.isEmpty()) player.setItemInHand(hand, got);
        else if (!player.getInventory().add(got)) player.drop(got, false);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof TubBlockEntity be) Containers.dropContents(level, pos, be.salt);
            if (state.getValue(LID)) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Salt.TUB_LID.get()));
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
