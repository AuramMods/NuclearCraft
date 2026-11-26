package nc.mixin.compat;

import nc.compat.CompactMachineTunnelBuffers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.energy.EnergyStorage;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "dev.compactmods.machines.tunnel.definitions.ForgeEnergyTunnel$Instance", remap = false)
abstract class CompactMachineEnergyTunnel implements CompactMachineTunnelBuffers.Owned {
    @Unique
    private @Nullable BlockEntity nuclearcraft$tunnelOwner;

    @Override
    public void nuclearcraft$setTunnelOwner(@Nullable BlockEntity owner) {
        nuclearcraft$tunnelOwner = owner;
    }

    @Redirect(method = {"<init>", "deserializeNBT(Lnet/minecraft/nbt/CompoundTag;)V"}, require = 2,
            at = @At(value = "NEW", target = "(I)Lnet/minecraftforge/energy/EnergyStorage;"))
    private EnergyStorage saveChangedEnergy(int capacity) {
        return CompactMachineTunnelBuffers.energy(capacity, () -> CompactMachineTunnelBuffers.markChanged(nuclearcraft$tunnelOwner));
    }
}
