package com.gigongibus.hungrycows.client;

import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModNetwork;
import com.gigongibus.hungrycows.carve.CarveMask;
import com.gigongibus.hungrycows.carve.CarveUpdatePacket;
import com.gigongibus.hungrycows.carve.ChiselSim;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import java.util.Random;

/**
 * ВІКНО РІЗЬБЛЕННЯ (Fij): заготовка 124 × 124 на столі й зубило (текстура Fij `gui/carve/chisel_124.png`), яке хапаєш мишею (ЛКМ),
 * тягнеш, крутиш (коліщатко) і кидаєш. Без жодних підказок — лише дерево, зубило й стружка. Вирізане йде на сервер раз на
 * секунду й при закритті; викинув зубило за край — вікно закривається, зубило випадає.
 */
public class CarvingScreen extends Screen {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final ResourceLocation CHISEL_TEX = new ResourceLocation(HungryCowsMod.MODID, "textures/gui/carve/chisel_124.png");
    private static final float FILL = 0.92F;           // поле займає 92% вікна гри
    private static final int SEND_EVERY_MS = 1000;     // вирізане — на сервер раз на секунду
    private static final int TABLE = 0xFF3B2A1A, TABLE_TOP = 0xFF5A4028, BG = 0xFF1E1812;
    private static final float GRAIN = 0.10F, NOISE = 0.06F;   // прожилки й шум деревини

    private final BlockPos pos;
    private final ChiselSim sim;
    private final int woodColor;
    private final ResourceLocation woodTexId;
    private DynamicTexture woodTex;
    private boolean woodDirty = true, unsent = false;
    private int lastCarved = 0;
    private long lastSend = System.currentTimeMillis(), lastFrame = System.nanoTime();
    private float scale, ox, oy;
    private final float[] grain = new float[CarveMask.SIZE * CarveMask.SIZE];

    public static void open(BlockPos pos, Item log, CarveMask mask) {
        Minecraft.getInstance().setScreen(new CarvingScreen(pos, log, mask));
    }

    private CarvingScreen(BlockPos pos, Item log, CarveMask mask) {
        super(Component.empty());
        this.pos = pos;
        this.sim = new ChiselSim(mask);
        int col = log instanceof BlockItem bi ? bi.getBlock().defaultMapColor().col : 0xA0804A;
        this.woodColor = col == 0 ? 0xA0804A : col;
        this.woodTexId = new ResourceLocation(HungryCowsMod.MODID, "carving/" + pos.asLong());
        Random r = new Random(pos.asLong());
        float phase = r.nextFloat() * 10;
        for (int yy = 0; yy < CarveMask.SIZE; yy++)          // колода лежить — прожилки вздовж (горизонтально), трохи хвилясті
            for (int xx = 0; xx < CarveMask.SIZE; xx++)
                grain[yy * CarveMask.SIZE + xx] = 1 + GRAIN * (float) Math.sin(yy * 0.55 + Math.sin(xx * 0.07 + phase) * 2.2)
                        + NOISE * (r.nextFloat() - 0.5F);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        scale = Math.min(width * FILL / ChiselSim.WORLD_W, height * FILL / ChiselSim.WORLD_H);
        ox = (width - ChiselSim.WORLD_W * scale) / 2;
        oy = (height - ChiselSim.WORLD_H * scale) / 2;
        if (woodTex == null) {
            woodTex = new DynamicTexture(CarveMask.SIZE, CarveMask.SIZE, false);
            Minecraft.getInstance().getTextureManager().register(woodTexId, woodTex);
        }
    }

    private void uploadWood() {
        NativeImage img = woodTex.getPixels();
        if (img == null) return;
        int r0 = woodColor >> 16 & 255, g0 = woodColor >> 8 & 255, b0 = woodColor & 255;
        for (int yy = 0; yy < CarveMask.SIZE; yy++)
            for (int xx = 0; xx < CarveMask.SIZE; xx++) {
                if (!sim.mask.get(xx, yy)) { img.setPixelRGBA(xx, yy, 0); continue; }
                float k = grain[yy * CarveMask.SIZE + xx];
                int r = Math.min(255, (int) (r0 * k)), g = Math.min(255, (int) (g0 * k)), b = Math.min(255, (int) (b0 * k));
                img.setPixelRGBA(xx, yy, 0xFF000000 | b << 16 | g << 8 | r);   // NativeImage — ABGR
            }
        woodTex.upload();
        woodDirty = false;
    }

    private float wx(double mx) { return (float) ((mx - ox) / scale); }
    private float wy(double my) { return (float) ((my - oy) / scale); }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && sim.hits(wx(mx), wy(my))) sim.grab(wx(mx), wy(my));
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        sim.drag(wx(mx), wy(my));
        return true;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if (sim.held()) sim.drag(wx(mx), wy(my));
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) sim.release();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        sim.scroll(amount);
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        long now = System.nanoTime();
        sim.step((now - lastFrame) / 1e9F);
        lastFrame = now;
        if (sim.carvedPixels != lastCarved) { lastCarved = sim.carvedPixels; woodDirty = true; unsent = true; }
        if (woodDirty) uploadWood();
        if (sim.thrown) {   // Fij: викинув — крафт кінчається, зубило випадає
            send(true);
            onClose();
            return;
        }
        if (unsent && System.currentTimeMillis() - lastSend > SEND_EVERY_MS) send(false);

        g.fill(0, 0, width, height, 0xC0000000);
        int x0 = (int) ox, y0 = (int) oy, x1 = (int) (ox + ChiselSim.WORLD_W * scale), y1 = (int) (oy + ChiselSim.WORLD_H * scale);
        g.fill(x0, y0, x1, y1, BG);
        g.fill(x0, y1 - 2, x1, y1 + (int) (10 * scale), TABLE);
        g.fill(x0, y1 - 2, x1, y1, TABLE_TOP);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(ox, oy, 0);
        pose.scale(scale, scale, 1);
        // заготовка
        g.blit(woodTexId, (int) ChiselSim.WOOD_X, (int) ChiselSim.WOOD_Y, 0, 0, CarveMask.SIZE, CarveMask.SIZE, CarveMask.SIZE, CarveMask.SIZE);
        // стружка
        int r0 = woodColor >> 16 & 255, g0 = woodColor >> 8 & 255, b0 = woodColor & 255;
        for (float[] c : sim.chips) {
            float k = 0.8F + 0.4F * c[5];
            int col = 0xFF000000 | Math.min(255, (int) (r0 * k)) << 16 | Math.min(255, (int) (g0 * k)) << 8 | Math.min(255, (int) (b0 * k));
            g.fill((int) c[0], (int) c[1], (int) c[0] + 2, (int) c[1] + 2, col);
        }
        // зубило
        pose.pushPose();
        pose.translate(sim.x, sim.y, 0);
        pose.mulPose(Axis.ZP.rotation(sim.angle));
        pose.translate(-ChiselSim.COM_X, -ChiselSim.COM_Y, 0);
        g.blit(CHISEL_TEX, 0, 0, 0, 0, 124, 124, 124, 124);
        pose.popPose();
        pose.popPose();
    }

    private void send(boolean thrown) {
        ModNetwork.CHANNEL.sendToServer(new CarveUpdatePacket(pos, sim.mask.toBytes(), thrown));
        lastSend = System.currentTimeMillis();
        unsent = false;
    }

    @Override
    public void removed() {
        if (!sim.thrown) send(false);
        Minecraft.getInstance().getTextureManager().release(woodTexId);
        super.removed();
    }
}
