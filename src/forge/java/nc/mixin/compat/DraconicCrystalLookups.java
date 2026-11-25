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
@Mixin(targets = {
        "com.brandon3055.draconicevolution.blocks.energynet.tileentity.TileCrystalBase",
        "com.brandon3055.draconicevolution.blocks.energynet.tileentity.TileCrystalDirectIO",
        "com.brandon3055.draconicevolution.blocks.energynet.tileentity.TileCrystalWirelessIO$LinkedDevice"
}, remap = false)
abstract class DraconicCrystalLookups {
    // A crystal should wait for its other endpoint to load without renewing a chunk ticket on every tick.
    @Redirect(method = {"balanceLinkedDevices", "tick", "isLinkValid"}, require = 0, at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_7702_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private @Nullable BlockEntity nuclearcraft$findLoadedEndpoint(Level level, BlockPos pos) {
        return LoadedBlockEntities.get(level, pos);
    }
}
