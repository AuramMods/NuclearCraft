package nc.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;

public abstract class EnergyBankBlockEntity extends BlockEntity {
    static final Direction[] FACES = Direction.values();

    private final EnumMap<Direction, EnergyPort> ports = new EnumMap<>(Direction.class);
    private @Nullable EnergyBank bank;
    private boolean unloading;
    private long share;
    private long transferTick = Long.MIN_VALUE;
    private long received;
    private long extracted;

    protected EnergyBankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected abstract long localCapacity();

    protected long minimumBankCapacity() {
        return 0;
    }

    private long localStorageLimit() {
        return Math.max(localCapacity(), minimumBankCapacity());
    }

    protected abstract int configRevision();

    protected abstract boolean sharesBankWith(EnergyBankBlockEntity other);

    protected abstract boolean canReceive(Direction face);

    protected abstract boolean canExtract(Direction face);

    protected long transferLimit() {
        return Long.MAX_VALUE;
    }

    protected long generated(long tick) {
        return 0;
    }

    protected final void pushPower() {
        if (stored() == 0) {
            return;
        }
        for (Direction side : FACES) {
            if (!canExtract(side) || connectedAt(side) != null) {
                continue;
            }
            BlockEntity target = neighborAt(side);
            if (target != null) {
                target.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).ifPresent(this::push);
            }
        }
    }

    private void push(IEnergyStorage target) {
        var bank = bank();
        int offered = available(bank, Integer.MAX_VALUE, false);
        if (offered == 0 || bank.transferring()) {
            return;
        }
        int requested = Math.max(0, Math.min(offered, target.receiveEnergy(offered, true)));
        int reserved = available(bank, requested, false);
        if (reserved == 0) {
            return;
        }
        bank.setTransferring(true);
        bank.setCharge(bank.charge() - reserved);
        int sent = 0;
        try {
            sent = Math.max(0, Math.min(reserved, target.receiveEnergy(reserved, false)));
        } finally {
            if (sent < reserved) {
                bank.setCharge(bank.charge() + reserved - sent);
            }
            recordTransfer(sent, false);
            bank.setTransferring(false);
        }
    }

    private int available(EnergyBank bank, int request, boolean insert) {
        long used = level != null && transferTick == level.getGameTime() ? (insert ? received : extracted) : 0;
        long quantity = insert ? bank.capacity() - bank.charge() : bank.charge();
        return (int) Math.max(0, Math.min(Math.min(request, transferLimit() - used), quantity));
    }

    private void recordTransfer(int amount, boolean insert) {
        long now = level.getGameTime();
        if (transferTick != now) {
            transferTick = now;
            received = 0;
            extracted = 0;
        }
        if (insert) {
            received += amount;
        } else {
            extracted += amount;
        }
    }

    private EnergyBank bank() {
        if (bank == null || !bank.usesConfig(configRevision())) {
            return EnergyBank.form(this);
        }
        return bank;
    }

    void join(EnergyBank newBank) {
        bank = newBank;
    }

    void leave(EnergyBank oldBank) {
        if (bank == oldBank) {
            bank = null;
        }
    }

    void invalidateBank() {
        if (bank != null) {
            bank.invalidate();
        }
    }

    private @Nullable BlockEntity neighborAt(Direction side) {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        BlockPos pos = worldPosition.relative(side);
        var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockEntity(pos);
    }

    @Nullable EnergyBankBlockEntity connectedAt(Direction side) {
        return neighborAt(side) instanceof EnergyBankBlockEntity part && part.available() && sharesBankWith(part) ? part : null;
    }

    private boolean available() {
        return level instanceof ServerLevel && !isRemoved() && !unloading;
    }

    long localCharge() {
        return share;
    }

    void setLocalCharge(long amount) {
        if (share != amount) {
            share = amount;
            setChanged();
        }
    }

    protected final void addCharge(long amount) {
        if (amount > 0 && available()) {
            EnergyBank bank = bank();
            bank.setCharge(bank.charge() + Math.min(bank.capacity() - bank.charge(), amount));
        }
    }

    protected final void generateTick(long tick) {
        bank().generate(tick);
    }

    public long stored() {
        return available() ? bank().charge() : share;
    }

    public long capacity() {
        return available() ? bank().capacity() : localStorageLimit();
    }

    protected final int comparator() {
        return available() ? bank().comparator() : 0;
    }

    protected final void refreshPort(Direction side) {
        EnergyPort port = ports.remove(side);
        if (port != null) {
            port.handler.invalidate();
        }
    }

    void refreshPorts() {
        var previous = List.copyOf(ports.values());
        ports.clear();
        for (EnergyPort port : previous) {
            port.handler.invalidate();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        unloading = false;
        if (available()) {
            for (Direction side : FACES) {
                EnergyBankBlockEntity neighbor = connectedAt(side);
                if (neighbor != null) {
                    neighbor.invalidateBank();
                }
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateBank();
    }

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        invalidateBank();
        super.onChunkUnloaded();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        refreshPorts();
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap != ForgeCapabilities.ENERGY || !available()) {
            return super.getCapability(cap, side);
        }
        Direction face = side == null ? Direction.DOWN : side;
        if ((!canReceive(face) && !canExtract(face)) || connectedAt(face) != null) {
            return LazyOptional.empty();
        }
        bank();
        return ports.computeIfAbsent(face, EnergyPort::new).handler.cast();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("energy", share);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("energy")) {
            long saved = Math.max(0, Math.min(localStorageLimit(), tag.getLong("energy")));
            if (share != saved) {
                share = saved;
                invalidateBank();
            }
        }
    }

    private final class EnergyPort implements IEnergyStorage {
        private final Direction face;
        private final LazyOptional<IEnergyStorage> handler = LazyOptional.of(() -> this);

        private EnergyPort(Direction face) {
            this.face = face;
        }

        private boolean usable() {
            return handler.isPresent() && available() && connectedAt(face) == null;
        }

        private int transfer(int request, boolean insert, boolean simulate) {
            if (!(insert ? canReceive() : canExtract())) {
                return 0;
            }
            EnergyBank bank = bank();
            if (bank.transferring() || !handler.isPresent()) {
                return 0;
            }
            int amount = EnergyBankBlockEntity.this.available(bank, request, insert);
            if (amount > 0 && !simulate) {
                bank.setCharge(bank.charge() + (insert ? amount : -amount));
                recordTransfer(amount, insert);
            }
            return amount;
        }

        @Override public int receiveEnergy(int max, boolean simulate) { return transfer(max, true, simulate); }
        @Override public int extractEnergy(int max, boolean simulate) { return transfer(max, false, simulate); }
        @Override public int getEnergyStored() { return usable() ? (int) Math.min(Integer.MAX_VALUE, stored()) : 0; }
        @Override public int getMaxEnergyStored() { return usable() ? (int) Math.min(Integer.MAX_VALUE, capacity()) : 0; }
        @Override public boolean canReceive() { return usable() && EnergyBankBlockEntity.this.canReceive(face); }
        @Override public boolean canExtract() { return usable() && EnergyBankBlockEntity.this.canExtract(face); }
    }
}
