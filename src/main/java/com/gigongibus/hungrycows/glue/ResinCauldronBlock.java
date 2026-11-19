package com.gigongibus.hungrycows.glue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Ванільний котел, у який поклали смолу (Fij). ПКМ смолою — ще шматок (до 30); присів + ПКМ порожньою рукою — шматок назад (поки
 * не розплавилась); ПКМ пензликом по розплавленій — пензлик зі смолою (−1 смола; скінчилась — знову порожній котел). Розплавлена
 * обпікає, тримає (важко вибратись) і сповільнює ще після того, як вийшов.
 */
public class ResinCauldronBlock extends BaseEntityBlock {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final float BURN_DAMAGE = 1F;        // розплавлена смола обпікає — 1 (пів серця) раз на секунду
    public static final int SLOW_TICKS = 200;          // і сповільнює ще 10 с після того, як вибрався (Fij)
    public static final double STUCK = 0.25;           // рух у смолі — чверть звичайного (важко вибратись)

    public static final BooleanProperty MOLTEN = BooleanProperty.create("molten");
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 3);
    // форма ванільного котла
    private static final VoxelShape INSIDE = box(2, 4, 2, 14, 16, 14);
    private static final VoxelShape SHAPE = Shapes.join(Shapes.block(), Shapes.or(box(0, 0, 4, 16, 3, 12), box(4, 0, 0, 12, 3, 16),
            box(2, 0, 2, 14, 3, 14), INSIDE), BooleanOp.ONLY_FIRST);

    public ResinCauldronBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(MOLTEN, false).setValue(LEVEL, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(MOLTEN, LEVEL);
    }

    /** Висота смоли в котлі: 1–10 шматків — низько, 11–20 — середньо, 21–30 — повний */
    public static int levelFor(int resin) {
        return resin <= 10 ? 1 : resin <= 20 ? 2 : 3;
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
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return INSIDE;
    }

    /** Котел без смоли — знову ванільний (Fij: «клацнув і поклав смолу»): перший шматок кладе `Glue.onRightClick` */
    public static void place(Level level, BlockPos pos, int resin) {
        level.setBlock(pos, Glue.RESIN_CAULDRON.get().defaultBlockState().setValue(LEVEL, levelFor(resin)), 3);
        if (level.getBlockEntity(pos) instanceof ResinCauldronBlockEntity be) be.setResin(resin);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ResinCauldronBlockEntity be)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        boolean molten = state.getValue(MOLTEN);
        if (held.is(Glue.RESIN.get())) {
            if (be.resin() >= ResinCauldronBlockEntity.MAX_RESIN) return InteractionResult.PASS;
            if (!level.isClientSide) {
                be.setResin(be.resin() + 1);
                if (!player.getAbilities().instabuild) held.shrink(1);
                level.playSound(null, pos, SoundEvents.HONEY_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 1.2F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.is(Glue.BRISTLE_BRUSH.get())) {
            if (!molten || ResinBrushItem.hasResin(held)) return InteractionResult.PASS;
            if (!level.isClientSide) {
                ResinBrushItem.setResin(held, true);
                level.playSound(null, pos, SoundEvents.HONEY_DRINK, SoundSource.BLOCKS, 0.8F, 0.8F);
                takeOne(level, pos, be);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.isEmpty() && player.isShiftKeyDown() && !molten && hand == InteractionHand.MAIN_HAND) {
            if (!level.isClientSide) {
                ItemStack r = new ItemStack(Glue.RESIN.get());
                if (!player.getInventory().add(r)) player.drop(r, false);
                level.playSound(null, pos, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.BLOCKS, 0.6F, 1.2F);
                takeOne(level, pos, be);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    private static void takeOne(Level level, BlockPos pos, ResinCauldronBlockEntity be) {
        if (be.resin() <= 1) level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
        else be.setResin(be.resin() - 1);
    }

    /** Розплавлена (Fij): обпікає, тримає й сповільнює (і ще 10 с після) */
    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity e) {
        if (!state.getValue(MOLTEN)) return;
        double top = pos.getY() + (6 + 3 * state.getValue(LEVEL)) / 16.0;
        if (e.getY() >= top) return;
        e.makeStuckInBlock(state, new Vec3(STUCK, STUCK * 0.6, STUCK));
        if (level.isClientSide || !(e instanceof LivingEntity le)) return;
        if (le.tickCount % 20 == 0) le.hurt(level.damageSources().hotFloor(), BURN_DAMAGE);
        le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 1, false, false, true));
    }

    /** Бульбашки й пара, поки розплавлена й під котлом горить багаття */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        if (!state.getValue(MOLTEN) || !ResinCauldronBlockEntity.hasFire(level, pos)) return;
        double y = pos.getY() + (6 + 3 * state.getValue(LEVEL)) / 16.0;
        if (r.nextInt(3) == 0)
            level.addParticle(ParticleTypes.BUBBLE_POP, pos.getX() + 0.2 + r.nextDouble() * 0.6, y, pos.getZ() + 0.2 + r.nextDouble() * 0.6, 0, 0.02, 0);
        if (r.nextInt(6) == 0)
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.3 + r.nextDouble() * 0.4, y + 0.1, pos.getZ() + 0.3 + r.nextDouble() * 0.4, 0, 0.03, 0);
        if (r.nextInt(25) == 0) level.playLocalSound(pos.getX() + 0.5, y, pos.getZ() + 0.5, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.3F, 1.4F, false);
    }

    /** Зламав: котел; застиглу смолу — назад шматками, розплавлену — пропала (ПРОПОЗИЦІЯ) */
    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder b) {
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(Items.CAULDRON));
        if (!state.getValue(MOLTEN) && b.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof ResinCauldronBlockEntity be && be.resin() > 0)
            out.add(new ItemStack(Glue.RESIN.get(), be.resin()));
        return out;
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(Items.CAULDRON);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResinCauldronBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Glue.RESIN_CAULDRON_BE.get(), ResinCauldronBlockEntity::serverTick);
    }
}
