package nc.processor;

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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RangedWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MelterBlockEntity extends BlockEntity implements MachineInterfaceTarget, MenuProvider {
    static final int TANK_CAPACITY = 16_000;
    private static final int ENERGY_CAPACITY = 512_000;
    private static final int PROGRESS_SCALE = 1000;

    private final RecipeManager.CachedCheck<Container, MelterRecipe> recipes =
            RecipeManager.createCheck(NCRegistries.MELTING.get());
    private final SimpleContainer recipeInput = new SimpleContainer(1);
    private final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final RangedWrapper automationInput = new RangedWrapper(inventory, 0, 1) {
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }
    };
    private final OutputTank tank = new OutputTank();
    private final ProcessorEnergy power = new ProcessorEnergy(this, ENERGY_CAPACITY);

    private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> automationInput);
    private LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> tank);
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(() -> power);
    private ResourceLocation activeRecipe;
    private long progressHalfTicks;
    private int recipeDuration;

    public MelterBlockEntity(BlockPos pos, BlockState state) {
        super(NCRegistries.MELTER_ENTITY.get(), pos, state);
    }

    ItemStackHandler inventory() {
        return inventory;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MelterBlockEntity melter) {
        boolean processing = melter.process(level, MachineInterface.powered(level, pos, melter));
        ProcessorBlock.setRunning(level, pos, state, processing);
    }

    private boolean process(Level level, boolean halted) {
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

        if (recipe == null || halted) {
            return false;
        }
        FluidStack product = recipe.product();
        if (!tank.canAccept(product) || !power.spend(recipe.energyPerTick())) {
            if (progressHalfTicks > 0) {
                progressHalfTicks = Math.max(0, progressHalfTicks - 3);
                setChanged();
            }
            return false;
        }

        progressHalfTicks += 2;
        if (progressHalfTicks >= 2L * recipe.ticks()) {
            tank.produce(product);
            inventory.extractItem(0, 1, false);
            progressHalfTicks = 0;
        }
        setChanged();
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", inventory.serializeNBT());
        tag.put("output", tank.writeToNBT(new CompoundTag()));
        tag.putInt("energy", power.getEnergyStored());
        tag.putLong("progress_half_ticks", progressHalfTicks);
        if (activeRecipe != null) {
            tag.putString("recipe", activeRecipe.toString());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("inventory"));
        tank.readFromNBT(tag.getCompound("output"));
        power.load(tag.getInt("energy"));
        progressHalfTicks = Math.max(0, tag.getLong("progress_half_ticks"));
        activeRecipe = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (!isRemoved()) {
            if (capability == ForgeCapabilities.ITEM_HANDLER) {
                return itemCapability.cast();
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
        itemCapability.invalidate();
        fluidCapability.invalidate();
        energyCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemCapability = LazyOptional.of(() -> automationInput);
        fluidCapability = LazyOptional.of(() -> tank);
        energyCapability = LazyOptional.of(() -> power);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public MelterMenu createMenu(int menuId, Inventory playerInventory, Player player) {
        return new MelterMenu(menuId, playerInventory, inventory, new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case MelterMenu.PROGRESS -> recipeDuration == 0 ? 0 :
                            (int) (PROGRESS_SCALE * Math.min(progressHalfTicks, 2L * recipeDuration) / (2L * recipeDuration));
                    case MelterMenu.ENERGY -> PROGRESS_SCALE * power.getEnergyStored() / power.getMaxEnergyStored();
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
