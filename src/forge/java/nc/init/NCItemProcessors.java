package nc.init;

import nc.NuclearCraft;
import nc.processor.ItemProcessorBlockEntity;
import nc.processor.ItemProcessorMenu;
import nc.processor.ProcessorBlock;
import nc.recipe.ItemProcessorRecipe;
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

public final class NCItemProcessors {
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ROCK_CRUSHER_ALTERNATE;

    static {
        var builder = new ForgeConfigSpec.Builder();
        builder.push("rock_crusher");
        ROCK_CRUSHER_ALTERNATE = builder.comment("Use the legacy alternate granite and andesite products.")
                .define("alternate_outputs", false);
        builder.pop();
        SPEC = builder.build();
    }

    private NCItemProcessors() {}

    static void register() {
        Machine.values();
    }

    public static void registerConfig(ModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, SPEC, "nuclearcraft-processors.toml");
    }

    public static boolean alternateRockCrusherOutputs() {
        return SPEC.isLoaded() ? ROCK_CRUSHER_ALTERNATE.get() : ROCK_CRUSHER_ALTERNATE.getDefault();
    }

    static void displayItems(CreativeModeTab.Output output) {
        for (Machine machine : Machine.values()) {
            output.accept(machine.item.get());
        }
    }

    public enum Machine {
        MANUFACTORY("manufactory", 1, 1, false, 56, -1, 116, 0, 74, 35, 16),
        DECAY_HASTENER("decay_hastener", 1, 1, false, 56, -1, 116, 0, 74, 35, 16),
        SEPARATOR("separator", 1, 2, false, 42, -1, 102, 28, 60, 34, 18),
        ALLOY_FURNACE("alloy_furnace", 2, 1, true, 46, -1, 126, 0, 84, 35, 16),
        INFUSER("infuser", 1, 1, true, 46, 66, 126, 0, 84, 35, 16),
        ROCK_CRUSHER("rock_crusher", 1, 3, false, 38, -1, 94, 20, 56, 35, 16),
        FUEL_REPROCESSOR("fuel_reprocessor", 1, 8, false, 30, -1, 86, 20, 48, 30, 38, 4, 12);

        private final String id;
        private final int inputs;
        private final int outputs;
        private final boolean losesProgress;
        private final int inputX;
        private final int fluidInputX;
        private final int outputX;
        private final int outputSpacing;
        private final int outputColumns;
        private final int menuExtension;
        private final int progressX;
        private final int progressY;
        private final int progressHeight;
        private final RegistryObject<ProcessorBlock<ItemProcessorBlockEntity>> block;
        private final RegistryObject<Item> item;
        private final RegistryObject<BlockEntityType<ItemProcessorBlockEntity>> entity;
        private final RegistryObject<MenuType<ItemProcessorMenu>> menu;
        private final RegistryObject<RecipeType<ItemProcessorRecipe>> recipes;
        private final RegistryObject<RecipeSerializer<ItemProcessorRecipe>> serializer;

        Machine(String id, int inputs, int outputs, boolean losesProgress, int inputX, int fluidInputX, int outputX,
                int outputSpacing, int progressX, int progressY, int progressHeight) {
            this(id, inputs, outputs, losesProgress, inputX, fluidInputX, outputX, outputSpacing, progressX, progressY,
                    progressHeight, outputs, 0);
        }

        Machine(String id, int inputs, int outputs, boolean losesProgress, int inputX, int fluidInputX, int outputX,
                int outputSpacing, int progressX, int progressY, int progressHeight, int outputColumns, int menuExtension) {
            this.id = id;
            this.inputs = inputs;
            this.outputs = outputs;
            this.losesProgress = losesProgress;
            this.inputX = inputX;
            this.fluidInputX = fluidInputX;
            this.outputX = outputX;
            this.outputSpacing = outputSpacing;
            this.outputColumns = outputColumns;
            this.menuExtension = menuExtension;
            this.progressX = progressX;
            this.progressY = progressY;
            this.progressHeight = progressHeight;
            block = NCRegistries.BLOCKS.register(id, () -> new ProcessorBlock<>(
                    BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                            .lightLevel(state -> state.getValue(ProcessorBlock.LIT) ? 7 : 0),
                    this::entityType, ItemProcessorBlockEntity::tick, ItemProcessorBlockEntity::inventory));
            item = NCRegistries.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
            entity = NCRegistries.BLOCK_ENTITIES.register(id, () -> BlockEntityType.Builder.of(
                    (pos, state) -> new ItemProcessorBlockEntity(pos, state, this), block.get()).build(null));
            menu = NCRegistries.MENUS.register(id,
                    () -> new MenuType<>((containerId, player) -> new ItemProcessorMenu(containerId, player, this),
                            FeatureFlags.DEFAULT_FLAGS));
            recipes = NCRegistries.RECIPE_TYPES.register(id, () -> RecipeType.simple(NuclearCraft.id(id)));
            serializer = NCRegistries.RECIPE_SERIALIZERS.register(id, () -> new ItemProcessorRecipe.Serializer(this));
        }

        public String id() {
            return id;
        }

        public int inputs() {
            return inputs;
        }

        public int outputs() {
            return outputs;
        }

        public int slots() {
            return inputs + outputs;
        }

        public int inputX(int slot) {
            return inputX + slot * 20;
        }

        public int inputY() {
            return 35 + menuExtension / 2;
        }

        public boolean hasFluidInput() {
            return fluidInputX >= 0;
        }

        public int fluidInputX() {
            return fluidInputX;
        }

        public int outputX(int slot) {
            return outputX + (slot % outputColumns) * outputSpacing;
        }

        public int outputY(int slot) {
            return inputY() + 20 * (slot / outputColumns) - 10 * ((outputs - 1) / outputColumns);
        }

        public int menuExtension() {
            return menuExtension;
        }

        public int progressX() {
            return progressX;
        }

        public int progressY() {
            return progressY;
        }

        public int progressHeight() {
            return progressHeight;
        }

        public boolean losesProgress() {
            return losesProgress;
        }

        public ProcessorBlock<ItemProcessorBlockEntity> block() {
            return block.get();
        }

        public BlockEntityType<ItemProcessorBlockEntity> entityType() {
            return entity.get();
        }

        public MenuType<ItemProcessorMenu> menu() {
            return menu.get();
        }

        public RecipeType<ItemProcessorRecipe> recipes() {
            return recipes.get();
        }

        public RecipeSerializer<ItemProcessorRecipe> serializer() {
            return serializer.get();
        }
    }
}
