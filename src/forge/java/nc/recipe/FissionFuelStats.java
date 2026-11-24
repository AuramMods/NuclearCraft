package nc.recipe;

import nc.init.NCFission;

public interface FissionFuelStats {
    int heat();
    double efficiency();
    double radiation();
    double runtimeTicks();
    int criticality();
    int intrinsicFlux();
    boolean selfPriming();
    double decayFactor();

    default int runtimeHeat() {
        return (int) (heat() * NCFission.fuelHeatMultiplier());
    }

    default double runtimeEfficiency() {
        return Math.min(Double.MAX_VALUE, efficiency() * NCFission.fuelEfficiencyMultiplier());
    }

    default double runtimeRadiation() {
        return Math.min(Double.MAX_VALUE, radiation() * NCFission.fuelRadiationMultiplier());
    }
}
