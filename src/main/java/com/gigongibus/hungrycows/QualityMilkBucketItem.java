package com.gigongibus.hungrycows;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MilkBucketItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Відро молока від нашої корови. Якість зберігається в NBT предмета ("Quality").
 */
public class QualityMilkBucketItem extends MilkBucketItem {

    public QualityMilkBucketItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(MilkQuality quality) {
        ItemStack stack = new ItemStack(ModItems.QUALITY_MILK_BUCKET.get());
        stack.getOrCreateTag().putString("Quality", quality.name());
        return stack;
    }

    public static MilkQuality quality(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("Quality") ? MilkQuality.byName(tag.getString("Quality")) : MilkQuality.V;
    }

    // Замість ванільного «зняти всі ефекти» діє за якістю
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            quality(stack).apply(entity);
        }
        if (entity instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
            serverPlayer.awardStat(Stats.ITEM_USED.get(this));
        }
        if (entity instanceof Player player && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return stack.isEmpty() ? new ItemStack(Items.BUCKET) : stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack), quality(stack).letter);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(quality(stack).description).withStyle(ChatFormatting.GRAY));
    }
}
