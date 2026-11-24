package nc.multiblock.machine;

import nc.init.NCMachines;
import nc.multiblock.Cuboid;
import nc.multiblock.machine.MachinePartBlock.Part;
import nc.recipe.InfiltratorPressureRecipe;
import nc.recipe.InfiltratorRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import static nc.recipe.MachineInputs.FLUID_INPUTS;
import static nc.recipe.MachineInputs.ITEM_INPUTS;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

public final class Infiltrator extends MachineController {
    private final ItemStackHandler inventory = new ItemStackHandler(ITEM_INPUTS + 1) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot < ITEM_INPUTS && acceptsItem(slot, stack);
        }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final List<FluidTank> inputs = List.of(inputTank(0), inputTank(1));
    private final FluidTank pressure = new FluidTank(Integer.MAX_VALUE, fluid -> pressureRecipe(fluid, false) != null) {
        @Override protected void onContentsChanged() { setChanged(); }
    };
    private List<BlockPos> heaters = List.of();
    private int chambers;
    private int heatedChambers;
    private double progress;
    private double batchStart;
    private double duration;
    private double pressureEfficiency;
    private double heatingBonus;
    private long energyPerTick;
    private boolean processing;
    private @Nullable ResourceLocation activeRecipe;
    private @Nullable RenderVolume renderVolume;
    private FluidStack lastRenderPressure = FluidStack.EMPTY;
    private float soundVolume;
    private float lastSoundVolume;

    public record RenderVolume(BlockPos origin, int width, int height, int depth, int capacity, FluidStack pressure, float soundVolume) {}

    public Infiltrator(BlockPos pos, BlockState state) {
        super(NCMachines.INFILTRATOR.get(), pos, state, Part.INFILTRATOR);
    }

    private FluidTank inputTank(int slot) {
        return new FluidTank(Integer.MAX_VALUE, fluid -> acceptsFluid(slot, fluid)) {
            @Override protected void onContentsChanged() { setChanged(); }
        };
    }

    @Override boolean indexedProcessPorts() { return true; }
    @Override int tankCount(boolean output) { return output ? 0 : FLUID_INPUTS; }
    @Override FluidTank tankFor(boolean output, int index) { return inputs.get(index); }
    @Override int itemCount(boolean output) { return output ? 1 : ITEM_INPUTS; }
    @Override ItemStackHandler processItems() { return inventory; }
    @Override FluidTank pressureTank() { return pressure; }

    @Override long energyCapacity() {
        return bounds == null ? 0 : (long) bounds.xLength() * bounds.yLength() * bounds.zLength()
                * NCMachines.infiltratorTime() * NCMachines.infiltratorPower();
    }

    private List<InfiltratorRecipe> recipes() {
        return level == null ? List.of() : level.getRecipeManager().getAllRecipesFor(NCMachines.INFILTRATOR_RECIPES.get());
    }

    private boolean acceptsFluid(int slot, FluidStack stack) {
        if (!NCMachines.infiltratorSmartInput() || stack.isFluidEqual(inputs.get(slot).getFluid())) {
            return recipes().stream().anyMatch(recipe -> recipe.accepts(stack));
        }
        var first = slot == 0 ? stack : inputs.get(0).getFluid();
        var second = slot == 1 ? stack : inputs.get(1).getFluid();
        return recipes().stream().anyMatch(recipe -> recipe.canMatch(inventory.getStackInSlot(0), inventory.getStackInSlot(1), first, second));
    }

    private boolean acceptsItem(int slot, ItemStack stack) {
        if (!NCMachines.infiltratorSmartInput() || ItemStack.isSameItemSameTags(stack, inventory.getStackInSlot(slot))) {
            return recipes().stream().anyMatch(recipe -> recipe.accepts(stack));
        }
        var first = slot == 0 ? stack : inventory.getStackInSlot(0);
        var second = slot == 1 ? stack : inventory.getStackInSlot(1);
        return recipes().stream().anyMatch(recipe -> recipe.canMatch(first, second, inputs.get(0).getFluid(), inputs.get(1).getFluid()));
    }

    private @Nullable InfiltratorPressureRecipe pressureRecipe(FluidStack stack, boolean requireAmount) {
        if (stack.isEmpty() || level == null) return null;
        return level.getRecipeManager().getAllRecipesFor(NCMachines.PRESSURE_RECIPES.get()).stream()
                .filter(recipe -> requireAmount ? recipe.fluid().matches(stack) : recipe.fluid().accepts(stack))
                .min(Comparator.comparing(InfiltratorPressureRecipe::id)).orElse(null);
    }

    @Override @Nullable String validateLayout(ServerLevel server, Cuboid found) {
        var pressureChambers = new ArrayList<BlockPos>();
        var heatingUnits = new HashSet<BlockPos>();
        for (var pos : BlockPos.betweenClosed(found.min().offset(1, 1, 1), found.max().offset(-1, -1, -1))) {
            Part part = MachinePartBlock.part(server.getBlockState(pos));
            if (part == Part.PRESSURE_CHAMBER) pressureChambers.add(pos.immutable());
            else if (part == Part.INFILTRATOR_HEATER) heatingUnits.add(pos.immutable());
            else if (part != null) return "interior";
        }
        int heated = 0;
        for (var pos : pressureChambers) {
            for (var face : Direction.values()) {
                if (heatingUnits.contains(pos.relative(face))) {
                    heated++;
                    break;
                }
            }
        }
        heaters = List.copyOf(heatingUnits);
        chambers = pressureChambers.size();
        heatedChambers = heated;
        return null;
    }

    @Override void assemblyChanged() {
        processing = false;
        soundVolume = 0;
        setLit(false);
        lightHeaters(false);
        syncRender();
    }

    @Override public void setRemoved() {
        lightHeaters(false);
        super.setRemoved();
    }

    @Override void processTick(ServerLevel server) {
        boolean active = infiltrate(server);
        if (!active) soundVolume = 0;
        setLit(active);
        if (processing != active) {
            processing = active;
            lightHeaters(active);
        }
    }

    private void lightHeaters(boolean active) {
        if (!(level instanceof ServerLevel server)) return;
        for (var pos : heaters) {
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) continue;
            var state = chunk.getBlockState(pos);
            if (MachinePartBlock.part(state) == Part.INFILTRATOR_HEATER && state.getValue(BlockStateProperties.LIT) != active) {
                server.setBlock(pos, state.setValue(BlockStateProperties.LIT, active), Block.UPDATE_CLIENTS);
            }
        }
    }

    private boolean infiltrate(ServerLevel server) {
        var firstItem = inventory.getStackInSlot(0);
        var secondItem = inventory.getStackInSlot(1);
        var firstFluid = inputs.get(0).getFluid();
        var secondFluid = inputs.get(1).getFluid();
        var recipe = recipes().stream().filter(candidate -> candidate.match(firstItem, secondItem, firstFluid, secondFluid) != null)
                .min(Comparator.comparing(InfiltratorRecipe::id)).orElse(null);
        var gas = pressureRecipe(pressure.getFluid(), true);
        pressureEfficiency = gas == null ? 0 : gas.efficiency();
        if (recipe == null) {
            if (activeRecipe != null) reset(null);
            duration = heatingBonus = 0;
            energyPerTick = 0;
            return false;
        }

        if (!recipe.id().equals(activeRecipe)) reset(recipe.id());
        var match = recipe.match(firstItem, secondItem, firstFluid, secondFluid);
        var product = recipe.product();
        var stored = inventory.getStackInSlot(ITEM_INPUTS);
        double fraction = Math.min(1D, (double) pressure.getFluidAmount() / pressure.getCapacity());
        heatingBonus = chambers == 0 ? 0 : (double) heatedChambers * recipe.heatingBonus() / chambers;
        duration = NCMachines.infiltratorTime() * recipe.time();
        double speed = chambers * pressureEfficiency * (1 + heatingBonus) * fraction;
        double power = NCMachines.infiltratorPower() * recipe.power() * (chambers + heaters.size()) * fraction;
        energyPerTick = (long) Math.ceil(power);
        if (speed <= 0 || blockedByRedstone(server) || !stored.isEmpty() && !ItemStack.isSameItemSameTags(stored, product)) return false;
        int possible = (product.getMaxStackSize() - stored.getCount()) / product.getCount();
        if (match.firstItems() > 0) possible = Math.min(possible, firstItem.getCount() / match.firstItems());
        if (match.secondItems() > 0) possible = Math.min(possible, secondItem.getCount() / match.secondItems());
        if (match.firstFluid() > 0) possible = Math.min(possible, firstFluid.getAmount() / match.firstFluid());
        if (match.secondFluid() > 0) possible = Math.min(possible, secondFluid.getAmount() / match.secondFluid());
        if (possible <= 0 || power() < energyPerTick || progress <= batchStart
                && power() < Math.min(energyCapacity(), (long) Math.ceil(duration / speed * power))) return false;

        setPower(power() - energyPerTick);
        soundVolume = (float) (NCMachines.infiltratorSoundVolume() * Math.log1p(Math.cbrt(speed)) / 128);
        progress += speed;
        int batches = Math.min(possible, (int) (progress / duration));
        if (batches > 0) {
            inventory.extractItem(0, batches * match.firstItems(), false);
            inventory.extractItem(1, batches * match.secondItems(), false);
            inputs.get(0).drain(batches * match.firstFluid(), EXECUTE);
            inputs.get(1).drain(batches * match.secondFluid(), EXECUTE);
            inventory.setStackInSlot(ITEM_INPUTS, product.copyWithCount(stored.getCount() + batches * product.getCount()));
            progress = batches == possible ? 0 : Math.max(0, progress - batches * duration);
            batchStart = progress;
        }
        setChanged();
        return true;
    }

    @Override void afterTransfers(ServerLevel server) {
        if (soundVolume != lastSoundVolume || server.getGameTime() % 5 == 0
                && !lastRenderPressure.isFluidStackIdentical(pressure.getFluid())) syncRender();
    }

    private void syncRender() {
        lastRenderPressure = pressure.getFluid().copy();
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
                bounds.xLength() - 2, bounds.yLength() - 2, bounds.zLength() - 2, pressure.getCapacity()});
        tag.put("pressure", pressure.getFluid().writeToNBT(new CompoundTag()));
        tag.putFloat("sound_volume", soundVolume);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag) {
        int[] volume = tag.getIntArray("volume");
        renderVolume = volume.length != 7 ? null : new RenderVolume(new BlockPos(volume[0], volume[1], volume[2]),
                volume[3], volume[4], volume[5], volume[6], FluidStack.loadFluidStackFromNBT(tag.getCompound("pressure")),
                tag.getFloat("sound_volume"));
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }

    private void reset(@Nullable ResourceLocation recipe) {
        activeRecipe = recipe;
        progress = batchStart = 0;
        setChanged();
    }

    boolean processing() { return assembled() && processing; }
    int chambers() { return chambers; }
    double progressFraction() { return duration > 0 ? Math.min(1, progress / duration) : 0; }
    double pressureEfficiency() { return pressureEfficiency; }
    double heatingBonus() { return heatingBonus; }
    long energyPerTick() { return energyPerTick; }

    @Override void openMenu(Player player) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, owner) -> new InfiltratorMenu(id, inventory, this, ContainerLevelAccess.create(level, worldPosition)),
                Component.translatable("block.nuclearcraft.infiltrator_controller")));
    }

    @Override void clear() {
        for (int slot = 0; slot < inventory.getSlots(); slot++) inventory.setStackInSlot(slot, ItemStack.EMPTY);
        for (var tank : inputs) tank.setFluid(FluidStack.EMPTY);
        pressure.setFluid(FluidStack.EMPTY);
        reset(null);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", inventory.serializeNBT());
        for (int slot = 0; slot < inputs.size(); slot++) tag.put("input_" + slot, inputs.get(slot).writeToNBT(new CompoundTag()));
        tag.put("pressure", pressure.writeToNBT(new CompoundTag()));
        tag.putDouble("progress", progress);
        tag.putDouble("batch_start", batchStart);
        if (activeRecipe != null) tag.putString("recipe", activeRecipe.toString());
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        var items = tag.getCompound("inventory").copy();
        items.putInt("Size", ITEM_INPUTS + 1);
        inventory.deserializeNBT(items);
        for (int slot = 0; slot < inputs.size(); slot++) inputs.get(slot).readFromNBT(tag.getCompound("input_" + slot));
        pressure.readFromNBT(tag.getCompound("pressure"));
        progress = finiteProgress(tag.getDouble("progress"));
        batchStart = Math.min(progress, finiteProgress(tag.getDouble("batch_start")));
        activeRecipe = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
    }

    private static double finiteProgress(double value) { return Double.isFinite(value) ? Math.max(0, value) : 0; }
}
