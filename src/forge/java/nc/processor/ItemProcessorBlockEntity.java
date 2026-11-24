package nc.processor;

import nc.radiation.ChunkRadiationSource;
import nc.automation.MachineInterface;
import nc.automation.MachineInterfaceTarget;

import nc.init.NCItemProcessors.Machine;
import nc.processor.ProcessorFluidTanks.OutputMode;
import nc.recipe.ItemProcessorRecipe;
import nc.recipe.PressurizerPlateRecipes;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

public final class ItemProcessorBlockEntity extends UpgradableProcessorBlockEntity implements MachineInterfaceTarget, MenuProvider, ChunkRadiationSource {
    private static final Direction[] SIDES = Direction.values();

    private final Machine machine;
    private final RecipeManager.CachedCheck<Container, ItemProcessorRecipe> recipes;
    private final @Nullable RecipeManager.CachedCheck<Container, SmeltingRecipe> smeltingRecipes;
    private final ProcessorInputs input;
    private final ItemStackHandler inventory;
    private final @Nullable ProcessorFluidTanks fluid;
    private OutputMode fluidOutputMode = OutputMode.HOLD;
    private final EnumMap<Direction, SideMode> sideModes = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, ItemAccess> ports = new EnumMap<>(Direction.class);
    private ItemAccess unsided;
    private LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> power);
    private double radiationEmission;
    private ResourceLocation activeRecipe;
    private @Nullable SmeltingRecipe cachedSmeltingSource;
    private @Nullable ItemProcessorRecipe cachedSmeltingRecipe;

    public ItemProcessorBlockEntity(BlockPos pos, BlockState state, Machine machine) {
        super(machine.entityType(), pos, state, machine.rate(), 512_000);
        this.machine = machine;
        recipes = RecipeManager.createCheck(machine.recipes());
        smeltingRecipes = machine == Machine.ELECTRIC_FURNACE ? RecipeManager.createCheck(RecipeType.SMELTING) : null;
        input = new ProcessorInputs(machine.inputs());
        fluid = machine.tank() == null ? null : new ProcessorFluidTanks(this, this::acceptsFluid,
                machine.hasFluidInput() ? 1 : 0, machine.hasFluidOutput() ? 1 : 0);
        inventory = new ItemStackHandler(machine.slots()) {
            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                if (slot >= machine.inputs() || stack.isEmpty()) {
                    return false;
                }
                if (level == null || level.isClientSide) {
                    return true;
                }
                refreshInput();
                RecipeManager manager = level.getRecipeManager();
                return manager.getAllRecipesFor(machine.recipes()).stream().anyMatch(recipe -> recipe.canInsert(input, slot, stack))
                        || machine == Machine.PRESSURIZER && PressurizerPlateRecipes.forServer(manager).stream()
                        .anyMatch(recipe -> recipe.canInsert(input, slot, stack))
                        || (smeltingRecipes != null && manager.getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack), level)
                        .map(recipe -> electricSmelting(recipe, level).canInsert(input, slot, stack))
                        .orElse(false));
            }

            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
        unsided = itemAccess(SideMode.BOTH);
        for (Direction side : SIDES) {
            sideModes.put(side, SideMode.BOTH);
            ports.put(side, itemAccess(SideMode.BOTH));
        }
    }

    public ItemStackHandler inventory() {
        return inventory;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ItemProcessorBlockEntity processor) {
        boolean running = processor.process(level, MachineInterface.powered(level, pos, processor));
        ProcessorBlock.setRunning(level, pos, state, running);
        if (processor.fluid != null) {
            processor.fluid.push(level);
        }
    }

    private void refreshInput() {
        for (int slot = 0; slot < machine.inputs(); slot++) {
            input.setItem(slot, inventory.getStackInSlot(slot));
        }
        if (fluid != null && machine.hasFluidInput()) {
            input.setFluid(fluid.stored(0));
        }
    }

    private boolean acceptsFluid(FluidStack stack) {
        return level != null && level.getRecipeManager().getAllRecipesFor(machine.recipes()).stream()
                .map(ItemProcessorRecipe::fluidInput).filter(Objects::nonNull).anyMatch(ingredient -> ingredient.accepts(stack));
    }

    private @Nullable ItemProcessorRecipe findRecipe(Level level) {
        ItemProcessorRecipe recipe = recipes.getRecipeFor(input, level).orElse(null);
        if (recipe != null || input.getItem(0).isEmpty()) {
            return recipe;
        }
        if (machine == Machine.PRESSURIZER) {
            return PressurizerPlateRecipes.forServer(level.getRecipeManager()).stream()
                    .filter(plate -> plate.matches(input, level)).findFirst().orElse(null);
        }
        if (smeltingRecipes == null) {
            return null;
        }
        SmeltingRecipe source = smeltingRecipes.getRecipeFor(input, level).orElse(null);
        if (source == null) {
            return null;
        }
        ItemProcessorRecipe smelting = electricSmelting(source, level);
        return smelting.matches(input, level) ? smelting : null;
    }

    private ItemProcessorRecipe electricSmelting(SmeltingRecipe source, Level level) {
        if (source != cachedSmeltingSource) {
            cachedSmeltingRecipe = ItemProcessorRecipe.electricSmelting(source, level.registryAccess());
            cachedSmeltingSource = source;
        }
        return cachedSmeltingRecipe;
    }

    @Override
    public double radiationEmission() {
        return radiationEmission;
    }

    private boolean process(Level level, boolean halted) {
        radiationEmission = 0D;
        refreshInput();
        ItemProcessorRecipe recipe = findRecipe(level);
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
        updateRates(recipe.ticks(), recipe.configuredTime(), recipe.configuredPower());
        List<ItemProcessorRecipe.Product> products = recipe.products();
        FluidStack fluidProduct = machine.hasFluidOutput() ? recipe.fluidOutput() : FluidStack.EMPTY;
        boolean room = hasOutputSpace(products, fluidProduct);
        double speed = speed();
        if (!room || halted || !power.spend(energyPerTick())) {
            if (progressHalfTicks > 0) {
                double nextProgress = machine.losesProgress() && !halted ? Math.max(0, progressHalfTicks - 3 * speed)
                        : room ? progressHalfTicks : 0;
                if (nextProgress != progressHalfTicks) {
                    progressHalfTicks = nextProgress;
                    setChanged();
                }
            }
            return false;
        }
        radiationEmission = recipe.radiation() * speed;
        progressHalfTicks += 2 * speed;
        double batch = 2D * batchTicks;
        while (progressHalfTicks >= batch) {
            int[] amounts = recipe.match(input);
            for (int slot = 0; slot < amounts.length; slot++) {
                inventory.extractItem(slot, amounts[slot], false);
            }
            if (fluid != null && recipe.fluidInput() != null) {
                fluid.consume(recipe.fluidInput().amount());
            }
            for (int index = 0; index < products.size(); index++) {
                int slot = machine.inputs() + index;
                ItemStack product = products.get(index).roll(level.random);
                if (!product.isEmpty()) {
                    product.grow(inventory.getStackInSlot(slot).getCount());
                    inventory.setStackInSlot(slot, product);
                }
            }
            if (fluid != null && !fluidProduct.isEmpty()) {
                fluid.produce(0, fluidProduct, fluidOutputMode);
            }
            progressHalfTicks -= batch;
            if (progressHalfTicks > 0) {
                refreshInput();
                if (!recipe.matches(input, level) || !hasOutputSpace(products, fluidProduct)) {
                    progressHalfTicks = 0;
                }
            }
        }
        setChanged();
        return true;
    }

    private boolean hasOutputSpace(List<ItemProcessorRecipe.Product> products, FluidStack fluidProduct) {
        for (int index = 0; index < products.size(); index++) {
            ItemStack stored = inventory.getStackInSlot(machine.inputs() + index);
            if (!stored.isEmpty()) {
                ItemStack product = products.get(index).stack();
                if (!product.isEmpty() && (!ItemStack.isSameItemSameTags(stored, product)
                        || product.getCount() > stored.getMaxStackSize() - stored.getCount())) {
                    return false;
                }
            }
        }
        return fluid == null || fluidProduct.isEmpty() || fluid.canProduce(0, fluidProduct, fluidOutputMode);
    }

    public void cycleSide(Direction side) {
        SideMode next = sideModes.get(side).next();
        sideModes.put(side, next);
        ports.put(side, itemAccess(next)).invalidate();
        updateConnections();
    }

    public void cycleFluidSide(Direction side) {
        if (fluid != null) {
            fluid.cycle(0, side);
            updateConnections();
        }
    }

    void cycleFluidOutputMode() {
        if (fluid != null && machine.hasFluidOutput()) {
            fluidOutputMode = fluidOutputMode.next();
            if (fluidOutputMode == OutputMode.VOID) {
                fluid.clear(0);
            }
            setChanged();
        }
    }

    private String fluidSaveKey() {
        return machine.hasFluidOutput() ? "output_fluid" : "input_fluid";
    }

    private void updateConnections() {
        setChanged();
        if (level != null) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (!isRemoved()) {
            if (capability == ForgeCapabilities.ITEM_HANDLER) {
                return (side == null ? unsided : ports.get(side)).capability.cast();
            }
            if (capability == ForgeCapabilities.ENERGY) {
                return energy.cast();
            }
            if (capability == ForgeCapabilities.FLUID_HANDLER && fluid != null) {
                return fluid.capability(side).cast();
            }
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        unsided.invalidate();
        ports.values().forEach(ItemAccess::invalidate);
        energy.invalidate();
        if (fluid != null) {
            fluid.invalidate();
        }
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        unsided = itemAccess(SideMode.BOTH);
        sideModes.forEach((side, mode) -> ports.put(side, itemAccess(mode)));
        energy = LazyOptional.of(() -> power);
        if (fluid != null) {
            fluid.revive();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", inventory.serializeNBT());
        if (activeRecipe != null) {
            tag.putString("recipe", activeRecipe.toString());
        }
        int[] modes = new int[SIDES.length];
        for (Direction side : SIDES) {
            modes[side.ordinal()] = sideModes.get(side).ordinal();
        }
        tag.putIntArray("item_sides", modes);
        if (fluid != null) {
            fluid.saveTank(0, tag, fluidSaveKey(), "fluid_sides");
            if (machine.hasFluidOutput()) {
                tag.putInt("fluid_output_mode", fluidOutputMode.ordinal());
            }
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        radiationEmission = 0D;
        inventory.deserializeNBT(tag.getCompound("inventory"));
        activeRecipe = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
        int[] modes = tag.getIntArray("item_sides");
        if (modes.length == SIDES.length) {
            for (Direction side : SIDES) {
                SideMode mode = SideMode.byId(modes[side.ordinal()]);
                sideModes.put(side, mode);
                ports.put(side, itemAccess(mode)).invalidate();
            }
        }
        if (fluid != null) {
            fluid.loadTank(0, tag, fluidSaveKey(), "fluid_sides");
            fluidOutputMode = OutputMode.byId(tag.getInt("fluid_output_mode"));
            fluid.refreshFaces();
        }
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public ItemProcessorMenu createMenu(int id, Inventory playerInventory, Player player) {
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case ItemProcessorMenu.PROGRESS -> progress();
                    case ItemProcessorMenu.ENERGY -> (int) (1000L * power.getEnergyStored() / power.getMaxEnergyStored());
                    case ItemProcessorMenu.FLUID_ID -> fluid == null ? 0
                            : BuiltInRegistries.FLUID.getId(fluid.stored(0).getFluid());
                    case ItemProcessorMenu.FLUID_AMOUNT -> fluid == null ? 0 : fluid.stored(0).getAmount();
                    case ItemProcessorMenu.FLUID_SIDES -> fluid == null ? 0 : fluid.sideModes(0);
                    case ItemProcessorMenu.FLUID_OUTPUT_MODE -> fluidOutputMode.ordinal();
                    default -> sideModes.get(SIDES[index - ItemProcessorMenu.FIRST_SIDE]).ordinal();
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return ItemProcessorMenu.dataSize(machine);
            }
        };
        return new ItemProcessorMenu(id, playerInventory, machine, inventory, upgrades, data,
                ContainerLevelAccess.create(player.level(), worldPosition), this);
    }

    public enum SideMode {
        BOTH(true, true, "both"), INPUT(true, false, "input"), OUTPUT(false, true, "output"), OFF(false, false, "off");

        private static final SideMode[] MODES = values();
        private final boolean input;
        private final boolean output;
        private final String id;

        SideMode(boolean input, boolean output, String id) {
            this.input = input;
            this.output = output;
            this.id = id;
        }

        public Component label() {
            return Component.translatable("gui.nuclearcraft.item_side." + id);
        }

        SideMode next() {
            return MODES[(ordinal() + 1) % MODES.length];
        }

        static SideMode byId(int id) {
            return id >= 0 && id < MODES.length ? MODES[id] : BOTH;
        }
    }

    private ItemAccess itemAccess(SideMode mode) {
        return new ItemAccess(this, inventory, machine.inputs(), mode, upgrades);
    }

    static final class ItemAccess implements IItemHandler {
        private final BlockEntity owner;
        private final ItemStackHandler inventory;
        private final int inputs;
        private final SideMode mode;
        private final @Nullable ProcessorUpgrades upgrades;
        final LazyOptional<IItemHandler> capability;
        private boolean valid = true;

        ItemAccess(BlockEntity owner, ItemStackHandler inventory, int inputs, SideMode mode) {
            this(owner, inventory, inputs, mode, null);
        }

        ItemAccess(BlockEntity owner, ItemStackHandler inventory, int inputs, SideMode mode,
                   @Nullable ProcessorUpgrades upgrades) {
            this.owner = owner;
            this.inventory = inventory;
            this.inputs = inputs;
            this.mode = mode;
            this.upgrades = upgrades;
            capability = mode == SideMode.OFF ? LazyOptional.empty() : LazyOptional.of(() -> this);
        }

        void invalidate() {
            valid = false;
            capability.invalidate();
        }

        private boolean visible(int slot) {
            return valid && !owner.isRemoved() && slot >= 0 && slot < getSlots()
                    && (inputSlot(slot) ? mode.input : mode.output);
        }

        private boolean inputSlot(int slot) {
            return slot < inputs || slot >= inventory.getSlots();
        }

        private ItemStackHandler handler(int slot) {
            return slot < inventory.getSlots() ? inventory : Objects.requireNonNull(upgrades);
        }

        private int index(int slot) {
            return slot < inventory.getSlots() ? slot : slot - inventory.getSlots();
        }

        @Override
        public int getSlots() {
            return inventory.getSlots() + (upgrades == null ? 0 : upgrades.getSlots());
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return visible(slot) ? handler(slot).getStackInSlot(index(slot)).copy() : ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return visible(slot) && inputSlot(slot) ? handler(slot).insertItem(index(slot), stack, simulate) : stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return visible(slot) && !inputSlot(slot) && amount > 0
                    ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return visible(slot) ? handler(slot).getSlotLimit(index(slot)) : 0;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return visible(slot) && handler(slot).isItemValid(index(slot), stack);
        }
    }
}
