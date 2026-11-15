package com.gigongibus.hungrycows.salt;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Інструмент, що лишається у верстаті й зношується на 1 за раз (сито, спиця) — як ступка */
public class WornToolItem extends Item {
    public WornToolItem(Properties p) {
        super(p);
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        ItemStack left = stack.copy();
        left.setCount(1);
        left.setDamageValue(left.getDamageValue() + 1);
        return left.getDamageValue() >= left.getMaxDamage() ? ItemStack.EMPTY : left;
    }
}
