package nc.mixin.compat;

import nc.util.LoadedBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = {
        "com.refinedmods.refinedstorage.apiimpl.storage.externalstorage.ItemExternalStorageProvider",
        "com.refinedmods.refinedstorage.apiimpl.storage.externalstorage.FluidExternalStorageProvider",
        "com.refinedmods.refinedstorage.apiimpl.network.node.NetworkNode"
}, remap = false)
abstract class RefinedStorageLookups {
    @Unique
    private static final String BLOCK_ENTITY_LOOKUP = "Lnet/minecraft/world/level/Level;"
            + "m_7702_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;";

    @Redirect(method = "lambda$provide$0", require = 0, at = @At(value = "INVOKE", target = BLOCK_ENTITY_LOOKUP))
    private static @Nullable BlockEntity findExternalStorage(Level level, BlockPos pos) {
        return LoadedBlockEntities.get(level, pos);
    }

    @Redirect(method = "getFacingBlockEntity", require = 0, at = @At(value = "INVOKE", target = BLOCK_ENTITY_LOOKUP))
    private @Nullable BlockEntity findAdjacentStorage(Level level, BlockPos pos) {
        return LoadedBlockEntities.get(level, pos);
    }
}
