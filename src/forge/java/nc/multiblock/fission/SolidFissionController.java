package nc.multiblock.fission;

import nc.init.NCFission;
import nc.init.NCFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final FluidTank coolant = new ReactorTank(true);
    private final FluidTank steam = new ReactorTank(false);
    private final Map<BlockPos, Long> clusterHeat = new HashMap<>();

    private SolidFissionLayout layout;
    private SolidFissionCore core;
    private Component validationMessage = Component.translatable("message.nuclearcraft.fission.checking");
    private long casingHeat;
    private boolean enabled;
    private boolean dirty = true;

    public SolidFissionController(BlockPos pos, BlockState state) {
        super(NCFission.CONTROLLER_ENTITY.get(), pos, state);
    }

    public boolean assembled() {
        return layout != null && !isRemoved() && level instanceof ServerLevel server && layout.bounds().isLoaded(server);
    }

    public Component validationMessage() {
        return validationMessage;
    }

    void partChanged(BlockPos pos) {
        int reach = FissionPartBlock.MAX_INTERIOR_LENGTH + 2;
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

    public void tick(ServerLevel server) {
        if (layout != null && !layout.bounds().isLoaded(server)) {
            disassemble();
        }
        if (dirty || (layout == null && (server.getGameTime() + worldPosition.asLong()) % 20 == 0)) {
            refresh(server);
        }
        boolean active = false;
        if (layout != null && (enabled || server.hasNeighborSignal(worldPosition))) {
            SolidFissionCore.Heat generated = core.tick(server, inventory, clusterHeat);
            if (!generated.overheatedCells().isEmpty()) {
                melt(server, generated.overheatedCells());
                return;
            }

            long capacity = heatCapacity();
            casingHeat = Math.min(capacity, casingHeat + generated.raw());
            if (casingHeat >= capacity) {
                casingHeat = 0;
                melt(server, List.of(worldPosition));
                return;
            }
            active = generated.raw() > 0;
            if (active) {
                heatWater(generated.effective() / generated.raw());
                setChanged();
            }
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

        BlockState state = getBlockState();
        Direction outward = layout.bounds().outward(worldPosition);
        if (state.getValue(FissionWallBlock.FACING) != outward) {
            server.setBlock(worldPosition, state.setValue(FissionWallBlock.FACING, outward), Block.UPDATE_CLIENTS);
        }
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
        for (SolidFissionLayout.Port port : previous.ports()) {
            var chunk = server.getChunkSource().getChunkNow(port.pos().getX() >> 4, port.pos().getZ() >> 4);
            if (chunk != null && chunk.getBlockEntity(port.pos()) instanceof FissionPortBlockEntity entity) {
                entity.unbind();
            }
        }
    }

    private void melt(ServerLevel level, List<BlockPos> parts) {
        disassemble();
        for (BlockPos pos : parts) {
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

    private long heatCapacity() {
        return layout == null ? 0 : SolidFissionCore.BASE_HEAT_CAPACITY * layout.bounds().capacityMultiplier();
    }

    FluidTank coolant() {
        return coolant;
    }

    FluidTank steam() {
        return steam;
    }

    ItemStackHandler inventory() {
        return inventory;
    }

    boolean acceptsFuel(ItemStack stack) {
        return level != null && !stack.isEmpty() && level.getRecipeManager()
                .getRecipeFor(NCFission.FUEL_TYPE.get(), new SimpleContainer(stack), level).isPresent();
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
                    case SolidFissionMenu.COOLANT -> tankFill(coolant);
                    case SolidFissionMenu.STEAM -> tankFill(steam);
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
            super(BASE_TANK_CAPACITY, fluid -> water ? fluid.getFluid() == Fluids.WATER
                    : fluid.getFluid() == NCFluids.HIGH_PRESSURE_STEAM.source().get());
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    }
}
