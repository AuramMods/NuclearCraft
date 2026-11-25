package nc.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public sealed interface FluidInput permits FluidIngredient, FluidInput.Alternatives {
    boolean isEmpty();

    boolean accepts(FluidStack stack);

    /**
     * Returns the amount to consume, or -1 when the input does not match.
     */
    int matchedAmount(FluidStack stack, int factor);

    default boolean matches(FluidStack stack) {
        return matches(stack, 1);
    }

    default boolean matches(FluidStack stack, int factor) {
        return matchedAmount(stack, factor) >= 0;
    }

    default List<FluidStack> alternatives() {
        return alternatives(1);
    }

    List<FluidStack> alternatives(int factor);

    int factor(int current);

    int maxAmount();

    static FluidInput readSlot(JsonElement entry, ResourceLocation id) {
        if (entry == null) {
            throw new JsonSyntaxException("Missing fluid input for " + id);
        }
        if (!hasAlternatives(entry)) {
            return readChoice(entry, id);
        }
        List<FluidIngredient> choices = new ArrayList<>();
        readChoices(entry, id, choices);
        return choose(choices);
    }

    private static void readChoices(JsonElement entry, ResourceLocation id, List<FluidIngredient> choices) {
        if (!hasAlternatives(entry)) {
            choices.add(readChoice(entry, id));
            return;
        }
        if (entry.isJsonObject() && entry.getAsJsonObject().size() != 1) {
            throw new JsonSyntaxException("Only alternatives are allowed in a fluid input group for " + id);
        }
        var entries = entry.isJsonArray() ? entry.getAsJsonArray() : GsonHelper.getAsJsonArray(entry.getAsJsonObject(), "alternatives");
        if (entries.isEmpty()) {
            throw new JsonSyntaxException("Expected at least one fluid alternative for " + id);
        }
        for (var choice : entries) {
            readChoices(choice, id, choices);
        }
    }

    private static boolean hasAlternatives(JsonElement entry) {
        return entry.isJsonArray() || entry.isJsonObject() && entry.getAsJsonObject().has("alternatives");
    }

    private static FluidIngredient readChoice(JsonElement entry, ResourceLocation id) {
        return MachineInputs.unusedSlot(entry) ? FluidIngredient.EMPTY
                : FluidIngredient.fromJson(GsonHelper.convertToJsonObject(entry, "fluid input"), id);
    }

    static List<FluidInput> readPair(JsonObject json, ResourceLocation id) {
        var inputs = GsonHelper.getAsJsonArray(json, "fluid_inputs");
        if (inputs.size() != 2) {
            throw new JsonSyntaxException("Expected two fluid inputs for " + id);
        }
        return List.of(readSlot(inputs.get(0), id), readSlot(inputs.get(1), id));
    }

    static List<FluidInput> readPair(FriendlyByteBuf buffer) {
        return List.of(fromNetwork(buffer), fromNetwork(buffer));
    }

    static FluidInput fromNetwork(FriendlyByteBuf buffer) {
        return choose(buffer.readList(FluidIngredient::fromNetwork));
    }

    static void write(FriendlyByteBuf buffer, FluidInput input) {
        if (input instanceof FluidIngredient ingredient) {
            buffer.writeVarInt(1);
            ingredient.toNetwork(buffer);
        } else if (input instanceof Alternatives alternatives) {
            buffer.writeCollection(alternatives.choices, (packet, choice) -> choice.toNetwork(packet));
        }
    }

    private static FluidInput choose(List<FluidIngredient> choices) {
        return choices.size() == 1 ? choices.get(0) : new Alternatives(choices);
    }

    record Alternatives(List<FluidIngredient> choices) implements FluidInput {
        public Alternatives {
            if (choices.isEmpty()) {
                throw new IllegalArgumentException("Fluid input needs an alternative");
            }
            choices = List.copyOf(choices);
        }

        @Override
        public boolean isEmpty() {
            return choices.stream().allMatch(FluidIngredient::isEmpty);
        }

        @Override
        public boolean accepts(FluidStack stack) {
            for (var choice : choices) {
                if (choice.accepts(stack)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int matchedAmount(FluidStack stack, int factor) {
            for (var choice : choices) {
                int amount = choice.matchedAmount(stack, factor);
                if (amount >= 0) {
                    return amount;
                }
            }
            return -1;
        }

        @Override
        public List<FluidStack> alternatives(int factor) {
            return choices.stream().flatMap(choice -> choice.alternatives(factor).stream()).toList();
        }

        @Override
        public int factor(int current) {
            for (var choice : choices) {
                current = choice.factor(current);
            }
            return current;
        }

        @Override
        public int maxAmount() {
            return choices.stream().mapToInt(FluidIngredient::amount).max().orElse(0);
        }
    }
}
