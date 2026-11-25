package nc.recipe;

import net.minecraftforge.fluids.FluidStack;

import java.util.List;

public final class FluidBatch {
    private FluidBatch() {}

    static int inputFactor(List<FluidIngredient> inputs) {
        int factor = 0;
        for (FluidIngredient input : inputs) {
            factor = gcd(factor, input.amount());
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

    public static FluidStack divide(FluidStack stack, int factor) {
        return factor == 1 || stack.isEmpty() ? stack : new FluidStack(stack, stack.getAmount() / factor);
    }
}
