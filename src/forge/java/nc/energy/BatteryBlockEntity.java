package nc.energy;

import nc.automation.MachineInterfaceTarget;

import nc.energy.BatteryBlock.Mode;
import nc.init.NCBatteries;
import nc.init.NCBatteries.Tier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;

public final class BatteryBlockEntity extends EnergyBankBlockEntity implements MachineInterfaceTarget {
    public static final ModelProperty<Integer> FACE_MODES = new ModelProperty<>();

    private final Tier tier;
    private volatile int modes;
    private int lastComparator;

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
        battery.pushPower();
    }

    @Override
    protected long localCapacity() {
        return tier.capacity();
    }

    @Override
    protected int configRevision() {
        return NCBatteries.configRevision();
    }

    @Override
    protected long transferLimit() {
        return tier.transfer();
    }

    @Override
    protected boolean sharesBankWith(EnergyBankBlockEntity other) {
        return other instanceof BatteryBlockEntity;
    }

    @Override
    protected boolean canReceive(Direction face) {
        return mode(face) == Mode.IN;
    }

    @Override
    protected boolean canExtract(Direction face) {
        return mode(face) == Mode.OUT;
    }

    void setPlacedCharge(int amount) {
        addCharge(amount);
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
        for (Direction side : FACES) {
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

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("energy_sides", modes);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
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

}
