package nc.multiblock.fission;

import nc.multiblock.AssemblyDiagnostic;
import nc.multiblock.Cuboid;
import nc.multiblock.MultiblockFrame;
import nc.multiblock.computer.ComputerPort;
import nc.init.NCFission;
import nc.init.NCPebbleFission;
import nc.init.NCSaltFission;
import org.jetbrains.annotations.Nullable;
import nc.init.NCFluids;
import nc.multiblock.fission.FissionPartBlock.Role;
import nc.radiation.ChunkRadiationSource;
import nc.radiation.WorldRadiation;
import nc.recipe.FissionHeatRecipe;
import nc.recipe.FluidBatch;
import nc.recipe.FluidRecipeLookup;
import nc.util.ValueTracker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.Objects;

public final class SolidFissionController extends BlockEntity implements MenuProvider, ComputerPort.Controller, AssemblyDiagnostic.Controller {
    private static final int BASE_TANK_CAPACITY = 4_000;
    private final ItemStackHandler inventory;
    private final FissionVariant variant;
    private final @Nullable FissionItemFuelBank itemFuel;
    private final FluidTank coolant;
    private final FluidTank steam;
    private final @Nullable PebbleCooling gasCooling;
    private final @Nullable SaltCooling saltCooling;
    private final @Nullable SaltFuelBank saltFuel;
    private final @Nullable ValueTracker heatingRate;
    private final Map<BlockPos, Long> clusterHeat = new HashMap<>();
    private final Map<ResourceLocation, IrradiatorItems> irradiationBanks = new HashMap<>();

    private final ComputerPort.Links computerPorts = new ComputerPort.Links(this);
    private SolidFissionLayout layout;
    private SolidFissionCore core;
    private @Nullable SolidFissionCore prediction;
    private long predictionTick;
    private double predictedOutput;
    private AssemblyDiagnostic validation = new AssemblyDiagnostic(
            Component.translatable("message.nuclearcraft.fission.checking"), null);
    private long casingHeat;
    private double heatingOutput;
    private boolean enabled;
    private boolean dirty = true;
    private long lastAssemblyAttempt = Long.MIN_VALUE;
    private long configRevision = -1;

    public SolidFissionController(BlockPos pos, BlockState state) {
        this(pos, state, FissionVariant.forController(FissionPartBlock.role(state)));
    }

    private SolidFissionController(BlockPos pos, BlockState state, FissionVariant variant) {
        super(variant.entity(), pos, state);
        this.variant = variant;
        itemFuel = variant == FissionVariant.SALT ? null : new FissionItemFuelBank(this, variant);
        inventory = itemFuel != null ? itemFuel.items(null) : new ItemStackHandler(2) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
        coolant = new ReactorTank(true);
        steam = new ReactorTank(false);
        gasCooling = variant == FissionVariant.PEBBLE ? new PebbleCooling(this) : null;
        saltCooling = variant == FissionVariant.SALT ? new SaltCooling(this) : null;
        saltFuel = variant == FissionVariant.SALT ? new SaltFuelBank(this) : null;
        heatingRate = variant == FissionVariant.SOLID ? new ValueTracker() : null;
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

    boolean containsFissionComponent(BlockPos pos) {
        return containsPart(pos) && layout.bounds().boundaryAxes(pos) == 0
                && FissionPartBlock.isClusterComponent(level.getBlockState(pos));
    }

    List<BlockPos> clusterPositions(BlockPos pos) {
        return core == null ? List.of() : core.clusterPositions(pos);
    }

    void partChanged(BlockPos pos, boolean removed) {
        int reach = NCFission.searchLength();
        if (Math.abs(pos.getX() - worldPosition.getX()) <= reach && Math.abs(pos.getY() - worldPosition.getY()) <= reach
                && Math.abs(pos.getZ() - worldPosition.getZ()) <= reach) {
            dirty = true;
            if (removed && clusterHeat.remove(pos) != null) {
                setChanged();
            }
            if (layout != null && layout.bounds().contains(pos)) {
                if (removed && (layout.sources().contains(pos) || layout.shields().contains(pos))
                        && level instanceof ServerLevel server) {
                    for (BlockPos manager : layout.managers()) {
                        controlAt(server, manager).ifPresent(control -> control.unlink(pos));
                    }
                }
                disassemble(false);
            }
        }
    }

    void chunkUnloaded(ChunkPos chunk) {
        if (layout != null && layout.bounds().touchesChunk(chunk)) {
            disassemble(true);
            dirty = true;
        }
    }

    public void tick(ServerLevel server) {
        if (layout != null && !layout.bounds().isLoaded(server)) {
            disassemble(true);
        }
        long revision = NCFission.configRevision();
        if (dirty || revision != configRevision || (layout == null && (server.getGameTime() + worldPosition.asLong()) % 20 == 0)) {
            configRevision = revision;
            refresh(server);
        }
        boolean active = false;
        int heatedOutput = 0;
        boolean requested = layout != null && (enabled || server.hasNeighborSignal(worldPosition));
        var decaySettings = layout == null ? null : NCFission.decaySettings();
        if (layout != null && (requested || decaySettings != null)) {
            SolidFissionCore.Heat generated = core.tick(server, clusterHeat, gasCooling, saltFuel, saltCooling,
                    requested, decaySettings, computerPorts.hasPorts());
            if (!generated.overheatedCells().isEmpty()) {
                meltCluster(server, generated.overheatedCells());
                return;
            }

            long capacity = heatCapacity();
            casingHeat = Math.min(capacity, casingHeat + generated.casingHeat());
            if (NCFission.overheats() && casingHeat >= capacity) {
                casingHeat = 0;
                melt(server, List.of(worldPosition));
                return;
            }
            active = generated.raw() > 0;
            if (active && variant == FissionVariant.SOLID) {
                heatedOutput = exchangeCasingHeat(generated.effective() / generated.raw());
            }
            if (active || generated.storedHeatChanged()) {
                setChanged();
            }
        }
        if (layout != null && !requested && decaySettings == null) {
            core.resetTelemetry();
        }
        if (heatingRate != null && layout != null) {
            if (requested) {
                heatingOutput = heatingRate.update(heatedOutput);
            } else {
                resetHeatingOutput();
            }
        }
        if (layout != null) {
            if (!active && variant != FissionVariant.SOLID) {
                exchangeCasingHeat(1D);
            }
            // A shared tank may have been exhausted by the last cooler or heater to run.
            if (gasCooling != null) {
                gasCooling.discardBlockedProgress(server, layout);
            }
            if (saltCooling != null) {
                saltCooling.discardBlockedProgress(server, layout);
            }
            long lost = NCFission.casingHeatLost(casingHeat, layout.bounds().exteriorSurfaceArea(), active);
            if (lost > 0) {
                casingHeat -= lost;
                setChanged();
            }
        }
        BlockState state = getBlockState();
        if (state.getValue(FissionWallBlock.ACTIVE) == active) {
            return;
        }
        server.setBlock(worldPosition, state.setValue(FissionWallBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        if (layout != null) {
            for (BlockPos pos : layout.monitors()) {
                if (server.getBlockEntity(pos) instanceof FissionMonitor monitor) {
                    monitor.display(active, null);
                }
            }
        }
    }

    boolean restorePortBinding(ServerLevel server) {
        if (layout == null) {
            if (lastAssemblyAttempt == server.getGameTime()) {
                return false;
            }
            refresh(server);
        }
        return true;
    }

    private void refresh(ServerLevel server) {
        lastAssemblyAttempt = server.getGameTime();
        dirty = false;
        SolidFissionLayout.Validation result = SolidFissionLayout.find(server, worldPosition);
        validation = result.diagnostic();
        SolidFissionLayout found = result.layout();
        if (found == null) {
            disassemble(false);
            return;
        }
        if (gasCooling != null) {
            gasCooling.restoreLegacyProgress(server, found);
        }
        if (saltCooling != null) {
            saltCooling.restoreLegacyProgress(server, found);
        }
        if (found.equals(layout)) {
            return;
        }
        disassemble(false);
        layout = found;
        MultiblockFrame.set(server, layout.frames(), true);
        computerPorts.bind(server, layout.computers());
        core = new SolidFissionCore(layout);
        casingHeat = Math.min(casingHeat, heatCapacity());
        int capacity = (int) Math.min(Integer.MAX_VALUE, (long) BASE_TANK_CAPACITY * layout.bounds().capacityMultiplier());
        // A smaller re-formed reactor retains previously stored fluids until they can be drained.
        coolant.setCapacity(Math.max(capacity, coolant.getFluidAmount()));
        steam.setCapacity(Math.max(capacity, steam.getFluidAmount()));
        if (itemFuel != null) {
            itemFuel.bind(server, layout);
        }
        if (gasCooling != null) {
            gasCooling.resize(server, layout);
        }
        if (saltCooling != null && saltFuel != null) {
            saltCooling.resize(server, layout);
            saltFuel.resize(server, layout);
        }

        BlockState state = getBlockState();
        Direction outward = layout.bounds().outward(worldPosition);
        if (state.getValue(FissionWallBlock.FACING) != outward) {
            server.setBlock(worldPosition, state.setValue(FissionWallBlock.FACING, outward), Block.UPDATE_CLIENTS);
        }
        controls(layout).forEach(pos -> controlAt(server, pos).ifPresent(control -> control.bind(this)));
        refreshControlStates(server);
        bindIrradiators(server);
        for (BlockPos pos : layout.monitors()) {
            if (server.getBlockEntity(pos) instanceof FissionMonitor monitor) {
                monitor.bind(this, layout.bounds().outward(pos));
            }
        }
        for (BlockPos pos : layout.cells()) {
            var entity = server.getBlockEntity(pos);
            if (entity instanceof FissionFuelCell cell) {
                cell.bind(this);
            } else if (entity instanceof SaltVessel vessel) {
                vessel.bind(this);
            }
            var decay = FissionFuelDecay.of(entity);
            if (decay != null) {
                decay.bind(this);
            }
        }
        if (gasCooling != null) {
            for (BlockPos pos : layout.sinks()) {
                if (PebbleCoolerBlock.entity(server, pos) instanceof PebbleCooler cooler) {
                    cooler.bind(this);
                }
            }
        }
        for (SolidFissionLayout.Port port : layout.ports()) {
            if (server.getBlockEntity(port.pos()) instanceof FissionPortBlockEntity entity) {
                entity.bind(this, port, layout.bounds());
                BlockState portState = entity.getBlockState();
                if (portState.getValue(FissionWallBlock.FACING) != port.side()) {
                    server.setBlock(port.pos(), portState.setValue(FissionWallBlock.FACING, port.side()), Block.UPDATE_CLIENTS);
                }
                server.updateNeighborsAt(port.pos(), portState.getBlock());
            }
        }
    }

    private void disassemble(boolean suspendPorts) {
        computerPorts.unbind();
        SolidFissionLayout previous = layout;
        layout = null;
        core = null;
        prediction = null;
        if (previous == null || !(level instanceof ServerLevel server)) {
            return;
        }
        resetHeatingOutput();
        MultiblockFrame.set(server, previous.frames(), false);
        irradiationBanks.values().forEach(bank -> bank.resize(0));
        irradiationBanks.clear();
        for (BlockPos pos : previous.monitors()) {
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null && chunk.getBlockEntity(pos) instanceof FissionMonitor monitor) {
                monitor.unbind();
            }
        }
        for (BlockPos pos : previous.cells()) {
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            var entity = chunk == null ? null : chunk.getBlockEntity(pos);
            if (entity instanceof FissionFuelCell cell) {
                cell.bind(null);
            } else if (entity instanceof SaltVessel vessel) {
                vessel.bind(null);
            }
            var decay = FissionFuelDecay.of(entity);
            if (decay != null) {
                decay.unbind(this);
            }
        }
        if (gasCooling != null || saltCooling != null) {
            for (BlockPos pos : previous.sinks()) {
                var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                var part = chunk == null ? null : chunk.getBlockEntity(pos);
                if (part instanceof PebbleCooler cooler) {
                    cooler.bind(null);
                } else if (part instanceof SaltHeater heater) {
                    heater.bind(null);
                }
            }
        }
        for (BlockPos pos : previous.irradiators()) {
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null && chunk.getBlockEntity(pos) instanceof FissionIrradiator irradiator) {
                irradiator.unbind();
            }
        }
        controls(previous).forEach(pos -> controlAt(server, pos).ifPresent(control -> control.unbind(server)));
        for (SolidFissionLayout.Port port : previous.ports()) {
            var chunk = server.getChunkSource().getChunkNow(port.pos().getX() >> 4, port.pos().getZ() >> 4);
            if (chunk != null && chunk.getBlockEntity(port.pos()) instanceof FissionPortBlockEntity entity) {
                entity.unbind(suspendPorts);
            }
        }
    }

    private void bindIrradiators(ServerLevel server) {
        for (SolidFissionLayout.Port port : layout.ports()) {
            if (port.role() == Role.IRRADIATOR_PORT
                    && server.getBlockEntity(port.pos()) instanceof FissionPortBlockEntity entity) {
                IrradiatorItems local = entity.localIrradiatorItems();
                irradiationBanks.merge(local.filter(), local,
                        (first, next) -> !first.hasContents() && next.hasContents() ? next : first);
            }
        }
        Map<IrradiatorItems, Integer> connections = new HashMap<>();
        for (BlockPos pos : layout.irradiators()) {
            if (server.getBlockEntity(pos) instanceof FissionIrradiator irradiator) {
                var bank = irradiationBanks.get(irradiator.localItems().filter());
                irradiator.bind(this, bank);
                if (bank != null) {
                    connections.merge(bank, 1, Integer::sum);
                }
            }
        }
        connections.forEach(IrradiatorItems::resize);
    }

    IrradiatorItems irradiatorItems(IrradiatorItems local) {
        return irradiationBanks.getOrDefault(local.filter(), local);
    }

    private static Stream<BlockPos> controls(SolidFissionLayout layout) {
        return Stream.of(layout.managers(), layout.sources(), layout.shields()).flatMap(List::stream);
    }

    private static Optional<FissionControlBlockEntity> controlAt(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk != null && chunk.getBlockEntity(pos) instanceof FissionControlBlockEntity control) {
            return Optional.of(control);
        }
        return Optional.empty();
    }

    void refreshControlStates(ServerLevel server) {
        if (!assembled()) {
            return;
        }
        Set<BlockPos> sources = Set.copyOf(layout.sources());
        Set<BlockPos> shields = Set.copyOf(layout.shields());
        Set<BlockPos> powered = new HashSet<>();
        for (BlockPos pos : layout.managers()) {
            controlAt(server, pos).ifPresent(manager -> {
                boolean active = server.hasNeighborSignal(pos);
                manager.apply(server, active, layout.bounds().outward(pos));
                Set<BlockPos> candidates = manager.role() == Role.SOURCE_MANAGER ? sources : shields;
                Set<BlockPos> targets = new HashSet<>(manager.listeners());
                if (targets.retainAll(candidates)) {
                    manager.replaceListeners(targets);
                }
                if (active) {
                    powered.addAll(targets);
                }
            });
        }
        for (BlockPos pos : layout.sources()) {
            controlAt(server, pos).ifPresent(source -> source.apply(server,
                    powered.contains(pos) || server.hasNeighborSignal(pos), layout.bounds().outward(pos)));
        }
        for (BlockPos pos : layout.shields()) {
            controlAt(server, pos).ifPresent(shield -> shield.apply(server, powered.contains(pos), null));
        }
    }

    Set<BlockPos> linkControls(ServerLevel level, FissionControlBlockEntity manager, Collection<BlockPos> requested) {
        List<BlockPos> candidates = manager.role() == Role.SOURCE_MANAGER ? layout.sources() : layout.shields();
        Set<BlockPos> selected = new HashSet<>(requested.isEmpty() ? candidates : requested);
        selected.retainAll(candidates);
        for (BlockPos pos : layout.managers()) {
            if (!pos.equals(manager.getBlockPos())) {
                controlAt(level, pos).ifPresent(other -> {
                    var remaining = new HashSet<>(other.listeners());
                    remaining.removeAll(selected);
                    other.replaceListeners(remaining);
                });
            }
        }
        manager.replaceListeners(selected);
        refreshControlStates(level);
        return selected;
    }

    @Nullable BlockPos primingTarget(ServerLevel level, BlockPos source) {
        if (!assembled()) {
            return null;
        }
        return SolidFissionCore.primingTarget(level, layout, source, layout.cells()::contains);
    }

    private void meltCluster(ServerLevel level, List<BlockPos> fuelParts) {
        Set<BlockPos> affected = new LinkedHashSet<>(fuelParts);
        for (BlockPos pos : fuelParts) {
            if (level.getBlockEntity(pos) instanceof ChunkRadiationSource source) {
                WorldRadiation.addLeak(level, pos, 8D * source.radiationEmission() * NCFission.meltdownRadiationMultiplier());
            }
            for (Direction side : Direction.values()) {
                if (level.random.nextDouble() < 0.75D) affected.add(pos.relative(side));
            }
        }
        melt(level, affected);
    }

    private void melt(ServerLevel level, Collection<BlockPos> parts) {
        disassemble(false);
        for (BlockPos pos : parts) {
            level.removeBlockEntity(pos);
            level.setBlockAndUpdate(pos, NCFluids.CORIUM.block().get().defaultBlockState());
        }
        setChanged();
    }

    private RecipeType<FissionHeatRecipe> casingRecipeType() {
        return (variant == FissionVariant.SOLID ? NCFission.HEATING_TYPE : NCFission.EMERGENCY_COOLING_TYPE).get();
    }

    private boolean acceptsCasingInput(FluidStack fluid) {
        return FluidRecipeLookup.accepts(level, casingRecipeType(), fluid, FissionHeatRecipe::input);
    }

    private @Nullable FissionHeatRecipe casingRecipe(FluidStack fluid) {
        return FluidRecipeLookup.match(level, casingRecipeType(), fluid, FissionHeatRecipe::input);
    }

    private int exchangeCasingHeat(double efficiency) {
        if (!(efficiency > 0D) || casingHeat == 0 || coolant.isEmpty()) {
            return 0;
        }
        FissionHeatRecipe recipe = casingRecipe(coolant.getFluid());
        if (recipe == null) {
            return 0;
        }
        long availableInput = coolant.getFluidAmount();
        double heatPerMb = recipe.heatPerMb();
        if (recipe.emergency()) {
            long vents = layout.ports().stream().filter(port -> port.role() == Role.VENT).count();
            availableInput = Math.min(availableInput, BASE_TANK_CAPACITY * vents);
            if (recipe.baseHeat() <= 0D && recipe.profile() == FissionHeatRecipe.Profile.EMERGENCY) {
                heatPerMb *= variant == FissionVariant.SALT
                        ? NCSaltFission.emergencyMultiplier() : NCPebbleFission.emergencyMultiplier();
            }
        }
        if (!Double.isFinite(heatPerMb)) {
            return 0;
        }
        var product = recipe.product();
        int room = product.batchesIn(steam.getFluid(), steam.getCapacity());
        int budget = (int) Math.min(availableInput, casingHeat * efficiency / heatPerMb);
        var usage = FluidBatch.fit(recipe.input(), coolant.getFluid(), budget, room);
        if (usage.batches() == 0) {
            return 0;
        }
        var produced = product.stack();
        int perBatch = recipe.emergency()
                ? (product.fillsExhaust(steam.getFluid(), level.random) ? produced.getAmount() : 0)
                : product.rollAmount(level.random);
        int amount = perBatch * usage.batches();
        if (amount > 0) {
            produced.setAmount(amount);
            steam.fill(produced, FluidAction.EXECUTE);
        }
        coolant.drain(usage.consumed(), FluidAction.EXECUTE);
        casingHeat -= Math.min(casingHeat, (long) (usage.consumed() * heatPerMb / efficiency));
        setChanged();
        return amount;
    }

    private void resetHeatingOutput() {
        if (heatingRate != null && heatingOutput != 0D) {
            heatingRate.reset();
            heatingOutput = 0D;
        }
    }

    SolidFissionLayout computerLayout() { return layout; }

    SolidFissionCore computerCore() {
        if (core == null || computerOn() || !(level instanceof ServerLevel server)) {
            return core;
        }
        if (prediction == null || predictionTick != server.getGameTime()) {
            prediction = new SolidFissionCore(layout);
            predictionTick = server.getGameTime();
            var heat = prediction.predict(server, clusterHeat, gasCooling, saltFuel, saltCooling, NCFission.decaySettings());
            predictedOutput = variant == FissionVariant.SOLID ? predictHeatingOutput(heat.effective()) : 0D;
        }
        return prediction;
    }

    private double predictHeatingOutput(double effectiveHeating) {
        FissionHeatRecipe recipe = casingRecipe(coolant.getFluid());
        if (recipe == null || !(effectiveHeating > 0)) {
            return 0D;
        }
        int dose = recipe.input().matchedAmount(coolant.getFluid(), 1);
        return dose > 0 ? recipe.product().stack().getAmount() * effectiveHeating / (dose * recipe.heatPerMb()) : 0D;
    }

    long computerHeat() { return casingHeat; }

    double computerTemperature() {
        BlockPos center = layout.bounds().min().offset(layout.bounds().xLength() / 2,
                layout.bounds().yLength() / 2, layout.bounds().zLength() / 2);
        double ambient = 273.15D + 20D * level.getBiome(center).value().getBaseTemperature();
        return ambient + (2400D - ambient) * (double) casingHeat / heatCapacity();
    }

    boolean computerOn() {
        return getBlockState().getValue(FissionWallBlock.ACTIVE);
    }

    CoolantTanks pebbleCircuit(PebbleCooling.Channel gas) {
        return gasCooling.circuit(gas);
    }

    CoolantTanks pebbleTanks(PebbleCooler cooler) {
        return gasCooling.tanks(cooler);
    }

    boolean isCooling(BlockPos pos) {
        return core != null && core.isCooling(pos);
    }

    void computerClear() {
        if (itemFuel != null) {
            itemFuel.clear();
        } else {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        coolant.setFluid(FluidStack.EMPTY);
        steam.setFluid(FluidStack.EMPTY);
        resetHeatingOutput();
        if (gasCooling != null) {
            gasCooling.clearMaterial();
            for (BlockPos pos : layout.sinks()) {
                if (level.getBlockEntity(pos) instanceof PebbleCooler cooler) {
                    cooler.localTanks().clear();
                    cooler.progress().set(0D);
                }
            }
        }
        if (saltFuel != null) saltFuel.clearMaterial();
        if (saltCooling != null) {
            saltCooling.clearMaterial();
            for (BlockPos pos : layout.sinks()) {
                if (level.getBlockEntity(pos) instanceof SaltHeater heater) {
                    heater.localTanks().clear();
                    heater.progress().set(0D);
                }
            }
        }
        for (BlockPos pos : layout.cells()) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof FissionFuelCell cell) {
                cell.clearMaterial();
            } else if (entity instanceof SaltVessel vessel) {
                vessel.clearMaterial();
            }
        }
        for (BlockPos pos : layout.irradiators()) {
            if (level.getBlockEntity(pos) instanceof FissionIrradiator irradiator) {
                irradiator.clearMaterial();
            }
        }
        for (SolidFissionLayout.Port port : layout.ports()) {
            if (port.role() == Role.IRRADIATOR_PORT
                    && level.getBlockEntity(port.pos()) instanceof FissionPortBlockEntity entity) {
                entity.localIrradiatorItems().clearContents();
            } else if (port.itemFuel() && level.getBlockEntity(port.pos()) instanceof FissionPortBlockEntity entity) {
                entity.localFuelItems().clearContents();
            } else if (port.role() == Role.COOLER_PORT && level.getBlockEntity(port.pos()) instanceof FissionPortBlockEntity entity) {
                entity.localPebbleGas().clear();
            } else if (port.role() == Role.HEATER_PORT && level.getBlockEntity(port.pos()) instanceof FissionPortBlockEntity entity) {
                entity.localSaltCoolant().clear();
            } else if (port.role() == Role.VESSEL_PORT && level.getBlockEntity(port.pos()) instanceof FissionPortBlockEntity entity) {
                entity.localVesselFuel().clear();
            }
        }
        setChanged();
    }

    long heatCapacity() {
        return layout == null ? 0 : SolidFissionCore.BASE_HEAT_CAPACITY * layout.bounds().capacityMultiplier();
    }

    FluidTank coolant() {
        return coolant;
    }

    FluidTank steam() {
        return steam;
    }

    FluidTank fluidTank(SolidFissionLayout.Port port) {
        if (port.gas() != null && gasCooling != null) {
            return gasCooling.circuit(port.gas()).tank(port.output());
        }
        if (port.salt() != null && saltCooling != null) {
            return saltCooling.tank(port.salt(), port.output());
        }
        if (port.role() == FissionPartBlock.Role.VESSEL_PORT && saltFuel != null) {
            return saltFuel.circuit(port.filter()).tank(port.output());
        }
        return port.output() ? steam : coolant;
    }

    SaltFuelBank saltFuelBank() {
        return Objects.requireNonNull(saltFuel);
    }

    @Nullable CoolantTanks connectedSaltCoolant(FissionCoolant coolant) {
        return !assembled() || saltCooling == null ? null : saltCooling.connectedTanks(coolant);
    }

    boolean containsPart(BlockPos pos) {
        return assembled() && layout.bounds().contains(pos);
    }

    float contactDamage(ServerLevel server, BlockPos pos) {
        if (!containsPart(pos)) {
            return 0;
        }
        var bounds = layout.bounds();
        BlockPos center = bounds.min().offset((bounds.xLength() - 1) / 2, (bounds.yLength() - 1) / 2,
                (bounds.zLength() - 1) / 2);
        double ambient = 273.15D + 20D * server.getBiome(center).value().getBaseTemperature();
        var cluster = partHeat(pos);
        double fill = cluster.capacity() == 0 ? (double) casingHeat / heatCapacity()
                : (double) cluster.amount() / cluster.capacity();
        double temperature = ambient + (2400D - ambient) * fill;
        return temperature < 353D ? 0F : (float) (1D + (temperature - 353D) / 200D);
    }

    SolidFissionCore.PartHeat partHeat(BlockPos pos) {
        return core == null || !(level instanceof ServerLevel server) ? SolidFissionCore.PartHeat.NONE
                : core.partHeat(server, pos, clusterHeat, getBlockState().getValue(FissionWallBlock.ACTIVE));
    }

    FissionVariant variant() {
        return variant;
    }

    ItemStackHandler inventory() {
        return inventory;
    }

    boolean hasItemFuelCircuit(@Nullable ResourceLocation filter) {
        return itemFuel != null && itemFuel.connected(filter);
    }

    FissionFuelItems fuelItems(@Nullable ResourceLocation filter) {
        return itemFuel.items(filter);
    }

    void dropFuelItems() {
        if (itemFuel != null) {
            itemFuel.dropContents();
        } else {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                        inventory.getStackInSlot(slot));
            }
        }
    }

    void toggle() {
        enabled = !enabled;
        setChanged();
    }

    @Override
    public void setRemoved() {
        disassemble(false);
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        disassemble(true);
        dirty = true;
        super.onChunkUnloaded();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", inventory.serializeNBT());
        if (itemFuel != null) {
            tag.put(variant.itemCircuitsKey(), itemFuel.save());
        }
        tag.put("coolant", coolant.writeToNBT(new CompoundTag()));
        tag.put("steam", steam.writeToNBT(new CompoundTag()));
        if (gasCooling != null) {
            tag.put("gas_cooling", gasCooling.save());
        }
        if (saltCooling != null && saltFuel != null) {
            tag.put("salt_cooling", saltCooling.save());
            tag.put("salt_fuel", saltFuel.save());
        }
        tag.putLong("heat", casingHeat);
        tag.putBoolean("enabled", enabled);
        ListTag storedClusters = new ListTag();
        for (Map.Entry<BlockPos, Long> entry : clusterHeat.entrySet()) {
            if (entry.getValue() > 0) {
                CompoundTag cluster = new CompoundTag();
                cluster.putLong("pos", entry.getKey().asLong());
                cluster.putLong("heat", entry.getValue());
                storedClusters.add(cluster);
            }
        }
        tag.put("clusters", storedClusters);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("inventory"));
        if (itemFuel != null) {
            itemFuel.load(tag.getList(variant.itemCircuitsKey(), Tag.TAG_COMPOUND));
        }
        coolant.readFromNBT(tag.getCompound("coolant"));
        steam.readFromNBT(tag.getCompound("steam"));
        if (gasCooling != null) {
            gasCooling.load(tag.getCompound("gas_cooling"));
        }
        if (saltCooling != null && saltFuel != null) {
            saltCooling.load(tag.getCompound("salt_cooling"));
            saltFuel.load(tag.getList("salt_fuel", Tag.TAG_COMPOUND));
        }
        casingHeat = Math.max(0, tag.getLong("heat"));
        enabled = tag.getBoolean("enabled");
        clusterHeat.clear();
        ListTag storedClusters = tag.getList("clusters", Tag.TAG_COMPOUND);
        for (int i = 0; i < storedClusters.size(); i++) {
            CompoundTag cluster = storedClusters.getCompound(i);
            clusterHeat.put(BlockPos.of(cluster.getLong("pos")), Math.max(0, cluster.getLong("heat")));
        }
        dirty = true;
    }

    private FissionTankTotals tankTotals(ServerLevel server) {
        if (saltCooling != null) {
            return saltCooling.tankTotals(server, layout);
        }
        if (gasCooling != null) {
            return gasCooling.tankTotals(server, layout);
        }
        var totals = new FissionTankTotals();
        totals.add(coolant, steam);
        return totals;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public SolidFissionMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new SolidFissionMenu(id, this, ContainerLevelAccess.create(player.level(), worldPosition));
    }

    void updateMenu(SolidFissionMenu menu) {
        if (layout == null || !(level instanceof ServerLevel server)) {
            menu.clearReadings();
            return;
        }
        var bounds = layout.bounds();
        var diagnostic = computerCore();
        var heat = diagnostic.heatStats();
        var tanks = tankTotals(server);
        int flags = enabled ? SolidFissionMenu.ENABLED : 0;
        if (computerOn()) {
            flags |= SolidFissionMenu.ACTIVE;
        } else if (heat.heating() > 0) {
            flags |= SolidFissionMenu.PREDICTED;
        }
        menu.set(SolidFissionMenu.Reading.FLAGS, flags);
        menu.set(SolidFissionMenu.Reading.LENGTH_X, bounds.xLength() - 2);
        menu.set(SolidFissionMenu.Reading.LENGTH_Y, bounds.yLength() - 2);
        menu.set(SolidFissionMenu.Reading.LENGTH_Z, bounds.zLength() - 2);
        menu.set(SolidFissionMenu.Reading.VOLUME, bounds.interiorVolume());
        menu.set(SolidFissionMenu.Reading.CLUSTERS, heat.clusters());
        menu.set(SolidFissionMenu.Reading.USEFUL_PARTS, heat.usefulParts());
        menu.set(SolidFissionMenu.Reading.HEAT, casingHeat);
        menu.set(SolidFissionMenu.Reading.HEAT_CAPACITY, heatCapacity());
        menu.set(SolidFissionMenu.Reading.HEATING, heat.heating());
        menu.set(SolidFissionMenu.Reading.COOLING, heat.cooling());
        menu.set(SolidFissionMenu.Reading.TEMPERATURE, computerTemperature());
        menu.set(SolidFissionMenu.Reading.EFFICIENCY, heat.efficiency());
        menu.set(SolidFissionMenu.Reading.HEAT_MULTIPLIER, heat.heatMultiplier());
        menu.set(SolidFissionMenu.Reading.SPARSITY, heat.sparsity());
        double outputRate = variant == FissionVariant.SOLID
                ? (diagnostic == prediction ? predictedOutput : heatingOutput) : heat.coolantSpeed();
        menu.set(SolidFissionMenu.Reading.OUTPUT_RATE, outputRate);
        menu.set(SolidFissionMenu.Reading.COLD_AMOUNT, tanks.coldAmount);
        menu.set(SolidFissionMenu.Reading.COLD_CAPACITY, tanks.coldCapacity);
        menu.set(SolidFissionMenu.Reading.HOT_AMOUNT, tanks.hotAmount);
        menu.set(SolidFissionMenu.Reading.HOT_CAPACITY, tanks.hotCapacity);
    }

    private final class ReactorTank extends FluidTank {
        ReactorTank(boolean input) {
            super(BASE_TANK_CAPACITY, fluid -> !input || acceptsCasingInput(fluid));
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    }
}
