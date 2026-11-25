package nc.multiblock.machine;

import nc.init.NCMachines;
import nc.multiblock.AssemblyDiagnostic;
import nc.multiblock.Cuboid;
import nc.multiblock.machine.MachinePartBlock.Part;
import nc.recipe.ElectrolyzerElectrolyteRecipe;
import nc.recipe.LargeElectrolyzerRecipe;
import nc.recipe.FluidProduct;
import nc.recipe.ItemProcessorRecipe.Product;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static nc.recipe.LargeElectrolyzerRecipe.FLUID_OUTPUTS;
import static nc.recipe.LargeElectrolyzerRecipe.ITEM_OUTPUTS;
import static nc.recipe.MachineInputs.FLUID_INPUTS;
import static nc.recipe.MachineInputs.ITEM_INPUTS;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

public final class LargeElectrolyzer extends MachineController {
    private final ItemStackHandler inventory = new ItemStackHandler(ITEM_INPUTS + ITEM_OUTPUTS) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot < ITEM_INPUTS && acceptsItem(slot, stack);
        }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final List<FluidTank> inputs = tanks(FLUID_INPUTS, false);
    private final List<FluidTank> outputs = tanks(FLUID_OUTPUTS, true);
    private final FluidTank electrolyte = new FluidTank(Integer.MAX_VALUE, this::acceptsElectrolyte) {
        @Override protected void onContentsChanged() { setChanged(); }
    };
    private @Nullable ElectrolyzerLayout layout;
    private double progress;
    private double batchStart;
    private double duration;
    private double electrolyteEfficiency;
    private double speed;
    private long energyPerTick;
    private boolean processing;
    private @Nullable ResourceLocation activeRecipe;
    private float soundVolume;
    private float lastSoundVolume;
    private FluidStack lastElectrolyte = FluidStack.EMPTY;
    private @Nullable RenderVolume renderVolume;

    public record RenderVolume(BlockPos origin, int width, int height, int depth, int capacity,
                               FluidStack electrolyte, List<BlockPos> terminals, float soundVolume) {}

    public LargeElectrolyzer(BlockPos pos, BlockState state) {
        super(NCMachines.ELECTROLYZER.get(), pos, state, Part.ELECTROLYZER);
    }

    private List<FluidTank> tanks(int count, boolean output) {
        var result = new ArrayList<FluidTank>(count);
        for (int i = 0; i < count; i++) {
            int index = i;
            result.add(new FluidTank(Integer.MAX_VALUE, fluid -> output || acceptsFluid(index, fluid)) {
                @Override protected void onContentsChanged() { setChanged(); }
            });
        }
        return List.copyOf(result);
    }

    @Override boolean indexedProcessPorts() { return true; }
    @Override int tankCount(boolean output) { return (output ? outputs : inputs).size(); }
    @Override FluidTank tankFor(boolean output, int index) { return (output ? outputs : inputs).get(index); }
    @Override int itemCount(boolean output) { return output ? ITEM_OUTPUTS : ITEM_INPUTS; }
    @Override ItemStackHandler processItems() { return inventory; }
    @Override FluidTank pressureTank() { return electrolyte; }

    @Override long energyCapacity() {
        return bounds == null ? 0 : (long) bounds.xLength() * bounds.yLength() * bounds.zLength()
                * NCMachines.electrolyzerTime() * NCMachines.electrolyzerPower();
    }

    private List<LargeElectrolyzerRecipe> recipes() {
        return level == null ? List.of() : level.getRecipeManager().getAllRecipesFor(NCMachines.ELECTROLYZER_RECIPES.get());
    }

    private boolean acceptsFluid(int slot, FluidStack stack) {
        if (!NCMachines.electrolyzerSmartInput() || stack.isFluidEqual(inputs.get(slot).getFluid())) {
            return recipes().stream().anyMatch(recipe -> recipe.accepts(stack));
        }
        var first = slot == 0 ? stack : inputs.get(0).getFluid();
        var second = slot == 1 ? stack : inputs.get(1).getFluid();
        return recipes().stream().anyMatch(recipe -> recipe.canMatch(inventory.getStackInSlot(0), inventory.getStackInSlot(1), first, second));
    }

    private boolean acceptsItem(int slot, ItemStack stack) {
        if (!NCMachines.electrolyzerSmartInput() || ItemStack.isSameItemSameTags(stack, inventory.getStackInSlot(slot))) {
            return recipes().stream().anyMatch(recipe -> recipe.accepts(stack));
        }
        var first = slot == 0 ? stack : inventory.getStackInSlot(0);
        var second = slot == 1 ? stack : inventory.getStackInSlot(1);
        return recipes().stream().anyMatch(recipe -> recipe.canMatch(first, second, inputs.get(0).getFluid(), inputs.get(1).getFluid()));
    }

    private boolean acceptsElectrolyte(FluidStack stack) {
        return level != null && level.getRecipeManager().getAllRecipesFor(NCMachines.ELECTROLYTES.get()).stream()
                .anyMatch(recipe -> recipe.fluid().accepts(stack));
    }

    private double efficiency(ServerLevel server, String group) {
        var stack = electrolyte.getFluid();
        if (stack.isEmpty()) return 0;
        return server.getRecipeManager().getAllRecipesFor(NCMachines.ELECTROLYTES.get()).stream()
                .filter(recipe -> recipe.group().equals(group) && recipe.fluid().matches(stack))
                .min(Comparator.comparing(ElectrolyzerElectrolyteRecipe::id))
                .map(ElectrolyzerElectrolyteRecipe::efficiency).orElse(0D);
    }

    @Override @Nullable String wallError(Part part, Direction outward) {
        if (part.electrodeTerminal()) return outward.getAxis() == Direction.Axis.Y ? null : "terminals";
        return super.wallError(part, outward);
    }

    @Override @Nullable AssemblyDiagnostic validateLayout(ServerLevel server, Cuboid candidate) {
        var result = ElectrolyzerLayout.validate(server, candidate);
        layout = result.layout();
        return result.error();
    }

    @Override void assemblyChanged() {
        processing = false;
        soundVolume = 0;
        setLit(false);
        if (assembled() && layout != null && level instanceof ServerLevel server) {
            for (BlockPos pos : layout.terminals()) {
                var state = server.getBlockState(pos);
                server.setBlock(pos, state.setValue(BlockStateProperties.FACING, bounds.outward(pos)), Block.UPDATE_CLIENTS);
            }
        }
        syncRender();
    }

    @Override void processTick(ServerLevel server) {
        if (layout == null) return;
        // Electrode blocks may be supplied by vanilla or other mods and bypass Large Machine part events.
        if (server.getGameTime() % 20 == Math.floorMod(worldPosition.asLong(), 20) && !layout.matches(server)) {
            fail(problem("interior", worldPosition));
            return;
        }
        processing = electrolyze(server, layout);
        if (!processing) soundVolume = 0;
        setLit(processing);
    }

    private boolean electrolyze(ServerLevel server, ElectrolyzerLayout geometry) {
        var firstItem = inventory.getStackInSlot(0);
        var secondItem = inventory.getStackInSlot(1);
        var firstFluid = inputs.get(0).getFluid();
        var secondFluid = inputs.get(1).getFluid();
        var recipe = recipes().stream().filter(candidate -> candidate.match(firstItem, secondItem, firstFluid, secondFluid) != null)
                .min(Comparator.comparing(LargeElectrolyzerRecipe::id)).orElse(null);
        if (recipe == null) {
            if (activeRecipe != null) reset(null);
            duration = speed = electrolyteEfficiency = 0;
            energyPerTick = 0;
            return false;
        }

        if (!recipe.id().equals(activeRecipe)) reset(recipe.id());
        var match = recipe.match(firstItem, secondItem, firstFluid, secondFluid);
        electrolyteEfficiency = efficiency(server, recipe.electrolyte());
        double fraction = Math.min(1D, (double) electrolyte.getFluidAmount() / electrolyte.getCapacity());
        duration = NCMachines.electrolyzerTime() * recipe.time();
        speed = geometry.speed() * electrolyteEfficiency * fraction;
        double power = NCMachines.electrolyzerPower() * recipe.power() * geometry.power() * fraction;
        energyPerTick = (long) Math.ceil(power);
        if (speed <= 0 || blockedByRedstone(server) || power() < energyPerTick
                || progress <= batchStart && power() < Math.min(energyCapacity(), (long) Math.ceil(duration / speed * power))) return false;

        int available = match.available(firstItem, secondItem, firstFluid, secondFluid);
        int room = outputBatches(recipe.itemProducts(), recipe.fluidProducts());
        if (room == 0) return false;

        setPower(power() - energyPerTick);
        emitRadiation(recipe.radiation(), speed);
        int terminals = geometry.terminals().size();
        soundVolume = (float) (NCMachines.electrolyzerSoundVolume() * Math.log1p(
                Math.sqrt(speed) / (4D * Math.sqrt(bounds.yLength() - 1D) * terminals * terminals)));
        progress += speed;
        int batches = Math.min(available, (int) (progress / duration));
        if (batches > 0) {
            int finished = 0;
            while (finished < batches && room > 0) {
                int count = Math.min(batches - finished, room);
                produce(recipe.itemProducts(), recipe.fluidProducts(), count, server.random);
                finished += count;
                room = outputBatches(recipe.itemProducts(), recipe.fluidProducts());
            }
            inventory.extractItem(0, finished * match.firstItems(), false);
            inventory.extractItem(1, finished * match.secondItems(), false);
            inputs.get(0).drain(finished * match.firstFluid(), EXECUTE);
            inputs.get(1).drain(finished * match.secondFluid(), EXECUTE);
            progress = finished == available || room == 0 ? 0 : Math.max(0, progress - finished * duration);
            batchStart = progress;
        }
        setChanged();
        return true;
    }

    private int outputBatches(List<Product> itemResults, List<FluidProduct> fluidResults) {
        int batches = Integer.MAX_VALUE;
        for (int slot = 0; slot < itemResults.size(); slot++) {
            var stored = inventory.getStackInSlot(ITEM_INPUTS + slot);
            batches = Math.min(batches, itemResults.get(slot).batchesIn(stored));
        }
        for (int slot = 0; slot < fluidResults.size(); slot++) {
            var result = fluidResults.get(slot).stack();
            if (result.isEmpty()) continue;
            var tank = outputs.get(slot);
            if (!tank.isEmpty() && !tank.getFluid().isFluidEqual(result)) return 0;
            batches = Math.min(batches, Math.max(0, tank.getCapacity() - tank.getFluidAmount()) / result.getAmount());
        }
        return batches;
    }

    private void produce(List<Product> itemResults, List<FluidProduct> fluidResults, int batches, RandomSource random) {
        for (int slot = 0; slot < itemResults.size(); slot++) {
            var result = itemResults.get(slot);
            if (result.isEmpty()) continue;
            int index = ITEM_INPUTS + slot;
            int count = result.totalCount(batches, random);
            if (count > 0) inventory.setStackInSlot(index, result.stack().copyWithCount(inventory.getStackInSlot(index).getCount() + count));
        }
        for (int slot = 0; slot < fluidResults.size(); slot++) {
            var result = fluidResults.get(slot);
            if (result.isEmpty()) continue;
            int amount = result.totalAmount(batches, 1, random);
            if (amount > 0) outputs.get(slot).fill(new FluidStack(result.stack(), amount), EXECUTE);
        }
    }

    private void reset(@Nullable ResourceLocation recipe) {
        activeRecipe = recipe;
        progress = batchStart = 0;
        setChanged();
    }

    @Override
    ProcessStatus computerProcess() {
        return new ProcessStatus(processing(), progress, Math.max(1D, duration), energyPerTick);
    }

    boolean processing() { return assembled() && processing; }
    int terminalPairs() { return layout == null || !assembled() ? 0 : layout.terminals().size() / 2; }
    double progressFraction() { return duration > 0 ? Math.min(1, progress / duration) : 0; }
    double electrolyteEfficiency() { return electrolyteEfficiency; }
    double speed() { return speed; }
    long energyPerTick() { return energyPerTick; }

    @Override void openMenu(Player player) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, owner) -> new LargeElectrolyzerMenu(id, inventory, this, ContainerLevelAccess.create(level, worldPosition)),
                Component.translatable("block.nuclearcraft.electrolyzer_controller")));
    }

    @Override void clear() {
        for (int slot = 0; slot < inventory.getSlots(); slot++) inventory.setStackInSlot(slot, ItemStack.EMPTY);
        for (var tank : inputs) tank.setFluid(FluidStack.EMPTY);
        for (var tank : outputs) tank.setFluid(FluidStack.EMPTY);
        electrolyte.setFluid(FluidStack.EMPTY);
        reset(null);
    }

    @Override void afterTransfers(ServerLevel server) {
        if (soundVolume != lastSoundVolume || server.getGameTime() % 5 == 0
                && !lastElectrolyte.isFluidStackIdentical(electrolyte.getFluid())) syncRender();
    }

    private void syncRender() {
        lastElectrolyte = electrolyte.getFluid().copy();
        lastSoundVolume = soundVolume;
        if (level != null && !isRemoved()) {
            var state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public @Nullable RenderVolume renderVolume() { return renderVolume; }

    @Override public CompoundTag getUpdateTag() {
        var tag = new CompoundTag();
        if (bounds == null || layout == null) return tag;
        BlockPos origin = bounds.min().offset(1, 1, 1).subtract(worldPosition);
        tag.putIntArray("volume", new int[]{origin.getX(), origin.getY(), origin.getZ(),
                bounds.xLength() - 2, bounds.yLength() - 2, bounds.zLength() - 2, electrolyte.getCapacity()});
        tag.put("electrolyte", electrolyte.getFluid().writeToNBT(new CompoundTag()));
        tag.putLongArray("terminals", layout.terminals().stream().map(BlockPos::asLong).toList());
        tag.putFloat("sound_volume", soundVolume);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag) {
        int[] volume = tag.getIntArray("volume");
        renderVolume = volume.length != 7 ? null : new RenderVolume(new BlockPos(volume[0], volume[1], volume[2]),
                volume[3], volume[4], volume[5], volume[6], FluidStack.loadFluidStackFromNBT(tag.getCompound("electrolyte")),
                Arrays.stream(tag.getLongArray("terminals")).mapToObj(BlockPos::of).toList(), tag.getFloat("sound_volume"));
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", inventory.serializeNBT());
        for (int i = 0; i < inputs.size(); i++) tag.put("input_" + i, inputs.get(i).writeToNBT(new CompoundTag()));
        for (int i = 0; i < outputs.size(); i++) tag.put("output_" + i, outputs.get(i).writeToNBT(new CompoundTag()));
        tag.put("electrolyte", electrolyte.writeToNBT(new CompoundTag()));
        tag.putDouble("progress", progress);
        tag.putDouble("batch_start", batchStart);
        if (activeRecipe != null) tag.putString("recipe", activeRecipe.toString());
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        var items = tag.getCompound("inventory").copy();
        items.putInt("Size", ITEM_INPUTS + ITEM_OUTPUTS);
        inventory.deserializeNBT(items);
        for (int i = 0; i < inputs.size(); i++) inputs.get(i).readFromNBT(tag.getCompound("input_" + i));
        for (int i = 0; i < outputs.size(); i++) outputs.get(i).readFromNBT(tag.getCompound("output_" + i));
        electrolyte.readFromNBT(tag.getCompound("electrolyte"));
        progress = finiteProgress(tag.getDouble("progress"));
        batchStart = Math.min(progress, finiteProgress(tag.getDouble("batch_start")));
        activeRecipe = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
    }

    private static double finiteProgress(double value) { return Double.isFinite(value) ? Math.max(0, value) : 0; }
}
