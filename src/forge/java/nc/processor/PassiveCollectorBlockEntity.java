package nc.processor;

import nc.init.NCCollectors;
import nc.init.NCCollectors.Collector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PassiveCollectorBlockEntity extends BlockEntity {
    private static final Direction[] SIDES = Direction.values();
    // The largest legacy energy buffer is 1,200 ticks at 255 FE/t with a 64x multiplier.
    private static final int MAX_ENERGY = 1200 * 255 * 64;

    private final Collector collector;
    private final ProcessorEnergy power;
    private LazyOptional<IEnergyStorage> energy;
    private LazyOptional<IItemHandler> items;
    private LazyOptional<IFluidHandler> fluids;
    private int stock;
    private double productRemainder;
    private double powerRemainder;
    private boolean transferring;

    public PassiveCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(NCCollectors.ENTITY.get(), pos, state);
        collector = ((PassiveCollectorBlock) state.getBlock()).collector();
        power = new ProcessorEnergy(this, MAX_ENERGY, () ->
                (int) Math.ceil(Math.max(16, NCCollectors.updateInterval()) * collector.power()));
        createCapabilities();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PassiveCollectorBlockEntity machine) {
        machine.generate();
        int interval = NCCollectors.updateInterval();
        if (machine.stock > 0 && NCCollectors.autoOutput()
                && Math.floorMod(level.getGameTime() + pos.hashCode(), interval) == 0) {
            machine.push(level, pos);
        }
    }

    private void generate() {
        double rate = collector.rate();
        int room = collector.capacity() - stock;
        if (rate <= 0 || room <= 0) {
            return;
        }
        double cost = powerRemainder + collector.power();
        int wholeCost = (int) cost;
        if (!power.spend(wholeCost)) {
            return;
        }
        powerRemainder = cost - wholeCost;
        double product = productRemainder + rate;
        int wholeProduct = (int) product;
        productRemainder = product - wholeProduct;
        stock += Math.min(room, wholeProduct);
        setChanged();
    }

    private void push(Level level, BlockPos pos) {
        transferring = true;
        try {
            for (Direction side : SIDES) {
                if (stock == 0) {
                    break;
                }
                var adjacent = pos.relative(side);
                BlockEntity target = level.hasChunkAt(adjacent) ? level.getBlockEntity(adjacent) : null;
                if (target == null) {
                    continue;
                }
                if (collector.producesItems()) {
                    target.getCapability(ForgeCapabilities.ITEM_HANDLER, side.getOpposite()).ifPresent(this::pushItems);
                } else {
                    target.getCapability(ForgeCapabilities.FLUID_HANDLER, side.getOpposite()).ifPresent(this::pushFluid);
                }
            }
        } finally {
            transferring = false;
        }
    }

    private void pushItems(IItemHandler target) {
        ItemStack offer = itemStack(stock);
        int withdrawn = offer.getCount() - ItemHandlerHelper.insertItemStacked(target, offer.copy(), true).getCount();
        if (withdrawn <= 0) {
            return;
        }
        stock -= withdrawn;
        int accepted = 0;
        try {
            accepted = Math.max(0, withdrawn - ItemHandlerHelper.insertItemStacked(target, itemStack(withdrawn), false).getCount());
        } finally {
            stock += withdrawn - accepted;
            if (accepted > 0) {
                setChanged();
            }
        }
    }

    private void pushFluid(IFluidHandler target) {
        FluidStack offer = new FluidStack(collector.fluid(), stock);
        int withdrawn = Math.min(stock, target.fill(offer.copy(), IFluidHandler.FluidAction.SIMULATE));
        if (withdrawn <= 0) {
            return;
        }
        stock -= withdrawn;
        int accepted = 0;
        try {
            accepted = Math.max(0, Math.min(withdrawn, target.fill(new FluidStack(collector.fluid(), withdrawn),
                    IFluidHandler.FluidAction.EXECUTE)));
        } finally {
            stock += withdrawn - accepted;
            if (accepted > 0) {
                setChanged();
            }
        }
    }

    private static ItemStack itemStack(int amount) {
        return amount <= 0 ? ItemStack.EMPTY : new ItemStack(Items.COBBLESTONE, Math.min(64, amount));
    }

    void dropItems(Level level, BlockPos pos) {
        while (stock > 0) {
            ItemStack dropped = itemStack(stock);
            stock -= dropped.getCount();
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), dropped);
        }
    }

    private int extract(int amount, boolean simulate) {
        int offered = Math.max(0, Math.min(amount, stock));
        if (!simulate && offered > 0) {
            stock -= offered;
            setChanged();
        }
        return offered;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (!isRemoved()) {
            if (capability == ForgeCapabilities.ITEM_HANDLER && collector.producesItems()) {
                return items.cast();
            }
            if (capability == ForgeCapabilities.FLUID_HANDLER && !collector.producesItems()) {
                return fluids.cast();
            }
            if (capability == ForgeCapabilities.ENERGY && collector.power() > 0) {
                return energy.cast();
            }
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        items.invalidate();
        fluids.invalidate();
        energy.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        createCapabilities();
    }

    private void createCapabilities() {
        items = LazyOptional.of(ItemOutlet::new);
        fluids = LazyOptional.of(FluidOutlet::new);
        energy = LazyOptional.of(() -> power);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("stock", stock);
        tag.putDouble("product_remainder", productRemainder);
        tag.putDouble("power_remainder", powerRemainder);
        tag.putInt("energy", power.getEnergyStored());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stock = Math.max(0, tag.getInt("stock"));
        productRemainder = readRemainder(tag.getDouble("product_remainder"));
        powerRemainder = readRemainder(tag.getDouble("power_remainder"));
        power.load(tag.getInt("energy"));
    }

    private static double readRemainder(double value) {
        return value >= 0 && value < 1 ? value : 0;
    }

    private final class ItemOutlet implements IItemHandler {
        private final LazyOptional<IItemHandler> lifetime = items;

        private boolean available(int slot) {
            return slot == 0 && lifetime.isPresent() && !isRemoved() && !transferring;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return available(slot) ? itemStack(stock) : ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return available(slot) ? itemStack(extract(Math.min(64, amount), simulate)) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return available(slot) ? Math.min(64, collector.capacity()) : 0;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return false;
        }
    }

    private final class FluidOutlet implements IFluidHandler {
        private final LazyOptional<IFluidHandler> lifetime = fluids;

        private boolean available() {
            return lifetime.isPresent() && !isRemoved() && !transferring;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int index) {
            return index == 0 && available() && stock > 0 ? new FluidStack(collector.fluid(), stock) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int index) {
            return index == 0 && available() ? Math.max(stock, collector.capacity()) : 0;
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack stack, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack stack, FluidAction action) {
            return !stack.isEmpty() && stack.getFluid() == collector.fluid() && !stack.hasTag()
                    ? drain(stack.getAmount(), action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int amount, FluidAction action) {
            if (!available() || amount <= 0 || stock == 0) {
                return FluidStack.EMPTY;
            }
            return new FluidStack(collector.fluid(), extract(amount, action.simulate()));
        }
    }
}
