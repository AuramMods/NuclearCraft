package nc.init;

import nc.NuclearCraft;
import nc.multiblock.machine.DecayPool;
import nc.multiblock.machine.DecayPoolMenu;
import nc.multiblock.machine.Distiller;
import nc.multiblock.machine.DistillerMenu;
import nc.multiblock.machine.MachinePartBlock;
import nc.multiblock.machine.MachinePartBlock.Part;
import nc.multiblock.machine.MachinePort;
import nc.recipe.DecayPoolRecipe;
import nc.recipe.DistillerRecipe;
import net.minecraft.sounds.SoundEvent;
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
    private static final ForgeConfigSpec.IntValue DISTILLER_TIME;
    private static final ForgeConfigSpec.IntValue DISTILLER_POWER;
    private static final ForgeConfigSpec.DoubleValue DISTILLER_SOUND_VOLUME;
    private static final ForgeConfigSpec.BooleanValue DISTILLER_SMART_INPUT;
    private static final ForgeConfigSpec.BooleanValue DISTILLER_FACTOR_RECIPES;
    private static final EnumMap<Part, ForgeConfigSpec.DoubleValue> SIEVE_EFFICIENCIES = new EnumMap<>(Part.class);

    static {
        var config = new ForgeConfigSpec.Builder();
        MIN_SIZE = config.comment("Minimum interior length on each axis").defineInRange("min_size", 1, 1, 255);
        MAX_SIZE = config.comment("Maximum interior length on each axis").defineInRange("max_size", 24, 3, 255);
        config.push("distiller");
        DISTILLER_TIME = config.defineInRange("time", 800, 1, 128000);
        DISTILLER_POWER = config.defineInRange("power", 10, 1, 128000);
        DISTILLER_SOUND_VOLUME = config.defineInRange("sound_volume", 1D, 0D, 15D);
        DISTILLER_SMART_INPUT = config.comment("Reject fluid combinations that cannot match any Distiller recipe")
                .define("smart_input", true);
        DISTILLER_FACTOR_RECIPES = config.comment("Divide recipe amounts and duration by their greatest common divisor")
                .define("factor_recipes", false);
        config.push("sieve_efficiency");
        SIEVE_EFFICIENCIES.put(Part.STEEL_SIEVE, config.defineInRange("steel", 0.8D, 0D, 255D));
        SIEVE_EFFICIENCIES.put(Part.PTFE_SIEVE, config.defineInRange("polytetrafluoroethene", 0.9D, 0D, 255D));
        SIEVE_EFFICIENCIES.put(Part.HASTELLOY_SIEVE, config.defineInRange("hastelloy", 1D, 0D, 255D));
        config.pop(2);
        SPEC = config.build();

        for (Part part : Part.values()) {
            String id = part.id();
            var block = NCRegistries.BLOCKS.register(id, () -> {
                var properties = part == Part.GLASS ? BlockBehaviour.Properties.copy(Blocks.GLASS).noOcclusion()
                        : BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK);
                return switch (part) {
                    case FRAME -> new MachinePartBlock.Frame(properties);
                    case REDSTONE -> new MachinePartBlock.Redstone(properties);
                    case REBOILER -> new MachinePartBlock.Reboiler(properties);
                    default -> part.wallEntity() ? new MachinePartBlock.Wall(properties, part) : new MachinePartBlock(properties, part);
                };
            });
            BLOCKS.put(part, block);
            NCRegistries.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        }
    }

    public static final RegistryObject<BlockEntityType<DecayPool>> POOL = NCRegistries.BLOCK_ENTITIES.register("decay_pool_controller",
            () -> BlockEntityType.Builder.of(DecayPool::new, block(Part.DECAY_POOL)).build(null));
    public static final RegistryObject<BlockEntityType<Distiller>> DISTILLER = NCRegistries.BLOCK_ENTITIES.register("distiller_controller",
            () -> BlockEntityType.Builder.of(Distiller::new, block(Part.DISTILLER)).build(null));
    public static final RegistryObject<MenuType<DistillerMenu>> DISTILLER_MENU = NCRegistries.MENUS.register("distiller_controller",
            () -> new MenuType<>((id, inventory) -> new DistillerMenu(id), FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<RecipeType<DistillerRecipe>> DISTILLER_RECIPES = NCRegistries.RECIPE_TYPES.register("multiblock_distiller",
            () -> RecipeType.simple(NuclearCraft.id("multiblock_distiller")));
    public static final RegistryObject<RecipeSerializer<DistillerRecipe>> DISTILLER_SERIALIZER =
            NCRegistries.RECIPE_SERIALIZERS.register("multiblock_distiller", DistillerRecipe.Serializer::new);
    public static final RegistryObject<SoundEvent> DISTILLER_RUN = NCRegistries.SOUNDS.register("block.distiller.run",
            () -> SoundEvent.createVariableRangeEvent(NuclearCraft.id("block.distiller.run")));
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
    public static int distillerTime() { return SPEC.isLoaded() ? DISTILLER_TIME.get() : DISTILLER_TIME.getDefault(); }
    public static int distillerPower() { return SPEC.isLoaded() ? DISTILLER_POWER.get() : DISTILLER_POWER.getDefault(); }
    public static double distillerSoundVolume() {
        return SPEC.isLoaded() ? DISTILLER_SOUND_VOLUME.get() : DISTILLER_SOUND_VOLUME.getDefault();
    }
    public static boolean distillerSmartInput() {
        return SPEC.isLoaded() ? DISTILLER_SMART_INPUT.get() : DISTILLER_SMART_INPUT.getDefault();
    }
    public static boolean distillerFactorRecipes() {
        return SPEC.isLoaded() ? DISTILLER_FACTOR_RECIPES.get() : DISTILLER_FACTOR_RECIPES.getDefault();
    }
    public static double sieveEfficiency(Part part) {
        var efficiency = SIEVE_EFFICIENCIES.get(part);
        return SPEC.isLoaded() ? efficiency.get() : efficiency.getDefault();
    }

    static void displayItems(CreativeModeTab.Output output) {
        BLOCKS.values().forEach(block -> output.accept(block.get()));
    }
}
