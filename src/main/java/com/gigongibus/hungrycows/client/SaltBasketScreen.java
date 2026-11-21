package com.gigongibus.hungrycows.client;

import com.gigongibus.hungrycows.salt.SaltBasketBlockEntity;
import com.gigongibus.hungrycows.salt.SaltBasketMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Екран корзини (як у діжки: без картинки, рамки й смуга кольором) */
public class SaltBasketScreen extends AbstractContainerScreen<SaltBasketMenu> {
    private static final int BG = 0xFFC6C6C6, DARK = 0xFF373737, LIGHT = 0xFFFFFFFF, SLOT = 0xFF8B8B8B;
    private static final int BAR = 0xFF6FB4E8, BAR_DONE = 0xFFF2F2F2, BAR_BG = 0xFF555555, ARROW = 0xFF8B8B8B;

    public SaltBasketScreen(SaltBasketMenu menu, Inventory inv, Component title) {
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
        slot(g, x + SaltBasketMenu.SALT_X, y + SaltBasketMenu.SALT_Y);
        slot(g, x + SaltBasketMenu.BLOCK_X, y + SaltBasketMenu.BLOCK_Y);
        // стрілка між місцями
        int ax = x + 68, ay = y + 26;
        g.fill(ax, ay, ax + 36, ay + 4, ARROW);
        for (int i = 0; i < 5; i++) g.fill(ax + 36 + i, ay - 3 + i, ax + 37 + i, ay + 7 - i, ARROW);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) slot(g, x + 8 + c * 18, y + 84 + r * 18);
        for (int c = 0; c < 9; c++) slot(g, x + 8 + c * 18, y + 142);
        // смуга прогресу
        int p = menu.data.get(0), bx = x + 30, by = y + 40, bw = 116;
        boolean done = menu.data.get(3) == 1;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 7, DARK);
        g.fill(bx, by, bx + bw, by + 6, BAR_BG);
        g.fill(bx, by, bx + (done ? bw : bw * p / 1000), by + 6, done ? BAR_DONE : BAR);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        super.renderLabels(g, mx, my);
        int salt = menu.getSlot(0).getItem().getCount();
        boolean hanging = menu.data.get(1) == 1, air = menu.data.get(2) == 1, done = menu.data.get(3) == 1;
        String state;
        if (done) state = "gui.hungrycows.basket.done";
        else if (!hanging) state = "gui.hungrycows.basket.no_chain";
        else if (!air) state = "gui.hungrycows.basket.no_air";
        else if (salt < SaltBasketBlockEntity.CAPACITY) state = "gui.hungrycows.basket.missing";
        else state = "gui.hungrycows.basket.dripping";
        g.drawString(font, Component.translatable("gui.hungrycows.basket.salt", salt, SaltBasketBlockEntity.CAPACITY), 8, 50, 0x404040, false);
        g.drawString(font, Component.translatable(state, menu.data.get(0) / 10), 8, 60, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
    }
}
