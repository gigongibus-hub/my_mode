package com.gigongibus.hungrycows.phys;

import com.gigongibus.hungrycows.chop.WoodPiece;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

/**
 * Частина фізичного тіла (Fij, 20.11: «реальна фізика дерева і шматків»): брусок у системі тіла — або цілий блок (колода, листок),
 * або шматок колоди з розрубки (`WoodPiece`). Брусок у системі тіла завжди рівний (вздовж осей), тіло ж повертається як завгодно.
 */
public final class Part {
    public static final byte BLOCK = 0, PIECE = 1;

    public final byte kind;
    public final BlockState state;        // BLOCK: який блок (для малювання й коли стане блоком)
    public final WoodPiece piece;         // PIECE: шматок
    public final Vector3d c;              // центр у системі тіла
    public final double hx, hy, hz;       // пів розмірів, бл.
    public final double mass;
    public final boolean leaf;            // листок (і ліана): м'який, ламається
    public boolean gone;                  // відламався / розчавлено
    /** Скільки бруска у воді (0…1) і куди тече вода там (бл./с) — рахує `PhysicsWorld` раз на тік, не зберігається */
    public double wet, flowX, flowZ;
    /** Точки дотику (у системі тіла), що не всередині сусідніх частин; null — ще не пораховано */
    double[] samples;

    private Part(byte kind, BlockState state, WoodPiece piece, Vector3d c, double hx, double hy, double hz, double mass, boolean leaf) {
        this.kind = kind;
        this.state = state;
        this.piece = piece;
        this.c = c;
        this.hx = hx;
        this.hy = hy;
        this.hz = hz;
        this.mass = mass;
        this.leaf = leaf;
    }

    /** Цілий блок з нижнім кутом у local (система тіла) */
    public static Part block(BlockState state, Vector3d corner, double mass, boolean leaf) {
        return new Part(BLOCK, state, null, new Vector3d(corner).add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5, mass, leaf);
    }

    /** Шматок: брусок piece.x…x+sx (пікс.) у блоці з нижнім кутом corner; маса — частка колоди */
    public static Part piece(WoodPiece p, Vector3d corner, double logMass) {
        Vector3d c = new Vector3d(corner).add((p.x + p.sx / 2.0) / 16, (p.y + p.sy / 2.0) / 16, (p.z + p.sz / 2.0) / 16);
        return new Part(PIECE, null, p.copy(), c, p.sx / 32.0, p.sy / 32.0, p.sz / 32.0, logMass * p.fraction(), false);
    }

    public double h(int axis) {
        return axis == 0 ? hx : axis == 1 ? hy : hz;
    }

    public double c(int axis) {
        return axis == 0 ? c.x : axis == 1 ? c.y : c.z;
    }

    /** Точка local (система тіла) усередині бруска, з запасом eps */
    public Part copy() {
        Part p = new Part(kind, state, piece == null ? null : piece.copy(), new Vector3d(c), hx, hy, hz, mass, leaf);
        p.gone = gone;
        return p;
    }

    /** Те саме, але з новою масою (гравцю маса не передається) */
    Part withMass(double m) {
        return new Part(kind, state, piece, c, hx, hy, hz, m, leaf);
    }

    public boolean contains(double x, double y, double z, double eps) {
        return Math.abs(x - c.x) < hx + eps && Math.abs(y - c.y) < hy + eps && Math.abs(z - c.z) < hz + eps;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putByte("K", kind);
        if (state != null) t.put("S", NbtUtils.writeBlockState(state));
        if (piece != null) t.put("P", piece.save());
        t.putDouble("X", c.x);
        t.putDouble("Y", c.y);
        t.putDouble("Z", c.z);
        t.putDouble("HX", hx);
        t.putDouble("HY", hy);
        t.putDouble("HZ", hz);
        t.putDouble("M", mass);
        if (leaf) t.putBoolean("L", true);
        if (gone) t.putBoolean("G", true);
        return t;
    }

    public static Part load(CompoundTag t, Level level) {
        byte k = t.getByte("K");
        BlockState s = t.contains("S") ? NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), t.getCompound("S")) : null;
        WoodPiece p = t.contains("P") ? WoodPiece.load(t.getCompound("P")) : null;
        Part part = new Part(k, s, p, new Vector3d(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z")),
                t.getDouble("HX"), t.getDouble("HY"), t.getDouble("HZ"), t.getDouble("M"), t.getBoolean("L"));
        part.gone = t.getBoolean("G");
        return part;
    }

    /** Для передачі гравцю (без «gone» — його передає окремий список) */
    public void write(net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeByte(kind);
        buf.writeVarInt(state == null ? 0 : Block.getId(state));
        if (kind == PIECE) buf.writeNbt(piece.save());
        buf.writeFloat((float) c.x);
        buf.writeFloat((float) c.y);
        buf.writeFloat((float) c.z);
        buf.writeFloat((float) hx);
        buf.writeFloat((float) hy);
        buf.writeFloat((float) hz);
        buf.writeBoolean(leaf);
    }

    public static Part read(net.minecraft.network.FriendlyByteBuf buf) {
        byte k = buf.readByte();
        int sid = buf.readVarInt();
        WoodPiece p = k == PIECE ? WoodPiece.load(buf.readNbt()) : null;
        Vector3d c = new Vector3d(buf.readFloat(), buf.readFloat(), buf.readFloat());
        double hx = buf.readFloat(), hy = buf.readFloat(), hz = buf.readFloat();
        boolean leaf = buf.readBoolean();
        return new Part(k, k == BLOCK ? Block.stateById(sid) : null, p, c, hx, hy, hz, 0, leaf);
    }
}
