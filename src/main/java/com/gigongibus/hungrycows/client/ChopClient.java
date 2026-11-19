package com.gigongibus.hungrycows.client;

import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModNetwork;
import com.gigongibus.hungrycows.chop.ChopPacket;
import com.gigongibus.hungrycows.chop.Chopping;
import com.gigongibus.hungrycows.chop.WoodPiece;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * РОЗРУБКА в грі гравця (Fij): затиснув ЛКМ сокирою на колоді / шматку — гра не ламає блок; з'являється рамка розрубу (де пройде
 * лезо) і шкала сили біля прицілу, що наповнюється, поки тримаєш. ←/→ — зсунути рамку на піксель, ↑/↓ — повернути (інша вісь).
 * Відпустив — удар (`ChopPacket`). Без написів: лише рамка, шкала, звук і тріски.
 */
@Mod.EventBusSubscriber(modid = HungryCowsMod.MODID, value = Dist.CLIENT)
public final class ChopClient {
    private ChopClient() {
    }

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final float LINE_R = 1F, LINE_G = 0.95F, LINE_B = 0.8F, LINE_A = 0.9F;   // рамка — світла, ледь тепла
    public static final int BAR_W = 3, BAR_H = 24, BAR_DX = 9;                              // шкала праворуч від прицілу

    private static boolean charging;
    private static BlockPos target;
    private static int index, at;
    private static Direction face;
    private static Direction.Axis axis;
    private static float power;

    /** Затиснута ЛКМ на тому, що можна рубати, — не ламати й не махати рукою */
    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = lookedPos(mc);
        if (charging || (pos != null && mc.player != null && Chopping.canChop(mc.level, mc.player, pos))) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    private static BlockPos lookedPos(Minecraft mc) {
        return mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK ? bh.getBlockPos() : null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) { charging = false; return; }
        boolean down = mc.options.keyAttack.isDown();
        if (!charging) {
            BlockPos pos = lookedPos(mc);
            if (!down || pos == null || !Chopping.canChop(mc.level, mc.player, pos)) return;
            List<WoodPiece> pieces = Chopping.targetPieces(mc.level, pos);
            int[] hit = pieces == null ? null : Chopping.pick(pieces, pos, mc.player.getEyePosition(), mc.player.getLookAngle());
            if (hit == null) return;
            charging = true;
            target = pos;
            index = hit[0];
            face = Direction.from3DDataValue(hit[1]);
            WoodPiece p = pieces.get(index);
            // спершу — уздовж волокон (розколоти), рамка посередині
            axis = null;
            for (Direction.Axis a : Direction.Axis.values()) if (a != face.getAxis() && a != p.grain && p.size(a) >= 2) { axis = a; break; }
            if (axis == null) for (Direction.Axis a : Direction.Axis.values()) if (a != face.getAxis() && p.size(a) >= 2) { axis = a; break; }
            if (axis == null) { charging = false; return; }
            at = p.size(axis) / 2;
            // є засічка (сокира застрягла) — рамка одразу на ній, щоб удар доповнив
            if (p.notchAxis >= 0 && p.notchAxis != face.getAxis().ordinal()) {
                axis = Direction.Axis.values()[p.notchAxis];
                at = p.notchAt;
            }
            // стовбур дерева (Fij): лише горизонтально; рамка — там, де вже є зарубка, або низько
            if (com.gigongibus.hungrycows.fell.Felling.isTrunk(mc.level, pos)) {
                axis = Direction.Axis.Y;
                at = mc.level.getBlockEntity(pos) instanceof com.gigongibus.hungrycows.fell.FellingCutBlockEntity cut
                        ? cut.at() : com.gigongibus.hungrycows.fell.Felling.DEFAULT_AT;
            }
            power = 0;
            return;
        }
        WoodPiece p = current(mc);
        if (p == null || !Chopping.holdsAxe(mc.player)) { charging = false; return; }
        if (down) {
            power = Math.min(1, power + 1F / Chopping.GAUGE_TICKS);
            return;
        }
        ModNetwork.CHANNEL.sendToServer(new ChopPacket(target, index, axis.ordinal(), at, power));
        mc.player.swing(InteractionHand.MAIN_HAND);
        charging = false;
    }

    private static WoodPiece current(Minecraft mc) {
        if (target == null) return null;
        List<WoodPiece> pieces = Chopping.targetPieces(mc.level, target);
        return pieces == null || index >= pieces.size() ? null : pieces.get(index);
    }

    /** Стрілки: ←/→ зсувають рамку туди, куди на екрані; ↑/↓ — повернути на іншу вісь */
    @SubscribeEvent
    public static void onKey(InputEvent.Key e) {
        if (!charging || (e.getAction() != GLFW.GLFW_PRESS && e.getAction() != GLFW.GLFW_REPEAT)) return;
        Minecraft mc = Minecraft.getInstance();
        WoodPiece p = current(mc);
        if (p == null || mc.player == null) return;
        int k = e.getKey();
        boolean trunk = com.gigongibus.hungrycows.fell.Felling.isTrunk(mc.level, target);
        if (trunk && mc.level.getBlockEntity(target) instanceof com.gigongibus.hungrycows.fell.FellingCutBlockEntity) return;   // зарубку вже почато
        if (trunk && k != GLFW.GLFW_KEY_LEFT && k != GLFW.GLFW_KEY_RIGHT) return;   // стовбур — лише горизонтально, висоту стрілками ←/→
        if (trunk) {
            at = Math.max(com.gigongibus.hungrycows.fell.Felling.MIN_AT, Math.min(com.gigongibus.hungrycows.fell.Felling.MAX_AT,
                    at + (k == GLFW.GLFW_KEY_RIGHT ? 1 : -1)));
            return;
        }
        if (k == GLFW.GLFW_KEY_UP || k == GLFW.GLFW_KEY_DOWN) {
            for (Direction.Axis a : Direction.Axis.values())
                if (a != axis && a != face.getAxis() && p.size(a) >= 2) { axis = a; at = p.size(a) / 2; break; }
        } else if (k == GLFW.GLFW_KEY_LEFT || k == GLFW.GLFW_KEY_RIGHT) {
            int dir = k == GLFW.GLFW_KEY_RIGHT ? 1 : -1;
            at = Math.max(1, Math.min(p.size(axis) - 1, at + dir * screenSign(mc, axis)));
        }
    }

    /** Чи «праворуч» на екрані — це «більше» по осі */
    private static int screenSign(Minecraft mc, Direction.Axis a) {
        if (a == Direction.Axis.Y) return 1;                  // по висоті: → — вище
        Vec3 look = mc.player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x);
        double d = a == Direction.Axis.X ? right.x : right.z;
        if (Math.abs(d) > 0.3) return d > 0 ? 1 : -1;
        double f = a == Direction.Axis.X ? look.x : look.z;   // вісь «від себе» — → далі
        return f >= 0 ? 1 : -1;
    }

    /** Рамка розрубу: прямокутник там, де пройде лезо */
    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (!charging || e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        WoodPiece p = current(mc);
        if (p == null) return;
        double[] mn = { p.x, p.y, p.z }, mx = { p.x + p.sx, p.y + p.sy, p.z + p.sz };
        int a = axis.ordinal();
        mn[a] = mx[a] = p.pos(axis) + at;
        double pad = 0.15;
        for (int i = 0; i < 3; i++) if (i != a) { mn[i] -= pad; mx[i] += pad; }
        Vec3 cam = e.getCamera().getPosition();
        AABB box = new AABB(target.getX() + mn[0] / 16, target.getY() + mn[1] / 16, target.getZ() + mn[2] / 16,
                target.getX() + mx[0] / 16, target.getY() + mx[1] / 16, target.getZ() + mx[2] / 16).move(-cam.x, -cam.y, -cam.z);
        PoseStack pose = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        LevelRenderer.renderLineBox(pose, buf.getBuffer(RenderType.lines()), box, LINE_R, LINE_G, LINE_B, LINE_A);
        buf.endBatch(RenderType.lines());
    }

    /** Шкала сили біля прицілу (без цифр): знизу вгору, колір від зеленого до червоного */
    public static void registerOverlay(RegisterGuiOverlaysEvent e) {
        e.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "chop_gauge", (gui, g, partial, w, h) -> {
            if (!charging) return;
            int x = w / 2 + BAR_DX, y = h / 2 - BAR_H / 2, fill = Math.round(power * BAR_H);
            g.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, 0xA0000000);
            int r = (int) (255 * Math.min(1, power * 2)), gr = (int) (255 * Math.min(1, 2 - power * 2));
            g.fill(x, y + BAR_H - fill, x + BAR_W, y + BAR_H, 0xFF000000 | r << 16 | gr << 8 | 0x30);
        });
    }

    /** Для перевірки в грі */
    public static boolean charging() {
        return charging;
    }
}
