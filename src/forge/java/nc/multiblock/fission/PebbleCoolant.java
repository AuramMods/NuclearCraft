package nc.multiblock.fission;

import nc.recipe.FluidIngredient;
import net.minecraft.world.level.material.Fluid;

public interface PebbleCoolant {
    String id();

    FluidIngredient inputIngredient();

    Fluid cold();

    Fluid hot();

    int outputAmount();

    int cooling();
}
