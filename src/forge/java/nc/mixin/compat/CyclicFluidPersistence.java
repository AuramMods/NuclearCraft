package nc.mixin.compat;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.lothrazar.cyclic.capabilities.block.FluidTankBase", remap = false)
abstract class CyclicFluidPersistence {
    @Redirect(method = "onContentsChanged", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/lothrazar/cyclic/block/TileBlockEntityCyclic;getCapability("
                    + "Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)"
                    + "Lnet/minecraftforge/common/util/LazyOptional;"))
    private LazyOptional<?> saveChangedFluid(@Coerce Object owner, Capability<?> capability, Direction side) {
        BlockEntity tile = (BlockEntity) owner;
        var level = tile.getLevel();
        if (level != null && !level.isClientSide()) {
            tile.setChanged();
        }
        return tile.getCapability(capability, side);
    }
}
