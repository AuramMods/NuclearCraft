package nc.mixin.compat;

import nc.radiation.ChunkRadiationSource;
import nc.radiation.RadiationConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "sviluppatore.ic2.core.block.reactor.ReactorBE", remap = false)
abstract class IndustrialCraftReactorRadiation implements ChunkRadiationSource {
    @Shadow
    public abstract double getReactorEUEnergyOutput();

    @Override
    public double radiationEmission() {
        return getReactorEUEnergyOutput() * RadiationConfig.industrialCraftReactorMultiplier();
    }
}
