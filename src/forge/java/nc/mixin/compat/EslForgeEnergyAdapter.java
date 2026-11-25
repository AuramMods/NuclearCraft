package nc.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import nc.compat.EslTransferProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "org.antarcticgardens.esl.forge.energy.E2FEnergyStorageAdapter", remap = false)
abstract class EslForgeEnergyAdapter {
    @WrapOperation(method = "receiveEnergy", require = 0, at = @At(value = "INVOKE",
            target = "Lorg/antarcticgardens/esl/energy/EnergyStorage;"
                    + "insert(JLorg/antarcticgardens/esl/transaction/TransactionContext;)J"))
    private long markForgeProbe(@Coerce Object storage, long maximum, @Coerce Object transaction,
                                Operation<Long> insert, int requested, boolean simulate) {
        if (simulate) {
            return EslTransferProbe.simulate(() -> insert.call(storage, maximum, transaction));
        }
        return insert.call(storage, maximum, transaction);
    }
}
