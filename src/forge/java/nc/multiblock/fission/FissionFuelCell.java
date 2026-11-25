package nc.multiblock.fission;

import nc.init.NCFission;
import nc.init.NCPebbleFission;
import nc.recipe.SolidFissionRecipe;
import nc.radiation.ChunkRadiationSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class FissionFuelCell extends BlockEntity implements ChunkRadiationSource {
    private final RecipeType<SolidFissionRecipe> fuelType;
    private final RecipeManager.CachedCheck<Container, SolidFissionRecipe> recipes;
    private final SimpleContainer recipeInput = new SimpleContainer(1);
    private final FissionFuelItems localItems;
    private @Nullable SolidFissionController controller;
    private @Nullable ResourceLocation reservedFilter;
    private @Nullable ResourceLocation reservedRecipe;
    private boolean reservedLocal;
    private ItemStack reservedFuel = ItemStack.EMPTY;
    private final FissionFuelDecay decay = new FissionFuelDecay(this);
    private double progress;
    private double sourceEfficiency = -1;
    private long emissionTick = Long.MIN_VALUE;
    private double emission;

    public FissionFuelCell(BlockPos pos, BlockState state) {
        super(FissionPartBlock.role(state) == FissionPartBlock.Role.PEBBLE_CHAMBER
                ? NCPebbleFission.CHAMBER_ENTITY.get() : NCFission.CELL_ENTITY.get(), pos, state);
        FissionVariant variant = FissionPartBlock.role(state) == FissionPartBlock.Role.PEBBLE_CHAMBER
                ? FissionVariant.PEBBLE : FissionVariant.SOLID;
        fuelType = variant.itemFuelType();
        recipes = RecipeManager.createCheck(fuelType);
        localItems = new FissionFuelItems(this, variant, null);
    }

    ItemStack unspentFuel() {
        return progress == 0 ? reservedFuel : ItemStack.EMPTY;
    }

    FissionFuelItems localItems() {
        return localItems;
    }

    void bind(@Nullable SolidFissionController next) {
        controller = next;
    }

    @Nullable SolidFissionController controller() {
        return controller != null && controller.assembled() ? controller : null;
    }

    @Nullable ResourceLocation filter() {
        return localItems.filter();
    }

    FissionFuelItems items(boolean output) {
        var current = controller();
        if (current == null) {
            return localItems;
        }
        if (output && !reservedFuel.isEmpty()) {
            return reservedLocal ? localItems : current.fuelItems(reservedFilter);
        }
        return current.hasItemFuelCircuit(filter()) ? current.fuelItems(filter()) : localItems;
    }

    SolidFissionRecipe prepare(Level level) {
        boolean needsFuel = reservedFuel.isEmpty();
        FissionFuelItems feed = items(false);
        // Existing Forge worlds may have unfiltered fuel buffered in a controller without input ports.
        var current = controller();
        if (feed == localItems && filter() == null && feed.getStackInSlot(0).isEmpty() && current != null) {
            feed = current.fuelItems(null);
        }
        ItemStack input = needsFuel ? feed.bulkStack(0) : reservedFuel;
        if (input.isEmpty()) {
            if (sourceEfficiency >= 0) {
                sourceEfficiency = -1;
                setChanged();
            }
            return null;
        }
        SolidFissionRecipe recipe = needsFuel ? findRecipe(level, input) : findReservation(level);
        if (recipe == null) {
            return null;
        }
        decay.remember(recipe, recipe.runtimeHeat());
        if (needsFuel) {
            reservedFuel = feed.takeFuel(recipe.fuel().matchedCount(input));
            reservedLocal = feed == localItems;
            reservedFilter = filter();
        }
        if (needsFuel || reservedRecipe == null) {
            reservedRecipe = recipe.id();
            setChanged();
        }
        if (progress >= recipe.runtimeTicks()) {
            discharge(recipe);
            return null;
        }
        return items(true).insertBulk(1, recipe.depleted(), true).isEmpty() ? recipe : null;
    }

    ItemStack reservedFuel() {
        return reservedFuel;
    }

    double progressFraction() {
        if (level == null || reservedFuel.isEmpty()) {
            return 0D;
        }
        var recipe = findReservation(level);
        return recipe == null ? 0D : Math.min(1D, progress / recipe.runtimeTicks());
    }

    private @Nullable SolidFissionRecipe findRecipe(Level level, ItemStack input) {
        recipeInput.setItem(0, input);
        return recipes.getRecipeFor(recipeInput, level).orElse(null);
    }

    private @Nullable SolidFissionRecipe findReservation(Level level) {
        var manager = level.getRecipeManager();
        if (reservedRecipe != null) {
            var recipe = manager.byKey(reservedRecipe).orElse(null);
            return recipe instanceof SolidFissionRecipe fuel && fuel.getType() == fuelType && matchesReservation(fuel) ? fuel : null;
        }
        var recipe = findRecipe(level, reservedFuel);
        if (recipe != null && matchesReservation(recipe)) {
            return recipe;
        }
        return manager.getAllRecipesFor(fuelType).stream().filter(this::matchesReservation).findFirst().orElse(null);
    }

    private boolean matchesReservation(SolidFissionRecipe recipe) {
        return recipe.fuel().matchedCount(reservedFuel) == reservedFuel.getCount();
    }

    double computerProgress() {
        return progress;
    }

    void clearMaterial() {
        reservedFuel = ItemStack.EMPTY;
        reservedRecipe = null;
        progress = 0D;
        localItems.clear();
        setChanged();
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

    void burn(SolidFissionRecipe recipe, double speed) {
        emission = Math.min(Double.MAX_VALUE, recipe.runtimeRadiation() * speed);
        emissionTick = level.getGameTime();
        progress += speed;
        if (progress >= recipe.runtimeTicks()) {
            progress = recipe.runtimeTicks();
            discharge(recipe);
        }
        setChanged();
    }

    @Override
    public double radiationEmission() {
        return level != null && level.getGameTime() == emissionTick ? emission : 0;
    }

    private void discharge(SolidFissionRecipe recipe) {
        var output = items(true);
        ItemStack depleted = recipe.depleted();
        if (output.insertBulk(1, depleted, true).isEmpty()) {
            output.insertBulk(1, recipe.product().roll(level.random), false);
            reservedFuel = ItemStack.EMPTY;
            reservedRecipe = null;
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
        localItems.save(tag);
        if (!reservedFuel.isEmpty()) {
            if (reservedRecipe != null) tag.putString("reserved_recipe", reservedRecipe.toString());
            tag.putBoolean("reserved_local", reservedLocal);
            if (reservedFilter != null) {
                tag.putString("reserved_filter", reservedFilter.toString());
            }
        }
        if (sourceEfficiency >= 0) {
            tag.putDouble("source_efficiency", sourceEfficiency);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        decay.load(tag);
        reservedFuel = ItemStack.of(tag.getCompound("fuel"));
        localItems.load(tag);
        reservedLocal = tag.getBoolean("reserved_local");
        reservedRecipe = tag.contains("reserved_recipe") ? ResourceLocation.tryParse(tag.getString("reserved_recipe")) : null;
        reservedFilter = tag.contains("reserved_filter") ? ResourceLocation.tryParse(tag.getString("reserved_filter")) : null;
        double stored = tag.getDouble("progress");
        progress = Double.isFinite(stored) ? Math.max(0, stored) : 0;
        double efficiency = tag.getDouble("source_efficiency");
        sourceEfficiency = tag.contains("source_efficiency") && Double.isFinite(efficiency) && efficiency >= 0 ? efficiency : -1;
    }
}
