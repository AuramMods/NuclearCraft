package nc.init;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.NuclearCraft;
import nc.init.NCItemProcessors.TankSlot;
import nc.processor.ItemProcessorBlockEntity;
import nc.processor.ItemProcessorMenu;
import nc.processor.ProcessorBlock;
import nc.processor.ProcessorFluidTanks;
import nc.processor.ProcessorLayout;
import nc.processor.ProcessorProfile;
import nc.recipe.ItemProcessorRecipe;
import net.minecraft.util.GsonHelper;
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
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;

final class NCScriptedProcessors {
    private static final List<Definition> PROFILES = new ArrayList<>();

    private NCScriptedProcessors() {}

    static void register() {
        var path = FMLPaths.CONFIGDIR.get().resolve("nuclearcraft-processors.json");
        try (var reader = Files.newBufferedReader(path)) {
            for (var entry : GsonHelper.parse(reader).entrySet()) {
                String id = entry.getKey();
                if (!id.matches("[a-z0-9_]+")) {
                    throw new JsonSyntaxException("Invalid scripted processor ID " + id);
                }
                PROFILES.add(new Definition(id, GsonHelper.convertToJsonObject(entry.getValue(), id)));
            }
        } catch (NoSuchFileException absent) {
            // Processor definitions are optional.
        } catch (IOException | IllegalArgumentException | JsonSyntaxException error) {
            throw new IllegalArgumentException("Unable to register processors from " + path, error);
        }
    }

    static List<? extends ProcessorProfile> profiles() {
        return List.copyOf(PROFILES);
    }

    static void displayItems(CreativeModeTab.Output output) {
        for (Definition profile : PROFILES) {
            output.accept(profile.item.get());
        }
    }

    private static int size(JsonObject json, String field) {
        int size = GsonHelper.getAsInt(json, field, 0);
        if (size < 0 || size > 8) {
            throw new JsonSyntaxException(field + " must be between 0 and 8");
        }
        return size;
    }

    private static int capacity(JsonObject json, String field) {
        int capacity = GsonHelper.getAsInt(json, field, ProcessorFluidTanks.CAPACITY);
        if (capacity <= 0) {
            throw new JsonSyntaxException(field + " must be positive");
        }
        return capacity;
    }

    private record ConfiguredRates(double stockTime, double stockPower) implements ProcessorProfile.Rates {
        @Override
        public double defaultTime() {
            return stockTime;
        }

        @Override
        public double baseTime() {
            return time(stockTime);
        }

        @Override
        public double basePower() {
            return stockPower * powerScale();
        }

        @Override
        public double time(double ticks) {
            return ticks * NCItemProcessors.timeMultiplier();
        }

        @Override
        public double power(int energyPerTick) {
            return energyPerTick * powerScale();
        }

        @Override
        public double powerScale() {
            return NCItemProcessors.powerMultiplier();
        }
    }

    private static final class Definition implements ProcessorProfile {
        private final String id;
        private final ConfiguredRates rates;
        private final int itemInputs;
        private final int itemOutputs;
        private final int fluidInputs;
        private final int fluidOutputs;
        private final List<TankSlot> tanks;
        private final int inputCapacity;
        private final int outputCapacity;
        private final ProcessorLayout layout;
        private final boolean upgradable;
        private final boolean generator;
        private final boolean consumesInputs;
        private final boolean losesProgress;
        private final boolean jeiEnabled;
        private final boolean redstoneControl;
        private final RegistryObject<ProcessorBlock<ItemProcessorBlockEntity>> block;
        private final RegistryObject<Item> item;
        private final RegistryObject<BlockEntityType<ItemProcessorBlockEntity>> entity;
        private final RegistryObject<MenuType<ItemProcessorMenu>> menu;
        private final RegistryObject<RecipeType<ItemProcessorRecipe>> recipes;
        private final RegistryObject<RecipeSerializer<ItemProcessorRecipe>> serializer;

        Definition(String id, JsonObject json) {
            this.id = id;
            itemInputs = size(json, "item_inputs");
            itemOutputs = size(json, "item_outputs");
            fluidInputs = size(json, "fluid_inputs");
            fluidOutputs = size(json, "fluid_outputs");
            generator = GsonHelper.getAsBoolean(json, "generator", false);
            consumesInputs = GsonHelper.getAsBoolean(json, "consumes_inputs", generator);
            if (!generator && itemOutputs + fluidOutputs == 0) {
                throw new JsonSyntaxException("A processor must have an output");
            }
            inputCapacity = capacity(json, "input_tank_capacity");
            outputCapacity = capacity(json, "output_tank_capacity");
            double time = GsonHelper.getAsDouble(json, "process_time", 200);
            double power = GsonHelper.getAsDouble(json, "process_power", 0);
            if (!Double.isFinite(time) || time <= 0 || !Double.isFinite(power) || power < 0) {
                throw new JsonSyntaxException("Invalid processor rates");
            }
            rates = new ConfiguredRates(time, power);
            upgradable = GsonHelper.getAsBoolean(json, "upgradable", false);
            losesProgress = GsonHelper.getAsBoolean(json, "loses_progress", false);
            jeiEnabled = GsonHelper.getAsBoolean(json, "jei_enabled", true);
            redstoneControl = GsonHelper.getAsBoolean(json, "redstone_control", true);

            layout = ProcessorLayout.read(id, json, itemInputs, itemOutputs, fluidInputs, fluidOutputs, upgradable);
            var particleNames = new ArrayList<String>();
            if (json.has("particles")) {
                for (var element : GsonHelper.getAsJsonArray(json, "particles")) {
                    String particle = GsonHelper.convertToString(element, "particle").strip();
                    if (!particle.isEmpty()) {
                        particleNames.add(particle);
                    }
                }
            }
            List<String> particles = List.copyOf(particleNames);
            List<TankSlot> tankSlots = new ArrayList<>(fluidInputs + fluidOutputs);
            addTanks(tankSlots, layout.fluidInputs(), false);
            addTanks(tankSlots, layout.fluidOutputs(), true);
            tanks = List.copyOf(tankSlots);

            block = NCRegistries.BLOCKS.register(id, () -> new ProcessorBlock<>(
                    BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                            .lightLevel(state -> state.getValue(ProcessorBlock.LIT) ? 7 : 0),
                    this::entityType, ItemProcessorBlockEntity::tick, ItemProcessorBlockEntity::inventory, particles));
            item = NCRegistries.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
            entity = NCRegistries.BLOCK_ENTITIES.register(id, () -> BlockEntityType.Builder.of(
                    (pos, state) -> new ItemProcessorBlockEntity(pos, state, this), block.get()).build(null));
            menu = NCRegistries.MENUS.register(id,
                    () -> new MenuType<>((containerId, player) -> new ItemProcessorMenu(containerId, player, this),
                            FeatureFlags.DEFAULT_FLAGS));
            recipes = NCRegistries.RECIPE_TYPES.register(id, () -> RecipeType.simple(NuclearCraft.id(id)));
            serializer = NCRegistries.RECIPE_SERIALIZERS.register(id, () -> new ItemProcessorRecipe.Serializer(this));
        }

        private static void addTanks(List<TankSlot> tanks, List<ProcessorLayout.Rect> slots, boolean output) {
            for (var slot : slots) {
                tanks.add(new TankSlot(slot.x(), slot.y(), slot.width(), slot.height(), output));
            }
        }

        @Override public String id() { return id; }
        @Override public ProcessorProfile.Rates rate() { return rates; }
        @Override public int inputs() { return itemInputs; }
        @Override public int outputs() { return itemOutputs; }
        @Override public int inputX(int slot) { return layout.itemInputs().get(slot).itemX(); }
        @Override public int inputY(int slot) { return layout.itemInputs().get(slot).itemY(); }
        @Override public int outputX(int slot) { return layout.itemOutputs().get(slot).itemX(); }
        @Override public int outputY(int slot) { return layout.itemOutputs().get(slot).itemY(); }
        @Override public int menuExtension() { return layout.size().y() - 166; }
        @Override public int progressX() { return layout.progress().area().x(); }
        @Override public int progressY() { return layout.progress().area().y(); }
        @Override public int progressHeight() { return layout.progress().area().height(); }
        @Override public boolean losesProgress() { return losesProgress; }
        @Override public boolean hasUpgrades() { return upgradable; }
        @Override public boolean generator() { return generator; }
        @Override public boolean consumesInputs() { return consumesInputs; }
        @Override public boolean scripted() { return true; }
        @Override public boolean jeiEnabled() { return jeiEnabled; }
        @Override public ProcessorLayout layout() { return layout; }
        @Override public boolean defaultRedstoneControl() { return redstoneControl; }
        @Override public @Nullable TankSlot tank() { return tanks.isEmpty() ? null : tanks.get(0); }
        @Override public List<TankSlot> tanks() { return tanks; }
        @Override public int fluidInputs() { return fluidInputs; }
        @Override public int fluidOutputs() { return fluidOutputs; }
        @Override public boolean hasFluidInput() { return fluidInputs > 0; }
        @Override public boolean hasFluidOutput() { return fluidOutputs > 0; }
        @Override public int tankCapacity(int index) { return index < fluidInputs ? inputCapacity : outputCapacity; }
        @Override public ProcessorBlock<ItemProcessorBlockEntity> block() { return block.get(); }
        @Override public BlockEntityType<ItemProcessorBlockEntity> entityType() { return entity.get(); }
        @Override public MenuType<ItemProcessorMenu> menu() { return menu.get(); }
        @Override public RecipeType<ItemProcessorRecipe> recipes() { return recipes.get(); }
        @Override public RecipeSerializer<ItemProcessorRecipe> serializer() { return serializer.get(); }
    }
}
