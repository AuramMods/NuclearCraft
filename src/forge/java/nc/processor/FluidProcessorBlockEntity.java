package nc.processor;

import nc.init.NCRegistries;
import nc.recipe.CentrifugeRecipe;
import nc.recipe.ChemicalReactorRecipe;
import nc.recipe.ElectrolyzerRecipe;
import nc.recipe.FluidIngredient;
import nc.recipe.FluidProcessorRecipe;
import nc.processor.ProcessorFluidTanks.OutputMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class FluidProcessorBlockEntity extends BlockEntity implements MenuProvider {
    private final Machine machine;
    private final RecipeManager.CachedCheck<Container, ? extends FluidProcessorRecipe> recipes;
    private final ProcessorInputs input;
    private final @Nullable FluidProcessorItemInput itemInput;
    private final ProcessorFluidTanks fluids;
    private final ProcessorEnergy power = new ProcessorEnergy(this, 256_000);
    private final OutputMode[] outputModes;

    private LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> power);
    private ResourceLocation activeRecipe;
    private double progressHalfTicks;
    private double recipeTicks;

    public FluidProcessorBlockEntity(Machine machine, BlockPos pos, BlockState state) {
        super(machine.entity(), pos, state);
        this.machine = machine;
        recipes = RecipeManager.createCheck(machine.recipes());
        input = new ProcessorInputs(machine.hasItemInput ? 1 : 0);
        itemInput = machine.hasItemInput ? new FluidProcessorItemInput(this, this::acceptsItem) : null;
        fluids = new ProcessorFluidTanks(this, this::acceptsFluid, machine.inputs, machine.outputs);
        outputModes = new OutputMode[machine.outputs];
        Arrays.fill(outputModes, OutputMode.HOLD);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FluidProcessorBlockEntity machine) {
        boolean running = machine.process(level, level.hasNeighborSignal(pos));
        ProcessorBlock.setRunning(level, pos, state, running);
        machine.fluids.push(level);
    }

    private boolean acceptsFluid(FluidStack stack) {
        return level != null && level.getRecipeManager().getAllRecipesFor(machine.recipes()).stream()
                .anyMatch(recipe -> recipe.inputs().stream().anyMatch(ingredient -> ingredient.accepts(stack)));
    }

    private boolean acceptsItem(ItemStack stack) {
        return level == null || level.isClientSide || level.getRecipeManager().getAllRecipesFor(machine.recipes())
                .stream().anyMatch(recipe -> recipe.acceptsItem(stack));
    }

    public ItemStackHandler inventory() {
        return Objects.requireNonNull(itemInput);
    }

    private boolean process(Level level, boolean halted) {
        if (itemInput != null) {
            input.setItem(0, itemInput.getStackInSlot(0));
        }
        input.setFluid(fluids.stored(0));
        if (machine.inputs == 2) {
            input.setSecondFluid(fluids.stored(1));
        }
        FluidProcessorRecipe recipe = input.fluid().isEmpty() ? null : recipes.getRecipeFor(input, level).orElse(null);
        ResourceLocation next = recipe == null ? null : recipe.id();
        if (!Objects.equals(activeRecipe, next)) {
            progressHalfTicks = 0;
            activeRecipe = next;
            setChanged();
        }
        recipeTicks = recipe == null ? 0 : recipe.duration();
        if (recipe == null || halted) {
            return false;
        }
        List<FluidStack> outputs = recipe.results();
        if (!hasOutputSpace(outputs) || !power.spend(recipe.energyPerTick())) {
            if (progressHalfTicks > 0) {
                progressHalfTicks = Math.max(0, progressHalfTicks - 3);
                setChanged();
            }
            return false;
        }
        progressHalfTicks += 2;
        double batchHalfTicks = 2 * recipeTicks;
        if (progressHalfTicks >= batchHalfTicks) {
            var item = recipe.itemInput();
            if (item != null && itemInput != null) {
                itemInput.extractItem(0, item.count(), false);
            }
            List<FluidIngredient> ingredients = recipe.inputs();
            boolean inOrder = recipe.inOrder(input);
            for (int index = 0; index < machine.inputs; index++) {
                int ingredient = inOrder ? index : 1 - index;
                fluids.consume(index, ingredients.get(ingredient).amount());
            }
            for (int index = 0; index < outputs.size(); index++) {
                FluidStack product = outputs.get(index);
                if (!product.isEmpty()) {
                    fluids.produce(index + machine.inputs, recipe.rollResult(index, product, level.random), outputModes[index]);
                }
            }
            double remainder = progressHalfTicks - batchHalfTicks;
            progressHalfTicks = remainder < 2 ? remainder : 0;
        }
        setChanged();
        return true;
    }

    private boolean hasOutputSpace(List<FluidStack> outputs) {
        for (int index = 0; index < outputs.size(); index++) {
            FluidStack product = outputs.get(index);
            if (!product.isEmpty() && !fluids.canProduce(index + machine.inputs, product, outputModes[index])) {
                return false;
            }
        }
        return true;
    }

    void cycleSide(int tank, Direction side) {
        fluids.cycle(tank, side);
        setChanged();
        if (level != null) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    void toggleItemSide(Direction side) {
        if (itemInput != null) {
            itemInput.toggle(side);
            setChanged();
            if (level != null) {
                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            }
        }
    }

    void cycleOutputMode(int tank) {
        OutputMode next = outputModes[tank - machine.inputs].next();
        outputModes[tank - machine.inputs] = next;
        if (next == OutputMode.VOID) {
            fluids.clear(tank);
        }
        setChanged();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (!isRemoved()) {
            if (capability == ForgeCapabilities.FLUID_HANDLER) {
                return fluids.capability(side).cast();
            }
            if (capability == ForgeCapabilities.ITEM_HANDLER && itemInput != null) {
                return itemInput.capability(side).cast();
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
        if (itemInput != null) {
            itemInput.invalidate();
        }
        energy.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        fluids.revive();
        if (itemInput != null) {
            itemInput.revive();
        }
        energy = LazyOptional.of(() -> power);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (itemInput != null) {
            itemInput.save(tag);
        }
        tag.putInt("energy", power.getEnergyStored());
        long wholeTicks = (long) progressHalfTicks;
        tag.putLong("progress_half_ticks", wholeTicks);
        if (progressHalfTicks > wholeTicks) {
            tag.putDouble("progress_fraction", progressHalfTicks - wholeTicks);
        }
        if (activeRecipe != null) {
            tag.putString("recipe", activeRecipe.toString());
        }
        for (int tank = 0; tank < machine.tanks(); tank++) {
            fluids.saveTank(tank, tag, "fluid_" + tank, "fluid_sides_" + tank);
            if (tank >= machine.inputs) {
                tag.putInt("fluid_output_mode_" + tank, outputModes[tank - machine.inputs].ordinal());
            }
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (itemInput != null) {
            itemInput.load(tag);
        }
        power.load(tag.getInt("energy"));
        double fraction = tag.getDouble("progress_fraction");
        progressHalfTicks = Math.max(0, tag.getLong("progress_half_ticks")) + (fraction > 0 && fraction < 1 ? fraction : 0);
        activeRecipe = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
        for (int tank = 0; tank < machine.tanks(); tank++) {
            fluids.loadTank(tank, tag, "fluid_" + tank, "fluid_sides_" + tank);
            if (tank >= machine.inputs) {
                outputModes[tank - machine.inputs] = OutputMode.byId(tag.getInt("fluid_output_mode_" + tank));
            }
        }
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
                    return recipeTicks == 0 ? 0 : (int) (1000L * Math.min(progressHalfTicks, 2D * recipeTicks) / (2D * recipeTicks));
                }
                if (index == FluidProcessorMenu.ENERGY) {
                    return (int) (1000L * power.getEnergyStored() / power.getMaxEnergyStored());
                }
                if (itemInput != null && index == FluidProcessorMenu.itemSidesIndex(machine)) {
                    return itemInput.disabledSides();
                }
                int tank = (index - FluidProcessorMenu.FIRST_TANK) / FluidProcessorMenu.TANK_DATA_SIZE;
                return switch ((index - FluidProcessorMenu.FIRST_TANK) % FluidProcessorMenu.TANK_DATA_SIZE) {
                    case FluidProcessorMenu.FLUID_ID -> BuiltInRegistries.FLUID.getId(fluids.stored(tank).getFluid());
                    case FluidProcessorMenu.AMOUNT -> fluids.stored(tank).getAmount();
                    case FluidProcessorMenu.SIDE_MODE -> fluids.sideModes(tank);
                    case FluidProcessorMenu.OUTPUT_MODE -> tank < machine.inputs ? 0 : outputModes[tank - machine.inputs].ordinal();
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

        Block block() {
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

        RecipeType<? extends FluidProcessorRecipe> recipes() {
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
