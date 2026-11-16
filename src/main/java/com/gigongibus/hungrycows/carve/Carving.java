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
    public static final float MATCH_MIN = 0.55F;          // схожість на шаблон нижче — палиці (схожість = спільне / разом)
    public static final float MATCH_PERFECT = 0.95F;      // від цієї — найкраща якість (1,0)
    // Fij (16.11): «ідеальну форму практично неможливо зробити» — пікселі ближче 2 до краю деталі не рахуються
    public static final int EDGE_TOLERANCE = 2;
    public static final int MAX_STRIKES_PER_PACKET = 200; // більше ударів за секунду гравець не зробить — не вірити

    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HungryCowsMod.MODID);

    public static final RegistryObject<Item> CHISEL = HungryCowsMod.ITEMS.register("chisel", () ->
            new Item(new Item.Properties().durability(CHISEL_DURABILITY)));
    // креслення (Fij: «поки текстура паперу з написами»), деталі й кружка (модель і текстура Fij)
    public static final RegistryObject<Item> MUG_BODY = HungryCowsMod.ITEMS.register("mug_body", () -> new CarvedPartItem(new Item.Properties()));
    public static final RegistryObject<Item> MUG_EARS = HungryCowsMod.ITEMS.register("mug_ears", () -> new CarvedPartItem(new Item.Properties()));
    public static final RegistryObject<Item> MUG_HANDLE = HungryCowsMod.ITEMS.register("mug_handle", () -> new CarvedPartItem(new Item.Properties()));
    public static final RegistryObject<Item> BLUEPRINT_MUG_BODY = HungryCowsMod.ITEMS.register("blueprint_mug_body", () ->
            new BlueprintItem("mug_body", MUG_BODY, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BLUEPRINT_MUG_EARS = HungryCowsMod.ITEMS.register("blueprint_mug_ears", () ->
            new BlueprintItem("mug_ears", MUG_EARS, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BLUEPRINT_MUG_HANDLE = HungryCowsMod.ITEMS.register("blueprint_mug_handle", () ->
            new BlueprintItem("mug_handle", MUG_HANDLE, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MUG = HungryCowsMod.ITEMS.register("mug", () -> new MugItem(new Item.Properties()));
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, HungryCowsMod.MODID);
    public static final RegistryObject<net.minecraft.world.item.crafting.RecipeSerializer<MugRecipe>> MUG_RECIPE = SERIALIZERS.register("mug", () ->
            new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(MugRecipe::new));

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
        SERIALIZERS.register(bus);
    }

    /** Точність (0…1) зі схожості на шаблон; нижче MATCH_MIN — не вийшло (−1) */
    public static float quality(float match) {
        if (match < MATCH_MIN) return -1;
        return Math.min(1, (match - MATCH_MIN) / (MATCH_PERFECT - MATCH_MIN));
    }

    /** Скільки разів можна випити з кружки такої точності */
    public static int usesFor(float q) {
        return Math.round(MugItem.MIN_USES + (MugItem.MAX_USES - MugItem.MIN_USES) * Math.max(0, Math.min(1, q)));
    }

    /** Колода обтесаного дерева (stripped_*: колоди, стовбури, «дерево»), не дошки (Fij) */
    public static boolean isStrippedLog(ItemStack s) {
        if (!(s.getItem() instanceof BlockItem) || !s.is(ItemTags.LOGS)) return false;
        var id = ForgeRegistries.ITEMS.getKey(s.getItem());
        return id != null && id.getPath().startsWith("stripped_");
    }

    /** Присів + ПКМ по верху верстака: кресленням — кладе креслення; колодою по кресленню — кладе колоду (Fij: спершу шаблон). */
    public static boolean tryPlace(Level level, Player player, BlockPos clicked, ItemStack held) {
        var st = level.getBlockState(clicked);
        if (held.getItem() instanceof BlueprintItem && st.is(Blocks.CRAFTING_TABLE)) {
            BlockPos at = clicked.above();
            if (!level.getBlockState(at).canBeReplaced()) return false;
            if (level.isClientSide) return true;
            level.setBlock(at, WORKPIECE.get().defaultBlockState(), 3);
            if (level.getBlockEntity(at) instanceof WorkpieceBlockEntity be) be.setBlueprint(held.getItem());
            level.playSound(null, at, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1F, 1F);
            if (player == null || !player.getAbilities().instabuild) held.shrink(1);
            return true;
        }
        if (isStrippedLog(held) && st.is(WORKPIECE.get()) && level.getBlockEntity(clicked) instanceof WorkpieceBlockEntity be && !be.hasLog()) {
            if (level.isClientSide) return true;
            be.setLog(held.getItem());
            level.playSound(null, clicked, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1F, 0.9F);
            if (player == null || !player.getAbilities().instabuild) held.shrink(1);
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        Player p = e.getEntity();
        if (e.getHand() != InteractionHand.MAIN_HAND || !p.isShiftKeyDown()) return;
        if (e.getFace() != Direction.UP && !e.getLevel().getBlockState(e.getPos()).is(WORKPIECE.get())) return;
        if (tryPlace(e.getLevel(), p, e.getPos(), e.getItemStack())) {
            e.setCanceled(true);
            e.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(e.getLevel().isClientSide));
        }
    }
}
