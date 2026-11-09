package com.gigongibus.hungrycows;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Голка з кров'ю і папір з кров'ю (ідея Fij «духовний документ», кроки 1–2; обидва пам'ятають, чия кров). Чиста голка (3 залізні самородки стовпчиком) — ПКМ по корові:
 * корова не втрачає здоров'я, але лякається, і довіра до гравця трохи падає. Голка стає «з кров'ю» й пам'ятає, чия кров.
 * Далі (крок 2) голка з кров'ю + папір → папір з кров'ю, голка повертається чистою.
 */
public class BloodNeedleItem extends Item {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int PRICK_FEAR = 15;      // укол — страх +15 (урону немає, Fij)
    public static final int PRICK_TRUST = -3;     // ... і довіра до гравця −3 (Fij: «трохи знижується»)

    public static final String TAG_ID = "CowId", TAG_NAME = "CowName", TAG_PAGES = "CowPages", TAG_DAY = "CowDay";

    public BloodNeedleItem(Properties properties) {
        super(properties);
    }

    // Уколоти корову чистою голкою (з HungryCow.mobInteract): повертає голку з кров'ю
    public static ItemStack prick(HungryCow cow, Player player) {
        cow.fear = Math.min(100, cow.fear + PRICK_FEAR);
        cow.addTrust(player, PRICK_TRUST, "уколов голкою", false);
        cow.level().playSound(null, cow.blockPosition(), SoundEvents.COW_HURT, SoundSource.NEUTRAL, 0.6F, 1.3F);
        ItemStack bloody = new ItemStack(ModItems.BLOODY_NEEDLE.get());
        CompoundTag tag = bloody.getOrCreateTag();
        tag.putUUID(TAG_ID, cow.getUUID());
        tag.putString(TAG_NAME, cow.family.name);
        writeSnapshot(tag, cow);   // знімок «Корововеда» береться в момент уколу (оновлюється ПКМ «Корововедом» по корові)
        return bloody;
    }

    // Знімок корови: рядки «головного» і день, коли знято
    public static void writeSnapshot(CompoundTag tag, HungryCow cow) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (String line : CowBook.essentials(cow)) list.add(net.minecraft.nbt.StringTag.valueOf(line));
        tag.put(TAG_PAGES, list);
        tag.putLong(TAG_DAY, cow.level().getDayTime() / 24000L);
        tag.putString(TAG_NAME, cow.family.name);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_NAME)) {
            tooltip.add(Component.literal("Кров корови: " + tag.getString(TAG_NAME)).withStyle(ChatFormatting.DARK_RED));
        }
    }
}
