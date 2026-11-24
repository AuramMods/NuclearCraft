package nc.multiblock.machine;

import nc.init.NCMachines;
import nc.multiblock.Cuboid;
import nc.multiblock.machine.MachinePartBlock.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

abstract class MachineController extends BlockEntity {
    protected @Nullable Cuboid bounds;
    private final Part controllerPart;
    private List<MachinePort> ports = List.of();
    private List<BlockPos> redstone = List.of();
    private List<BlockPos> frames = List.of();
    private boolean dirty = true;
    private long nextValidationTick;
    private String validation = "dimensions";
    private boolean halted;
    private long power;

    MachineController(BlockEntityType<?> type, BlockPos pos, BlockState state, Part controllerPart) {
        super(type, pos, state);
        this.controllerPart = controllerPart;
    }

    final void tick() {
        if (!(level instanceof ServerLevel server)) return;
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

    public final boolean assembled() { return bounds != null && !isRemoved(); }
    public final boolean halted() { return halted; }
    final long power() { return power; }

    final void setPower(long quantity) {
        if (power != quantity) {
            power = quantity;
            setChanged();
        }
    }

    final void toggle() {
        halted = !halted;
        setChanged();
    }

    final boolean blockedByRedstone(ServerLevel server) {
        return halted || server.hasNeighborSignal(worldPosition) || redstone.stream().anyMatch(server::hasNeighborSignal);
    }

    abstract int tankCount(boolean product);
    abstract FluidTank tankFor(boolean product, int index);
    abstract long energyCapacity();
    abstract void processTick(ServerLevel server);
    void afterTransfers(ServerLevel server) {}
    abstract void openMenu(Player player);
    abstract void clear();

    private void assemble(ServerLevel server) {
        var found = Cuboid.findConnected(server, worldPosition, NCMachines.maxSize() + 2,
                state -> MachinePartBlock.part(state) != null);
        if (found == null || Math.min(found.xLength(), Math.min(found.yLength(), found.zLength())) < NCMachines.minSize() + 2) {
            fail("dimensions");
            return;
        }
        if (!found.isLoaded(server)) {
            fail("unloaded");
            return;
        }
        if (found.boundaryAxes(worldPosition) != 1) {
            fail("wall");
            return;
        }

        var installedPorts = new ArrayList<MachinePort>();
        var installedRedstone = new ArrayList<BlockPos>();
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
                fail("wall");
                return;
            }
            var error = wallError(part, found.outward(pos));
            if (error != null) {
                fail(error);
                return;
            }
            if (part.port()) {
                if (server.getBlockEntity(pos) instanceof MachinePort port) installedPorts.add(port);
                else {
                    fail("wall");
                    return;
                }
            } else if (part == Part.REDSTONE) installedRedstone.add(pos.immutable());
        }

        String error = validateLayout(server, found);
        if (error != null) {
            fail(error);
            return;
        }

        disconnect();
        bounds = found;
        ports = List.copyOf(installedPorts);
        redstone = List.copyOf(installedRedstone);
        frames = List.copyOf(edgeFrames);
        validation = "assembled";
        long volume = (long) found.xLength() * found.yLength() * found.zLength();
        int capacity = (int) Math.min(Integer.MAX_VALUE, volume * 1000);
        for (int i = 0; i < tankCount(false); i++) tankFor(false, i).setCapacity(capacity);
        for (int i = 0; i < tankCount(true); i++) tankFor(true, i).setCapacity(capacity);
        var state = getBlockState();
        server.setBlock(worldPosition, state.setValue(BlockStateProperties.FACING, found.outward(worldPosition)),
                Block.UPDATE_CLIENTS);
        for (var port : ports) port.bind(this, found.outward(port.getBlockPos()));
        setFrames(true);
        assemblyChanged();
    }

    @Nullable String wallError(Part part, Direction outward) { return part.distillerWall() ? "wall" : null; }
    abstract @Nullable String validateLayout(ServerLevel server, Cuboid found);
    void assemblyChanged() {}

    final void fail(String error) {
        boolean previouslyAssembled = bounds != null;
        setFrames(false);
        disconnect();
        validation = error;
        setLit(false);
        if (previouslyAssembled) assemblyChanged();
    }

    private void disconnect() {
        bounds = null;
        for (var port : ports) port.unbind(this);
        ports = List.of();
        redstone = List.of();
        frames = List.of();
    }

    private void setFrames(boolean assembled) {
        if (!(level instanceof ServerLevel server)) return;
        for (BlockPos pos : frames) {
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) continue;
            var state = chunk.getBlockState(pos);
            if (state.hasProperty(MachinePartBlock.FRAME_EDGE) && state.getValue(MachinePartBlock.FRAME_EDGE) != assembled) {
                server.setBlock(pos, state.setValue(MachinePartBlock.FRAME_EDGE, assembled), Block.UPDATE_CLIENTS);
            }
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
            fail("wall");
        }
        dirty = true;
    }

    final void chunkUnloaded(ChunkPos chunk) {
        if (bounds != null && bounds.touchesChunk(chunk)) {
            validation = "unloaded";
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

    final void open(Player player) {
        if (!(level instanceof ServerLevel server)) return;
        if (dirty || !assembled()) {
            assemble(server);
            dirty = false;
        }
        if (assembled()) openMenu(player);
        else player.displayClientMessage(Component.translatable("message.nuclearcraft." + controllerPart.id().replace("_controller", "")
                + "." + validation), true);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("halted", halted);
        tag.putLong("energy", power);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        halted = tag.getBoolean("halted");
        power = Math.max(0, tag.getLong("energy"));
    }
}
