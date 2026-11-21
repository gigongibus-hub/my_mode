package com.gigongibus.hungrycows.salt;

import com.gigongibus.hungrycows.WorldClock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Вміст діжки: сіль (до 30) і прогрес вимочування. Вода й кришка — у стані блока (щоб їх було видно). */
public class TubBlockEntity extends BlockEntity implements MenuProvider {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int MAX_SALT = 30;          // скільки перемоленої солі треба (Fij)
    public static final int MAX_WATER = 10;         // скільки відер води (Fij)
    public static final int SOAK_TICKS = 24000;     // ігрова доба (прискорюється /hungrycows speed)

    public final SimpleContainer salt = new SimpleContainer(1) {
        @Override
        public int getMaxStackSize() {
            return MAX_SALT;
        }

        @Override
        public boolean canPlaceItem(int slot, net.minecraft.world.item.ItemStack stack) {
            return stack.is(Salt.GROUND_SALT_DIRTY.get());
        }

        @Override
        public void setChanged() {
            super.setChanged();
            TubBlockEntity.this.setChanged();
        }
    };
    private int progress = 0;

    // для інтерфейсу: 0 прогрес (‰), 1 скільки відер, 2 розчин (1) чи вода (0), 3 кришка
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            BlockState s = getBlockState();
            return switch (i) {
                case 0 -> (int) (progress * 1000L / SOAK_TICKS);
                case 1 -> s.hasProperty(TubBlock.LEVEL) ? s.getValue(TubBlock.LEVEL) : 0;
                case 2 -> s.hasProperty(TubBlock.LIQUID) && s.getValue(TubBlock.LIQUID) == TubBlock.Liquid.BRINE ? 1 : 0;
                case 3 -> s.hasProperty(TubBlock.LID) && s.getValue(TubBlock.LID) ? 1 : 0;
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

    public TubBlockEntity(BlockPos pos, BlockState state) {
        super(Salt.TUB_BE.get(), pos, state);
    }

    public int progress() {
        return progress;
    }

    /** Усе є для вимочування: кришка, 10 відер води, 30 солі */
    public boolean ready(BlockState s) {
        return s.getValue(TubBlock.LID) && s.getValue(TubBlock.LEVEL) == MAX_WATER && s.getValue(TubBlock.LIQUID) == TubBlock.Liquid.WATER
                && salt.getItem(0).getCount() >= MAX_SALT;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TubBlockEntity be) {
        // Fij: чогось менше — «просто не вийде»; кришку зняли посеред справи — відлік стоїть
        if (!be.ready(state)) return;
        be.progress += WorldClock.speed((ServerLevel) level);
        if (be.progress >= SOAK_TICKS) {
            be.progress = 0;
            be.salt.setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
            level.setBlock(pos, state.setValue(TubBlock.LIQUID, TubBlock.Liquid.BRINE), 3);
        }
        be.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Salt", salt.getItem(0).save(new CompoundTag()));
        tag.putInt("Progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        salt.setItem(0, net.minecraft.world.item.ItemStack.of(tag.getCompound("Salt")));
        progress = tag.getInt("Progress");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.hungrycows.tub");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new TubMenu(id, inv, salt, data);
    }
}
