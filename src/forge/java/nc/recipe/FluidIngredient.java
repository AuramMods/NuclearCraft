package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public record FluidIngredient(ResourceLocation id, boolean tag, int amount) {
    public boolean accepts(FluidStack stack) {
        return !stack.isEmpty() && (tag ? stack.getFluid().is(TagKey.create(Registries.FLUID, id))
                : stack.getFluid() == ForgeRegistries.FLUIDS.getValue(id));
    }

    public boolean matches(FluidStack stack) {
        return stack.getAmount() >= amount && accepts(stack);
    }

    public List<FluidStack> alternatives() {
        if (!tag) {
            return List.of(new FluidStack(ForgeRegistries.FLUIDS.getValue(id), amount));
        }
        return ForgeRegistries.FLUIDS.getValues().stream()
                .filter(fluid -> fluid.isSource(fluid.defaultFluidState()))
                .map(fluid -> new FluidStack(fluid, amount))
                .filter(this::accepts).toList();
    }

    public static FluidIngredient fromJson(JsonObject json, ResourceLocation recipeId) {
        boolean tag = json.has("tag");
        ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(json, tag ? "tag" : "fluid"));
        int amount = GsonHelper.getAsInt(json, "amount");
        if (id == null || amount < 1 || tag == json.has("fluid")
                || (!tag && (!ForgeRegistries.FLUIDS.containsKey(id) || ForgeRegistries.FLUIDS.getValue(id) == Fluids.EMPTY))) {
            throw new JsonSyntaxException("Invalid fluid input for " + recipeId);
        }
        return new FluidIngredient(id, tag, amount);
    }

    public static FluidIngredient fromNetwork(FriendlyByteBuf buffer) {
        return new FluidIngredient(buffer.readResourceLocation(), buffer.readBoolean(), buffer.readVarInt());
    }

    public void toNetwork(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(id);
        buffer.writeBoolean(tag);
        buffer.writeVarInt(amount);
    }
}
