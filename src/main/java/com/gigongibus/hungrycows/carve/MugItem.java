package com.gigongibus.hungrycows.carve;

import com.gigongibus.hungrycows.MilkQuality;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * ДЕРЕВ'ЯНА КРУЖКА (модель і текстура Fij): з заготовки, вушок і ручки. Міцність = скільки разів можна випити (з точності деталей).
 * Наливати (Fij «якщо зможеш»): ПКМ по воді / котлу з водою — вода; ПКМ по корові, що дає молоко, — молоко її якості
 * (`HungryCow`). Повна — ПКМ: випити (молоко діє за якістю, як відро; вода — гасить вогонь), −1 міцності, кружка порожня.
 * У NBT «Drink»: water / milk (+ «Milk» — якість).
 */
public class MugItem extends Item {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int MAX_USES = 64;          // найкраща кружка — 64 рази
    public static final int MIN_USES = 4;           // ледь схожа — 4
    public static final int DRINK_TICKS = 24;       // п'є 1,2 с (відро — 1,6)

    public MugItem(Properties p) {
        super(p.durability(MAX_USES));
    }

    public static String drink(ItemStack s) {
        return s.hasTag() ? s.getTag().getString("Drink") : "";
    }

    /** 0 порожня, 1 вода, 2 молоко — для вигляду (рідина в моделі) */
    public static float drinkKind(ItemStack s) {
        return switch (drink(s)) { case "water" -> 1; case "milk" -> 2; default -> 0; };
    }

    public static void fillMilk(ItemStack s, MilkQuality q) {
        CompoundTag t = s.getOrCreateTag();
        t.putString("Drink", "milk");
        t.putString("Milk", q.name());
    }

    public static void fillWater(ItemStack s) {
        s.getOrCreateTag().putString("Drink", "water");
    }

    /** Кружка з такою кількістю використань */
    public static ItemStack withUses(ItemStack s, int uses) {
        s.setDamageValue(Math.max(0, MAX_USES - Math.max(1, Math.min(MAX_USES, uses))));
        return s;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (!drink(s).isEmpty()) return net.minecraft.world.item.ItemUtils.startUsingInstantly(level, player, hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(s);
        var pos = hit.getBlockPos();
        var st = level.getBlockState(pos);
        boolean water = level.getFluidState(pos).is(Fluids.WATER);
        if (!water && st.is(Blocks.WATER_CAULDRON)) {
            water = true;
            if (!level.isClientSide) LayeredCauldronBlock.lowerFillLevel(st, level, pos);
        }
        if (!water) return InteractionResultHolder.pass(s);
        if (!level.isClientSide) {
            fillWater(s);
            level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1F, 1.2F);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack s, Level level, LivingEntity who) {
        String d = drink(s);
        if (!level.isClientSide && !d.isEmpty()) {
            if (d.equals("milk")) MilkQuality.byName(s.getTag().getString("Milk")).apply(who);
            else who.clearFire();
            s.getTag().remove("Drink");
            s.getTag().remove("Milk");
            if (!(who instanceof Player p && p.getAbilities().instabuild))
                s.hurtAndBreak(1, who, e -> e.broadcastBreakEvent(who.getUsedItemHand()));
        }
        return s;
    }

    @Override
    public int getUseDuration(ItemStack s) {
        return drink(s).isEmpty() ? 0 : DRINK_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) {
        return UseAnim.DRINK;
    }

    @Override
    public Component getName(ItemStack s) {
        String d = drink(s);
        return d.isEmpty() ? super.getName(s) : Component.translatable(getDescriptionId() + "." + d);
    }
}
