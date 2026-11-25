package nc.recipe;

import net.minecraftforge.fluids.FluidStack;

import java.util.List;

public final class FluidBatch {
    private FluidBatch() {}

    static int inputFactor(List<FluidInput> inputs) {
        int factor = 0;
        for (FluidInput input : inputs) {
            factor = input.factor(factor);
        }
        return Math.max(1, factor);
    }

    static int gcd(int first, int second) {
        while (second != 0) {
            int remainder = first % second;
            first = second;
            second = remainder;
        }
        return first;
    }

    public record Usage(int consumed, int batches) {}

    public static Usage fit(FluidInput input, FluidStack stored, int available, int limit) {
        int remaining = Math.min(available, stored.getAmount());
        int count = 0;
        int consumed = 0;
        while (remaining > 0 && count < limit) {
            int volume = input.matchedAmount(stored, 1, remaining);
            if (volume <= 0) {
                break;
            }
            int batches = Math.min(remaining / volume, limit - count);
            int used = batches * volume;
            count += batches;
            remaining -= used;
            consumed += used;
        }
        return new Usage(consumed, count);
    }

    public static FluidStack divide(FluidStack stack, int factor) {
        return factor == 1 || stack.isEmpty() ? stack : new FluidStack(stack, stack.getAmount() / factor);
    }
}
