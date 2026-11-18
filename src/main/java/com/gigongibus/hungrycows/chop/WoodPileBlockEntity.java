package com.gigongibus.hungrycows.chop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/** Купа шматків колоди (Fij): що лежить і де. Новий шматок кладеться в найнижче вільне місце (стоячи, а не влазить — лежачи). */
public class WoodPileBlockEntity extends BlockEntity {
    private final List<WoodPiece> pieces = new ArrayList<>();
    private VoxelShape shape;
    /** Скільки ниток на купі (Fij, 18.11): 2 — перев'язана, ламається однією в'язанкою */
    private int strings;
    public static final int STRINGS_TO_TIE = 2;          // потрібно ниток, щоб перев'язати

    public WoodPileBlockEntity(BlockPos pos, BlockState state) {
        super(Chopping.WOOD_PILE_BE.get(), pos, state);
    }

    public List<WoodPiece> pieces() {
        return pieces;
    }

    public int strings() {
        return strings;
    }

    public boolean tied() {
        return strings >= STRINGS_TO_TIE;
    }

    public void setStrings(int n) {
        strings = Math.max(0, Math.min(STRINGS_TO_TIE, n));
        changed();
    }

    public void setPieces(List<WoodPiece> list) {
        pieces.clear();
        pieces.addAll(list);
        changed();
    }

    /** Шматки, під якими порожньо, осідають (забрали нижній, кривий удар лишив щілину) */
    public void settle() {
        boolean moved = true;
        while (moved) {
            moved = false;
            for (WoodPiece p : pieces) {
                if (p.y <= 0) continue;
                boolean free = true;
                for (WoodPiece o : pieces) {
                    if (o == p) continue;
                    boolean overlapX = p.x < o.x + o.sx && o.x < p.x + p.sx, overlapZ = p.z < o.z + o.sz && o.z < p.z + p.sz;
                    if (overlapX && overlapZ && o.y + o.sy == p.y) { free = false; break; }
                }
                if (free) { p.y--; moved = true; }
            }
        }
    }

    public void changed() {
        settle();
        shape = null;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Без місця клацання (тести): ближче до кута, стоячи / лежачи вздовж x */
    public boolean add(WoodPiece fromItem, boolean lying) {
        return add(fromItem, lying, 0, 0, Direction.Axis.X);
    }

    /**
     * Покласти шматок (Fij, 18.11: «дивно складається»): туди, куди клацнув (tx, tz — пікселі в блоці), рівно по сітці (кратно 4,
     * не влазить — по пікселю), найнижче, де є опора. Стоячи — волокна вгору (можна повернути на 90°, щоб влізло); присівши —
     * лежачи, волокна вздовж погляду (along). Не влазить так — false (не кладе як завгодно).
     */
    public boolean add(WoodPiece fromItem, boolean lying, double tx, double tz, Direction.Axis along) {
        if (strings > 0) return false;                    // перев'язану (чи з ниткою) не докладеш
        WoodPiece up = fromItem.grain == Direction.Axis.Y ? fromItem
                : fromItem.reoriented(fromItem.grain == Direction.Axis.X ? new int[]{ 1, 0, 2 } : new int[]{ 0, 2, 1 });
        List<WoodPiece> orients = new ArrayList<>();
        if (!lying) {
            orients.add(up);
            orients.add(up.reoriented(new int[]{ 2, 1, 0 }));
        } else {
            // волокна (вісь y у «стоячому») лягають уздовж погляду
            WoodPiece l1 = along == Direction.Axis.Z ? up.reoriented(new int[]{ 0, 2, 1 }) : up.reoriented(new int[]{ 1, 0, 2 });
            orients.add(l1);
            orients.add(along == Direction.Axis.Z ? up.reoriented(new int[]{ 2, 0, 1 }) : up.reoriented(new int[]{ 1, 2, 0 }));
        }
        boolean[] occ = occupancy();
        WoodPiece best = null;
        double bestScore = Double.MAX_VALUE;
        for (int pass = 0; pass < 2; pass++) {
            for (WoodPiece p : orients) {
                int n = WoodPiece.FULL;
                // сітка — розмір самого шматка (половинки — 0 або 8, четвертинки — 0/4/8/12), не ділить 16 — по 4; удруге — по пікселю
                int stepX = pass == 1 ? 1 : n % p.sx == 0 ? p.sx : 4, stepZ = pass == 1 ? 1 : n % p.sz == 0 ? p.sz : 4;
                for (int y = 0; y + p.sy <= n; y++)
                    for (int z = 0; z + p.sz <= n; z += stepZ)
                        for (int x = 0; x + p.sx <= n; x += stepX) {
                            if (!free(occ, x, y, z, p.sx, p.sy, p.sz)) continue;
                            if (y > 0 && !supported(occ, x, y, z, p.sx, p.sz)) continue;
                            double dx = x + p.sx / 2.0 - tx, dz = z + p.sz / 2.0 - tz;
                            double score = y * 1000 + dx * dx + dz * dz;      // спершу нижче, потім ближче до клацання
                            if (score < bestScore) {
                                bestScore = score;
                                best = p.copy();
                                best.x = x; best.y = y; best.z = z;
                            }
                        }
            }
            if (best != null) break;
        }
        if (best == null) return false;
        best.notch = 0; best.notchAxis = best.notchAt = -1;
        pieces.add(best);
        changed();
        return true;
    }

    private boolean[] occupancy() {
        int n = WoodPiece.FULL;
        boolean[] occ = new boolean[n * n * n];
        for (WoodPiece p : pieces)
            for (int y = p.y; y < p.y + p.sy; y++)
                for (int z = p.z; z < p.z + p.sz; z++)
                    for (int x = p.x; x < p.x + p.sx; x++)
                        if (x >= 0 && y >= 0 && z >= 0 && x < n && y < n && z < n) occ[(y * n + z) * n + x] = true;
        return occ;
    }

    private static boolean free(boolean[] occ, int x0, int y0, int z0, int sx, int sy, int sz) {
        int n = WoodPiece.FULL;
        for (int y = y0; y < y0 + sy; y++)
            for (int z = z0; z < z0 + sz; z++)
                for (int x = x0; x < x0 + sx; x++) if (occ[(y * n + z) * n + x]) return false;
        return true;
    }

    /** Хоч половина низу на чомусь лежить */
    private static boolean supported(boolean[] occ, int x0, int y, int z0, int sx, int sz) {
        int n = WoodPiece.FULL, under = 0;
        for (int z = z0; z < z0 + sz; z++) for (int x = x0; x < x0 + sx; x++) if (occ[((y - 1) * n + z) * n + x]) under++;
        return under * 2 >= sx * sz;
    }

    /** Форма для ЛКМ/ПКМ і ходіння: усі шматки разом */
    public VoxelShape shape() {
        if (shape != null) return shape;
        VoxelShape s = Shapes.empty();
        for (WoodPiece p : pieces)
            s = Shapes.joinUnoptimized(s, Block.box(p.x, p.y, p.z, p.x + p.sx, p.y + p.sy, p.z + p.sz), BooleanOp.OR);
        return shape = s.isEmpty() ? Block.box(6, 0, 6, 10, 1, 10) : s.optimize();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag l = new ListTag();
        for (WoodPiece p : pieces) l.add(p.save());
        tag.put("Pieces", l);
        if (strings > 0) tag.putInt("Strings", strings);
    }

    /** Шматки списком для в'язанки */
    public ListTag piecesTag() {
        ListTag l = new ListTag();
        for (WoodPiece p : pieces) l.add(p.save());
        return l;
    }

    public static List<WoodPiece> piecesFrom(ListTag l) {
        List<WoodPiece> out = new ArrayList<>();
        for (Tag t : l) out.add(WoodPiece.load((CompoundTag) t));
        return out;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        pieces.clear();
        for (Tag t : tag.getList("Pieces", Tag.TAG_COMPOUND)) pieces.add(WoodPiece.load((CompoundTag) t));
        strings = tag.getInt("Strings");
        shape = null;
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
