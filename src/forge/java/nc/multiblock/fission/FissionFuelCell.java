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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public final class FissionFuelCell extends BlockEntity implements ChunkRadiationSource {
    private final RecipeManager.CachedCheck<Container, SolidFissionRecipe> recipes;
    private final SimpleContainer recipeInput = new SimpleContainer(1);
    private final @Nullable PebbleFuelItems pebbleItems;
    private @Nullable SolidFissionController pebbleController;
    private @Nullable ResourceLocation reservedFilter;
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
        boolean pebble = FissionPartBlock.role(state) == FissionPartBlock.Role.PEBBLE_CHAMBER;
        recipes = RecipeManager.createCheck(pebble ? NCPebbleFission.FUEL_TYPE.get() : NCFission.FUEL_TYPE.get());
        pebbleItems = pebble ? new PebbleFuelItems(this, null) : null;
    }

    ItemStack unspentFuel() {
        return progress == 0 ? reservedFuel : ItemStack.EMPTY;
    }

    @Nullable PebbleFuelItems localPebbleItems() {
        return pebbleItems;
    }

    void bindPebble(@Nullable SolidFissionController controller) {
        pebbleController = controller;
    }

    @Nullable SolidFissionController pebbleController() {
        return pebbleController != null && pebbleController.assembled() ? pebbleController : null;
    }

    @Nullable ResourceLocation pebbleFilter() {
        return pebbleItems == null ? null : pebbleItems.filter();
    }

    PebbleFuelItems pebbleItems(boolean output) {
        var controller = pebbleController();
        if (controller == null) {
            return pebbleItems;
        }
        if (output && !reservedFuel.isEmpty()) {
            return reservedLocal ? pebbleItems : controller.pebbleItems(reservedFilter);
        }
        return controller.hasPebbleCircuit(pebbleFilter()) ? controller.pebbleItems(pebbleFilter()) : pebbleItems;
    }

    ItemStack itemForInspection(ItemStackHandler fallback, boolean output) {
        return (pebbleItems == null ? fallback : pebbleItems(output)).getStackInSlot(output ? 1 : 0);
    }

    SolidFissionRecipe prepare(Level level, ItemStackHandler inventory) {
        boolean needsFuel = reservedFuel.isEmpty();
        ItemStackHandler feed = pebbleItems == null ? inventory : pebbleItems(false);
        // Existing Forge worlds may have fuel in the controller even after all chamber ports were removed.
        if (feed == pebbleItems && pebbleFilter() == null && feed.getStackInSlot(0).isEmpty()) {
            feed = inventory;
        }
        ItemStack input = needsFuel ? feed.getStackInSlot(0) : reservedFuel;
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
            reservedFuel = feed.extractItem(0, 1, false);
            if (pebbleItems != null) {
                reservedLocal = feed == pebbleItems;
                reservedFilter = pebbleFilter();
            }
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

    double progressFraction() {
        if (level == null || reservedFuel.isEmpty()) {
            return 0D;
        }
        recipeInput.setItem(0, reservedFuel);
        return recipes.getRecipeFor(recipeInput, level).map(recipe -> Math.min(1D, progress / recipe.ticks())).orElse(0D);
    }

    double computerProgress() {
        return progress;
    }

    void clearMaterial() {
        reservedFuel = ItemStack.EMPTY;
        progress = 0D;
        if (pebbleItems != null) {
            pebbleItems.clear();
        }
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
        if (pebbleItems != null) {
            inventory = pebbleItems(true);
        }
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
        if (pebbleItems != null) {
            pebbleItems.save(tag);
            if (!reservedFuel.isEmpty()) {
                tag.putBoolean("reserved_local", reservedLocal);
                if (reservedFilter != null) {
                    tag.putString("reserved_filter", reservedFilter.toString());
                }
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
        if (pebbleItems != null) {
            pebbleItems.load(tag);
            reservedLocal = tag.getBoolean("reserved_local");
            reservedFilter = tag.contains("reserved_filter") ? ResourceLocation.tryParse(tag.getString("reserved_filter")) : null;
        }
        double stored = tag.getDouble("progress");
        progress = Double.isFinite(stored) ? Math.max(0, stored) : 0;
        double efficiency = tag.getDouble("source_efficiency");
        sourceEfficiency = tag.contains("source_efficiency") && Double.isFinite(efficiency) && efficiency >= 0 ? efficiency : -1;
    }
}
