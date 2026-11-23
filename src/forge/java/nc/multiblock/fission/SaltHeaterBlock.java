package nc.multiblock.fission;

import nc.init.NCSaltCoolants;

public final class SaltHeaterBlock extends FissionPartBlock {
    private final NCSaltCoolants coolant;

    public SaltHeaterBlock(Properties properties, NCSaltCoolants coolant) {
        super(properties, Role.SALT_HEATER);
        this.coolant = coolant;
    }

    NCSaltCoolants coolant() {
        return coolant;
    }
}
