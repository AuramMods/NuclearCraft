package nc.multiblock.fission;

import nc.recipe.FluidIngredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;

public interface FissionCoolant {
    String id();

    FluidIngredient inputIngredient();

    default Fluid cold() {
        var input = inputIngredient();
        if (!input.tag()) {
            return Objects.requireNonNullElse(ForgeRegistries.FLUIDS.getValue(input.id()), Fluids.EMPTY);
        }
        return input.alternatives().stream().map(FluidStack::getFluid).findFirst().orElse(Fluids.EMPTY);
    }

    Fluid hot();

    int outputAmount();

    int cooling();
}
