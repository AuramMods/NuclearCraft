package nc.mixin.compat;

import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ServerChunkCache.class, priority = 100, remap = false)
abstract class BetterChunkLoadingWaiting {}
