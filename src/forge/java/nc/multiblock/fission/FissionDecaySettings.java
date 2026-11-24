package nc.multiblock.fission;

public record FissionDecaySettings(Step heat, Step iodine, Step poison, double exponential, double linear) {
    public record Step(double buildupTicks, double lifetimeTicks, double equilibrium, double daughterMultiplier) {}
}
