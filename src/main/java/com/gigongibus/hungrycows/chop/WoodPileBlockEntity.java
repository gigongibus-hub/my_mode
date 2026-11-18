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

    public WoodPileBlockEntity(BlockPos pos, BlockState state) {
        super(Chopping.WOOD_PILE_BE.get(), pos, state);
    }

    public List<WoodPiece> pieces() {
        return pieces;
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

    /** Покласти шматок (як лежав у предметі — волокна вгору); lying — покласти лежачи. false — не влазить. */
    public boolean add(WoodPiece fromItem, boolean lying) {
        WoodPiece best = null;
        int bestY = Integer.MAX_VALUE;
        // перестановки осей: стоячи (волокна вгору) двома способами, лежачи — вздовж x і z
        int g = fromItem.grain.ordinal();
        int[][] perms = { { 0, 1, 2 }, { 2, 1, 0 }, { 1, 0, 2 }, { 0, 2, 1 }, { 1, 2, 0 }, { 2, 0, 1 } };
        boolean[] occ = occupancy();
        // спершу як просили (стоячи / лежачи), не влазить — як завгодно
        for (int pass = 0; pass < 2 && best == null; pass++)
            for (int[] perm : perms) {
                WoodPiece p = fromItem.reoriented(perm);
                boolean standing = p.grain == Direction.Axis.Y;
                if (pass == 0 && standing == lying) continue;
                int[] at = lowestFit(occ, p.sx, p.sy, p.sz);
                if (at != null && at[1] < bestY) {
                    bestY = at[1];
                    best = p;
                    best.x = at[0]; best.y = at[1]; best.z = at[2];
                }
            }
        if (best == null) return false;
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

    /** Найнижче вільне місце для бруска; складається з кутка рядами (щоб 16 скіпок лягли щільно) */
    private static int[] lowestFit(boolean[] occ, int sx, int sy, int sz) {
        int n = WoodPiece.FULL;
        for (int y = 0; y + sy <= n; y++)
            for (int z = 0; z + sz <= n; z++)
                for (int x = 0; x + sx <= n; x++) {
                    if (!free(occ, x, y, z, sx, sy, sz)) continue;
                    if (y > 0 && !supported(occ, x, y, z, sx, sz)) continue;
                    return new int[]{ x, y, z };
                }
        return null;
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
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        pieces.clear();
        for (Tag t : tag.getList("Pieces", Tag.TAG_COMPOUND)) pieces.add(WoodPiece.load((CompoundTag) t));
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
