package nc.multiblock.fission;

import nc.init.NCFission;
import nc.init.NCPebbleFission;
import nc.init.NCSaltCoolants;
import nc.init.NCSaltFission;
import org.jetbrains.annotations.Nullable;
import nc.init.NCFluids;
import nc.multiblock.fission.FissionPartBlock.Role;
import nc.radiation.ChunkRadiationSource;
import nc.radiation.WorldRadiation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class SolidFissionController extends BlockEntity implements MenuProvider {
    private static final int BASE_TANK_CAPACITY = 4_000;
    private static final int WATER_HEAT_PER_MB = 64;
    private static final int STEAM_PER_WATER = 4;
    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final FissionVariant variant;
    private final FluidTank coolant;
    private final FluidTank steam;
    private final @Nullable PebbleCooling gasCooling;
    private final @Nullable SaltCooling saltCooling;
    private final @Nullable SaltFuelBank saltFuel;
    private final Map<BlockPos, Long> clusterHeat = new HashMap<>();

    private SolidFissionLayout layout;
    private SolidFissionCore core;
    private Component validationMessage = Component.translatable("message.nuclearcraft.fission.checking");
    private long casingHeat;
    private boolean enabled;
    private boolean dirty = true;

    public SolidFissionController(BlockPos pos, BlockState state) {
        this(pos, state, FissionVariant.forController(FissionPartBlock.role(state)));
    }

    private SolidFissionController(BlockPos pos, BlockState state, FissionVariant variant) {
        super(variant.entity(), pos, state);
        this.variant = variant;
        coolant = new ReactorTank(true);
        steam = new ReactorTank(false);
        gasCooling = variant == FissionVariant.PEBBLE ? new PebbleCooling(this) : null;
        saltCooling = variant == FissionVariant.SALT ? new SaltCooling(this) : null;
        saltFuel = variant == FissionVariant.SALT ? new SaltFuelBank(this) : null;
    }

    public boolean assembled() {
        return layout != null && !isRemoved() && level instanceof ServerLevel server && layout.bounds().isLoaded(server);
    }

    public Component validationMessage() {
        return validationMessage;
    }

    void partChanged(BlockPos pos, boolean removed) {
        int reach = FissionPartBlock.MAX_INTERIOR_LENGTH + 2;
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

    public void tick(ServerLevel server) {
        if (layout != null && !layout.bounds().isLoaded(server)) {
            disassemble();
        }
        if (dirty || (layout == null && (server.getGameTime() + worldPosition.asLong()) % 20 == 0)) {
            refresh(server);
        }
        boolean active = false;
        if (layout != null && (enabled || server.hasNeighborSignal(worldPosition))) {
            SolidFissionCore.Heat generated = core.tick(server, inventory, clusterHeat, gasCooling, saltFuel, saltCooling);
            if (!generated.overheatedCells().isEmpty()) {
                melt(server, generated.overheatedCells());
                return;
            }

            long capacity = heatCapacity();
            casingHeat = Math.min(capacity, casingHeat + generated.casingHeat());
            if (casingHeat >= capacity) {
                casingHeat = 0;
                melt(server, List.of(worldPosition));
                return;
            }
            active = generated.raw() > 0;
            if (active && variant == FissionVariant.SOLID) {
                heatWater(generated.effective() / generated.raw());
            }
            if (active || generated.storedHeatChanged()) {
                setChanged();
            }
        } else if (layout != null && variant != FissionVariant.SOLID && casingHeat > 0) {
            coolCasing();
        }
        BlockState state = getBlockState();
        if (state.getValue(FissionWallBlock.ACTIVE) != active) {
            server.setBlock(worldPosition, state.setValue(FissionWallBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    private void refresh(ServerLevel server) {
        dirty = false;
        SolidFissionLayout.Validation result = SolidFissionLayout.find(server, worldPosition);
        validationMessage = result.message();
        SolidFissionLayout found = result.layout();
        if (found == null) {
            disassemble();
            return;
        }
        if (found.equals(layout)) {
            return;
        }
        disassemble();
        layout = found;
        core = new SolidFissionCore(layout);
        casingHeat = Math.min(casingHeat, heatCapacity());
        int capacity = BASE_TANK_CAPACITY * layout.bounds().capacityMultiplier();
        // A smaller re-formed reactor retains previously stored fluids until they can be drained.
        coolant.setCapacity(Math.max(capacity, coolant.getFluidAmount()));
        steam.setCapacity(Math.max(capacity, steam.getFluidAmount()));
        if (gasCooling != null) {
            gasCooling.resize(layout);
        }
        if (saltCooling != null && saltFuel != null) {
            saltCooling.resize(layout);
            saltFuel.resize(layout);
        }

        BlockState state = getBlockState();
        Direction outward = layout.bounds().outward(worldPosition);
        if (state.getValue(FissionWallBlock.FACING) != outward) {
            server.setBlock(worldPosition, state.setValue(FissionWallBlock.FACING, outward), Block.UPDATE_CLIENTS);
        }
        controls(layout).forEach(pos -> controlAt(server, pos).ifPresent(control -> control.bind(this)));
        refreshControlStates(server);
        for (SolidFissionLayout.Port port : layout.ports()) {
            if (server.getBlockEntity(port.pos()) instanceof FissionPortBlockEntity entity) {
                entity.bind(this, port);
                BlockState portState = entity.getBlockState();
                if (portState.getValue(FissionWallBlock.FACING) != port.side()) {
                    server.setBlock(port.pos(), portState.setValue(FissionWallBlock.FACING, port.side()), Block.UPDATE_CLIENTS);
                }
                server.updateNeighborsAt(port.pos(), portState.getBlock());
            }
        }
    }

    private void disassemble() {
        SolidFissionLayout previous = layout;
        layout = null;
        core = null;
        if (previous == null || !(level instanceof ServerLevel server)) {
            return;
        }
        controls(previous).forEach(pos -> controlAt(server, pos).ifPresent(control -> control.unbind(server)));
        for (SolidFissionLayout.Port port : previous.ports()) {
            var chunk = server.getChunkSource().getChunkNow(port.pos().getX() >> 4, port.pos().getZ() >> 4);
            if (chunk != null && chunk.getBlockEntity(port.pos()) instanceof FissionPortBlockEntity entity) {
                entity.unbind();
            }
        }
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

    private void melt(ServerLevel level, List<BlockPos> parts) {
        disassemble();
        for (BlockPos pos : parts) {
            if (level.getBlockEntity(pos) instanceof ChunkRadiationSource source) {
                WorldRadiation.addLeak(level, pos, 8D * source.radiationEmission() * NCFission.meltdownRadiationMultiplier());
            }
            level.removeBlockEntity(pos);
            level.setBlockAndUpdate(pos, NCFluids.CORIUM.block().get().defaultBlockState());
        }
        setChanged();
    }

    private void heatWater(double efficiency) {
        int steamRoom = (steam.getCapacity() - steam.getFluidAmount()) / STEAM_PER_WATER;
        int water = Math.min(Math.min(coolant.getFluidAmount(), steamRoom),
                (int) (casingHeat * efficiency / WATER_HEAT_PER_MB));
        if (water > 0) {
            FluidStack product = new FluidStack(NCFluids.HIGH_PRESSURE_STEAM.source().get(), water * STEAM_PER_WATER);
            if (steam.fill(product, FluidAction.SIMULATE) == product.getAmount()) {
                steam.fill(product, FluidAction.EXECUTE);
                coolant.drain(water, FluidAction.EXECUTE);
                casingHeat -= (long) (water * (double) WATER_HEAT_PER_MB / efficiency);
            }
        }
    }

    private void coolCasing() {
        double heatPerMb = variant == FissionVariant.SALT ? NCSaltFission.emergencyMultiplier() : NCPebbleFission.emergencyMultiplier();
        int amount = Math.min(coolant.getFluidAmount(), Math.min(steam.getCapacity() - steam.getFluidAmount(),
                (int) (casingHeat / heatPerMb)));
        if (amount > 0) {
            var product = new FluidStack(NCFluids.HEATED_EMERGENCY_COOLANT.source().get(), amount);
            if (steam.fill(product, FluidAction.SIMULATE) == amount) {
                steam.fill(product, FluidAction.EXECUTE);
                coolant.drain(amount, FluidAction.EXECUTE);
                casingHeat = (long) Math.max(0, casingHeat - amount * heatPerMb);
                setChanged();
            }
        }
    }

    private long heatCapacity() {
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
            return gasCooling.tank(port.gas(), port.output());
        }
        if (port.salt() != null && saltCooling != null) {
            return saltCooling.tank(port.salt(), port.output());
        }
        if (port.role() == FissionPartBlock.Role.VESSEL_PORT && saltFuel != null) {
            return saltFuel.tank(port.fuelFilter(), port.output());
        }
        return port.output() ? steam : coolant;
    }

    boolean containsSaltComponent(BlockPos pos, SaltFissionPartMenu.Kind kind) {
        return variant == FissionVariant.SALT && assembled() && layout.bounds().contains(pos)
                && (kind == SaltFissionPartMenu.Kind.VESSEL ? layout.cells() : layout.sinks()).contains(pos);
    }

    @Nullable FluidTank existingSaltFuelTank(@Nullable ResourceLocation filter, boolean output) {
        return saltFuel == null ? null : saltFuel.existingTank(filter, output);
    }

    @Nullable FluidTank existingSaltCoolantTank(NCSaltCoolants coolant, boolean output) {
        return saltCooling == null ? null : saltCooling.existingTank(coolant, output);
    }

    double heaterProgress(BlockPos pos, NCSaltCoolants coolant) {
        return saltCooling == null ? 0 : saltCooling.progress(pos, coolant);
    }

    SolidFissionCore.PartHeat partHeat(BlockPos pos) {
        return core == null ? SolidFissionCore.PartHeat.NONE
                : core.partHeat(pos, clusterHeat, getBlockState().getValue(FissionWallBlock.ACTIVE));
    }

    FissionVariant variant() {
        return variant;
    }

    ItemStackHandler inventory() {
        return inventory;
    }

    boolean acceptsFuel(ItemStack stack) {
        return level != null && !stack.isEmpty() && level.getRecipeManager()
                .getRecipeFor(variant == FissionVariant.PEBBLE ? NCPebbleFission.FUEL_TYPE.get() : NCFission.FUEL_TYPE.get(), new SimpleContainer(stack), level).isPresent();
    }

    void toggle() {
        enabled = !enabled;
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

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", inventory.serializeNBT());
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

    private static int tankFill(FluidTank tank) {
        return (int) (1000L * tank.getFluidAmount() / tank.getCapacity());
    }

    private int coolerFill(boolean output) {
        if (saltCooling != null) {
            int capacity = saltCooling.capacity(output);
            return capacity == 0 ? 0 : (int) (1000L * saltCooling.amount(output) / capacity);
        }
        if (gasCooling == null) {
            return tankFill(output ? steam : coolant);
        }
        int capacity = gasCooling.gasCapacity(output);
        return capacity == 0 ? 0 : (int) (1000L * gasCooling.gasAmount(output) / capacity);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public SolidFissionMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new SolidFissionMenu(id, this, ContainerLevelAccess.create(player.level(), worldPosition), new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case SolidFissionMenu.ENABLED -> enabled ? 1 : 0;
                    case SolidFissionMenu.ACTIVE -> getBlockState().getValue(FissionWallBlock.ACTIVE) ? 1 : 0;
                    case SolidFissionMenu.HEAT -> heatCapacity() == 0 ? 0 : (int) (1000 * casingHeat / heatCapacity());
                    case SolidFissionMenu.COOLANT -> coolerFill(false);
                    case SolidFissionMenu.STEAM -> coolerFill(true);
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return SolidFissionMenu.DATA_COUNT;
            }
        });
    }

    private final class ReactorTank extends FluidTank {
        ReactorTank(boolean water) {
            super(BASE_TANK_CAPACITY, fluid -> water
                    ? fluid.getFluid() == (variant != FissionVariant.SOLID ? NCFluids.EMERGENCY_COOLANT.source().get() : Fluids.WATER)
                    : fluid.getFluid() == (variant != FissionVariant.SOLID ? NCFluids.HEATED_EMERGENCY_COOLANT.source().get()
                            : NCFluids.HIGH_PRESSURE_STEAM.source().get()));
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    }
}
