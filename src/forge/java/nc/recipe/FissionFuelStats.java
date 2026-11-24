package nc.recipe;

public interface FissionFuelStats {
    int heat();
    double efficiency();
    int criticality();
    int intrinsicFlux();
    boolean selfPriming();
    double decayFactor();
}
