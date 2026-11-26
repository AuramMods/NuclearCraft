package nc.mixin.compat;

import nc.compat.CompactMachineCapabilities;
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
@Mixin(targets = "dev.compactmods.machines.machine.CompactMachineBlockEntity", remap = false)
abstract class CompactMachineExterior extends BlockEntity {
    @Unique
    private final CompactMachineCapabilities nuclearcraft$tunnelCaps = new CompactMachineCapabilities();

    protected CompactMachineExterior(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "getCapability", at = @At("RETURN"), cancellable = true)
    private <T> void bindTunnelCapability(Capability<T> capability, Direction side,
                                          CallbackInfoReturnable<LazyOptional<T>> callback) {
        if (CompactMachineCapabilities.isTunnelCapability(capability)) {
            callback.setReturnValue(isRemoved() ? LazyOptional.empty() : nuclearcraft$tunnelCaps.bind(callback.getReturnValue()));
        }
    }

    @Inject(method = {"setConnectedRoom", "disconnect"}, at = @At("RETURN"), require = 2)
    private void invalidatePreviousConnection(CallbackInfo callback) {
        nuclearcraft$tunnelCaps.invalidate();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        nuclearcraft$tunnelCaps.invalidate();
    }
}
