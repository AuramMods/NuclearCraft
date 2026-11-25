package nc.compat;

public interface EslForgeEnergy {
    boolean supportsExtraction();

    boolean supportsInsertion();

    int nuclearcraft$simulateExtraction(int maximum);

    int nuclearcraft$simulateInsertion(int maximum);
}
