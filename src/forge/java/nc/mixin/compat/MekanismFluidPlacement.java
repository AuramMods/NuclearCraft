package nc.mixin.compat;

import mekanism.api.fluid.IExtendedFluidTank;
import nc.fluid.FluidConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "mekanism.common.tile.machine.TileEntityFluidicPlenisher", remap = false)
abstract class MekanismFluidPlacement {
    @Redirect(method = "onUpdateServer", require = 0, at = @At(value = "INVOKE",
            target = "Lmekanism/common/capabilities/fluid/BasicFluidTank;isEmpty()Z"))
    private boolean noPlaceableFluid(@Coerce Object inputTank) {
        IExtendedFluidTank tank = (IExtendedFluidTank) inputTank;
        return tank.isEmpty() || FluidConfig.blocksPlacement(tank.getFluid().getFluid());
    }
}
