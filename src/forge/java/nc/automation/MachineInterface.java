package nc.automation;

import nc.compat.mekanism.GasDisposal;
import nc.init.NCAutomation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.EmptyFluidHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.EmptyHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public final class MachineInterface extends BlockEntity {
    private static final Direction[] FACES = Direction.values();
    private static final IEnergyStorage EMPTY_ENERGY = new EnergyStorage(0);

    private final Map<Port, Relay<?>> relays = new HashMap<>();
    private @Nullable BlockEntity target;
    private boolean available;
    private boolean notifyNeighbors;

    public MachineInterface(BlockPos pos, BlockState state) {
        super(NCAutomation.INTERFACE_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        available = true;
        refreshTarget();
    }

    @Nullable BlockEntity refreshTarget() {
        if (!available || isRemoved() || !(level instanceof ServerLevel server)) {
            return null;
        }
        BlockEntity next = null;
        for (Direction side : FACES) {
            BlockEntity candidate = loadedEntity(server, worldPosition.relative(side));
            if (candidate instanceof MachineInterfaceTarget && !candidate.isRemoved()) {
                next = candidate;
                break;
            }
        }
        if (target != next) {
            target = next;
            clearRelays();
            notifyNeighbors = true;
        }
        return target;
    }

    public static boolean powered(Level level, BlockPos pos, BlockEntity machine) {
        if (level.hasNeighborSignal(pos)) {
            return true;
        }
        if (level instanceof ServerLevel server) {
            for (Direction side : FACES) {
                var adjacent = pos.relative(side);
                if (loadedEntity(server, adjacent) instanceof MachineInterface relay
                        && relay.refreshTarget() == machine && server.hasNeighborSignal(adjacent)) {
                    return true;
                }
            }
        }
        return false;
    }

    static void tick(Level level, BlockPos pos, BlockState state, MachineInterface machine) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        BlockEntity target = machine.refreshTarget();
        if (machine.notifyNeighbors) {
            machine.notifyNeighbors = false;
            server.updateNeighborsAt(pos, state.getBlock());
        }
        if (!(target instanceof MachineInterfaceTarget output)) {
            return;
        }
        for (Direction side : FACES) {
            BlockEntity neighbor = loadedEntity(server, pos.relative(side));
            if (neighbor != null && neighbor != target
                    && !(neighbor instanceof MachineInterface relay && relay.refreshTarget() == target)) {
                var source = target.getCapability(ForgeCapabilities.ENERGY, side).orElse(null);
                if (source != null && source.canExtract() && source.getEnergyStored() > 0) {
                    neighbor.getCapability(ForgeCapabilities.ENERGY, side.getOpposite())
                            .ifPresent(recipient -> output.pushInterfaceEnergy(side, recipient));
                }
            }
        }
    }

    private static @Nullable BlockEntity loadedEntity(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockEntity(pos);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        var host = refreshTarget();
        if (host != null) {
            if (cap == ForgeCapabilities.ITEM_HANDLER) {
                return forward(host, ForgeCapabilities.ITEM_HANDLER, side, EmptyHandler.INSTANCE, Items::new).cast();
            }
            if (cap == ForgeCapabilities.FLUID_HANDLER) {
                return forward(host, ForgeCapabilities.FLUID_HANDLER, side, EmptyFluidHandler.INSTANCE, Fluids::new).cast();
            }
            if (cap == ForgeCapabilities.ENERGY) {
                return forward(host, ForgeCapabilities.ENERGY, side, EMPTY_ENERGY, Energy::new).cast();
            }
            if (ModList.get().isLoaded("mekanism") && GasDisposal.handles(cap)) {
                return GasDisposal.forward(this, host, side).cast();
            }
        }
        return super.getCapability(cap, side);
    }

    public <T> LazyOptional<T> forward(BlockEntity host, Capability<T> cap, @Nullable Direction side, T fallback,
                                       Function<Supplier<T>, T> proxy) {
        Port port = new Port(cap, side);
        LazyOptional<T> source = host.getCapability(cap, side);
        Relay<?> cached = relays.get(port);
        if (cached != null && cached.source == source && cached.access.isPresent()) {
            return cached.access.cast();
        }
        if (cached != null) {
            relays.remove(port);
            cached.close();
        }
        if (!source.isPresent()) {
            return LazyOptional.empty();
        }
        Relay<T> relay = new Relay<>(source, fallback, proxy);
        relays.put(port, relay);
        return relay.access;
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        available = false;
        target = null;
        clearRelays();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        available = true;
    }

    private void clearRelays() {
        var previous = List.copyOf(relays.values());
        relays.clear();
        previous.forEach(Relay::close);
    }

    private record Port(Capability<?> capability, @Nullable Direction face) {}

    private static final class Relay<T> {
        private final LazyOptional<T> source;
        private final LazyOptional<T> access;
        private final NonNullConsumer<LazyOptional<T>> invalidation;

        private Relay(LazyOptional<T> source, T fallback, Function<Supplier<T>, T> proxy) {
            this.source = source;
            access = LazyOptional.of(() -> proxy.apply(() -> activeSource(fallback)));
            invalidation = ignored -> access.invalidate();
            source.addListener(invalidation);
        }

        private T activeSource(T fallback) {
            return access.isPresent() ? source.orElse(fallback) : fallback;
        }

        void close() {
            if (source.isPresent()) {
                source.removeListener(invalidation);
            }
            access.invalidate();
        }
    }

    private record Items(Supplier<IItemHandler> handler) implements IItemHandler {
        @Override public int getSlots() { return handler.get().getSlots(); }
        @Override public @NotNull ItemStack getStackInSlot(int slot) { return handler.get().getStackInSlot(slot); }
        @Override public int getSlotLimit(int slot) { return handler.get().getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return handler.get().isItemValid(slot, stack); }
        @Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return handler.get().insertItem(slot, stack, simulate);
        }
        @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return handler.get().extractItem(slot, amount, simulate);
        }
    }

    private record Fluids(Supplier<IFluidHandler> handler) implements IFluidHandler {
        @Override public int getTanks() { return handler.get().getTanks(); }
        @Override public @NotNull FluidStack getFluidInTank(int tank) { return handler.get().getFluidInTank(tank); }
        @Override public int getTankCapacity(int tank) { return handler.get().getTankCapacity(tank); }
        @Override public boolean isFluidValid(int tank, @NotNull FluidStack stack) { return handler.get().isFluidValid(tank, stack); }
        @Override public int fill(FluidStack resource, FluidAction action) { return handler.get().fill(resource, action); }
        @Override public @NotNull FluidStack drain(FluidStack resource, FluidAction action) { return handler.get().drain(resource, action); }
        @Override public @NotNull FluidStack drain(int amount, FluidAction action) { return handler.get().drain(amount, action); }
    }

    private record Energy(Supplier<IEnergyStorage> handler) implements IEnergyStorage {
        @Override public int receiveEnergy(int amount, boolean simulate) { return handler.get().receiveEnergy(amount, simulate); }
        @Override public int extractEnergy(int amount, boolean simulate) { return handler.get().extractEnergy(amount, simulate); }
        @Override public int getEnergyStored() { return handler.get().getEnergyStored(); }
        @Override public int getMaxEnergyStored() { return handler.get().getMaxEnergyStored(); }
        @Override public boolean canExtract() { return handler.get().canExtract(); }
        @Override public boolean canReceive() { return handler.get().canReceive(); }
    }
}
