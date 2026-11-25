package nc.multiblock.turbine;

import nc.multiblock.AssemblyDiagnostic;
import nc.multiblock.Cuboid;
import nc.multiblock.MultiblockFrame;
import nc.multiblock.computer.ComputerPort;
import nc.multiblock.computer.ComputerValues;
import nc.init.NCTurbine;
import nc.init.TurbineConfig;
import nc.recipe.TurbineRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.IntStream;

public final class TurbineController extends BlockEntity implements MenuProvider, ComputerPort.Controller, AssemblyDiagnostic.Controller {
    private static final int BASE_STEAM = 1000;
    private static final int BASE_EXHAUST = 4000;

    private final FluidTank steam = new TurbineTank(BASE_STEAM, true);
    private final FluidTank exhaust = new TurbineTank(BASE_EXHAUST, false);
    private final TurbineDrive drive = new TurbineDrive();
    private long energy;
    private long capacity = TurbineConfig.baseEnergyCapacity();
    private long power;
    private boolean processing;
    private boolean dirty = true;
    private long configRevision = -1;
    private boolean enabled;
    private boolean computerActivated;
    private int inputRate;
    private double idealExpansion = 1D;
    private final ComputerPort.Links computerPorts = new ComputerPort.Links(this);
    private TurbineLayout layout;
    private @Nullable TurbineVisuals visuals;
    private double rotorAngle;
    private double rotorVelocity;
    private double lastSentVelocity;
    private boolean lastSentProcessing;
    private FluidStack lastSentExhaust = FluidStack.EMPTY;
    private @Nullable ResourceLocation particle;
    private double particleSpeed;
    private AssemblyDiagnostic validation = new AssemblyDiagnostic(
            Component.translatable("message.nuclearcraft.turbine.checking"), null);

    public TurbineController(BlockPos pos, BlockState state) {
        super(NCTurbine.CONTROLLER_ENTITY.get(), pos, state);
    }

    @Override
    public Cuboid computerBounds() {
        return assembled() ? layout.bounds() : null;
    }

    public boolean assembled() {
        return layout != null && !isRemoved() && level instanceof ServerLevel server && layout.bounds().isLoaded(server);
    }

    public Component validationMessage() {
        return validation.message();
    }

    @Override
    public @Nullable AssemblyDiagnostic diagnostic(ServerLevel server) {
        if (dirty) {
            refresh(server);
        }
        return assembled() ? null : validation;
    }

    public void tick(ServerLevel server) {
        if (layout != null && !layout.bounds().isLoaded(server)) {
            disassemble();
        }
        long revision = TurbineConfig.revision();
        if (dirty || configRevision != revision || (layout == null && (server.getGameTime() + worldPosition.asLong()) % 20 == 0)) {
            configRevision = revision;
            refresh(server);
        }
        boolean active = computerOn();
        if (layout != null) {
            if (!runTurbine(server, active)) {
                return;
            }
            pushOutputs(server);
            if (layout != null && needsVisualUpdate(server.getGameTime())) {
                syncVisuals(server);
            }
        }
        BlockState state = getBlockState();
        if (state.getValue(TurbineWallBlock.ACTIVE) != active) {
            server.setBlock(worldPosition, state.setValue(TurbineWallBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    private boolean runTurbine(ServerLevel server, boolean active) {
        TurbineRecipe recipe = recipeFor(steam.getFluid());
        int consumed = active && recipe != null ? expandSteam(recipe) : 0;
        long previousPower = power;
        double previousTension = drive.tension();
        power = drive.tick(layout, recipe, consumed);
        processing = consumed > 0;
        inputRate = consumed;
        if (recipe != null) {
            idealExpansion = recipe.expansion();
            particle = recipe.particle();
            particleSpeed = recipe.particleSpeed();
        }
        double speed = Mth.clamp(drive.speedRatio(), 0D, 2D);
        rotorVelocity = speed < 1e-5D ? 0D : 50D * TurbineConfig.rotorSpeed() * speed;
        if (layout.glass()) {
            rotorAngle = (rotorAngle + rotorVelocity) % 360D;
        }
        if (power > 0) {
            energy += Math.min(Math.max(0, capacity - energy), power);
        }
        if (processing || power != previousPower || drive.tension() != previousTension) {
            setChanged();
        }
        if (drive.tension() > 1D) {
            failBearing(server);
            return false;
        }
        return true;
    }

    private int expandSteam(TurbineRecipe recipe) {
        double fill = Math.min(1D, (double) steam.getFluidAmount() / steam.getCapacity());
        double factor = 1D + (TurbineConfig.tensionThroughputFactor() - 1D) * fill;
        int throughput = (int) Math.min(steam.getFluidAmount(), drive.ratedFlow(layout) * factor);
        FluidStack product = recipe.output();
        int batches = Math.min(throughput / recipe.input().amount(),
                exhaust.getSpace() / product.getAmount());
        if (batches <= 0) {
            return 0;
        }
        product.setAmount(product.getAmount() * batches);
        if (exhaust.fill(product, FluidAction.SIMULATE) != product.getAmount()) {
            return 0;
        }
        exhaust.fill(product, FluidAction.EXECUTE);
        int used = batches * recipe.input().amount();
        steam.drain(used, FluidAction.EXECUTE);
        return used;
    }

    private TurbineRecipe recipeFor(FluidStack fluid) {
        if (level == null || fluid.isEmpty()) {
            return null;
        }
        for (TurbineRecipe recipe : level.getRecipeManager().getAllRecipesFor(NCTurbine.RECIPES.get())) {
            if (recipe.accepts(fluid)) {
                return recipe;
            }
        }
        return null;
    }

    private void pushOutputs(ServerLevel server) {
        for (TurbineLayout.Port port : layout.ports()) {
            boolean fluid = port.part() == TurbinePartBlock.Part.OUTLET && !exhaust.isEmpty();
            boolean fe = port.part().dynamo() && energy > 0;
            if (!fluid && !fe) {
                continue;
            }
            BlockPos neighbor = port.pos().relative(port.side());
            var chunk = server.getChunkSource().getChunkNow(neighbor.getX() >> 4, neighbor.getZ() >> 4);
            BlockEntity target = chunk == null ? null : chunk.getBlockEntity(neighbor);
            if (target != null) {
                Direction side = port.side().getOpposite();
                if (fluid) {
                    target.getCapability(ForgeCapabilities.FLUID_HANDLER, side).ifPresent(this::pushExhaust);
                } else {
                    target.getCapability(ForgeCapabilities.ENERGY, side).ifPresent(this::pushEnergy);
                }
            }
        }
    }

    private void pushExhaust(IFluidHandler target) {
        FluidStack offer = exhaust.getFluid().copy();
        int requested = Math.min(offer.getAmount(), target.fill(offer.copy(), FluidAction.SIMULATE));
        if (requested <= 0) {
            return;
        }
        FluidStack reserved = exhaust.drain(requested, FluidAction.EXECUTE);
        int sent = 0;
        try {
            sent = Math.max(0, Math.min(reserved.getAmount(), target.fill(reserved.copy(), FluidAction.EXECUTE)));
        } finally {
            int unsent = reserved.getAmount() - sent;
            if (unsent > 0) {
                reserved.setAmount(exhaust.getFluidAmount() + unsent);
                exhaust.setFluid(reserved);
                setChanged();
            }
        }
    }

    private void pushEnergy(IEnergyStorage target) {
        int offered = extractEnergy(Integer.MAX_VALUE, true);
        int reserved = extractEnergy(Math.min(offered, target.receiveEnergy(offered, true)), false);
        if (reserved <= 0) {
            return;
        }
        int sent = 0;
        try {
            sent = Math.max(0, Math.min(reserved, target.receiveEnergy(reserved, false)));
        } finally {
            energy += reserved - sent;
            setChanged();
        }
    }

    private void failBearing(ServerLevel server) {
        TurbineLayout damaged = layout;
        disassemble();
        drive.load(new CompoundTag());
        for (BlockPos pos : damaged.bearings()) {
            server.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            server.explode(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 4F,
                    Level.ExplosionInteraction.NONE);
        }
        for (BlockPos pos : damaged.rotor().keySet()) {
            TurbinePartBlock.Part part = TurbinePartBlock.part(server.getBlockState(pos));
            if (part != null && part.stator() && server.random.nextFloat() < 0.04F) {
                server.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            } else if (part != null && part.blade() && server.random.nextFloat() < 0.18F) {
                server.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                server.explode(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 4F,
                        Level.ExplosionInteraction.NONE);
            }
        }
        dirty = true;
        setChanged();
    }

    @Override
    public void setRemoved() {
        disassemble();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        disassemble();
        dirty = true;
        super.onChunkUnloaded();
    }

    void partChanged(BlockPos pos) {
        int reach = TurbineConfig.searchLength();
        if (Math.abs(pos.getX() - worldPosition.getX()) <= reach && Math.abs(pos.getY() - worldPosition.getY()) <= reach
                && Math.abs(pos.getZ() - worldPosition.getZ()) <= reach) {
            dirty = true;
            if (layout != null && layout.bounds().contains(pos)) {
                disassemble();
            }
        }
    }

    void chunkUnloaded(ChunkPos chunk) {
        if (layout != null && layout.bounds().touchesChunk(chunk)) {
            disassemble();
            dirty = true;
        }
    }

    private void refresh(ServerLevel server) {
        dirty = false;
        TurbineLayout.Validation result = TurbineLayout.find(server, worldPosition);
        validation = result.diagnostic();
        TurbineLayout found = result.layout();
        if (found == null) {
            disassemble();
            return;
        }
        capacity = (long) TurbineConfig.baseEnergyCapacity() * found.exteriorVolume();
        if (found.equals(layout)) {
            return;
        }
        disassemble();
        layout = found;
        MultiblockFrame.set(server, layout.frames(), true);
        computerPorts.bind(server, layout.computers());
        steam.setCapacity((int) Math.min(Integer.MAX_VALUE, (long) BASE_STEAM * found.exteriorVolume()));
        exhaust.setCapacity((int) Math.min(Integer.MAX_VALUE, (long) BASE_EXHAUST * found.exteriorVolume()));
        faceOutward(server, worldPosition, found.bounds().outward(worldPosition));
        updateRotorBlocks(server, found, found.glass());
        for (TurbineLayout.Port port : found.ports()) {
            if (server.getBlockEntity(port.pos()) instanceof TurbinePort outlet) {
                outlet.bind(this, port);
                faceOutward(server, port.pos(), port.side());
                server.updateNeighborsAt(port.pos(), outlet.getBlockState().getBlock());
            }
        }
        syncVisuals(server);
    }

    private static void updateRotorBlocks(ServerLevel server, TurbineLayout found, boolean animated) {
        for (Map.Entry<BlockPos, Direction.Axis> entry : found.rotor().entrySet()) {
            BlockPos pos = entry.getKey();
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            BlockState old = chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
            if (old.getBlock() instanceof TurbineRotorBlock) {
                BlockState state = old.setValue(TurbineRotorBlock.AXIS, entry.getValue())
                        .setValue(TurbineRotorBlock.ANIMATED, animated);
                if (old != state) {
                    server.setBlock(pos, state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static void faceOutward(ServerLevel server, BlockPos pos, Direction direction) {
        BlockState state = server.getBlockState(pos);
        if (state.getValue(TurbineWallBlock.FACING) != direction) {
            server.setBlock(pos, state.setValue(TurbineWallBlock.FACING, direction), Block.UPDATE_CLIENTS);
        }
    }

    private void disassemble() {
        computerPorts.unbind();
        TurbineLayout previous = layout;
        layout = null;
        if (previous == null || !(level instanceof ServerLevel server)) {
            return;
        }
        MultiblockFrame.set(server, previous.frames(), false);
        processing = false;
        power = 0;
        inputRate = 0;
        rotorAngle = rotorVelocity = 0D;
        drive.clearRotation();
        if (previous.glass()) {
            updateRotorBlocks(server, previous, false);
        }
        syncVisuals(server);
        for (TurbineLayout.Port port : previous.ports()) {
            var chunk = server.getChunkSource().getChunkNow(port.pos().getX() >> 4, port.pos().getZ() >> 4);
            if (chunk != null && chunk.getBlockEntity(port.pos()) instanceof TurbinePort outlet) {
                outlet.unbind();
            }
        }
        if (!isRemoved()) {
            setChanged();
        }
    }

    private boolean needsVisualUpdate(long tick) {
        if (processing != lastSentProcessing) {
            return true;
        }
        return tick % 5 == 0 && (processing || layout.glass() && (rotorVelocity != 0D || lastSentVelocity != 0D
                || !lastSentExhaust.isFluidStackIdentical(exhaust.getFluid())));
    }

    private void syncVisuals(ServerLevel server) {
        lastSentVelocity = rotorVelocity;
        lastSentProcessing = processing;
        lastSentExhaust = layout != null && layout.glass() ? exhaust.getFluid().copy() : FluidStack.EMPTY;
        if (!isRemoved() && server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4) != null) {
            BlockState state = getBlockState();
            server.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public @Nullable TurbineVisuals visuals() {
        return visuals;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        CompoundTag display = new CompoundTag();
        if (layout != null) {
            var origin = layout.bounds().min().offset(1, 1, 1).subtract(worldPosition);
            double rate = processing ? drive.throughput() / drive.ratedFlow(layout) : 0D;
            display = new TurbineVisuals(origin, layout.flow(), layout.shaftWidth(), layout.bladeLength(), layout.stages(),
                    layout.glass(), processing, rotorAngle, rotorVelocity, level.getGameTime(),
                    Mth.clamp(drive.speedRatio(), 0D, 2D), exhaust.getFluid(), exhaust.getCapacity(),
                    particle, rate, particleSpeed).write();
        }
        // The empty compound is sent as an explicit clear on disassembly.
        tag.put("visuals", display);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        visuals = TurbineVisuals.read(tag.getCompound("visuals"));
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) {
            handleUpdateTag(packet.getTag());
        }
    }

    FluidTank steam() {
        return steam;
    }

    FluidTank exhaust() {
        return exhaust;
    }

    int extractEnergy(int requested, boolean simulate) {
        int extracted = (int) Math.min(Math.max(0, requested), Math.min(energy, capacity));
        if (!simulate && extracted > 0) {
            energy -= extracted;
            setChanged();
        }
        return extracted;
    }

    int energyStored() {
        return (int) Math.min(Integer.MAX_VALUE, energy);
    }

    int energyCapacity() {
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    boolean computerOn() {
        return assembled() && level instanceof ServerLevel server
                && (enabled || computerActivated || server.hasNeighborSignal(worldPosition)
                || layout.redstonePorts().stream().anyMatch(server::hasNeighborSignal));
    }

    void computerActivate(boolean active) {
        if (computerActivated != active) {
            computerActivated = active;
            setChanged();
        }
    }

    void computerClear() {
        steam.setFluid(FluidStack.EMPTY);
        exhaust.setFluid(FluidStack.EMPTY);
        setChanged();
    }

    boolean computerProcessing() { return processing; }
    long computerEnergy() { return energy; }
    long computerCapacity() { return capacity; }
    long computerPower() { return power; }
    double computerConductivity() { return layout.dynamo().conductivity(); }
    String computerFlow() { return layout.flow().getSerializedName(); }
    int computerInputRate() { return inputRate; }
    double computerIdealExpansion() { return idealExpansion; }
    int computerDynamoCount() { return layout.dynamo().installed().size(); }

    double computerTotalExpansion() {
        double expansion = 1D;
        for (TurbineRotorBlock stage : layout.stages()) expansion *= stage.expansion();
        return expansion;
    }

    List<Double> computerExpansionLevels() {
        var values = new ArrayList<Double>(layout.stages().size());
        double expansion = 1D;
        for (TurbineRotorBlock stage : layout.stages()) {
            values.add(expansion * Math.sqrt(stage.expansion()));
            expansion *= stage.expansion();
        }
        return values;
    }

    List<Double> computerIdealLevels() {
        int stages = layout.stages().size();
        return IntStream.range(0, stages).mapToObj(stage -> Math.pow(idealExpansion, (stage + 0.5D) / stages)).toList();
    }

    List<Double> computerBladeEfficiencies() {
        return layout.stages().stream().map(TurbineRotorBlock::efficiency).toList();
    }

    List<List<Object>> computerDynamoParts() {
        return layout.dynamo().installed().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> List.<Object>of(ComputerValues.position(entry.getKey()),
                        entry.getValue().dynamoName(), layout.dynamo().parts().containsKey(entry.getKey())))
                .toList();
    }

    void toggle() {
        enabled = !enabled;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("steam", steam.writeToNBT(new CompoundTag()));
        tag.put("exhaust", exhaust.writeToNBT(new CompoundTag()));
        tag.putLong("energy", energy);
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("computerActivated", computerActivated);
        tag.putDouble("ideal_expansion", idealExpansion);
        tag.putDouble("rotor_angle", rotorAngle);
        drive.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        steam.readFromNBT(tag.getCompound("steam"));
        exhaust.readFromNBT(tag.getCompound("exhaust"));
        energy = Math.max(0, tag.getLong("energy"));
        enabled = tag.getBoolean("enabled");
        computerActivated = tag.getBoolean("computerActivated");
        double savedExpansion = tag.getDouble("ideal_expansion");
        idealExpansion = Double.isFinite(savedExpansion) && savedExpansion > 0 ? savedExpansion : 1D;
        double savedAngle = tag.getDouble("rotor_angle");
        rotorAngle = Double.isFinite(savedAngle) ? savedAngle % 360D : 0D;
        drive.load(tag);
        dirty = true;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public TurbineMenu createMenu(int id, Inventory inventory, Player player) {
        return new TurbineMenu(id, this, ContainerLevelAccess.create(player.level(), worldPosition), new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case TurbineMenu.ENABLED -> enabled ? 1 : 0;
                    case TurbineMenu.PROCESSING -> processing ? 1 : 0;
                    case TurbineMenu.STEAM -> (int) (1000L * Math.min(steam.getFluidAmount(), steam.getCapacity()) / steam.getCapacity());
                    case TurbineMenu.EXHAUST -> (int) (1000L * Math.min(exhaust.getFluidAmount(), exhaust.getCapacity()) / exhaust.getCapacity());
                    case TurbineMenu.ENERGY -> (int) (1000D * Math.min(energy, capacity) / capacity);
                    case TurbineMenu.POWER_LOW -> (int) Math.min(Integer.MAX_VALUE, power) & 0xFFFF;
                    case TurbineMenu.POWER_HIGH -> (int) Math.min(Integer.MAX_VALUE, power) >>> 16;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return TurbineMenu.DATA_COUNT;
            }
        });
    }

    private final class TurbineTank extends FluidTank {
        TurbineTank(int capacity, boolean input) {
            super(capacity, fluid -> !input || recipeFor(fluid) != null);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            // Re-forming a smaller shell keeps its stored contents available until they are drawn down.
            return getSpace() > 0 ? super.fill(resource, action) : 0;
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    }
}
