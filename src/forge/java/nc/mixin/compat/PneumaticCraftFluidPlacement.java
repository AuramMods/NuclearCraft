package nc.mixin.compat;

import nc.fluid.FluidConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.desht.pneumaticcraft.common.util.FluidUtils", remap = false)
abstract class PneumaticCraftFluidPlacement {
    @Inject(method = "tryPourOutFluid", at = @At("HEAD"), cancellable = true, require = 0)
    private static void keepDisabledFluidInTank(LazyOptional<IFluidHandler> source, Level level, BlockPos pos,
                                               boolean sound, boolean force, IFluidHandler.FluidAction action,
                                               CallbackInfoReturnable<Boolean> result) {
        if (!FluidConfig.optionalFluidBlocksEnabled() && source.map(tank -> FluidConfig.blocksPlacement(
                tank.drain(FluidType.BUCKET_VOLUME, IFluidHandler.FluidAction.SIMULATE).getFluid())).orElse(false)) {
            result.setReturnValue(false);
        }
    }
}
