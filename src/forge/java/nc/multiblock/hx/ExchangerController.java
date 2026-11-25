package nc.multiblock.hx;

import nc.multiblock.AssemblyDiagnostic;
import nc.multiblock.Cuboid;
import nc.multiblock.MultiblockFrame;
import nc.multiblock.computer.ComputerPort;
import nc.multiblock.computer.ComputerValues;
import nc.init.HeatExchangerConfig;
import nc.init.NCHeatExchangers;
import nc.recipe.CondenserCoolants;
import nc.recipe.CondenserRecipe;
import nc.recipe.FluidBatch;
import nc.recipe.FluidInput;
import nc.recipe.FluidProduct;
import nc.recipe.HeatExchangerRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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

public final class ExchangerController extends BlockEntity implements MenuProvider, ComputerPort.Controller, AssemblyDiagnostic.Controller {
    private static final int SHELL_CAPACITY_PER_BLOCK = 4000;
    private final boolean condenser;
    private final Circuit shell;
    private final Map<BlockPos, Circuit> circuits = new LinkedHashMap<>();
    private final Map<BlockPos, Circuit> legacyCircuits = new LinkedHashMap<>();
    private final ComputerPort.Links computerPorts = new ComputerPort.Links(this);
    private ExchangerLayout layout;
    private AssemblyDiagnostic validation = new AssemblyDiagnostic(
            Component.translatable("message.nuclearcraft.exchanger.checking"), null);
    private boolean dirty = true;
    private boolean enabled;
    private boolean computerActivated;
    private int activeCircuits;
    private int activeTubes;
    private double heatRate;
    private double shellSpeed;
    private double temperatureDifference;
    private int shellInputRate;
    private long tubeInputRate;
    private FluidStack renderedInput = FluidStack.EMPTY;
    private FluidStack renderedOutput = FluidStack.EMPTY;
    private @Nullable RenderVolume renderVolume;

    public record RenderVolume(BlockPos origin, int width, int height, int depth, boolean condenser,
                               FluidStack input, FluidStack output, int inputCapacity, int outputCapacity) {}

    public ExchangerController(BlockPos pos, BlockState state) {
        super((state.is(NCHeatExchangers.CONDENSER.get())
                ? NCHeatExchangers.CONDENSER_ENTITY : NCHeatExchangers.CONTROLLER_ENTITY).get(), pos, state);
        condenser = state.is(NCHeatExchangers.CONDENSER.get());
        shell = new Circuit(stack -> condenser ? CondenserCoolants.find(level, stack) != null : exchangeRecipe(stack) != null);
    }

    @Override
    public Cuboid computerBounds() {
        return assembled() ? layout.bounds() : null;
    }

    boolean assembled() {
        return layout != null && !isRemoved() && level instanceof ServerLevel server && layout.bounds().isLoaded(server);
    }

    @Override
    public @Nullable AssemblyDiagnostic diagnostic(ServerLevel server) {
        if (dirty) {
            refresh(server);
        }
        return assembled() ? null : validation;
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
        tubeInputRate = 0L;
        boolean on = computerOn();
        if (layout != null) {
            if (on && !shell.input.isEmpty()) {
                int before = shell.input.getFluidAmount();
                if (condenser) {
                    condenseShell();
                } else {
                    HeatExchange.Activity activity = HeatExchange.tick(layout.networks(), shell, circuits, this::exchangeRecipe);
                    activeCircuits = activity.circuits();
                    activeTubes = activity.tubes();
                    heatRate = activity.heatRate();
                    shellSpeed = activity.shellSpeed();
                    temperatureDifference = activity.temperatureDifference();
                    tubeInputRate = activity.tubeInputRate();
                }
                shellInputRate = before - shell.input.getFluidAmount();
            }
            pushOutputs(server);
            if (layout != null && layout.glass() && server.getGameTime() % 5 == 0
                    && (!renderedInput.isFluidStackIdentical(shell.input.getFluid())
                    || !renderedOutput.isFluidStackIdentical(shell.output.getFluid()))) {
                syncRender(server);
            }
        }

        BlockState state = getBlockState();
        if (state.getValue(ExchangerWallBlock.ACTIVE) != on) {
            server.setBlock(worldPosition, state.setValue(ExchangerWallBlock.ACTIVE, on), Block.UPDATE_CLIENTS);
        }
    }

    private void condenseShell() {
        var coolant = shell.input.getFluid();
        var recipe = CondenserCoolants.find(level, coolant);
        if (recipe == null || !recipe.input().matches(coolant)) {
            return;
        }
        double shellFraction = Math.min(1D, (double) coolant.getAmount() / shell.input.getCapacity());
        for (ExchangerLayout.Network network : layout.networks()) {
            condense(network, circuits.get(network.tubeAnchor()), recipe.temperature(), shellFraction);
        }
        if (activeTubes > 0) {
            temperatureDifference /= activeTubes;
        }
    }

    private void condense(ExchangerLayout.Network network, Circuit circuit, int shellTemperature, double shellFraction) {
        CondenserRecipe recipe = condenserRecipe(circuit.input.getFluid());
        if (recipe == null || recipe.outputTemperature() < shellTemperature) {
            return;
        }
        var product = recipe.product();
        if (circuit.capacity(recipe.input(), product) == 0) {
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
            tubeInputRate += circuit.process(recipe.id(), recipe.input(), product, recipe.cooling(), rate);
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
            validation = result.diagnostic();
            disassemble();
            return;
        }
        layout = result.layout();
        MultiblockFrame.set(server, layout.frames(), true);
        computerPorts.bind(server, layout.computers());
        for (BlockPos redstone : layout.redstone()) {
            ExchangerPartBlock.Redstone.updateSignal(server.getBlockState(redstone), server, redstone);
        }
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
        validation = new AssemblyDiagnostic(Component.empty(), null);
        if (layout != null && layout.glass()) {
            syncRender(server);
        }
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
            MultiblockFrame.set(server, old.frames(), false);
            if (old.glass()) {
                syncRender(server);
            }
            for (ExchangerLayout.Port port : old.ports()) {
                var chunk = server.getChunkSource().getChunkNow(port.pos().getX() >> 4, port.pos().getZ() >> 4);
                if (chunk != null && chunk.getBlockEntity(port.pos()) instanceof ExchangerPort entity) {
                    entity.unbind(this);
                    notifyPipe(server, port, entity.getBlockState().getBlock());
                }
            }
        }
    }

    private void syncRender(ServerLevel server) {
        renderedInput = shell.input.getFluid().copy();
        renderedOutput = shell.output.getFluid().copy();
        if (!isRemoved() && server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4) != null) {
            BlockState state = getBlockState();
            server.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public @Nullable RenderVolume renderVolume() {
        return renderVolume;
    }

    @Override
    public CompoundTag getUpdateTag() {
        var tag = new CompoundTag();
        if (layout != null && layout.glass()) {
            var bounds = layout.bounds();
            BlockPos origin = bounds.min().offset(1, 1, 1).subtract(worldPosition);
            tag.putIntArray("shell_volume", new int[]{origin.getX(), origin.getY(), origin.getZ(),
                    bounds.xLength() - 2, bounds.yLength() - 2, bounds.zLength() - 2,
                    shell.input.getCapacity(), shell.output.getCapacity()});
            tag.put("shell_input", shell.input.getFluid().writeToNBT(new CompoundTag()));
            tag.put("shell_output", shell.output.getFluid().writeToNBT(new CompoundTag()));
        } else {
            tag.putIntArray("shell_volume", new int[0]);
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        int[] data = tag.getIntArray("shell_volume");
        renderVolume = data.length == 8 ? new RenderVolume(new BlockPos(data[0], data[1], data[2]),
                data[3], data[4], data[5], condenser, FluidStack.loadFluidStackFromNBT(tag.getCompound("shell_input")),
                FluidStack.loadFluidStackFromNBT(tag.getCompound("shell_output")), data[6], data[7]) : null;
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
            var block = (ExchangerTubeBlock) state.getBlock();
            List<String> settings = Arrays.stream(Direction.values())
                    .map(side -> ExchangerTubeBlock.setting(state, side).getSerializedName()).toList();
            return List.<Object>of(ComputerValues.position(tube.pos()), block.conductivity(), block.retention(), settings,
                    ComputerValues.vector(tube.tubeFlow()), ComputerValues.vector(tube.shellFlow()));
        }).toList();
    }

    void computerClear() {
        shell.clear();
        circuits.values().forEach(Circuit::clear);
        legacyCircuits.clear();
        if (level instanceof ServerLevel server && layout != null && layout.glass()) {
            syncRender(server);
        }
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
                int field = index < ExchangerMenu.NETWORKS ? index : index & ~1;
                int value = menuValue(field);
                return index < ExchangerMenu.NETWORKS ? value : value >>> (16 * (index & 1)) & 0xffff;
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return ExchangerMenu.DATA_COUNT;
            }
        });
    }

    private int menuValue(int field) {
        if (layout == null) {
            return 0;
        }
        return switch (field) {
            case ExchangerMenu.LENGTH_X -> layout.bounds().xLength() - 2;
            case ExchangerMenu.LENGTH_Y -> layout.bounds().yLength() - 2;
            case ExchangerMenu.LENGTH_Z -> layout.bounds().zLength() - 2;
            case ExchangerMenu.FLAGS -> (enabled ? ExchangerMenu.MANUAL : 0)
                    | (getBlockState().getValue(ExchangerWallBlock.ACTIVE) ? ExchangerMenu.RUNNING : 0);
            case ExchangerMenu.NETWORKS -> layout.networks().size();
            case ExchangerMenu.ACTIVE_NETWORKS -> activeCircuits;
            case ExchangerMenu.TUBES -> layout.tubes().size();
            case ExchangerMenu.ACTIVE_TUBES -> activeTubes;
            case ExchangerMenu.SHELL_INPUT -> shell.input.getFluidAmount();
            case ExchangerMenu.SHELL_INPUT_CAPACITY -> shell.input.getCapacity();
            case ExchangerMenu.SHELL_OUTPUT -> condenser ? 0 : shell.output.getFluidAmount();
            case ExchangerMenu.SHELL_OUTPUT_CAPACITY -> condenser ? 0 : shell.output.getCapacity();
            case ExchangerMenu.TUBE_RATE -> Float.floatToIntBits(tubeInputRate);
            case ExchangerMenu.SHELL_RATE -> shellInputRate;
            case ExchangerMenu.HEAT_RATE -> Float.floatToIntBits((float) Math.min(Float.MAX_VALUE, heatRate));
            case ExchangerMenu.TEMPERATURE_DIFFERENCE -> Float.floatToIntBits((float) temperatureDifference);
            default -> 0;
        };
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

        int capacity(FluidInput ingredient, FluidProduct product) {
            return fit(ingredient, product, Integer.MAX_VALUE).batches();
        }

        private FluidBatch.Usage fit(FluidInput ingredient, FluidProduct product, int requested) {
            int room = product.batchesIn(output.getFluid(), output.getCapacity());
            return FluidBatch.fit(ingredient, input.getFluid(), input.getFluidAmount(), Math.min(requested, room));
        }

        double credit(ResourceLocation id, double required) {
            return id.equals(recipe) ? Math.min(credit, Math.nextDown(required)) : 0D;
        }

        int process(ResourceLocation id, FluidInput ingredient, FluidProduct product, double required, double rate) {
            double heat = credit(id, required) + rate;
            int consumed = 0;
            while (heat >= required) {
                var usage = fit(ingredient, product, (int) (heat / required));
                if (usage.batches() == 0) {
                    break;
                }
                int amount = product.totalAmount(usage.batches(), 1, level.random);
                if (amount > 0) {
                    var produced = product.stack();
                    produced.setAmount(amount);
                    output.fill(produced, FluidAction.EXECUTE);
                }
                input.drain(usage.consumed(), FluidAction.EXECUTE);
                consumed += usage.consumed();
                heat = Math.max(0D, heat - usage.batches() * required);
            }
            recipe = id;
            credit = heat < required && capacity(ingredient, product) > 0 ? heat : 0D;
            setChanged();
            return consumed;
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
