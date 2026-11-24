package nc.multiblock.computer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public final class ComputerValues {
    private ComputerValues() {}

    public static List<Integer> position(BlockPos pos) {
        return List.of(pos.getX(), pos.getY(), pos.getZ());
    }

    public static List<Double> vector(Vec3 value) {
        return List.of(value.x(), value.y(), value.z());
    }

    public static List<Object> item(ItemStack stack) {
        return stack.isEmpty() ? List.of(0, "null")
                : List.of(stack.getCount(), BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    public static List<Object> fluid(FluidStack stack) {
        return stack.isEmpty() ? List.of(0, "null")
                : List.of(stack.getAmount(), ForgeRegistries.FLUIDS.getKey(stack.getFluid()).toString());
    }
}
