package com.gigongibus.hungrycows;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Ступка з товкачиком (ідея Fij «духовний документ», крок 3). Рецепт — 4 діорити (`recipes/mortar.json`).
 * Ступка + пісок душ у верстаті → 9 щіпок піску душ; ступка лишається у верстаті й зношується на 1, після 34 разів ламається.
 */
public class MortarItem extends Item {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int USES = 34;   // скільки разів можна товкти (Fij)

    public MortarItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    // після крафту ступка лишається, але зношена на 1; останнє використання — ламається
    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        ItemStack left = stack.copy();
        left.setCount(1);
        left.setDamageValue(left.getDamageValue() + 1);
        return left.getDamageValue() >= left.getMaxDamage() ? ItemStack.EMPTY : left;
    }
}
