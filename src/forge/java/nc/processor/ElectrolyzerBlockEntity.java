package nc.processor;

import nc.init.NCRegistries;
import nc.recipe.ElectrolyzerRecipe;
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
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class ElectrolyzerBlockEntity extends BlockEntity implements MenuProvider {
    private final RecipeManager.CachedCheck<Container, ElectrolyzerRecipe> recipes =
            RecipeManager.createCheck(NCRegistries.ELECTROLYSIS.get());
    private final ProcessorInputs input = new ProcessorInputs(0);
    private final ProcessorFluidTanks fluids = new ProcessorFluidTanks(this, this::acceptsFluid, ElectrolyzerRecipe.OUTPUTS);
    private final ProcessorEnergy power = new ProcessorEnergy(this, 256_000);
    private final OutputMode[] outputModes = new OutputMode[ElectrolyzerRecipe.OUTPUTS];

    private LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> power);
    private ResourceLocation activeRecipe;
    private long progressHalfTicks;
    private int recipeTicks;

    public ElectrolyzerBlockEntity(BlockPos pos, BlockState state) {
        super(NCRegistries.ELECTROLYZER_ENTITY.get(), pos, state);
        Arrays.fill(outputModes, OutputMode.HOLD);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ElectrolyzerBlockEntity machine) {
        boolean running = machine.process(level, level.hasNeighborSignal(pos));
        ProcessorBlock.setRunning(level, pos, state, running);
        machine.fluids.push(level);
    }

    private boolean acceptsFluid(FluidStack stack) {
        return level != null && level.getRecipeManager().getAllRecipesFor(NCRegistries.ELECTROLYSIS.get()).stream()
                .anyMatch(recipe -> recipe.input().accepts(stack));
    }

    private boolean process(Level level, boolean halted) {
        input.setFluid(fluids.stored(0));
        ElectrolyzerRecipe recipe = input.fluid().isEmpty() ? null : recipes.getRecipeFor(input, level).orElse(null);
        ResourceLocation next = recipe == null ? null : recipe.id();
        if (!Objects.equals(activeRecipe, next)) {
            progressHalfTicks = 0;
            activeRecipe = next;
            setChanged();
        }
        recipeTicks = recipe == null ? 0 : recipe.ticks();
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
        if (progressHalfTicks >= 2L * recipe.ticks()) {
            fluids.consume(recipe.input().amount());
            for (int index = 0; index < outputs.size(); index++) {
                if (outputModes[index] != OutputMode.VOID) {
                    fluids.produce(index + 1, outputs.get(index));
                }
            }
            progressHalfTicks = 0;
        }
        setChanged();
        return true;
    }

    private boolean hasOutputSpace(List<FluidStack> outputs) {
        for (int index = 0; index < outputs.size(); index++) {
            FluidStack product = outputs.get(index);
            FluidStack stored = fluids.stored(index + 1);
            if (outputModes[index] == OutputMode.VOID) {
                continue;
            }
            if ((!stored.isEmpty() && !stored.isFluidEqual(product))
                    || (outputModes[index] == OutputMode.HOLD && !fluids.canProduce(index + 1, product))) {
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

    void cycleOutputMode(int tank) {
        OutputMode next = outputModes[tank - 1].next();
        outputModes[tank - 1] = next;
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
        energy.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        fluids.revive();
        energy = LazyOptional.of(() -> power);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("energy", power.getEnergyStored());
        tag.putLong("progress_half_ticks", progressHalfTicks);
        if (activeRecipe != null) {
            tag.putString("recipe", activeRecipe.toString());
        }
        for (int tank = 0; tank < ElectrolyzerMenu.TANK_COUNT; tank++) {
            fluids.saveTank(tank, tag, "fluid_" + tank, "fluid_sides_" + tank);
            if (tank > 0) {
                tag.putInt("fluid_output_mode_" + tank, outputModes[tank - 1].ordinal());
            }
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        power.load(tag.getInt("energy"));
        progressHalfTicks = Math.max(0, tag.getLong("progress_half_ticks"));
        activeRecipe = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
        for (int tank = 0; tank < ElectrolyzerMenu.TANK_COUNT; tank++) {
            fluids.loadTank(tank, tag, "fluid_" + tank, "fluid_sides_" + tank);
            if (tank > 0) {
                outputModes[tank - 1] = OutputMode.byId(tag.getInt("fluid_output_mode_" + tank));
            }
        }
        fluids.refreshFaces();
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public ElectrolyzerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new ElectrolyzerMenu(id, playerInventory, new ContainerData() {
            @Override
            public int get(int index) {
                if (index == ElectrolyzerMenu.PROGRESS) {
                    return recipeTicks == 0 ? 0 : (int) (1000L * Math.min(progressHalfTicks, 2L * recipeTicks) / (2L * recipeTicks));
                }
                if (index == ElectrolyzerMenu.ENERGY) {
                    return (int) (1000L * power.getEnergyStored() / power.getMaxEnergyStored());
                }
                int tank = (index - ElectrolyzerMenu.FIRST_TANK) / ElectrolyzerMenu.TANK_DATA_SIZE;
                return switch ((index - ElectrolyzerMenu.FIRST_TANK) % ElectrolyzerMenu.TANK_DATA_SIZE) {
                    case ElectrolyzerMenu.FLUID_ID -> BuiltInRegistries.FLUID.getId(fluids.stored(tank).getFluid());
                    case ElectrolyzerMenu.AMOUNT -> fluids.stored(tank).getAmount();
                    case ElectrolyzerMenu.SIDE_MODE -> fluids.sideModes(tank);
                    case ElectrolyzerMenu.OUTPUT_MODE -> tank == 0 ? 0 : outputModes[tank - 1].ordinal();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return ElectrolyzerMenu.DATA_COUNT;
            }
        }, ContainerLevelAccess.create(player.level(), worldPosition), this);
    }

    public enum OutputMode {
        HOLD("hold"), VOID_EXCESS("void_excess"), VOID("void");

        private static final OutputMode[] MODES = values();
        private final String key;

        OutputMode(String key) {
            this.key = key;
        }

        private OutputMode next() {
            return MODES[(ordinal() + 1) % MODES.length];
        }

        static OutputMode byId(int index) {
            return index >= 0 && index < MODES.length ? MODES[index] : HOLD;
        }

        public Component label() {
            return Component.translatable("gui.nuclearcraft.fluid_output." + key);
        }
    }
}
