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
 * Заготовка на верстаку: креслення, на ньому колода (малює `client/WorkpieceRenderer`). ПКМ зубилом — вікно різьблення.
 * ПКМ порожньою рукою (Fij, варіант А): є колода — зняти її: гра порівнює вирізане з шаблоном креслення → деталь (з точністю) або
 * палиці; креслення лишається; немає колоди — забрати креслення. Зламав: креслення + (ціла — колода; почата — палиці).
 */
public class WorkpieceBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = box(2, 0, 3, 14, 10, 13);
    private static final VoxelShape PAPER = box(1, 0, 1, 15, 1, 15);

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
        if (!(level.getBlockEntity(pos) instanceof WorkpieceBlockEntity be)) return SHAPE;
        if (!be.hasLog()) return PAPER;
        return be.voxels() != null ? be.voxelShape() : SHAPE;
    }

    /** Зубилом не ламається (ЛКМ зубилом — удар різьби, `VoxelCarve`) */
    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (player.getMainHandItem().is(Carving.CHISEL.get())) return 0;
        return super.getDestroyProgress(state, player, level, pos);
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
        if (hand != InteractionHand.MAIN_HAND || !(level.getBlockEntity(pos) instanceof WorkpieceBlockEntity be)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Carving.CHISEL.get()) && be.hasLog() && be.voxels() != null) return InteractionResult.PASS;   // справжня різьба — ЛКМ
        if (held.is(Carving.CHISEL.get()) && be.hasLog()) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                CarveMask keep = be.blueprint() instanceof BlueprintItem bp ? CarveTemplates.keep(sp.server, bp.template) : CarveMask.empty();
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                        new OpenCarvingPacket(pos, be.log(), be.mask().toBytes(), keep.toBytes()));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.isEmpty() && !player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                ItemStack got;
                if (be.hasLog()) {
                    got = finish(level, be);
                    be.clearLog();
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.WOOD_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.2F);
                } else {
                    got = be.blueprint() == null ? ItemStack.EMPTY : new ItemStack(be.blueprint());
                    level.removeBlock(pos, false);
                }
                if (!got.isEmpty() && !player.getInventory().add(got)) player.drop(got, false);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    /** Що вийшло: ціла колода — назад колода; схоже на шаблон креслення — деталь з точністю; інакше — палиці (Fij) */
    public static ItemStack finish(Level level, WorkpieceBlockEntity be) {
        if (be.voxels() != null) {           // справжня різьба: порівняти з об'ємним шаблоном креслення
            if (be.voxels().untouched()) return new ItemStack(be.log());
            if (be.blueprint() instanceof BlueprintItem bp && level.getServer() != null) {
                float q = Carving.quality3d(be.voxels().match(CarveTemplates.keep3d(level.getServer(), bp.template)));
                if (q >= 0) return CarvedPartItem.withQuality(new ItemStack(bp.result.get()), q);
            }
            return new ItemStack(Items.STICK, Carving.STICKS_WHEN_RUINED);
        }
        if (be.mask().untouched()) return new ItemStack(be.log());
        if (be.blueprint() instanceof BlueprintItem bp && level.getServer() != null) {
            float q = Carving.quality(be.mask().match(CarveTemplates.keep(level.getServer(), bp.template), Carving.EDGE_TOLERANCE));
            if (q >= 0) return CarvedPartItem.withQuality(new ItemStack(bp.result.get()), q);
        }
        return new ItemStack(Items.STICK, Carving.STICKS_WHEN_RUINED);
    }

    /** Ціла — та сама колода; почата — палиці (Fij) */
    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder b) {
        if (b.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof WorkpieceBlockEntity be) {
            List<ItemStack> out = new java.util.ArrayList<>();
            if (be.blueprint() != null) out.add(new ItemStack(be.blueprint()));
            boolean whole = be.voxels() != null ? be.voxels().untouched() : be.mask().untouched();
            if (be.hasLog()) out.add(whole ? new ItemStack(be.log()) : new ItemStack(Items.STICK, Carving.STICKS_WHEN_RUINED));
            return out;
        }
        return List.of();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WorkpieceBlockEntity(pos, state);
    }
}
