package nc.mixin.compat;

import nc.util.LoadedBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.enderio.core.common.capability.CapabilityUtil", remap = false)
abstract class EnderIoCapabilityLookups {
    // Repeated neighbor queries would otherwise keep the queried chunk loaded.
    @Redirect(method = "find", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_7702_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private static @Nullable BlockEntity findLoadedNeighbor(Level level, BlockPos pos) {
        return LoadedBlockEntities.get(level, pos);
    }
}
