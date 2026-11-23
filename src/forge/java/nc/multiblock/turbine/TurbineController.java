package nc.multiblock.turbine;

import nc.init.NCTurbine;
import nc.recipe.TurbineRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

import java.util.Map;

public final class TurbineController extends BlockEntity implements MenuProvider {
    private static final int BASE_STEAM = 1000;
    private static final int BASE_EXHAUST = 4000;
    private static final int BASE_ENERGY = 16000;

    private final FluidTank steam = new TurbineTank(BASE_STEAM, true);
    private final FluidTank exhaust = new TurbineTank(BASE_EXHAUST, false);
    private final TurbineDrive drive = new TurbineDrive();
    private long energy;
    private long capacity = BASE_ENERGY;
    private long power;
    private boolean processing;
    private boolean dirty = true;
    private boolean enabled;
    private TurbineLayout layout;
    private Component validationMessage = Component.translatable("message.nuclearcraft.turbine.checking");

    public TurbineController(BlockPos pos, BlockState state) {
        super(NCTurbine.CONTROLLER_ENTITY.get(), pos, state);
    }

    public boolean assembled() {
        return layout != null && !isRemoved() && level instanceof ServerLevel server && layout.bounds().isLoaded(server);
    }

    public Component validationMessage() {
        return validationMessage;
    }

    public void tick(ServerLevel server) {
        if (layout != null && !layout.bounds().isLoaded(server)) {
            disassemble();
        }
        if (dirty || (layout == null && (server.getGameTime() + worldPosition.asLong()) % 20 == 0)) {
            refresh(server);
        }
        boolean active = layout != null && (enabled || server.hasNeighborSignal(worldPosition)
                || layout.redstonePorts().stream().anyMatch(server::hasNeighborSignal));
        if (layout != null) {
            if (!runTurbine(server, active)) {
                return;
            }
            pushOutputs(server);
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
        int rated = drive.ratedFlow(layout);
        int throughput = (int) (rated * (1D + (double) steam.getFluidAmount() / steam.getCapacity()));
        FluidStack product = recipe.output();
        int batches = Math.min(Math.min(steam.getFluidAmount(), throughput) / recipe.inputAmount(),
                exhaust.getSpace() / product.getAmount());
        if (batches <= 0) {
            return 0;
        }
        product.setAmount(product.getAmount() * batches);
        if (exhaust.fill(product, FluidAction.SIMULATE) != product.getAmount()) {
            return 0;
        }
        exhaust.fill(product, FluidAction.EXECUTE);
        int used = batches * recipe.inputAmount();
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
            if (part == TurbinePartBlock.Part.STATOR && server.random.nextFloat() < 0.04F) {
                server.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            } else if (part != null && part.efficiency > 0 && server.random.nextFloat() < 0.18F) {
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
        int reach = TurbinePartBlock.MAX_INTERIOR_LENGTH + 2;
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
        validationMessage = result.message();
        TurbineLayout found = result.layout();
        if (found == null) {
            disassemble();
            return;
        }
        if (found.equals(layout)) {
            return;
        }

        disassemble();
        layout = found;
        capacity = (long) BASE_ENERGY * found.exteriorVolume();
        steam.setCapacity(BASE_STEAM * found.exteriorVolume());
        exhaust.setCapacity(BASE_EXHAUST * found.exteriorVolume());
        faceOutward(server, worldPosition, found.bounds().outward(worldPosition));
        for (Map.Entry<BlockPos, Direction.Axis> entry : found.rotor().entrySet()) {
            BlockState state = server.getBlockState(entry.getKey());
            if (state.getValue(TurbineRotorBlock.AXIS) != entry.getValue()) {
                server.setBlock(entry.getKey(), state.setValue(TurbineRotorBlock.AXIS, entry.getValue()), Block.UPDATE_CLIENTS);
            }
        }
        for (TurbineLayout.Port port : found.ports()) {
            if (server.getBlockEntity(port.pos()) instanceof TurbinePort outlet) {
                outlet.bind(this, port);
                faceOutward(server, port.pos(), port.side());
                server.updateNeighborsAt(port.pos(), outlet.getBlockState().getBlock());
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
        TurbineLayout previous = layout;
        layout = null;
        if (previous == null || !(level instanceof ServerLevel server)) {
            return;
        }
        processing = false;
        power = 0;
        drive.clearRotation();
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
        drive.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        steam.readFromNBT(tag.getCompound("steam"));
        exhaust.readFromNBT(tag.getCompound("exhaust"));
        energy = Math.max(0, tag.getLong("energy"));
        enabled = tag.getBoolean("enabled");
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
                    case TurbineMenu.ENERGY -> (int) (1000L * Math.min(energy, capacity) / capacity);
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
