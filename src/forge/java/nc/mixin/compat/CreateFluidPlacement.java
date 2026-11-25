package nc.mixin.compat;

import nc.fluid.FluidConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.fluids.transfer.FluidFillingBehaviour", remap = false)
abstract class CreateFluidPlacement {
    @Inject(method = "tryDeposit", at = @At("HEAD"), cancellable = true, require = 0)
    private void keepDisabledFluidInHose(Fluid fluid, BlockPos pos, boolean simulate, CallbackInfoReturnable<Boolean> result) {
        if (FluidConfig.blocksPlacement(fluid)) {
            result.setReturnValue(false);
        }
    }
}
