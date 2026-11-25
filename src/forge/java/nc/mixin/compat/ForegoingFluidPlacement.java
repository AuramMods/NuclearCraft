package nc.mixin.compat;

import nc.fluid.FluidConfig;
import net.minecraftforge.fluids.IFluidTank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.buuz135.industrial.block.resourceproduction.tile.FluidPlacerTile", remap = false)
abstract class ForegoingFluidPlacement {
    @Redirect(method = "work", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/hrznstudio/titanium/component/fluid/SidedFluidTankComponent;getFluidAmount()I"))
    private int placeableVolume(@Coerce Object inputTank) {
        IFluidTank tank = (IFluidTank) inputTank;
        return FluidConfig.blocksPlacement(tank.getFluid().getFluid()) ? 0 : tank.getFluidAmount();
    }
}
