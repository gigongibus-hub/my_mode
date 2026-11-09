package com.gigongibus.hungrycows;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * «Корововед» — духовний документ (ідея Fij, крок 4). Крафт: папір з кров'ю посередині + 8 щіпок піску душ навколо.
 * ПКМ у повітря — відкриває головне про корову, чия кров (ім'я, вік, стать, масть, молоко, родовід, характер, гени).
 * Це знімок на момент уколу голкою; «інколи потрібно оновлювати» (Fij) — ПКМ «Корововедом» по тій самій корові,
 * але лише 6 разів (Fij), далі — робити новий.
 */
public class CowReaderItem extends Item {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int MAX_REFRESHES = 6;   // оновити можна лише 6 разів, потім — робити новий (Fij)
    public static final String TAG_REFRESHES = "Refreshes";

    public CowReaderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            CompoundTag tag = stack.getTag();
            List<String> lines = new ArrayList<>();
            if (tag != null && tag.contains(BloodNeedleItem.TAG_PAGES)) {
                ListTag list = tag.getList(BloodNeedleItem.TAG_PAGES, Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) lines.add(list.getString(i));
                long ago = level.getDayTime() / 24000L - tag.getLong(BloodNeedleItem.TAG_DAY);
                if (!lines.isEmpty()) lines.add(1, "Записано " + (ago <= 0 ? "сьогодні" : ago + " діб тому")
                        + ". Оновити: ПКМ Корововедом по корові.");
            } else {
                lines.add("#Корововед");
                lines.add("Порожній. Візьми кров корови голкою і зроби новий.");
            }
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new OpenCowBookPacket(lines));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // ПКМ по корові: та сама — оновити знімок
    public static void refresh(ItemStack stack, HungryCow cow, Player player) {
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.hasUUID(BloodNeedleItem.TAG_ID) && tag.getUUID(BloodNeedleItem.TAG_ID).equals(cow.getUUID())) {
            int used = tag.getInt(TAG_REFRESHES);
            if (used >= MAX_REFRESHES) {
                player.displayClientMessage(Component.literal("Корововед більше не оновлюється: зроби новий"), true);
                return;
            }
            BloodNeedleItem.writeSnapshot(tag, cow);
            tag.putInt(TAG_REFRESHES, used + 1);
            player.displayClientMessage(Component.literal("Корововед оновлено: " + cow.family.name
                    + " (лишилось оновлень: " + (MAX_REFRESHES - used - 1) + ")"), true);
        } else {
            player.displayClientMessage(Component.literal("Це не та корова: Корововед знає лише "
                    + tag.getString(BloodNeedleItem.TAG_NAME)), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(BloodNeedleItem.TAG_NAME)) {
            tooltip.add(Component.literal("Корова: " + tag.getString(BloodNeedleItem.TAG_NAME)).withStyle(ChatFormatting.DARK_PURPLE));
            tooltip.add(Component.literal("Оновлень лишилось: " + Math.max(0, MAX_REFRESHES - tag.getInt(TAG_REFRESHES))).withStyle(ChatFormatting.GRAY));
        }
    }
}
