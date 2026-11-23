package nc.multiblock.turbine;

import nc.init.NCTurbine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TurbinePort extends BlockEntity {
    private Binding binding;
    private LazyOptional<IFluidHandler> fluid = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> energy = LazyOptional.empty();

    public TurbinePort(BlockPos pos, BlockState state) {
        super(NCTurbine.PORT_ENTITY.get(), pos, state);
    }

    void bind(TurbineController controller, TurbineLayout.Port port) {
        unbind();
        Binding next = new Binding(controller, port.side());
        binding = next;
        if (port.part().dynamo()) {
            energy = LazyOptional.of(() -> new DynamoOutput(next));
        } else {
            fluid = LazyOptional.of(() -> new SteamPort(next, port.part() == TurbinePartBlock.Part.OUTLET));
        }
    }

    void unbind() {
        binding = null;
        fluid.invalidate();
        energy.invalidate();
        fluid = LazyOptional.empty();
        energy = LazyOptional.empty();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        unbind();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        Binding current = binding;
        if (current != null && connected(current) && (side == null || side == current.side)) {
            if (capability == ForgeCapabilities.FLUID_HANDLER) {
                return fluid.cast();
            }
            if (capability == ForgeCapabilities.ENERGY) {
                return energy.cast();
            }
        }
        return super.getCapability(capability, side);
    }

    private boolean connected(Binding attached) {
        return !isRemoved() && binding == attached && attached.controller.assembled();
    }

    private record Binding(TurbineController controller, Direction side) {}

    private final class SteamPort implements IFluidHandler {
        private final Binding attached;
        private final FluidTank tank;
        private final boolean output;

        SteamPort(Binding attached, boolean output) {
            this.attached = attached;
            this.output = output;
            tank = output ? attached.controller.exhaust() : attached.controller.steam();
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int index) {
            return index == 0 && connected(attached) ? tank.getFluid().copy() : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int index) {
            return index == 0 && connected(attached) ? tank.getCapacity() : 0;
        }

        @Override
        public boolean isFluidValid(int index, @NotNull FluidStack stack) {
            return index == 0 && !output && connected(attached) && tank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return !output && connected(attached) ? tank.fill(resource, action) : 0;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return output && connected(attached) ? tank.drain(resource, action) : FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int amount, FluidAction action) {
            return output && connected(attached) && amount > 0 ? tank.drain(amount, action) : FluidStack.EMPTY;
        }
    }

    private final class DynamoOutput implements IEnergyStorage {
        private final Binding attached;

        DynamoOutput(Binding attached) {
            this.attached = attached;
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            return connected(attached) ? attached.controller.extractEnergy(amount, simulate) : 0;
        }

        @Override
        public int getEnergyStored() {
            return connected(attached) ? attached.controller.energyStored() : 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return connected(attached) ? attached.controller.energyCapacity() : 0;
        }

        @Override
        public boolean canExtract() {
            return connected(attached);
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }
}
