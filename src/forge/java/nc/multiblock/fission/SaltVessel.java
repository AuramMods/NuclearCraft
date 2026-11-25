package nc.multiblock.fission;

import nc.init.NCSaltFission;
import nc.processor.ProcessorInputs;
import nc.recipe.FluidBatch;
import nc.recipe.SaltFissionRecipe;
import nc.radiation.ChunkRadiationSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

public final class SaltVessel extends BlockEntity implements ChunkRadiationSource {
    private final RecipeManager.CachedCheck<Container, SaltFissionRecipe> recipes =
            RecipeManager.createCheck(NCSaltFission.FUEL_TYPE.get());
    private final ProcessorInputs recipeInput = new ProcessorInputs(0);
    private final SaltFuelBank.Tanks local = new SaltFuelBank.Tanks(this, this::filter);
    private @Nullable SolidFissionController controller;
    private @Nullable ResourceLocation filter;
    private @Nullable ResourceLocation reservedCircuit;
    private @Nullable ResourceLocation reservedRecipe;
    private FluidStack reserved = FluidStack.EMPTY;
    private boolean reservedLocal;
    private final FissionFuelDecay decay = new FissionFuelDecay(this);
    private double progress;
    private double sourceEfficiency = -1;
    private long emissionTick = Long.MIN_VALUE;
    private double emission;

    public SaltVessel(BlockPos pos, BlockState state) {
        super(NCSaltFission.VESSEL_ENTITY.get(), pos, state);
    }

    @Nullable ResourceLocation filter() {
        return filter;
    }

    boolean selectFilter(@Nullable ResourceLocation next) {
        if (controller() != null || !local.tank(false).isEmpty() || Objects.equals(filter, next)) {
            return false;
        }
        filter = next;
        setChanged();
        if (level instanceof ServerLevel server) {
            FissionPartBlock.geometryChanged(server, worldPosition);
        }
        return true;
    }

    void bind(@Nullable SolidFissionController next) {
        controller = next;
    }

    @Nullable SolidFissionController controller() {
        return controller != null && controller.assembled() ? controller : null;
    }

    SaltFuelBank.Tanks localTanks() {
        return local;
    }

    FluidTank fuelTank(boolean output) {
        var current = controller();
        return current == null ? local.tank(output) : fuelTank(current.saltFuelBank(), output);
    }

    private FluidTank fuelTank(SaltFuelBank bank, boolean output) {
        return (output && !reserved.isEmpty() ? reservedTanks(bank) : feedTanks(bank)).tank(output);
    }

    private SaltFuelBank.Tanks feedTanks(SaltFuelBank bank) {
        var shared = bank.connected(filter);
        return shared == null ? local : shared;
    }

    private SaltFuelBank.Tanks reservedTanks(SaltFuelBank bank) {
        return reservedLocal ? local : bank.circuit(reservedCircuit);
    }

    SaltFissionRecipe prepare(Level level, SaltFuelBank bank) {
        boolean fromTank = reserved.isEmpty();
        var feed = feedTanks(bank);
        var input = feed.tank(false);
        FluidStack fuel = fromTank ? input.getFluid() : reserved;
        if (fuel.isEmpty() || (fromTank && !input.isFluidValid(fuel))) {
            if (sourceEfficiency >= 0) {
                sourceEfficiency = -1;
                setChanged();
            }
            return null;
        }
        SaltFissionRecipe recipe = fromTank ? findRecipe(level, fuel) : findReservation(level);
        if (recipe == null) {
            return null;
        }
        decay.remember(recipe, recipe.runtimeHeat());
        double processTime = recipe.runtimeTicks();
        if (fromTank) {
            // Reserve a measured input once and keep its destination channel through a rebuild.
            if (!hasOutputSpace(recipe, feed.tank(true))) {
                return null;
            }
            reserved = input.drain(recipe.fuel().matchedAmount(fuel, 1), EXECUTE);
            reservedCircuit = filter;
            reservedLocal = feed == local;
        }
        if (fromTank || reservedRecipe == null) {
            reservedRecipe = recipe.id();
            setChanged();
        }
        var output = fuelTank(bank, true);
        if (progress >= processTime) {
            discharge(recipe, output);
            return null;
        }
        if (!hasOutputSpace(recipe, output)) {
            return null;
        }
        return recipe;
    }

    FluidStack reservedForInspection() {
        return reserved.copy();
    }

    double progressForInspection() {
        if (level == null || reserved.isEmpty()) {
            return 0;
        }
        var recipe = findReservation(level);
        return recipe == null ? 0D : Math.min(1, progress / recipe.runtimeTicks());
    }

    private @Nullable SaltFissionRecipe findRecipe(Level level, FluidStack fuel) {
        recipeInput.setFluid(fuel);
        return recipes.getRecipeFor(recipeInput, level).orElse(null);
    }

    private @Nullable SaltFissionRecipe findReservation(Level level) {
        var manager = level.getRecipeManager();
        if (reservedRecipe != null) {
            var recipe = manager.byKey(reservedRecipe).orElse(null);
            return recipe instanceof SaltFissionRecipe fuel && matchesReservation(fuel) ? fuel : null;
        }
        var recipe = findRecipe(level, reserved);
        if (recipe != null && matchesReservation(recipe)) {
            return recipe;
        }
        return manager.getAllRecipesFor(NCSaltFission.FUEL_TYPE.get()).stream().filter(this::matchesReservation).findFirst().orElse(null);
    }

    private boolean matchesReservation(SaltFissionRecipe recipe) {
        return recipe.fuel().matchedAmount(reserved, 1) == reserved.getAmount();
    }

    boolean sameFuel(SaltVessel other) {
        return Objects.equals(reservedCircuit, other.reservedCircuit) && reserved.isFluidEqual(other.reserved);
    }

    double computerProgress() {
        return progress;
    }

    void clearMaterial() {
        local.clear();
        resetReservation();
        setChanged();
    }

    private void resetReservation() {
        reserved = FluidStack.EMPTY;
        reservedCircuit = null;
        reservedRecipe = null;
        reservedLocal = false;
        progress = 0;
    }

    void primeWith(double efficiency) {
        if (efficiency > sourceEfficiency) {
            sourceEfficiency = efficiency;
            setChanged();
        }
    }

    FissionFuelDecay decay() {
        return decay;
    }

    double sourceEfficiency() {
        return sourceEfficiency < 0 ? 1 : sourceEfficiency;
    }

    void burn(SaltFissionRecipe recipe, SaltFuelBank bank, double speed) {
        emission = Math.min(Double.MAX_VALUE, recipe.runtimeRadiation() * speed);
        emissionTick = level.getGameTime();
        double ticks = recipe.runtimeTicks();
        progress += speed;
        if (progress >= ticks) {
            finishDoses(recipe, bank, ticks);
        }
        setChanged();
    }

    @Override
    public double radiationEmission() {
        return level != null && level.getGameTime() == emissionTick ? emission : 0;
    }

    private void finishDoses(SaltFissionRecipe recipe, SaltFuelBank bank, double ticks) {
        var tanks = reservedTanks(bank);
        FluidTank output = tanks.tank(true);
        FluidTank input = tanks.tank(false);
        var product = recipe.product();
        boolean sameFeed = feedTanks(bank) == tanks && Objects.equals(filter, reservedCircuit)
                && reserved.isFluidEqual(input.getFluid());
        int pending = (int) Math.floor(progress / ticks);
        int room = product.batchesIn(output.getFluid(), output.getCapacity());
        if (room == 0) {
            progress = ticks;
            return;
        }

        // Check room for the maximum of each dose. A smaller roll can free room for more doses this tick.
        int completed = 0;
        while (completed < pending && room > 0) {
            int inclReserved = completed == 0 ? 1 : 0;
            int extraLimit = Math.min(pending - completed, room) - inclReserved;
            int available = sameFeed ? input.getFluidAmount() : 0;
            var extra = FluidBatch.fit(recipe.fuel(), input.getFluid(), available, extraLimit);
            int doses = inclReserved + extra.batches();
            if (doses == 0) {
                break;
            }
            int produced = product.totalAmount(doses, 1, level.random);
            input.drain(extra.consumed(), EXECUTE);
            if (produced > 0) {
                output.fill(new FluidStack(product.stack(), produced), EXECUTE);
            }
            completed += doses;
            room = product.batchesIn(output.getFluid(), output.getCapacity());
        }
        int inputSize = sameFeed ? recipe.fuel().matchedAmount(input.getFluid(), 1) : -1;
        if (inputSize > 0 && room > 0) {
            reserved = input.drain(inputSize, EXECUTE);
            progress = Math.max(0, progress - completed * ticks);
        } else {
            resetReservation();
        }
    }

    private static boolean hasOutputSpace(SaltFissionRecipe recipe, FluidTank tank) {
        return recipe.product().batchesIn(tank.getFluid(), tank.getCapacity()) > 0;
    }

    private void discharge(SaltFissionRecipe recipe, FluidTank tank) {
        if (hasOutputSpace(recipe, tank)) {
            tank.fill(recipe.product().roll(level.random), EXECUTE);
            resetReservation();
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        decay.save(tag);
        if (filter != null) {
            tag.putString("fuel_filter", filter.toString());
        }
        if (reservedCircuit != null) {
            tag.putString("reserved_circuit", reservedCircuit.toString());
        }
        if (reservedLocal) {
            tag.putBoolean("reserved_local", true);
        }
        tag.put("fuel_tanks", local.save());
        if (reservedRecipe != null) tag.putString("reserved_recipe", reservedRecipe.toString());
        tag.put("fuel", reserved.writeToNBT(new CompoundTag()));
        tag.putDouble("progress", progress);
        if (sourceEfficiency >= 0) {
            tag.putDouble("source_efficiency", sourceEfficiency);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        decay.load(tag);
        var previousFilter = filter;
        filter = tag.contains("fuel_filter") ? ResourceLocation.tryParse(tag.getString("fuel_filter")) : null;
        local.load(tag.getCompound("fuel_tanks"));
        reservedLocal = tag.getBoolean("reserved_local");
        reservedRecipe = tag.contains("reserved_recipe") ? ResourceLocation.tryParse(tag.getString("reserved_recipe")) : null;
        reservedCircuit = tag.contains("reserved_circuit") ? ResourceLocation.tryParse(tag.getString("reserved_circuit")) : null;
        reserved = FluidStack.loadFluidStackFromNBT(tag.getCompound("fuel"));
        double storedTime = tag.getDouble("progress");
        progress = Double.isFinite(storedTime) ? Math.max(0, storedTime) : 0;
        double efficiency = tag.getDouble("source_efficiency");
        sourceEfficiency = tag.contains("source_efficiency") && Double.isFinite(efficiency) && efficiency >= 0 ? efficiency : -1;
        if (!Objects.equals(filter, previousFilter) && level instanceof ServerLevel server) {
            FissionPartBlock.geometryChanged(server, worldPosition);
        }
    }
}
