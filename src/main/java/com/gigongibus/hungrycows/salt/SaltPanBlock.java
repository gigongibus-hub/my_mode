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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.EnumProperty;
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
 * СОЛЬОВА СКОВОРОДА (сіль, крок 5, модель Fij). Ставиться будь-де; ПКМ нею по звичайному багаттю (не душ) — стає прямо на
 * поліна, займаючи місце багаття (`CAMPFIRE` lit/unlit; Fij). Смажить лише на багатті, що горить (і по-старому — над ним).
 * Згасле — запалити кресалом / вогняним зарядом; погасити лопатою. Зламав — випадають сковорода, багаття й вміст. ПКМ недоочищеною сіллю — кладе ПО ОДНІЙ
 * (до 20; шар солі піднімається, як вода в діжці — 10 рівнів); 20 штук + багаття горить — за пів доби прожарюється; згасло — прогрес потроху спадає.
 * ПКМ порожньою рукою — забрати все (готову — прожарену).
 */
public class SaltPanBlock extends BaseEntityBlock {
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 10);
    public static final BooleanProperty ROASTED = BooleanProperty.create("roasted");
    public static final EnumProperty<Fire> CAMPFIRE = EnumProperty.create("campfire", Fire.class);
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 5, 16);        // на землі
    private static final VoxelShape SHAPE_FIRE = box(0, 0, 0, 16, 12, 16);  // поліна (7) + сковорода
    public static final float FIRE_DAMAGE = 1.0F;                           // хто стоїть на сковороді на вогні (як ванільне багаття)

    /** Під сковородою: нічого / багаття горить / багаття згасле */
    public enum Fire implements StringRepresentable {
        NONE, LIT, UNLIT;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public SaltPanBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FILL, 0).setValue(ROASTED, false).setValue(CAMPFIRE, Fire.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FILL, ROASTED, CAMPFIRE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(CAMPFIRE) == Fire.NONE ? SHAPE : SHAPE_FIRE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void stepOn(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.Entity e) {
        if (state.getValue(CAMPFIRE) == Fire.LIT && !e.fireImmune() && e instanceof net.minecraft.world.entity.LivingEntity
                && !net.minecraft.world.item.enchantment.EnchantmentHelper.hasFrostWalker((net.minecraft.world.entity.LivingEntity) e))
            e.hurt(level.damageSources().inFire(), FIRE_DAMAGE);
        super.stepOn(level, pos, state, e);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource r) {
        if (state.getValue(CAMPFIRE) != Fire.LIT) return;
        if (r.nextInt(10) == 0)
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.5F + r.nextFloat(), r.nextFloat() * 0.7F + 0.6F, false);
        if (r.nextInt(3) == 0) {   // вогник з-під дна, по боках
            double a = r.nextDouble() * Math.PI * 2;
            level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.5 + Math.cos(a) * 0.45, pos.getY() + 0.35, pos.getZ() + 0.5 + Math.sin(a) * 0.45, 0, 0.01, 0);
        }
        if (r.nextInt(4) == 0)
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5 + r.nextDouble() / 2 - 0.25, pos.getY() + 0.8, pos.getZ() + 0.5 + r.nextDouble() / 2 - 0.25, 0, 0.03, 0);
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
        Fire f = state.getValue(CAMPFIRE);
        if (f == Fire.UNLIT && (held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE))) {
            level.setBlock(pos, state.setValue(CAMPFIRE, Fire.LIT), 11);
            if (held.is(Items.FLINT_AND_STEEL)) {
                level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1F, 1F);
                held.hurtAndBreak(1, player, pl -> pl.broadcastBreakEvent(hand));
            } else {
                level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1F, 1F);
                if (!player.getAbilities().instabuild) held.shrink(1);
            }
            return InteractionResult.CONSUME;
        }
        if (f == Fire.LIT && held.getItem() instanceof net.minecraft.world.item.ShovelItem) {
            level.setBlock(pos, state.setValue(CAMPFIRE, Fire.UNLIT), 11);
            level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1F, 1F);
            held.hurtAndBreak(1, player, pl -> pl.broadcastBreakEvent(hand));
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
