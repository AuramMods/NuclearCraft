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
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class FissionPortBlockEntity extends BlockEntity {
    private final @Nullable IrradiatorItems irradiationItems;
    private final @Nullable FissionFuelItems localFuelItems;
    private final @Nullable PebbleCooling.Tanks localPebbleGas;
    private Binding binding;
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
                ? new PebbleCooling.Tanks(((FissionWallBlock) state.getBlock()).coolant(), this, this::gasFilter) : null;
        localFuelItems = switch (FissionPartBlock.role(state)) {
            case FUEL_PORT -> new FissionFuelItems(this, FissionVariant.SOLID, null);
            case CHAMBER_PORT -> new FissionFuelItems(this, FissionVariant.PEBBLE, null);
            default -> null;
        };
        irradiationItems = FissionPartBlock.role(state) == Role.IRRADIATOR_PORT ? new IrradiatorItems(this) : null;
    }

    void bind(SolidFissionController controller, SolidFissionLayout.Port port) {
        Binding next = new Binding(controller, port);
        if (next.equals(binding)) {
            return;
        }
        unbind();
        binding = next;
        if (port.role() == Role.POWER_PORT) {
            power = LazyOptional.of(() -> new PowerHandler(next));
        } else if (port.fluid()) {
            fluid = LazyOptional.of(() -> new VentHandler(next));
        } else {
            items = LazyOptional.of(() -> new FuelHandler(next));
        }
    }

    void unbind() {
        binding = null;
        fluid.invalidate();
        items.invalidate();
        power.invalidate();
        power = LazyOptional.empty();
        fluid = LazyOptional.empty();
        items = LazyOptional.empty();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        unbind();
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

    PebbleCooling.Tanks localPebbleGas() {
        return Objects.requireNonNull(localPebbleGas);
    }

    PebbleCooling.Tanks pebbleGas() {
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
                || (!sample.isEmpty() && !sample.getFluid().is(channel.gas().input()))) {
            return false;
        }
        var next = sample.isEmpty() ? null : ForgeRegistries.FLUIDS.getKey(sample.getFluid());
        if (Objects.equals(gasFilter, next)) {
            return false;
        }
        gasFilter = next;
        setChanged();
        if (level instanceof ServerLevel server) {
            FissionPartBlock.geometryChanged(server, worldPosition);
        }
        return true;
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

    void exportIrradiation(ServerLevel level) {
        Binding current = binding;
        if (current == null || !current.port.output() || irradiatorController() == null) {
            return;
        }
        var inventory = irradiatorItems();
        ItemStack product = inventory.getStackInSlot(1);
        if (product.isEmpty()) {
            return;
        }
        Direction side = current.port.side();
        BlockPos adjacent = worldPosition.relative(side);
        if (!level.hasChunkAt(adjacent)) {
            return;
        }
        var target = level.getBlockEntity(adjacent);
        if (target == null) {
            return;
        }
        var destination = target.getCapability(ForgeCapabilities.ITEM_HANDLER, side.getOpposite()).orElse(null);
        if (destination != null) {
            int available = product.getCount();
            var remainder = ItemHandlerHelper.insertItem(destination, product.copy(), false);
            inventory.extractItem(1, available - remainder.getCount(), false);
        }
    }

    void exportFuelOrGas(ServerLevel level) {
        Binding current = binding;
        if (current == null || !current.port.output() || !connected(current)) {
            return;
        }

        Role role = current.port.role();
        var fuel = current.port.itemFuel() ? current.controller.fuelItems(current.port.fuelFilter()) : null;
        var gas = role == Role.COOLER_PORT ? current.controller.fluidTank(current.port) : null;
        if ((fuel == null || fuel.getStackInSlot(1).isEmpty()) && (gas == null || gas.isEmpty())) {
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
        if (fuel != null) {
            target.getCapability(ForgeCapabilities.ITEM_HANDLER, side).ifPresent(destination -> pushFuel(fuel, destination));
        } else if (gas != null) {
            target.getCapability(ForgeCapabilities.FLUID_HANDLER, side).ifPresent(destination -> pushGas(gas, destination));
        }
    }

    private static void pushFuel(FissionFuelItems source, IItemHandler target) {
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

    private static void pushGas(FluidTank source, IFluidHandler target) {
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
                source.fill(reserved, FluidAction.EXECUTE);
            }
        }
    }

    @Nullable ResourceLocation fuelFilter() {
        return localFuelItems == null ? fuelFilter : localFuelItems.filter();
    }

    void fuelFilter(@Nullable ResourceLocation next) {
        fuelFilter = next;
        setChanged();
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
        if (irradiationItems != null) {
            irradiationItems.save(tag);
        }
        if (fuelFilter != null) {
            tag.putString("fuel_filter", fuelFilter.toString());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (localPebbleGas != null) {
            localPebbleGas.load(tag.getCompound("pebble_gas"));
            var previous = gasFilter;
            gasFilter = tag.contains("gas_filter") ? ResourceLocation.tryParse(tag.getString("gas_filter")) : null;
            if (!Objects.equals(previous, gasFilter) && level instanceof ServerLevel server) {
                FissionPartBlock.geometryChanged(server, worldPosition);
            }
            return;
        }
        if (localFuelItems != null) {
            var previous = localFuelItems.filter();
            localFuelItems.load(tag);
            if (level instanceof ServerLevel server && !Objects.equals(previous, localFuelItems.filter())) {
                FissionPartBlock.geometryChanged(server, worldPosition);
            }
            return;
        }
        if (irradiationItems != null) {
            var previous = irradiationItems.filter();
            irradiationItems.load(tag);
            if (level instanceof ServerLevel server
                    && !Objects.equals(previous, irradiationItems.filter())) {
                FissionPartBlock.geometryChanged(server, worldPosition);
            }
            return;
        }
        var next = tag.contains("fuel_filter") ? ResourceLocation.tryParse(tag.getString("fuel_filter")) : null;
        if (!Objects.equals(fuelFilter, next)) {
            fuelFilter = next;
            if (level instanceof ServerLevel server) {
                FissionPartBlock.geometryChanged(server, worldPosition);
            }
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
        private final Binding connection;
        private final FluidTank tank;

        private VentHandler(Binding connection) {
            this.connection = connection;
            tank = connection.controller.fluidTank(connection.port);
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int index) {
            return index == 0 && connected(connection) ? tank.getFluid().copy() : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int index) {
            return index == 0 && connected(connection) ? tank.getCapacity() : 0;
        }

        @Override
        public boolean isFluidValid(int index, @NotNull FluidStack stack) {
            return index == 0 && !connection.port.output() && connected(connection) && tank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return !connection.port.output() && connected(connection) ? tank.fill(resource, action) : 0;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return connection.port.output() && connected(connection) ? tank.drain(resource, action) : FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int amount, FluidAction action) {
            return connection.port.output() && connected(connection) ? tank.drain(amount, action) : FluidStack.EMPTY;
        }
    }

    private final class FuelHandler implements IItemHandler {
        private final Binding connection;
        private final ItemStackHandler inventory;
        private final @Nullable FissionFuelItems fuel;

        private FuelHandler(Binding connection) {
            this.connection = connection;
            fuel = connection.port.itemFuel() ? connection.controller.fuelItems(connection.port.fuelFilter()) : null;
            inventory = fuel != null ? fuel : connection.controller.irradiatorItems(localIrradiatorItems());
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            if (slot != 0 || !connected(connection)) {
                return ItemStack.EMPTY;
            }
            int index = connection.port.output() ? 1 : 0;
            return fuel == null ? inventory.getStackInSlot(index).copy() : fuel.bulkStack(index);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) {
                return stack;
            }
            return fuel == null ? inventory.insertItem(0, stack, simulate) : fuel.insertBulk(0, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 0 && amount > 0 && connection.port.output() && connected(connection)
                    ? inventory.extractItem(1, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot != 0 || !connected(connection)) {
                return 0;
            }
            return fuel == null ? inventory.getSlotLimit(connection.port.output() ? 1 : 0) : fuel.capacity();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot == 0 && !connection.port.output() && connected(connection) && inventory.isItemValid(0, stack);
        }
    }
}
