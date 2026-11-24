package nc.multiblock.machine;

import nc.init.NCMachines;
import nc.multiblock.Cuboid;
import nc.multiblock.machine.MachinePartBlock.Part;
import nc.recipe.DecayPoolRecipe;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

public final class DecayPool extends BlockEntity {
    private final FluidTank input = tank(true);
    private final FluidTank output = tank(false);
    private final DecayReservoir reservoir = new DecayReservoir();
    private @Nullable Cuboid bounds;
    private List<MachinePort> ports = List.of();
    private List<BlockPos> redstone = List.of();
    private List<BlockPos> frames = List.of();
    private int containers;
    private boolean dirty = true;
    private long nextValidationTick;
    private boolean halted;
    private double processHeat;
    private @Nullable ResourceLocation heatingRecipe;
    private boolean processing;
    private float processRate;
    private int power;
    private String validation = "dimensions";
    private @Nullable RenderVolume renderVolume;
    private FluidStack lastRenderFeed = FluidStack.EMPTY;
    private FluidStack lastRenderProduct = FluidStack.EMPTY;

    public record RenderVolume(BlockPos origin, int width, int height, int depth, int capacity,
                               FluidStack feed, FluidStack product) {}

    public DecayPool(BlockPos pos, BlockState state) {
        super(NCMachines.POOL.get(), pos, state);
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

    void tick() {
        if (!(level instanceof ServerLevel server)) return;
        long time = server.getGameTime();
        if (dirty || (bounds == null && time >= nextValidationTick)) {
            assemble(server);
            dirty = false;
            nextValidationTick = time + 20;
        }
        if (!assembled()) return;
        if (reservoir.tick(sources(), fuelCapacity())) setChanged();
        processing = heatWater(server);
        setLit(processing);
        for (var port : ports) port.push();
        if (time % 5 == 0 && (!lastRenderFeed.isFluidStackIdentical(input.getFluid())
                || !lastRenderProduct.isFluidStackIdentical(output.getFluid()))) syncRender();
    }

    public boolean assembled() { return bounds != null && !isRemoved(); }

    private void assemble(ServerLevel server) {
        var found = Cuboid.findConnected(server, worldPosition, NCMachines.maxSize() + 2,
                state -> MachinePartBlock.part(state) != null);
        if (found == null || Math.min(found.xLength(), Math.min(found.yLength(), found.zLength())) < NCMachines.minSize() + 2) {
            fail("dimensions");
            return;
        }
        if (!found.isLoaded(server)) {
            fail("unloaded");
            return;
        }

        var installedPorts = new ArrayList<MachinePort>();
        var installedRedstone = new ArrayList<BlockPos>();
        var edgeFrames = new ArrayList<BlockPos>();
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(found.min(), found.max())) {
            var part = MachinePartBlock.part(server.getBlockState(pos));
            int boundary = found.boundaryAxes(pos);
            if (boundary == 0) {
                if (part == Part.CONTAINER) count++;
                else if (part != null) {
                    fail("interior");
                    return;
                }
            } else if (part == Part.FRAME) {
                if (boundary > 1) edgeFrames.add(pos.immutable());
                continue;
            } else if (boundary != 1 || part == null || part == Part.CONTAINER
                    || (part == Part.DECAY_POOL && !pos.equals(worldPosition))) {
                fail("wall");
                return;
            } else if (part.port()) {
                if (server.getBlockEntity(pos) instanceof MachinePort port) installedPorts.add(port);
                else {
                    fail("wall");
                    return;
                }
            } else if (part == Part.REDSTONE) installedRedstone.add(pos.immutable());
        }
        if (found.boundaryAxes(worldPosition) != 1) {
            fail("wall");
            return;
        }

        disconnect();
        bounds = found;
        ports = List.copyOf(installedPorts);
        redstone = List.copyOf(installedRedstone);
        frames = List.copyOf(edgeFrames);
        containers = count;
        long volume = (long) found.xLength() * found.yLength() * found.zLength();
        int capacity = (int) Math.min(Integer.MAX_VALUE, volume * 1000);
        input.setCapacity(capacity);
        output.setCapacity(capacity);
        validation = "assembled";
        var state = getBlockState();
        level.setBlock(worldPosition, state.setValue(BlockStateProperties.FACING, found.outward(worldPosition)),
                Block.UPDATE_CLIENTS);
        for (var port : ports) port.bind(this, found.outward(port.getBlockPos()));
        setFrames(true);
        syncRender();
    }

    private void fail(String error) {
        boolean previouslyAssembled = bounds != null;
        setFrames(false);
        disconnect();
        validation = error;
        setLit(false);
        if (previouslyAssembled) syncRender();
    }

    private void disconnect() {
        bounds = null;
        for (var port : ports) port.unbind(this);
        ports = List.of();
        frames = List.of();
    }

    private void setFrames(boolean assembled) {
        if (!(level instanceof ServerLevel server)) return;
        for (BlockPos pos : frames) {
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) continue;
            var state = chunk.getBlockState(pos);
            if (state.hasProperty(MachinePartBlock.FRAME_EDGE) && state.getValue(MachinePartBlock.FRAME_EDGE) != assembled) {
                level.setBlock(pos, state.setValue(MachinePartBlock.FRAME_EDGE, assembled), Block.UPDATE_CLIENTS);
            }
        }
    }

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

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }

    void partChanged(BlockPos pos) {
        if (bounds != null) {
            if (!bounds.contains(pos) && !bounds.contains(pos.above()) && !bounds.contains(pos.below())
                    && !bounds.contains(pos.north()) && !bounds.contains(pos.south())
                    && !bounds.contains(pos.east()) && !bounds.contains(pos.west())) return;
            fail("wall");
        }
        dirty = true;
    }

    void chunkUnloaded(ChunkPos chunk) {
        if (bounds != null && bounds.touchesChunk(chunk)) {
            validation = "unloaded";
            disconnect();
            if (!new ChunkPos(worldPosition).equals(chunk)) {
                setLit(false);
                syncRender();
            }
            dirty = true;
        }
    }

    @Override
    public void onChunkUnloaded() {
        disconnect();
        dirty = true;
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        setFrames(false);
        disconnect();
        super.setRemoved();
    }

    private boolean heatWater(ServerLevel server) {
        processRate = 0;
        if (halted || reservoir.heatRate() <= 0 || server.hasNeighborSignal(worldPosition)
                || redstone.stream().anyMatch(server::hasNeighborSignal)) return false;
        if (input.isEmpty()) return false;
        var recipe = server.getRecipeManager().getAllRecipesFor(NCMachines.POOL_RECIPES.get()).stream()
                .filter(candidate -> candidate.accepts(input.getFluid()))
                .min(Comparator.comparing(DecayPoolRecipe::id)).orElse(null);
        if (recipe == null) return false;
        if (!recipe.id().equals(heatingRecipe)) {
            processHeat = 0;
            heatingRecipe = recipe.id();
            setChanged();
        }
        int feed = recipe.fluidInput().amount();
        var product = recipe.fluidResult();
        if (!output.isEmpty() && !output.getFluid().isFluidEqual(product)) return false;
        int possible = Math.min(input.getFluidAmount() / feed,
                Math.max(0, output.getCapacity() - output.getFluidAmount()) / product.getAmount());
        if (possible == 0) return false;
        processRate = (float) (reservoir.heatRate() * product.getAmount() / recipe.heat());
        double available = processHeat + reservoir.heatRate();
        int batches = Math.min(possible, (int) (available / recipe.heat()));
        processHeat = available - batches * recipe.heat();
        if (processHeat >= recipe.heat()) processHeat %= recipe.heat();
        if (batches > 0) {
            input.drain(batches * feed, EXECUTE);
            output.fill(new FluidStack(product, batches * product.getAmount()), EXECUTE);
        }
        setChanged();
        return true;
    }

    private void setLit(boolean active) {
        if (level != null && getBlockState().hasProperty(BlockStateProperties.LIT)
                && getBlockState().getValue(BlockStateProperties.LIT) != active) {
            level.setBlock(worldPosition, getBlockState().setValue(BlockStateProperties.LIT, active), Block.UPDATE_CLIENTS);
        }
    }

    FluidTank tankFor(boolean product) { return product ? output : input; }
    DecayReservoir reservoir() { return reservoir; }
    public int fuelCapacity() { return containers * DecayReservoir.ITEM_VOLUME; }
    public double heatingRate() { return reservoir.heatRate(); }
    public double decayRate() { return reservoir.decayRate(); }
    public boolean processing() { return processing && assembled(); }
    public boolean halted() { return halted; }
    int power() { return power; }

    void setPower(int quantity) {
        if (power != quantity) {
            power = quantity;
            setChanged();
        }
    }

    void toggle() {
        halted = !halted;
        setChanged();
    }

    void clear() {
        reservoir.clear();
        input.setFluid(FluidStack.EMPTY);
        output.setFluid(FluidStack.EMPTY);
        processHeat = 0;
        heatingRecipe = null;
        setChanged();
    }

    void open(Player player) {
        if (level == null) return;
        if (dirty || !assembled()) {
            if (level instanceof ServerLevel server) {
                assemble(server);
                dirty = false;
            }
        }
        if (!assembled()) {
            player.displayClientMessage(Component.translatable("message.nuclearcraft.decay_pool." + validation), true);
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, owner) -> new DecayPoolMenu(id, this, ContainerLevelAccess.create(level, worldPosition)),
                Component.translatable("block.nuclearcraft.decay_pool_controller")));
    }

    int menuValue(int index) {
        return switch (index) {
            case 0 -> halted ? 1 : 0;
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
        tag.putBoolean("halted", halted);
        tag.putDouble("process_heat", processHeat);
        if (heatingRecipe != null) tag.putString("heating_recipe", heatingRecipe.toString());
        tag.putInt("energy", power);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        input.readFromNBT(tag.getCompound("feed"));
        output.readFromNBT(tag.getCompound("product"));
        reservoir.load(tag.getCompound("reservoir"));
        halted = tag.getBoolean("halted");
        double heat = tag.getDouble("process_heat");
        processHeat = Double.isFinite(heat) ? Math.max(0, heat) : 0;
        heatingRecipe = ResourceLocation.tryParse(tag.getString("heating_recipe"));
        power = Math.max(0, Math.min(16, tag.getInt("energy")));
    }
}
