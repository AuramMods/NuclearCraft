package nc.processor;

import nc.radiation.ChunkRadiationSource;
import nc.automation.MachineInterfaceTarget;
import nc.init.NCItemProcessors.Rate;

import nc.init.NCRegistries;
import nc.recipe.MelterRecipe;
import nc.recipe.MelterMaterialRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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

public final class MelterBlockEntity extends UpgradableProcessorBlockEntity implements MachineInterfaceTarget, MenuProvider, ChunkRadiationSource {
    private static final int ENERGY_CAPACITY = 512_000;
    private static final int PROGRESS_SCALE = 1000;

    private final ProcessorRecipeLookup<MelterRecipe> recipes =
            new ProcessorRecipeLookup<>(NCRegistries.MELTING.get(), recipe -> recipe.input().matches(ItemStack.EMPTY));
    private final SimpleContainer recipeInput = new SimpleContainer(1);
    private final ProcessorItemInput inventory = new ProcessorItemInput(this, 1, this::acceptsItem);
    private final ProcessorFluidTanks fluids = new ProcessorFluidTanks(this, 1);

    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(() -> power);
    private double radiationEmission;
    private ResourceLocation activeRecipe;

    public MelterBlockEntity(BlockPos pos, BlockState state) {
        super(NCRegistries.MELTER_ENTITY.get(), pos, state, Rate.MELTER, ENERGY_CAPACITY);
    }

    ItemStackHandler inventory() {
        return inventory;
    }

    private boolean acceptsItem(ItemStack stack) {
        if (!rate.enabled()) {
            return false;
        }
        if (level == null || level.isClientSide) {
            return true;
        }
        var manager = level.getRecipeManager();
        return manager.getAllRecipesFor(NCRegistries.MELTING.get()).stream().anyMatch(recipe -> recipe.input().accepts(stack))
                || MelterMaterialRecipes.find(manager, stack) != null;
    }

    @Override
    ProcessorItemPorts itemPorts() {
        return inventory.ports;
    }

    @Override
    ProcessorFluidTanks fluidTanks() {
        return fluids;
    }

    @Override
    List<MelterRecipe> availableRecipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(NCRegistries.MELTING.get());
    }

    @Override
    ProcessorInputs recipeInput() {
        return new ProcessorInputs(1, 0);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MelterBlockEntity melter) {
        boolean processing = melter.process(level, melter.redstoneHalted(level, pos));
        ProcessorBlock.setRunning(level, pos, state, processing);
        melter.fluids.push(level);
        if (!processing) {
            melter.inventory.ports.push(level);
        }
    }

    @Override
    public double radiationEmission() {
        return radiationEmission;
    }

    private boolean process(Level level, boolean halted) {
        radiationEmission = 0D;
        if (!rate.enabled()) {
            return false;
        }
        recipeInput.setItem(0, inventory.getStackInSlot(0));
        MelterRecipe recipe = recipes.find(recipeInput, level, recipeInput.isEmpty());
        if (recipe == null && !recipeInput.isEmpty()) {
            recipe = MelterMaterialRecipes.find(level.getRecipeManager(), recipeInput.getItem(0));
        }
        if (recipe == null || !recipe.id().equals(activeRecipe)) {
            activeRecipe = recipe == null ? null : recipe.id();
            if (progressHalfTicks != 0) {
                progressHalfTicks = 0;
                setChanged();
            }
        }
        if (recipe == null) {
            updateRates(0, 0, 0);
            return false;
        }
        updateRates(recipe.ticks(), rate.time(recipe.ticks()), rate.power(recipe.energyPerTick()));
        if (halted) {
            return false;
        }
        FluidStack product = recipe.product();
        double speed = speed();
        if (!hasOutputSpace(product) || !power.spend(energyPerTick())) {
            if (progressHalfTicks > 0) {
                progressHalfTicks = Math.max(0, progressHalfTicks - 3 * speed);
                setChanged();
            }
            return false;
        }

        radiationEmission = recipe.radiation() * speed;
        progressHalfTicks += 2 * speed;
        double batch = 2D * batchTicks;
        while (progressHalfTicks + 1E-9 >= batch && progressHalfTicks > 0) {
            if (!product.isEmpty()) {
                fluids.produce(0, recipe.fluidProduct().roll(level.random));
            }
            inventory.extractItem(0, recipe.input().matchedCount(recipeInput.getItem(0)), false);
            inventory.ports.push(level);
            progressHalfTicks = Math.max(0, progressHalfTicks - batch);
            if (progressHalfTicks > 0) {
                recipeInput.setItem(0, inventory.getStackInSlot(0));
                if (!recipe.matches(recipeInput, level) || !hasOutputSpace(product)) {
                    progressHalfTicks = 0;
                }
            }
        }
        setChanged();
        return true;
    }

    private boolean hasOutputSpace(FluidStack product) {
        return product.isEmpty() || fluids.canProduce(0, product);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        inventory.ports.save(tag);
        fluids.saveTank(0, tag, "output", "fluid_sides", "fluid_output_mode");
        if (activeRecipe != null) {
            tag.putString("recipe", activeRecipe.toString());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        radiationEmission = 0D;
        inventory.ports.load(tag);
        fluids.loadTank(0, tag, "output", "fluid_sides", "fluid_output_mode");
        fluids.refreshFaces();
        activeRecipe = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (!isRemoved()) {
            if (capability == ForgeCapabilities.ITEM_HANDLER) {
                return inventory.ports.capability(side).cast();
            }
            if (capability == ForgeCapabilities.FLUID_HANDLER) {
                return fluids.capability(side).cast();
            }
            if (capability == ForgeCapabilities.ENERGY) {
                return energyCapability.cast();
            }
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        inventory.ports.invalidate();
        fluids.invalidate();
        energyCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        inventory.ports.revive();
        fluids.revive();
        energyCapability = LazyOptional.of(() -> power);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public MelterMenu createMenu(int menuId, Inventory playerInventory, Player player) {
        return new MelterMenu(menuId, playerInventory, inventory, upgrades, this, new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case MelterMenu.PROGRESS -> progress();
                    case MelterMenu.ENERGY -> (int) ((long) PROGRESS_SCALE * power.getEnergyStored() / power.getMaxEnergyStored());
                    case MelterMenu.FLUID_AMOUNT -> fluids.stored(0).getAmount();
                    case MelterMenu.FLUID_ID -> BuiltInRegistries.FLUID.getId(fluids.stored(0).getFluid());
                    case MelterMenu.FLUID_SIDES -> fluids.sideModes(0);
                    case MelterMenu.FLUID_OUTPUT_MODE -> fluids.setting(0);
                    case MelterMenu.REDSTONE -> redstoneControl() ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return MelterMenu.DATA_COUNT;
            }
        }, ContainerLevelAccess.create(player.level(), worldPosition));
    }

}
