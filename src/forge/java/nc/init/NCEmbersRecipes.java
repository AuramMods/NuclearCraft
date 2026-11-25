package nc.init;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.rekindled.embers.recipe.FluidIngredient;
import com.rekindled.embers.recipe.MeltingRecipe;
import com.rekindled.embers.recipe.MixingRecipe;
import com.rekindled.embers.recipe.StampingRecipe;
import com.rekindled.embers.util.FluidOutput;
import nc.NuclearCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.function.Consumer;

public final class NCEmbersRecipes {
    private NCEmbersRecipes() {}

    public static void register() {
        StockRecipeAdjustments.register("embers", NCEmbersRecipes::adjustRecipe);
    }

    private static Recipe<?> adjustRecipe(Recipe<?> recipe, Consumer<Recipe<?>> additions) {
        if (recipe instanceof StampingRecipe stamping) {
            var input = normalize(stamping.fluid, 1);
            return input == stamping.fluid ? stamping
                    : new StampingRecipe(stamping.id, stamping.stamp, stamping.input, input, stamping.output);
        }
        if (recipe instanceof MeltingRecipe melting) {
            var output = normalize(melting.output, 1);
            var bonus = normalize(melting.bonus, 1);
            return output == melting.output && bonus == melting.bonus ? melting
                    : new MeltingRecipe(melting.id, melting.ingredient, output, bonus);
        }
        if (recipe instanceof MixingRecipe mixing) {
            return adjustMixture(mixing, additions);
        }
        return recipe;
    }

    private static MixingRecipe adjustMixture(MixingRecipe mixing, Consumer<Recipe<?>> additions) {
        var output = normalize(mixing.output, 1);
        var inputs = new ArrayList<FluidIngredient>();
        if (mixing.inputs.stream().map(FluidIngredient::serialize).noneMatch(NCEmbersRecipes::needsWholeBatch)) {
            mixing.inputs.forEach(input -> inputs.add(normalize(input, 1)));
            return output == mixing.output && inputs.equals(mixing.inputs) ? mixing
                    : new MixingRecipe(mixing.id, inputs, output);
        }

        // Keep the native batch for standard melts and use exact whole-mB batches when a legacy melt is present.
        var grouped = new ArrayList<FluidIngredient>();
        var affected = new LinkedHashMap<Integer, JsonArray>();
        for (int index = 0; index < mixing.inputs.size(); index++) {
            var ingredient = mixing.inputs.get(index);
            var json = ingredient.serialize();
            var alternatives = json.isJsonObject() ? MoltenMetalVolumes.metalAlternatives(json.getAsJsonObject()) : null;
            if (alternatives == null) {
                inputs.add(ingredient);
                grouped.add(normalize(ingredient, 5));
                continue;
            }
            inputs.add(FluidIngredient.deserialize(alternatives.get(1), "fluid"));
            scaleAmounts(json, 5);
            var sized = MoltenMetalVolumes.metalAlternatives(json.getAsJsonObject());
            grouped.add(FluidIngredient.deserialize(sized, "fluid"));
            affected.put(index, sized);
        }
        var groupedOutput = normalize(mixing.output, 5);
        affected.forEach((index, alternatives) -> {
            var variant = new ArrayList<>(grouped);
            variant.set(index, FluidIngredient.deserialize(alternatives.get(0), "fluid"));
            additions.accept(new MixingRecipe(NuclearCraft.id("compat/embers/" + mixing.id.getPath() + "/" + index),
                    variant, groupedOutput));
            // Later variants require earlier inputs to be standard so the legacy cases never overlap.
            grouped.set(index, FluidIngredient.deserialize(alternatives.get(1), "fluid"));
        });
        return new MixingRecipe(mixing.id, inputs, output);
    }

    private static FluidIngredient normalize(FluidIngredient input, int batches) {
        var json = input.serialize();
        if (batches != 1) {
            scaleAmounts(json, batches);
        }
        if (json.isJsonObject()) {
            var alternatives = MoltenMetalVolumes.metalAlternatives(json.getAsJsonObject());
            if (alternatives != null) {
                return FluidIngredient.deserialize(alternatives, "fluid");
            }
        }
        return batches == 1 ? input : FluidIngredient.deserialize(json, "fluid");
    }

    private static FluidOutput normalize(FluidOutput output, int batches) {
        if (output.tag == null) {
            return batches == 1 ? output
                    : new FluidOutput(new FluidStack(output.stack, Math.multiplyExact(output.stack.getAmount(), batches)));
        }
        var alternate = MoltenMetalVolumes.standardTag(output.tag.location());
        if (alternate == null && batches == 1) {
            return output;
        }
        var tag = alternate == null ? output.tag : TagKey.create(Registries.FLUID, alternate);
        return new FluidOutput(tag, Math.multiplyExact(output.amount, batches));
    }

    private static boolean needsWholeBatch(JsonElement input) {
        if (!input.isJsonObject()) {
            return false;
        }
        var object = input.getAsJsonObject();
        var tag = ResourceLocation.tryParse(GsonHelper.getAsString(object, "tag", ""));
        return MoltenMetalVolumes.standardTag(tag) != null && GsonHelper.getAsInt(object, "amount", 1) % 5 != 0;
    }

    private static void scaleAmounts(JsonElement input, int batches) {
        if (input.isJsonArray()) {
            input.getAsJsonArray().forEach(member -> scaleAmounts(member, batches));
        } else if (input.isJsonObject() && input.getAsJsonObject().has("amount")) {
            var object = input.getAsJsonObject();
            object.addProperty("amount", Math.multiplyExact(GsonHelper.getAsInt(object, "amount"), batches));
        }
    }
}
