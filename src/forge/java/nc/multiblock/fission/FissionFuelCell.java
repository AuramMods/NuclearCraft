package nc.multiblock.fission;

import nc.init.NCFission;
import nc.init.NCPebbleFission;
import nc.recipe.SolidFissionRecipe;
import nc.radiation.ChunkRadiationSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;

public final class FissionFuelCell extends BlockEntity implements ChunkRadiationSource {
    private final RecipeManager.CachedCheck<Container, SolidFissionRecipe> recipes;
    private final SimpleContainer recipeInput = new SimpleContainer(1);
    private ItemStack reservedFuel = ItemStack.EMPTY;
    private final FissionFuelDecay decay = new FissionFuelDecay(this);
    private double progress;
    private double sourceEfficiency = -1;
    private long emissionTick = Long.MIN_VALUE;
    private double emission;

    public FissionFuelCell(BlockPos pos, BlockState state) {
        super(FissionPartBlock.role(state) == FissionPartBlock.Role.PEBBLE_CHAMBER
                ? NCPebbleFission.CHAMBER_ENTITY.get() : NCFission.CELL_ENTITY.get(), pos, state);
        recipes = RecipeManager.createCheck(getType() == NCPebbleFission.CHAMBER_ENTITY.get()
                ? NCPebbleFission.FUEL_TYPE.get() : NCFission.FUEL_TYPE.get());
    }

    ItemStack unspentFuel() {
        return progress == 0 ? reservedFuel : ItemStack.EMPTY;
    }

    SolidFissionRecipe prepare(Level level, ItemStackHandler inventory) {
        boolean needsFuel = reservedFuel.isEmpty();
        ItemStack input = needsFuel ? inventory.getStackInSlot(0) : reservedFuel;
        if (input.isEmpty()) {
            if (sourceEfficiency >= 0) {
                sourceEfficiency = -1;
                setChanged();
            }
            return null;
        }
        recipeInput.setItem(0, input);
        SolidFissionRecipe recipe = recipes.getRecipeFor(recipeInput, level).orElse(null);
        if (recipe == null) {
            return null;
        }
        decay.remember(recipe, recipe.heat());
        if (needsFuel) {
            reservedFuel = inventory.extractItem(0, 1, false);
            setChanged();
        }
        if (progress >= recipe.ticks()) {
            discharge(recipe, inventory);
            return null;
        }
        return recipe;
    }

    ItemStack reservedFuel() {
        return reservedFuel;
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

    void burn(SolidFissionRecipe recipe, ItemStackHandler inventory, double speed) {
        emission = recipe.radiation() * speed;
        emissionTick = level.getGameTime();
        progress += speed;
        if (progress >= recipe.ticks()) {
            progress = recipe.ticks();
            discharge(recipe, inventory);
        }
        setChanged();
    }

    @Override
    public double radiationEmission() {
        return level != null && level.getGameTime() == emissionTick ? emission : 0;
    }

    private void discharge(SolidFissionRecipe recipe, ItemStackHandler inventory) {
        ItemStack depleted = recipe.depleted();
        if (inventory.insertItem(1, depleted, true).isEmpty()) {
            inventory.insertItem(1, depleted, false);
            reservedFuel = ItemStack.EMPTY;
            progress = 0;
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        decay.save(tag);
        tag.put("fuel", reservedFuel.save(new CompoundTag()));
        tag.putDouble("progress", progress);
        if (sourceEfficiency >= 0) {
            tag.putDouble("source_efficiency", sourceEfficiency);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        decay.load(tag);
        reservedFuel = ItemStack.of(tag.getCompound("fuel"));
        double stored = tag.getDouble("progress");
        progress = Double.isFinite(stored) ? Math.max(0, stored) : 0;
        double efficiency = tag.getDouble("source_efficiency");
        sourceEfficiency = tag.contains("source_efficiency") && Double.isFinite(efficiency) && efficiency >= 0 ? efficiency : -1;
    }
}
