package nc.init;

import nc.NuclearCraft;
import nc.multiblock.turbine.TurbineController;
import nc.multiblock.turbine.TurbineMenu;
import nc.multiblock.turbine.TurbinePartBlock;
import nc.multiblock.turbine.TurbinePartBlock.Part;
import nc.multiblock.turbine.TurbinePort;
import nc.multiblock.turbine.TurbineRotorBlock;
import nc.multiblock.turbine.TurbineWallBlock;
import nc.recipe.TurbineRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class NCTurbine {
    private static final List<RegistryObject<? extends Block>> PARTS = new ArrayList<>();
    private static Map<String, String> scriptedCoilRules = Map.of();

    public static final RegistryObject<TurbinePartBlock> CASING = part("turbine_casing", Part.CASING);
    public static final RegistryObject<TurbinePartBlock> GLASS = block("turbine_glass",
            () -> new TurbinePartBlock(BlockBehaviour.Properties.copy(Blocks.GLASS).noOcclusion(), Part.GLASS));
    public static final RegistryObject<TurbineWallBlock> CONTROLLER = wall("turbine_controller", Part.CONTROLLER);
    public static final RegistryObject<TurbineWallBlock> INLET = wall("turbine_inlet", Part.INLET);
    public static final RegistryObject<TurbineWallBlock> OUTLET = wall("turbine_outlet", Part.OUTLET);
    public static final RegistryObject<TurbinePartBlock> BEARING = part("turbine_rotor_bearing", Part.BEARING);
    public static final RegistryObject<TurbineRotorBlock> SHAFT = rotor("turbine_rotor_shaft", Part.SHAFT);
    public static final RegistryObject<TurbineRotorBlock> STEEL = rotor("turbine_rotor_blade_steel", Part.STEEL);
    public static final RegistryObject<TurbineRotorBlock> EXTREME = rotor("turbine_rotor_blade_extreme", Part.EXTREME);
    public static final RegistryObject<TurbineRotorBlock> SIC_SIC_CMC = rotor("turbine_rotor_blade_sic_sic_cmc", Part.SIC_SIC_CMC);
    public static final RegistryObject<TurbineRotorBlock> STATOR = rotor("turbine_rotor_stator", Part.STATOR);
    public static final RegistryObject<TurbineWallBlock> MAGNESIUM = coil(Part.MAGNESIUM);
    public static final RegistryObject<TurbineWallBlock> BERYLLIUM = coil(Part.BERYLLIUM);
    public static final RegistryObject<TurbineWallBlock> ALUMINUM = coil(Part.ALUMINUM);
    public static final RegistryObject<TurbineWallBlock> GOLD = coil(Part.GOLD);
    public static final RegistryObject<TurbineWallBlock> COPPER = coil(Part.COPPER);
    public static final RegistryObject<TurbineWallBlock> SILVER = coil(Part.SILVER);
    public static final RegistryObject<TurbineWallBlock> CONNECTOR = wall("turbine_coil_connector", Part.CONNECTOR);
    public static final RegistryObject<TurbineWallBlock> REDSTONE = wall("turbine_redstone_port", Part.REDSTONE);

    public static final RegistryObject<BlockEntityType<TurbineController>> CONTROLLER_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("turbine_controller", () ->
                    BlockEntityType.Builder.of(TurbineController::new, CONTROLLER.get()).build(null));
    public static final RegistryObject<BlockEntityType<TurbinePort>> PORT_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("turbine_port", () ->
                    BlockEntityType.Builder.of(TurbinePort::new, registeredPorts()).build(null));
    public static final RegistryObject<MenuType<TurbineMenu>> MENU = NCRegistries.MENUS.register("turbine_controller",
            () -> new MenuType<>(TurbineMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<RecipeType<TurbineRecipe>> RECIPES =
            NCRegistries.RECIPE_TYPES.register("turbine", () -> RecipeType.simple(NuclearCraft.id("turbine")));
    public static final RegistryObject<RecipeSerializer<TurbineRecipe>> SERIALIZER =
            NCRegistries.RECIPE_SERIALIZERS.register("turbine", TurbineRecipe.Serializer::new);

    public static final RegistryObject<SoundEvent> RUN_SOUND = NCRegistries.SOUNDS.register("block.nc.turbine_run",
            () -> SoundEvent.createVariableRangeEvent(NuclearCraft.id("block.nc.turbine_run")));

    private static final RegistryObject<CreativeModeTab> TAB = NCRegistries.TABS.register("turbine", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.nuclearcraft.turbine"))
                    .icon(() -> CONTROLLER.get().asItem().getDefaultInstance())
                    .displayItems((parameters, output) -> PARTS.forEach(part -> output.accept(part.get()))).build());

    private static RegistryObject<TurbinePartBlock> part(String id, Part part) {
        return block(id, () -> new TurbinePartBlock(properties(), part));
    }

    private static RegistryObject<TurbineWallBlock> wall(String id, Part part) {
        return block(id, () -> new TurbineWallBlock(properties(), part));
    }

    private static RegistryObject<TurbineWallBlock> coil(Part part) {
        return wall("turbine_dynamo_coil_" + part.name().toLowerCase(Locale.ROOT), part);
    }

    private static RegistryObject<TurbineRotorBlock> rotor(String id, Part part) {
        return block(id, () -> new TurbineRotorBlock(properties().noOcclusion(), part));
    }

    private static Block[] registeredPorts() {
        var ports = ForgeRegistries.BLOCKS.getValues().stream().filter(TurbineWallBlock.class::isInstance)
                .map(TurbineWallBlock.class::cast).filter(block -> block != CONTROLLER.get()).toList();
        scriptedCoilRules = ports.stream().filter(block -> block.scriptedRule() != null)
                .collect(Collectors.toUnmodifiableMap(TurbineWallBlock::dynamoName, TurbineWallBlock::scriptedRule));
        return ports.toArray(Block[]::new);
    }

    public static Map<String, String> scriptedCoilRules() { return scriptedCoilRules; }

    public static TurbineWallBlock createScriptedCoil(String name, double conductivity, String rule) {
        return TurbineWallBlock.scriptedCoil(properties(), name, conductivity, rule);
    }

    public static BlockItem createScriptedCoilItem(String blockId) {
        var id = ResourceLocation.tryParse(blockId);
        var block = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
        if (!(block instanceof TurbineWallBlock wall) || wall.scriptedRule() == null) {
            throw new IllegalArgumentException("No scripted NuclearCraft dynamo coil with ID " + blockId);
        }
        return new BlockItem(block, new Item.Properties());
    }

    public static TurbineRotorBlock createScriptedBlade(double efficiency, double expansion) {
        return TurbineRotorBlock.scriptedBlade(properties().noOcclusion(), efficiency, expansion);
    }

    public static TurbineRotorBlock createScriptedStator(double expansion) {
        return TurbineRotorBlock.scriptedStator(properties().noOcclusion(), expansion);
    }

    public static BlockItem createScriptedRotorItem(String blockId) {
        var id = ResourceLocation.tryParse(blockId);
        var block = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
        if (!(block instanceof TurbineRotorBlock)) {
            throw new IllegalArgumentException("No NuclearCraft turbine rotor block with ID " + blockId);
        }
        return new BlockItem(block, new Item.Properties());
    }

    private static <T extends Block> RegistryObject<T> block(String id, Supplier<T> supplier) {
        RegistryObject<T> block = NCRegistries.BLOCKS.register(id, supplier);
        NCRegistries.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        PARTS.add(block);
        return block;
    }

    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK);
    }

    private NCTurbine() {}

    static void register() {
        // Populate the shared deferred registers before Forge's registry events.
    }
}
