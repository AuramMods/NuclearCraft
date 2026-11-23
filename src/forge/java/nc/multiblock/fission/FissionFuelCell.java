package nc.multiblock.fission;

import nc.init.NCFission;
import nc.recipe.SolidFissionRecipe;
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

public final class FissionFuelCell extends BlockEntity {
    private final RecipeManager.CachedCheck<Container, SolidFissionRecipe> recipes =
            RecipeManager.createCheck(NCFission.FUEL_TYPE.get());
    private final SimpleContainer recipeInput = new SimpleContainer(1);
    private ItemStack reservedFuel = ItemStack.EMPTY;
    private double progress;

    public FissionFuelCell(BlockPos pos, BlockState state) {
        super(NCFission.CELL_ENTITY.get(), pos, state);
    }

    ItemStack unspentFuel() {
        return progress == 0 ? reservedFuel : ItemStack.EMPTY;
    }

    SolidFissionRecipe prepare(Level level, ItemStackHandler inventory) {
        boolean needsFuel = reservedFuel.isEmpty();
        ItemStack input = needsFuel ? inventory.getStackInSlot(0) : reservedFuel;
        if (input.isEmpty()) {
            return null;
        }
        recipeInput.setItem(0, input);
        SolidFissionRecipe recipe = recipes.getRecipeFor(recipeInput, level).orElse(null);
        if (recipe == null) {
            return null;
        }
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

    void burn(SolidFissionRecipe recipe, ItemStackHandler inventory, double speed) {
        progress += speed;
        if (progress >= recipe.ticks()) {
            progress = recipe.ticks();
            discharge(recipe, inventory);
        }
        setChanged();
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
        tag.put("fuel", reservedFuel.save(new CompoundTag()));
        tag.putDouble("progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        reservedFuel = ItemStack.of(tag.getCompound("fuel"));
        double stored = tag.getDouble("progress");
        progress = Double.isFinite(stored) ? Math.max(0, stored) : 0;
    }
}
