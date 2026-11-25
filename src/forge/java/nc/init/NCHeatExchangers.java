package nc.init;

import nc.NuclearCraft;
import nc.multiblock.hx.ExchangerController;
import nc.multiblock.hx.ExchangerMenu;
import nc.multiblock.hx.ExchangerPort;
import nc.multiblock.hx.ExchangerPartBlock;
import nc.multiblock.hx.ExchangerPartBlock.Part;
import nc.multiblock.hx.ExchangerTubeBlock;
import nc.multiblock.hx.ExchangerWallBlock;
import nc.multiblock.machine.MachinePartBlock;
import nc.recipe.CondenserRecipe;
import nc.recipe.HeatExchangerRecipe;
import nc.multiblock.fission.FissionMultitool;
import nc.multiblock.quantum.QuantumMultitool;
import nc.processor.ProcessorMultitool;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class NCHeatExchangers {
    private static final List<RegistryObject<? extends Block>> PARTS = new ArrayList<>();

    public static final RegistryObject<ExchangerPartBlock> CASING = block("heat_exchanger_casing",
            () -> new ExchangerPartBlock(metal(), Part.CASING));
    public static final RegistryObject<ExchangerPartBlock> GLASS = block("heat_exchanger_glass",
            () -> new ExchangerPartBlock(BlockBehaviour.Properties.copy(Blocks.GLASS).noOcclusion(), Part.GLASS));
    public static final RegistryObject<ExchangerPartBlock> REDSTONE = block("heat_exchanger_redstone_port",
            () -> new ExchangerPartBlock(metal(), Part.REDSTONE));
    public static final RegistryObject<ExchangerPartBlock> BAFFLE = block("heat_exchanger_baffle",
            () -> new ExchangerPartBlock(metal(), Part.BAFFLE));
    public static final RegistryObject<ExchangerWallBlock> CONDENSER = wall("condenser_controller", Part.CONDENSER);
    public static final RegistryObject<ExchangerWallBlock> CONTROLLER = wall("heat_exchanger_controller", Part.CONTROLLER);
    public static final RegistryObject<ExchangerWallBlock> INLET = wall("heat_exchanger_inlet", Part.INLET);
    public static final RegistryObject<ExchangerWallBlock> OUTLET = wall("heat_exchanger_outlet", Part.OUTLET);
    public static final RegistryObject<ExchangerTubeBlock> COPPER = tube(Part.COPPER);
    public static final RegistryObject<ExchangerTubeBlock> HARD_CARBON = tube(Part.HARD_CARBON);
    public static final RegistryObject<ExchangerTubeBlock> THERMOCONDUCTING = tube(Part.THERMOCONDUCTING);

    public static final RegistryObject<Item> MULTITOOL = NCRegistries.ITEMS.register("multitool",
            () -> new Item(new Item.Properties().stacksTo(1)) {
                @Override
                public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
                    BlockState state = context.getLevel().getBlockState(context.getClickedPos());
                    var quantum = QuantumMultitool.useOn(stack, context);
                    if (quantum != InteractionResult.PASS) return quantum;
                    var fission = FissionMultitool.useOn(stack, context);
                    if (fission != InteractionResult.PASS) return fission;
                    var processor = ProcessorMultitool.useOn(stack, context);
                    if (processor != InteractionResult.PASS) return processor;
                    if (state.getBlock() instanceof ExchangerTubeBlock tube) return tube.configure(state, context);
                    if (state.getBlock() instanceof MachinePartBlock.Wall wall && context.getPlayer() != null
                            && context.getPlayer().isShiftKeyDown()) return wall.reverseProcessPort(context);
                    return InteractionResult.PASS;
                }

                @Override
                public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
                    ItemStack stack = player.getItemInHand(hand);
                    if (QuantumMultitool.setAngle(player, stack)) {
                        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
                    }
                    if (level instanceof ServerLevel server && player instanceof ServerPlayer user && player.isShiftKeyDown()) {
                        var hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
                        boolean clearedFission = (hit.getType() == HitResult.Type.MISS
                                || !FissionMultitool.handles(level.getBlockState(hit.getBlockPos())))
                                && FissionMultitool.clear(server, user, stack);
                        boolean clearedProcessor = hit.getType() == HitResult.Type.MISS && ProcessorMultitool.clear(user, stack);
                        if (clearedFission || clearedProcessor) {
                            return InteractionResultHolder.consume(stack);
                        }
                    }
                    return super.use(level, player, hand);
                }
            });

    public static final RegistryObject<BlockEntityType<ExchangerController>> CONDENSER_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("condenser_controller", () ->
                    BlockEntityType.Builder.of(ExchangerController::new, CONDENSER.get()).build(null));
    public static final RegistryObject<BlockEntityType<ExchangerController>> CONTROLLER_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("heat_exchanger_controller", () ->
                    BlockEntityType.Builder.of(ExchangerController::new, CONTROLLER.get()).build(null));
    public static final RegistryObject<BlockEntityType<ExchangerPort>> PORT_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("heat_exchanger_port", () ->
                    BlockEntityType.Builder.of(ExchangerPort::new, INLET.get(), OUTLET.get()).build(null));
    public static final RegistryObject<MenuType<ExchangerMenu>> CONDENSER_MENU = NCRegistries.MENUS.register("condenser_controller",
            () -> new MenuType<>((id, inventory) -> new ExchangerMenu(id, true), FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<MenuType<ExchangerMenu>> MENU = NCRegistries.MENUS.register("heat_exchanger_controller",
            () -> new MenuType<>((id, inventory) -> new ExchangerMenu(id, false), FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<RecipeType<CondenserRecipe>> RECIPES =
            NCRegistries.RECIPE_TYPES.register("condenser", () -> RecipeType.simple(NuclearCraft.id("condenser")));
    public static final RegistryObject<RecipeSerializer<CondenserRecipe>> SERIALIZER =
            NCRegistries.RECIPE_SERIALIZERS.register("condenser", CondenserRecipe.Serializer::new);

    public static final RegistryObject<RecipeType<HeatExchangerRecipe>> HEAT_EXCHANGER_RECIPES =
            NCRegistries.RECIPE_TYPES.register("heat_exchanger", () -> RecipeType.simple(NuclearCraft.id("heat_exchanger")));
    public static final RegistryObject<RecipeSerializer<HeatExchangerRecipe>> HEAT_EXCHANGER_SERIALIZER =
            NCRegistries.RECIPE_SERIALIZERS.register("heat_exchanger", HeatExchangerRecipe.Serializer::new);

    private static final RegistryObject<CreativeModeTab> TAB = NCRegistries.TABS.register("heat_exchanger", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.nuclearcraft.heat_exchanger"))
                    .icon(() -> CONTROLLER.get().asItem().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        PARTS.forEach(part -> output.accept(part.get()));
                        output.accept(MULTITOOL.get());
                        output.accept(NCFluids.CONDENSATE_WATER.bucket().get());
                        output.accept(NCFluids.PREHEATED_WATER.bucket().get());
                        NCSaltCoolants.displayBuckets(fluid -> output.accept(fluid.bucket().get()));
                    }).build());

    private static RegistryObject<ExchangerWallBlock> wall(String id, Part part) {
        return block(id, () -> new ExchangerWallBlock(metal(), part));
    }

    private static RegistryObject<ExchangerTubeBlock> tube(Part part) {
        return block("heat_exchanger_tube_" + part.name().toLowerCase(Locale.ROOT),
                () -> new ExchangerTubeBlock(metal().noOcclusion().dynamicShape(), part));
    }

    public static ExchangerTubeBlock createScriptedTube(double conductivity, double retention) {
        return new ExchangerTubeBlock(metal().noOcclusion().dynamicShape(), conductivity, retention);
    }

    public static BlockItem createScriptedTubeItem(String blockId) {
        var id = ResourceLocation.tryParse(blockId);
        var block = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
        if (!(block instanceof ExchangerTubeBlock)) {
            throw new IllegalArgumentException("No NuclearCraft heat exchanger tube with ID " + blockId);
        }
        return new BlockItem(block, new Item.Properties());
    }

    private static <T extends Block> RegistryObject<T> block(String id, Supplier<T> factory) {
        RegistryObject<T> block = NCRegistries.BLOCKS.register(id, factory);
        NCRegistries.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        PARTS.add(block);
        return block;
    }

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK);
    }

    private NCHeatExchangers() {}

    static void register() {
        // Populate the shared deferred registers before Forge's registry events.
    }
}
