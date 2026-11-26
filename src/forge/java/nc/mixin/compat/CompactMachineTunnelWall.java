package nc.mixin.compat;

import nc.compat.CompactMachineCapabilities;
import nc.compat.CompactMachineTunnelBuffers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.compactmods.machines.tunnel.TunnelWallEntity", remap = false)
abstract class CompactMachineTunnelWall extends BlockEntity {
    @Unique
    private final CompactMachineCapabilities nuclearcraft$tunnelCaps = new CompactMachineCapabilities();
    @Unique
    private CompactMachineTunnelBuffers.Owned nuclearcraft$boundTunnel;

    protected CompactMachineTunnelWall(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = {"m_142466_", "setTunnelType", "setInstance"}, at = @At("RETURN"), require = 3)
    private void bindTunnelBuffer(CallbackInfo callback) {
        var buffer = CompactMachineTunnelBuffers.ownerBuffer(this);
        if (buffer == nuclearcraft$boundTunnel) {
            return;
        }
        nuclearcraft$tunnelCaps.invalidate();
        if (nuclearcraft$boundTunnel != null) {
            nuclearcraft$boundTunnel.nuclearcraft$setTunnelOwner(null);
        }
        if (buffer != null) {
            buffer.nuclearcraft$setTunnelOwner(this);
        }
        nuclearcraft$boundTunnel = buffer;
    }

    @Inject(method = {"getTunnelCapability", "getCapability"}, at = @At("RETURN"), cancellable = true, require = 2)
    private <T> void bindTunnelCapability(Capability<T> capability, Direction side,
                                          CallbackInfoReturnable<LazyOptional<T>> callback) {
        if (CompactMachineCapabilities.isTunnelCapability(capability)) {
            callback.setReturnValue(isRemoved() ? LazyOptional.empty() : nuclearcraft$tunnelCaps.bind(callback.getReturnValue()));
        }
    }

    @Inject(method = "setConnectedTo", at = @At("RETURN"))
    private void invalidatePreviousConnection(CallbackInfo callback) {
        nuclearcraft$tunnelCaps.invalidate();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        nuclearcraft$tunnelCaps.invalidate();
    }
}
