package com.gigongibus.hungrycows.chop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Шматок колоди в руці (Fij): ПКМ по землі — покласти (стоячи; присівши — лежачи), ПКМ по купі — докласти, якщо влазить.
 * Назва за розміром: колода, половинка, четвертинка, поліно, скіпка. Ціла колода темного дуба теж такий предмет, коли її забрати з купи.
 */
public class WoodPieceItem extends Item {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final float[] SIZE_FROM = { 0.9F, 0.4F, 0.2F, 0.09F };   // частка колоди: ціла / половинка / четвертинка / поліно / скіпка
    public static final String[] SIZE_KEYS = { "log", "half", "quarter", "billet", "splinter" };

    public WoodPieceItem(Properties p) {
        super(p);
    }

    /** 0 ціла … 4 скіпка */
    public static int sizeClass(float fraction) {
        for (int i = 0; i < SIZE_FROM.length; i++) if (fraction >= SIZE_FROM[i]) return i;
        return SIZE_FROM.length;
    }

    public static ItemStack of(WoodPiece p) {
        ItemStack s = new ItemStack(Chopping.WOOD_PIECE.get());
        // однакові шматки — однаковий запис (волокна вгору, ширший бік уздовж x), щоб складались у стос
        WoodPiece c = p;
        if (c.grain != Direction.Axis.Y) c = c.reoriented(c.grain == Direction.Axis.X ? new int[]{ 1, 0, 2 } : new int[]{ 0, 2, 1 });
        if (c.sx < c.sz) c = c.reoriented(new int[]{ 2, 1, 0 });
        s.getOrCreateTag().put("Piece", c.itemTag());
        return s;
    }

    public static WoodPiece piece(ItemStack s) {
        CompoundTag t = s.getTag();
        if (t != null && t.contains("Piece")) return WoodPiece.load(t.getCompound("Piece"));
        return WoodPiece.whole(Direction.Axis.Y, true, false);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.hungrycows.wood_piece." + SIZE_KEYS[sizeClass(piece(stack).fraction())]);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos clicked = ctx.getClickedPos();
        boolean lying = ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown();
        WoodPiece p = piece(ctx.getItemInHand());
        Direction.Axis along = ctx.getPlayer() == null ? Direction.Axis.X : ctx.getPlayer().getDirection().getAxis();
        if (level.getBlockEntity(clicked) instanceof WoodPileBlockEntity pile) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            double tx = (ctx.getClickLocation().x - clicked.getX()) * 16, tz = (ctx.getClickLocation().z - clicked.getZ()) * 16;
            if (!pile.add(p, lying, tx, tz, along)) return InteractionResult.FAIL;
            done(ctx, clicked);
            return InteractionResult.CONSUME;
        }
        BlockPos at = clicked.relative(ctx.getClickedFace());
        if (!level.getBlockState(at).canBeReplaced() || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP))
            return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        level.setBlock(at, Chopping.WOOD_PILE.get().defaultBlockState(), 3);
        double tx = (ctx.getClickLocation().x - at.getX()) * 16, tz = (ctx.getClickLocation().z - at.getZ()) * 16;
        if (!(level.getBlockEntity(at) instanceof WoodPileBlockEntity pile) || !pile.add(p, lying, tx, tz, along)) {
            level.removeBlock(at, false);
            return InteractionResult.FAIL;
        }
        done(ctx, at);
        return InteractionResult.CONSUME;
    }

    private static void done(UseOnContext ctx, BlockPos pos) {
        ctx.getLevel().playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1F, 0.9F);
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
    }
}
