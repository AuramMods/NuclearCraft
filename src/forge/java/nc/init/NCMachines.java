package nc.init;

import nc.NuclearCraft;
import nc.multiblock.machine.DecayPool;
import nc.multiblock.machine.DecayPoolMenu;
import nc.multiblock.machine.MachinePartBlock;
import nc.multiblock.machine.MachinePartBlock.Part;
import nc.multiblock.machine.MachinePort;
import nc.recipe.DecayPoolRecipe;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;

public final class NCMachines {
    private static final EnumMap<Part, RegistryObject<MachinePartBlock>> BLOCKS = new EnumMap<>(Part.class);
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.IntValue MIN_SIZE;
    private static final ForgeConfigSpec.IntValue MAX_SIZE;

    static {
        var config = new ForgeConfigSpec.Builder();
        MIN_SIZE = config.comment("Minimum interior length on each axis").defineInRange("min_size", 1, 1, 255);
        MAX_SIZE = config.comment("Maximum interior length on each axis").defineInRange("max_size", 24, 3, 255);
        SPEC = config.build();

        for (Part part : Part.values()) {
            String id = part.id();
            var block = NCRegistries.BLOCKS.register(id, () -> {
                var properties = part == Part.GLASS ? BlockBehaviour.Properties.copy(Blocks.GLASS).noOcclusion()
                        : BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK);
                return switch (part) {
                    case FRAME -> new MachinePartBlock.Frame(properties);
                    case REDSTONE -> new MachinePartBlock.Redstone(properties);
                    default -> part.wallEntity() ? new MachinePartBlock.Wall(properties, part) : new MachinePartBlock(properties, part);
                };
            });
            BLOCKS.put(part, block);
            NCRegistries.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        }
    }

    public static final RegistryObject<BlockEntityType<DecayPool>> POOL = NCRegistries.BLOCK_ENTITIES.register("decay_pool_controller",
            () -> BlockEntityType.Builder.of(DecayPool::new, block(Part.DECAY_POOL)).build(null));
    public static final RegistryObject<BlockEntityType<MachinePort>> PORT = NCRegistries.BLOCK_ENTITIES.register("machine_port",
            () -> BlockEntityType.Builder.of(MachinePort::new, block(Part.PROCESS), block(Part.RESERVOIR), block(Part.POWER)).build(null));
    public static final RegistryObject<MenuType<DecayPoolMenu>> MENU = NCRegistries.MENUS.register("decay_pool_controller",
            () -> new MenuType<>((id, inventory) -> new DecayPoolMenu(id), FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<RecipeType<DecayPoolRecipe>> POOL_RECIPES = NCRegistries.RECIPE_TYPES.register("multiblock_decay_pool",
            () -> RecipeType.simple(NuclearCraft.id("multiblock_decay_pool")));
    public static final RegistryObject<RecipeType<DecayPoolRecipe>> HEAT_SOURCES = NCRegistries.RECIPE_TYPES.register("decay_pool_heat_source",
            () -> RecipeType.simple(NuclearCraft.id("decay_pool_heat_source")));
    public static final RegistryObject<RecipeSerializer<DecayPoolRecipe>> POOL_SERIALIZER =
            NCRegistries.RECIPE_SERIALIZERS.register("multiblock_decay_pool", () -> new DecayPoolRecipe.Serializer(false));
    public static final RegistryObject<RecipeSerializer<DecayPoolRecipe>> HEAT_SOURCE_SERIALIZER =
            NCRegistries.RECIPE_SERIALIZERS.register("decay_pool_heat_source", () -> new DecayPoolRecipe.Serializer(true));

    private NCMachines() {}

    public static void registerConfig(ModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, SPEC, "nuclearcraft-machines.toml");
    }

    public static MachinePartBlock block(Part part) { return BLOCKS.get(part).get(); }
    public static int minSize() { return SPEC.isLoaded() ? MIN_SIZE.get() : MIN_SIZE.getDefault(); }
    public static int maxSize() { return SPEC.isLoaded() ? MAX_SIZE.get() : MAX_SIZE.getDefault(); }

    static void displayItems(CreativeModeTab.Output output) {
        BLOCKS.values().forEach(block -> output.accept(block.get()));
    }
}
