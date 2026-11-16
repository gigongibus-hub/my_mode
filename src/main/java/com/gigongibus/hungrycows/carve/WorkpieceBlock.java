package com.gigongibus.hungrycows.carve;

import com.gigongibus.hungrycows.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

/**
 * Заготовка на верстаку: колода лежить на ванільному верстаку (малює `client/WorkpieceRenderer`). ПКМ зубилом — вікно різьблення.
 * Зламав: ціла — колода назад; почата — палиці. Верстак прибрали — падає.
 */
public class WorkpieceBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = box(2, 0, 3, 14, 10, 13);

    public WorkpieceBlock(Properties p) {
        super(p);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(Blocks.CRAFTING_TABLE);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        if (dir == Direction.DOWN && !canSurvive(state, level, pos) && level instanceof Level l && !l.isClientSide) l.destroyBlock(pos, true);
        return state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).is(Carving.CHISEL.get())) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof WorkpieceBlockEntity be) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new OpenCarvingPacket(pos, be.log(), be.mask().toBytes()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Ціла — та сама колода; почата — палиці (Fij) */
    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder b) {
        if (b.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof WorkpieceBlockEntity be) {
            return be.mask().untouched() ? List.of(new ItemStack(be.log())) : List.of(new ItemStack(Items.STICK, Carving.STICKS_WHEN_RUINED));
        }
        return List.of();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WorkpieceBlockEntity(pos, state);
    }
}
