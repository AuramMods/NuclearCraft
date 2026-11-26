package nc.mixin.compat;

import nc.compat.CompactMachineTunnelBuffers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "dev.compactmods.machines.tunnel.definitions.FluidTunnel$Instance", remap = false)
abstract class CompactMachineFluidTunnel implements CompactMachineTunnelBuffers.Owned {
    @Unique
    private @Nullable BlockEntity nuclearcraft$tunnelOwner;

    @Override
    public void nuclearcraft$setTunnelOwner(@Nullable BlockEntity owner) {
        nuclearcraft$tunnelOwner = owner;
    }

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "(I)Lnet/minecraftforge/fluids/capability/templates/FluidTank;"))
    private FluidTank saveChangedFluid(int capacity) {
        return CompactMachineTunnelBuffers.fluid(capacity, () -> CompactMachineTunnelBuffers.markChanged(nuclearcraft$tunnelOwner));
    }
}
