package com.gigongibus.hungrycows;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Теля від мандрівного торговця (етап 10): торговець іноді продає теля за 30 смарагдів. Теля — не предмет: щойно «квиток»
 * потрапляє в інвентар, біля гравця з'являється теля, а квиток зникає. Теля вважає гравця матір'ю (поїти молоком — див. сиріт).
 */
public class CalfTicketItem extends Item {
    public CalfTicketItem(Properties p) {
        super(p);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (level.isClientSide || !(holder instanceof Player player) || !(level instanceof ServerLevel server)) return;
        int n = stack.getCount();
        stack.setCount(0);
        for (int i = 0; i < n; i++) {
            HungryCow calf = ModEntities.HUNGRY_COW.get().create(server);
            if (calf == null) continue;
            calf.moveTo(player.getX() + (server.getRandom().nextDouble() - 0.5) * 2, player.getY(), player.getZ() + (server.getRandom().nextDouble() - 0.5) * 2,
                    player.getYRot() + 180F, 0);
            calf.finalizeSpawn(server, server.getCurrentDifficultyAt(player.blockPosition()), MobSpawnType.TRIGGERED, null, null);
            calf.setAge(-24000);
            calf.family.adoptByPlayer(player);
            server.addFreshEntity(calf);
            player.sendSystemMessage(Component.literal("Ви купили теля «" + calf.family.name + "». Воно вважає вас мамою — напоїть його молоком."));
        }
    }
}
