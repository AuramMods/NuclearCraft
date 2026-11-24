package nc.distributor;

import nc.energy.EnergyBankBlockEntity;
import nc.init.NCDistributors;
import nc.init.NCDistributors.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;

public final class DistributorBlockEntity extends EnergyBankBlockEntity {
    static final int ITEM_CAPACITY = 64;
    static final int FLUID_CAPACITY = 4_000;
    private static final int ENERGY_CAPACITY = 16_000;

    private final Part part;
    private final EnumMap<Direction, Port> ports = new EnumMap<>(Direction.class);
    private final int[] outputOffsets = new int[3];
    private @Nullable DistributorNetwork network;
    private ItemStack items = ItemStack.EMPTY;
    private FluidStack fluid = FluidStack.EMPTY;

    public DistributorBlockEntity(BlockPos pos, BlockState state) {
        super(NCDistributors.ENTITY.get(), pos, state);
        part = ((DistributorBlock) state.getBlock()).part();
    }

    static void tick(Level level, BlockPos pos, BlockState state, DistributorBlockEntity entity) {
        entity.network().distribute(level.getGameTime());
    }

    @Override
    protected long localCapacity() {
        return ENERGY_CAPACITY;
    }

    @Override
    protected int configRevision() {
        return 0;
    }

    @Override
    protected boolean sharesBankWith(EnergyBankBlockEntity other) {
        return other instanceof DistributorBlockEntity;
    }

    @Override
    protected boolean canReceive(Direction face) {
        return part == Part.INLET;
    }

    @Override
    protected boolean canExtract(Direction face) {
        return part == Part.OUTLET;
    }

    boolean outlet() {
        return part == Part.OUTLET;
    }

    <T> LazyOptional<T> neighborCapability(Direction direction, Capability<T> capability) {
        var neighbor = exteriorFace(direction) ? neighborAt(direction) : null;
        return neighbor == null ? LazyOptional.empty() : neighbor.getCapability(capability, direction.getOpposite());
    }

    void sendEnergy(IEnergyStorage target, int amount) {
        pushEnergyTo(target, amount);
    }

    ItemStack localItems() {
        return items;
    }

    void setLocalItems(ItemStack items) {
        if (!ItemStack.matches(this.items, items)) {
            this.items = items;
            setChanged();
        }
    }

    FluidStack localFluid() {
        return fluid;
    }

    void setLocalFluid(FluidStack fluid) {
        if (!this.fluid.isFluidStackIdentical(fluid)) {
            this.fluid = fluid;
            setChanged();
        }
    }

    int outputOffset(int channel) {
        return outputOffsets[channel];
    }

    void setOutputOffset(int channel, int offset) {
        if (outputOffsets[channel] != offset) {
            outputOffsets[channel] = offset;
            setChanged();
        }
    }

    private DistributorNetwork network() {
        if (network == null) {
            var parts = bankMembers().stream().map(DistributorBlockEntity.class::cast).toList();
            var joined = new DistributorNetwork(parts);
            for (DistributorBlockEntity member : parts) {
                member.network = joined;
            }
        }
        return network;
    }

    @Override
    protected void refreshPorts() {
        network = null;
        var oldPorts = List.copyOf(ports.values());
        ports.clear();
        for (Port port : oldPorts) {
            port.live = false;
        }
        super.refreshPorts();
        for (Port port : oldPorts) {
            port.itemHandler.invalidate();
            port.fluidHandler.invalidate();
        }
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability != ForgeCapabilities.ITEM_HANDLER && capability != ForgeCapabilities.FLUID_HANDLER) {
            return super.getCapability(capability, side);
        }
        Direction face = side == null ? Direction.DOWN : side;
        if (part == Part.BUFFER || !exteriorFace(face)) {
            return LazyOptional.empty();
        }
        var current = network();
        var port = ports.computeIfAbsent(face, direction -> new Port(current, direction));
        return capability == ForgeCapabilities.ITEM_HANDLER ? port.itemHandler.cast() : port.fluidHandler.cast();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("items", items.save(new CompoundTag()));
        tag.put("fluid", fluid.writeToNBT(new CompoundTag()));
        tag.putIntArray("output_offsets", outputOffsets);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("items", Tag.TAG_COMPOUND)) {
            var saved = ItemStack.of(tag.getCompound("items"));
            if (saved.getCount() > ITEM_CAPACITY) {
                saved.setCount(ITEM_CAPACITY);
            }
            items = saved;
        }
        if (tag.contains("fluid", Tag.TAG_COMPOUND)) {
            var saved = FluidStack.loadFluidStackFromNBT(tag.getCompound("fluid"));
            if (saved.getAmount() > FLUID_CAPACITY) {
                saved.setAmount(FLUID_CAPACITY);
            }
            fluid = saved;
        }
        var offsets = tag.getIntArray("output_offsets");
        if (offsets.length == outputOffsets.length) {
            System.arraycopy(offsets, 0, outputOffsets, 0, offsets.length);
        }
    }

    private final class Port implements IItemHandler, IFluidHandler {
        private final DistributorNetwork group;
        private final Direction face;
        private final LazyOptional<IItemHandler> itemHandler = LazyOptional.of(() -> this);
        private final LazyOptional<IFluidHandler> fluidHandler = LazyOptional.of(() -> this);
        private boolean live = true;

        Port(DistributorNetwork group, Direction face) {
            this.group = group;
            this.face = face;
        }

        private boolean open(int slot) {
            return live && slot == 0 && exteriorFace(face);
        }

        @Override
        public int getSlots() {
            return open(0) ? 1 : 0;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return open(slot) ? group.contents() : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return open(slot) ? group.itemCapacity() : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return open(slot) && canReceive(face) && !stack.isEmpty();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return isItemValid(slot, stack) ? group.insert(stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return open(slot) && canExtract(face) ? group.extract(amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getTanks() {
            return getSlots();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return open(tank) ? group.fluid() : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return open(tank) ? group.fluidCapacity() : 0;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return open(tank) && canReceive(face) && !stack.isEmpty();
        }

        @Override
        public int fill(FluidStack stack, FluidAction action) {
            return isFluidValid(0, stack) ? group.fill(stack, action.simulate()) : 0;
        }

        @Override
        public FluidStack drain(FluidStack stack, FluidAction action) {
            if (!open(0) || !canExtract(face) || stack.isEmpty() || !stack.isFluidEqual(group.fluid())) {
                return FluidStack.EMPTY;
            }
            return group.drain(stack.getAmount(), action.simulate());
        }

        @Override
        public FluidStack drain(int amount, FluidAction action) {
            return open(0) && canExtract(face) ? group.drain(amount, action.simulate()) : FluidStack.EMPTY;
        }
    }
}
