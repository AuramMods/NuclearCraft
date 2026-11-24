package nc.processor;

import nc.radiation.ChunkRadiationSource;
import nc.automation.MachineInterface;
import nc.automation.MachineInterfaceTarget;

import nc.init.NCRegistries;
import nc.recipe.MelterRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MelterBlockEntity extends UpgradableProcessorBlockEntity implements MachineInterfaceTarget, MenuProvider, ChunkRadiationSource {
    static final int TANK_CAPACITY = 16_000;
    private static final int ENERGY_CAPACITY = 512_000;
    private static final int PROGRESS_SCALE = 1000;

    private final RecipeManager.CachedCheck<Container, MelterRecipe> recipes =
            RecipeManager.createCheck(NCRegistries.MELTING.get());
    private final SimpleContainer recipeInput = new SimpleContainer(1);
    private final ProcessorItemInput inventory = new ProcessorItemInput(this, 1, stack -> true);
    private final OutputTank tank = new OutputTank();

    private LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> tank);
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(() -> power);
    private double radiationEmission;
    private ResourceLocation activeRecipe;
    private int recipeDuration;

    public MelterBlockEntity(BlockPos pos, BlockState state) {
        super(NCRegistries.MELTER_ENTITY.get(), pos, state, ENERGY_CAPACITY);
    }

    ItemStackHandler inventory() {
        return inventory;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MelterBlockEntity melter) {
        boolean processing = melter.process(level, MachineInterface.powered(level, pos, melter));
        ProcessorBlock.setRunning(level, pos, state, processing);
    }

    @Override
    public double radiationEmission() {
        return radiationEmission;
    }

    private boolean process(Level level, boolean halted) {
        radiationEmission = 0D;
        recipeInput.setItem(0, inventory.getStackInSlot(0));
        MelterRecipe recipe = recipeInput.isEmpty() ? null : recipes.getRecipeFor(recipeInput, level).orElse(null);
        if (recipe == null || !recipe.id().equals(activeRecipe)) {
            activeRecipe = recipe == null ? null : recipe.id();
            if (progressHalfTicks != 0) {
                progressHalfTicks = 0;
                setChanged();
            }
        }
        recipeDuration = recipe == null ? 0 : recipe.ticks();
        basePower = recipe == null ? 0 : recipe.energyPerTick();

        if (recipe == null || halted) {
            return false;
        }
        FluidStack product = recipe.product();
        double speed = speed();
        if (!tank.canAccept(product) || !power.spend(energyPerTick())) {
            if (progressHalfTicks > 0) {
                progressHalfTicks = Math.max(0, progressHalfTicks - 3 * speed);
                setChanged();
            }
            return false;
        }

        radiationEmission = recipe.radiation() * speed;
        progressHalfTicks += 2 * speed;
        double batch = 2D * recipeDuration;
        while (progressHalfTicks >= batch) {
            tank.produce(product);
            inventory.extractItem(0, 1, false);
            progressHalfTicks -= batch;
            if (progressHalfTicks > 0) {
                recipeInput.setItem(0, inventory.getStackInSlot(0));
                if (!recipe.matches(recipeInput, level) || !tank.canAccept(product)) {
                    progressHalfTicks = 0;
                }
            }
        }
        setChanged();
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", inventory.serializeNBT());
        tag.put("output", tank.writeToNBT(new CompoundTag()));
        if (activeRecipe != null) {
            tag.putString("recipe", activeRecipe.toString());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        radiationEmission = 0D;
        inventory.deserializeNBT(tag.getCompound("inventory"));
        tank.readFromNBT(tag.getCompound("output"));
        activeRecipe = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (!isRemoved()) {
            if (capability == ForgeCapabilities.ITEM_HANDLER) {
                return inventory.capability(side).cast();
            }
            if (capability == ForgeCapabilities.FLUID_HANDLER) {
                return fluidCapability.cast();
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
        inventory.invalidate();
        fluidCapability.invalidate();
        energyCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        inventory.revive();
        fluidCapability = LazyOptional.of(() -> tank);
        energyCapability = LazyOptional.of(() -> power);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public MelterMenu createMenu(int menuId, Inventory playerInventory, Player player) {
        return new MelterMenu(menuId, playerInventory, inventory, upgrades, new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case MelterMenu.PROGRESS -> recipeDuration == 0 ? 0 :
                            (int) (PROGRESS_SCALE * Math.min(progressHalfTicks, 2L * recipeDuration) / (2L * recipeDuration));
                    case MelterMenu.ENERGY -> (int) ((long) PROGRESS_SCALE * power.getEnergyStored() / power.getMaxEnergyStored());
                    case MelterMenu.FLUID_AMOUNT -> tank.getFluidAmount();
                    case MelterMenu.FLUID_ID -> BuiltInRegistries.FLUID.getId(tank.getFluid().getFluid());
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

    private final class OutputTank extends FluidTank {
        private OutputTank() {
            super(TANK_CAPACITY);
        }

        private boolean canAccept(FluidStack product) {
            return super.fill(product, FluidAction.SIMULATE) == product.getAmount();
        }

        private void produce(FluidStack product) {
            super.fill(product, FluidAction.EXECUTE);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack fluid) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    }
}
