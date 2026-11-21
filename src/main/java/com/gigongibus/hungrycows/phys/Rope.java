package com.gigongibus.hungrycows.phys;

import com.gigongibus.hungrycows.HungryCowsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * КІНЬ ТЯГНЕ КОЛОДУ (Fij, 21.11: «за допомогою повідця фізичне дерево можна прив'язати до коня»; варіант «тягне по землі»).
 * Прив'язати: сидиш на коні чи ведеш коня на повідці — ПКМ повідцем по фізичній колоді (там, куди клацнув, — вузол). Або ПКМ
 * повідцем по колоді, потім ПКМ ним же по коню. Відв'язати — ПКМ повідцем по прив'язаній колоді ще раз (повідець повертається).
 * Мотузка — як справжня: поки не натягнута, нічого не робить; натягнута — тягне колоду за вузол (колода волочиться з тертям,
 * повертається кінцем до коня). Важка колода сповільнює коня (ціле дерево — дуже). Кінь зник, помер чи далеко — мотузка
 * рветься, повідець падає. Прив'язана колода не стає блоками.
 */
@Mod.EventBusSubscriber(modid = HungryCowsMod.MODID)
public final class Rope {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double LENGTH = 3.0;           // довжина мотузки, бл.
    public static final double BREAK_AT = 12;          // кінь далі 12 бл. від вузла (застрягло, телепорт) — мотузка рветься
    // Fij (21.11: «колода надто сильно відскакує вперед і б'є коня до смерті»): було «вирівняти за підкрок на 30%, до 6 бл./с»
    // — колода летіла й обганяла коня. Тепер вузол лише йде за конем з його швидкістю (уздовж мотузки), а розтяг вирівнює м'яко
    public static final double PULL_BIAS = 2.0;        // розтяг на 1 бл. — додатково 2 бл./с до коня …
    public static final double MAX_PULL_SPEED = 1.5;   // … але не більше 1,5 бл./с понад швидкість коня
    public static final double HORSE_PULL = 6;         // «сила» коня в колодах: сповільнення = маса / (маса + 6) …
    public static final double MAX_SLOW = 0.85;        // … не більше 85% (ціле дерево — кінь ледь іде)
    public static final double REACH = 5;              // клацнути по колоді можна з 5 бл.
    public static final double LED_RANGE = 10;         // кінь, якого ведеш на повідці, — до 10 бл. від тебе
    public static final int PENDING_TICKS = 20 * 30;   // «тримаєш кінець мотузки» 30 с, потім забуто
    private static final UUID SLOW_ID = UUID.fromString("6f6a1e3c-2b9b-4a39-9c8e-1d2f5a7b3c11");

    private Rope() {}

    /** Гравець прив'язав мотузку до колоди й іде до коня: тіло, вузол (система тіла), коли */
    private record Pending(int body, double x, double y, double z, long time) {}
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    // ---------- ПКМ повідцем ----------

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem e) {
        if (handle(e.getEntity(), e.getHand(), e.getItemStack())) { e.setCanceled(true); e.setCancellationResult(InteractionResult.SUCCESS); }
    }

    @SubscribeEvent
    public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock e) {
        if (handle(e.getEntity(), e.getHand(), e.getItemStack())) { e.setCanceled(true); e.setCancellationResult(InteractionResult.SUCCESS); }
    }

    /** ПКМ повідцем по коню, тримаючи кінець мотузки від колоди */
    @SubscribeEvent
    public static void onUseOnEntity(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getTarget() instanceof AbstractHorse horse) || !e.getItemStack().is(Items.LEAD) || !(e.getLevel() instanceof ServerLevel level)) return;
        Pending p = PENDING.remove(e.getEntity().getUUID());
        if (p == null || level.getGameTime() - p.time > PENDING_TICKS) return;
        if (!(level.getEntity(p.body) instanceof WoodBodyEntity body) || body.isRemoved()) return;
        tie(body, horse, new Vector3d(p.x, p.y, p.z), e.getEntity(), e.getItemStack());
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Повідцем по фізичній колоді (промінь погляду, ближче за блок): прив'язати / відв'язати / взяти кінець мотузки */
    private static boolean handle(Player pl, InteractionHand hand, ItemStack stack) {
        if (!stack.is(Items.LEAD) || !(pl.level() instanceof ServerLevel level)) return false;
        Vec3 eye = pl.getEyePosition(), look = pl.getLookAngle();
        double limit = REACH;
        var bh = pl.pick(REACH, 1, false);
        if (bh.getType() != net.minecraft.world.phys.HitResult.Type.MISS) limit = Math.min(limit, bh.getLocation().distanceTo(eye));
        WoodBodyEntity best = null;
        int[] hit = null;
        for (WoodBodyEntity e : BodyCollision.near(level, pl.getBoundingBox().inflate(REACH))) {
            int[] h = BodyChop.pick(e.body, eye, look, limit);
            if (h == null) continue;
            limit = h[2] / 1000.0;
            best = e;
            hit = h;
        }
        if (best == null) return false;
        if (best.ropeHorse != null) { untie(best, pl); return true; }
        Vector3d at = new Vector3d(eye.x + look.x * limit, eye.y + look.y * limit, eye.z + look.z * limit);
        Vector3d local = best.body.toLocal(at, new Vector3d());
        AbstractHorse horse = pl.getVehicle() instanceof AbstractHorse v ? v : null;
        if (horse == null)
            for (AbstractHorse hr : level.getEntitiesOfClass(AbstractHorse.class, pl.getBoundingBox().inflate(LED_RANGE), x -> x.getLeashHolder() == pl)) { horse = hr; break; }
        if (horse != null) tie(best, horse, local, pl, stack);
        else {
            PENDING.put(pl.getUUID(), new Pending(best.getId(), local.x, local.y, local.z, level.getGameTime()));
            level.playSound(null, BlockPos.containing(at.x, at.y, at.z), SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 0.7F, 1.2F);
        }
        pl.swing(hand, true);
        return true;
    }

    public static void tie(WoodBodyEntity body, AbstractHorse horse, Vector3d local, Player pl, ItemStack stack) {
        body.ropeHorse = horse.getUUID();
        body.ropeLocal.set(local);
        body.ropeHorseId = horse.getId();
        body.syncRope();
        body.body.asleep = false;
        if (!pl.getAbilities().instabuild) stack.shrink(1);
        body.level().playSound(null, horse.blockPosition(), SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 1F, 1F);
    }

    /** Відв'язати: повідець — гравцю (або на землю), кінь знову швидкий */
    public static void untie(WoodBodyEntity body, Player pl) {
        if (body.level() instanceof ServerLevel level && level.getEntity(body.ropeHorse) instanceof AbstractHorse h) slow(h, 0);
        body.ropeHorse = null;
        body.ropeHorseId = -1;
        body.syncRope();
        ItemStack lead = new ItemStack(Items.LEAD);
        if (pl == null || !pl.getInventory().add(lead)) body.spawnAtLocation(lead);
        body.level().playSound(null, body.blockPosition(), SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 1F, 1F);
    }

    // ---------- фізика ----------

    /** Раз на тік (з `PhysicsWorld.tick`): де кінь, чи не порвалась; повертає точку прив'язі на коні або null */
    static Vector3d anchor(ServerLevel level, WoodBodyEntity body) {
        if (body.ropeHorse == null) return null;
        Entity h = level.getEntity(body.ropeHorse);
        Vector3d knot = body.body.toWorld(body.ropeLocal.x, body.ropeLocal.y, body.ropeLocal.z, new Vector3d());
        if (!(h instanceof AbstractHorse horse) || !h.isAlive() || h.position().distanceTo(new Vec3(knot.x, knot.y, knot.z)) > BREAK_AT) {
            untie(body, null);
            return null;
        }
        if (body.ropeHorseId != h.getId()) { body.ropeHorseId = h.getId(); body.syncRope(); }
        slow(horse, Math.min(MAX_SLOW, body.body.mass / (body.body.mass + HORSE_PULL)));
        return hitch(horse, new Vector3d());
    }

    /** Швидкість коня (бл./с): верхи її рахує гра гравця, тож — за зсувом за тік */
    static Vector3d horseVelocity(ServerLevel level, WoodBodyEntity body) {
        Entity h = body.ropeHorse == null ? null : level.getEntity(body.ropeHorse);
        if (h == null) return new Vector3d();
        return new Vector3d(h.getX() - h.xo, h.getY() - h.yo, h.getZ() - h.zo).mul(20);
    }

    /** Де мотузка тримається за коня: позаду сідла, на висоті ~0,7 зросту */
    public static Vector3d hitch(Entity horse, Vector3d out) {
        double yaw = Math.toRadians(horse.getYRot());
        return out.set(horse.getX() + Math.sin(yaw) * horse.getBbWidth() * 0.6, horse.getY() + horse.getBbHeight() * 0.7,
                horse.getZ() - Math.cos(yaw) * horse.getBbWidth() * 0.6);
    }

    /** Натягнута мотузка (за підкрок): вузол не відходить від коня далі довжини — поштовх тілу в точці вузла, без ривка */
    static void pull(RigidBody b, Vector3d localKnot, Vector3d to, Vector3d horseVel, double dt) {
        Vector3d knot = b.toWorld(localKnot.x, localKnot.y, localKnot.z, new Vector3d());
        Vector3d n = new Vector3d(to).sub(knot);
        double dist = n.length();
        if (dist <= LENGTH || dist < 1e-6) return;
        n.div(dist);
        Vector3d cw = b.comWorld(new Vector3d());
        double vn = b.velAt(knot, cw, new Vector3d()).dot(n);
        double want = Math.max(0, horseVel.dot(n)) + Math.min(MAX_PULL_SPEED, (dist - LENGTH) * PULL_BIAS);
        if (vn >= want) return;                                         // мотузка лише тягне, не штовхає
        double k = b.invMassAlong(new Vector3d(knot).sub(cw), n);
        if (k <= 0) return;
        b.impulse(new Vector3d(n).mul((want - vn) / k), knot, cw);
        b.asleep = false;
        b.still = 0;
    }

    /** Кінь з колодою повільніший (і верхи — швидкість коня рахує гра гравця, тож лише через властивість швидкості) */
    static void slow(AbstractHorse h, double amount) {
        AttributeInstance a = h.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        AttributeModifier m = a.getModifier(SLOW_ID);
        if (m != null && Math.abs(m.getAmount() + amount) < 0.02) return;
        if (m != null) a.removeModifier(SLOW_ID);
        if (amount > 0) a.addTransientModifier(new AttributeModifier(SLOW_ID, "hungrycows log drag", -amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
}
