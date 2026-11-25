package nc.processor;

import nc.radiation.ChunkRadiationSource;
import nc.automation.MachineInterfaceTarget;

import nc.init.NCItemProcessors;
import nc.init.NCItemProcessors.Machine;
import nc.processor.ProcessorFluidTanks.OutputMode;
import nc.recipe.ItemProcessorRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
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
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class ItemProcessorBlockEntity extends UpgradableProcessorBlockEntity implements MachineInterfaceTarget, MenuProvider, ChunkRadiationSource {
    private final ProcessorProfile machine;
    private final RecipeManager.CachedCheck<Container, ItemProcessorRecipe> recipes;
    private final @Nullable RecipeManager.CachedCheck<Container, SmeltingRecipe> smeltingRecipes;
    private final ProcessorInputs input;
    private final ItemStackHandler inventory;
    private final @Nullable ProcessorFluidTanks fluid;
    private final ProcessorItemPorts itemPorts;
    private LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> power);
    private double radiationEmission;
    private ResourceLocation activeRecipe;
    private @Nullable ProcessorInputs reserved;
    private @Nullable SmeltingRecipe cachedSmeltingSource;
    private @Nullable ItemProcessorRecipe cachedSmeltingRecipe;

    public ItemProcessorBlockEntity(BlockPos pos, BlockState state, ProcessorProfile machine) {
        super(machine.entityType(), pos, state, machine.rate(), 512_000, machine.generator(),
                machine.scripted() && machine.defaultRedstoneControl());
        this.machine = machine;
        recipes = RecipeManager.createCheck(machine.recipes());
        smeltingRecipes = machine == Machine.ELECTRIC_FURNACE ? RecipeManager.createCheck(RecipeType.SMELTING) : null;
        input = new ProcessorInputs(machine.inputs(), machine.fluidInputs());
        fluid = machine.tanks().isEmpty() ? null : new ProcessorFluidTanks(this, this::acceptsFluid, this::canFill,
                machine.fluidInputs(), machine.fluidOutputs(), machine::tankCapacity, false);
        inventory = new ItemStackHandler(machine.slots()) {
            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                if (!rate.enabled() || slot >= machine.inputs() || stack.isEmpty()) {
                    return false;
                }
                if (level == null || level.isClientSide) {
                    return true;
                }
                return acceptsItem(slot, stack);
            }

            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
        itemPorts = new ProcessorItemPorts(this, inventory, this::isIngredient, machine.inputs(),
                hasUpgrades() ? upgrades : null, false);
    }

    @Override
    ProcessorItemPorts itemPorts() {
        return itemPorts;
    }

    public ItemStackHandler inventory() {
        return inventory;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ItemProcessorBlockEntity processor) {
        boolean running = processor.process(level, processor.redstoneHalted(level, pos));
        ProcessorBlock.setRunning(level, pos, state, running);
        if (!running) {
            processor.itemPorts.push(level);
        }
        if (processor.fluid != null) {
            processor.fluid.push(level);
        }
        if (processor.machine.generator()) {
            for (Direction side : ProcessorItemPorts.SIDES) {
                if (processor.power.getEnergyStored() == 0) {
                    break;
                }
                BlockPos neighbor = pos.relative(side);
                BlockEntity target = level.hasChunkAt(neighbor) ? level.getBlockEntity(neighbor) : null;
                if (target != null) {
                    target.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).ifPresent(processor.power::pushTo);
                }
            }
        }
    }

    @Override
    public void pushInterfaceEnergy(Direction side, IEnergyStorage recipient) {
        power.pushTo(recipient);
    }

    @Override
    boolean allowsSettingsCopy() {
        var layout = machine.layout();
        return layout == null || layout.config().visible() && layout.redstone().visible();
    }

    @Override
    boolean missingRedstoneControl() {
        return machine.scripted() ? machine.defaultRedstoneControl() : super.missingRedstoneControl();
    }

    private void refreshInput() {
        for (int slot = 0; slot < machine.inputs(); slot++) {
            input.setItem(slot, inventory.getStackInSlot(slot));
        }
        for (int tank = 0; tank < machine.fluidInputs(); tank++) {
            input.setFluid(tank, fluid.stored(tank));
        }
    }

    private boolean acceptsItem(int slot, ItemStack stack) {
        if (!NCItemProcessors.smartProcessorInput() || ItemStack.isSameItemSameTags(stack, inventory.getStackInSlot(slot))) {
            return isIngredient(stack);
        }
        RecipeManager manager = level.getRecipeManager();
        refreshInput();
        return manager.getAllRecipesFor(machine.recipes()).stream()
                .anyMatch(recipe -> recipe.canInsert(input, slot, stack)) || acceptsSmeltingInput(manager, stack);
    }

    private boolean isIngredient(ItemStack stack) {
        if (!rate.enabled()) {
            return false;
        }
        if (level == null || level.isClientSide) {
            return true;
        }
        RecipeManager manager = level.getRecipeManager();
        return manager.getAllRecipesFor(machine.recipes()).stream().anyMatch(recipe -> recipe.acceptsItem(stack))
                || acceptsSmeltingInput(manager, stack);
    }

    private boolean acceptsSmeltingInput(RecipeManager manager, ItemStack stack) {
        return smeltingRecipes != null
                && manager.getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack), level).isPresent();
    }

    private boolean acceptsFluid(FluidStack stack) {
        return rate.enabled() && level != null && level.getRecipeManager().getAllRecipesFor(machine.recipes()).stream()
                .anyMatch(recipe -> recipe.acceptsFluid(stack));
    }

    private boolean canFill(int tank, FluidStack stack) {
        if (!rate.enabled() || level == null) {
            return false;
        }
        if (!NCItemProcessors.smartProcessorInput() || fluid.stored(tank).isFluidEqual(stack)) {
            return acceptsFluid(stack);
        }
        refreshInput();
        return level.getRecipeManager().getAllRecipesFor(machine.recipes()).stream()
                .anyMatch(recipe -> recipe.canFill(input, tank, stack));
    }

    private @Nullable ItemProcessorRecipe findRecipe(Level level) {
        ItemProcessorRecipe recipe = recipes.getRecipeFor(input, level).orElse(null);
        if (recipe != null || input.getItem(0).isEmpty()) {
            return recipe;
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
        if (!rate.enabled()) {
            return false;
        }
        refreshInput();
        ItemProcessorRecipe recipe = reserved == null ? findRecipe(level) : reservedRecipe(level);
        if (recipe == null && reserved != null) {
            if (!restoreReservation()) {
                return false;
            }
            refreshInput();
            recipe = findRecipe(level);
        }
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
        List<FluidStack> fluidProducts = recipe.fluidOutputs();
        boolean room = hasOutputSpace(products, fluidProducts);
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
        if (machine.consumesInputs() && reserved == null) {
            reserve(recipe);
        }
        radiationEmission = recipe.radiation() * speed;
        progressHalfTicks += 2 * speed;
        double batch = 2D * batchTicks;
        while (progressHalfTicks >= batch) {
            if (reserved == null) {
                consume(recipe, null);
            } else {
                reserved = null;
            }
            for (int index = 0; index < products.size(); index++) {
                int slot = machine.inputs() + index;
                if (itemPorts.overflow(slot) != OutputMode.VOID) {
                    itemPorts.produce(slot, products.get(index).roll(level.random));
                }
            }
            for (int index = 0; index < fluidProducts.size(); index++) {
                if (!fluidProducts.get(index).isEmpty()) {
                    fluid.produce(machine.fluidInputs() + index, recipe.fluidProducts().get(index).roll(level.random));
                }
            }
            itemPorts.push(level);
            if (fluid != null && fluid.discardsUnusableInputs()) {
                fluid.push(level);
                refreshInput();
                ItemProcessorRecipe nextBatch = findRecipe(level);
                if (nextBatch == null || !hasOutputSpace(nextBatch.products(), nextBatch.fluidOutputs())) {
                    fluid.discardUnusableInputs();
                    progressHalfTicks = 0;
                    break;
                }
            }
            progressHalfTicks -= batch;
            if (progressHalfTicks > 0) {
                refreshInput();
                if (!recipe.matches(input, level) || !hasOutputSpace(products, fluidProducts)) {
                    progressHalfTicks = 0;
                } else if (machine.consumesInputs()) {
                    reserve(recipe);
                }
            }
        }
        setChanged();
        return true;
    }

    private @Nullable ItemProcessorRecipe reservedRecipe(Level level) {
        var current = activeRecipe == null ? null : level.getRecipeManager().byKey(activeRecipe).orElse(null);
        if (!(current instanceof ItemProcessorRecipe recipe) || recipe.machine() != machine) {
            return null;
        }
        int[] items = recipe.match(reserved);
        int[] fluids = recipe.matchFluids(reserved);
        if (items == null || fluids == null) {
            return null;
        }
        for (int slot = 0; slot < items.length; slot++) {
            if (items[slot] != reserved.getItem(slot).getCount()) {
                return null;
            }
        }
        for (int tank = 0; tank < fluids.length; tank++) {
            if (fluids[tank] != reserved.fluid(tank).getAmount()) {
                return null;
            }
        }
        return recipe;
    }

    private void reserve(ItemProcessorRecipe recipe) {
        reserved = new ProcessorInputs(machine.inputs(), machine.fluidInputs());
        consume(recipe, reserved);
    }

    private void consume(ItemProcessorRecipe recipe, @Nullable ProcessorInputs batch) {
        int[] items = recipe.match(input);
        int[] fluids = recipe.matchFluids(input);
        for (int slot = 0; slot < items.length; slot++) {
            var extracted = inventory.extractItem(slot, items[slot], false);
            if (batch != null) {
                batch.setItem(slot, extracted);
            }
        }
        for (int tank = 0; tank < fluids.length; tank++) {
            if (batch != null) {
                var stack = fluid.stored(tank).copy();
                stack.setAmount(fluids[tank]);
                batch.setFluid(tank, stack);
            }
            fluid.consume(tank, fluids[tank]);
        }
    }

    private boolean restoreReservation() {
        for (int slot = 0; slot < machine.inputs(); slot++) {
            ItemStack held = reserved.getItem(slot);
            ItemStack stored = inventory.getStackInSlot(slot);
            if (!held.isEmpty() && ((!stored.isEmpty() && !ItemStack.isSameItemSameTags(held, stored))
                    || held.getCount() > held.getMaxStackSize() - stored.getCount())) {
                return false;
            }
        }
        for (int tank = 0; tank < machine.fluidInputs(); tank++) {
            FluidStack held = reserved.fluid(tank);
            if (!held.isEmpty() && !fluid.canRestoreInput(tank, held)) {
                return false;
            }
        }
        for (int slot = 0; slot < machine.inputs(); slot++) {
            ItemStack held = reserved.getItem(slot);
            if (!held.isEmpty()) {
                inventory.setStackInSlot(slot, held.copyWithCount(held.getCount() + inventory.getStackInSlot(slot).getCount()));
            }
        }
        for (int tank = 0; tank < machine.fluidInputs(); tank++) {
            FluidStack held = reserved.fluid(tank);
            if (!held.isEmpty()) {
                fluid.restoreInput(tank, held);
            }
        }
        reserved = null;
        activeRecipe = null;
        progressHalfTicks = 0;
        setChanged();
        return true;
    }

    @Override
    void dropAuxiliaryInventory(Level level, BlockPos pos) {
        super.dropAuxiliaryInventory(level, pos);
        if (reserved != null) {
            for (int slot = 0; slot < machine.inputs(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), reserved.getItem(slot));
            }
        }
    }

    private boolean hasOutputSpace(List<ItemProcessorRecipe.Product> products, List<FluidStack> fluidProducts) {
        for (int index = 0; index < products.size(); index++) {
            if (!itemPorts.canProduce(machine.inputs() + index, products.get(index).stack())) {
                return false;
            }
        }
        for (int index = 0; index < fluidProducts.size(); index++) {
            FluidStack product = fluidProducts.get(index);
            if (!product.isEmpty() && !fluid.canProduce(machine.fluidInputs() + index, product)) {
                return false;
            }
        }
        return true;
    }

    public void cycleSide(Direction side) {
        itemPorts.cycleGroup(side);
    }

    public void cycleFluidSide(Direction side) {
        if (fluid != null) {
            fluid.cycle(0, side);
        }
    }

    @Override
    @Nullable ProcessorFluidTanks fluidTanks() {
        return fluid;
    }

    @Override
    boolean hasUpgrades() {
        return machine.hasUpgrades();
    }

    private String fluidSaveKey() {
        return machine.hasFluidOutput() ? "output_fluid" : "input_fluid";
    }

    private String fluidSettingKey() {
        return machine.hasFluidOutput() ? "fluid_output_mode" : "fluid_input_mode";
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (!isRemoved()) {
            if (capability == ForgeCapabilities.ITEM_HANDLER) {
                return itemPorts.capability(side).cast();
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
        itemPorts.invalidate();
        energy.invalidate();
        if (fluid != null) {
            fluid.invalidate();
        }
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemPorts.revive();
        energy = LazyOptional.of(() -> power);
        if (fluid != null) {
            fluid.revive();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (activeRecipe != null) {
            tag.putString("recipe", activeRecipe.toString());
        }
        itemPorts.save(tag);
        if (reserved != null) {
            CompoundTag batch = new CompoundTag();
            for (int slot = 0; slot < machine.inputs(); slot++) {
                batch.put("item_" + slot, reserved.getItem(slot).save(new CompoundTag()));
            }
            for (int tank = 0; tank < machine.fluidInputs(); tank++) {
                batch.put("fluid_" + tank, reserved.fluid(tank).writeToNBT(new CompoundTag()));
            }
            tag.put("reserved_inputs", batch);
        }
        if (fluid != null) {
            for (int tank = 0; tank < fluid.size(); tank++) {
                if (machine.scripted()) {
                    fluid.saveTank(tank, tag, "fluid_" + tank, "fluid_sides_" + tank, customFluidSettingKey(tank));
                } else {
                    fluid.saveTank(tank, tag, fluidSaveKey(), "fluid_sides", fluidSettingKey());
                }
            }
            fluid.saveSeparation(tag);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        radiationEmission = 0D;
        activeRecipe = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
        reserved = null;
        if (tag.contains("reserved_inputs")) {
            CompoundTag batch = tag.getCompound("reserved_inputs");
            reserved = new ProcessorInputs(machine.inputs(), machine.fluidInputs());
            for (int slot = 0; slot < machine.inputs(); slot++) {
                reserved.setItem(slot, ItemStack.of(batch.getCompound("item_" + slot)));
            }
            for (int tank = 0; tank < machine.fluidInputs(); tank++) {
                reserved.setFluid(tank, FluidStack.loadFluidStackFromNBT(batch.getCompound("fluid_" + tank)));
            }
        }
        itemPorts.load(tag);
        if (fluid != null) {
            for (int tank = 0; tank < fluid.size(); tank++) {
                if (machine.scripted()) {
                    fluid.loadTank(tank, tag, "fluid_" + tank, "fluid_sides_" + tank, customFluidSettingKey(tank));
                } else {
                    fluid.loadTank(tank, tag, fluidSaveKey(), "fluid_sides", fluidSettingKey());
                }
            }
            fluid.loadSeparation(tag);
            fluid.refreshFaces();
        }
    }

    private String customFluidSettingKey(int tank) {
        return (tank < machine.fluidInputs() ? "fluid_input_mode_" : "fluid_output_mode_") + tank;
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
                if (index == ItemProcessorMenu.redstoneDataIndex(machine)) {
                    return redstoneControl() ? 1 : 0;
                }
                if (machine.scripted() && fluid != null && index >= ItemProcessorMenu.DATA_COUNT) {
                    return ItemProcessorMenu.scriptedTankData(fluid, index);
                }
                return switch (index) {
                    case ItemProcessorMenu.PROGRESS -> progress();
                    case ItemProcessorMenu.ENERGY -> (int) (1000L * power.getEnergyStored() / power.getMaxEnergyStored());
                    case ItemProcessorMenu.FLUID_ID -> fluid == null ? 0
                            : BuiltInRegistries.FLUID.getId(fluid.stored(0).getFluid());
                    case ItemProcessorMenu.FLUID_AMOUNT -> fluid == null ? 0 : fluid.stored(0).getAmount();
                    case ItemProcessorMenu.FLUID_SIDES -> fluid == null ? 0 : fluid.sideModes(0);
                    case ItemProcessorMenu.FLUID_OUTPUT_MODE -> fluid == null ? 0 : fluid.setting(0);
                    default -> 0;
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
}
