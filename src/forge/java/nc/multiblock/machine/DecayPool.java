package nc.multiblock.machine;

import nc.init.NCMachines;
import nc.multiblock.AssemblyDiagnostic;
import nc.multiblock.Cuboid;
import nc.multiblock.machine.MachinePartBlock.Part;
import nc.recipe.DecayPoolRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

public final class DecayPool extends MachineController {
    private final FluidTank input = tank(true);
    private final FluidTank output = tank(false);
    private final DecayReservoir reservoir = new DecayReservoir();
    private int containers;
    private double processHeat;
    private double processHeatRequired = 1D;
    private @Nullable ResourceLocation heatingRecipe;
    private boolean processing;
    private float processRate;
    private @Nullable RenderVolume renderVolume;
    private FluidStack lastRenderFeed = FluidStack.EMPTY;
    private FluidStack lastRenderProduct = FluidStack.EMPTY;

    public record RenderVolume(BlockPos origin, int width, int height, int depth, int capacity,
                               FluidStack feed, FluidStack product) {}

    public DecayPool(BlockPos pos, BlockState state) {
        super(NCMachines.POOL.get(), pos, state, Part.DECAY_POOL);
    }

    private FluidTank tank(boolean inlet) {
        return new FluidTank(Integer.MAX_VALUE, fluid -> !inlet || acceptsWater(fluid)) {
            @Override protected void onContentsChanged() { setChanged(); }
        };
    }

    private boolean acceptsWater(FluidStack stack) {
        return level != null && level.getRecipeManager().getAllRecipesFor(NCMachines.POOL_RECIPES.get())
                .stream().anyMatch(recipe -> recipe.accepts(stack));
    }

    private List<DecayPoolRecipe> sources() {
        return level == null ? List.of() : level.getRecipeManager().getAllRecipesFor(NCMachines.HEAT_SOURCES.get())
                .stream().sorted(Comparator.comparing(DecayPoolRecipe::id)).toList();
    }

    boolean acceptsFuel(ItemStack item) {
        return sources().stream().anyMatch(recipe -> recipe.accepts(item));
    }

    boolean acceptsFuel(FluidStack fluid) {
        return sources().stream().anyMatch(recipe -> recipe.accepts(fluid));
    }

    @Override void processTick(ServerLevel server) {
        if (reservoir.tick(sources(), fuelCapacity(), server.random)) setChanged();
        processing = heatWater(server);
        setLit(processing);
    }

    @Override void afterTransfers(ServerLevel server) {
        if (server.getGameTime() % 5 == 0 && (!lastRenderFeed.isFluidStackIdentical(input.getFluid())
                || !lastRenderProduct.isFluidStackIdentical(output.getFluid()))) syncRender();
    }

    @Override @Nullable AssemblyDiagnostic validateLayout(ServerLevel server, Cuboid found) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(found.min().offset(1, 1, 1), found.max().offset(-1, -1, -1))) {
            Part part = MachinePartBlock.part(server.getBlockState(pos));
            if (part == Part.CONTAINER) count++;
            else if (part != null) return problem("interior", pos);
        }
        containers = count;
        return null;
    }

    @Override void assemblyChanged() { syncRender(); }

    private void syncRender() {
        lastRenderFeed = input.getFluid().copy();
        lastRenderProduct = output.getFluid().copy();
        if (level != null && !isRemoved()) {
            var state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public @Nullable RenderVolume renderVolume() { return renderVolume; }

    @Override
    public CompoundTag getUpdateTag() {
        var tag = new CompoundTag();
        if (bounds != null) {
            BlockPos origin = bounds.min().offset(1, 1, 1).subtract(worldPosition);
            tag.putIntArray("volume", new int[]{origin.getX(), origin.getY(), origin.getZ(),
                    bounds.xLength() - 2, bounds.yLength() - 2, bounds.zLength() - 2, input.getCapacity()});
            tag.put("feed", input.getFluid().writeToNBT(new CompoundTag()));
            tag.put("product", output.getFluid().writeToNBT(new CompoundTag()));
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        int[] data = tag.getIntArray("volume");
        renderVolume = data.length == 7 ? new RenderVolume(new BlockPos(data[0], data[1], data[2]),
                data[3], data[4], data[5], data[6], FluidStack.loadFluidStackFromNBT(tag.getCompound("feed")),
                FluidStack.loadFluidStackFromNBT(tag.getCompound("product"))) : null;
    }

    private boolean heatWater(ServerLevel server) {
        processRate = 0;
        if (reservoir.heatRate() <= 0 || blockedByRedstone(server)) return false;
        if (input.isEmpty()) return false;
        var recipe = server.getRecipeManager().getAllRecipesFor(NCMachines.POOL_RECIPES.get()).stream()
                .filter(candidate -> candidate.fluidInput().matches(input.getFluid()))
                .min(Comparator.comparing(DecayPoolRecipe::id)).orElse(null);
        if (recipe == null) return false;
        if (!recipe.id().equals(heatingRecipe)) {
            processHeat = 0;
            heatingRecipe = recipe.id();
            setChanged();
        }
        int feed = recipe.fluidInput().matchedAmount(input.getFluid(), 1);
        var product = recipe.fluidProduct();
        int room = product.batchesIn(output.getFluid(), output.getCapacity());
        processHeatRequired = recipe.heatFor(feed);
        if (room == 0) return false;

        double available = Math.min(Double.MAX_VALUE, processHeat + reservoir.heatRate());
        processRate = (float) (reservoir.heatRate() * product.stack().getAmount() / processHeatRequired);
        while (feed > 0 && room > 0) {
            processHeatRequired = recipe.heatFor(feed);
            int batches = Math.min(Math.min(input.getFluidAmount() / feed, room), (int) (available / processHeatRequired));
            if (batches == 0) break;
            int amount = product.totalAmount(batches, 1, server.random);
            input.drain(batches * feed, EXECUTE);
            if (amount > 0) {
                var produced = product.stack();
                produced.setAmount(amount);
                output.fill(produced, EXECUTE);
            }
            available = Math.max(0, available - batches * processHeatRequired);
            feed = recipe.fluidInput().matchedAmount(input.getFluid(), 1);
            room = product.batchesIn(output.getFluid(), output.getCapacity());
        }
        processHeat = available % processHeatRequired;
        setChanged();
        return true;
    }

    @Override int tankCount(boolean product) { return 1; }
    @Override FluidTank tankFor(boolean product, int index) { return product ? output : input; }
    @Override long energyCapacity() { return 16; }

    DecayReservoir reservoir() { return reservoir; }
    public int fuelCapacity() { return containers * DecayReservoir.ITEM_VOLUME; }
    public double heatingRate() { return reservoir.heatRate(); }
    public double decayRate() { return reservoir.decayRate(); }
    @Override
    ProcessStatus computerProcess() {
        return new ProcessStatus(processing(), processHeat, processHeatRequired, 0L);
    }

    public boolean processing() { return processing && assembled(); }
    @Override
    void clear() {
        reservoir.clear();
        input.setFluid(FluidStack.EMPTY);
        output.setFluid(FluidStack.EMPTY);
        processHeat = 0;
        processHeatRequired = 1D;
        heatingRecipe = null;
        setChanged();
    }

    @Override void openMenu(Player player) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, owner) -> new DecayPoolMenu(id, this, ContainerLevelAccess.create(level, worldPosition)),
                Component.translatable("block.nuclearcraft.decay_pool_controller")));
    }

    int menuValue(int index) {
        return switch (index) {
            case 0 -> halted() ? 1 : 0;
            case 1 -> containers;
            case 2 -> bounds == null ? 0 : bounds.xLength() - 2;
            case 3 -> bounds == null ? 0 : bounds.yLength() - 2;
            case 4 -> bounds == null ? 0 : bounds.zLength() - 2;
            case 5 -> processing() ? 1 : 0;
            case 6, 7 -> DecayPoolMenu.word(Float.floatToIntBits((float) reservoir.heatRate()), index - 6);
            case 8, 9 -> DecayPoolMenu.word(Float.floatToIntBits((float) reservoir.decayRate()), index - 8);
            case 10, 11 -> DecayPoolMenu.word(input.getFluidAmount(), index - 10);
            case 12, 13 -> DecayPoolMenu.word(output.getFluidAmount(), index - 12);
            case 14, 15 -> DecayPoolMenu.word(Float.floatToIntBits(processRate), index - 14);
            default -> 0;
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("feed", input.writeToNBT(new CompoundTag()));
        tag.put("product", output.writeToNBT(new CompoundTag()));
        tag.put("reservoir", reservoir.save());
        tag.putDouble("process_heat", processHeat);
        if (heatingRecipe != null) tag.putString("heating_recipe", heatingRecipe.toString());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        input.readFromNBT(tag.getCompound("feed"));
        output.readFromNBT(tag.getCompound("product"));
        reservoir.load(tag.getCompound("reservoir"));
        double heat = tag.getDouble("process_heat");
        processHeat = Double.isFinite(heat) ? Math.max(0, heat) : 0;
        heatingRecipe = ResourceLocation.tryParse(tag.getString("heating_recipe"));
        setPower(Math.min(energyCapacity(), power()));
    }
}
