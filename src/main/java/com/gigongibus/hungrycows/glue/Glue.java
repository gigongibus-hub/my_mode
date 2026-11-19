package com.gigongibus.hungrycows.glue;

import com.gigongibus.hungrycows.HungryCowsMod;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/**
 * СКЛЕЮВАННЯ КРУЖКИ (Fij, 19.11; текстури Fij `textures/item/glue/`): смола з ялини (рубаєш колоду сокирою), щетина свині
 * (ножиці по свині), пензлик (3 щетини / самородок / палиця), котел зі смолою на багатті (доба → розплавлена), пензлем
 * намазати заготовку кружки, приставити вушка, намазати, приставити ручку, пів доби — кружка.
 */
@Mod.EventBusSubscriber(modid = HungryCowsMod.MODID)
public final class Glue {
    private Glue() {
    }

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final float RESIN_CHANCE = 0.5F;        // зрубав колоду ялини сокирою — 50% смола (Fij)
    public static final int RESIN_MIN = 1, RESIN_MAX = 6; // скільки смоли (Fij: від 1 до 6)
    public static final int BRISTLE_MIN = 1, BRISTLE_MAX = 3; // ножиці по свині — 1–3 щетини (Fij)
    public static final int SHEARS_PER_PERIOD = 3;        // стригти ту саму свиню — лише 3 рази…
    public static final long SHEAR_PERIOD = 5 * 24000L;   // …за 5 діб (Fij)
    public static final int BRUSH_USES = 20;              // пензлик — 20 намазувань (Fij)

    public static final RegistryObject<Item> RESIN = HungryCowsMod.ITEMS.register("resin", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PIG_BRISTLE = HungryCowsMod.ITEMS.register("pig_bristle", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> BRISTLE_BRUSH = HungryCowsMod.ITEMS.register("bristle_brush", () ->
            new ResinBrushItem(new Item.Properties().durability(BRUSH_USES)));

    public static final net.minecraftforge.registries.DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            net.minecraftforge.registries.DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.BLOCK_ENTITY_TYPES, HungryCowsMod.MODID);
    // котел зі смолою — свій блок на місці ванільного котла (як котел з лавою); предмета немає (ламається в котел)
    public static final RegistryObject<Block> RESIN_CAULDRON = HungryCowsMod.BLOCKS.register("resin_cauldron", () ->
            new ResinCauldronBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.CAULDRON)));
    public static final RegistryObject<Block> MUG_ASSEMBLY = HungryCowsMod.BLOCKS.register("mug_assembly", () ->
            new MugAssemblyBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                    .mapColor(net.minecraft.world.level.material.MapColor.WOOD).strength(0.5F)
                    .sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<ResinCauldronBlockEntity>> RESIN_CAULDRON_BE =
            BLOCK_ENTITIES.register("resin_cauldron", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder
                    .of(ResinCauldronBlockEntity::new, RESIN_CAULDRON.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<MugAssemblyBlockEntity>> MUG_ASSEMBLY_BE =
            BLOCK_ENTITIES.register("mug_assembly", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder
                    .of(MugAssemblyBlockEntity::new, MUG_ASSEMBLY.get()).build(null));

    public static void init() {
    }

    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }

    /** ПКМ смолою по порожньому ванільному котлу — котел зі смолою (1 шматок) */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        ItemStack held = e.getItemStack();
        if (!held.is(RESIN.get()) || !e.getLevel().getBlockState(e.getPos()).is(net.minecraft.world.level.block.Blocks.CAULDRON)) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.sidedSuccess(e.getLevel().isClientSide));
        if (e.getLevel().isClientSide) return;
        ResinCauldronBlock.place(e.getLevel(), e.getPos(), 1);
        if (!e.getEntity().getAbilities().instabuild) held.shrink(1);
        e.getLevel().playSound(null, e.getPos(), SoundEvents.HONEY_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 1.2F);
    }

    /** Колода ялини сокирою (не в творчому) — з шансом смола поруч із колодою */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        Player p = e.getPlayer();
        if (p == null || p.getAbilities().instabuild || !e.getState().is(BlockTags.SPRUCE_LOGS)) return;
        if (!(p.getMainHandItem().getItem() instanceof AxeItem)) return;
        if (level.random.nextFloat() >= RESIN_CHANCE) return;
        int n = RESIN_MIN + level.random.nextInt(RESIN_MAX - RESIN_MIN + 1);
        Block.popResource(level, e.getPos(), new ItemStack(RESIN.get(), n));
    }

    /** Ножиці по свині (Fij): 1–3 щетини, не частіше 3 разів за 5 діб на свиню */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getTarget() instanceof Pig pig) || pig.isBaby()) return;
        ItemStack held = e.getItemStack();
        if (!held.is(Items.SHEARS)) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.sidedSuccess(e.getLevel().isClientSide));
        if (e.getLevel().isClientSide) return;
        if (!shear(pig, e.getLevel().getGameTime())) return;
        int n = BRISTLE_MIN + pig.getRandom().nextInt(BRISTLE_MAX - BRISTLE_MIN + 1);
        pig.spawnAtLocation(new ItemStack(PIG_BRISTLE.get(), n), 0.5F);
        pig.level().playSound(null, pig, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1F, 1.1F);
        held.hurtAndBreak(1, e.getEntity(), pl -> pl.broadcastBreakEvent(e.getHand()));
    }

    /** Чи можна стригти зараз (і записати час); пам'ятає часи стрижок у самій свині */
    public static boolean shear(Pig pig, long now) {
        var data = pig.getPersistentData();
        ListTag times = data.getList("HcShorn", Tag.TAG_LONG);
        ListTag kept = new ListTag();
        for (Tag t : times) if (now - ((LongTag) t).getAsLong() < SHEAR_PERIOD) kept.add(t);
        if (kept.size() >= SHEARS_PER_PERIOD) { data.put("HcShorn", kept); return false; }
        kept.add(LongTag.valueOf(now));
        data.put("HcShorn", kept);
        return true;
    }
}
