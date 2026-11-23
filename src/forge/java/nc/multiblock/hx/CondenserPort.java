package nc.multiblock.hx;

import nc.init.NCCondenser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CondenserPort extends BlockEntity {
    private Binding binding;
    private LazyOptional<IFluidHandler> fluid = LazyOptional.empty();

    public CondenserPort(BlockPos pos, BlockState state) {
        super(NCCondenser.PORT_ENTITY.get(), pos, state);
    }

    void bind(CondenserController controller, CondenserLayout.Port port, FluidTank tank) {
        clearBinding();
        Binding next = new Binding(controller, port.side());
        binding = next;
        fluid = LazyOptional.of(() -> new TankView(next, port.outlet(), tank));
    }

    void unbind(CondenserController controller) {
        if (binding != null && binding.controller == controller) {
            clearBinding();
        }
    }

    private void clearBinding() {
        binding = null;
        fluid.invalidate();
        fluid = LazyOptional.empty();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        clearBinding();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        Binding current = binding;
        if (capability == ForgeCapabilities.FLUID_HANDLER && current != null
                && connected(current) && (side == null || side == current.side)) {
            return fluid.cast();
        }
        return super.getCapability(capability, side);
    }

    private boolean connected(Binding attached) {
        return !isRemoved() && binding == attached && attached.controller.assembled();
    }

    private record Binding(CondenserController controller, Direction side) {}

    private final class TankView implements IFluidHandler {
        private final Binding attached;
        private final FluidTank tank;
        private final boolean output;

        TankView(Binding attached, boolean output, FluidTank tank) {
            this.attached = attached;
            this.output = output;
            this.tank = tank;
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

}
