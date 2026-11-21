package com.gigongibus.hungrycows.salt;

import com.gigongibus.hungrycows.HungryCowsMod;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Події ланцюжка солі */
@Mod.EventBusSubscriber(modid = HungryCowsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SaltEvents {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final float GROUND_BONUS_CHANCE = 0.5F;   // ступка: 1 шматок → 1 або 2 «не чистої» (Fij «від 1 до 2»)

    private SaltEvents() {
    }

    // рецепт дає 1; з шансом 50% — ще одна в інвентар (у рецепті не можна випадкову кількість: верстат показує результат наперед)
    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack out = event.getCrafting();
        if (event.getEntity().level().isClientSide || !out.is(Salt.GROUND_SALT_DIRTY.get())) return;
        if (event.getEntity().getRandom().nextFloat() < GROUND_BONUS_CHANCE) {
            ItemStack extra = new ItemStack(Salt.GROUND_SALT_DIRTY.get());
            if (!event.getEntity().getInventory().add(extra)) event.getEntity().drop(extra, false);
        }
    }
}
