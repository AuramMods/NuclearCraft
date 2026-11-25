package nc.mixin.compat;

import nc.compat.EslForgeEnergy;
import nc.compat.EslTransferProbe;
import net.minecraftforge.energy.IEnergyStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "org.antarcticgardens.esl.forge.energy.F2EEnergyStorageAdapter", remap = false)
abstract class EslEnergyAdapter implements EslForgeEnergy {
    @Shadow @Final private IEnergyStorage handle;

    @Override
    public int nuclearcraft$simulateExtraction(int maximum) {
        return handle.extractEnergy(maximum, true);
    }

    @Override
    public int nuclearcraft$simulateInsertion(int maximum) {
        return EslTransferProbe.receive(handle, maximum);
    }
}
