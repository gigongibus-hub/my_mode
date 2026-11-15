package com.gigongibus.hungrycows.salt;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Пучок лози певної стадії: кладеться на землю тим самим пучком (сухий — сухим тощо) */
public class WickerBundleItem extends BlockItem {
    private final WickerBundleBlock.Stage stage;

    public WickerBundleItem(Block block, WickerBundleBlock.Stage stage, Properties p) {
        super(block, p);
        this.stage = stage;
    }

    @Override
    protected BlockState getPlacementState(BlockPlaceContext ctx) {
        BlockState s = super.getPlacementState(ctx);
        return s == null ? null : s.setValue(WickerBundleBlock.STAGE, stage);
    }

    public WickerBundleBlock.Stage stage() {
        return stage;
    }

    @Override
    public String getDescriptionId() {
        return "item.hungrycows.wicker_bundle_" + stage.getSerializedName();
    }

    @Override
    public void registerBlocks(java.util.Map<Block, net.minecraft.world.item.Item> map, net.minecraft.world.item.Item item) {
        if (stage == WickerBundleBlock.Stage.FRESH) super.registerBlocks(map, item);   // «головний» предмет блока — свіжий
    }
}
