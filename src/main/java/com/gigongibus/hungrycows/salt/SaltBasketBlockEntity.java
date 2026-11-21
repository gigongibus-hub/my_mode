package com.gigongibus.hungrycows.salt;

import com.gigongibus.hungrycows.WorldClock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Вміст корзини: клітинка 0 — прожарена сіль (до 9), клітинка 1 — готовий блок солі (лише забрати). Інтерфейс (Fij: «як у діжки») —
 * `SaltBasketMenu`; ПКМ сіллю в руці теж кладе, як було.
 */
public class SaltBasketBlockEntity extends BlockEntity implements MenuProvider {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int CAPACITY = 9;            // 9 прожареної (Fij)
    public static final int DRIP_TICKS = 24000;      // ігрова доба (прискорюється /hungrycows speed)
    public static final int DROP_EVERY = 10;         // крапля знизу раз на пів секунди

    public final SimpleContainer slots = new SimpleContainer(2) {
        @Override
        public int getMaxStackSize() {
            return CAPACITY;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot == 0 && stack.is(Salt.GROUND_SALT_ROASTED.get());
        }

        @Override
        public void setChanged() {
            super.setChanged();
            SaltBasketBlockEntity.this.setChanged();
        }
    };
    private int progress = 0;

    // для інтерфейсу: 0 прогрес (‰), 1 висить на ланцюгу, 2 під нею повітря, 3 готово
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> (int) (progress * 1000L / DRIP_TICKS);
                case 1 -> level != null && SaltBasketBlock.hanging(level, worldPosition) ? 1 : 0;
                case 2 -> level != null && airBelow(level, worldPosition) ? 1 : 0;
                case 3 -> done() ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public SaltBasketBlockEntity(BlockPos pos, BlockState state) {
        super(Salt.SALT_BASKET_BE.get(), pos, state);
    }

    public int count() { return slots.getItem(0).getCount(); }
    public boolean done() { return !slots.getItem(1).isEmpty(); }
    public int progress() { return progress; }

    public void add(int n) {
        ItemStack s = slots.getItem(0);
        if (s.isEmpty()) slots.setItem(0, new ItemStack(Salt.GROUND_SALT_ROASTED.get(), n));
        else s.grow(n);
        progress = 0;
        slots.setChanged();
    }

    /** Забрати все: готовий блок або назад сіль */
    public ItemStack takeAll() {
        ItemStack out = done() ? slots.removeItemNoUpdate(1) : slots.removeItemNoUpdate(0);
        progress = 0;
        slots.setChanged();
        return out;
    }

    /** Під корзиною порожньо — вода стікає (Fij «хоча б один блок пустоти») */
    public static boolean airBelow(Level level, BlockPos pos) {
        return level.getBlockState(pos.below()).isAir();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SaltBasketBlockEntity be) {
        if (be.done()) return;
        if (be.count() < CAPACITY) {   // сіль забрали з інтерфейсу — починати спочатку
            if (be.progress != 0) { be.progress = 0; be.setChanged(); }
            return;
        }
        if (!SaltBasketBlock.hanging(level, pos) || !airBelow(level, pos)) return;   // лише на ланцюгу (Fij)
        be.progress += WorldClock.speed((ServerLevel) level);
        if (level.getGameTime() % DROP_EVERY == 0)
            ((ServerLevel) level).sendParticles(ParticleTypes.DRIPPING_WATER, pos.getX() + 0.3 + level.random.nextDouble() * 0.4, pos.getY() - 0.02,
                    pos.getZ() + 0.3 + level.random.nextDouble() * 0.4, 1, 0, 0, 0, 0);
        if (be.progress >= DRIP_TICKS) {
            be.slots.setItem(0, ItemStack.EMPTY);
            be.slots.setItem(1, new ItemStack(Salt.SALT_BLOCK_ITEM.get()));
            be.progress = 0;
        }
        be.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Salt", slots.getItem(0).save(new CompoundTag()));
        tag.put("Block", slots.getItem(1).save(new CompoundTag()));
        tag.putInt("Progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        progress = tag.getInt("Progress");
        if (tag.contains("Salt")) {
            slots.setItem(0, ItemStack.of(tag.getCompound("Salt")));
            slots.setItem(1, ItemStack.of(tag.getCompound("Block")));
        } else {   // старе збереження: Count / Done
            int count = tag.getInt("Count");
            slots.setItem(0, count > 0 ? new ItemStack(Salt.GROUND_SALT_ROASTED.get(), count) : ItemStack.EMPTY);
            slots.setItem(1, tag.getBoolean("Done") ? new ItemStack(Salt.SALT_BLOCK_ITEM.get()) : ItemStack.EMPTY);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.hungrycows.salt_basket");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new SaltBasketMenu(id, inv, slots, data);
    }
}
