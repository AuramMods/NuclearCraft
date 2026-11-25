package nc.multiblock.quantum;

import nc.init.NCQuantum;
import nc.multiblock.AssemblyDiagnostic;
import nc.multiblock.fission.FissionHighlights;
import nc.multiblock.quantum.QuantumPart.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.LazyOptional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class QuantumPartEntity extends BlockEntity {
    private static final Map<Setting, String> SELECTION_KEYS = Map.of(Setting.CONTROLS, "cQubits",
            Setting.TARGETS, "nQubits", Setting.FIRST, "iQubits", Setting.SECOND, "jQubits");
    private final QuantumPart part;
    private final QuantumComputer computer;
    private final List<Setting> settings;
    private final Map<Setting, int[]> selections = new EnumMap<>(Setting.class);
    private LazyOptional<?> peripheral = LazyOptional.empty();
    private int nextSetting;
    private int qubitId = -1;
    private boolean pulsed;
    private boolean measured;
    private int result;
    private long colorTick;
    private double angle;

    public QuantumPartEntity(BlockPos pos, BlockState state) {
        super(NCQuantum.PART_ENTITY.get(), pos, state);
        part = ((QuantumBlock) state.getBlock()).part();
        computer = part == QuantumPart.CONTROLLER ? new QuantumComputer(this) : null;
        settings = part.settings();
    }

    public QuantumPart part() { return part; }
    QuantumComputer computer() { return computer; }
    public int qubitId() { return qubitId; }
    public boolean measuredOne() { return measured; }
    public int result() { return result; }
    public long colorTick() { return colorTick; }
    double angle() { return angle; }
    int[] selection(Setting setting) { return selections.getOrDefault(setting, new int[0]); }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            QuantumNetworks.add(server, this);
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel server) {
            QuantumNetworks.remove(server, this, false);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level instanceof ServerLevel server) {
            QuantumNetworks.remove(server, this, true);
        }
        super.onChunkUnloaded();
    }

    <T> LazyOptional<T> peripheral(Supplier<T> supplier) {
        if (!peripheral.isPresent()) {
            peripheral = LazyOptional.of(supplier::get);
        }
        return peripheral.cast();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        peripheral.invalidate();
        peripheral = LazyOptional.empty();
    }

    void serverTick(ServerLevel level) {
        QuantumNetworks.Network network = QuantumNetworks.at(level, worldPosition);
        QuantumComputer active = network == null ? null : network.computer();
        boolean powered = level.hasNeighborSignal(worldPosition);
        if (pulsed != powered) {
            pulsed = powered;
            if (powered && active != null) {
                active.queue(level, network, this);
            }
            setChanged();
        }
        if (computer != null) {
            computer.tick(level, network);
        }
    }

    void setQubitId(int id) {
        qubitId = id;
        setChanged();
    }

    void recordMeasurement(boolean one) {
        measured = one;
        result = one ? 1 : -1;
        colorTick = level.getGameTime();
        setChanged();
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    Component inspect(ServerLevel level, ServerPlayer player) {
        QuantumNetworks.Network network = QuantumNetworks.at(level, worldPosition);
        if (part == QuantumPart.QUBIT) {
            return Component.translatable("message.nuclearcraft.quantum.qubit", qubitId,
                    Component.translatable(result == 0 ? "message.nuclearcraft.quantum.unmeasured"
                            : "message.nuclearcraft.quantum.bit_" + (measured ? 1 : 0)));
        }
        if (part.isGate()) {
            QuantumMultitool.highlight(player, network, selections.values());
            var message = Component.translatable("message.nuclearcraft.quantum.gate", getBlockState().getBlock().getName());
            for (Setting setting : settings) {
                String value = setting == Setting.ANGLE ? Double.toString(angle) : Arrays.toString(selection(setting));
                message.append(" ").append(QuantumMultitool.label(setting)).append(" ").append(value);
            }
            return message;
        }
        QuantumComputer active = network == null ? null : network.computer();
        if (active != null) {
            return active.inspect(level, network);
        }
        if (network == null || network.controllers().isEmpty()) {
            return Component.translatable(AssemblyDiagnostic.NO_CONTROLLER);
        }
        FissionHighlights.send(player, network.controllers().stream().map(BlockEntity::getBlockPos).toList());
        return Component.translatable("message.nuclearcraft.quantum.controllers");
    }

    Component configure(ServerLevel level, ServerPlayer player, ItemStack tool) {
        QuantumNetworks.Network network = QuantumNetworks.at(level, worldPosition);
        if (part == QuantumPart.QUBIT) {
            if (player.isShiftKeyDown()) {
                return inspect(level, player);
            }
            return QuantumMultitool.selectQubit(level, network, tool, this);
        }
        if (part.role == QuantumPart.Role.EXPORT && !player.isShiftKeyDown()) {
            QuantumComputer active = network == null ? null : network.computer();
            return active == null ? Component.translatable("message.nuclearcraft.quantum.controllers")
                    : active.export(level, network, part);
        }
        if (settings.isEmpty()) {
            return inspect(level, player);
        }
        Setting setting = settings.get(nextSetting);
        if (player.isShiftKeyDown()) {
            QuantumMultitool.start(level, tool, worldPosition, setting);
            return Component.translatable("message.nuclearcraft.quantum.start", QuantumMultitool.label(setting));
        }
        CompoundTag session = QuantumMultitool.session(tool);
        if (!QuantumMultitool.matches(session, level, worldPosition, setting)) {
            return inspect(level, player);
        }
        if (setting == Setting.ANGLE) {
            angle = session.getDouble("angle");
        } else {
            int[] selected = session.getIntArray("selection");
            selections.put(setting, selected);
            QuantumMultitool.highlight(player, network, List.of(selected));
        }
        nextSetting = (nextSetting + 1) % settings.size();
        QuantumMultitool.clear(tool);
        setChanged();
        return Component.translatable("message.nuclearcraft.quantum.saved", QuantumMultitool.label(setting));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("pulsed", pulsed);
        if (part.isQubit()) {
            tag.putInt("qubitID", qubitId);
            tag.putBoolean("qubitRedstone", measured);
            tag.putInt("result", result);
            tag.putLong("colorTick", colorTick);
        }
        if (part.isGate()) {
            tag.putDouble("qGateAngle", angle);
            tag.putInt("setting", nextSetting);
            selections.forEach((setting, ids) -> tag.putIntArray(SELECTION_KEYS.get(setting), ids));
        }
        if (computer != null) {
            tag.put("circuit", computer.save());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        pulsed = tag.getBoolean("pulsed") || tag.getBoolean("qubitPulsed");
        qubitId = tag.contains("qubitID", Tag.TAG_INT) ? tag.getInt("qubitID") : -1;
        measured = tag.getBoolean("qubitRedstone");
        result = tag.contains("result", Tag.TAG_INT) ? tag.getInt("result") : (int) Math.signum(tag.getFloat("qubitMeasureColor"));
        colorTick = tag.getLong("colorTick");
        angle = tag.getDouble("qGateAngle");
        if (!Double.isFinite(angle)) {
            angle = 0D;
        }
        nextSetting = settings.isEmpty() ? 0 : Math.floorMod(tag.getInt("setting"), settings.size());
        selections.clear();
        SELECTION_KEYS.forEach((setting, key) -> {
            if (tag.contains(key, Tag.TAG_INT_ARRAY)) {
                selections.put(setting, tag.getIntArray(key));
            } else if (tag.contains(key, Tag.TAG_COMPOUND)) {
                selections.put(setting, tag.getCompound(key).getIntArray("ints"));
            }
        });
        if (computer != null) {
            if (tag.contains("circuit", Tag.TAG_COMPOUND)) {
                computer.load(tag.getCompound("circuit"));
            } else if (tag.contains("size", Tag.TAG_INT)) {
                computer.load(tag);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("result", result);
        tag.putLong("colorTick", colorTick);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        result = tag.getInt("result");
        colorTick = tag.getLong("colorTick");
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) {
            handleUpdateTag(packet.getTag());
        }
    }
}
