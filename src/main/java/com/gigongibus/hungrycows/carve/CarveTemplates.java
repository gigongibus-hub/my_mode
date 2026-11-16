package com.gigongibus.hungrycows.carve;

import com.gigongibus.hungrycows.HungryCowsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Шаблони ремесла: малюнки 124 × 124 у `data/hungrycows/carving_templates/<назва>.png` (Fij: чорне — лишається, решта — вибрати).
 * Сервер читає їх раз і пам'ятає. «Чорне» = темний непрозорий піксель.
 */
public final class CarveTemplates {
    private CarveTemplates() {
    }

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int DARK = 90;              // яскравість нижче — «чорне», дерево лишається
    private static final Map<String, CarveMask> CACHE = new HashMap<>();

    public static CarveMask keep(MinecraftServer server, String name) {
        return CACHE.computeIfAbsent(name, n -> load(server, n));
    }

    private static CarveMask load(MinecraftServer server, String name) {
        CarveMask m = CarveMask.empty();
        var res = server == null ? null : server.getResourceManager()
                .getResource(new ResourceLocation(HungryCowsMod.MODID, "carving_templates/" + name + ".png")).orElse(null);
        if (res == null) return m;
        // ImageIO (звичайна Java), не NativeImage: та лише в грі гравця, на сервері її немає
        try (InputStream in = res.open()) {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
            for (int y = 0; y < Math.min(CarveMask.SIZE, img.getHeight()); y++)
                for (int x = 0; x < Math.min(CarveMask.SIZE, img.getWidth()); x++) {
                    int c = img.getRGB(x, y);                  // ARGB
                    int a = c >>> 24 & 255, r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
                    if (a > 128 && (r + g + b) / 3 < DARK) m.set(x, y);
                }
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().warn("Шаблон ремесла {} не прочитано: {}", name, e.toString());
        }
        return m;
    }
}
