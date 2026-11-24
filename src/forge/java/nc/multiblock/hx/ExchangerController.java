package nc.multiblock.hx;

import nc.multiblock.Cuboid;
import nc.multiblock.computer.ComputerPort;
import nc.multiblock.computer.ComputerValues;
import nc.init.HeatExchangerConfig;
import nc.init.NCHeatExchangers;
import nc.recipe.CondenserRecipe;
import nc.recipe.HeatExchangerRecipe;
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
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class ExchangerController extends BlockEntity implements MenuProvider, ComputerPort.Controller {
    private static final int SHELL_CAPACITY_PER_BLOCK = 4000;
    private final boolean condenser;
    private final Circuit shell;
    private final Map<BlockPos, Circuit> circuits = new LinkedHashMap<>();
    private final Map<BlockPos, Circuit> legacyCircuits = new LinkedHashMap<>();
    private final ComputerPort.Links computerPorts = new ComputerPort.Links(this);
    private ExchangerLayout layout;
    private Component validation = Component.translatable("message.nuclearcraft.exchanger.checking");
    private boolean dirty = true;
    private boolean enabled;
    private boolean computerActivated;
    private int activeCircuits;
    private int activeTubes;
    private double heatRate;
    private double shellSpeed;
    private double temperatureDifference;
    private int shellInputRate;

    public ExchangerController(BlockPos pos, BlockState state) {
        super((state.is(NCHeatExchangers.CONDENSER.get())
                ? NCHeatExchangers.CONDENSER_ENTITY : NCHeatExchangers.CONTROLLER_ENTITY).get(), pos, state);
        condenser = state.is(NCHeatExchangers.CONDENSER.get());
        shell = new Circuit(stack -> condenser ? !stack.isEmpty() && temperature(stack) <= 300 : exchangeRecipe(stack) != null);
    }

    @Override
    public Cuboid computerBounds() {
        return assembled() ? layout.bounds() : null;
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
        heatRate = shellSpeed = temperatureDifference = 0D;
        shellInputRate = 0;
        boolean on = computerOn();
        if (layout != null) {
            if (on && !shell.input.isEmpty()) {
                int before = shell.input.getFluidAmount();
                if (condenser) {
                    int shellTemperature = temperature(shell.input.getFluid());
                    double shellFraction = Math.min(1D, (double) shell.input.getFluidAmount() / shell.input.getCapacity());
                    for (ExchangerLayout.Network network : layout.networks()) {
                        condense(network, circuits.get(network.tubeAnchor()), shellTemperature, shellFraction);
                    }
                    if (activeTubes > 0) {
                        temperatureDifference /= activeTubes;
                    }
                } else {
                    HeatExchange.Activity activity = HeatExchange.tick(layout.networks(), shell, circuits, this::exchangeRecipe);
                    activeCircuits = activity.circuits();
                    activeTubes = activity.tubes();
                    heatRate = activity.heatRate();
                    shellSpeed = activity.shellSpeed();
                    temperatureDifference = activity.temperatureDifference();
                }
                shellInputRate = before - shell.input.getFluidAmount();
            }
            pushOutputs(server);
        }

        BlockState state = getBlockState();
        if (state.getValue(ExchangerWallBlock.ACTIVE) != on) {
            server.setBlock(worldPosition, state.setValue(ExchangerWallBlock.ACTIVE, on), Block.UPDATE_CLIENTS);
        }
    }

    private void condense(ExchangerLayout.Network network, Circuit circuit, int shellTemperature, double shellFraction) {
        CondenserRecipe recipe = condenserRecipe(circuit.input.getFluid());
        if (recipe == null || recipe.outputTemperature() < shellTemperature) {
            return;
        }
        FluidStack product = recipe.output();
        int capacity = circuit.capacity(recipe.input().amount(), product);
        if (capacity <= 0) {
            return;
        }
        double mean = HeatExchangerConfig.meanDifference(recipe.inputTemperature() - shellTemperature,
                recipe.outputTemperature() - shellTemperature);
        double rate = mean * recipe.flowMultiplier(network.tubeFlow()) * network.conductivity() * shellFraction;
        if (rate > 0D) {
            activeCircuits++;
            activeTubes += network.usefulTubes();
            heatRate += rate;
            shellSpeed += rate / recipe.cooling();
            temperatureDifference += mean * network.usefulTubes();
            circuit.process(recipe.id(), recipe.input().amount(), product, recipe.cooling(), capacity, rate);
        }
    }

    private HeatExchangerRecipe exchangeRecipe(FluidStack stack) {
        if (level != null && !stack.isEmpty()) {
            for (HeatExchangerRecipe recipe : level.getRecipeManager().getAllRecipesFor(NCHeatExchangers.HEAT_EXCHANGER_RECIPES.get())) {
                if (recipe.input().accepts(stack)) {
                    return recipe;
                }
            }
        }
        return null;
    }

    private CondenserRecipe condenserRecipe(FluidStack stack) {
        if (level != null && !stack.isEmpty()) {
            for (CondenserRecipe recipe : level.getRecipeManager().getAllRecipesFor(NCHeatExchangers.RECIPES.get())) {
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
        for (ExchangerLayout.Port port : layout.ports()) {
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
        ExchangerLayout.Validation result = ExchangerLayout.find(server, worldPosition, condenser);
        if (result.layout() == null) {
            validation = Component.translatable("message.nuclearcraft.exchanger." + result.error(),
                    result.position().getX(), result.position().getY(), result.position().getZ());
            return;
        }
        layout = result.layout();
        computerPorts.bind(server, layout.computers());
        var bounds = layout.bounds();
        BlockState state = getBlockState();
        if (state.getValue(ExchangerWallBlock.FACING) != bounds.outward(worldPosition)) {
            server.setBlock(worldPosition, state.setValue(ExchangerWallBlock.FACING, bounds.outward(worldPosition)),
                    Block.UPDATE_CLIENTS);
        }
        long volume = (long) bounds.xLength() * bounds.yLength() * bounds.zLength();
        shell.input.setCapacity(boundedCapacity(SHELL_CAPACITY_PER_BLOCK, volume));
        if (!condenser) {
            shell.output.setCapacity(boundedCapacity(16000, volume));
        }
        for (ExchangerLayout.Network network : layout.networks()) {
            Circuit circuit = circuits.get(network.tubeAnchor());
            if (circuit == null) {
                circuit = recoverCircuit(network);
                circuits.put(network.tubeAnchor(), circuit);
                setChanged();
            }
            if (!network.inlet().equals(circuit.inlet)) {
                circuit.inlet = network.inlet();
                setChanged();
            }
            circuit.input.setCapacity(boundedCapacity(condenser ? 32000 : 4000, network.tubeCount() + 2L));
            circuit.output.setCapacity(boundedCapacity(condenser ? 8000 : 16000, network.tubeCount() + 2L));
        }
        for (ExchangerLayout.Port port : layout.ports()) {
            if (server.getBlockEntity(port.pos()) instanceof ExchangerPort entity) {
                BlockState portState = entity.getBlockState();
                if (portState.getValue(ExchangerWallBlock.FACING) != port.side()) {
                    server.setBlock(port.pos(), portState.setValue(ExchangerWallBlock.FACING, port.side()), Block.UPDATE_CLIENTS);
                }
                entity.bind(this, port, tank(port));
            }
        }
        for (ExchangerLayout.Port port : layout.ports()) {
            notifyPipe(server, port, server.getBlockState(port.pos()).getBlock());
        }
        validation = Component.empty();
    }

    private Circuit recoverCircuit(ExchangerLayout.Network network) {
        Circuit legacy = legacyCircuits.remove(network.inlet());
        if (legacy != null) {
            return legacy;
        }
        for (BlockPos tube : network.tubes()) {
            Circuit existing = circuits.remove(tube);
            if (existing != null) {
                return existing;
            }
        }
        return tubeCircuit();
    }

    private static int boundedCapacity(int density, long volume) {
        return (int) Math.min(Integer.MAX_VALUE, density * volume);
    }

    private Circuit tubeCircuit() {
        return new Circuit(stack -> condenser ? condenserRecipe(stack) != null : exchangeRecipe(stack) != null);
    }

    private Tank tank(ExchangerLayout.Port port) {
        Circuit circuit = port.circuit() == null ? shell : circuits.get(port.circuit());
        return port.outlet() && !(port.circuit() == null && condenser) ? circuit.output : circuit.input;
    }

    private void disassemble() {
        computerPorts.unbind();
        ExchangerLayout old = layout;
        layout = null;
        if (old != null && level instanceof ServerLevel server) {
            for (ExchangerLayout.Port port : old.ports()) {
                var chunk = server.getChunkSource().getChunkNow(port.pos().getX() >> 4, port.pos().getZ() >> 4);
                if (chunk != null && chunk.getBlockEntity(port.pos()) instanceof ExchangerPort entity) {
                    entity.unbind(this);
                    notifyPipe(server, port, entity.getBlockState().getBlock());
                }
            }
        }
    }

    private static void notifyPipe(ServerLevel server, ExchangerLayout.Port port, Block block) {
        BlockPos adjacent = port.pos().relative(port.side());
        if (server.getChunkSource().getChunkNow(adjacent.getX() >> 4, adjacent.getZ() >> 4) != null) {
            server.neighborChanged(adjacent, block, port.pos());
        }
    }

    void partChanged(BlockPos pos) {
        if (Math.abs(pos.getX() - worldPosition.getX()) < ExchangerLayout.maxLength()
                && Math.abs(pos.getY() - worldPosition.getY()) < ExchangerLayout.maxLength()
                && Math.abs(pos.getZ() - worldPosition.getZ()) < ExchangerLayout.maxLength()) {
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

    boolean computerOn() {
        return assembled() && (enabled || computerActivated || level.hasNeighborSignal(worldPosition)
                || layout.redstone().stream().anyMatch(level::hasNeighborSignal));
    }

    void computerActivate(boolean activated) {
        if (computerActivated != activated) {
            computerActivated = activated;
            setChanged();
        }
    }

    int computerActiveCircuits() { return activeCircuits; }
    int computerActiveTubes() { return activeTubes; }
    double computerShellSpeed() { return shellSpeed; }
    int computerShellInputRate() { return shellInputRate; }
    double computerHeatRate() { return heatRate; }
    double computerTemperatureDifference() { return temperatureDifference; }
    int computerNetworkCount() { return layout.networks().size(); }
    int computerTubeCount() { return layout.tubes().size(); }

    List<List<Object>> computerNetworks() {
        return layout.networks().stream().map(network -> {
            Circuit circuit = circuits.get(network.tubeAnchor());
            return List.<Object>of(network.usefulTubes(), ComputerValues.vector(network.tubeFlow()),
                    ComputerValues.vector(network.shellFlow()), network.flowCosine(), network.retainedConductivity(),
                    network.conductivity(),
                    List.of(ComputerValues.fluid(circuit.input.getFluid()), ComputerValues.fluid(circuit.output.getFluid())));
        }).toList();
    }

    List<List<Object>> computerTubes() {
        return layout.tubes().stream().map(tube -> {
            BlockState state = level.getBlockState(tube.pos());
            int material = ExchangerLayout.tubeMaterial(state);
            List<String> settings = Arrays.stream(Direction.values())
                    .map(side -> ExchangerTubeBlock.setting(state, side).getSerializedName()).toList();
            return List.<Object>of(ComputerValues.position(tube.pos()), HeatExchangerConfig.conductivity(material),
                    HeatExchangerConfig.retention(material), settings,
                    ComputerValues.vector(tube.tubeFlow()), ComputerValues.vector(tube.shellFlow()));
        }).toList();
    }

    void computerClear() {
        shell.clear();
        circuits.values().forEach(Circuit::clear);
        legacyCircuits.clear();
        setChanged();
    }

    void toggle() {
        enabled = !enabled;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("computerActivated", computerActivated);
        if (condenser) {
            tag.put("coolant", shell.input.writeToNBT(new CompoundTag()));
        } else {
            tag.put("shell", shell.save());
        }
        ListTag saved = new ListTag();
        circuits.forEach((pos, circuit) -> saveCircuit(saved, circuit.inlet, pos, circuit));
        legacyCircuits.forEach((pos, circuit) -> saveCircuit(saved, pos, null, circuit));
        tag.put("circuits", saved);
    }

    private static void saveCircuit(ListTag saved, BlockPos inlet, @Nullable BlockPos tubeAnchor, Circuit circuit) {
        if (!circuit.input.isEmpty() || !circuit.output.isEmpty() || circuit.credit > 0D) {
            CompoundTag entry = circuit.save();
            entry.putLong("anchor", inlet.asLong());
            if (tubeAnchor != null) {
                entry.putLong("tube_anchor", tubeAnchor.asLong());
            }
            saved.add(entry);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        disassemble();
        dirty = true;
        enabled = tag.getBoolean("enabled");
        computerActivated = tag.getBoolean("computerActivated");
        if (condenser) {
            shell.input.readFromNBT(tag.getCompound("coolant"));
        } else {
            shell.load(tag.getCompound("shell"));
        }
        circuits.clear();
        legacyCircuits.clear();
        for (Tag data : tag.getList("circuits", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) data;
            Circuit circuit = tubeCircuit();
            circuit.load(entry);
            circuit.inlet = BlockPos.of(entry.getLong("anchor"));
            if (entry.contains("tube_anchor", Tag.TAG_LONG)) {
                circuits.put(BlockPos.of(entry.getLong("tube_anchor")), circuit);
            } else {
                legacyCircuits.put(circuit.inlet, circuit);
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public ExchangerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ExchangerMenu(id, condenser, this, ContainerLevelAccess.create(player.level(), worldPosition), new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case ExchangerMenu.ENABLED -> enabled ? 1 : 0;
                    case ExchangerMenu.NETWORKS -> layout == null ? 0 : layout.networks().size();
                    case ExchangerMenu.ACTIVE_NETWORKS -> activeCircuits;
                    case ExchangerMenu.ACTIVE_TUBES -> activeTubes;
                    case ExchangerMenu.COOLANT -> shell.input.fillLevel();
                    case ExchangerMenu.SHELL_OUTPUT -> condenser ? 0 : shell.output.fillLevel();
                    case ExchangerMenu.RATE_LOW -> (int) Math.min(Integer.MAX_VALUE, heatRate) & 0xFFFF;
                    case ExchangerMenu.RATE_HIGH -> (int) Math.min(Integer.MAX_VALUE, heatRate) >>> 16;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return ExchangerMenu.DATA_COUNT;
            }
        });
    }

    final class Circuit {
        final Tank input;
        final Tank output = new Tank(1, stack -> true);
        private ResourceLocation recipe;
        private BlockPos inlet;
        private double credit;

        Circuit(Predicate<FluidStack> accepts) {
            input = new Tank(1, accepts);
        }

        int capacity(int inputAmount, FluidStack product) {
            if (!output.isEmpty() && !output.getFluid().isFluidEqual(product)) {
                return 0;
            }
            return Math.max(0, Math.min(input.getFluidAmount() / inputAmount, output.getSpace() / product.getAmount()));
        }

        double credit(ResourceLocation id, double required) {
            return id.equals(recipe) ? Math.min(credit, Math.nextDown(required)) : 0D;
        }

        void process(ResourceLocation id, int inputAmount, FluidStack product, double required, int capacity, double rate) {
            double heat = Math.min(credit(id, required) + rate, required * capacity);
            int batches = Math.min(capacity, (int) (heat / required));
            recipe = id;
            credit = Math.max(0D, heat - batches * required);
            if (batches > 0) {
                FluidStack produced = product.copy();
                produced.setAmount(produced.getAmount() * batches);
                output.fill(produced, FluidAction.EXECUTE);
                input.drain(batches * inputAmount, FluidAction.EXECUTE);
            }
            setChanged();
        }

        void clear() {
            input.setFluid(FluidStack.EMPTY);
            output.setFluid(FluidStack.EMPTY);
            recipe = null;
            credit = 0D;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put("input", input.writeToNBT(new CompoundTag()));
            tag.put("output", output.writeToNBT(new CompoundTag()));
            if (recipe != null) {
                tag.putString("recipe", recipe.toString());
                tag.putDouble("credit", credit);
            }
            return tag;
        }

        void load(CompoundTag tag) {
            input.readFromNBT(tag.getCompound("input"));
            output.readFromNBT(tag.getCompound("output"));
            recipe = ResourceLocation.tryParse(tag.getString("recipe"));
            double saved = tag.getDouble("credit");
            credit = Double.isFinite(saved) ? Math.max(0D, saved) : 0D;
        }
    }

    final class Tank extends FluidTank {
        Tank(int capacity, Predicate<FluidStack> validator) {
            super(capacity, validator);
        }

        int fillLevel() {
            return (int) (1000L * Math.min(getFluidAmount(), getCapacity()) / getCapacity());
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
