package nc.init;

import nc.NuclearCraft;
import nc.item.ProcessorUpgradeItem;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class NCItemProcessors {
    public static final RegistryObject<Item> SPEED_UPGRADE = NCRegistries.ITEMS.register("speed_upgrade",
            () -> new ProcessorUpgradeItem(0));
    public static final RegistryObject<Item> ENERGY_UPGRADE = NCRegistries.ITEMS.register("energy_upgrade",
            () -> new ProcessorUpgradeItem(1));
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ROCK_CRUSHER_ALTERNATE;
    private static final ForgeConfigSpec.BooleanValue MEKANISM_GAS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> SPEED_POWERS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> SPEED_FACTORS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> ENERGY_POWERS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> ENERGY_FACTORS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> UPGRADE_LIMITS;

    static {
        var builder = new ForgeConfigSpec.Builder();
        MEKANISM_GAS = builder.comment("Allow the Universal Bin to accept Mekanism gases.")
                .define("enable_mek_gas", true);
        builder.push("upgrades");
        String speedOrder = "Order: Processor Time, Processor Power, Generator Time, Generator Power.";
        String energyOrder = "Order: Processor Power, Generator Time.";
        SPEED_POWERS = upgradeArray(builder, "speed_upgrade_power_laws_fp", speedOrder,
                List.of(1D, 2D, 2D, 1D), 1, 15);
        SPEED_FACTORS = upgradeArray(builder, "speed_upgrade_multipliers_fp", speedOrder,
                List.of(1D, 1D, 1D, 1D), 0, 15);
        ENERGY_POWERS = upgradeArray(builder, "energy_upgrade_power_laws_fp", energyOrder, List.of(1D, 1D), 1, 15);
        ENERGY_FACTORS = upgradeArray(builder, "energy_upgrade_multipliers_fp", energyOrder, List.of(1D, 1D), 0, 15);
        UPGRADE_LIMITS = builder.comment("Stack limits for Speed Upgrades and Energy Upgrades, from 1 to 64.")
                .define("upgrade_stack_sizes", List.of(64, 64), candidate -> candidate instanceof List<?> values
                        && values.size() == 2 && values.stream().allMatch(element ->
                        element instanceof Integer size && size >= 1 && size <= 64));
        builder.pop();
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

    public static boolean mekanismGasEnabled() {
        return SPEC.isLoaded() ? MEKANISM_GAS.get() : MEKANISM_GAS.getDefault();
    }

    public static double speedUpgrade(int count) {
        return upgradeFactor(count, SPEED_POWERS, SPEED_FACTORS, 0);
    }

    public static double powerUpgrade(int speedCount, int energyCount) {
        return upgradeFactor(speedCount, SPEED_POWERS, SPEED_FACTORS, 1)
                / upgradeFactor(Math.min(speedCount, energyCount), ENERGY_POWERS, ENERGY_FACTORS, 0);
    }

    public static int upgradeLimit(int slot) {
        return value(UPGRADE_LIMITS, slot).intValue();
    }

    private static double upgradeFactor(int count, ForgeConfigSpec.ConfigValue<List<? extends Number>> powers,
                                        ForgeConfigSpec.ConfigValue<List<? extends Number>> factors, int index) {
        return count == 0 ? 1D : 1D + value(factors, index).doubleValue()
                * (Math.pow(1D + count, value(powers, index).doubleValue()) - 1D);
    }

    private static Number value(ForgeConfigSpec.ConfigValue<List<? extends Number>> setting, int index) {
        return (SPEC.isLoaded() ? setting.get() : setting.getDefault()).get(index);
    }

    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> upgradeArray(ForgeConfigSpec.Builder builder,
            String key, String description, List<? extends Number> defaults, int min, int max) {
        return builder.comment(description).define(key, defaults, candidate -> candidate instanceof List<?> values
                && values.size() == defaults.size() && values.stream().allMatch(element -> element instanceof Number number
                && number.doubleValue() >= min && number.doubleValue() <= max));
    }

    static void displayItems(CreativeModeTab.Output output) {
        for (Machine machine : Machine.values()) {
            output.accept(machine.item.get());
        }
        output.accept(SPEED_UPGRADE.get());
        output.accept(ENERGY_UPGRADE.get());
    }

    public record TankSlot(int x, int y, int size, boolean output) {}

    public enum Machine {
        MANUFACTORY("manufactory", 1, 1, false, 56, null, 116, 0, 74, 35, 16),
        DECAY_HASTENER("decay_hastener", 1, 1, false, 56, null, 116, 0, 74, 35, 16),
        SEPARATOR("separator", 1, 2, false, 42, null, 102, 28, 60, 34, 18),
        ALLOY_FURNACE("alloy_furnace", 2, 1, true, 46, null, 126, 0, 84, 35, 16),
        INFUSER("infuser", 1, 1, true, 46, new TankSlot(66, 35, 16, false), 126, 0, 84, 35, 16),
        ROCK_CRUSHER("rock_crusher", 1, 3, false, 38, null, 94, 20, 56, 35, 16),
        FUEL_REPROCESSOR("fuel_reprocessor", 1, 8, false, 30, null, 86, 20, 48, 30, 38, 1, 4, 12),
        PRESSURIZER("pressurizer", 1, 1, true, 56, null, 116, 0, 74, 35, 16),
        ASSEMBLER("assembler", 4, 1, false, 46, null, 126, 0, 84, 31, 36, 2, 1, 12),
        EXTRACTOR("extractor", 1, 1, true, 42, new TankSlot(126, 31, 24, true), 102, 0, 60, 34, 18, 1, 1, 12),
        INGOT_FORMER("ingot_former", 0, 1, false, 0, new TankSlot(56, 35, 16, false), 116, 0, 74, 35, 16),
        CRYSTALLIZER("crystallizer", 0, 1, true, 0, new TankSlot(56, 35, 16, false), 116, 0, 74, 35, 16),
        ELECTRIC_FURNACE("electric_furnace", 1, 1, false, 56, null, 116, 0, 74, 35, 16);

        private final String id;
        private final int inputs;
        private final int outputs;
        private final boolean losesProgress;
        private final int inputX;
        private final int inputColumns;
        private final @Nullable TankSlot tank;
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

        Machine(String id, int inputs, int outputs, boolean losesProgress, int inputX, @Nullable TankSlot tank, int outputX,
                int outputSpacing, int progressX, int progressY, int progressHeight) {
            this(id, inputs, outputs, losesProgress, inputX, tank, outputX, outputSpacing, progressX, progressY,
                    progressHeight, inputs, outputs, 0);
        }

        Machine(String id, int inputs, int outputs, boolean losesProgress, int inputX, @Nullable TankSlot tank, int outputX,
                int outputSpacing, int progressX, int progressY, int progressHeight, int inputColumns,
                int outputColumns, int menuExtension) {
            this.id = id;
            this.inputs = inputs;
            this.outputs = outputs;
            this.losesProgress = losesProgress;
            this.inputX = inputX;
            this.inputColumns = inputColumns;
            this.tank = tank;
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
            return inputX + (slot % inputColumns) * 20;
        }

        public int inputY(int slot) {
            return slotY(slot, inputColumns, inputs);
        }

        public boolean hasFluidInput() {
            return tank != null && !tank.output();
        }

        public boolean hasFluidOutput() {
            return tank != null && tank.output();
        }

        public @Nullable TankSlot tank() {
            return tank;
        }

        public int outputX(int slot) {
            return outputX + (slot % outputColumns) * outputSpacing;
        }

        public int outputY(int slot) {
            return slotY(slot, outputColumns, outputs);
        }

        private int slotY(int slot, int columns, int count) {
            return 35 + (hasFluidOutput() ? 0 : menuExtension / 2) + 20 * (slot / columns) - 10 * ((count - 1) / columns);
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
