package nc.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import nc.recipe.ItemProcessorRecipe.Input;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public sealed interface ItemInput permits Input, ItemInput.Alternatives {
    boolean isEmpty();

    boolean accepts(ItemStack stack);

    /**
     * Returns the number of items to consume, or -1 when the input does not match.
     */
    int matchedCount(ItemStack stack);

    default boolean matches(ItemStack stack) {
        return matchedCount(stack) >= 0;
    }

    List<ItemStack> alternatives();

    static ItemInput readSlot(JsonElement entry, ResourceLocation id) {
        return readSlot(entry, id, Ingredient::fromJson);
    }

    static ItemInput readSlot(JsonElement entry, ResourceLocation id, Function<JsonElement, Ingredient> readIngredient) {
        if (entry == null) {
            throw new JsonSyntaxException("Missing item input for " + id);
        }
        List<Input> choices = new ArrayList<>();
        readChoices(entry, id, readIngredient, choices);
        return choose(choices);
    }

    private static void readChoices(JsonElement entry, ResourceLocation id, Function<JsonElement, Ingredient> readIngredient,
                                    List<Input> choices) {
        if (!entry.isJsonArray() && !(entry.isJsonObject() && entry.getAsJsonObject().has("alternatives"))) {
            if (MachineInputs.unusedSlot(entry)) {
                choices.add(Input.EMPTY);
                return;
            }
            var json = GsonHelper.convertToJsonObject(entry, "ingredient");
            int count = GsonHelper.getAsInt(json, "count", 1);
            if (count < 1 || count > 64) {
                throw new JsonSyntaxException("Invalid ingredient count for " + id);
            }
            choices.add(new Input(readIngredient.apply(GsonHelper.getNonNull(json, "ingredient")), count));
            return;
        }

        if (entry.isJsonObject() && entry.getAsJsonObject().size() != 1) {
            throw new JsonSyntaxException("Only alternatives are allowed in an item input group for " + id);
        }
        var entries = entry.isJsonArray() ? entry.getAsJsonArray() : GsonHelper.getAsJsonArray(entry.getAsJsonObject(), "alternatives");
        if (entries.isEmpty()) {
            throw new JsonSyntaxException("Expected at least one item alternative for " + id);
        }
        for (var choice : entries) {
            readChoices(choice, id, readIngredient, choices);
        }
    }

    static ItemInput fromNetwork(FriendlyByteBuf buffer) {
        return choose(buffer.readList(Input::fromNetwork));
    }

    static void write(FriendlyByteBuf buffer, ItemInput input) {
        if (input instanceof Input counted) {
            buffer.writeVarInt(1);
            counted.toNetwork(buffer);
        } else if (input instanceof Alternatives group) {
            buffer.writeCollection(group.choices, (packet, choice) -> choice.toNetwork(packet));
        }
    }

    private static ItemInput choose(List<Input> choices) {
        return choices.size() == 1 ? choices.get(0) : new Alternatives(choices);
    }

    record Alternatives(List<Input> choices) implements ItemInput {
        public Alternatives {
            if (choices.isEmpty()) {
                throw new IllegalArgumentException("Item input needs an alternative");
            }
            choices = List.copyOf(choices);
        }

        @Override
        public boolean isEmpty() {
            return choices.stream().allMatch(Input::isEmpty);
        }

        @Override
        public boolean accepts(ItemStack stack) {
            for (var choice : choices) {
                if (choice.accepts(stack)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int matchedCount(ItemStack stack) {
            for (var choice : choices) {
                int count = choice.matchedCount(stack);
                if (count >= 0) {
                    return count;
                }
            }
            return -1;
        }

        @Override
        public List<ItemStack> alternatives() {
            return choices.stream().flatMap(choice -> choice.alternatives().stream()).toList();
        }
    }
}
