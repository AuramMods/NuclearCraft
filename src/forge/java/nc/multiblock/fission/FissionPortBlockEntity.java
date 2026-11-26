package nc.multiblock.fission;

import nc.init.NCFission;
import nc.multiblock.fission.FissionPartBlock.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class FissionPortBlockEntity extends BlockEntity {
    private final @Nullable IrradiatorItems irradiationItems;
    private final @Nullable FissionFuelItems localFuelItems;
    private final @Nullable CoolantTanks localPebbleGas;
    private final @Nullable CoolantTanks localSaltCoolant;
    private final @Nullable SaltFuelBank.Tanks localVesselFuel;
    private @Nullable VentHandler detachedVent;
    private LazyOptional<IFluidHandler> detachedFluid = LazyOptional.empty();
    private @Nullable ItemPortHandler detachedItemPort;
    private LazyOptional<IItemHandler> detachedItems = LazyOptional.empty();
    private @Nullable Binding binding;
    private boolean detachedCapabilities = true;
    private @Nullable ResourceLocation fuelFilter;
    private @Nullable ResourceLocation gasFilter;
    private LazyOptional<IFluidHandler> fluid = LazyOptional.empty();
    private LazyOptional<IItemHandler> items = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> power = LazyOptional.empty();

    public FissionPortBlockEntity(BlockPos pos, BlockState state) {
        super(switch (FissionPartBlock.role(state)) {
            case IRRADIATOR_PORT -> NCFission.IRRADIATOR_PORT_ENTITY.get();
            case POWER_PORT -> NCFission.POWER_PORT_ENTITY.get();
            default -> NCFission.PORT_ENTITY.get();
        }, pos, state);
        localPebbleGas = FissionPartBlock.role(state) == Role.COOLER_PORT
                ? new CoolantTanks(((FissionWallBlock) state.getBlock()).coolant(), this, this::gasFilter) : null;
        localSaltCoolant = FissionPartBlock.role(state) == Role.HEATER_PORT
                ? new CoolantTanks(((FissionWallBlock) state.getBlock()).saltCoolant(), this) : null;
        localVesselFuel = FissionPartBlock.role(state) == Role.VESSEL_PORT
                ? new SaltFuelBank.Tanks(this, this::fuelFilter) : null;
        localFuelItems = switch (FissionPartBlock.role(state)) {
            case FUEL_PORT -> new FissionFuelItems(this, FissionVariant.SOLID, null);
            case CHAMBER_PORT -> new FissionFuelItems(this, FissionVariant.PEBBLE, null);
            default -> null;
        };
        irradiationItems = FissionPartBlock.role(state) == Role.IRRADIATOR_PORT ? new IrradiatorItems(this) : null;
    }

    void bind(SolidFissionController controller, SolidFissionLayout.Port port) {
        Binding next = new Binding(controller, port);
        if (!next.equals(binding)) {
            rebind(next);
        }
    }

    void unbind() {
        rebind(null);
    }

    private void rebind(@Nullable Binding next) {
        var previousFluid = fluid;
        var previousItems = items;
        var previousPower = power;
        binding = next;
        fluid = LazyOptional.empty();
        items = LazyOptional.empty();
        power = LazyOptional.empty();
        if (next != null) {
            if (next.port.role() == Role.POWER_PORT) {
                power = LazyOptional.of(() -> new PowerHandler(next));
            } else if (next.port.fluid()) {
                fluid = LazyOptional.of(() -> new VentHandler(next));
            } else {
                items = LazyOptional.of(() -> new ItemPortHandler(next));
            }
        }
        invalidateDetachedFluid();
        invalidateDetachedItems();
        previousFluid.invalidate();
        previousItems.invalidate();
        previousPower.invalidate();
    }

    private void invalidateDetachedFluid() {
        var previous = detachedFluid;
        detachedVent = null;
        detachedFluid = LazyOptional.empty();
        previous.invalidate();
    }

    void invalidateDetachedItems() {
        var previous = detachedItems;
        detachedItemPort = null;
        detachedItems = LazyOptional.empty();
        previous.invalidate();
    }

    @Override
    public void setBlockState(BlockState state) {
        BlockState previous = getBlockState();
        super.setBlockState(state);
        if (state != previous) {
            invalidateDetachedFluid();
            invalidateDetachedItems();
        }
    }

    private boolean hasLocalFluid() {
        return localPebbleGas != null || localSaltCoolant != null || localVesselFuel != null;
    }

    SaltFuelBank.Tanks localVesselFuel() {
        return Objects.requireNonNull(localVesselFuel);
    }

    private LazyOptional<IFluidHandler> detachedFluidHandler() {
        if (detachedVent == null) {
            boolean output = getBlockState().getValue(FissionWallBlock.ACTIVE);
            var coolant = localPebbleGas != null ? localPebbleGas : localSaltCoolant;
            FluidTank tank = coolant != null ? coolant.tank(output) : localVesselFuel().tank(output);
            VentHandler handler = new VentHandler(output, tank);
            detachedVent = handler;
            detachedFluid = LazyOptional.of(() -> handler);
        }
        return detachedFluid;
    }

    private LazyOptional<IItemHandler> detachedItemHandler() {
        if (detachedItemPort == null) {
            boolean output = getBlockState().getValue(FissionWallBlock.ACTIVE);
            FissionItemBuffer inventory = localFuelItems != null ? localFuelItems : localIrradiatorItems();
            ItemPortHandler handler = new ItemPortHandler(null, output, inventory);
            detachedItemPort = handler;
            detachedItems = LazyOptional.of(() -> handler);
        }
        return detachedItems;
    }

    @Override
    public void invalidateCaps() {
        detachedCapabilities = false;
        super.invalidateCaps();
        unbind();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        detachedCapabilities = true;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        Binding current = binding;
        if (current != null && current.controller.assembled() && (side == null || side == current.port.side())) {
            if (capability == ForgeCapabilities.FLUID_HANDLER) {
                return fluid.cast();
            }
            if (capability == ForgeCapabilities.ITEM_HANDLER) {
                return items.cast();
            }
            if (capability == ForgeCapabilities.ENERGY) {
                return power.cast();
            }
        }
        if (current == null && detachedCapabilities && !isRemoved()
                && (side == null || side == getBlockState().getValue(FissionWallBlock.FACING))) {
            if (capability == ForgeCapabilities.FLUID_HANDLER && hasLocalFluid()) {
                return detachedFluidHandler().cast();
            }
            if (capability == ForgeCapabilities.ITEM_HANDLER && (localFuelItems != null || irradiationItems != null)) {
                return detachedItemHandler().cast();
            }
        }
        return super.getCapability(capability, side);
    }

    private boolean connected(Binding connection) {
        return !isRemoved() && binding == connection && connection.controller.assembled();
    }

    boolean assembled() {
        return binding != null && connected(binding);
    }

    @Nullable SolidFissionController pebbleController() {
        Binding current = binding;
        return current != null && connected(current) && current.controller.variant() == FissionVariant.PEBBLE
                ? current.controller : null;
    }

    FissionFuelItems localFuelItems() {
        return Objects.requireNonNull(localFuelItems);
    }

    @Nullable SolidFissionController itemFuelController() {
        Binding current = binding;
        return localFuelItems != null && current != null && connected(current) ? current.controller : null;
    }

    FissionFuelItems fuelItems() {
        var controller = itemFuelController();
        return controller == null ? localFuelItems() : controller.fuelItems(localFuelItems().filter());
    }

    CoolantTanks localPebbleGas() {
        return Objects.requireNonNull(localPebbleGas);
    }

    CoolantTanks pebbleGas() {
        Binding current = binding;
        return current == null || !connected(current) ? localPebbleGas() : current.controller.pebbleCircuit(current.port.gas());
    }

    private @Nullable ResourceLocation gasFilter() {
        return gasFilter;
    }

    @Nullable PebbleCooling.Channel gasChannel() {
        return localPebbleGas == null ? null
                : new PebbleCooling.Channel(((FissionWallBlock) getBlockState().getBlock()).coolant(), gasFilter);
    }

    boolean selectGasFilter(FluidStack sample) {
        var channel = gasChannel();
        if (channel == null || assembled() || !localPebbleGas().cold.isEmpty()
                || (!sample.isEmpty() && !localPebbleGas().accepts(sample))) {
            return false;
        }
        var next = sample.isEmpty() ? null : ForgeRegistries.FLUIDS.getKey(sample.getFluid());
        if (Objects.equals(gasFilter, next)) {
            return false;
        }
        gasFilter = next;
        setChanged();
        detachedFluidFilterChanged();
        return true;
    }

    CoolantTanks localSaltCoolant() {
        return Objects.requireNonNull(localSaltCoolant);
    }

    boolean selectSaltCoolantFilter(FluidStack sample) {
        if (localSaltCoolant == null || assembled() || !localSaltCoolant.selectFilter(sample)) {
            return false;
        }
        detachedFluidFilterChanged();
        return true;
    }

    private void detachedFluidFilterChanged() {
        invalidateDetachedFluid();
        if (level instanceof ServerLevel server) {
            FissionPartBlock.geometryChanged(server, worldPosition);
        }
    }

    @Nullable SolidFissionController saltController() {
        Binding current = binding;
        return current != null && connected(current) && current.controller.variant() == FissionVariant.SALT
                ? current.controller : null;
    }

    @Nullable SolidFissionController irradiatorController() {
        Binding current = binding;
        return irradiationItems != null && current != null && connected(current) ? current.controller : null;
    }

    IrradiatorItems localIrradiatorItems() {
        return Objects.requireNonNull(irradiationItems);
    }

    IrradiatorItems irradiatorItems() {
        var controller = irradiatorController();
        return controller == null ? localIrradiatorItems() : controller.irradiatorItems(localIrradiatorItems());
    }

    void exportOutput(ServerLevel level) {
        Binding current = binding;
        if (current == null || !current.port.output() || !connected(current)) {
            return;
        }

        var inventory = current.port.fluid() ? null : itemBuffer(current);
        var output = current.port.fluid() ? current.controller.fluidTank(current.port) : null;
        if ((inventory == null || inventory.getStackInSlot(1).isEmpty()) && (output == null || output.isEmpty())) {
            return;
        }
        Direction outward = current.port.side();
        BlockPos adjacent = worldPosition.relative(outward);
        var chunk = level.getChunkSource().getChunkNow(adjacent.getX() >> 4, adjacent.getZ() >> 4);
        var target = chunk == null ? null : chunk.getBlockEntity(adjacent);
        if (target == null) {
            return;
        }
        Direction side = outward.getOpposite();
        if (inventory != null) {
            target.getCapability(ForgeCapabilities.ITEM_HANDLER, side).ifPresent(destination -> pushItems(inventory, destination));
        } else if (output != null) {
            target.getCapability(ForgeCapabilities.FLUID_HANDLER, side).ifPresent(destination -> pushFluid(output, destination));
        }
    }

    private FissionItemBuffer itemBuffer(Binding connection) {
        return connection.port.itemFuel() ? connection.controller.fuelItems(connection.port.filter())
                : connection.controller.irradiatorItems(localIrradiatorItems());
    }

    private static void pushItems(FissionItemBuffer source, IItemHandler target) {
        var offer = source.getStackInSlot(1).copy();
        int accepted = offer.getCount() - ItemHandlerHelper.insertItem(target, offer.copy(), true).getCount();
        if (accepted <= 0) {
            return;
        }
        var reserved = source.extractItem(1, accepted, false);
        ItemStack remaining = reserved;
        try {
            remaining = ItemHandlerHelper.insertItem(target, reserved.copy(), false);
        } finally {
            if (!remaining.isEmpty()) {
                source.restoreOutput(remaining);
            }
        }
    }

    private static void pushFluid(FluidTank source, IFluidHandler target) {
        int accepted = target.fill(source.getFluid().copy(), FluidAction.SIMULATE);
        if (accepted <= 0) {
            return;
        }
        var reserved = source.drain(accepted, FluidAction.EXECUTE);
        int sent = 0;
        try {
            sent = Math.max(0, Math.min(reserved.getAmount(), target.fill(reserved.copy(), FluidAction.EXECUTE)));
        } finally {
            if (sent < reserved.getAmount()) {
                reserved.shrink(sent);
                // Restoring a transfer can exceed the nominal capacity of a migrated tank.
                FluidStack remaining = source.getFluid().copy();
                if (remaining.isEmpty()) {
                    source.setFluid(reserved);
                } else {
                    remaining.grow(reserved.getAmount());
                    source.setFluid(remaining);
                }
            }
        }
    }

    @Nullable ResourceLocation fuelFilter() {
        return localFuelItems == null ? fuelFilter : localFuelItems.filter();
    }

    boolean selectVesselFilter(@Nullable ResourceLocation next) {
        if (localVesselFuel == null || assembled() || !localVesselFuel.tank(false).isEmpty() || Objects.equals(fuelFilter, next)) {
            return false;
        }
        fuelFilter = next;
        invalidateDetachedFluid();
        setChanged();
        if (level instanceof ServerLevel server) {
            FissionPartBlock.geometryChanged(server, worldPosition);
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (localFuelItems != null) {
            localFuelItems.save(tag);
        }
        if (localPebbleGas != null) {
            tag.put("pebble_gas", localPebbleGas.save());
            if (gasFilter != null) {
                tag.putString("gas_filter", gasFilter.toString());
            }
        }
        if (localSaltCoolant != null) {
            tag.put("heater_coolant", localSaltCoolant.save());
        }
        if (irradiationItems != null) {
            irradiationItems.save(tag);
        }
        if (localVesselFuel != null) {
            tag.put("vessel_fuel", localVesselFuel.save());
        }
        if (fuelFilter != null) {
            tag.putString("fuel_filter", fuelFilter.toString());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (localSaltCoolant != null) {
            var previous = localSaltCoolant.filter();
            localSaltCoolant.load(tag.getCompound("heater_coolant"));
            if (!Objects.equals(previous, localSaltCoolant.filter())) {
                detachedFluidFilterChanged();
            }
            return;
        }
        if (localPebbleGas != null) {
            localPebbleGas.load(tag.getCompound("pebble_gas"));
            var previous = gasFilter;
            gasFilter = tag.contains("gas_filter") ? ResourceLocation.tryParse(tag.getString("gas_filter")) : null;
            if (!Objects.equals(previous, gasFilter)) {
                detachedFluidFilterChanged();
            }
            return;
        }
        if (localFuelItems != null) {
            var previous = localFuelItems.filter();
            localFuelItems.load(tag);
            if (!Objects.equals(previous, localFuelItems.filter())) {
                detachedItemFilterLoaded();
            }
            return;
        }
        if (irradiationItems != null) {
            var previous = irradiationItems.filter();
            irradiationItems.load(tag);
            if (!Objects.equals(previous, irradiationItems.filter())) {
                detachedItemFilterLoaded();
            }
            return;
        }
        if (localVesselFuel != null) {
            localVesselFuel.load(tag.getCompound("vessel_fuel"));
        }
        var next = tag.contains("fuel_filter") ? ResourceLocation.tryParse(tag.getString("fuel_filter")) : null;
        if (!Objects.equals(fuelFilter, next)) {
            fuelFilter = next;
            if (localVesselFuel != null) {
                invalidateDetachedFluid();
            }
            if (level instanceof ServerLevel server) {
                FissionPartBlock.geometryChanged(server, worldPosition);
            }
        }
    }

    private void detachedItemFilterLoaded() {
        invalidateDetachedItems();
        if (level instanceof ServerLevel server) {
            FissionPartBlock.geometryChanged(server, worldPosition);
        }
    }

    private record Binding(SolidFissionController controller, SolidFissionLayout.Port port) {}

    // The legacy fission power port had no reactor energy storage in any of the three variants.
    private final class PowerHandler implements IEnergyStorage {
        private final Binding connection;

        PowerHandler(Binding connection) {
            this.connection = connection;
        }

        @Override public int receiveEnergy(int amount, boolean simulate) { return 0; }
        @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return 0; }
        @Override public int getMaxEnergyStored() { return 0; }
        @Override public boolean canExtract() { return connected(connection) && connection.port.output(); }
        @Override public boolean canReceive() { return connected(connection) && !connection.port.output(); }
    }

    private final class VentHandler implements IFluidHandler {
        private final @Nullable Binding connection;
        private final boolean output;
        private final FluidTank tank;

        private VentHandler(Binding connection) {
            this.connection = connection;
            output = connection.port.output();
            tank = connection.controller.fluidTank(connection.port);
        }

        private VentHandler(boolean output, FluidTank tank) {
            this.connection = null;
            this.output = output;
            this.tank = tank;
        }

        private boolean valid() {
            return connection == null ? !isRemoved() && binding == null && detachedVent == this : connected(connection);
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int index) {
            return index == 0 && valid() ? tank.getFluid().copy() : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int index) {
            return index == 0 && valid() ? tank.getCapacity() : 0;
        }

        @Override
        public boolean isFluidValid(int index, @NotNull FluidStack stack) {
            return index == 0 && !output && valid() && tank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return !output && valid() ? tank.fill(resource, action) : 0;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return output && valid() ? tank.drain(resource, action) : FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int amount, FluidAction action) {
            return output && valid() ? tank.drain(amount, action) : FluidStack.EMPTY;
        }
    }

    private final class ItemPortHandler implements IItemHandler {
        private final @Nullable Binding connection;
        private final boolean output;
        private final FissionItemBuffer inventory;

        private ItemPortHandler(Binding connection) {
            this(connection, connection.port.output(), itemBuffer(connection));
        }

        private ItemPortHandler(@Nullable Binding connection, boolean output, FissionItemBuffer inventory) {
            this.connection = connection;
            this.output = output;
            this.inventory = inventory;
        }

        private boolean valid() {
            return connection == null ? !isRemoved() && binding == null && detachedItemPort == this : connected(connection);
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            if (slot != 0 || !valid()) {
                return ItemStack.EMPTY;
            }
            return inventory.bulkStack(output ? 1 : 0);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) {
                return stack;
            }
            return inventory.insertBulk(0, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 0 && amount > 0 && output && valid()
                    ? inventory.extractItem(1, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot != 0 || !valid()) {
                return 0;
            }
            return inventory.capacity();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot == 0 && !output && valid() && inventory.isItemValid(0, stack);
        }
    }
}
