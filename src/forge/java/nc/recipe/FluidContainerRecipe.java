package nc.recipe;

import com.google.gson.JsonObject;
import nc.init.NCRegistries;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import org.jetbrains.annotations.Nullable;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

public final class FluidContainerRecipe implements CraftingRecipe {
    private final ResourceLocation id;
    private final String group;
    private final CraftingBookCategory category;
    private final Ingredient material;
    private final FluidIngredient fluid;
    private final ItemStack result;

    private FluidContainerRecipe(ResourceLocation id, String group, CraftingBookCategory category,
                                 Ingredient material, FluidIngredient fluid, ItemStack result) {
        this.id = id;
        this.group = group;
        this.category = category;
        this.material = material;
        this.fluid = fluid;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingContainer grid, Level level) {
        return fluidSlot(grid) >= 0;
    }

    @Override
    public ItemStack assemble(CraftingContainer grid, RegistryAccess registries) {
        return result.copy();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
        var remaining = NonNullList.withSize(grid.getContainerSize(), ItemStack.EMPTY);
        int fluidSlot = fluidSlot(grid);
        if (fluidSlot < 0) {
            return remaining;
        }
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack input = grid.getItem(slot);
            if (slot == fluidSlot) {
                ItemStack container = drain(input, EXECUTE);
                if (container != null) {
                    remaining.set(slot, container);
                }
            } else if (input.hasCraftingRemainingItem()) {
                remaining.set(slot, input.getCraftingRemainingItem());
            }
        }
        return remaining;
    }

    private int fluidSlot(CraftingContainer grid) {
        int first = -1;
        int second = -1;
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            if (grid.getItem(slot).isEmpty()) {
                continue;
            }
            if (first < 0) {
                first = slot;
            } else if (second < 0) {
                second = slot;
            } else {
                return -1;
            }
        }
        if (second < 0) {
            return -1;
        }
        if (material.test(grid.getItem(first)) && drain(grid.getItem(second), SIMULATE) != null) {
            return second;
        }
        if (material.test(grid.getItem(second)) && drain(grid.getItem(first), SIMULATE) != null) {
            return first;
        }
        return -1;
    }

    private @Nullable ItemStack drain(ItemStack stack, FluidAction action) {
        var handler = FluidUtil.getFluidHandler(stack.copyWithCount(1)).orElse(null);
        if (handler == null) {
            return null;
        }
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack available = handler.getFluidInTank(tank);
            if (!fluid.matches(available)) {
                continue;
            }
            var requested = new FluidStack(available, fluid.amount());
            if (handler.drain(requested, SIMULATE).isFluidStackIdentical(requested)) {
                // The crafting slot still holds the original. Only the used unit is drained and returned.
                return action.simulate() || handler.drain(requested, EXECUTE).isFluidStackIdentical(requested)
                        ? handler.getContainer() : null;
            }
        }
        return null;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        Ingredient display = Ingredient.of(fluid.alternatives().stream()
                .map(FluidUtil::getFilledBucket)
                .filter(bucket -> !bucket.isEmpty() && drain(bucket, SIMULATE) != null));
        return NonNullList.of(Ingredient.EMPTY, material, display);
    }

    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 2; }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return result; }
    @Override public ResourceLocation getId() { return id; }
    @Override public String getGroup() { return group; }
    @Override public CraftingBookCategory category() { return category; }
    @Override public RecipeSerializer<?> getSerializer() { return NCRegistries.FLUID_CONTAINER_CRAFTING.get(); }

    public static final class Serializer implements RecipeSerializer<FluidContainerRecipe> {
        @Override
        public FluidContainerRecipe fromJson(ResourceLocation id, JsonObject json) {
            var category = CraftingBookCategory.CODEC.byName(GsonHelper.getAsString(json, "category", "misc"),
                    CraftingBookCategory.MISC);
            var fluid = FluidIngredient.fromJson(GsonHelper.getAsJsonObject(json, "fluid_input"), id);
            return new FluidContainerRecipe(id, GsonHelper.getAsString(json, "group", ""), category,
                    Ingredient.fromJson(json.get("ingredient"), false), fluid,
                    ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result")));
        }

        @Override
        public FluidContainerRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new FluidContainerRecipe(id, buffer.readUtf(), buffer.readEnum(CraftingBookCategory.class),
                    Ingredient.fromNetwork(buffer), FluidIngredient.fromNetwork(buffer), buffer.readItem());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, FluidContainerRecipe recipe) {
            buffer.writeUtf(recipe.group);
            buffer.writeEnum(recipe.category);
            recipe.material.toNetwork(buffer);
            recipe.fluid.toNetwork(buffer);
            buffer.writeItem(recipe.result);
        }
    }
}
