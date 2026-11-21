package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.HungryCowsMod;
import net.minecraft.gametest.framework.GameTestBatch;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Для швидкої перевірки: якщо в робочій теці тестів (`run-gametest/`) є файл `only_batches.txt` (назви партій через кому чи
 * з нового рядка, напр. `salt`), запускаються лише ці партії. Немає файлу — усі тести, як завжди. На гру не впливає.
 */
@Mod.EventBusSubscriber(modid = HungryCowsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OnlyBatches {
    private static final String FILE = "only_batches.txt";

    private OnlyBatches() {
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void filter(ServerAboutToStartEvent event) {
        if (!(event.getServer() instanceof GameTestServer server)) return;
        Path p = Path.of(FILE);
        if (!Files.exists(p)) return;
        try {
            Set<String> keep = new HashSet<>();
            for (String s : Files.readString(p).split("[,\\s]+")) if (!s.isBlank()) keep.add(s.trim());
            if (keep.isEmpty()) return;
            for (Field f : GameTestServer.class.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                List<GameTestBatch> batches = (List<GameTestBatch>) f.get(server);
                // назва партії в грі — «назва:номер» (напр. "salt:1")
                batches.removeIf(b -> !keep.contains(b.getName().split(":")[0]));
            }
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().warn("only_batches.txt: не вдалося відібрати партії", e);
        }
    }
}
