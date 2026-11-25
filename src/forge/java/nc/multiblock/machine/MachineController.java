package nc.multiblock.machine;

import nc.init.NCAdvancements;
import nc.init.NCMachines;
import nc.multiblock.AssemblyDiagnostic;
import nc.multiblock.Cuboid;
import nc.multiblock.MultiblockFrame;
import nc.multiblock.computer.ComputerPort;
import nc.multiblock.machine.MachinePartBlock.Part;
import nc.radiation.ChunkRadiationSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

abstract class MachineController extends BlockEntity
        implements ComputerPort.Controller, AssemblyDiagnostic.Controller, ChunkRadiationSource {
    protected @Nullable Cuboid bounds;
    private final Part controllerPart;
    private final ComputerPort.Links computerPorts = new ComputerPort.Links(this);
    private List<MachinePort> ports = List.of();
    private List<BlockPos> redstone = List.of();
    private List<BlockPos> frames = List.of();
    private boolean dirty = true;
    private long nextValidationTick;
    private AssemblyDiagnostic validation;
    private boolean halted;
    private long power;
    private double radiationEmission;

    MachineController(BlockEntityType<?> type, BlockPos pos, BlockState state, Part controllerPart) {
        super(type, pos, state);
        this.controllerPart = controllerPart;
        validation = problem("dimensions", pos);
    }

    final void tick() {
        if (!(level instanceof ServerLevel server)) return;
        radiationEmission = 0D;
        long time = server.getGameTime();
        if (dirty || bounds == null && time >= nextValidationTick) {
            assemble(server);
            dirty = false;
            nextValidationTick = time + 20;
        }
        if (!assembled()) return;
        processTick(server);
        if (!assembled()) return;
        for (var port : ports) port.push();
        afterTransfers(server);
    }

    @Override
    public Cuboid computerBounds() {
        return assembled() && level instanceof ServerLevel server && bounds.isLoaded(server) ? bounds : null;
    }

    public final boolean assembled() { return bounds != null && !isRemoved(); }
    public final boolean halted() { return halted; }
    final long power() { return power; }

    @Override
    public final double radiationEmission() {
        return radiationEmission;
    }

    final void emitRadiation(double base, double speed) {
        if (base > 0D) radiationEmission = Math.min(Double.MAX_VALUE, base * speed);
    }

    final void setPower(long quantity) {
        if (power != quantity) {
            power = quantity;
            setChanged();
        }
    }

    final void toggle() {
        setHalted(!halted);
    }

    final void setHalted(boolean halt) {
        if (halted != halt) {
            halted = halt;
            if (halt) radiationEmission = 0D;
            setChanged();
        }
    }

    record ProcessStatus(boolean processing, double progress, double duration, long power) {}

    abstract ProcessStatus computerProcess();

    final boolean blockedByRedstone(ServerLevel server) {
        return halted || server.hasNeighborSignal(worldPosition) || redstone.stream().anyMatch(server::hasNeighborSignal);
    }

    abstract int tankCount(boolean product);
    abstract FluidTank tankFor(boolean product, int index);
    @Nullable IItemHandler processItems() { return null; }
    int itemCount(boolean output) { return 0; }
    @Nullable FluidTank pressureTank() { return null; }
    boolean indexedProcessPorts() { return false; }
    abstract long energyCapacity();
    abstract void processTick(ServerLevel server);
    void afterTransfers(ServerLevel server) {}
    abstract void openMenu(Player player);
    abstract void clear();

    private void assemble(ServerLevel server) {
        var found = Cuboid.findConnected(server, worldPosition, NCMachines.maxSize() + 2,
                state -> MachinePartBlock.part(state) != null);
        if (found == null || Math.min(found.xLength(), Math.min(found.yLength(), found.zLength())) < NCMachines.minSize() + 2) {
            fail(problem("dimensions", worldPosition));
            return;
        }
        if (!found.isLoaded(server)) {
            fail(problem("unloaded", worldPosition));
            return;
        }
        if (found.boundaryAxes(worldPosition) != 1) {
            fail(problem("wall", worldPosition));
            return;
        }

        var installedPorts = new ArrayList<MachinePort>();
        var installedRedstone = new ArrayList<BlockPos>();
        var installedComputers = new ArrayList<BlockPos>();
        var edgeFrames = new ArrayList<BlockPos>();
        for (BlockPos pos : BlockPos.betweenClosed(found.min(), found.max())) {
            int boundary = found.boundaryAxes(pos);
            if (boundary == 0) continue;
            var part = MachinePartBlock.part(server.getBlockState(pos));
            if (part == Part.FRAME) {
                if (boundary > 1) edgeFrames.add(pos.immutable());
                continue;
            }
            if (boundary != 1 || part == null || part.internal() || part.controller() && !pos.equals(worldPosition)) {
                fail(problem("wall", pos));
                return;
            }
            var error = wallError(part, found.outward(pos));
            if (error != null) {
                fail(problem(error, pos));
                return;
            }
            if (part.port()) {
                if (server.getBlockEntity(pos) instanceof MachinePort port) installedPorts.add(port);
                else {
                    fail(problem("wall", pos));
                    return;
                }
            } else if (part == Part.REDSTONE) {
                installedRedstone.add(pos.immutable());
            } else if (part == Part.COMPUTER) {
                installedComputers.add(pos.immutable());
            }
        }

        AssemblyDiagnostic error = validateLayout(server, found);
        if (error != null) {
            fail(error);
            return;
        }

        disconnect();
        bounds = found;
        computerPorts.bind(server, installedComputers);
        ports = List.copyOf(installedPorts);
        redstone = List.copyOf(installedRedstone);
        frames = List.copyOf(edgeFrames);
        long volume = (long) found.xLength() * found.yLength() * found.zLength();
        int capacity = (int) Math.min(Integer.MAX_VALUE, volume * 1000);
        for (int i = 0; i < tankCount(false); i++) tankFor(false, i).setCapacity(capacity);
        for (int i = 0; i < tankCount(true); i++) tankFor(true, i).setCapacity(capacity);
        var pressure = pressureTank();
        if (pressure != null) pressure.setCapacity(capacity);
        var state = getBlockState();
        server.setBlock(worldPosition, state.setValue(BlockStateProperties.FACING, found.outward(worldPosition)),
                Block.UPDATE_CLIENTS);
        for (var port : ports) port.bind(this, found.outward(port.getBlockPos()));
        setFrames(true);
        assemblyChanged();
    }

    @Nullable String wallError(Part part, Direction outward) { return part.distillerWall() || part.electrodeTerminal() ? "wall" : null; }
    abstract @Nullable AssemblyDiagnostic validateLayout(ServerLevel server, Cuboid found);
    void assemblyChanged() {}
    void beforeDisconnect() {}

    final AssemblyDiagnostic problem(String key, BlockPos pos) {
        return AssemblyDiagnostic.at("message.nuclearcraft." + controllerPart.id().replace("_controller", "") + "." + key, pos);
    }

    final void fail(AssemblyDiagnostic error) {
        boolean previouslyAssembled = bounds != null;
        setFrames(false);
        disconnect();
        validation = error;
        setLit(false);
        if (previouslyAssembled) assemblyChanged();
    }

    private void disconnect() {
        if (bounds != null) beforeDisconnect();
        radiationEmission = 0D;
        computerPorts.unbind();
        bounds = null;
        for (var port : ports) port.unbind(this);
        ports = List.of();
        redstone = List.of();
        frames = List.of();
    }

    private void setFrames(boolean assembled) {
        if (level instanceof ServerLevel server) {
            MultiblockFrame.set(server, frames, assembled);
        }
    }

    final void setLit(boolean active) {
        if (level != null && getBlockState().getValue(BlockStateProperties.LIT) != active) {
            level.setBlock(worldPosition, getBlockState().setValue(BlockStateProperties.LIT, active), Block.UPDATE_CLIENTS);
        }
    }

    final void partChanged(BlockPos pos) {
        if (bounds != null) {
            if (!bounds.contains(pos) && !bounds.contains(pos.above()) && !bounds.contains(pos.below())
                    && !bounds.contains(pos.north()) && !bounds.contains(pos.south())
                    && !bounds.contains(pos.east()) && !bounds.contains(pos.west())) return;
            fail(problem("wall", pos));
        }
        dirty = true;
    }

    final void chunkUnloaded(ChunkPos chunk) {
        if (bounds != null && bounds.touchesChunk(chunk)) {
            validation = problem("unloaded", worldPosition);
            disconnect();
            if (!new ChunkPos(worldPosition).equals(chunk)) {
                setLit(false);
                assemblyChanged();
            }
            dirty = true;
        }
    }

    @Override public void onChunkUnloaded() {
        disconnect();
        dirty = true;
        super.onChunkUnloaded();
    }

    @Override public void setRemoved() {
        setFrames(false);
        disconnect();
        super.setRemoved();
    }

    @Override
    public final @Nullable AssemblyDiagnostic diagnostic(ServerLevel server) {
        if (dirty || !assembled()) {
            assemble(server);
            dirty = false;
        }
        return assembled() ? null : validation;
    }

    final void open(Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server)) return;
        var diagnostic = diagnostic(server);
        if (diagnostic == null) {
            NCAdvancements.inspectAssembly(player, hand, getBlockState());
            openMenu(player);
        } else {
            diagnostic.show(player, true);
        }
    }

    @Override
    public final ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public final void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        // Forge sends an empty update tag as null when the machine has no assembled volume.
        handleUpdateTag(Objects.requireNonNullElseGet(packet.getTag(), CompoundTag::new));
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("halted", halted);
        tag.putLong("energy", power);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        radiationEmission = 0D;
        halted = tag.getBoolean("halted");
        power = Math.max(0, tag.getLong("energy"));
    }
}
