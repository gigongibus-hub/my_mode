package com.gigongibus.hungrycows.salt;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Інтерфейс діжки: одне місце для солі (до 30) і рядок прогресу */
public class TubMenu extends AbstractContainerMenu {
    public static final int SALT_X = 80, SALT_Y = 20;   // де місце для солі (пікселі інтерфейсу)
    private final Container salt;
    public final ContainerData data;

    // на клієнті
    public TubMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(1) {
            @Override public int getMaxStackSize() { return TubBlockEntity.MAX_SALT; }
        }, new SimpleContainerData(4));
    }

    public TubMenu(int id, Inventory inv, Container salt, ContainerData data) {
        super(Salt.TUB_MENU.get(), id);
        this.salt = salt;
        this.data = data;
        addSlot(new Slot(salt, 0, SALT_X, SALT_Y) {
            @Override public boolean mayPlace(ItemStack s) { return s.is(Salt.GROUND_SALT_DIRTY.get()) && TubMenu.this.data.get(3) == 0; }
            @Override public boolean mayPickup(Player p) { return TubMenu.this.data.get(3) == 0; }   // під кришкою не дістати
            @Override public int getMaxStackSize() { return TubBlockEntity.MAX_SALT; }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 142));
        addDataSlots(data);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack s = slot.getItem(), copy = s.copy();
        if (index == 0) {
            if (!moveItemStackTo(s, 1, 37, true)) return ItemStack.EMPTY;
        } else if (!slots.get(0).mayPlace(s) || !moveItemStackTo(s, 0, 1, false)) return ItemStack.EMPTY;
        if (s.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return salt.stillValid(player);
    }
}
