package nc.mixin.compat;

import nc.compat.CompactMachineTunnelBuffers;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.compactmods.machines.tunnel.TunnelWallEntity", remap = false)
abstract class CompactMachineTunnelWall {
    @Unique
    private CompactMachineTunnelBuffers.Owned nuclearcraft$boundTunnel;

    @Inject(method = {"m_142466_", "setTunnelType", "setInstance"}, at = @At("RETURN"), require = 3)
    private void bindTunnelBuffer(CallbackInfo callback) {
        BlockEntity wall = (BlockEntity) (Object) this;
        var buffer = CompactMachineTunnelBuffers.ownerBuffer(wall);
        if (buffer == nuclearcraft$boundTunnel) {
            return;
        }
        if (nuclearcraft$boundTunnel != null) {
            nuclearcraft$boundTunnel.nuclearcraft$setTunnelOwner(null);
        }
        if (buffer != null) {
            buffer.nuclearcraft$setTunnelOwner(wall);
        }
        nuclearcraft$boundTunnel = buffer;
    }
}
