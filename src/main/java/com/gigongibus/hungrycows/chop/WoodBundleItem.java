package com.gigongibus.hungrycows.chop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * В'язанка дров (Fij, 18.11): перев'язана двома нитками купа, зламана одним предметом. ПКМ по землі — стає купою точно такою,
 * як була (ті самі шматки на тих самих місцях), і далі перев'язана.
 */
public class WoodBundleItem extends Item {
    public WoodBundleItem(Properties p) {
        super(p);
    }

    public static ItemStack of(WoodPileBlockEntity be) {
        ItemStack s = new ItemStack(Chopping.WOOD_BUNDLE.get());
        s.getOrCreateTag().put("Pieces", be.piecesTag());
        return s;
    }

    public static List<WoodPiece> pieces(ItemStack s) {
        CompoundTag t = s.getTag();
        ListTag l = t == null ? new ListTag() : t.getList("Pieces", Tag.TAG_COMPOUND);
        List<WoodPiece> out = WoodPileBlockEntity.piecesFrom(l);
        if (out.isEmpty()) out.add(WoodPiece.whole(Direction.Axis.Y, true, false));   // з творчої вкладки — ціла колода
        return out;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos at = level.getBlockState(ctx.getClickedPos()).canBeReplaced() ? ctx.getClickedPos() : ctx.getClickedPos().relative(ctx.getClickedFace());
        if (!level.getBlockState(at).canBeReplaced() || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP))
            return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        level.setBlock(at, Chopping.WOOD_PILE.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(at) instanceof WoodPileBlockEntity pile)) return InteractionResult.FAIL;
        pile.setPieces(pieces(ctx.getItemInHand()));
        pile.setStrings(WoodPileBlockEntity.STRINGS_TO_TIE);
        level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1F, 0.8F);
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
