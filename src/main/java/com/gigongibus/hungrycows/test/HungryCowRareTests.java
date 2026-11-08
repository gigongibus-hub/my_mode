package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModItems;
import com.gigongibus.hungrycows.VillageCows;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Рідкісні корови (етап 10). Ігрова логіка не змінюється: тест кличе те саме, що й гра (поява корови, корови для села, квиток-теля).
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowRareTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    // 1. Ванільна корова не з'являється
    @GameTest(template = "platform", batch = "rare", timeoutTicks = 40)
    public static void noVanillaCows(GameTestHelper h) {
        TestWorld.calm(h);
        h.spawn(EntityType.COW, new BlockPos(4, 2, 4));
        h.runAtTickTime(5, () -> {
            AABB box = new AABB(h.absolutePos(new BlockPos(0, 0, 0))).inflate(10);
            long n = h.getLevel().getEntities((net.minecraft.world.entity.Entity) null, box, e -> e.getType() == EntityType.COW).size();
            LOG.info("[тест] noVanillaCows: ванільних корів після появи {}", n);
            h.assertTrue(n == 0, "ванільна корова з'явилась");
            h.succeed();
        });
    }

    // 2. Корови для нового села: 1–6
    @GameTest(template = "arena", batch = "rare", timeoutTicks = 40)
    public static void villageGetsCows(GameTestHelper h) {
        TestWorld.calm(h);
        BlockPos center = h.absolutePos(new BlockPos(7, 2, 7));
        int n = VillageCows.spawnCows(h.getLevel(), center);
        AABB box = new AABB(center).inflate(10, 100, 10);   // висота — уся (у тестовому світі карта висот дивна)
        List<HungryCow> cows = h.getLevel().getEntitiesOfClass(HungryCow.class, box);
        LOG.info("[тест] villageGetsCows: поставлено {}, у селі корів {}", n, cows.size());
        cows.forEach(HungryCow::discard);
        h.assertTrue(n >= VillageCows.MIN_COWS && n <= VillageCows.MAX_COWS, "корів не 1–6: " + n);
        h.assertTrue(cows.size() == n, "поставлено " + n + ", а знайдено " + cows.size());
        h.succeed();
    }

    // 3. Теля від торговця: квиток у інвентарі — теля біля гравця, гравець — його «мама»
    @GameTest(template = "platform", batch = "rare", timeoutTicks = 40)
    public static void traderCalf(GameTestHelper h) {
        TestWorld.calm(h);
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "buyer"));
        var at = h.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 2, 4.5));
        p.moveTo(at.x, at.y, at.z, 0, 0);
        ItemStack ticket = new ItemStack(ModItems.CALF_TICKET.get());
        ticket.getItem().inventoryTick(ticket, h.getLevel(), p, 0, false);
        List<HungryCow> calves = h.getLevel().getEntitiesOfClass(HungryCow.class, p.getBoundingBox().inflate(4), HungryCow::isBaby);
        LOG.info("[тест] traderCalf: квиток лишився {}, телят біля гравця {}", ticket.getCount(), calves.size());
        boolean ok = ticket.isEmpty() && calves.size() == 1 && calves.get(0).family.hasPlayerMother();
        calves.forEach(HungryCow::discard);
        h.assertTrue(ok, "теля не з'явилось або не вважає гравця мамою");
        h.succeed();
    }
}
