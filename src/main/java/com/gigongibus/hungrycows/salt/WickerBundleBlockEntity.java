package com.gigongibus.hungrycows.salt;

import com.gigongibus.hungrycows.CowHome;
import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.WorldClock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Пучки лози на одному блоці (Fij: до 6, розкидані випадково, кожен сохне ОКРЕМО). Кожен пучок пам'ятає своє місце й поворот
 * (вибрані випадково, коли поклали), стадію, скільки пролежав на сонці й скільки мокрим. Стан блока `stage` = стадія першого пучка.
 */
public class WickerBundleBlockEntity extends BlockEntity {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int CHECK_TICKS = 20;          // перевіряє раз на секунду
    public static final int SUN_DAY = 12000;           // «день на сонці» = 12000 тіків світла (уночі не сохне)
    public static final int WILT_DAYS = 1;             // Fij: 1-й день свіжий, 2-й підсохлий, 3-й сухий ...
    public static final int DRY_DAYS = 2;              // ... тобто сухий після 2 сонячних днів (на третій уже сухий)
    public static final int ROT_TICKS = 24000;         // мокрий ще доба (дощ) — гниє
    public static final int MAX_PIECES = 6;            // Fij: до 6 пучків на блок
    public static final float EDGE = 0.25F;            // центр пучка — не ближче 0,25 бл. до краю (пучок 0,5 бл.)
    public static final float SPREAD = 0.22F;          // намагається класти не ближче 0,22 бл. до іншого (розкидані)
    public static final int PLACE_TRIES = 12;          // скільки випадкових місць пробує, щоб не накласти

    /** Один пучок: стадія, сонце, мокрий, де лежить (0..1) і поворот (градуси) */
    public static class Piece {
        public WickerBundleBlock.Stage stage;
        public int sun, wet;
        public float x, z, rot;

        Piece(WickerBundleBlock.Stage stage, float x, float z, float rot) {
            this.stage = stage;
            this.x = x;
            this.z = z;
            this.rot = rot;
        }
    }

    private final List<Piece> pieces = new ArrayList<>();

    public WickerBundleBlockEntity(BlockPos pos, BlockState state) {
        super(Salt.WICKER_BUNDLE_BE.get(), pos, state);
    }

    public List<Piece> pieces() {
        ensure();
        return pieces;
    }

    public int count() {
        return pieces().size();
    }

    /** Сонце першого пучка (для тестів) */
    public int sun() {
        ensure();
        return pieces.isEmpty() ? 0 : pieces.get(0).sun;
    }

    /** Порожній список (щойно поставлений блок, старе збереження) — один пучок зі стадії блока */
    private boolean ensure() {
        if (pieces.isEmpty() && level != null && !level.isClientSide) {
            pieces.add(newPiece(getBlockState().getValue(WickerBundleBlock.STAGE), level.getRandom()));
            return true;
        }
        return false;
    }

    private Piece newPiece(WickerBundleBlock.Stage stage, RandomSource r) {
        float bx = 0.5F, bz = 0.5F, best = -1;
        for (int i = 0; i < PLACE_TRIES; i++) {
            float x = EDGE + r.nextFloat() * (1 - 2 * EDGE), z = EDGE + r.nextFloat() * (1 - 2 * EDGE);
            float near = Float.MAX_VALUE;
            for (Piece p : pieces) near = Math.min(near, (p.x - x) * (p.x - x) + (p.z - z) * (p.z - z));
            if (near > best) { best = near; bx = x; bz = z; }
            if (near >= SPREAD * SPREAD) break;
        }
        return new Piece(stage, bx, bz, r.nextFloat() * 360F);
    }

    /** Покласти ще один пучок (ПКМ пучком по блоку). false — уже 6 */
    public boolean add(WickerBundleBlock.Stage stage) {
        ensure();
        if (level == null || pieces.size() >= MAX_PIECES) return false;
        pieces.add(newPiece(stage, level.getRandom()));
        changed();
        return true;
    }

    /** Предмети для ламання: кожен пучок своєю стадією */
    public List<ItemStack> drops() {
        List<ItemStack> out = new ArrayList<>();
        for (Piece p : pieces()) out.add(new ItemStack(Salt.bundleItem(p.stage)));
        return out;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState s = getBlockState();
            WickerBundleBlock.Stage first = pieces.get(0).stage;
            if (s.getValue(WickerBundleBlock.STAGE) != first) level.setBlock(worldPosition, s.setValue(WickerBundleBlock.STAGE, first), 3);
            else level.sendBlockUpdated(worldPosition, s, s, 3);
        }
    }

    /** Чи падає дощ на пучок (за блоками над ним, як для корів) */
    public static boolean rainedOn(Level level, BlockPos pos) {
        return level.isRaining() && HungryCow.rainReaches(level, pos);
    }

    /** Сонце: день, не дощ, без даху */
    public static boolean sunny(Level level, BlockPos pos) {
        long t = level.getDayTime() % 24000;
        return t < 12000 && !level.isRaining() && !CowHome.hasRoof(level, pos);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WickerBundleBlockEntity be) {
        if (level.getGameTime() % CHECK_TICKS != 0) return;
        boolean created = be.ensure();
        int amount = CHECK_TICKS * WorldClock.speed((ServerLevel) level);
        boolean rain = rainedOn(level, pos), sun = sunny(level, pos), any = false;
        for (Piece p : be.pieces) {
            WickerBundleBlock.Stage next = grow(p, amount, rain, sun);
            if (next != p.stage) { p.stage = next; any = true; }
        }
        if (any || created) be.changed();
        else be.setChanged();
    }

    /** Один пучок за одну перевірку (як було для цілого блока) */
    private static WickerBundleBlock.Stage grow(Piece p, int amount, boolean rain, boolean sun) {
        WickerBundleBlock.Stage stage = p.stage;
        if (stage == WickerBundleBlock.Stage.ROTTEN) return stage;
        if (rain) {
            p.wet += amount;
            return p.wet >= ROT_TICKS ? WickerBundleBlock.Stage.ROTTEN : WickerBundleBlock.Stage.WET;
        }
        if (stage == WickerBundleBlock.Stage.WET) {
            // дощ скінчився раніше, ніж згнив — сохне з початку (Fij)
            p.sun = 0;
            p.wet = 0;
            return WickerBundleBlock.Stage.FRESH;
        }
        if (!sun) return stage;
        // покладений підсохлим чи сухим — уже «відлежав» своє (інакше сухий на сонці ставав свіжим: лічильник був 0)
        int had = stage == WickerBundleBlock.Stage.DRY ? DRY_DAYS * SUN_DAY : stage == WickerBundleBlock.Stage.WILTED ? WILT_DAYS * SUN_DAY : 0;
        p.sun = Math.max(p.sun, had) + amount;
        return p.sun >= DRY_DAYS * SUN_DAY ? WickerBundleBlock.Stage.DRY
                : p.sun >= WILT_DAYS * SUN_DAY ? WickerBundleBlock.Stage.WILTED : WickerBundleBlock.Stage.FRESH;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ensure();
        ListTag list = new ListTag();
        for (Piece p : pieces) {
            CompoundTag t = new CompoundTag();
            t.putString("Stage", p.stage.getSerializedName());
            t.putInt("Sun", p.sun);
            t.putInt("Wet", p.wet);
            t.putFloat("X", p.x);
            t.putFloat("Z", p.z);
            t.putFloat("Rot", p.rot);
            list.add(t);
        }
        tag.put("Pieces", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        pieces.clear();
        if (tag.contains("Pieces", Tag.TAG_LIST)) {
            for (Tag e : tag.getList("Pieces", Tag.TAG_COMPOUND)) {
                CompoundTag t = (CompoundTag) e;
                WickerBundleBlock.Stage st = WickerBundleBlock.Stage.FRESH;
                for (WickerBundleBlock.Stage s : WickerBundleBlock.Stage.values()) if (s.getSerializedName().equals(t.getString("Stage"))) st = s;
                Piece p = new Piece(st, t.getFloat("X"), t.getFloat("Z"), t.getFloat("Rot"));
                p.sun = t.getInt("Sun");
                p.wet = t.getInt("Wet");
                pieces.add(p);
            }
        } else if (tag.contains("Sun")) {
            // старе збереження: один пучок посередині зі стадією блока
            Piece p = new Piece(getBlockState().getValue(WickerBundleBlock.STAGE), 0.5F, 0.5F, 0);
            p.sun = tag.getInt("Sun");
            p.wet = tag.getInt("Wet");
            pieces.add(p);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
