package nc.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public record FluidIngredient(ResourceLocation id, boolean tag, int amount, boolean legacyFluidAliases) {
    private static final FluidIngredient EMPTY = new FluidIngredient(new ResourceLocation("minecraft", "empty"), false, 0);

    public boolean isEmpty() {
        return amount == 0;
    }

    public FluidIngredient(ResourceLocation id, boolean tag, int amount) {
        this(id, tag, amount, false);
    }

    public boolean accepts(FluidStack stack) {
        return !isEmpty() && !stack.isEmpty() && accepts(stack.getFluid());
    }

    private boolean accepts(Fluid fluid) {
        if (tag) {
            if (!fluid.is(TagKey.create(Registries.FLUID, id))) {
                return false;
            }
        } else if (fluid == ForgeRegistries.FLUIDS.getValue(id)) {
            return true;
        }
        var alias = legacyFluidAliases ? LegacyFluidAliases.matching(id.getPath(), fluid) : null;
        return alias == null ? tag : alias.enabled();
    }

    public boolean matches(FluidStack stack) {
        return matches(stack, 1);
    }

    public boolean matches(FluidStack stack, int factor) {
        return isEmpty() ? stack.isEmpty() : stack.getAmount() >= amount / factor && accepts(stack);
    }

    public List<FluidStack> alternatives() {
        return alternatives(1);
    }

    public List<FluidStack> alternatives(int factor) {
        if (isEmpty()) {
            return List.of();
        }
        int batchAmount = amount / factor;
        if (!tag) {
            var primary = new FluidStack(ForgeRegistries.FLUIDS.getValue(id), batchAmount);
            if (!legacyFluidAliases) {
                return List.of(primary);
            }
            var aliases = Arrays.stream(LegacyFluidAliases.values()).filter(LegacyFluidAliases::enabled)
                    .flatMap(source -> source.sources(id.getPath()))
                    .map(fluid -> new FluidStack(fluid, batchAmount));
            return Stream.concat(Stream.of(primary), aliases).distinct().toList();
        }
        return BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, id)).stream()
                .flatMap(holders -> holders.stream().map(Holder::value))
                .filter(fluid -> fluid.isSource(fluid.defaultFluidState()) && accepts(fluid))
                .map(fluid -> new FluidStack(fluid, batchAmount)).toList();
    }

    public static FluidIngredient fromJson(JsonObject json, ResourceLocation recipeId) {
        boolean tag = json.has("tag");
        ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(json, tag ? "tag" : "fluid"));
        int amount = GsonHelper.getAsInt(json, "amount");
        if (id == null || amount < 1 || tag == json.has("fluid")
                || (!tag && (!ForgeRegistries.FLUIDS.containsKey(id) || ForgeRegistries.FLUIDS.getValue(id) == Fluids.EMPTY))) {
            throw new JsonSyntaxException("Invalid fluid input for " + recipeId);
        }
        return new FluidIngredient(id, tag, amount, GsonHelper.getAsBoolean(json, "legacy_fluid_aliases", false));
    }

    static FluidIngredient readSlot(JsonElement element, ResourceLocation recipe) {
        if (element == null) {
            throw new JsonSyntaxException("Missing fluid input for " + recipe);
        }
        return MachineInputs.unusedSlot(element) ? EMPTY
                : fromJson(GsonHelper.convertToJsonObject(element, "fluid input"), recipe);
    }

    static List<FluidIngredient> readPair(JsonObject json, ResourceLocation recipe) {
        var inputs = GsonHelper.getAsJsonArray(json, "fluid_inputs");
        if (inputs.size() != 2) {
            throw new JsonSyntaxException("Expected two fluid inputs for " + recipe);
        }
        return List.of(readSlot(inputs.get(0), recipe), readSlot(inputs.get(1), recipe));
    }

    static List<FluidIngredient> readPair(FriendlyByteBuf buffer) {
        return List.of(fromNetwork(buffer), fromNetwork(buffer));
    }

    static FluidStack readResult(JsonObject json, ResourceLocation recipe) {
        ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(json, "fluid"));
        var fluid = id == null ? null : ForgeRegistries.FLUIDS.getValue(id);
        int amount = GsonHelper.getAsInt(json, "amount");
        if (fluid == null || fluid == Fluids.EMPTY || amount < 1) {
            throw new JsonSyntaxException("Invalid fluid result for " + recipe);
        }
        return new FluidStack(fluid, amount);
    }

    public static FluidIngredient fromNetwork(FriendlyByteBuf buffer) {
        return new FluidIngredient(buffer.readResourceLocation(), buffer.readBoolean(), buffer.readVarInt(), buffer.readBoolean());
    }

    public void toNetwork(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(id);
        buffer.writeBoolean(tag);
        buffer.writeVarInt(amount);
        buffer.writeBoolean(legacyFluidAliases);
    }
}
