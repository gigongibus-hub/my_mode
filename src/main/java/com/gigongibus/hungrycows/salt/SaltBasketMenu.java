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

/** Інтерфейс корзини (Fij: «як у діжки»): місце для 9 прожареної солі, місце для готового блока солі (лише забрати), прогрес */
public class SaltBasketMenu extends AbstractContainerMenu {
    public static final int SALT_X = 44, SALT_Y = 20;     // місце для солі
    public static final int BLOCK_X = 116, BLOCK_Y = 20;  // готовий блок солі
    private final Container basket;
    public final ContainerData data;

    // на клієнті
    public SaltBasketMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(2) {
            @Override public int getMaxStackSize() { return SaltBasketBlockEntity.CAPACITY; }
        }, new SimpleContainerData(4));
    }

    public SaltBasketMenu(int id, Inventory inv, Container slots, ContainerData data) {
        super(Salt.SALT_BASKET_MENU.get(), id);
        this.basket = slots;
        this.data = data;
        addSlot(new Slot(slots, 0, SALT_X, SALT_Y) {
            @Override public boolean mayPlace(ItemStack s) { return s.is(Salt.GROUND_SALT_ROASTED.get()) && SaltBasketMenu.this.data.get(3) == 0; }
            @Override public int getMaxStackSize() { return SaltBasketBlockEntity.CAPACITY; }
        });
        addSlot(new Slot(slots, 1, BLOCK_X, BLOCK_Y) {
            @Override public boolean mayPlace(ItemStack s) { return false; }   // лише забрати
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
        if (index < 2) {
            if (!moveItemStackTo(s, 2, 38, true)) return ItemStack.EMPTY;
        } else if (!slots.get(0).mayPlace(s) || !moveItemStackTo(s, 0, 1, false)) return ItemStack.EMPTY;
        if (s.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return basket.stillValid(player);
    }
}
