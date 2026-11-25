package nc.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import nc.compat.EslForgeEnergy;
import nc.compat.EslTransferProbe;
import nc.util.LoadedBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.antarcticgardens.cna.content.electricity.network.ElectricalNetwork", remap = false)
abstract class NewAgeElectricalNetwork {
    @Shadow public abstract void updateConsumersAndSources();
    @Unique private boolean nuclearcraft$refreshForgePorts;

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void refreshForgePorts(CallbackInfo callback) {
        if (nuclearcraft$refreshForgePorts) {
            nuclearcraft$refreshForgePorts = false;
            updateConsumersAndSources();
        }
    }

    @Redirect(method = "updateConsumersAndSources", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;"
                    + "m_7702_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private @Nullable BlockEntity findLoadedEndpoint(Level level, BlockPos position) {
        return LoadedBlockEntities.get(level, position);
    }

    @WrapOperation(method = "tick", require = 0, at = @At(value = "INVOKE",
            target = "Lorg/antarcticgardens/esl/energy/EnergyStorage;"
                    + "extract(JLorg/antarcticgardens/esl/transaction/TransactionContext;)J"),
            slice = @Slice(
                    from = @At(value = "INVOKE", target = "Lorg/antarcticgardens/esl/transaction/Transaction;"
                            + "openNested()Lorg/antarcticgardens/esl/transaction/Transaction;"),
                    to = @At(value = "INVOKE", target = "Lorg/antarcticgardens/esl/transaction/Transaction;abort()V")))
    private long simulateForgeSource(@Coerce Object storage, long maximum, @Coerce Object transaction,
                                     Operation<Long> extract) {
        // ESL aborts this probe by reinserting the energy, which an output-only Forge face rejects.
        if (storage instanceof EslForgeEnergy forge) {
            if (!forge.supportsExtraction()) {
                nuclearcraft$refreshForgePorts = true;
                return 0;
            }
            return forge.nuclearcraft$simulateExtraction((int) Math.min(maximum, Integer.MAX_VALUE));
        }
        return extract.call(storage, maximum, transaction);
    }

    @WrapOperation(method = "insertInto", require = 0, at = @At(value = "INVOKE",
            target = "Lorg/antarcticgardens/esl/energy/EnergyStorage;"
                    + "insert(JLorg/antarcticgardens/esl/transaction/TransactionContext;)J"))
    private long simulateForgeSink(@Coerce Object storage, long maximum, @Coerce Object transaction,
                                   Operation<Long> insert) {
        if (storage instanceof EslForgeEnergy forge) {
            // A battery bank can invalidate an adjacent port without changing the connector's block.
            if (!forge.supportsInsertion()) {
                nuclearcraft$refreshForgePorts = true;
                return 0;
            }
            // A simulated Forge push cannot take back energy through an input-only face.
            if (EslTransferProbe.active()) {
                return forge.nuclearcraft$simulateInsertion((int) Math.min(maximum, Integer.MAX_VALUE));
            }
        }
        return insert.call(storage, maximum, transaction);
    }

    @WrapWithCondition(method = "insertInto", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BlockEntity;m_6596_()V"))
    private boolean markDestinationChanged(BlockEntity destination) {
        return !EslTransferProbe.active();
    }

    @WrapWithCondition(method = "insertInto", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerChunkCache;m_8450_(Lnet/minecraft/core/BlockPos;)V"))
    private boolean notifyDestinationChanged(ServerChunkCache chunks, BlockPos position) {
        return !EslTransferProbe.active();
    }
}
