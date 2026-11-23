package nc.multiblock.fission;

import nc.init.NCFission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FissionPortBlockEntity extends BlockEntity {
    private Binding binding;
    private @Nullable ResourceLocation fuelFilter;
    private LazyOptional<IFluidHandler> fluid = LazyOptional.empty();
    private LazyOptional<IItemHandler> items = LazyOptional.empty();

    public FissionPortBlockEntity(BlockPos pos, BlockState state) {
        super(NCFission.PORT_ENTITY.get(), pos, state);
    }

    void bind(SolidFissionController controller, SolidFissionLayout.Port port) {
        Binding next = new Binding(controller, port);
        if (next.equals(binding)) {
            return;
        }
        unbind();
        binding = next;
        if (port.fluid()) {
            fluid = LazyOptional.of(() -> new VentHandler(next));
        } else {
            items = LazyOptional.of(() -> new FuelHandler(next));
        }
    }

    void unbind() {
        binding = null;
        fluid.invalidate();
        items.invalidate();
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
        }
        return super.getCapability(capability, side);
    }

    private boolean connected(Binding connection) {
        return !isRemoved() && binding == connection && connection.controller.assembled();
    }

    @Nullable ResourceLocation fuelFilter() {
        return fuelFilter;
    }

    void fuelFilter(@Nullable ResourceLocation next) {
        fuelFilter = next;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (fuelFilter != null) {
            tag.putString("fuel_filter", fuelFilter.toString());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        var next = tag.contains("fuel_filter") ? ResourceLocation.tryParse(tag.getString("fuel_filter")) : null;
        if (!java.util.Objects.equals(fuelFilter, next)) {
            fuelFilter = next;
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                FissionPartBlock.geometryChanged(server, worldPosition);
            }
        }
    }

    private record Binding(SolidFissionController controller, SolidFissionLayout.Port port) {}

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

        private FuelHandler(Binding connection) {
            this.connection = connection;
            inventory = connection.controller.inventory();
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return slot == 0 && connected(connection) ? inventory.getStackInSlot(connection.port.output() ? 1 : 0).copy() : ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return isItemValid(slot, stack) ? inventory.insertItem(0, stack, simulate) : stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 0 && connection.port.output() && connected(connection)
                    ? inventory.extractItem(1, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 && connected(connection) ? inventory.getSlotLimit(connection.port.output() ? 1 : 0) : 0;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot == 0 && !connection.port.output() && connected(connection) && connection.controller.acceptsFuel(stack);
        }
    }
}
