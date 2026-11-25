package nc.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public record FluidProduct(FluidStack stack, int minAmount, int step, int chancePercent) {
    static final FluidProduct EMPTY = new FluidProduct(FluidStack.EMPTY, 0, 1, 100);

    public FluidProduct {
        stack = stack.copy();
    }

    @Override
    public FluidStack stack() {
        return stack.copy();
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public boolean varies() {
        return chancePercent > 0 && chancePercent < 100 && minAmount < stack.getAmount();
    }

    public int rollAmount(RandomSource random) {
        if (chancePercent == 100) {
            return stack.getAmount();
        }
        int amount = minAmount;
        if (varies()) {
            for (int attempts = (stack.getAmount() - minAmount) / step; attempts > 0; attempts--) {
                if (random.nextInt(100) < chancePercent) {
                    amount += step;
                }
            }
        }
        return amount;
    }

    public FluidStack roll(RandomSource random) {
        int amount = rollAmount(random);
        return amount == 0 ? FluidStack.EMPTY : new FluidStack(stack, amount);
    }

    public int totalAmount(int batches, int factor, RandomSource random) {
        if (!varies()) return (rollAmount(random) / factor) * batches;
        int total = 0;
        for (int batch = 0; batch < batches; batch++) {
            total += rollAmount(random) / factor;
        }
        return total;
    }

    int factor(int current) {
        if (isEmpty()) {
            return current;
        }
        int factor = FluidBatch.gcd(current, stack.getAmount());
        return chancePercent < 100 ? FluidBatch.gcd(FluidBatch.gcd(factor, minAmount), step) : factor;
    }

    static FluidProduct fixed(FluidStack stack) {
        return new FluidProduct(stack, 0, 1, 100);
    }

    static FluidProduct readSlot(ResourceLocation id, JsonElement entry) {
        if (entry == null) {
            throw new JsonSyntaxException("Missing fluid result for " + id);
        }
        return MachineInputs.unusedSlot(entry) ? EMPTY : fromJson(id, GsonHelper.convertToJsonObject(entry, "fluid result"));
    }

    static List<FluidProduct> readSlots(ResourceLocation id, JsonArray entries) {
        List<FluidProduct> products = new ArrayList<>(entries.size());
        for (var entry : entries) {
            products.add(readSlot(id, entry));
        }
        return products;
    }

    static FluidProduct fromJson(ResourceLocation id, JsonObject json) {
        FluidStack stack = FluidIngredient.readResult(json, id);
        int minimum = GsonHelper.getAsInt(json, "min_amount", 0);
        int step = GsonHelper.getAsInt(json, "step", 1);
        int chance = GsonHelper.getAsInt(json, "chance_percent", 100);
        if (chance < 0 || chance > 100 || step < 1 || minimum < 0 || minimum > stack.getAmount()
                || (stack.getAmount() - minimum) % step != 0) {
            throw new JsonSyntaxException("Invalid fluid output chance for " + id);
        }
        return new FluidProduct(stack, minimum, step, chance);
    }

    static FluidProduct fromNetwork(FriendlyByteBuf buffer) {
        return new FluidProduct(buffer.readFluidStack(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUnsignedByte());
    }

    void toNetwork(FriendlyByteBuf buffer) {
        buffer.writeFluidStack(stack);
        buffer.writeVarInt(minAmount);
        buffer.writeVarInt(step);
        buffer.writeByte(chancePercent);
    }
}
