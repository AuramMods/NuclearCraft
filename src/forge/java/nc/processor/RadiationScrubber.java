package nc.processor;

import nc.automation.MachineInterface;
import nc.automation.MachineInterfaceTarget;

import nc.init.NCItemProcessors;
import nc.init.NCRadiation;
import nc.radiation.ScrubberEnvironment;
import nc.radiation.ScrubberSettings;
import nc.recipe.ScrubberRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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

public final class RadiationScrubber extends BlockEntity implements MachineInterfaceTarget, MenuProvider {
    private static final int ENVIRONMENT_INTERVAL = 400;
    private static final int DEFAULT_CAPACITY = 19_200_000;

    private final ProcessorFluidTanks tanks = new ProcessorFluidTanks(this, this::acceptsFluid, this::canFill, 1, 1);
    private final ProcessorEnergy power = new ProcessorEnergy(this, Integer.MAX_VALUE, this::capacity);
    private final ProcessorInputs input = new ProcessorInputs(1);
    private final ProcessorRecipeLookup<ScrubberRecipe> recipes = new ProcessorRecipeLookup<>(
            NCRadiation.SCRUBBER_RECIPES.get(), recipe -> recipe.itemInput() == null && recipe.fluidInput() == null);
    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot == 0 && recipes().stream().anyMatch(recipe -> recipe.acceptsItem(stack)
                    && (!NCItemProcessors.smartProcessorInput() || ItemStack.isSameItemSameTags(stack, getStackInSlot(0))
                    || tanks.stored(0).isEmpty() || recipe.acceptsFluid(tanks.stored(0))));
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final ProcessorItemPorts itemPorts = new ProcessorItemPorts(this, inventory, this::isIngredient, 1, null, false);
    private LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> power);
    private boolean redstoneControl;

    private @Nullable ResourceLocation reservedRecipe;
    private ItemStack reservedItem = ItemStack.EMPTY;
    private FluidStack reservedFluid = FluidStack.EMPTY;
    private int progress;
    private int duration;

    private double activeEfficiency;
    private double openness = 1D;
    private long nextEnvironmentCheck = Long.MIN_VALUE;
    private double measuredRemoval;

    public RadiationScrubber(BlockPos pos, BlockState state) {
        super(NCRadiation.SCRUBBER_ENTITY.get(), pos, state);
    }

    ProcessorItemPorts itemPorts() {
        return itemPorts;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RadiationScrubber machine) {
        boolean active = machine.process(level, machine.redstoneControl && MachineInterface.powered(level, pos, machine));
        ProcessorBlock.setRunning(level, pos, state, active);
        machine.tanks.push(level);
        if (!active) {
            machine.itemPorts.push(level);
        }
        if (active && level instanceof ServerLevel server && level.getGameTime() >= machine.nextEnvironmentCheck) {
            machine.refreshEnvironment(server);
        }
    }

    private boolean process(Level level, boolean stopped) {
        var recipe = reserveRecipe(level);
        duration = recipe == null ? 0 : recipe.ticks();
        boolean active = recipe != null && !stopped && canProduce(recipe) && power.spend(recipe.power());
        if (!active) {
            activeEfficiency = 0;
            measuredRemoval = 0;
            return false;
        }
        activeEfficiency = recipe.efficiency();
        if (++progress >= duration) {
            itemPorts.produce(1, recipe.itemProduct().roll(level.random));
            if (!recipe.fluidProduct().isEmpty()) {
                tanks.produce(1, recipe.fluidProduct().roll(level.random));
            }
            clearBatch();
            itemPorts.push(level);
            if (tanks.discardsUnusableInputs()) {
                tanks.push(level);
                ScrubberRecipe nextBatch = findUnreservedRecipe(level);
                if (nextBatch == null || !canProduce(nextBatch)) {
                    tanks.discardUnusableInputs();
                }
            }
        }
        setChanged();
        return true;
    }

    private @Nullable ScrubberRecipe reserveRecipe(Level level) {
        if (reservedRecipe != null) {
            var existing = level.getRecipeManager().byKey(reservedRecipe).orElse(null);
            if (existing instanceof ScrubberRecipe recipe && recipe.matches(reservedItem, reservedFluid)
                    && reservedItem.getCount() == itemAmount(recipe) && reservedFluid.getAmount() == fluidAmount(recipe)) {
                return recipe;
            }
            if (!restoreSupplies()) {
                return null;
            }
        }

        var recipe = findUnreservedRecipe(level);
        if (recipe != null) {
            reservedItem = inventory.extractItem(0, itemAmount(recipe), false);
            int amount = fluidAmount(recipe);
            if (amount > 0) {
                reservedFluid = tanks.stored(0).copy();
                reservedFluid.setAmount(amount);
                tanks.consume(amount);
            } else {
                reservedFluid = FluidStack.EMPTY;
            }
            reservedRecipe = recipe.id();
            setChanged();
        }
        return recipe;
    }

    private @Nullable ScrubberRecipe findUnreservedRecipe(Level level) {
        ItemStack items = inventory.getStackInSlot(0);
        FluidStack fluid = tanks.stored(0);
        input.setItem(0, items);
        input.setFluid(fluid);
        return recipes.find(input, level, items.isEmpty() && fluid.isEmpty());
    }

    private boolean restoreSupplies() {
        ItemStack items = inventory.getStackInSlot(0);
        if (!hasItemRoom(items, reservedItem)
                || (!reservedFluid.isEmpty() && !tanks.canRestoreInput(reservedFluid))) {
            return false;
        }
        if (!reservedItem.isEmpty()) {
            inventory.setStackInSlot(0, reservedItem.copyWithCount(reservedItem.getCount() + items.getCount()));
        }
        if (!reservedFluid.isEmpty()) {
            tanks.restoreInput(reservedFluid);
        }
        clearBatch();
        setChanged();
        return true;
    }

    private void clearBatch() {
        reservedRecipe = null;
        reservedItem = ItemStack.EMPTY;
        reservedFluid = FluidStack.EMPTY;
        progress = 0;
    }

    private static int itemAmount(ScrubberRecipe recipe) {
        var item = recipe.itemInput();
        return item == null ? 0 : item.count();
    }

    private static int fluidAmount(ScrubberRecipe recipe) {
        var fluid = recipe.fluidInput();
        return fluid == null ? 0 : fluid.amount();
    }

    private boolean canProduce(ScrubberRecipe recipe) {
        FluidStack product = recipe.fluidOutput();
        return itemPorts.canProduce(1, recipe.itemOutput())
                && (product.isEmpty() || tanks.canProduce(1, product));
    }

    private static boolean hasItemRoom(ItemStack stored, ItemStack offered) {
        return offered.isEmpty() || stored.isEmpty()
                || ItemStack.isSameItemSameTags(stored, offered)
                && offered.getCount() <= stored.getMaxStackSize() - stored.getCount();
    }

    private List<ScrubberRecipe> recipes() {
        return level == null ? List.of() : level.getRecipeManager().getAllRecipesFor(NCRadiation.SCRUBBER_RECIPES.get());
    }

    private boolean isIngredient(ItemStack stack) {
        return recipes().stream().anyMatch(recipe -> recipe.acceptsItem(stack));
    }

    private boolean acceptsFluid(FluidStack stack) {
        return recipes().stream().anyMatch(recipe -> recipe.acceptsFluid(stack));
    }

    private boolean canFill(int tank, FluidStack stack) {
        ItemStack items = inventory.getStackInSlot(0);
        return recipes().stream().anyMatch(recipe -> recipe.acceptsFluid(stack)
                && (!NCItemProcessors.smartProcessorInput() || tanks.stored(tank).isFluidEqual(stack)
                || items.isEmpty() || recipe.acceptsItem(items)));
    }

    private int capacity() {
        var recipes = recipes();
        if (recipes.isEmpty()) {
            return DEFAULT_CAPACITY;
        }
        int ticks = recipes.stream().mapToInt(ScrubberRecipe::ticks).max().orElse(1);
        int power = recipes.stream().mapToInt(ScrubberRecipe::power).max().orElse(0);
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, (long) ticks * power));
    }

    public double contribution() {
        return activeEfficiency * openness * ScrubberSettings.maximumFraction();
    }

    public double effectiveEfficiency() {
        return activeEfficiency * openness;
    }

    public void refreshEnvironment(ServerLevel level) {
        openness = ScrubberEnvironment.openness(level, worldPosition);
        nextEnvironmentCheck = level.getGameTime() + ENVIRONMENT_INTERVAL;
    }

    public void measuredRemoval(double rate) {
        measuredRemoval = rate;
    }

    public double measuredRemoval() {
        return activeEfficiency > 0 && ScrubberSettings.radiationEnabled() ? measuredRemoval : 0;
    }

    public ItemStackHandler inventory() { return inventory; }
    ItemStack reservedItem() { return reservedItem; }
    FluidStack fluid(int tank) { return tanks.stored(tank); }
    int tankModes(int tank) { return tanks.sideModes(tank); }
    ProcessorFluidTanks fluidTanks() { return tanks; }
    boolean redstoneControl() { return redstoneControl; }

    int progress() {
        return duration == 0 ? 0 : (int) Math.min(1000L, 1000L * progress / duration);
    }

    int charge() {
        return (int) (1000L * power.getEnergyStored() / power.getMaxEnergyStored());
    }

    void cycleTank(int tank, Direction side) {
        tanks.cycle(tank, side);
    }

    void toggleRedstone() {
        redstoneControl = !redstoneControl;
        setChanged();
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (!isRemoved()) {
            if (capability == ForgeCapabilities.ITEM_HANDLER) {
                return itemPorts.capability(side).cast();
            }
            if (capability == ForgeCapabilities.FLUID_HANDLER) {
                return tanks.capability(side).cast();
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
        itemPorts.invalidate();
        tanks.invalidate();
        energy.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemPorts.revive();
        tanks.revive();
        energy = LazyOptional.of(() -> power);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("energy", power.getEnergyStored());
        tag.putInt("progress", progress);
        if (reservedRecipe != null) {
            tag.putString("recipe", reservedRecipe.toString());
            tag.put("reserved_item", reservedItem.save(new CompoundTag()));
            tag.put("reserved_fluid", reservedFluid.writeToNBT(new CompoundTag()));
        }
        itemPorts.save(tag);
        tanks.saveTank(0, tag, "input_fluid", "input_sides", "fluid_input_mode");
        tanks.saveTank(1, tag, "output_fluid", "output_sides", "fluid_output_mode");
        tag.putBoolean("redstoneControl", redstoneControl);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        power.load(tag.getInt("energy"));
        reservedRecipe = tag.contains("recipe", 8) ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
        reservedItem = ItemStack.of(tag.getCompound("reserved_item"));
        reservedFluid = FluidStack.loadFluidStackFromNBT(tag.getCompound("reserved_fluid"));
        progress = reservedRecipe == null ? 0 : Math.min(Integer.MAX_VALUE - 1, Math.max(0, tag.getInt("progress")));
        itemPorts.load(tag);
        tanks.loadTank(0, tag, "input_fluid", "input_sides", "fluid_input_mode");
        tanks.loadTank(1, tag, "output_fluid", "output_sides", "fluid_output_mode");
        tanks.refreshFaces();
        redstoneControl = tag.getBoolean("redstoneControl");
        activeEfficiency = 0;
        measuredRemoval = 0;
        nextEnvironmentCheck = Long.MIN_VALUE;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public ScrubberMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new ScrubberMenu(id, playerInventory, this, ContainerLevelAccess.create(player.level(), worldPosition));
    }
}
