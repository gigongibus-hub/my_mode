package com.gigongibus.hungrycows.carve;

import com.gigongibus.hungrycows.HungryCowsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * РЕМЕСЛО (Fij, 16.11): своя система крафту — вирізати зубилом. Крок 1: зубило (середнє, текстури Fij), заготовка на
 * ванільному верстаку (присів + ПКМ колодою обтесаного дерева), вікно 124 × 124 з фізикою зубила (`client/CarvingScreen`,
 * `ChiselSim`). Вирізане зберігається в заготовці. Шаблони (кружка), якість, знос — наступні кроки.
 */
@Mod.EventBusSubscriber(modid = HungryCowsMod.MODID)
public final class Carving {
    private Carving() {
    }

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int CHISEL_DURABILITY = 250;      // міцність зубила (ПРОПОЗИЦІЯ; знос — наступний крок)
    public static final double MAX_REACH = 8.0;           // далі 8 бл. від заготовки — сервер не приймає вирізане
    public static final int STICKS_WHEN_RUINED = 2;       // зламав почату заготовку / вийшло ні на що не схоже — палиці (Fij «поки просто палки»)

    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HungryCowsMod.MODID);

    public static final RegistryObject<Item> CHISEL = HungryCowsMod.ITEMS.register("chisel", () ->
            new Item(new Item.Properties().durability(CHISEL_DURABILITY)));
    public static final RegistryObject<Block> WORKPIECE = HungryCowsMod.BLOCKS.register("workpiece", () ->
            new WorkpieceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<WorkpieceBlockEntity>> WORKPIECE_BE =
            BLOCK_ENTITIES.register("workpiece", () ->
                    net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(WorkpieceBlockEntity::new, WORKPIECE.get()).build(null));

    public static void init() {
    }

    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }

    /** Колода обтесаного дерева (stripped_*: колоди, стовбури, «дерево»), не дошки (Fij) */
    public static boolean isStrippedLog(ItemStack s) {
        if (!(s.getItem() instanceof BlockItem) || !s.is(ItemTags.LOGS)) return false;
        var id = ForgeRegistries.ITEMS.getKey(s.getItem());
        return id != null && id.getPath().startsWith("stripped_");
    }

    /** Покласти колоду на верстак (присів + ПКМ). true — поклав */
    public static boolean tryPlace(Level level, Player player, BlockPos table, ItemStack held) {
        if (!level.getBlockState(table).is(Blocks.CRAFTING_TABLE) || !isStrippedLog(held)) return false;
        BlockPos at = table.above();
        if (!level.getBlockState(at).canBeReplaced()) return false;
        if (level.isClientSide) return true;
        level.setBlock(at, WORKPIECE.get().defaultBlockState(), 3);
        if (level.getBlockEntity(at) instanceof WorkpieceBlockEntity be) be.setLog(held.getItem());
        level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1F, 0.9F);
        if (player == null || !player.getAbilities().instabuild) held.shrink(1);
        return true;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        Player p = e.getEntity();
        if (e.getHand() != InteractionHand.MAIN_HAND || !p.isShiftKeyDown() || e.getFace() != Direction.UP) return;
        if (tryPlace(e.getLevel(), p, e.getPos(), e.getItemStack())) {
            e.setCanceled(true);
            e.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(e.getLevel().isClientSide));
        }
    }
}
