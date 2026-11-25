package nc.multiblock.machine;

import nc.init.NCMachines;
import nc.multiblock.AssemblyDiagnostic;
import nc.multiblock.Cuboid;
import nc.multiblock.machine.MachinePartBlock.Part;
import nc.multiblock.machine.MachinePartBlock.TrayPosition;
import nc.recipe.DistillerRecipe;
import nc.recipe.FluidBatch;
import nc.recipe.FluidProduct;
import nc.recipe.MachineMaterialRecipe;
import nc.recipe.MachineMaterialRecipe.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

public final class Distiller extends MachineController {
    private final List<FluidTank> inputs = makeTanks(DistillerRecipe.INPUT_COUNT, false);
    private final List<FluidTank> outputs = makeTanks(DistillerRecipe.OUTPUT_COUNT, true);
    private List<BlockPos> reboilers = List.of();
    private List<BlockPos> distributors = List.of();
    private List<Block> layers = List.of();
    private List<Integer> trayLevels = List.of();
    private List<Integer> positionedTrays = List.of();
    private @Nullable RecipeManager sieveRecipeManager;
    private int distillerConfigRevision;
    private int area;
    private int trays;
    private int refluxUnits;
    private int liquidDistributors;
    private double baseSpeed;
    private double basePower;
    private double progress;
    private double batchStart;
    private double duration;
    private double refluxBonus;
    private double reboilingBonus;
    private double distributionBonus;
    private long energyPerTick;
    private boolean processing;
    private @Nullable ResourceLocation activeRecipe;
    private @Nullable RenderVolume renderVolume;
    private FluidStack lastRenderFirst = FluidStack.EMPTY;
    private FluidStack lastRenderSecond = FluidStack.EMPTY;
    private float soundVolume;
    private float lastSoundVolume;

    public record RenderVolume(BlockPos origin, int width, int height, int depth, int capacity, List<Integer> trays,
                               List<BlockPos> distributors, FluidStack first, FluidStack second, float soundVolume) {}

    public Distiller(BlockPos pos, BlockState state) {
        super(NCMachines.DISTILLER.get(), pos, state, Part.DISTILLER);
    }

    private List<FluidTank> makeTanks(int count, boolean output) {
        var tanks = new ArrayList<FluidTank>(count);
        for (int slot = 0; slot < count; slot++) {
            int selected = slot;
            tanks.add(new FluidTank(Integer.MAX_VALUE, stack -> output || accepts(selected, stack)) {
                @Override protected void onContentsChanged() { setChanged(); }
            });
        }
        return List.copyOf(tanks);
    }

    private boolean accepts(int slot, FluidStack stack) {
        var other = inputs.get(1 - slot).getFluid();
        boolean checkPair = NCMachines.distillerSmartInput() && !other.isEmpty()
                && !stack.isFluidEqual(inputs.get(slot).getFluid());
        return level != null && level.getRecipeManager().getAllRecipesFor(NCMachines.DISTILLER_RECIPES.get())
                .stream().anyMatch(recipe -> checkPair ? recipe.acceptsPair(stack, other) : recipe.accepts(stack));
    }

    @Override int tankCount(boolean output) { return (output ? outputs : inputs).size(); }
    @Override boolean indexedProcessPorts() { return true; }
    @Override FluidTank tankFor(boolean output, int index) { return (output ? outputs : inputs).get(index); }

    @Override long energyCapacity() {
        return bounds == null ? 0 : (long) bounds.xLength() * bounds.yLength() * bounds.zLength()
                * NCMachines.distillerTime() * NCMachines.distillerPower();
    }

    @Override @Nullable String wallError(Part part, Direction outward) {
        return switch (part) {
            case RESERVOIR -> "wall";
            case REBOILER -> outward == Direction.DOWN ? null : "reboiler";
            case REFLUX -> outward == Direction.UP ? null : "reflux";
            case LIQUID_DISTRIBUTOR -> outward == Direction.UP ? null : "distributor";
            default -> super.wallError(part, outward);
        };
    }

    @Override @Nullable AssemblyDiagnostic validateLayout(ServerLevel server, Cuboid found) {
        RecipeManager recipes = server.getRecipeManager();
        int configRevision = NCMachines.distillerConfigRevision();
        var sieves = recipes.getAllRecipesFor(NCMachines.materialRecipes(Kind.SIEVE)).stream()
                .sorted(Comparator.comparing(MachineMaterialRecipe::id)).toList();
        var low = found.min().offset(1, 1, 1);
        var high = found.max().offset(-1, -1, -1);
        var plane = new ArrayList<Block>(found.yLength() - 2);
        var installedReboilers = new ArrayList<BlockPos>();
        var installedDistributors = new ArrayList<BlockPos>();
        var installedTrays = new ArrayList<Integer>();
        int reflux = 0;
        int packedCount = 0;
        double trayEfficiency = 0;
        double packedEfficiency = 0;

        for (int y = low.getY(); y <= high.getY(); y++) {
            BlockPos planeOrigin = new BlockPos(low.getX(), y, low.getZ());
            BlockState sample = server.getBlockState(planeOrigin);
            Block block = planeBlock(sample);
            var mismatch = nonUniformBlock(server, low, high, y, block);
            if (mismatch != null) return problem("symmetry", mismatch);
            plane.add(block);
            Part part = MachinePartBlock.part(sample);
            if (part == Part.SIEVE_TRAY) {
                BlockPos packing = planeOrigin.above();
                Block upper = server.getBlockState(packing).getBlock();
                var sieve = MachineMaterialRecipe.firstMatch(sieves, upper);
                if (sieve == null) return problem("sieve", packing);
                trayEfficiency += sieve.efficiency();
                installedTrays.add(y - low.getY());
            } else {
                if (part != null && !part.sieve()) return problem("interior", planeOrigin);
                boolean placedOnTray = y > low.getY() && plane.get(plane.size() - 2) == NCMachines.block(Part.SIEVE_TRAY);
                var sieve = placedOnTray ? null : MachineMaterialRecipe.firstMatch(sieves, block);
                if (sieve != null) {
                    packedEfficiency += sieve.efficiency();
                    packedCount++;
                }
            }
        }

        for (int x = low.getX(); x <= high.getX(); x++) {
            for (int z = low.getZ(); z <= high.getZ(); z++) {
                var top = new BlockPos(x, found.max().getY(), z);
                var bottom = new BlockPos(x, found.min().getY(), z);
                Part roof = MachinePartBlock.part(server.getBlockState(top));
                Part floor = MachinePartBlock.part(server.getBlockState(bottom));
                if (roof == Part.REFLUX) reflux++;
                if (roof == Part.LIQUID_DISTRIBUTOR) installedDistributors.add(top.subtract(low));
                if (floor == Part.REBOILER) installedReboilers.add(bottom);
            }
        }
        area = (found.xLength() - 2) * (found.zLength() - 2);
        trays = installedTrays.size();
        refluxUnits = reflux;
        liquidDistributors = installedDistributors.size();
        layers = List.copyOf(plane);
        trayLevels = List.copyOf(installedTrays);
        distributors = List.copyOf(installedDistributors);
        reboilers = List.copyOf(installedReboilers);
        double efficiency = trays == 0 ? packedEfficiency : packedCount == 0 ? trayEfficiency
                : (packedCount * trayEfficiency + packedEfficiency) / (1D + packedCount);
        baseSpeed = area * efficiency;
        basePower = (double) area * (trays + packedCount) + reboilers.size();
        sieveRecipeManager = recipes;
        distillerConfigRevision = configRevision;
        return null;
    }

    private static Block planeBlock(BlockState state) {
        return state.isAir() ? Blocks.AIR : state.getBlock();
    }

    private static @Nullable BlockPos nonUniformBlock(ServerLevel server, BlockPos low, BlockPos high, int y, Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(low.getX(), y, low.getZ(), high.getX(), y, high.getZ())) {
            if (planeBlock(server.getBlockState(pos)) != block) return pos.immutable();
        }
        return null;
    }

    @Override void processTick(ServerLevel server) {
        if (sieveRecipeManager != server.getRecipeManager() || distillerConfigRevision != NCMachines.distillerConfigRevision()) {
            AssemblyDiagnostic error = validateLayout(server, bounds);
            if (error != null) {
                fail(error);
                return;
            }
        }
        // Commands and other mods can change ordinary interior blocks without notifying a machine part.
        if (server.getGameTime() % 20 == Math.floorMod(worldPosition.asLong(), 20)) {
            var low = bounds.min().offset(1, 1, 1);
            var high = bounds.max().offset(-1, -1, -1);
            for (int layer = 0; layer < layers.size(); layer++) {
                var mismatch = nonUniformBlock(server, low, high, low.getY() + layer, layers.get(layer));
                if (mismatch != null) {
                    fail(problem("symmetry", mismatch));
                    return;
                }
            }
        }
        setActive(distill(server));
    }

    @Override void assemblyChanged() {
        processing = false;
        soundVolume = 0;
        setLit(false);
        setReboilersActive(false);
        if (bounds != null) {
            setTrayPositions(trayLevels, true);
            positionedTrays = trayLevels;
        }
        syncRender();
    }

    @Override void beforeDisconnect() {
        setTrayPositions(positionedTrays, false);
        positionedTrays = List.of();
    }

    private void setTrayPositions(List<Integer> levels, boolean assembled) {
        if (!(level instanceof ServerLevel server)) return;
        BlockPos low = bounds.min().offset(1, 1, 1);
        BlockPos high = bounds.max().offset(-1, -1, -1);
        for (int index = 0; index < levels.size(); index++) {
            int y = low.getY() + levels.get(index);
            TrayPosition position = assembled ? (index % 2 == 0 ? TrayPosition.EVEN : TrayPosition.ODD) : TrayPosition.STATIC;
            for (int x = low.getX(); x <= high.getX(); x++) {
                for (int z = low.getZ(); z <= high.getZ(); z++) {
                    var chunk = server.getChunkSource().getChunkNow(x >> 4, z >> 4);
                    if (chunk == null) continue;
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.hasProperty(MachinePartBlock.TRAY_POSITION) && state.getValue(MachinePartBlock.TRAY_POSITION) != position) {
                        server.setBlock(pos, state.setValue(MachinePartBlock.TRAY_POSITION, position), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private void setActive(boolean active) {
        setLit(active);
        if (!active) soundVolume = 0;
        if (processing == active) return;
        processing = active;
        setReboilersActive(active);
    }

    private void setReboilersActive(boolean active) {
        if (!(level instanceof ServerLevel server)) return;
        for (var pos : reboilers) {
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) continue;
            var state = chunk.getBlockState(pos);
            if (MachinePartBlock.part(state) == Part.REBOILER && state.getValue(BlockStateProperties.LIT) != active) {
                server.setBlock(pos, state.setValue(BlockStateProperties.LIT, active), Block.UPDATE_CLIENTS);
            }
        }
    }

    private boolean distill(ServerLevel server) {
        var first = inputs.get(0).getFluid();
        var second = inputs.get(1).getFluid();
        var recipe = server.getRecipeManager().getAllRecipesFor(NCMachines.DISTILLER_RECIPES.get()).stream()
                .filter(candidate -> candidate.firstInput(first, second, candidate.batchFactor()) >= 0)
                .min(Comparator.comparing(DistillerRecipe::id)).orElse(null);
        if (recipe == null) {
            if (activeRecipe != null) resetProgress(null);
            duration = refluxBonus = reboilingBonus = distributionBonus = 0;
            energyPerTick = 0;
            return false;
        }
        if (!recipe.id().equals(activeRecipe)) resetProgress(recipe.id());
        int factor = recipe.batchFactor();
        int order = recipe.firstInput(first, second, factor);
        int firstAmount = recipe.inputAmount(order, factor);
        int secondAmount = recipe.inputAmount(1 - order, factor);
        int available = Math.min(firstAmount == 0 ? Integer.MAX_VALUE : first.getAmount() / firstAmount,
                secondAmount == 0 ? Integer.MAX_VALUE : second.getAmount() / secondAmount);
        List<FluidStack> maximums = recipe.products().stream().map(product -> FluidBatch.divide(product.stack(), factor)).toList();
        int gases = (isGas(first) ? 1 : 0) + (isGas(second) ? 1 : 0);
        int fluidCount = recipe.inputs().size();
        for (var maximum : maximums) {
            if (maximum.isEmpty()) continue;
            fluidCount++;
            if (isGas(maximum)) gases++;
        }
        refluxBonus = (double) refluxUnits * gases / ((double) area * fluidCount);
        reboilingBonus = (double) reboilers.size() * (fluidCount - gases) / ((double) area * fluidCount);
        distributionBonus = (double) liquidDistributors / ((double) area * (1 + trays));
        double speed = baseSpeed * (1 + refluxBonus) * (1 + reboilingBonus) * (1 + distributionBonus);
        double nominalDuration = NCMachines.distillerTime() * recipe.timeMultiplier();
        double nextDuration = nominalDuration / factor;
        if (progress > 0 && duration != nextDuration) {
            // Earlier Forge saves used the standalone Distiller switch.
            double previousDuration = duration > 0 ? duration
                    : NCMachines.distillerFactorRecipes() ? nextDuration : nominalDuration;
            double ratio = nextDuration / previousDuration;
            progress *= ratio;
            batchStart *= ratio;
            setChanged();
        }
        duration = nextDuration;
        double power = NCMachines.distillerPower() * recipe.powerMultiplier() * basePower;
        energyPerTick = (long) Math.ceil(power);
        int room = outputSpace(maximums);
        if (speed <= 0 || room == 0 || recipe.sieveTrays() > trays || blockedByRedstone(server)) return false;
        if (power() < energyPerTick || progress <= batchStart
                && power() < Math.min(energyCapacity(), (long) Math.ceil(duration / speed * power))) return false;

        setPower(power() - energyPerTick);
        emitRadiation(recipe.radiation(), speed);
        soundVolume = (float) (NCMachines.distillerSoundVolume() * Math.log1p(Math.cbrt(speed)) / 128);
        progress += speed;
        int batches = Math.min(available, (int) (progress / duration));
        if (batches > 0) {
            int finished = 0;
            while (finished < batches && room > 0) {
                int count = Math.min(batches - finished, room);
                produce(recipe.products(), count, factor, server.random);
                finished += count;
                room = outputSpace(maximums);
            }
            inputs.get(0).drain(finished * firstAmount, EXECUTE);
            inputs.get(1).drain(finished * secondAmount, EXECUTE);
            progress = finished == available || room == 0 ? 0 : Math.max(0, progress - finished * duration);
            batchStart = progress;
        }
        setChanged();
        return true;
    }

    private int outputSpace(List<FluidStack> maximums) {
        int room = Integer.MAX_VALUE;
        for (int slot = 0; slot < maximums.size(); slot++) {
            var result = maximums.get(slot);
            if (result.isEmpty()) continue;
            var tank = outputs.get(slot);
            if (!tank.isEmpty() && !tank.getFluid().isFluidEqual(result)) return 0;
            room = Math.min(room, Math.max(0, tank.getCapacity() - tank.getFluidAmount()) / result.getAmount());
        }
        return room;
    }

    private void produce(List<FluidProduct> products, int batches, int factor, RandomSource random) {
        for (int slot = 0; slot < products.size(); slot++) {
            FluidProduct product = products.get(slot);
            if (product.isEmpty()) continue;
            int amount = product.totalAmount(batches, factor, random);
            if (amount > 0) outputs.get(slot).fill(new FluidStack(product.stack(), amount), EXECUTE);
        }
    }

    public static boolean isGas(FluidStack stack) {
        return !stack.isEmpty() && stack.getFluid().getFluidType().getDensity(stack) < 0;
    }

    @Override void afterTransfers(ServerLevel server) {
        if (soundVolume != lastSoundVolume || server.getGameTime() % 5 == 0
                && (!lastRenderFirst.isFluidStackIdentical(inputs.get(0).getFluid())
                || !lastRenderSecond.isFluidStackIdentical(inputs.get(1).getFluid()))) syncRender();
    }

    private void syncRender() {
        lastRenderFirst = inputs.get(0).getFluid().copy();
        lastRenderSecond = inputs.get(1).getFluid().copy();
        lastSoundVolume = soundVolume;
        if (level != null && !isRemoved()) {
            var state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public @Nullable RenderVolume renderVolume() { return renderVolume; }

    @Override public CompoundTag getUpdateTag() {
        var tag = new CompoundTag();
        if (bounds == null) return tag;
        BlockPos origin = bounds.min().offset(1, 1, 1).subtract(worldPosition);
        tag.putIntArray("volume", new int[]{origin.getX(), origin.getY(), origin.getZ(),
                bounds.xLength() - 2, bounds.yLength() - 2, bounds.zLength() - 2, inputs.get(0).getCapacity()});
        tag.putIntArray("trays", trayLevels);
        tag.putLongArray("distributors", distributors.stream().map(BlockPos::asLong).toList());
        tag.put("first", inputs.get(0).getFluid().writeToNBT(new CompoundTag()));
        tag.put("second", inputs.get(1).getFluid().writeToNBT(new CompoundTag()));
        tag.putFloat("sound_volume", soundVolume);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag) {
        int[] volume = tag.getIntArray("volume");
        renderVolume = volume.length != 7 ? null : new RenderVolume(new BlockPos(volume[0], volume[1], volume[2]),
                volume[3], volume[4], volume[5], volume[6], Arrays.stream(tag.getIntArray("trays")).boxed().toList(),
                Arrays.stream(tag.getLongArray("distributors")).mapToObj(BlockPos::of).toList(),
                FluidStack.loadFluidStackFromNBT(tag.getCompound("first")),
                FluidStack.loadFluidStackFromNBT(tag.getCompound("second")), tag.getFloat("sound_volume"));
    }

    private void resetProgress(@Nullable ResourceLocation recipe) {
        activeRecipe = recipe;
        progress = batchStart = 0;
        setChanged();
    }

    @Override
    ProcessStatus computerProcess() {
        return new ProcessStatus(processing(), progress, Math.max(1D, duration), energyPerTick);
    }

    public boolean processing() { return assembled() && processing; }
    int trayCount() { return trays; }
    double progressFraction() { return duration > 0 ? Math.min(1, progress / duration) : 0; }
    long energyPerTick() { return energyPerTick; }
    double refluxBonus() { return refluxBonus; }
    double reboilingBonus() { return reboilingBonus; }
    double distributionBonus() { return distributionBonus; }

    @Override void openMenu(Player player) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, owner) -> new DistillerMenu(id, this, ContainerLevelAccess.create(level, worldPosition)),
                Component.translatable("block.nuclearcraft.distiller_controller")));
    }

    @Override void clear() {
        for (var tank : inputs) tank.setFluid(FluidStack.EMPTY);
        for (var tank : outputs) tank.setFluid(FluidStack.EMPTY);
        resetProgress(null);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        for (int slot = 0; slot < inputs.size(); slot++) tag.put("input_" + slot, inputs.get(slot).writeToNBT(new CompoundTag()));
        for (int slot = 0; slot < outputs.size(); slot++) tag.put("output_" + slot, outputs.get(slot).writeToNBT(new CompoundTag()));
        tag.putDouble("progress", progress);
        tag.putDouble("batch_start", batchStart);
        if (progress > 0 && duration > 0) tag.putDouble("progress_duration", duration);
        if (activeRecipe != null) tag.putString("recipe", activeRecipe.toString());
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        for (int slot = 0; slot < inputs.size(); slot++) inputs.get(slot).readFromNBT(tag.getCompound("input_" + slot));
        for (int slot = 0; slot < outputs.size(); slot++) outputs.get(slot).readFromNBT(tag.getCompound("output_" + slot));
        progress = finiteProgress(tag.getDouble("progress"));
        batchStart = Math.min(progress, finiteProgress(tag.getDouble("batch_start")));
        duration = finiteProgress(tag.getDouble("progress_duration"));
        activeRecipe = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
    }

    private static double finiteProgress(double value) { return Double.isFinite(value) ? Math.max(0, value) : 0; }
}
