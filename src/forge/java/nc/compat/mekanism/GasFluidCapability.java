package nc.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import nc.NuclearCraft;
import nc.automation.MachineInterface;
import nc.automation.UniversalBin;
import nc.fluid.IndexedFluidHandler;
import nc.init.NCItemProcessors;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GasFluidCapability {
    private GasFluidCapability() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, GasFluidCapability::attach);
        MinecraftForge.EVENT_BUS.addListener((TagsUpdatedEvent event) -> GasFluidPhases.clear());
    }

    private static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        BlockEntity owner = event.getObject();
        var type = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(owner.getType());
        if (type == null || !NuclearCraft.MOD_ID.equals(type.getNamespace())
                || owner instanceof UniversalBin || owner instanceof MachineInterface) {
            return;
        }
        Provider provider = new Provider(owner);
        event.addCapability(NuclearCraft.id("mekanism_gas"), provider);
        event.addListener(provider::invalidate);
    }

    private static final class Provider implements ICapabilityProvider {
        private final BlockEntity owner;
        private final Map<Direction, Route> sides = new HashMap<>();

        private Provider(BlockEntity owner) {
            this.owner = owner;
        }

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
            if (!GasDisposal.handles(capability) || !NCItemProcessors.mekanismGasEnabled() || owner.isRemoved()) {
                return LazyOptional.empty();
            }
            Route cached = sides.get(side);
            if (cached != null) {
                return cached.gas.cast();
            }
            LazyOptional<IFluidHandler> backing = owner.getCapability(ForgeCapabilities.FLUID_HANDLER, side);
            IFluidHandler handler = backing.orElse(null);
            if (handler == null || (!(handler instanceof IndexedFluidHandler) && handler.getTanks() != 1)) {
                return LazyOptional.empty();
            }
            Route route = new Route(owner, handler);
            sides.put(side, route);
            backing.addListener(ignored -> {
                sides.remove(side, route);
                route.invalidate();
            });
            return route.gas.cast();
        }

        private void invalidate() {
            List<Route> routes = List.copyOf(sides.values());
            sides.clear();
            routes.forEach(Route::invalidate);
        }
    }

    private static final class Route implements IGasHandler {
        private final BlockEntity owner;
        private final IFluidHandler fluid;
        private final LazyOptional<IGasHandler> gas = LazyOptional.of(() -> this);
        private boolean valid = true;

        private Route(BlockEntity owner, IFluidHandler fluid) {
            this.owner = owner;
            this.fluid = fluid;
        }

        private void invalidate() {
            valid = false;
            gas.invalidate();
        }

        @Nullable
        private GasFluidPhases phases() {
            var level = owner.getLevel();
            return valid && !owner.isRemoved() && level != null && NCItemProcessors.mekanismGasEnabled()
                    ? GasFluidPhases.get(level.getRecipeManager()) : null;
        }

        @Override
        public int getTanks() {
            return valid && !owner.isRemoved() && NCItemProcessors.mekanismGasEnabled() ? fluid.getTanks() : 0;
        }

        @Override
        public GasStack getChemicalInTank(int tank) {
            var phases = phases();
            return phases == null || tank < 0 || tank >= getTanks() ? GasStack.EMPTY
                    : phases.gas(fluid.getFluidInTank(tank));
        }

        @Override
        public void setChemicalInTank(int tank, GasStack stack) {
            // External automation cannot bypass the sided fill and drain rules.
        }

        @Override
        public long getTankCapacity(int tank) {
            return tank >= 0 && tank < getTanks() ? fluid.getTankCapacity(tank) : 0;
        }

        @Override
        public boolean isValid(int tank, GasStack stack) {
            var phases = phases();
            return phases != null && !stack.isEmpty() && getTankCapacity(tank) > 0 && phases.fluids(stack).stream()
                    .anyMatch(type -> fluid.isFluidValid(tank, new FluidStack(type, 1)));
        }

        @Override
        public GasStack insertChemical(int tank, GasStack stack, Action action) {
            var phases = phases();
            if (phases == null || stack.isEmpty() || getTankCapacity(tank) == 0) {
                return stack;
            }
            int requested = (int) Math.min(Integer.MAX_VALUE, stack.getAmount());
            FluidStack stored = fluid.getFluidInTank(tank);
            if (!stored.isEmpty()) {
                GasStack existing = phases.gas(stored);
                return existing.isEmpty() || existing.getType() != stack.getType() ? stack
                        : remainder(stack, fill(tank, new FluidStack(stored, requested), action.toFluidAction()));
            }
            for (Fluid type : phases.fluids(stack)) {
                int filled = fill(tank, new FluidStack(type, requested), action.toFluidAction());
                if (filled > 0) {
                    return remainder(stack, filled);
                }
            }
            return stack;
        }

        private static GasStack remainder(GasStack stack, int filled) {
            return filled == 0 ? stack : filled == stack.getAmount() ? GasStack.EMPTY
                    : new GasStack(stack, stack.getAmount() - filled);
        }

        private int fill(int tank, FluidStack stack, IFluidHandler.FluidAction action) {
            if (!fluid.isFluidValid(tank, stack)) {
                return 0;
            }
            return fluid instanceof IndexedFluidHandler indexed ? indexed.fillTank(tank, stack, action)
                    : fluid.fill(stack, action);
        }

        @Override
        public GasStack extractChemical(int tank, long amount, Action action) {
            var phases = phases();
            if (phases == null || amount <= 0 || getTankCapacity(tank) == 0) {
                return GasStack.EMPTY;
            }
            FluidStack stored = fluid.getFluidInTank(tank);
            if (phases.gas(stored).isEmpty()) {
                return GasStack.EMPTY;
            }
            FluidStack requested = new FluidStack(stored, (int) Math.min(amount, Integer.MAX_VALUE));
            FluidStack drained = fluid instanceof IndexedFluidHandler indexed
                    ? indexed.drainTank(tank, requested, action.toFluidAction())
                    : fluid.drain(requested, action.toFluidAction());
            return phases.gas(drained);
        }
    }
}
