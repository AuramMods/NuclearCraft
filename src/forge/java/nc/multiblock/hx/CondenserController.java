package nc.multiblock.hx;

import nc.init.NCCondenser;
import nc.recipe.CondenserRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

public final class CondenserController extends BlockEntity implements MenuProvider {
    private static final int SHELL_CAPACITY_PER_BLOCK = 4000;
    private final Tank coolant = new Tank(1, stack -> temperature(stack) <= 300);
    private final Map<BlockPos, Circuit> circuits = new LinkedHashMap<>();
    private CondenserLayout layout;
    private Component validation = Component.translatable("message.nuclearcraft.exchanger.checking");
    private boolean dirty = true;
    private boolean enabled;
    private int activeCircuits;
    private int activeTubes;
    private double heatRate;

    public CondenserController(BlockPos pos, BlockState state) {
        super(NCCondenser.CONTROLLER_ENTITY.get(), pos, state);
    }

    boolean assembled() {
        return layout != null && !isRemoved() && level instanceof ServerLevel server && layout.bounds().isLoaded(server);
    }

    Component validationMessage() {
        return validation;
    }

    void tick(ServerLevel server) {
        if (layout != null && !layout.bounds().isLoaded(server)) {
            disassemble();
        }
        if (dirty || (layout == null && (server.getGameTime() + worldPosition.asLong()) % 20 == 0)) {
            refresh(server);
        }
        activeCircuits = activeTubes = 0;
        heatRate = 0D;
        boolean on = layout != null && (enabled || server.hasNeighborSignal(worldPosition)
                || layout.redstone().stream().anyMatch(server::hasNeighborSignal));
        if (layout != null) {
            if (on && !coolant.isEmpty()) {
                int shellTemperature = temperature(coolant.getFluid());
                double shellFraction = (double) coolant.getFluidAmount() / coolant.getCapacity();
                for (CondenserLayout.Network network : layout.networks()) {
                    process(network, circuits.get(network.anchor()), shellTemperature, Math.min(1D, shellFraction));
                }
            }
            pushOutputs(server);
        }

        BlockState state = getBlockState();
        if (state.getValue(ExchangerWallBlock.ACTIVE) != on) {
            server.setBlock(worldPosition, state.setValue(ExchangerWallBlock.ACTIVE, on), Block.UPDATE_CLIENTS);
        }
    }

    private void process(CondenserLayout.Network network, Circuit circuit, int shellTemperature, double shellFraction) {
        CondenserRecipe recipe = recipeFor(circuit.input.getFluid());
        if (recipe == null || recipe.outputTemperature() < shellTemperature) {
            return;
        }
        FluidStack product = recipe.output();
        int inputBatches = circuit.input.getFluidAmount() / recipe.input().amount();
        int room = circuit.output.getSpace();
        if (room < product.getAmount() || inputBatches == 0 || (!circuit.output.isEmpty()
                && !circuit.output.getFluid().isFluidEqual(product))) {
            return;
        }

        double meanDifference = (recipe.inputTemperature() + (double) recipe.outputTemperature()) / 2D - shellTemperature;
        double rate = meanDifference * recipe.flowMultiplier(network.flow()) * network.conductivity() * shellFraction;
        if (rate <= 0D) {
            return;
        }
        if (!recipe.id().equals(circuit.recipe)) {
            circuit.credit = 0D;
            circuit.recipe = recipe.id();
        }
        activeCircuits++;
        activeTubes += network.usefulTubes();
        heatRate += rate;
        int capacity = Math.min(inputBatches, room / product.getAmount());
        double credit = Math.min(circuit.credit + rate, recipe.cooling() * capacity);
        int batches = Math.min(capacity, (int) (credit / recipe.cooling()));
        circuit.credit = Math.max(0D, credit - batches * recipe.cooling());
        if (batches > 0) {
            product.setAmount(product.getAmount() * batches);
            circuit.output.fill(product, FluidAction.EXECUTE);
            circuit.input.drain(batches * recipe.input().amount(), FluidAction.EXECUTE);
        }
        setChanged();
    }

    private CondenserRecipe recipeFor(FluidStack stack) {
        if (level != null && !stack.isEmpty()) {
            for (CondenserRecipe recipe : level.getRecipeManager().getAllRecipesFor(NCCondenser.RECIPES.get())) {
                if (recipe.input().accepts(stack)) {
                    return recipe;
                }
            }
        }
        return null;
    }

    private static int temperature(FluidStack stack) {
        return stack.getFluid().getFluidType().getTemperature(stack);
    }

    private void pushOutputs(ServerLevel server) {
        for (CondenserLayout.Port port : layout.ports()) {
            if (!port.outlet()) {
                continue;
            }
            Tank tank = tank(port);
            if (tank.isEmpty()) {
                continue;
            }
            BlockPos adjacent = port.pos().relative(port.side());
            var chunk = server.getChunkSource().getChunkNow(adjacent.getX() >> 4, adjacent.getZ() >> 4);
            BlockEntity entity = chunk == null ? null : chunk.getBlockEntity(adjacent);
            if (entity != null) {
                entity.getCapability(ForgeCapabilities.FLUID_HANDLER, port.side().getOpposite())
                        .ifPresent(target -> push(tank, target));
            }
        }
    }

    private static void push(Tank from, IFluidHandler target) {
        int accepted = target.fill(from.getFluid().copy(), FluidAction.SIMULATE);
        if (accepted <= 0) {
            return;
        }
        FluidStack reserved = from.drain(accepted, FluidAction.EXECUTE);
        int transferred = 0;
        try {
            transferred = Math.max(0, Math.min(reserved.getAmount(), target.fill(reserved.copy(), FluidAction.EXECUTE)));
        } finally {
            int unsent = reserved.getAmount() - transferred;
            if (unsent > 0) {
                reserved.setAmount(from.getFluidAmount() + unsent);
                from.setFluid(reserved);
            }
        }
    }

    private void refresh(ServerLevel server) {
        dirty = false;
        CondenserLayout.Validation result = CondenserLayout.find(server, worldPosition);
        if (result.layout() == null) {
            validation = Component.translatable("message.nuclearcraft.exchanger." + result.error(),
                    result.position().getX(), result.position().getY(), result.position().getZ());
            return;
        }
        layout = result.layout();
        var bounds = layout.bounds();
        BlockState state = getBlockState();
        if (state.getValue(ExchangerWallBlock.FACING) != bounds.outward(worldPosition)) {
            server.setBlock(worldPosition, state.setValue(ExchangerWallBlock.FACING, bounds.outward(worldPosition)),
                    Block.UPDATE_CLIENTS);
        }
        coolant.setCapacity(SHELL_CAPACITY_PER_BLOCK * bounds.xLength() * bounds.yLength() * bounds.zLength());
        for (CondenserLayout.Network network : layout.networks()) {
            Circuit circuit = circuits.computeIfAbsent(network.anchor(), key -> new Circuit());
            circuit.input.setCapacity(32000 * (network.tubeCount() + 2));
            circuit.output.setCapacity(8000 * (network.tubeCount() + 2));
        }
        for (CondenserLayout.Port port : layout.ports()) {
            if (server.getBlockEntity(port.pos()) instanceof CondenserPort entity) {
                BlockState portState = entity.getBlockState();
                if (portState.getValue(ExchangerWallBlock.FACING) != port.side()) {
                    server.setBlock(port.pos(), portState.setValue(ExchangerWallBlock.FACING, port.side()), Block.UPDATE_CLIENTS);
                }
                entity.bind(this, port, tank(port));
            }
        }
        for (CondenserLayout.Port port : layout.ports()) {
            notifyPipe(server, port, server.getBlockState(port.pos()).getBlock());
        }
        validation = Component.empty();
    }

    private Tank tank(CondenserLayout.Port port) {
        if (port.circuit() == null) {
            return coolant;
        }
        Circuit circuit = circuits.get(port.circuit());
        return port.outlet() ? circuit.output : circuit.input;
    }

    private void disassemble() {
        CondenserLayout old = layout;
        layout = null;
        if (old != null && level instanceof ServerLevel server) {
            for (CondenserLayout.Port port : old.ports()) {
                var chunk = server.getChunkSource().getChunkNow(port.pos().getX() >> 4, port.pos().getZ() >> 4);
                if (chunk != null && chunk.getBlockEntity(port.pos()) instanceof CondenserPort entity) {
                    entity.unbind(this);
                    notifyPipe(server, port, entity.getBlockState().getBlock());
                }
            }
        }
    }

    private static void notifyPipe(ServerLevel server, CondenserLayout.Port port, Block block) {
        BlockPos adjacent = port.pos().relative(port.side());
        if (server.getChunkSource().getChunkNow(adjacent.getX() >> 4, adjacent.getZ() >> 4) != null) {
            server.neighborChanged(adjacent, block, port.pos());
        }
    }

    void partChanged(BlockPos pos) {
        if (Math.abs(pos.getX() - worldPosition.getX()) < CondenserLayout.MAX_LENGTH
                && Math.abs(pos.getY() - worldPosition.getY()) < CondenserLayout.MAX_LENGTH
                && Math.abs(pos.getZ() - worldPosition.getZ()) < CondenserLayout.MAX_LENGTH) {
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

    @Override
    public void onChunkUnloaded() {
        disassemble();
        dirty = true;
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        disassemble();
        super.setRemoved();
    }

    void toggle() {
        enabled = !enabled;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("enabled", enabled);
        tag.put("coolant", coolant.writeToNBT(new CompoundTag()));
        ListTag saved = new ListTag();
        circuits.forEach((pos, circuit) -> {
            if (!circuit.input.isEmpty() || !circuit.output.isEmpty() || circuit.credit > 0D) {
                CompoundTag entry = new CompoundTag();
                entry.putLong("anchor", pos.asLong());
                entry.put("input", circuit.input.writeToNBT(new CompoundTag()));
                entry.put("output", circuit.output.writeToNBT(new CompoundTag()));
                if (circuit.recipe != null) {
                    entry.putString("recipe", circuit.recipe.toString());
                    entry.putDouble("credit", circuit.credit);
                }
                saved.add(entry);
            }
        });
        tag.put("circuits", saved);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        disassemble();
        dirty = true;
        enabled = tag.getBoolean("enabled");
        coolant.readFromNBT(tag.getCompound("coolant"));
        circuits.clear();
        for (Tag data : tag.getList("circuits", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) data;
            Circuit circuit = new Circuit();
            circuit.input.readFromNBT(entry.getCompound("input"));
            circuit.output.readFromNBT(entry.getCompound("output"));
            circuit.recipe = ResourceLocation.tryParse(entry.getString("recipe"));
            double credit = entry.getDouble("credit");
            circuit.credit = Double.isFinite(credit) ? Math.max(0D, credit) : 0D;
            circuits.put(BlockPos.of(entry.getLong("anchor")), circuit);
        }
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public CondenserMenu createMenu(int id, Inventory inventory, Player player) {
        return new CondenserMenu(id, this, ContainerLevelAccess.create(player.level(), worldPosition), new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case CondenserMenu.ENABLED -> enabled ? 1 : 0;
                    case CondenserMenu.NETWORKS -> layout == null ? 0 : layout.networks().size();
                    case CondenserMenu.ACTIVE_NETWORKS -> activeCircuits;
                    case CondenserMenu.ACTIVE_TUBES -> activeTubes;
                    case CondenserMenu.COOLANT -> (int) (1000L * Math.min(coolant.getFluidAmount(), coolant.getCapacity())
                            / coolant.getCapacity());
                    case CondenserMenu.RATE_LOW -> (int) Math.min(Integer.MAX_VALUE, heatRate) & 0xFFFF;
                    case CondenserMenu.RATE_HIGH -> (int) Math.min(Integer.MAX_VALUE, heatRate) >>> 16;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return CondenserMenu.DATA_COUNT;
            }
        });
    }

    private final class Circuit {
        final Tank input = new Tank(1, stack -> recipeFor(stack) != null);
        final Tank output = new Tank(1, stack -> true);
        ResourceLocation recipe;
        double credit;
    }

    private final class Tank extends FluidTank {
        Tank(int capacity, Predicate<FluidStack> validator) {
            super(capacity, validator);
        }

        @Override
        public int fill(FluidStack stack, FluidAction action) {
            return getSpace() > 0 ? super.fill(stack, action) : 0;
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    }
}
