package nc.processor;

import nc.radiation.ChunkRadiationSource;
import nc.automation.MachineInterfaceTarget;
import nc.init.NCItemProcessors;
import nc.init.NCItemProcessors.Rate;

import nc.init.NCRegistries;
import nc.recipe.CentrifugeRecipe;
import nc.recipe.ChemicalReactorRecipe;
import nc.recipe.ElectrolyzerRecipe;
import nc.recipe.FluidBatch;
import nc.recipe.FluidInput;
import nc.recipe.FluidProcessorRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class FluidProcessorBlockEntity extends UpgradableProcessorBlockEntity implements MachineInterfaceTarget, MenuProvider, ChunkRadiationSource {
    private final Machine machine;
    private final ProcessorRecipeLookup<? extends FluidProcessorRecipe> recipes;
    private final ProcessorInputs input;
    private final ProcessorItemInput itemInput;
    private final ProcessorFluidTanks fluids;

    private LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> power);
    private double radiationEmission;
    private ResourceLocation activeRecipe;

    public FluidProcessorBlockEntity(Machine machine, BlockPos pos, BlockState state) {
        super(machine.entity(), pos, state, machine.rate, 256_000);
        this.machine = machine;
        recipes = new ProcessorRecipeLookup<>(machine.recipes(), FluidProcessorRecipe::acceptsEmptyFluidInputs);
        input = new ProcessorInputs(machine.hasItemInput ? 1 : 0);
        itemInput = new ProcessorItemInput(this, machine.hasItemInput ? 1 : 0, this::isIngredient, this::acceptsItem);
        fluids = new ProcessorFluidTanks(this, this::acceptsFluid, this::canFill, machine.inputs, machine.outputs);
    }

    @Override
    List<? extends FluidProcessorRecipe> availableRecipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(machine.recipes());
    }

    @Override
    ProcessorInputs recipeInput() {
        return new ProcessorInputs(machine.hasItemInput ? 1 : 0, machine.inputs);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FluidProcessorBlockEntity machine) {
        boolean running = machine.process(level, machine.redstoneHalted(level, pos));
        ProcessorBlock.setRunning(level, pos, state, running);
        if (!running && !state.getValue(ProcessorBlock.LIT)) {
            machine.autoEject(level);
        }
    }

    private boolean acceptsFluid(FluidStack stack) {
        return rate.enabled() && level != null && level.getRecipeManager().getAllRecipesFor(machine.recipes()).stream()
                .anyMatch(recipe -> recipe.acceptsFluid(stack));
    }

    private boolean canFill(int tank, FluidStack stack) {
        if (!rate.enabled() || level == null) {
            return false;
        }
        if (!NCItemProcessors.smartProcessorInput() || fluids.stored(tank).isFluidEqual(stack)) {
            return acceptsFluid(stack);
        }
        refreshInput();
        return level.getRecipeManager().getAllRecipesFor(machine.recipes()).stream()
                .anyMatch(recipe -> recipe.canFill(input, tank, stack));
    }

    private boolean isIngredient(ItemStack stack) {
        return level == null || level.isClientSide || level.getRecipeManager().getAllRecipesFor(machine.recipes())
                .stream().anyMatch(recipe -> recipe.acceptsItem(stack));
    }

    private boolean acceptsItem(ItemStack stack) {
        if (!rate.enabled()) {
            return false;
        }
        if (level == null || level.isClientSide) {
            return true;
        }
        if (!NCItemProcessors.smartProcessorInput() || ItemStack.isSameItemSameTags(stack, itemInput.getStackInSlot(0))) {
            return isIngredient(stack);
        }
        refreshInput();
        return level.getRecipeManager().getAllRecipesFor(machine.recipes()).stream()
                .anyMatch(recipe -> recipe.canInsertItem(input, stack));
    }

    public ItemStackHandler inventory() {
        return itemInput;
    }

    @Override
    public double radiationEmission() {
        return radiationEmission;
    }

    private void refreshInput() {
        if (machine.hasItemInput) {
            input.setItem(0, itemInput.getStackInSlot(0));
        }
        input.setFluid(fluids.stored(0));
        if (machine.inputs == 2) {
            input.setSecondFluid(fluids.stored(1));
        }
    }

    private @Nullable FluidProcessorRecipe findRecipe(Level level) {
        return recipes.find(input, level, input.fluid().isEmpty() && input.secondFluid().isEmpty());
    }

    private boolean process(Level level, boolean halted) {
        radiationEmission = 0D;
        if (!rate.enabled()) {
            return false;
        }
        refreshInput();
        FluidProcessorRecipe recipe = findRecipe(level);
        ResourceLocation next = recipe == null ? null : recipe.id();
        if (!Objects.equals(activeRecipe, next)) {
            progressHalfTicks = 0;
            activeRecipe = next;
            setChanged();
        }
        if (recipe == null) {
            updateRates(0, 0, 0);
            return false;
        }
        int factor = recipe.batchFactor();
        double duration = recipe.duration();
        // Keep work in the original recipe's units so factoring does not repeatedly subtract a rounded duration.
        updateRates(duration, rate.time(duration), rate.power(recipe.energyPerTick()));
        if (halted) {
            return false;
        }
        List<FluidStack> outputs = recipe.results();
        double speed = speed();
        double progressPerTick = 2 * speed * factor;
        if (!hasOutputSpace(outputs, factor) || !power.spend(energyPerTick())) {
            if (progressHalfTicks > 0) {
                progressHalfTicks = Math.max(0, progressHalfTicks - 1.5 * progressPerTick);
                setChanged();
            }
            return false;
        }
        radiationEmission = recipe.radiation() * speed;
        progressHalfTicks += progressPerTick;
        while (progressHalfTicks > 0) {
            double batchHalfTicks = 2 * batchTicks;
            // Config multipliers can round the nominal duration. Allow that error across one original batch.
            double rounding = 4 * Math.ulp(batchHalfTicks) * factor;
            if (progressHalfTicks + rounding < batchHalfTicks) break;
            var item = recipe.itemInput();
            if (item != null && machine.hasItemInput) {
                itemInput.extractItem(0, item.matchedCount(input.getItem(0)), false);
            }
            List<FluidInput> ingredients = recipe.inputs();
            boolean inOrder = recipe.inOrder(input, factor);
            for (int index = 0; index < machine.inputs; index++) {
                int ingredient = inOrder ? index : 1 - index;
                fluids.consume(index, ingredients.get(ingredient).matchedAmount(input.fluid(index), factor));
            }
            for (int index = 0; index < outputs.size(); index++) {
                FluidStack product = outputs.get(index);
                if (!product.isEmpty()) {
                    var rolled = recipe.rollResult(index, product, level.random);
                    fluids.produce(index + machine.inputs, FluidBatch.divide(rolled, factor));
                }
            }
            autoEject(level);
            progressHalfTicks = Math.max(0, progressHalfTicks - batchHalfTicks);
            refreshInput();
            var nextRecipe = findRecipe(level);
            if (nextRecipe != recipe) {
                recipe = nextRecipe;
                activeRecipe = recipe == null ? null : recipe.id();
                setBatchRates(recipe == null ? 0 : rate.time(recipe.duration()), recipe == null ? 0 : rate.power(recipe.energyPerTick()));
                outputs = recipe == null ? List.of() : recipe.results();
                if (recipe != null) {
                    int nextFactor = recipe.batchFactor();
                    // Carry paid time into the next recipe's original batch units.
                    progressHalfTicks = progressHalfTicks / factor * nextFactor;
                    factor = nextFactor;
                }
            }
            if (recipe == null || !hasOutputSpace(outputs, factor)) {
                if (fluids.discardsUnusableInputs()) fluids.discardUnusableInputs();
                progressHalfTicks = 0;
                break;
            }
        }
        setChanged();
        return true;
    }

    private boolean hasOutputSpace(List<FluidStack> outputs, int factor) {
        for (int index = 0; index < outputs.size(); index++) {
            FluidStack product = outputs.get(index);
            if (!product.isEmpty() && !fluids.canProduce(index + machine.inputs, FluidBatch.divide(product, factor))) {
                return false;
            }
        }
        return true;
    }

    void cycleSide(int tank, Direction side) {
        fluids.cycle(tank, side);
    }

    @Override
    ProcessorFluidTanks fluidTanks() {
        return fluids;
    }

    @Override
    ProcessorItemPorts itemPorts() {
        return itemInput.ports;
    }

    private String fluidSettingKey(int tank) {
        return (tank < machine.inputs ? "fluid_input_mode_" : "fluid_output_mode_") + tank;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (!isRemoved()) {
            if (capability == ForgeCapabilities.FLUID_HANDLER) {
                return fluids.capability(side).cast();
            }
            if (capability == ForgeCapabilities.ITEM_HANDLER) {
                return itemInput.ports.capability(side).cast();
            }
            if (capability == ForgeCapabilities.ENERGY) {
                return energy.cast();
            }
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluids.invalidate();
        itemInput.ports.invalidate();
        energy.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        fluids.revive();
        itemInput.ports.revive();
        energy = LazyOptional.of(() -> power);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        itemInput.ports.save(tag);
        if (activeRecipe != null) {
            tag.putString("recipe", activeRecipe.toString());
        }
        for (int tank = 0; tank < machine.tanks(); tank++) {
            fluids.saveTank(tank, tag, "fluid_" + tank, "fluid_sides_" + tank, fluidSettingKey(tank));
        }
        fluids.saveSeparation(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        radiationEmission = 0D;
        itemInput.ports.load(tag);
        activeRecipe = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
        for (int tank = 0; tank < machine.tanks(); tank++) {
            fluids.loadTank(tank, tag, "fluid_" + tank, "fluid_sides_" + tank, fluidSettingKey(tank));
        }
        fluids.loadSeparation(tag);
        fluids.refreshFaces();
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public FluidProcessorMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new FluidProcessorMenu(id, playerInventory, machine, new ContainerData() {
            @Override
            public int get(int index) {
                if (index == FluidProcessorMenu.PROGRESS) {
                    return progress();
                }
                if (index == FluidProcessorMenu.ENERGY) {
                    return (int) (1000L * power.getEnergyStored() / power.getMaxEnergyStored());
                }
                if (index == FluidProcessorMenu.separationIndex(machine)) {
                    return fluids.separatesInputs() ? 1 : 0;
                }
                if (index == FluidProcessorMenu.redstoneIndex(machine)) {
                    return redstoneControl() ? 1 : 0;
                }
                int tank = (index - FluidProcessorMenu.FIRST_TANK) / FluidProcessorMenu.TANK_DATA_SIZE;
                return switch ((index - FluidProcessorMenu.FIRST_TANK) % FluidProcessorMenu.TANK_DATA_SIZE) {
                    case FluidProcessorMenu.FLUID_ID -> BuiltInRegistries.FLUID.getId(fluids.stored(tank).getFluid());
                    case FluidProcessorMenu.AMOUNT -> fluids.stored(tank).getAmount();
                    case FluidProcessorMenu.SIDE_MODE -> fluids.sideModes(tank);
                    case FluidProcessorMenu.OUTPUT_MODE -> fluids.setting(tank);
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return FluidProcessorMenu.dataCount(machine);
            }
        }, ContainerLevelAccess.create(player.level(), worldPosition), this);
    }

    public enum Machine {
        ELECTROLYZER(1, ElectrolyzerRecipe.OUTPUTS),
        SALT_MIXER(2, 1),
        CHEMICAL_REACTOR(2, ChemicalReactorRecipe.OUTPUTS),
        ENRICHER(1, 1, true),
        SUPERCOOLER(1, 1),
        CENTRIFUGE(1, CentrifugeRecipe.OUTPUTS);

        private final int inputs;
        private final int outputs;
        private final boolean hasItemInput;
        private final Rate rate = Rate.valueOf(name());

        Machine(int inputs, int outputs) {
            this(inputs, outputs, false);
        }

        Machine(int inputs, int outputs, boolean hasItemInput) {
            this.inputs = inputs;
            this.outputs = outputs;
            this.hasItemInput = hasItemInput;
        }

        public boolean hasItemInput() {
            return hasItemInput;
        }

        public int inputs() {
            return inputs;
        }

        public int tanks() {
            return inputs + outputs;
        }

        public Block block() {
            return switch (this) {
                case ELECTROLYZER -> NCRegistries.ELECTROLYZER.get();
                case SALT_MIXER -> NCRegistries.SALT_MIXER.get();
                case CHEMICAL_REACTOR -> NCRegistries.CHEMICAL_REACTOR.get();
                case ENRICHER -> NCRegistries.ENRICHER.get();
                case SUPERCOOLER -> NCRegistries.SUPERCOOLER.get();
                case CENTRIFUGE -> NCRegistries.CENTRIFUGE.get();
            };
        }

        BlockEntityType<FluidProcessorBlockEntity> entity() {
            return switch (this) {
                case ELECTROLYZER -> NCRegistries.ELECTROLYZER_ENTITY.get();
                case SALT_MIXER -> NCRegistries.SALT_MIXER_ENTITY.get();
                case CHEMICAL_REACTOR -> NCRegistries.CHEMICAL_REACTOR_ENTITY.get();
                case ENRICHER -> NCRegistries.ENRICHER_ENTITY.get();
                case SUPERCOOLER -> NCRegistries.SUPERCOOLER_ENTITY.get();
                case CENTRIFUGE -> NCRegistries.CENTRIFUGE_ENTITY.get();
            };
        }

        MenuType<FluidProcessorMenu> menu() {
            return switch (this) {
                case ELECTROLYZER -> NCRegistries.ELECTROLYZER_MENU.get();
                case SALT_MIXER -> NCRegistries.SALT_MIXER_MENU.get();
                case CHEMICAL_REACTOR -> NCRegistries.CHEMICAL_REACTOR_MENU.get();
                case ENRICHER -> NCRegistries.ENRICHER_MENU.get();
                case SUPERCOOLER -> NCRegistries.SUPERCOOLER_MENU.get();
                case CENTRIFUGE -> NCRegistries.CENTRIFUGE_MENU.get();
            };
        }

        public Rate rate() {
            return rate;
        }

        public RecipeType<? extends FluidProcessorRecipe> recipes() {
            return switch (this) {
                case ELECTROLYZER -> NCRegistries.ELECTROLYSIS.get();
                case SALT_MIXER -> NCRegistries.MIXING.get();
                case CHEMICAL_REACTOR -> NCRegistries.CHEMICAL_REACTIONS.get();
                case ENRICHER -> NCRegistries.ENRICHING.get();
                case SUPERCOOLER -> NCRegistries.SUPERCOOLING.get();
                case CENTRIFUGE -> NCRegistries.CENTRIFUGING.get();
            };
        }
    }

}
