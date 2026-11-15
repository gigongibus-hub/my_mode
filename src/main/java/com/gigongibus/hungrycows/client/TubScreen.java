package com.gigongibus.hungrycows.client;

import com.gigongibus.hungrycows.salt.TubBlockEntity;
import com.gigongibus.hungrycows.salt.TubMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Екран діжки (без окремої картинки: рамки й смуга малюються кольором). */
public class TubScreen extends AbstractContainerScreen<TubMenu> {
    private static final int BG = 0xFFC6C6C6, DARK = 0xFF373737, LIGHT = 0xFFFFFFFF, SLOT = 0xFF8B8B8B;
    private static final int BAR = 0xFF6FB4E8, BAR_BRINE = 0xFFE8F2F8, BAR_BG = 0xFF555555;

    public TubScreen(TubMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    private void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, DARK);
        g.fill(x, y, x + 17, y + 17, LIGHT);
        g.fill(x, y, x + 16, y + 16, SLOT);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        g.fill(x, y, x + imageWidth, y + 1, LIGHT);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, DARK);
        slot(g, x + TubMenu.SALT_X, y + TubMenu.SALT_Y);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) slot(g, x + 8 + c * 18, y + 84 + r * 18);
        for (int c = 0; c < 9; c++) slot(g, x + 8 + c * 18, y + 142);
        // смуга прогресу
        int p = menu.data.get(0), bx = x + 30, by = y + 44, bw = 116;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 7, DARK);
        g.fill(bx, by, bx + bw, by + 6, BAR_BG);
        boolean brine = menu.data.get(2) == 1;
        g.fill(bx, by, bx + (brine ? bw : bw * p / 1000), by + 6, brine ? BAR_BRINE : BAR);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        super.renderLabels(g, mx, my);
        int water = menu.data.get(1);
        boolean brine = menu.data.get(2) == 1, lid = menu.data.get(3) == 1;
        int salt = menu.getSlot(0).getItem().getCount();
        g.drawString(font, Component.translatable("gui.hungrycows.tub.salt", salt, TubBlockEntity.MAX_SALT), 8, 24, 0x404040, false);
        g.drawString(font, Component.translatable(brine ? "gui.hungrycows.tub.brine" : "gui.hungrycows.tub.water", water, TubBlockEntity.MAX_WATER),
                102, 24, 0x404040, false);
        String state;
        if (brine) state = "gui.hungrycows.tub.done";
        else if (!lid) state = "gui.hungrycows.tub.open";
        else if (water < TubBlockEntity.MAX_WATER || salt < TubBlockEntity.MAX_SALT) state = "gui.hungrycows.tub.missing";
        else state = "gui.hungrycows.tub.soaking";
        g.drawString(font, Component.translatable(state, menu.data.get(0) / 10), 30, 56, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
    }
}
