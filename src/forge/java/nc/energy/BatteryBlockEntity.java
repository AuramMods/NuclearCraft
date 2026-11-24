package nc.energy;

import nc.energy.BatteryBlock.Mode;
import nc.init.NCBatteries.Tier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;

public final class BatteryBlockEntity extends BlockEntity {
    public static final ModelProperty<Integer> FACE_MODES = new ModelProperty<>();

    private final Tier tier;
    private final EnumMap<Direction, EnergyPort> ports = new EnumMap<>(Direction.class);
    private @Nullable BatteryBank bank;
    private boolean unloading;
    private int share;
    private volatile int modes;
    private int lastComparator;
    private long transferTick = Long.MIN_VALUE;
    private int received;
    private int extracted;

    public BatteryBlockEntity(BlockPos pos, BlockState state, Tier tier) {
        super(tier.entity(), pos, state);
        this.tier = tier;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BatteryBlockEntity battery) {
        int comparator = battery.comparator();
        if (comparator != battery.lastComparator) {
            battery.lastComparator = comparator;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
        if (battery.stored() == 0) {
            return;
        }
        for (Direction side : BatteryBlock.FACES) {
            if (battery.mode(side) != Mode.OUT) {
                continue;
            }
            BlockEntity target = battery.neighborAt(side);
            if (target != null && !(target instanceof BatteryBlockEntity)) {
                target.getCapability(ForgeCapabilities.ENERGY, side.getOpposite())
                        .ifPresent(handler -> battery.push(handler));
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

    private int available(BatteryBank bank, int request, boolean insert) {
        int used = level != null && transferTick == level.getGameTime() ? (insert ? received : extracted) : 0;
        long quantity = insert ? bank.capacity() - bank.charge() : bank.charge();
        return (int) Math.max(0, Math.min(Math.min(request, tier.transfer() - used), quantity));
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

    private BatteryBank bank() {
        if (bank == null || !bank.usesCurrentConfig()) {
            return BatteryBank.form(this);
        }
        return bank;
    }

    void join(BatteryBank newBank) {
        bank = newBank;
    }

    void leave(BatteryBank oldBank) {
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

    @Nullable BatteryBlockEntity batteryAt(Direction side) {
        return neighborAt(side) instanceof BatteryBlockEntity part && part.available() ? part : null;
    }

    private boolean available() {
        return level instanceof ServerLevel && !isRemoved() && !unloading;
    }

    int localCharge() {
        return share;
    }

    int localCapacity() {
        return tier.capacity();
    }

    void setLocalCharge(int amount) {
        if (share != amount) {
            share = amount;
            setChanged();
        }
    }

    void setPlacedCharge(int amount) {
        if (amount > 0) {
            BatteryBank bank = bank();
            bank.setCharge(Math.min(bank.capacity(), bank.charge() + amount));
        }
    }

    public long stored() {
        return available() ? bank().charge() : share;
    }

    public long capacity() {
        return available() ? bank().capacity() : localCapacity();
    }

    int comparator() {
        return available() ? bank().comparator() : 0;
    }

    public Mode mode(Direction side) {
        return Mode.read(modes, side);
    }

    public void setMode(Direction side, Mode mode) {
        int shift = 2 * side.ordinal();
        setModes(modes & ~(3 << shift) | mode.ordinal() << shift);
    }

    void setModes(int packed) {
        if (modes == packed) {
            return;
        }
        int previous = modes;
        modes = packed;
        for (Direction side : BatteryBlock.FACES) {
            if (Mode.read(previous, side) != mode(side)) {
                refreshPort(side);
            }
        }
        if (level != null) {
            var state = getBlockState();
            if (level.isClientSide) {
                requestModelDataUpdate();
            } else {
                setChanged();
                level.updateNeighborsAt(worldPosition, state.getBlock());
            }
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void refreshPort(Direction side) {
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
            for (Direction side : BatteryBlock.FACES) {
                BatteryBlockEntity neighbor = batteryAt(side);
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
        Mode mode = mode(face);
        if (mode == Mode.OFF || batteryAt(face) != null) {
            return LazyOptional.empty();
        }
        bank();
        return ports.computeIfAbsent(face, key -> new EnergyPort(key, mode)).handler.cast();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("energy", share);
        tag.putInt("energy_sides", modes);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("energy")) {
            int saved = (int) Math.max(0, Math.min(localCapacity(), tag.getLong("energy")));
            if (share != saved) {
                share = saved;
                invalidateBank();
            }
        }
        setModes(tag.getInt("energy_sides"));
    }

    @Override
    public CompoundTag getUpdateTag() {
        var tag = new CompoundTag();
        tag.putInt("energy_sides", modes);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull ModelData getModelData() {
        return ModelData.builder().with(FACE_MODES, modes).build();
    }

    private final class EnergyPort implements IEnergyStorage {
        private final Direction face;
        private final Mode mode;
        private final LazyOptional<IEnergyStorage> handler = LazyOptional.of(() -> this);

        private EnergyPort(Direction face, Mode mode) {
            this.face = face;
            this.mode = mode;
        }

        private boolean usable() {
            return handler.isPresent() && available() && batteryAt(face) == null
                    && mode(face) == mode;
        }

        private int transfer(int request, boolean insert, boolean simulate) {
            if (!(insert ? canReceive() : canExtract())) {
                return 0;
            }
            BatteryBank bank = bank();
            if (bank.transferring() || !handler.isPresent()) {
                return 0;
            }
            int amount = BatteryBlockEntity.this.available(bank, request, insert);
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
        @Override public boolean canReceive() { return mode == Mode.IN && usable(); }
        @Override public boolean canExtract() { return mode == Mode.OUT && usable(); }
    }
}
