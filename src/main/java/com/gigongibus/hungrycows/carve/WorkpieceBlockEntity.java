package com.gigongibus.hungrycows.carve;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Заготовка на верстаку: креслення (Fij: кладеться першим), колода (колір) і що від неї лишилось (маска; зберігається, можна
 * продовжити). Старі збереження без креслення — колода є, креслення немає (вийдуть лише палиці).
 */
public class WorkpieceBlockEntity extends BlockEntity {
    private Item blueprint = null;
    private Item log = null;
    private CarveMask mask = new CarveMask();
    // справжня різьба (Fij, 17.11): об'ємна колода; null — стара (вікно) або ще не почата
    private VoxelMask voxels = null;
    private VoxelMask keep3d = null;                 // шаблон креслення — у грі гравця (деталь стає чорною, коли до неї дійшов)
    private int sink = 0;                            // на скільки шарів осіла колода (шаблон «опускається» разом із нею)
    private net.minecraft.world.phys.shapes.VoxelShape shapeCache = null;

    public WorkpieceBlockEntity(BlockPos pos, BlockState state) {
        super(Carving.WORKPIECE_BE.get(), pos, state);
    }

    public Item log() {
        return log == null ? Items.STRIPPED_OAK_LOG : log;
    }

    public boolean hasLog() {
        return log != null;
    }

    public Item blueprint() {
        return blueprint;
    }

    public void setBlueprint(Item item) {
        blueprint = item;
        changed();
    }

    public void setLog(Item item) {
        log = item;
        mask = new CarveMask();
        voxels = null;
        sink = 0;
        changed();
    }

    /** Зняли колоду (готова деталь або палиці): креслення лишається на верстаку */
    public void clearLog() {
        log = null;
        mask = new CarveMask();
        voxels = null;
        sink = 0;
        changed();
    }

    public CarveMask mask() {
        return mask;
    }

    /** Вирізане від гравця: лише вибивати (що вже вибито — не повертається) */
    public void applyCarve(CarveMask fromPlayer) {
        mask.keepOnly(fromPlayer);
        changed();
    }

    public VoxelMask voxels() {
        return voxels;
    }

    /** Почати справжню різьбу: ціла колода 12 × 12 × 12 */
    public void startVoxels() {
        voxels = new VoxelMask();
        sink = 0;
        changed();
    }

    /** Для тестів: поставити готову об'ємну форму */
    public void setVoxels(VoxelMask m) {
        voxels = m;
        sink = 0;
        changed();
    }

    public int sink() {
        return sink;
    }

    public void addSink(int d) {
        sink += d;
    }

    public void voxelsChanged() {
        changed();
    }

    public VoxelMask keep3d() {
        return keep3d;
    }

    /** Форма для ЛКМ і обвідки: що лишилось від колоди + креслення (рядки клітинок уздовж x злиті) */
    public net.minecraft.world.phys.shapes.VoxelShape voxelShape() {
        if (shapeCache != null) return shapeCache;
        var shape = net.minecraft.world.level.block.Block.box(1, 0, 1, 15, 1, 15);
        int n = VoxelMask.N, ox = VoxelCarve.OFFSET_XZ, oy = VoxelCarve.OFFSET_Y;
        if (voxels != null)
            for (int y = 0; y < n; y++)
                for (int z = 0; z < n; z++)
                    for (int x = 0; x < n; ) {
                        if (!voxels.get(x, y, z)) { x++; continue; }
                        int x0 = x;
                        while (x < n && voxels.get(x, y, z)) x++;
                        shape = net.minecraft.world.phys.shapes.Shapes.joinUnoptimized(shape, net.minecraft.world.level.block.Block.box(
                                ox + x0, oy + y, ox + z, ox + x, oy + y + 1, ox + z + 1), net.minecraft.world.phys.shapes.BooleanOp.OR);
                    }
        return shapeCache = shape.optimize();
    }

    private void changed() {
        shapeCache = null;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private static String id(Item i) {
        ResourceLocation r = i == null ? null : ForgeRegistries.ITEMS.getKey(i);
        return r == null ? "" : r.toString();
    }

    private static Item item(String s) {
        if (s == null || s.isEmpty()) return null;
        Item i = ForgeRegistries.ITEMS.getValue(new ResourceLocation(s));
        return i == null || i == Items.AIR ? null : i;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Log", id(log));
        tag.putString("Blueprint", id(blueprint));
        tag.putByteArray("Mask", mask.toBytes());
        if (voxels != null) tag.putByteArray("Voxels", voxels.toBytes());
        if (sink != 0) tag.putInt("Sink", sink);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        log = item(tag.getString("Log"));
        blueprint = item(tag.getString("Blueprint"));
        mask = tag.contains("Mask") ? CarveMask.fromBytes(tag.getByteArray("Mask")) : new CarveMask();
        voxels = tag.contains("Voxels") ? VoxelMask.fromBytes(tag.getByteArray("Voxels")) : null;
        sink = tag.getInt("Sink");
        keep3d = tag.contains("Keep3d") ? VoxelMask.fromBytes(tag.getByteArray("Keep3d")) : null;
        shapeCache = null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = saveWithoutMetadata();
        // гравцю — і шаблон креслення (олівцеві лінії на колоді); у збереження не пишеться
        if (voxels != null && blueprint instanceof BlueprintItem bp && level != null && level.getServer() != null)
            t.putByteArray("Keep3d", CarveTemplates.keep3d(level.getServer(), bp.template).toBytes());
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
