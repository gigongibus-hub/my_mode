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
    // де в колоді наступна деталь (зсув шаблону): чорнішає, коли до неї доходиш; осідає разом з колодою; null — не вміщається
    // місце наступної деталі (клітинки шаблону, повернутого й зсунутого) — чорніє, коли до неї доходиш; осідає разом з колодою
    private VoxelMask guide = null;
    private boolean guideReady = false;           // false — ще не шукали (шукає сервер при першому ударі)
    private int[] legacyGuide = null;             // старе збереження: місце деталі як зсув шаблону
    // розрубка (Fij, 18.11): шматок колоди темного дуба замість цілої колоди — заготовка його розміру; null — звичайна колода
    private CompoundTag piece = null;
    private int pieceStart = 0;                      // скільки «пікселів» було, коли поклали (менше — уже почали різати)
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
        guide = null;
        guideReady = false;
        piece = null;
        changed();
    }

    /** Зняли колоду (готова деталь або палиці): креслення лишається на верстаку */
    public void clearLog() {
        log = null;
        mask = new CarveMask();
        voxels = null;
        guide = null;
        guideReady = false;
        piece = null;
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
    /** Шматок колоди на кресленні: брусок його розміру (пікс. × 12/16), волокна вздовж x, лежить на кресленні посередині */
    public void setPiece(com.gigongibus.hungrycows.chop.WoodPiece p, VoxelMask keep) {
        log = net.minecraft.world.item.Items.STRIPPED_DARK_OAK_LOG;
        mask = new CarveMask();
        piece = p.itemTag();
        int n = VoxelMask.N, g = scale(p.size(p.grain));
        int a = 0, b = 0;
        for (net.minecraft.core.Direction.Axis ax : net.minecraft.core.Direction.Axis.values())
            if (ax != p.grain) { if (a == 0) a = scale(p.size(ax)); else b = scale(p.size(ax)); }
        int h = Math.min(a, b), w = Math.max(a, b);         // нижчою стороною донизу
        voxels = VoxelMask.empty();
        int x0 = (n - g) / 2, z0 = (n - w) / 2;
        for (int y = 0; y < h; y++) for (int z = z0; z < z0 + w; z++) for (int x = x0; x < x0 + g; x++) voxels.set(x, y, z);
        pieceStart = voxels.count();
        guide = keep == null ? null : PartMatcher.findGuide(voxels, keep);
        guideReady = true;
        changed();
    }

    private static int scale(int px) {
        return Math.max(1, Math.min(VoxelMask.N, Math.round(px * VoxelMask.N / 16F)));
    }

    /** Шматок, якого ще не торкались, — повернути як був; null — це не шматок або вже різали */
    public net.minecraft.world.item.ItemStack untouchedPiece() {
        if (piece == null || voxels == null || voxels.count() != pieceStart) return null;
        return com.gigongibus.hungrycows.chop.WoodPieceItem.of(com.gigongibus.hungrycows.chop.WoodPiece.load(piece));
    }

    public boolean isPiece() {
        return piece != null;
    }

    public void startVoxels() {
        voxels = new VoxelMask();
        guide = null;
        guideReady = false;
        changed();
    }

    /** Для тестів: поставити готову об'ємну форму */
    public void setVoxels(VoxelMask m) {
        voxels = m;
        guide = null;
        guideReady = false;
        changed();
    }

    public VoxelMask guide() {
        return guide;
    }

    public void setGuide(VoxelMask g) {
        guide = g;
        guideReady = true;
    }

    /** Сервер: місце деталі ще не знайдене — знайти (або перенести зі старого збереження) */
    public void ensureGuide(VoxelMask keep) {
        if (guideReady || voxels == null) return;
        if (keep == null) guide = null;
        else if (legacyGuide != null) guide = PartMatcher.shifted(keep, legacyGuide);
        else guide = PartMatcher.findGuide(voxels, keep);
        legacyGuide = null;
        guideReady = true;
    }

    /** Колода осіла на d шарів — і місце деталі з нею */
    public void sinkGuide(int d) {
        if (guide != null) guide.shiftDown(d);
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
        if (piece != null) { tag.put("Piece", piece); tag.putInt("PieceStart", pieceStart); }
        if (guide != null) tag.putByteArray("GuideMask", guide.toBytes());
        if (guideReady) tag.putBoolean("GuideReady", true);
        else if (legacyGuide != null) tag.putIntArray("Guide", legacyGuide);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        log = item(tag.getString("Log"));
        blueprint = item(tag.getString("Blueprint"));
        mask = tag.contains("Mask") ? CarveMask.fromBytes(tag.getByteArray("Mask")) : new CarveMask();
        voxels = tag.contains("Voxels") ? VoxelMask.fromBytes(tag.getByteArray("Voxels")) : null;
        piece = tag.contains("Piece") ? tag.getCompound("Piece") : null;
        pieceStart = tag.getInt("PieceStart");
        guide = tag.contains("GuideMask") ? VoxelMask.fromBytes(tag.getByteArray("GuideMask")) : null;
        guideReady = tag.getBoolean("GuideReady") || tag.getBoolean("NoGuide");
        legacyGuide = null;
        if (!guideReady && voxels != null) {        // старі збереження (17.11): зсув або «осіла»
            if (tag.contains("Guide")) legacyGuide = tag.getIntArray("Guide");
            else if (tag.contains("Sink")) legacyGuide = new int[]{ 0, -tag.getInt("Sink"), 0 };
        }
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
